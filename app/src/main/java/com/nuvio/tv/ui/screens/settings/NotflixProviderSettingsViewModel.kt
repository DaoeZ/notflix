package com.nuvio.tv.ui.screens.settings

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.core.server.DeviceIpAddress
import com.nuvio.tv.core.server.NotflixKeysConfigServer
import com.nuvio.tv.data.local.NotflixIntegrationCredentials
import com.nuvio.tv.data.local.NotflixIntegrationStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The QR shown on the TV while the phone page is open. */
data class NotflixPhoneEntry(val url: String, val qrBitmap: Bitmap)

@HiltViewModel
class NotflixProviderSettingsViewModel @Inject constructor(
    private val store: NotflixIntegrationStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val mutableValues = MutableStateFlow(NotflixIntegrationCredentials())
    val values: StateFlow<NotflixIntegrationCredentials> = mutableValues.asStateFlow()

    private val mutablePhoneEntry = MutableStateFlow<NotflixPhoneEntry?>(null)
    val phoneEntry: StateFlow<NotflixPhoneEntry?> = mutablePhoneEntry.asStateFlow()

    private val mutablePhoneError = MutableStateFlow<String?>(null)
    val phoneError: StateFlow<String?> = mutablePhoneError.asStateFlow()

    private var phoneServer: NotflixKeysConfigServer? = null

    init {
        viewModelScope.launch {
            mutableValues.value = withContext(Dispatchers.IO) { store.read() }
            store.values.collectLatest { mutableValues.value = it }
        }
    }

    fun saveTmdbApiKey(value: String) = update { it.copy(tmdbApiKey = value) }

    fun clearTmdbApiKey() = update { it.copy(tmdbApiKey = "") }

    fun saveIntroDbBaseUrl(value: String) = update { it.copy(introDbBaseUrl = value) }

    fun clearIntroDbBaseUrl() = update { it.copy(introDbBaseUrl = "") }

    /** Starts the phone page for this session only. The token in the QR is new each time. */
    fun startPhoneEntry() {
        stopPhoneEntry()
        val ip = DeviceIpAddress.get(context)
        if (ip == null) {
            mutablePhoneError.value = context.getString(R.string.notflix_provider_phone_error)
            return
        }
        val token = NotflixKeysConfigServer.newToken()
        val server = NotflixKeysConfigServer.startOnAvailablePort(
            token = token,
            currentProvider = { store.read() },
            onSave = { credentials -> viewModelScope.launch(Dispatchers.IO) { store.save(credentials) } },
            labels = { phonePageLabels() }
        )
        if (server == null) {
            mutablePhoneError.value = context.getString(R.string.notflix_provider_phone_error)
            return
        }
        phoneServer = server
        val url = "http://$ip:${server.listeningPort}/?t=$token"
        mutablePhoneError.value = null
        mutablePhoneEntry.value = NotflixPhoneEntry(url, QrCodeGenerator.generate(url, 512))
    }

    fun stopPhoneEntry() {
        phoneServer?.stop()
        phoneServer = null
        mutablePhoneEntry.value = null
    }

    override fun onCleared() {
        stopPhoneEntry()
        super.onCleared()
    }

    private fun phonePageLabels(): Map<String, String> = mapOf(
        "title" to context.getString(R.string.notflix_keys_page_title),
        "subtitle" to context.getString(R.string.notflix_keys_page_subtitle),
        "tmdb" to context.getString(R.string.notflix_keys_page_tmdb),
        "traktId" to context.getString(R.string.notflix_keys_page_trakt_id),
        "traktSecret" to context.getString(R.string.notflix_keys_page_trakt_secret),
        "introDb" to context.getString(R.string.notflix_keys_page_introdb),
        "clear" to context.getString(R.string.notflix_keys_page_clear),
        "show" to context.getString(R.string.notflix_keys_page_show),
        "save" to context.getString(R.string.notflix_keys_page_save),
        "saved" to context.getString(R.string.notflix_keys_page_saved),
        "error" to context.getString(R.string.notflix_keys_page_error),
        "forbidden" to context.getString(R.string.notflix_keys_page_forbidden),
        "none" to context.getString(R.string.notflix_keys_page_none),
        "current" to context.getString(R.string.notflix_keys_page_current)
    )

    private fun update(transform: (NotflixIntegrationCredentials) -> NotflixIntegrationCredentials) {
        viewModelScope.launch {
            runCatching { store.save(transform(store.read())) }
                .onFailure { Log.w("NotflixProviderSettings", "Unable to save provider settings", it) }
        }
    }
}
