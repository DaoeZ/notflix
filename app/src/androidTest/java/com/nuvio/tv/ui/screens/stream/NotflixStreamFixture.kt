package com.nuvio.tv.ui.screens.stream

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.core.streams.StreamBadgeSettings
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.ui.appearance.*
import com.nuvio.tv.ui.components.SourceChipItem
import com.nuvio.tv.ui.components.SourceChipStatus

internal fun fixtureStream(number: Int, addon: String = "Addon A") = Stream(
    name = "Synthetic source $number", title = "Synthetic file $number", description = "Synthetic source details $number",
    url = "https://fixture.invalid/source-$number.mp4", ytId = null, infoHash = null, fileIdx = null,
    externalUrl = null, behaviorHints = null, addonName = addon, addonLogo = null)

/** Synthetic callbacks only. No Hilt, repositories, stores, actual resolution or player. */
internal class StreamFixtureState(count: Int = 8) {
    lateinit var input: android.view.View
    var active by mutableStateOf(true)
    var ui by mutableStateOf(StreamScreenUiState(isLoading = false, autoPlayDecided = true,
        title = "Synthetic title", year = "2024", genres = "Drama",
        allStreams = (1..count).map { fixtureStream(it, if (it % 2 == 0) "Addon B" else "Addon A") },
        filteredStreams = (1..count).map { fixtureStream(it, if (it % 2 == 0) "Addon B" else "Addon A") },
        availableAddons = listOf("Addon A", "Addon B"),
        sourceChips = listOf(SourceChipItem("Addon A", SourceChipStatus.SUCCESS), SourceChipItem("Addon B", SourceChipStatus.SUCCESS))))
    var badges by mutableStateOf(StreamBadgeSettings())
    var shared by mutableStateOf<SharedStream?>(null)
    var focusedIndex by mutableIntStateOf(0)
    var restore by mutableStateOf(false)
    var child by mutableStateOf(false)
    var ask by mutableStateOf(false)
    var picker by mutableStateOf(false)
    var resumeText by mutableStateOf<String?>(null)
    val selections = mutableListOf<Stream>()
    val sharedSelections = mutableListOf<Stream>()
    val filters = mutableListOf<String?>()
    val playerChoices = mutableListOf<String>()
    var backCount = 0; var refreshCount = 0; var retryCount = 0; var expansionCount = 0; var restoredCount = 0
    fun choose(stream: Stream) {
        selections += stream
        focusedIndex = ui.filteredStreams.indexOfFirst { it === stream }.coerceAtLeast(0)
        if (ask) picker = true else child = true
    }
    fun closeChild() { child = false; restore = true }
}

@Composable
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
internal fun StreamFixture(state: StreamFixtureState) {
    state.input = LocalView.current
    CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = state.active, reduceHomeMotion = true)) {
      NotflixPresentationTheme(state.active) {
        val savedSources = rememberSaveableStateHolder()
        Box(Modifier.fillMaxSize()) {
          if (!state.child) savedSources.SaveableStateProvider("sources") {
            StreamScreenContent(uiState = state.ui, onBackPress = { state.backCount++ },
                resumeText = state.resumeText, badgeSettings = state.badges, sharedStream = state.shared,
                focusedStreamIndex = state.focusedIndex, shouldRestoreFocusedStream = state.restore,
                onRestoreFocusedStreamHandled = { state.restore = false; state.restoredCount++ },
                onAddonFilterSelected = { addon ->
                    state.filters += addon
                    state.ui = state.ui.copy(selectedAddonFilter = addon,
                        filteredStreams = state.ui.allStreams.filter { addon == null || it.addonName == addon })
                },
                onRefresh = { state.refreshCount++ }, onRetry = { state.retryCount++ },
                onExpandStreams = { state.expansionCount++ },
                onSharedStreamSelected = { state.sharedSelections += it; state.child = true },
                onStreamSelected = state::choose)
          }
            if (state.picker) PlayerChoiceDialog(
                onInternalSelected = { state.playerChoices += "internal"; state.picker = false; state.child = true },
                onExternalSelected = { state.playerChoices += "external"; state.picker = false; state.child = true },
                onDismiss = { state.picker = false })
            if (state.child) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Button(onClick = state::closeChild,
                        modifier = Modifier.focusRequester(focus).testTag("synthetic-stream-child-close")) {
                        Text("Close synthetic child")
                    }
                }
            }
        }
        BackHandler { if (state.child) state.closeChild() else state.backCount++ }
      }
    }
}
