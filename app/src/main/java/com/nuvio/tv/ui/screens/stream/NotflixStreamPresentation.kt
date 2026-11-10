@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.stream

import androidx.compose.foundation.background
import com.nuvio.tv.ui.appearance.NotflixWordmark
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nuvio.tv.R
import com.nuvio.tv.ui.appearance.NotflixVisualSpec

/** Slots receive the existing screen controls; this file owns no stream state or IO. */
@Composable
internal fun NotflixStreamStage(
    isEpisode: Boolean,
    onBack: () -> Unit,
    left: @Composable (Modifier) -> Unit,
    right: @Composable (Modifier) -> Unit
) {
    val backLabel = stringResource(R.string.action_back)
    Box(Modifier.fillMaxSize().testTag("notflix_stream_stage")) {
        NotflixWordmark(height = 15.dp, modifier = Modifier.padding(start = 34.dp, top = 16.dp))
        Row(Modifier.fillMaxSize().padding(start = if (isEpisode) 80.dp else 64.dp,
            end = 34.dp, top = 52.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            left(Modifier.weight(1f).fillMaxHeight())
            right(Modifier.width(300.dp).fillMaxHeight())
        }
        Card(onClick = onBack,
            modifier = Modifier.padding(start = 20.dp, top = 72.dp).size(34.dp).testTag("notflix_stream_back")
                .semantics { contentDescription = backLabel },
            colors = CardDefaults.colors(containerColor = Color.White.copy(alpha = .04f),
                focusedContainerColor = Color.White.copy(alpha = .12f)),
            shape = CardDefaults.shape(RoundedCornerShape(4.dp)),
            border = CardDefaults.border(focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(4.dp))),
            scale = CardDefaults.scale(focusedScale = 1f)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("‹", color = Color.White, fontSize = 24.sp,
                    modifier = Modifier.testTag("notflix_stream_back_glyph"))
            }
        }
    }
}

@Composable
internal fun NotflixStreamPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(modifier.background(NotflixVisualSpec.Surface.copy(alpha = .82f), shape)
        .border(1.dp, Color.White.copy(alpha = .10f), shape).padding(8.dp)
        .testTag("notflix_stream_panel"), content = content)
}

@Composable
internal fun NotflixStreamHeading(
    title: String,
    logo: ImageRequest?,
    onLogoError: () -> Unit,
    episodeLabel: String?,
    episodeName: String?,
    runtime: Int?,
    movieInfo: String?,
    modifier: Modifier
) {
    Column(modifier.padding(bottom = 34.dp).testTag("notflix_stream_heading"),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start) {
        if (logo != null) {
            AsyncImage(logo, contentDescription = title, onError = { onLogoError() },
                contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth().height(64.dp).testTag("notflix_stream_logo"))
        } else {
            Text(title, fontFamily = NotflixVisualSpec.BodyFont, fontWeight = FontWeight.Bold,
                fontSize = 32.sp, lineHeight = 36.sp, color = NotflixVisualSpec.Text,
                maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("notflix_stream_title"))
        }
        Spacer(Modifier.height(20.dp))
        if (episodeLabel != null) {
            Text(episodeLabel, fontFamily = NotflixVisualSpec.BodyFont, fontSize = 12.sp, color = NotflixVisualSpec.Secondary)
            episodeName?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontFamily = NotflixVisualSpec.BodyFont, fontSize = 18.sp, color = NotflixVisualSpec.Text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            runtime?.let {
                val label = if (it >= 60) "${it / 60}h" + if (it % 60 > 0) " ${it % 60}m" else "" else "${it}m"
                Spacer(Modifier.height(8.dp))
                Text(label, fontFamily = NotflixVisualSpec.BodyFont, fontSize = 12.sp, color = NotflixVisualSpec.Secondary)
            }
        } else if (!movieInfo.isNullOrBlank()) {
            Text(movieInfo, fontFamily = NotflixVisualSpec.BodyFont, fontSize = 12.sp, color = NotflixVisualSpec.Secondary)
        }
    }
}
