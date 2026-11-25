package com.nuvio.tv.core.profile

import com.nuvio.tv.data.local.ProfileAgeLimitStore
import com.nuvio.tv.domain.model.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class ActiveProfileKidsGateTest {
    private fun item(id: String, rating: String? = null) = MetaPreview(id, ContentType.MOVIE,
        name = id, poster = null, posterShape = PosterShape.POSTER, background = null,
        logo = null, description = null, releaseInfo = null, imdbRating = null,
        genres = emptyList(), ageRating = rating)

    private suspend fun close(gate: ActiveProfileAgeGate) {
        val field = ActiveProfileAgeGate::class.java.getDeclaredField("scope").apply { isAccessible = true }
        (field.get(gate) as CoroutineScope).coroutineContext[Job]?.cancelAndJoin()
    }

    @Test fun kidsPagePublishesSafeCachedTitlesBeforeWaitingOnNetwork() = runBlocking {
        val allowNetwork = CompletableDeferred<Unit>()
        val cache = mockk<AgeRatingCache>(relaxed = true) {
            coEvery { all(any()) } returns mapOf(
                ageRatingKey("movie", "cached") to CachedAgeRating(AgeLevel.TP, System.currentTimeMillis())
            )
        }
        val source = object : TmdbAgeRatingSource {
            override suspend fun lookup(apiType: String, id: String, fallbackImdbId: String?): AgeRatingLookup {
                allowNetwork.await()
                return AgeRatingLookup(if (id == "family") AgeLevel.TP else null, cacheable = true)
            }
        }
        val profiles = mockk<ProfileManager> { every { activeProfileId } returns MutableStateFlow(2) }
        val limits = mockk<ProfileAgeLimitStore> { every { observe(any()) } returns flowOf(AgeLevel.TP) }
        val gate = ActiveProfileAgeGate(profiles, limits, AgeRatingResolver(source, cache) { true })
        try {
            val first = CompletableDeferred<Pair<List<String>, Boolean>>()
            val last = CompletableDeferred<List<String>>()
            val loading = launch {
                gate.filterKidsProgressively(
                    listOf(item("family"), item("adult", "16"), item("cached"), item("unrated"), item("own", "TP")),
                    AgeLevel.TP
                ) { items, complete ->
                    val ids = items.map { it.id }
                    first.complete(ids to complete)
                    if (complete) last.complete(ids)
                }
            }
            assertEquals(listOf("cached", "own") to false, withTimeout(5_000) { first.await() })
            assertTrue(loading.isActive)
            allowNetwork.complete(Unit)
            assertEquals(listOf("family", "cached", "own"), withTimeout(5_000) { last.await() })
        } finally {
            allowNetwork.complete(Unit)
            close(gate)
        }
    }

    @Test fun explicitKidsCeilingNeverFallsBackToUnrestrictedWhileTheProfileIsLoading() = runBlocking {
        val profiles = mockk<ProfileManager> { every { activeProfileId } returns MutableStateFlow(2) }
        val limits = mockk<ProfileAgeLimitStore> { every { observe(any()) } returns MutableSharedFlow() }
        val resolver = mockk<AgeRatingResolver> {
            every { ratings } returns MutableStateFlow(emptyMap())
            coEvery { resolve(any<MetaPreview>()) } coAnswers {
                if (firstArg<MetaPreview>().id == "rated-by-source") AgeLevel.TP else null
            }
        }
        val gate = ActiveProfileAgeGate(profiles, limits, resolver)
        try {
            assertNull(gate.currentLimit())
            val allowed = gate.filterForLimit(listOf(item("own-tp", "TP"), item("adult", "16"),
                item("unknown"), item("rated-by-source")), AgeLevel.TP)
            assertEquals(listOf("own-tp", "rated-by-source"), allowed.map { it.id })
        } finally { close(gate) }
    }

    @Test fun aStoredLimitArrivingAfterAProfileSwitchCannotAuthorizeTheOldKidsRoute() = runBlocking {
        val active = MutableStateFlow(2)
        val first = MutableSharedFlow<AgeLevel?>()
        val profiles = mockk<ProfileManager> { every { activeProfileId } returns active }
        val limits = mockk<ProfileAgeLimitStore> {
            every { observe(2) } returns first
            every { observe(3) } returns MutableStateFlow<AgeLevel?>(AgeLevel.A7)
        }
        val resolver = mockk<AgeRatingResolver> { every { ratings } returns MutableStateFlow(emptyMap()) }
        val gate = ActiveProfileAgeGate(profiles, limits, resolver)
        try {
            val pending = async(Dispatchers.Default) { gate.currentLimitFromStore() }
            withTimeout(5_000) { first.subscriptionCount.first { it >= 2 } }
            active.value = 3
            first.emit(AgeLevel.TP)
            assertNull(withTimeout(5_000) { pending.await() })
        } finally { close(gate) }
    }
}
