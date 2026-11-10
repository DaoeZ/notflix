package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.core.streams.PlaybackAvailability
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.ui.appearance.*
import com.nuvio.tv.ui.components.LocalPlaybackAvailability
import com.nuvio.tv.ui.navigation.LocalDetailChildClosedEpoch
import com.nuvio.tv.ui.navigation.LocalDetailChildOverlayVisible

/** Explicit synthetic state and callback sink. No Hilt, stores, network, server or playback. */
internal class DetailFixtureState(series: Boolean = false) {
    lateinit var input: android.view.View
    var meta by mutableStateOf(detailFixtureMeta(series))
    var active by mutableStateOf(true)
    var season by mutableIntStateOf(1)
    var library by mutableStateOf(false)
    var movieWatched by mutableStateOf(false)
    var blur by mutableStateOf(false)
    var playback by mutableStateOf(true)
    var unavailable by mutableStateOf(setOf<String>())
    var overallRatings by mutableStateOf(HomeImdbRatingsVisibility.SHOW_ALL)
    var episodeRatingsVisibility by mutableStateOf(DetailImdbRatingsVisibility.SHOW_ALL)
    var watched by mutableStateOf(setOf<Pair<Int,Int>>())
    var progress by mutableStateOf(mapOf<Pair<Int,Int>,WatchProgress>())
    var ratings by mutableStateOf(mapOf((1 to 1) to 7.3, (1 to 2) to 9.2))
    var mdbRatings by mutableStateOf<MDBListRatings?>(null)
    var mdbActive by mutableStateOf(false)
    var mdbOrder by mutableStateOf(MDBListSettings.DEFAULT_RATING_ORDER)
    var tmdbRating by mutableStateOf<Float?>(null)
    var nextToWatch by mutableStateOf<NextToWatch?>(NextToWatch(null, false, null, null, null, "Play"))
    var comments by mutableStateOf(false)
    var overlay by mutableStateOf(false)
    var epoch by mutableIntStateOf(0)
    val actions = mutableListOf<String>()
    val navigation = mutableListOf<String>()
    val lastFocused = mutableMapOf<Int,String>()
    fun open(kind: String, id: String) { actions += "$kind:$id"; overlay = true }
}

internal fun detailFixtureMeta(series: Boolean): Meta = Meta(
    id = if (series) "fixture-series" else "fixture-movie",
    type = if (series) ContentType.SERIES else ContentType.MOVIE,
    name = if (series) "Synthetic series" else "Synthetic movie", poster = null,
    posterShape = PosterShape.POSTER, background = null, logo = null,
    description = "Synthetic description with real callback contracts. ".repeat(20),
    releaseInfo = "2024", imdbRating = 7.6f, genres = listOf("Drama", "Adventure"),
    runtime = "42 min", director = listOf("Synthetic director"), cast = listOf("Synthetic cast"),
    videos = if (series) (1..2).flatMap { season -> (1..8).map { episode ->
        Video("fixture:$season:$episode", "Synthetic episode $season-$episode", "2024-01-01T00:00:00Z", null,
            season = season, episode = episode, overview = "Synthetic spoiler $season-$episode", runtime = 42)
    } } else emptyList(), country = null, awards = null, language = null, links = emptyList())

