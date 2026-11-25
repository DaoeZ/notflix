package com.nuvio.tv.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.nuvio.tv.core.profile.ProfileManager
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NotflixTrailerSoundStoreTest {
    private class Memory : DataStore<Preferences> {
        override val data = MutableStateFlow<Preferences>(preferencesOf(stringPreferencesKey("untouched") to "es"))
        var beforeEdit: suspend () -> Unit = {}
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            beforeEdit(); return transform(data.value).also { data.value = it }
        }
    }
    private val profile = MutableStateFlow(1)
    private val stores = java.util.concurrent.ConcurrentHashMap<Int, Memory>()
    private val factory = mockk<ProfileDataStoreFactory> {
        every { get(any(), "layout_settings") } answers { stores.computeIfAbsent(firstArg()) { Memory() } }
    }
    private fun store() = LayoutPreferenceDataStore(factory, mockk<ProfileManager> { every { activeProfileId } returns profile })
    private val muted = booleanPreferencesKey("focused_poster_backdrop_trailer_muted")

    @Test fun explicitWritesUseTheExistingKeyAndCapturedProfileWithoutMigratingOnEntry() = runTest {
        val store = store()
        assertTrue(stores.values.all { it.data.value[muted] == null })
        assertTrue(store.setFocusedPosterBackdropTrailerMuted(false, 1) { true })
        profile.value = 2
        assertTrue(store.setFocusedPosterBackdropTrailerMuted(true, 1) { true })
        assertNull(stores[2]?.data?.value?.get(muted))
        assertEquals(true, stores.getValue(1).data.value[muted])
        assertEquals("es", stores.getValue(1).data.value[stringPreferencesKey("untouched")])
        assertEquals(2, stores.getValue(1).data.value.asMap().size)
        store.setFocusedPosterBackdropTrailerMuted(false) // original Nuvio setter still follows active profile
        assertEquals(false, stores.getValue(2).data.value[muted])
    }

    @Test fun authorizationIsRecheckedInsideTheQueuedEditAndRejectsALateSession() = runTest {
        val store = store(); val memory = Memory(); stores[1] = memory
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        memory.beforeEdit = { entered.complete(Unit); release.await() }
        var authorized = true
        val write = async { store.setFocusedPosterBackdropTrailerMuted(false, 1) { authorized } }
        entered.await(); profile.value = 2; authorized = false; release.complete(Unit)
        assertFalse(write.await())
        assertNull(memory.data.value[muted]); assertNull(stores[2]?.data?.value?.get(muted))
    }
}
