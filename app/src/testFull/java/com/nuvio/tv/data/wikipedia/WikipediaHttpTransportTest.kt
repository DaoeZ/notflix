package com.nuvio.tv.data.wikipedia

import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.*
import org.junit.Test

/** Fake Call.Factory only: no HTTP server, socket, DNS or public requests. */
class WikipediaHttpTransportTest {
    private val url="https://es.wikipedia.org/w/api.php?action=parse".toHttpUrl()
    private fun response(request:Request,code:Int=200,body:ResponseBody="{}".toResponseBody()):Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Synthetic fixture").body(body).build()
    @Test fun dedicatedClientHasNoCookiesAuthInterceptorsRedirectOrAutomaticRetry() {
        val c=WikipediaHttpTransport.anonymousClient()
        assertSame(CookieJar.NO_COOKIES,c.cookieJar);assertSame(Authenticator.NONE,c.authenticator);assertSame(Authenticator.NONE,c.proxyAuthenticator)
        assertTrue(c.interceptors.isEmpty());assertTrue(c.networkInterceptors.isEmpty());assertFalse(c.followRedirects);assertFalse(c.followSslRedirects);assertFalse(c.retryOnConnectionFailure)
        assertEquals(12_000,c.callTimeoutMillis);assertEquals(5000,c.connectTimeoutMillis);assertEquals(8000,c.readTimeoutMillis)
    }
    @Test fun descriptiveHonestHeadersContainNoAccountCredentialsOrCookies()=runBlocking<Unit> {
        val call=mockk<Call>();val factory=mockk<Call.Factory>();lateinit var sent:Request
        every{factory.newCall(any())} answers{sent=firstArg();call};every{call.cancel()} just Runs
        every{call.enqueue(any())} answers{firstArg<Callback>().onResponse(call,response(sent));Unit}
        assertEquals(200,WikipediaHttpTransport(factory).get(url).status)
        assertEquals(WikipediaPolicy.USER_AGENT,sent.header("User-Agent"));assertEquals(WikipediaPolicy.USER_AGENT,sent.header("Api-User-Agent"))
        assertNull(sent.header("Cookie"));assertNull(sent.header("Authorization"));assertNull(sent.header("Proxy-Authorization"))
    }
    @Test fun hostileUrlIsRejectedBeforeCallFactoryAndParametersAreEncoded()=runBlocking<Unit> {
        val factory=mockk<Call.Factory>()
        try{WikipediaHttpTransport(factory).get("https://evil.test/w/api.php".toHttpUrl());fail("Expected allowlist rejection")}catch(_:IllegalArgumentException){}
        verify(exactly=0){factory.newCall(any())}
        val title="Synthetic A&B / ¿title?"
        val built=WikipediaFullRepository.articleUrl("es",title)
        assertEquals(title,built.queryParameter("page"));assertEquals("parse",built.queryParameter("action"));assertNull(built.queryParameter("B"))
    }
    @Test fun redirectIsReturnedWithoutFollowingLocationOrReadingItsHtml()=runBlocking<Unit> {
        val factory=mockk<Call.Factory>();val call=mockk<Call>();lateinit var req:Request
        every{factory.newCall(any())} answers{req=firstArg();call};every{call.cancel()} just Runs
        every{call.enqueue(any())} answers{firstArg<Callback>().onResponse(call,response(req,302,"do not keep this body".toResponseBody()).newBuilder().header("Location","https://evil.test/").build());Unit}
        val result=WikipediaHttpTransport(factory).get(url);assertEquals(302,result.status);assertEquals("",result.body)
        verify(exactly=1){factory.newCall(any())}
    }
    @Test fun unknownLengthDecompressedResponseIsBoundedDuringReading()=runBlocking<Unit> {
        val factory=mockk<Call.Factory>();val call=mockk<Call>();lateinit var req:Request
        val oversized=object:ResponseBody(){override fun contentType()="application/json".toMediaType();override fun contentLength()=-1L;override fun source():BufferedSource=Buffer().write(ByteArray(WikipediaPolicy.MAX_RESPONSE_BYTES+1){'x'.code.toByte()})}
        every{factory.newCall(any())} answers{req=firstArg();call};every{call.cancel()} just Runs
        every{call.enqueue(any())} answers{firstArg<Callback>().onResponse(call,response(req,body=oversized));Unit}
        try{WikipediaHttpTransport(factory).get(url);fail("Expected bounded response failure")}catch(_:IOException){}
    }
    @Test fun cancellationCancelsCallAndLateResponseCannotResumeCancelledRequest()=runBlocking<Unit> {
        val factory=mockk<Call.Factory>();val call=mockk<Call>();val started=CompletableDeferred<Callback>();lateinit var req:Request
        every{factory.newCall(any())} answers{req=firstArg();call};every{call.cancel()} just Runs
        every{call.enqueue(any())} answers{started.complete(firstArg());Unit}
        val job=async{WikipediaHttpTransport(factory).get(url)};val callback=started.await();job.cancelAndJoin()
        callback.onResponse(call,response(req));assertTrue(job.isCancelled);verify(exactly=1){call.cancel()}
    }
}
