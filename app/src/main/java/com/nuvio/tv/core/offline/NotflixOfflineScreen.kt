package com.nuvio.tv.core.offline

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import com.nuvio.tv.ui.screens.ambient.LocalAmbientIdleController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.ui.appearance.NotflixVisualSpec
import com.nuvio.tv.ui.appearance.notflix.NotflixFlatButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class OfflinePairingDisplay(val address: String, val code: String, val qr: ImageBitmap)

@Composable
internal fun NotflixOfflineScreen(
    onBack: () -> Unit,
    onPlay: (OfflineVideoState) -> Unit,
    viewModel: NotflixOfflineViewModel = hiltViewModel(),
) {
    val owner by viewModel.owner.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val back = remember { FocusRequester() }
    var rows by remember(owner) { mutableStateOf<List<OfflineVideoState>>(emptyList()) }
    var error by remember(owner) { mutableStateOf(false) }
    var receiving by remember(owner) { mutableStateOf(false) }
    var sessionGeneration by remember(owner) { mutableStateOf(0) }
    var pairing by remember(owner) { mutableStateOf<OfflinePairingDisplay?>(null) }
    var expired by remember(owner) { mutableStateOf(false) }
    var reception by remember(owner) { mutableStateOf(OfflineReceiveProgress()) }
    var removeKey by remember(owner) { mutableStateOf<String?>(null) }
    LaunchedEffect(owner, receiving, lifecycle, sessionGeneration) {
        val current = owner ?: return@LaunchedEffect
        if (!receiving) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var server: NotflixOfflineReceiver? = null
            try {
                pairing = withContext(Dispatchers.IO) {
                    val (endpoint, receiver) = viewModel.openReceiver(current)
                    server = receiver
                    val qr = QrCodeGenerator.generate("notflix://offline-transfer/$endpoint/${receiver.code}")
                    OfflinePairingDisplay(endpoint, receiver.code.chunked(4).joinToString(" "), qr.asImageBitmap())
                }
                val receiver = checkNotNull(server)
                receiver.progress.collect { progress ->
                    reception = progress
                    if (progress.complete) rows = viewModel.list(current)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = true
                receiving = false
            } finally {
                pairing = null
                withContext(NonCancellable + Dispatchers.IO) { server?.close() }
            }
        }
    }
    LaunchedEffect(pairing, reception.received, reception.size, reception.complete) {
        if (pairing == null) return@LaunchedEffect
        delay(when {
            reception.complete -> 95_000
            reception.size > 0 -> 120_000
            else -> 300_000
        })
        expired = !reception.complete
        receiving = false
    }

    // While the code is shown or a video is arriving, neither Notflix's ambient mode nor the
    // system screensaver may take over: the user is busy with the phone, not the remote.
    val ambient = LocalAmbientIdleController.current
    val view = LocalView.current
    val transferActive = receiving && pairing != null
    if (transferActive) {
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
        LaunchedEffect(ambient) {
            if (ambient?.isShowing?.value == true) ambient.dismiss()
            while (true) {
                ambient?.onInteraction()
                delay(15_000)
            }
        }
    }

    LaunchedEffect(Unit) { back.requestFocus() }
    LaunchedEffect(owner) {
        val current = owner ?: return@LaunchedEffect
        while (true) {
            try {
                rows = viewModel.list(current)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = true
            }
            delay(3_000)
        }
    }

    Column(
        Modifier.fillMaxSize().background(NotflixVisualSpec.Background).padding(36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.notflix_offline_title), color = NotflixVisualSpec.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NotflixFlatButton(stringResource(R.string.action_back), onBack, Modifier.focusRequester(back), trailing = "")
            owner?.let {
                NotflixFlatButton(
                    stringResource(if (receiving && !reception.complete) R.string.action_cancel else R.string.notflix_offline_receive),
                    onClick = {
                        error = false
                        expired = false
                        if (!receiving || reception.complete) {
                            reception = OfflineReceiveProgress()
                            sessionGeneration += 1
                            receiving = true
                        } else {
                            receiving = false
                        }
                    },
                    trailing = "",
                )
                if ((pairing != null && reception.size == 0L) || expired) {
                    NotflixFlatButton(
                        stringResource(R.string.notflix_offline_new_code),
                        onClick = {
                            error = false
                            expired = false
                            reception = OfflineReceiveProgress()
                            sessionGeneration += 1
                            receiving = true
                        },
                        trailing = "",
                    )
                }
            }
        }
        pairing?.let { (address, code, qr) ->
            if (reception.size == 0L) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(qr, stringResource(R.string.notflix_offline_scan), Modifier.size(124.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.notflix_offline_scan), color = NotflixVisualSpec.Text)
                        Text(stringResource(R.string.notflix_offline_pairing, address, code), color = NotflixVisualSpec.Text)
                    }
                }
            } else {
                Text("${(reception.received.toDouble() * 100 / reception.size).toInt()}%", color = NotflixVisualSpec.Text)
            }
        }
        if (expired) Text(stringResource(R.string.notflix_offline_expired), color = NotflixVisualSpec.Text)
        if (error) Text(stringResource(R.string.notflix_offline_failed), color = NotflixVisualSpec.Text)
        if (owner == null || rows.isEmpty()) {
            Text(stringResource(if (owner == null) R.string.notflix_offline_profile else R.string.notflix_offline_empty), color = NotflixVisualSpec.Text)
        }
        val current = owner ?: return@Column
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(rows, key = { it.video.key }) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NotflixFlatButton(
                        label = row.video.title,
                        onClick = {
                            scope.launch {
                                try {
                                    viewModel.play(current, row.video.key)?.let(onPlay) ?: run { error = true }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    error = true
                                }
                            }
                        },
                        modifier = Modifier.weight(1f, fill = false),
                        trailing = if (row.playableUri != null) "▶" else "",
                        enabled = row.playableUri != null,
                    )
                    if (row.isDownloading) {
                        Text(
                            row.percent?.let { stringResource(R.string.update_downloading_progress, it) }
                                ?: stringResource(R.string.update_downloading_ellipsis),
                            color = NotflixVisualSpec.Text,
                        )
                    } else if (row.playableUri == null) {
                        Text(stringResource(R.string.notflix_offline_unavailable), color = NotflixVisualSpec.Text)
                    }
                    val confirm = removeKey == row.video.key
                    NotflixFlatButton(
                        stringResource(if (confirm) R.string.notflix_offline_confirm_remove else R.string.action_delete),
                        onClick = {
                            if (!confirm) {
                                removeKey = row.video.key
                            } else {
                                removeKey = null
                                scope.launch {
                                    try {
                                        viewModel.remove(current, row.video.key)
                                        rows = viewModel.list(current)
                                        back.requestFocus()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        error = true
                                    }
                                }
                            }
                        },
                        trailing = "",
                    )
                }
            }
        }
    }
}
