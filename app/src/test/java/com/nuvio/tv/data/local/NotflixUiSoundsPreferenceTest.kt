package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.domain.model.AppAppearance
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M44: ajuste de sonidos de interfaz sobre el DataStore real de apariencia. */
class NotflixUiSoundsPreferenceTest {
    private val dir = Files.createTempDirectory("notflix-ui-sounds-").toFile()
    private val factories = mutableListOf<ProfileDataStoreFactory>()

    private fun factory(folder: File = dir): ProfileDataStoreFactory {
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns folder
        every { context.applicationContext } returns context
        return ProfileDataStoreFactory(context).also { factories += it }
    }

    private fun cap(store: NotflixAppearanceStore, id: Int = 2) =
        requireNotNull(store.contextFor(id, true, setOf(1, 2)))

    private suspend fun close(factory: ProfileDataStoreFactory) {
        val cache = ProfileDataStoreFactory::class.java.getDeclaredField("cache")
            .apply { isAccessible = true }.get(factory) as MutableMap<*, *>
        cache.values.toList().forEach { scoped ->
            (scoped!!::class.java.getDeclaredField("job").apply { isAccessible = true }.get(scoped) as Job).cancelAndJoin()
        }
        cache.clear()
    }

    @After fun cleanup() = runBlocking {
        factories.forEach { close(it) }
        dir.deleteRecursively()
        Unit
    }

    @Test fun `ui sounds default to on without writing the key`() = runBlocking {
        val stores = factory()
        val store = NotflixAppearanceStore(stores)
        val read = store.observe(cap(store)).first()
        assertTrue(read.uiSoundsEnabled)
        assertTrue(read.uiSoundsOptionsValid)
        assertTrue(read.introSoundEnabled)
        assertTrue(read.previewEnabled)
        val prefs = stores.get(2, NotflixAppearanceStore.FEATURE)
        assertNull(prefs.data.first()[booleanPreferencesKey(NotflixAppearanceStore.UI_SOUNDS_ENABLED)])
    }

    @Test fun `corrupt value reads as on and leaves the other settings intact`() = runBlocking {
        val stores = factory()
        val store = NotflixAppearanceStore(stores)
        val capability = cap(store)
        store.select(capability, AppAppearance.NOTFLIX).getOrThrow()
        store.selectHomeMotion(capability, true).getOrThrow()
        store.selectIntroEnabled(capability, false).getOrThrow()
        val disk = stores.get(2, NotflixAppearanceStore.FEATURE)
        disk.edit { it[stringPreferencesKey(NotflixAppearanceStore.UI_SOUNDS_ENABLED)] = "true" }
        val read = store.observe(capability).first()
        assertTrue(read.uiSoundsEnabled)
        assertFalse(read.uiSoundsOptionsValid)
        assertTrue(read.reduceHomeMotion)
        assertFalse(read.introEnabled)
        assertTrue(read.introSoundEnabled)
        assertTrue(read.previewEnabled)
        assertEquals("true", disk.data.first()[stringPreferencesKey(NotflixAppearanceStore.UI_SOUNDS_ENABLED)])
    }

    @Test fun `chosen value persists per profile and through reopen`() = runBlocking {
        val first = factory()
        val store = NotflixAppearanceStore(first)
        store.selectUiSoundsEnabled(cap(store), false).getOrThrow()
        assertFalse(store.observe(cap(store)).first().uiSoundsEnabled)
        assertTrue(store.observe(cap(store, 1)).first().uiSoundsEnabled)
        close(first)
        val reopened = NotflixAppearanceStore(factory())
        assertFalse(reopened.observe(cap(reopened)).first().uiSoundsEnabled)
        assertTrue(reopened.observe(cap(reopened, 1)).first().uiSoundsEnabled)
    }
}
