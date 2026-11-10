package com.nuvio.tv.data.wikipedia

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal data class WikipediaHttpResponse(val status: Int, val body: String, val retryAfter: String?)
internal fun interface WikipediaTransport { suspend fun get(url: HttpUrl): WikipediaHttpResponse }

/** Dedicated anonymous client: never inherits official account cookies, credentials or interceptors. */
internal class WikipediaHttpTransport(private val calls: Call.Factory = anonymousClient()) : WikipediaTransport {
    companion object {
        fun anonymousClient(): OkHttpClient = OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build()
    }
    override suspend fun get(url: HttpUrl): WikipediaHttpResponse {
        require(WikipediaPolicy.requestAllowed(url)) { "Unapproved Wikipedia API endpoint" }
        val request = Request.Builder().url(url).get().header("User-Agent", WikipediaPolicy.USER_AGENT)
            .header("Api-User-Agent", WikipediaPolicy.USER_AGENT)
            .header("Accept", "application/json, application/sparql-results+json").build()
        return suspendCancellableCoroutine { continuation ->
            val call = calls.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            if (!continuation.isActive) return@suspendCancellableCoroutine
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val value = response.use { r ->
                            val text = if (r.code == 200) {
                                val body = r.body ?: throw IOException("Empty API response")
                                if (body.contentLength() > WikipediaPolicy.MAX_RESPONSE_BYTES) throw IOException("API response too large")
                                val output = ByteArrayOutputStream()
                                body.byteStream().use { input ->
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        if (!continuation.isActive) throw IOException("API request cancelled")
                                        val size = input.read(buffer)
                                        if (size < 0) break
                                        if (output.size() + size > WikipediaPolicy.MAX_RESPONSE_BYTES) throw IOException("API response too large")
                                        output.write(buffer, 0, size)
                                    }
                                }
                                output.toByteArray().toString(Charsets.UTF_8)
                            } else ""
                            WikipediaHttpResponse(r.code, text, r.header("Retry-After")?.take(256))
                        }
                        if (continuation.isActive) continuation.resume(value)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
    }
}
