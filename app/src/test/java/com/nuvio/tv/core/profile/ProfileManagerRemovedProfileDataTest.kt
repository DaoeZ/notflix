package com.nuvio.tv.core.profile

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.tv.data.local.ProfileDataStore
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import com.nuvio.tv.domain.model.UserProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProfileManagerRemovedProfileDataTest {

    private lateinit var tempDir: File
    private lateinit var factory: ProfileDataStoreFactory
    private lateinit var credentialStore: ProfileScopedCredentialStore
    private lateinit var manager: ProfileManager
    private val storedProfiles = MutableStateFlow(listOf(profile(1), profile(2), profile(3)))
    private val pendingIds = mutableSetOf<String>()

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("profile_manager_test").toFile()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putStringSet("ids", any()) } answers {
            pendingIds.clear(); pendingIds += secondArg<Set<String>>(); editor
        }
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { getStringSet("ids", any()) } answers { pendingIds.toSet() }
            every { edit() } returns editor
        }
        val context = mockk<Context>(relaxed = true) {
            every { filesDir } returns tempDir
            every { getSharedPreferences(any(), any()) } returns prefs
            every { getString(any(), any()) } returns "Perfil"
        }
        val profileDataStore = mockk<ProfileDataStore>(relaxed = true) {
            every { profilesList } returns storedProfiles
            every { activeProfileId } returns flowOf(1)
            every { hasEverSelectedProfile } returns flowOf(true)
            every { rememberLastProfileEnabled } returns flowOf(false)
            every { confirmExitEnabled } returns flowOf(false)
            every { startupSplashEnabled } returns flowOf(true)
        }
        coEvery { profileDataStore.replaceAllProfiles(any()) } answers { storedProfiles.value = firstArg() }
        coEvery { profileDataStore.deleteProfile(any()) } answers {
            storedProfiles.value = storedProfiles.value.filterNot { it.id == firstArg<Int>() }
        }
        coEvery { profileDataStore.upsertProfile(any()) } answers { storedProfiles.value += firstArg<UserProfile>() }
        factory = mockk(relaxed = true)
        credentialStore = mockk(relaxed = true)
        manager = ProfileManager(profileDataStore, factory, setOf(credentialStore), context)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `profiles removed on another device lose their local data`() = runTest {
        val removed = profileFiles(2)
        val kept = profileFiles(3) + file("datastore/library_preferences.preferences_pb")

        manager.replaceAllProfiles(listOf(profile(1), profile(3)))

        removed.forEach { assertFalse("${it.path} should be deleted", it.exists()) }
        kept.forEach { assertTrue("${it.path} should be kept", it.exists()) }
        verify(exactly = 1) { credentialStore.removeProfile(2) }
        verify(exactly = 0) { credentialStore.removeProfile(3) }
        coVerify(exactly = 1) { factory.clearProfile(2) }
    }

    @Test
    fun `a profile created here survives a server pull that does not have it yet`() = runTest {
        manager.profiles.first { it.size == 3 }
        val created = requireNotNull(manager.createProfile("Kids", "#E50914"))

        assertTrue(manager.replaceAllProfiles(listOf(profile(1), profile(2), profile(3))))
        assertTrue(storedProfiles.value.any { it.id == created.id })
        verify(exactly = 0) { credentialStore.removeProfile(created.id) }

        // Once the server lists it, it is no longer pending and nothing needs pushing.
        assertFalse(manager.replaceAllProfiles(storedProfiles.value))
        assertEquals(emptySet<Int>(), manager.pendingCreatedProfileIds())
    }

    @Test
    fun `profiles added on another device are not treated as deleted`() = runTest {
        manager.replaceAllProfiles(listOf(profile(1), profile(2), profile(3), profile(4)))

        verify(exactly = 1) { factory.markProfileCreated(4) }
        coVerify(exactly = 1) { factory.prepareAppearanceProfile(4) }
        verify(exactly = 0) { credentialStore.removeProfile(any()) }
        coVerify(exactly = 0) { factory.clearProfile(any()) }
    }

    @Test
    fun `deleting a profile removes its shadow copies and Continue Watching cache`() = runTest {
        manager.profiles.first { profiles -> profiles.any { it.id == 2 } }
        val removed = profileFiles(2)
        val kept = profileFiles(3)

        manager.deleteProfile(2)

        removed.forEach { assertFalse("${it.path} should be deleted", it.exists()) }
        kept.forEach { assertTrue("${it.path} should be kept", it.exists()) }
        verify(exactly = 1) { credentialStore.removeProfile(2) }
    }

    @Test
    fun `new local profile prepares only its own appearance before publication`() = runTest {
        manager.profiles.first { it.size == 3 }
        val created = manager.createProfile("New", "#1E88E5")
        assertTrue(created?.id == 4)
        coVerify(exactly = 1) { factory.prepareAppearanceProfile(4) }
        coVerify(exactly = 0) { factory.prepareAppearanceProfile(1) }
    }

    private fun profileFiles(id: Int) = listOf(
        "datastore/library_preferences_p$id.preferences_pb",
        "datastore/library_preferences_p$id.preferences_pb.bak",
        "datastore/trakt_auth_store_p$id.preferences_pb.bak",
        "cw_enrichment/inprogress_$id.json",
        "cw_enrichment/nextup_$id.json",
        "plugin_code_p$id/scraper.js"
    ).map(::file)

    private fun file(path: String) = File(tempDir, path).apply {
        parentFile?.mkdirs()
        writeText("x")
    }

    private fun profile(id: Int) = UserProfile(id = id, name = "Profile $id", avatarColorHex = "#1E88E5")
}
