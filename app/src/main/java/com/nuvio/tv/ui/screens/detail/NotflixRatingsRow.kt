@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.appearance.NotflixVisualSpec

/** Read-only wrapping labels. No focus target, maxItems truncation, network or settings writes. */
@Composable
internal fun NotflixRatingsRow(ratings: List<NotflixRating>) {
    if(ratings.isEmpty())return
    val locale=LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxWidth().testTag("notflix-detail-ratings"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        if(ratings.any{it.fromMDBList}) Text(stringResource(R.string.notflix_ratings_via_mdblist),
            fontFamily=NotflixVisualSpec.BodyFont,fontSize=9.sp,lineHeight=11.sp,color=NotflixVisualSpec.Secondary)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            ratings.forEach { rating ->
                val name=when(rating.provider) {
                    NotflixRatingProvider.IMDB -> "IMDb"
                    NotflixRatingProvider.TRAKT -> "Trakt"
                    NotflixRatingProvider.TMDB -> "TMDB"
                    NotflixRatingProvider.LETTERBOXD -> "Letterboxd"
                    NotflixRatingProvider.METACRITIC -> "Metacritic"
                    NotflixRatingProvider.MAL -> "MyAnimeList"
                    NotflixRatingProvider.TOMATOES -> stringResource(R.string.notflix_ratings_rt_critics)
                    NotflixRatingProvider.AUDIENCE -> stringResource(R.string.notflix_ratings_rt_audience)
                }
                val label="$name ${rating.formatted(locale)}"
                Text(label,fontFamily=NotflixVisualSpec.BodyFont,fontSize=11.sp,lineHeight=14.sp,
                    color=NotflixVisualSpec.Text,
                    modifier=Modifier.background(Color.White.copy(alpha=.09f),RoundedCornerShape(4.dp))
                        .padding(horizontal=7.dp,vertical=4.dp)
                        .semantics(mergeDescendants=true){contentDescription=label}
                        .testTag("notflix-rating-${rating.provider.id}"))
            }
        }
    }
}
