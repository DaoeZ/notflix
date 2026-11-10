package com.nuvio.tv.data.wikipedia

import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.*
import okhttp3.HttpUrl
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class WikipediaFullRepositoryTest {
    private val root=Files.createTempDirectory("nf18-repository-").toFile()
    private var time=1_700_000_000_000L
    private val budget=WikipediaLabBudget(File(root,"budget/budget.json")){time}
    private val cache=WikipediaExtractCache(File(root,"cache")){time}
    private val requests=mutableListOf<HttpUrl>()
    private val writer=WikipediaCacheWriter{_,authorized,action->if(authorized())action()}
    private fun identity(ambiguous:Boolean=false,empty:Boolean=false):String {
        fun row(q:String)=mapOf("item" to mapOf("type" to "uri","value" to "http://www.wikidata.org/entity/$q"),
            "es" to mapOf("type" to "uri","value" to wikipediaFixture().articleUrl),"en" to mapOf("type" to "uri","value" to wikipediaFixture(language="en").articleUrl))
        return Gson().toJson(mapOf("results" to mapOf("bindings" to if(empty)emptyList()else if(ambiguous)listOf(row("Q123"),row("Q456"))else listOf(row("Q123")))))
    }
    private fun repository(available:Boolean=true,timeout:Long=30_000,respond:suspend(HttpUrl)->WikipediaHttpResponse):WikipediaFullRepository =
        WikipediaFullRepository(WikipediaTransport{url->requests+=url;respond(url)},cache,budget,writer,available=available,now={time},operationTimeoutMs=timeout)
    @After fun cleanup(){check(root.canonicalFile.parentFile==File(System.getProperty("java.io.tmpdir")).canonicalFile && root.name.startsWith("nf18-repository-"));root.deleteRecursively()}
    @Test fun spanishFirstAndConcurrentReopenUseOneResolutionAndOneArticle()=runBlocking<Unit> {
        val repo=repository{url->WikipediaHttpResponse(200,if(url.host=="query.wikidata.org")identity()else wikipediaPayload(),null)}
        val a=async{repo.find(wikipediaBinding()){true}};val b=async{repo.find(wikipediaBinding()){true}}
        assertEquals("es",a.await()!!.language);assertEquals("es",b.await()!!.language)
        assertEquals(2,requests.size);assertEquals(2,budget.snapshot()!!.requests)
        assertEquals("tocdata|text|revid",requests.last().queryParameter("prop"));assertNull(requests.last().queryParameter("section"))
    }
    @Test fun unsafeSpanishFallsBackToLabelledEnglishInThirdRequest()=runBlocking<Unit> {
        val repo=repository{url->WikipediaHttpResponse(200,when(url.host){"query.wikidata.org"->identity();"es.wikipedia.org"->wikipediaPayload("<h2 id='Critical_response'>Crítica</h2><p>\"$WIKI_FIXTURE_TEXT\"</p>");else->wikipediaPayload("<h2 id='Critical_response'>Critical response</h2><p>$WIKI_FIXTURE_TEXT</p>","en")},null)}
        val v=repo.find(wikipediaBinding()){true}!!;assertEquals("en",v.language);assertEquals(3,requests.size)
    }
    @Test fun ambiguousEntityOrAbsentIdentityProducesNoArticleRequest()=runBlocking<Unit> {
        val repo=repository{WikipediaHttpResponse(200,identity(ambiguous=true),null)}
        assertNull(repo.find(wikipediaBinding()){true});assertEquals(1,requests.size)
        assertNull(repo.find(wikipediaBinding()){true});assertEquals(1,requests.size);assertEquals("absent",cache.read(wikipediaBinding())!!.outcome)
        val missing=repository{WikipediaHttpResponse(200,identity(empty=true),null)}
        assertNull(missing.find(wikipediaBinding("tt0000002")){true});assertEquals(2,requests.size)
    }
    @Test fun unapprovedReturnedArticleUrlNeverTriggersArbitraryFetch()=runBlocking<Unit> {
        val payload=identity().replace("https://es.wikipedia.org/wiki/NF-M18_synthetic_fixture","https://evil.test/private")
        val repo=repository{WikipediaHttpResponse(200,payload,null)}
        assertNull(repo.find(wikipediaBinding()){true});assertEquals(1,requests.size)
    }
    @Test fun disabledOrUnsupportedVariantDoesNoDiskOrNetworkWork()=runBlocking<Unit> {
        val repo=repository(available=false){error("No network allowed")}
        assertNull(repo.find(wikipediaBinding()){true});assertTrue(requests.isEmpty());assertFalse(File(root,"cache").exists());assertFalse(File(root,"budget").exists())
        val enabled=repository{error("No network while revoked")};assertNull(enabled.find(wikipediaBinding()){false});assertTrue(requests.isEmpty())
    }
    @Test fun retryAfterStopsFallbackAndPersistsCooldownAcrossVisits()=runBlocking<Unit> {
        val repo=repository{WikipediaHttpResponse(429,"","600")}
        assertNull(repo.find(wikipediaBinding()){true});assertEquals(time+600_000,budget.snapshot()!!.notBefore)
        assertNull(repo.find(wikipediaBinding()){true});assertEquals(1,requests.size);assertNull(cache.read(wikipediaBinding()))
        assertEquals(Long.MAX_VALUE,WikipediaFullRepository.retryAt(Long.MAX_VALUE.toString(),time))
        time+=600_000;assertNull(repo.find(wikipediaBinding()){true});assertEquals(2,requests.size)
    }
    @Test fun offlineAndSocketTimeoutUseShortErrorCacheWithoutRetryLoops()=runBlocking<Unit> {
        val repo=repository{throw java.net.SocketTimeoutException("Synthetic offline timeout")}
        assertNull(repo.find(wikipediaBinding()){true});assertEquals("error",cache.read(wikipediaBinding())!!.outcome)
        assertNull(repo.find(wikipediaBinding()){true});assertEquals(1,requests.size)
        time+=WikipediaExtractCache.ERROR_TTL;assertNull(repo.find(wikipediaBinding()){true});assertEquals(2,requests.size)
    }
    @Test fun cancellationAndLateRevocationNeverCacheOrRequestAnotherLanguage()=runBlocking<Unit> {
        val started=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>();var allowed=true
        val repo=repository{started.complete(Unit);finish.await();WikipediaHttpResponse(200,identity(),null)}
        val job=async{repo.find(wikipediaBinding()){allowed}};started.await();allowed=false;finish.complete(Unit)
        try{job.await();fail("Expected cancelled context")}catch(_:CancellationException){}
        assertEquals(1,requests.size);assertNull(cache.read(wikipediaBinding()))
    }
    @Test fun totalOperationDeadlineCancelsTransportWithoutLateCache()=runBlocking<Unit> {
        var cancelled=false
        val repo=repository(timeout=50){try{awaitCancellation()}finally{cancelled=true}}
        try{repo.find(wikipediaBinding()){true};fail("Expected operation deadline")}catch(_:TimeoutCancellationException){}
        assertTrue(cancelled);assertNull(cache.read(wikipediaBinding()));assertEquals(1,requests.size)
    }
    @Test fun malformedApiAndMaxlagFailSafelyWithoutArticleBodyPersistence()=runBlocking<Unit> {
        val malformed=repository{WikipediaHttpResponse(200,"not JSON",null)};assertNull(malformed.find(wikipediaBinding()){true})
        val maxlag=repository{WikipediaHttpResponse(200,"{\"error\":{\"code\":\"maxlag\"}}","120")}
        assertNull(maxlag.find(wikipediaBinding("tt0000002")){true});assertEquals(time+120_000,budget.snapshot()!!.notBefore)
        assertTrue(File(root,"cache").listFiles().orEmpty().filter{it.extension=="json"}.all{!it.readText().contains("<h2")})
    }
}
