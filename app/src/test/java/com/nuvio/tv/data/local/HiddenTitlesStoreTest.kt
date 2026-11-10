package com.nuvio.tv.data.local

import com.nuvio.tv.domain.repository.HiddenTitlesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenTitlesStoreTest {
    /** Misma semántica que el almacén real, aislada por perfil para la prueba JVM. */
    private class InMemoryHiddenTitles : HiddenTitlesRepository {
        private val profiles = mutableMapOf<Int, MutableStateFlow<Set<String>>>()

        private fun store(profileId: Int) = profiles.getOrPut(profileId) { MutableStateFlow(emptySet()) }
        override fun observe(profileId: Int): Flow<Set<String>> = store(profileId)
        override suspend fun hide(profileId: Int, key: String) {
            store(profileId).update { it + key }
        }
        override suspend fun show(profileId: Int, key: String) {
            store(profileId).update { it - key }
        }
    }

    @Test
    fun `hidden titles are scoped to their profile`() = runBlocking {
        val store = InMemoryHiddenTitles()
        store.hide(1, hiddenTitleKey("movie", "tt1"))

        assertTrue(hiddenTitleKey("movie", "tt1") in store.observe(1).first())
        assertFalse(hiddenTitleKey("movie", "tt1") in store.observe(2).first())
    }

    @Test
    fun `showing again removes the title and undo returns it`() = runBlocking {
        val store = InMemoryHiddenTitles()
        val key = hiddenTitleKey("series", "tt2")
        store.hide(3, key)
        assertTrue(key in store.observe(3).first())

        store.show(3, key)
        assertFalse(key in store.observe(3).first())
    }

    @Test
    fun `movie and series with the same id are different hidden keys`() {
        assertEquals("movie:tt1", hiddenTitleKey("movie", "tt1"))
        assertEquals("series:tt1", hiddenTitleKey("series", "tt1"))
        assertTrue(hiddenTitleKey("movie", "tt1") != hiddenTitleKey("series", "tt1"))
    }
}
