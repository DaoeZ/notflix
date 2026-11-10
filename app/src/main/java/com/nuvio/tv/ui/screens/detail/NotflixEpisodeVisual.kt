@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import com.nuvio.tv.ui.appearance.NotflixVisualSpec
import com.nuvio.tv.R

internal val NotflixEpisodeImageBlurApplied = SemanticsPropertyKey<Boolean>("NotflixEpisodeImageBlurApplied")

/** Pixels only; the wrapping EpisodeCard still owns official keys, options and restoration. */
@Composable
internal fun NotflixEpisodeVisual(
    image: ImageRequest,
    title: String,
    episodeNumber: Int?,
    date: String,
    runtime: String?,
    rating: String?,
    watched: Boolean,
    unavailable: String?,
    showProgress: Boolean,
    progress: Float
) {
    Box(Modifier.fillMaxWidth().height(78.dp).testTag("notflix_episode_item").semantics {
        this[NotflixEpisodeImageBlurApplied] = image.transformations.any { it is com.nuvio.tv.ui.util.BlurTransformation }
    }) {
        Row(Modifier.fillMaxSize().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            val fallback = ColorPainter(NotflixVisualSpec.Surface)
            AsyncImage(image, contentDescription = null, contentScale = ContentScale.Crop,
                placeholder = fallback, error = fallback, fallback = fallback,
                modifier = Modifier.width(104.dp).height(58.dp).clip(RoundedCornerShape(4.dp)).testTag("notflix_episode_thumbnail"))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(listOfNotNull(episodeNumber?.let { "$it." }, title).joinToString(" "),
                    fontFamily = NotflixVisualSpec.BodyFont, fontSize = 12.sp, lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold, color = NotflixVisualSpec.Text, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(date.takeIf { it.isNotBlank() }, runtime, rating?.let { "IMDb $it" }).joinToString(" · "),
                    fontFamily = NotflixVisualSpec.BodyFont, fontSize = 9.sp, color = NotflixVisualSpec.Secondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (watched || unavailable != null) Text(listOfNotNull(if (watched) stringResource(R.string.episodes_cd_watched) else null, unavailable).joinToString(" · "),
                    fontSize = 9.sp, color = NotflixVisualSpec.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (showProgress) Box(Modifier.align(Alignment.BottomStart).padding(horizontal = 6.dp)
            .fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(NotflixVisualSpec.Accent))
    }
}
