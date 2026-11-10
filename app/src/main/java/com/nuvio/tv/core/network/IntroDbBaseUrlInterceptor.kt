package com.nuvio.tv.core.network

import com.nuvio.tv.data.local.NotflixIntegrationConfig
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Reads the IntroDB base URL per request and rewrites the placeholder host, so changing the URL in
 * Settings takes effect without rebuilding Retrofit. Requests are gated before they reach here.
 */
@Singleton
class IntroDbBaseUrlInterceptor @Inject constructor(
    private val config: NotflixIntegrationConfig
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val parsed = config.introDbBaseUrl().toHttpUrlOrNull() ?: return chain.proceed(request)
        val rewritten = parsed.newBuilder()
            .addEncodedPathSegments(request.url.encodedPath.trimStart('/'))
            .encodedQuery(request.url.encodedQuery)
            .build()
        return chain.proceed(request.newBuilder().url(rewritten).build())
    }
}
