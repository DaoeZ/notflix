package com.nuvio.tv.ui.screens.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.nuvio.tv.domain.model.MDBListSettings
import com.nuvio.tv.domain.model.RatingSource
import com.nuvio.tv.ui.screens.detail.NotflixRatingsRow
import com.nuvio.tv.ui.screens.detail.notflixDetailRatings
import com.nuvio.tv.ui.screens.detail.notflixStandardRatings
import org.junit.Rule
import org.junit.Test

/** M57: the visible badge and detail row carry the real origin, not a hardcoded IMDb label. */
@OptIn(ExperimentalTestApi::class)
class NotflixRatingLabelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tmdbBadgeShowsTmdb() {
        compose.setContent { RatingBadge(8.5f, source = RatingSource.TMDB) }
        compose.onNodeWithText("TMDB").assertExists()
        compose.onNodeWithText("IMDb").assertDoesNotExist()
    }

    @Test fun imdbBadgeKeepsImdb() {
        compose.setContent { RatingBadge(8.5f, source = RatingSource.IMDB) }
        compose.onNodeWithText("IMDb").assertExists()
        compose.onNodeWithText("TMDB").assertDoesNotExist()
    }

    @Test fun tmdbDetailRatingUsesTheTmdbSlot() {
        val standards = notflixStandardRatings(8.5f, RatingSource.TMDB, null)
        val ratings = notflixDetailRatings(
            ratings = null,
            order = MDBListSettings.DEFAULT_RATING_ORDER,
            standardImdb = standards.imdb,
            standardTmdb = standards.tmdb
        )
        compose.setContent { NotflixRatingsRow(ratings) }

        compose.onNodeWithTag("notflix-rating-tmdb").assertTextEquals("TMDB 85/100")
        compose.onNodeWithTag("notflix-rating-imdb").assertDoesNotExist()
    }
}
