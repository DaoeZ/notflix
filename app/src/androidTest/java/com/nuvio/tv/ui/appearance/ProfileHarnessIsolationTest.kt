package com.nuvio.tv.ui.appearance

import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.data.local.ProfileDataStore
import com.nuvio.tv.data.local.ThemeDataStore
import com.nuvio.tv.domain.model.AppTheme
import com.nuvio.tv.domain.model.UserProfile
import com.squareup.moshi.Moshi
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class ProfileHarnessIsolationTest {
    @Test fun delegatedContextsAliasButExplicitStoresKeepProfilesPreferencesAndFilesIndependent() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val moshi = Moshi.Builder().build()
        val applicationProfiles = ProfileDataStore(base, moshi)
        val expectedExternal = InstrumentationRegistry.getArguments().getString("nf_external_profile")?.toInt()
        val externalBefore = applicationProfiles.activeProfileId.first() to applicationProfiles.confirmExitEnabled.first()
        expectedExternal?.let { assertEquals("External profile precondition", it, externalBefore.first) }
        IsolatedProfileHarness(base).use { a -> IsolatedProfileHarness(base).use { b ->
            // Read-only discriminant of the old harness: changing Context.filesDir
            // cannot change a DataStore instance already cached by its top-level delegate.
            val legacyA = ProfileDataStore(a.context, moshi)
            val legacyB = ProfileDataStore(b.context, moshi)
            val field = ProfileDataStore::class.java.getDeclaredField("dataStore").apply { isAccessible = true }
            assertSame(field.get(legacyA), field.get(legacyB))
            assertSame(field.get(applicationProfiles), field.get(legacyA))
            assertNotSame(field.get(legacyA), a.dataStore)
            assertNotSame(a.dataStore, b.dataStore)
            val before = legacyA.activeProfileId.first() to legacyA.confirmExitEnabled.first()
            assertEquals(before.first, legacyB.activeProfileId.first())
            a.profiles.upsertProfile(UserProfile(id = 2, name = "Fixture A", avatarColorHex = "#112233"))
            a.profiles.setActiveProfile(2)
            a.profiles.setConfirmExitEnabled(false)
            b.profiles.setActiveProfile(1)
            b.profiles.setConfirmExitEnabled(true)
            withTimeout(10_000) { a.manager.activeProfileId.first { it == 2 }; b.manager.activeProfileReady.first { it } }
            val themeA = ThemeDataStore(a.factory, a.manager)
            val themeB = ThemeDataStore(b.factory, b.manager)
            themeA.setTheme(AppTheme.OCEAN)
            themeB.setTheme(AppTheme.WHITE)
            assertEquals(2, a.profiles.activeProfileId.first())
            assertEquals(1, b.profiles.activeProfileId.first())
            assertFalse(a.profiles.confirmExitEnabled.first())
            assertTrue(b.profiles.confirmExitEnabled.first())
            assertEquals(AppTheme.OCEAN, themeA.getThemeForProfile(2))
            assertEquals(AppTheme.WHITE, themeB.getThemeForProfile(1))
            assertTrue(a.profiles.profilesList.first().any { it.name == "Fixture A" })
            assertFalse(b.profiles.profilesList.first().any { it.name == "Fixture A" })
            assertNotEquals(a.profileFile.canonicalPath, b.profileFile.canonicalPath)
            assertFalse(a.profileFile.canonicalPath.startsWith(base.filesDir.canonicalPath + File.separator))
            assertTrue(a.profileFile.isFile); assertTrue(b.profileFile.isFile)
            assertEquals(before, legacyA.activeProfileId.first() to legacyA.confirmExitEnabled.first())
            assertEquals(before, legacyB.activeProfileId.first() to legacyB.confirmExitEnabled.first())
            assertEquals(externalBefore, applicationProfiles.activeProfileId.first() to applicationProfiles.confirmExitEnabled.first())
            android.util.Log.i("NF_M5_ISOLATION", "externalProfile=${externalBefore.first} unchanged; delegatedContextsAlias=true; explicitProfiles=2,1; filesIndependent=true")
            Unit
        } }
    }
}
