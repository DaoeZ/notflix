package com.nuvio.tv.data.wikipedia

import java.io.File
import java.nio.file.Files
import okhttp3.HttpUrl
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class WikipediaExtractCacheTest {
    private val root = Files.createTempDirectory("nf18-cache-").toFile()
    private var time = 1_700_000_000_000L
    private fun cache() = WikipediaExtractCache(root) { time }
    @After fun cleanup() {
        check(root.canonicalFile.parentFile == File(System.getProperty("java.io.tmpdir")).canonicalFile && root.name.startsWith("nf18-cache-"))
        root.deleteRecursively()
    }
    @Test fun positiveReopensWithRevisionAndExpiresAtThreeDays() {
        val b=wikipediaBinding();val v=wikipediaFixture(time=time)
        assertTrue(cache().write(b,v,"found"){true});assertEquals(v,cache().read(b)!!.result)
        time+=WikipediaExtractCache.POSITIVE_TTL-1;assertEquals(20L,cache().read(b)!!.result!!.revisionId)
        time++;assertNull(cache().read(b))
    }
    @Test fun negativeAndNetworkErrorUseShorterExpiryAndRemainDistinguishable() {
        val b=wikipediaBinding();assertTrue(cache().write(b,null,"absent"){true})
        assertEquals("absent",cache().read(b)!!.outcome);time+=WikipediaExtractCache.NEGATIVE_TTL;assertNull(cache().read(b))
        assertTrue(cache().write(b,null,"error"){true});assertEquals("error",cache().read(b)!!.outcome)
        time+=WikipediaExtractCache.ERROR_TTL;assertNull(cache().read(b))
    }
    @Test fun corruptionAndOversizeFailClosedAndDoNotTouchUnrelatedFiles() {
        val b=wikipediaBinding();cache().write(b,wikipediaFixture(time=time),"found"){true}
        val file=root.listFiles()!!.single();file.writeText("{broken");assertNull(cache().read(b))
        file.writeText("x".repeat(WikipediaExtractCache.MAX_ENTRY+1));assertNull(cache().read(b))
        val unrelated=File(root,"official-do-not-touch.txt").apply{writeText("sentinel")}
        cache().write(b,wikipediaFixture(time=time),"found"){true};assertEquals("sentinel",unrelated.readText())
    }
    @Test fun revokedWriteAndAnotherProfileCannotExposeAnExtract() {
        val b=wikipediaBinding();assertFalse(cache().write(b,wikipediaFixture(time=time),"found"){false})
        assertNull(cache().read(b));assertTrue(cache().write(b,wikipediaFixture(time=time),"found"){true})
        assertNull(cache().read(wikipediaBinding(profile=1)))
        assertFalse(cache().write(b,wikipediaFixture("tt0000002",time=time),"found"){true})
    }
    @Test fun entryAndTotalDiskBoundsApplyAcrossManyLargeUnicodeExtracts() {
        val title="NF-M18 "+"文".repeat(180)
        val url=HttpUrl.Builder().scheme("https").host("es.wikipedia.org").addPathSegment("wiki").addPathSegment(title.replace(' ','_')).build().toString()
        for(index in 1..60) {
            val id="tt"+index.toString().padStart(7,'0');val b=wikipediaBinding(id)
            val v=wikipediaFixture(id,time=time).copy(articleTitle=title,articleUrl=url,anchor="節".repeat(180),paragraphs=listOf("文".repeat(889)+"."))
            assertTrue(v.validFor(b.identity));assertTrue(cache().write(b,v,"found"){true});time++
        }
        val files=root.listFiles()!!.filter{it.extension=="json"}
        assertTrue(files.size<=48);assertTrue(files.sumOf{it.length()}<=256*1024)
        assertTrue(files.all{it.length()<=16*1024});assertNull(cache().read(wikipediaBinding("tt0000001")))
    }
    @Test fun forgedOutcomeOrFutureContentIsNotReadAsCurrentData() {
        val b=wikipediaBinding();assertTrue(cache().write(b,wikipediaFixture(time=time+1),"found"){true})
        assertNull(cache().read(b));assertFalse(cache().write(b,null,"found"){true})
        assertFalse(cache().write(b,wikipediaFixture(time=time),"absent"){true})
    }
    @Test fun reopenedCacheRemovesOnlyItsOwnAbandonedTemporaryFiles() {
        val own=File(root,"f".repeat(64)+".json.tmp").apply{writeText("partial")}
        val other=File(root,"unrelated.tmp").apply{writeText("sentinel")}
        assertNull(cache().read(wikipediaBinding()));assertFalse(own.exists());assertEquals("sentinel",other.readText())
    }
    @Test fun previousParserCannotRepublishItsExtractEvenIfItsFileIsStillPresent() {
        val b=wikipediaBinding();assertTrue(cache().write(b,wikipediaFixture(time=time),"found"){true})
        val current=root.listFiles()!!.single()
        val oldJson=current.readText().replace("\"parser\":${WikipediaPolicy.PARSER_VERSION}","\"parser\":1")
        assertNotEquals(current.readText(),oldJson);current.writeText(oldJson);assertNull(cache().read(b))
        val oldKey=b.cacheKey().replace("parser${WikipediaPolicy.PARSER_VERSION}","parser1");assertNotEquals(oldKey,b.cacheKey())
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(oldKey.toByteArray()).joinToString(""){"%02x".format(it)}
        val legacy=File(root,"$hash.json").apply{writeText(oldJson.replace(b.cacheKey(),oldKey))}
        check(current.delete());assertTrue(legacy.exists());assertNull(cache().read(b))
    }
}
