package com.nuvio.tv.ui.screens.settings

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.server.DeviceIpAddress
import com.nuvio.tv.core.util.withAppLocale
import com.nuvio.tv.data.local.NotflixIntegrationCredentials
import com.nuvio.tv.data.local.NotflixIntegrationStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotflixProviderSettingsLanguageTest {
    private val base = mockk<Context>()
    private val italian = mockk<Context>()
    private val french = mockk<Context>()
    private var appContext = italian
    private lateinit var model: NotflixProviderSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkStatic("com.nuvio.tv.core.util.AppLocaleContextKt")
        every { base.withAppLocale() } answers { appContext }
        every { italian.getString(any()) } answers { "it:${firstArg<Int>()}" }
        every { french.getString(any()) } answers { "fr:${firstArg<Int>()}" }
        val store = mockk<NotflixIntegrationStore> {
            every { read() } returns NotflixIntegrationCredentials()
            every { values } returns MutableStateFlow(NotflixIntegrationCredentials())
        }
        mockkObject(DeviceIpAddress)
        every { DeviceIpAddress.get(base) } returns null
        model = NotflixProviderSettingsViewModel(store, base)
    }

    @After
    fun tearDown() = runTest {
        model.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
        unmockkObject(DeviceIpAddress)
        unmockkStatic("com.nuvio.tv.core.util.AppLocaleContextKt")
        Dispatchers.resetMain()
    }

    @Test
    fun phoneLabelsUseAppLanguageRatherThanSystemContext() {
        val labels = model.phonePageLabels()
        assertEquals("it:${R.string.notflix_keys_page_title}", labels["title"])
        assertEquals("it:${R.string.notflix_keys_page_save}", labels["save"])
        assertTrue(labels.values.all { it.startsWith("it:") })
        verify(exactly = 0) { base.getString(any()) }
    }

    @Test
    fun phoneRequestsResolveLanguageAgainAfterItChanges() {
        val first = model.phonePageLabels()
        appContext = french
        val second = model.phonePageLabels()
        assertEquals(first.keys, second.keys)
        assertTrue(second.values.all { it.startsWith("fr:") })
        verify(exactly = 2) { base.withAppLocale() }
    }

    @Test
    fun unavailableNetworkMessageAlsoUsesAppLanguageWithoutStartingServer() {
        model.startPhoneEntry()
        assertEquals("it:${R.string.notflix_provider_phone_error}", model.phoneError.value)
        assertEquals(null, model.phoneEntry.value)
        verify(exactly = 0) { base.getString(any()) }
    }
}
