package com.nuvio.tv.core.profile

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgeRatingResolverTest {
    private fun meta(id: String = "tt1", age: String? = null) = MetaPreview(
        id = id,
        type = ContentType.MOVIE,
        name = "Title",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = null,
        genres = emptyList(),
        ageRating = age,
        imdbId = "tt1"
    )

    private class FakeSource(
        var lookup: AgeRatingLookup = AgeRatingLookup(AgeLevel.A16, cacheable = true)
    ) : TmdbAgeRatingSource {
        var calls = 0
        override suspend fun lookup(apiType: String, id: String, fallbackImdbId: String?): AgeRatingLookup {
            calls++
            return lookup
        }
    }

    private class FakeCache(
        private val entries: MutableMap<String, CachedAgeRating> = mutableMapOf()
    ) : AgeRatingCache {
        var puts = 0
        override suspend fun all(nowMs: Long): Map<String, CachedAgeRating> = entries.toMap()
        override suspend fun put(key: String, level: AgeLevel?, nowMs: Long) {
            puts++
            entries[key] = CachedAgeRating(level, nowMs)
        }
    }

    @Test
    fun `uses the title's own rating without asking tmdb`() = runBlocking {
        val source = FakeSource()
        val resolver = AgeRatingResolver(source, FakeCache(), TmdbAvailability { true })
        assertEquals(AgeLevel.A12, resolver.resolve(meta(age = "+12")))
        assertEquals(0, source.calls)
    }

    @Test
    fun `falls back to tmdb and caches the result`() = runBlocking {
        val source = FakeSource()
        val cache = FakeCache()
        val resolver = AgeRatingResolver(source, cache, TmdbAvailability { true })
        assertEquals(AgeLevel.A16, resolver.resolve(meta()))
        assertEquals(1, source.calls)
        assertEquals(AgeLevel.A16, resolver.resolve(meta()))
        assertEquals(1, source.calls)
        assertEquals(1, cache.puts)
    }

    @Test
    fun `respects a fresh persistent cache`() = runBlocking {
        val key = ageRatingKey("movie", "tt1")
        val now = System.currentTimeMillis()
        val cache = FakeCache(mutableMapOf(key to CachedAgeRating(AgeLevel.A18, now)))
        val source = FakeSource()
        val resolver = AgeRatingResolver(source, cache, TmdbAvailability { true })
        assertEquals(AgeLevel.A18, resolver.resolve(meta()))
        assertEquals(0, source.calls)
    }

    @Test
    fun `ignores an expired persistent cache`() = runBlocking {
        val key = ageRatingKey("movie", "tt1")
        val expired = System.currentTimeMillis() - AgeRatingResolver.CACHE_TTL_MS - 1
        val cache = FakeCache(mutableMapOf(key to CachedAgeRating(AgeLevel.A18, expired)))
        val source = FakeSource()
        val resolver = AgeRatingResolver(source, cache, TmdbAvailability { true })
        assertEquals(AgeLevel.A16, resolver.resolve(meta()))
        assertEquals(1, source.calls)
    }

    @Test
    fun `without a tmdb key the title is unknown and nothing is requested`() = runBlocking {
        val source = FakeSource()
        val cache = FakeCache()
        val resolver = AgeRatingResolver(source, cache, TmdbAvailability { false })
        assertNull(resolver.resolve(meta()))
        assertEquals(0, source.calls)
        assertEquals(0, cache.puts)
    }

    @Test
    fun `without a limit it resolves nothing`() = runBlocking {
        val source = FakeSource()
        val resolver = AgeRatingResolver(source, FakeCache(), TmdbAvailability { true })
        assertNull(resolver.resolveForLimit(meta(), null))
        assertEquals(0, source.calls)
    }

    @Test
    fun `concurrent requests for the same title are coalesced`() = runBlocking {
        val source = FakeSource()
        val resolver = AgeRatingResolver(source, FakeCache(), TmdbAvailability { true })
        coroutineScope {
            (1..8).map { async { resolver.resolve(meta()) } }.awaitAll()
        }
        assertEquals(1, source.calls)
    }
}
