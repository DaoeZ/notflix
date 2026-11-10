package com.nuvio.tv.ui.screens.detail

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.nuvio.tv.data.wikipedia.WikipediaExtractCache
import com.nuvio.tv.data.wikipedia.WikipediaLabBudget
import com.nuvio.tv.data.wikipedia.WikipediaPolicy
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/** Explicit read-only export of this new feature's own evaluation counter/public source metadata only. */
class WikipediaRuntimeEvidenceTest {
    @Test fun exportOwnCounterAndPublicReferencesWithoutTextOrOtherAppData() {
        val ins=InstrumentationRegistry.getInstrumentation();val ctx=ins.targetContext
        val budget=WikipediaLabBudget(File(ctx.filesDir,"notflix_wikipedia_evaluation_v1/budget.json")).snapshot()
        assertNotNull(budget);assertTrue(budget!!.requests<=30);assertTrue(budget.identities.size<=6)
        val dir=File(ctx.cacheDir,"notflix_wikipedia_v1");val gson=Gson()
        val stored=dir.listFiles().orEmpty().filter{Regex("[a-f0-9]{64}\\.json").matches(it.name) && it.isFile && it.length()<=16*1024 && it.canonicalFile.parentFile==dir.canonicalFile}.mapNotNull { file ->
            runCatching{gson.fromJson(file.readText(),WikipediaExtractCache.Entry::class.java)}.getOrNull()
        }
        val entries=stored.filter{it.schema==1 && it.parser==WikipediaPolicy.PARSER_VERSION && it.key.startsWith("wiki:1:parser${WikipediaPolicy.PARSER_VERSION}:")}
        val sources=entries.mapNotNull { entry ->
            runCatching{entry.result?.let {
                mapOf("imdb" to it.imdbId,"type" to it.type,"language" to it.language,"entity" to it.entityId,"title" to it.articleTitle,
                    "url" to it.articleUrl,"section" to it.section,"revision" to it.revisionId,"page" to it.pageId,"fetched" to it.fetchedAtEpochMs,
                    "paragraphs" to it.paragraphs.size,"characters" to it.paragraphs.joinToString("\n\n").length)
            }}.getOrNull()
        }
        val output=File(requireNotNull(ctx.getExternalFilesDir(null)),"nf003-m18/runtime-${UUID.randomUUID()}");check(output.mkdirs())
        val outcomes=entries.mapNotNull { entry ->
            runCatching {
                val id=entry.key.split(':').singleOrNull{Regex("tt[0-9]{7,12}").matches(it)} ?: return@runCatching null
                if(entry.outcome !in listOf("found","absent","error")) return@runCatching null
                mapOf("imdb" to id,"outcome" to entry.outcome,"created" to entry.created,"expires" to entry.expires)
            }.getOrNull()
        }
        File(output,"metadata.json").writeText(gson.toJson(mapOf("budget" to budget,"cache_parser" to WikipediaPolicy.PARSER_VERSION,
            "legacy_entries_excluded" to (stored.size-entries.size),"sources_without_prose" to sources,"cache_outcomes" to outcomes)))
        Log.d("NotflixWikipediaTest","runtime_metadata_dir=${output.absolutePath}")
    }
}
