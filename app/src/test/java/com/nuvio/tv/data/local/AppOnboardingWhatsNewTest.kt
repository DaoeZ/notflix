package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppOnboardingWhatsNewTest {
    @get:Rule val folder = TemporaryFolder()
    private val jobs = mutableListOf<Job>()

    private fun store(file: File): AppOnboardingDataStore {
        val job = SupervisorJob().also { jobs += it }
        return AppOnboardingDataStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { file }
        ))
    }

    @After fun close() = runBlocking {
        jobs.forEach { it.cancelAndJoin() }
    }

    @Test fun `device version survives reopening and leaves QR onboarding intact`() = runBlocking {
        val file = File(folder.root, "onboarding.preferences_pb")
        val first = store(file)
        assertEquals(0, first.notflixWhatsNewVersionCode.first())
        first.setHasSeenAuthQrOnFirstLaunch(true)
        first.setNotflixWhatsNewVersionCode(120)
        jobs.last().cancelAndJoin()

        val reopened = store(file)
        assertEquals(120, reopened.notflixWhatsNewVersionCode.first())
        assertTrue(reopened.hasSeenAuthQrOnFirstLaunch.first())
    }

    @Test fun `late writes and downgrades cannot lower the seen version`() = runBlocking {
        val device = store(File(folder.root, "onboarding.preferences_pb"))
        device.setNotflixWhatsNewVersionCode(121)
        device.setNotflixWhatsNewVersionCode(120)
        device.setNotflixWhatsNewVersionCode(0)
        device.setNotflixWhatsNewVersionCode(-1)
        assertEquals(121, device.notflixWhatsNewVersionCode.first())
    }

    @Test fun `a separate device starts without a seen version`() = runBlocking {
        val first = store(File(folder.root, "first.preferences_pb"))
        first.setNotflixWhatsNewVersionCode(120)
        val second = store(File(folder.root, "second.preferences_pb"))
        assertEquals(0, second.notflixWhatsNewVersionCode.first())
    }
}
