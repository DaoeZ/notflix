@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.wikipedia.WikipediaExtract
import com.nuvio.tv.data.wikipedia.WikipediaIdentity
import com.nuvio.tv.data.wikipedia.WikipediaPolicy
import com.nuvio.tv.ui.appearance.NotflixVisualSpec
import com.nuvio.tv.ui.components.NuvioDialog
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/** Plain attributed text only. No HTML, active links, fetch, or initial focus request. */
@Composable
internal fun NotflixWikipediaBlock(extract: WikipediaExtract, modifier: Modifier = Modifier) {
    if (!extract.validFor(WikipediaIdentity(extract.imdbId, extract.type))) return
    val sourceFocus = remember(extract.imdbId, extract.revisionId) { FocusRequester() }
    val infoFocus = remember { FocusRequester() }
    var showSource by remember(extract.imdbId, extract.revisionId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun close() {
        showSource = false
        scope.launch { withFrameNanos { }; runCatching { sourceFocus.requestFocus() } }
    }
    val date = remember(extract.fetchedAtEpochMs) { Instant.ofEpochMilli(extract.fetchedAtEpochMs).atZone(ZoneOffset.UTC).toLocalDate().toString() }
    Column(modifier.fillMaxWidth().padding(horizontal = 64.dp, vertical = 24.dp).testTag("notflix-wikipedia"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.notflix_wikipedia_title), fontFamily = NotflixVisualSpec.BodyFont,
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NotflixVisualSpec.Text)
        Text(stringResource(if (extract.language == "en") R.string.notflix_wikipedia_english else R.string.notflix_wikipedia_spanish),
            fontFamily = NotflixVisualSpec.BodyFont, fontSize = 12.sp, color = NotflixVisualSpec.Secondary)
        extract.paragraphs.forEachIndexed { index, paragraph ->
            Text(paragraph, fontFamily = NotflixVisualSpec.BodyFont, fontSize = 14.sp, lineHeight = 20.sp,
                color = NotflixVisualSpec.Text, modifier = Modifier.testTag("notflix-wikipedia-paragraph-$index"))
        }
        Text(stringResource(R.string.notflix_wikipedia_credit), fontFamily = NotflixVisualSpec.BodyFont,
            fontSize = 11.sp, color = NotflixVisualSpec.Secondary, modifier = Modifier.testTag("notflix-wikipedia-credit"))
        Button(onClick = { showSource = true }, modifier = Modifier.focusRequester(sourceFocus).testTag("notflix-wikipedia-source")) {
            Text(stringResource(R.string.notflix_wikipedia_source))
        }
    }
    if (showSource) {
        val infoScroll = rememberScrollState()
        var infoFocused by remember { mutableStateOf(false) }
        // Resolve the caller's locale before entering the separate dialog window.
        val credit = stringResource(R.string.notflix_wikipedia_credit)
        val section = stringResource(R.string.notflix_wikipedia_section, extract.section)
        val revision = stringResource(R.string.notflix_wikipedia_revision, extract.revisionId.toString())
        val fetched = stringResource(R.string.notflix_wikipedia_fetched, date)
        val changes = stringResource(R.string.notflix_wikipedia_changes)
        val closeLabel = stringResource(R.string.notflix_wikipedia_close)
        NuvioDialog(onDismiss = { close() }, title = stringResource(R.string.notflix_wikipedia_source), width = 620.dp) {
            Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(infoScroll)
                .border(1.dp, if (infoFocused) NotflixVisualSpec.Text else NotflixVisualSpec.Secondary.copy(alpha = .3f))
                .onFocusChanged { infoFocused = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    val up = event.key == Key.DirectionUp
                    val down = event.key == Key.DirectionDown
                    val canScroll = if (up) infoScroll.value > 0 else down && infoScroll.value < infoScroll.maxValue
                    if (event.type == KeyEventType.KeyDown && canScroll) {
                        scope.launch { infoScroll.scrollBy(if (up) -120f else 120f) }; true
                    } else false
                }.focusRequester(infoFocus).focusable().padding(8.dp).testTag("notflix-wikipedia-attribution"),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WikipediaAttributionText(credit)
                WikipediaAttributionText(extract.articleTitle)
                WikipediaAttributionText(section)
                WikipediaAttributionText(extract.articleUrl)
                WikipediaAttributionText(revision)
                WikipediaAttributionText(requireNotNull(extract.revisionUrl()))
                WikipediaAttributionText(fetched)
                WikipediaAttributionText("CC BY-SA 4.0")
                WikipediaAttributionText(WikipediaPolicy.LICENSE_URL)
                WikipediaAttributionText(changes)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { close() }, modifier = Modifier.testTag("notflix-wikipedia-close")) {
                Text(closeLabel)
            }
        }
        LaunchedEffect(Unit) { infoFocus.requestFocusAfterFrames() }
    }
}

@Composable
private fun WikipediaAttributionText(value: String) {
    Text(value, color = NotflixVisualSpec.Text, fontFamily = NotflixVisualSpec.BodyFont,
        fontSize = 14.sp, lineHeight = 20.sp)
}
