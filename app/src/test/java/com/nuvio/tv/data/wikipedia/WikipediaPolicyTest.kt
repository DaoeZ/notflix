package com.nuvio.tv.data.wikipedia

import com.nuvio.tv.domain.model.AppearanceContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

// All fixture prose, titles and revision numbers are synthetic, authored for these tests (CC0).
internal const val WIKI_FIXTURE_TEXT = "A synthetic reception paragraph is written solely for a Notflix test; it makes no claim about a real film or its critics."
internal fun wikipediaFixture(id: String = "tt0000001", language: String = "es", type: String = "movie", time: Long = 1_700_000_000_000): WikipediaExtract =
    WikipediaExtract(id, type, "Q123", language, "NF-M18 synthetic fixture", "https://$language.wikipedia.org/wiki/NF-M18_synthetic_fixture",
        if (language == "es") "Crítica" else "Critical response", "Critical_response", 10, 20, time, listOf(WIKI_FIXTURE_TEXT))
internal fun wikipediaBinding(id: String = "tt0000001", profile: Int = 2, generation: Long = 1) =
    WikipediaBinding(AppearanceContext(profile, generation), generation, WikipediaIdentity(id, "movie"))

class WikipediaPolicyTest {
    @Test fun exactIdentityRejectsNamesAndConflictingIdsAndUnsupportedTypes() {
        assertEquals(WikipediaIdentity("tt0068646", "movie"), WikipediaIdentity.resolve("tt0068646", null, "movie"))
        assertEquals(WikipediaIdentity("tt0903747", "series"), WikipediaIdentity.resolve("provider:series", "tt0903747", "series"))
        assertNull(WikipediaIdentity.resolve("A shared movie title", null, "movie"))
        assertNull(WikipediaIdentity.resolve("tt0068646", "tt0468569", "movie"))
        assertNull(WikipediaIdentity.resolve("tt0068646:1:2", null, "series"))
        assertNull(WikipediaIdentity.resolve("tt0068646", null, "anime"))
        assertFalse(WikipediaIdentity("tt123\" injection", "movie").valid())
    }
    @Test fun requestEndpointsRequireExactHttpsHostsAndNoCredentialsPortsOrFragments() {
        listOf("https://query.wikidata.org/sparql?query=x", "https://es.wikipedia.org/w/api.php?action=parse", "https://en.wikipedia.org/w/api.php").forEach {
            assertTrue(WikipediaPolicy.requestAllowed(it.toHttpUrl()))
        }
        listOf("http://es.wikipedia.org/w/api.php", "https://es.wikipedia.org.evil.test/w/api.php", "https://evil.test/w/api.php",
            "https://user:pass@es.wikipedia.org/w/api.php", "https://es.wikipedia.org:444/w/api.php", "https://es.wikipedia.org/w/api.php#fragment",
            "https://es.wikipedia.org/wiki/Arbitrary", "https://query.wikidata.org/not-sparql").forEach { assertFalse(it, WikipediaPolicy.requestAllowed(it.toHttpUrl())) }
        val long = HttpUrl.Builder().scheme("https").host("es.wikipedia.org").encodedPath("/w/api.php").addQueryParameter("x", "x".repeat(9000)).build()
        assertFalse(WikipediaPolicy.requestAllowed(long))
    }
    @Test fun articleDataCannotBecomeArbitraryRemoteRequestsOrNamespaces() {
        assertEquals("NF-M18 synthetic fixture", WikipediaPolicy.article(wikipediaFixture().articleUrl, "es")!!.second)
        listOf("https://evil.test/wiki/A", "https://es.wikipedia.org/wiki/A?next=evil", "https://es.wikipedia.org/wiki/A#evil",
            "https://es.wikipedia.org/wiki/Special:Random", "https://es.wikipedia.org/wiki/Usuario:Someone", "javascript:alert(1)",
            "https://es.wikipedia.org/wiki/%E2%80%AEspoof").forEach { assertNull(it, WikipediaPolicy.article(it, "es")) }
        assertNull(WikipediaPolicy.article(wikipediaFixture("tt0000001", "en").articleUrl, "es"))
    }
    @Test fun extractRequiresBoundedPlainTextAndVerifiableIdentityAttribution() {
        val v = wikipediaFixture(); assertTrue(v.validFor(WikipediaIdentity(v.imdbId, v.type)))
        assertTrue(v.revisionUrl()!!.startsWith("https://es.wikipedia.org/w/index.php?oldid=20#"))
        for (bad in listOf(v.copy(revisionId=0), v.copy(entityId="unknown"), v.copy(language="fr"), v.copy(paragraphs=emptyList()),
            v.copy(paragraphs=List(4){WIKI_FIXTURE_TEXT}), v.copy(paragraphs=listOf("x".repeat(901))),
            v.copy(paragraphs=listOf("x\u202e"+WIKI_FIXTURE_TEXT)), v.copy(adapted=false), v.copy(articleTitle="Other"))) {
            assertFalse(bad.toString(), bad.validFor(WikipediaIdentity(v.imdbId, v.type)))
        }
    }
    @Test fun cacheIdentitySeparatesTypeAndProfileButNotTransientSelectionAttempts() {
        val a = wikipediaBinding()
        assertNotEquals(a.cacheKey(), a.copy(identity=a.identity.copy(type="series")).cacheKey())
        assertNotEquals(a.cacheKey(), wikipediaBinding(profile=1).cacheKey())
        assertEquals(a.cacheKey(), a.copy(selectionGeneration=99).cacheKey())
        assertTrue(a.cacheKey().endsWith(":es,en"))
    }
}