@Composable
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
internal fun DetailFixture(state: DetailFixtureState, wikipediaExtract: com.nuvio.tv.data.wikipedia.WikipediaExtract? = null) {
    state.input = androidx.compose.ui.platform.LocalView.current
    val availability = remember(state.playback, state.unavailable) {
        // Capability predicate only: this does not create or contact a server.
        PlaybackAvailability(serverStreams = { _, id -> state.playback && id !in state.unavailable })
    }
    CompositionLocalProvider(LocalNotflixPresentation provides NotflixPresentation(active = state.active, reduceHomeMotion = true),
        LocalPlaybackAvailability provides availability,
        LocalDetailChildClosedEpoch provides state.epoch, LocalDetailChildOverlayVisible provides state.overlay) {
      NotflixPresentationTheme(state.active) {
        MetaDetailsContent(
            meta = state.meta,
            wikipediaExtract = wikipediaExtract,
            onDetailReturnEpisodeFocusConsumed = { },
            lastFocusedEpisodeIdBySeason = state.lastFocused,
            seasons = state.meta.videos.mapNotNull { it.season }.distinct().sorted(),
            selectedSeason = state.season,
            episodesForSeason = state.meta.videos.filter { it.season == state.season },
            isInLibrary = state.library,
            librarySourceMode = LibrarySourceMode.LOCAL,
            nextToWatch = state.nextToWatch,
            episodeProgressMap = state.progress,
            watchedEpisodes = state.watched,
            episodeWatchedPendingKeys = emptySet(),
            blurUnwatchedEpisodes = state.blur,
            randomEpisodeEnabled = false,
            episodeShuffle = EpisodeShuffleSettings(),
            shufflePoolEmpty = false,
            onEpisodeShuffleChange = { true },
            episodeOptionsOverlayStyle = EpisodeOptionsOverlayStyle.ARTWORK,
            showFullReleaseDate = false,
            overallRatingsVisibility = state.overallRatings,
            detailImdbRatingsVisibility = state.episodeRatingsVisibility,
            isMovieWatched = state.movieWatched,
            isMovieWatchedPending = false,
            moreLikeThis = emptyList(),
            moreLikeThisSource = null,
            collection = emptyList(),
            collectionName = null,
            episodeImdbRatings = state.ratings,
            isEpisodeRatingsLoading = false,
            episodeRatingsError = null,
            mdbListRatings = state.mdbRatings,
            mdbListRatingOrder = state.mdbOrder,
            isMdbListRatingsActive = state.mdbActive,
            tmdbRating = state.tmdbRating,
            comments = emptyList(),
            commentsCurrentPage = 1,
            commentsPageCount = 1,
            isCommentsLoading = false,
            isCommentsLoadingMore = false,
            commentsError = "Synthetic comments error".takeIf { state.comments },
            shouldShowCommentsSection = state.comments,
            commentsMode = CommentsMode.TITLE,
            commentsEpisodeTarget = null,
            selectedComment = null,
            onSeasonSelected = { state.season = it; state.navigation += "season:$it" },
            onEpisodeClick = { state.open("play", it.id) },
            onEpisodeManualPlayClick = { state.open("manual", it.id) },
            onEpisodeStartFromBeginningClick = { state.open("start", it.id) },
            onPlayClick = { state.open("play", it) },
            onPlayManuallyClick = { state.open("manual", it) },
            onPlayStartFromBeginningClick = { state.open("start", it) },
            showManualPlayOption = true,
            onPlayButtonFocused = { },
            onToggleLibrary = { state.library = !state.library; state.actions += "library" },
            onLibraryLongPress = { },
            onToggleMovieWatched = { state.movieWatched = !state.movieWatched; state.actions += "movie-watched" },
            onToggleEpisodeWatched = { state.actions += "episode-watched:${it.id}" },
            onMarkSeasonWatched = { state.actions += "season-watched:$it" },
            onMarkSeasonUnwatched = { state.actions += "season-unwatched:$it" },
            onMarkPreviousEpisodesWatched = { state.actions += "previous:${it.id}" },
            onMarkPreviousSeasonsWatched = { state.actions += "previous-seasons:$it" },
            isSeasonFullyWatched = { false },
            trailerUrl = null,
            trailerAudioUrl = null,
            isTrailerPlaying = false,
            isBackgroundTrailerPlaying = false,
            pauseBackgroundTrailerOnScroll = false,
            showTrailerControls = false,
            hideLogoDuringTrailer = false,
            trailerButtonEnabled = false,
            isSharedTrailerOverlayVisible = false,
            isSharedTrailerLoading = false,
            sharedTrailerUrl = null,
            sharedTrailerAudioUrl = null,
            sharedTrailerErrorMessage = null,
            selectedSharedTrailer = null,
            trailerSeekToken = 0,
            trailerSeekDeltaMs = 0L,
            onTrailerControlKey = { _, _, _ -> false },
            onTrailerProgressChanged = { _, _ -> },
            onTrailerEnded = { },
            onTrailerButtonClick = { },
            onSharedTrailerSelected = { _ -> },
            onDismissSharedTrailer = { },
            onRetrySharedTrailer = { },
            onRetryComments = { state.actions += "retry-comments" },
            onLoadMoreComments = { },
            onCommentsModeSelected = { _ -> },
            onCommentsEpisodeSelected = { state.navigation += "comments:${it.id}" },
            onCommentClick = { _ -> },
            onShowPreviousComment = { },
            onShowNextComment = { },
            onDismissCommentOverlay = { },
            commentOverlayDirection = 0,
            restorePlayFocusAfterTrailerBackToken = 0,
            restoreSharedTrailerFocusToken = 0,
            onSharedTrailerFocusRestored = { },
            onNavigateToCastDetail = { id, _, _ -> state.actions += "cast:$id" },
        )
        if (state.overlay) {
            val focus = remember { FocusRequester() }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .9f)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Button(onClick = { state.overlay = false; state.epoch++ },
                    modifier = Modifier.focusRequester(focus).testTag("synthetic-source-close")) { Text("Close synthetic source") }
            }
            LaunchedEffect(Unit) { focus.requestFocusAfterFrames() }
        }
      }
    }
}
