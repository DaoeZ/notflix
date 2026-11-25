package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.local.InternalPlayerEngine
import com.nuvio.tv.domain.model.NotflixPlayerAction.*
import com.nuvio.tv.domain.model.NotflixPlayerAction
import com.nuvio.tv.domain.model.NotflixPlayerActions
import org.junit.Assert.*
import org.junit.Test

class NotflixPlayerActionsTest {
    private val all = NotflixPlayerActions.configurable
    private val widths = NotflixPlayerAction.entries.associateWith { 60f }
    private fun complete() = PlayerUiState(currentSeason = 1, currentEpisode = 2,
        audioTracks = listOf(TrackInfo(0, "Audio", "en")), subtitleTracks = listOf(TrackInfo(0, "Subtitle", "es")),
        chapters = listOf(PlayerChapter(0, "Chapter")), activeSkipInterval = com.nuvio.tv.data.repository.SkipInterval(0.0, 60.0, "intro", "test"),
        nextEpisode = NextEpisodeInfo("s:1:3", 1, 3, "Next", null, null, "2020-01-01", true, available = true, unairedMessage = null),
        internalPlayerEngine = InternalPlayerEngine.MVP_PLAYER, notflixSharpenAvailable = true, playbackIssueReportsEnabled = true)

    @Test fun everyOfficialControlFunctionHasOneDestinationIncludingManualSkipAndSharpen() {
        val available = notflixAvailablePlayerActions(complete(), false, true, true)
        assertEquals(setOf(AUDIO_SUBTITLES, EPISODES, NEXT_EPISODE, SPEED, NIGHT_MODE, SLEEP_TIMER, SOURCES, ASPECT, STREAM_INFO,
            CHAPTERS, SKIP_SEGMENT, SHARPEN, PARTY, ENGINE, EXTERNAL, REPORT), available.toSet())
        for (selection in listOf(NotflixPlayerActions.defaults, all.reversed().map { it.id }, emptyList())) {
            val layout = NotflixPlayerActions.layout(available, selection, 240f, widths, 10f)
            assertEquals(MORE, layout.row.last())
            assertEquals(available.toSet(), (layout.row.dropLast(1) + layout.more).toSet())
            assertTrue(layout.more.none { it in layout.row })
            assertEquals(available.size, layout.row.size - 1 + layout.more.size)
        }
    }

    @Test fun defaultsAndResetKeepThePromoterOrderAndMoreLast() {
        val layout = NotflixPlayerActions.layout(all, NotflixPlayerActions.defaults, 1000f, widths, 10f)
        assertEquals(listOf(AUDIO_SUBTITLES, EPISODES, NEXT_EPISODE, SPEED, MORE), layout.row)
        assertEquals(NotflixPlayerActions.defaults, NotflixPlayerActions.decode(null))
    }

    @Test fun configurationRejectsDuplicatesUnknownIdsAndSelectableMore() {
        assertEquals(listOf(SOURCES.id, SPEED.id), NotflixPlayerActions.normalize(listOf("unknown", SOURCES.id, SOURCES.id, MORE.id, SPEED.id)))
        assertEquals(NotflixPlayerActions.defaults, NotflixPlayerActions.decode("future-or-corrupt"))
        assertEquals(emptyList<String>(), NotflixPlayerActions.decode(""))
    }

    @Test fun maximumIsFiveActionsPlusFixedMoreAndRemovingAllowsAnotherSelection() {
        val selected = NotflixPlayerActions.normalize(all.map { it.id })
        assertEquals(5, selected.size)
        assertEquals(selected, NotflixPlayerActions.toggle(selected, ASPECT.id))
        val without = NotflixPlayerActions.toggle(selected, selected.first())
        assertEquals(without + ASPECT.id, NotflixPlayerActions.toggle(without, ASPECT.id))
    }

    @Test fun reorderingPreservesMembershipAndBounds() {
        val ids = listOf(SOURCES.id, SPEED.id, ASPECT.id)
        assertEquals(listOf(SPEED.id, SOURCES.id, ASPECT.id), NotflixPlayerActions.move(ids, SPEED.id, -1))
        assertEquals(listOf(SOURCES.id, ASPECT.id, SPEED.id), NotflixPlayerActions.move(ids, SPEED.id, 1))
        assertEquals(ids, NotflixPlayerActions.move(ids, SOURCES.id, -1))
        assertEquals(ids, NotflixPlayerActions.move(ids, ASPECT.id, 1))
    }

    @Test fun longTranslatedLabelsOverflowToMoreWithoutDroppingActions() {
        val measured = widths + (AUDIO_SUBTITLES to 220f)
        val layout = NotflixPlayerActions.layout(all, NotflixPlayerActions.defaults, 200f, measured, 12f)
        assertEquals(listOf(MORE), layout.row)
        assertEquals(all, layout.more)
        val compact = NotflixPlayerActions.layout(all, listOf(SPEED.id, SOURCES.id), 200f, widths, 10f)
        assertEquals(listOf(SPEED, SOURCES, MORE), compact.row)
    }

    @Test fun movieAndLiveAvailabilityDoesNotInventEpisodesChaptersOrTracks() {
        val movie = notflixAvailablePlayerActions(PlayerUiState(), false, false, false)
        assertEquals(listOf(SPEED, NIGHT_MODE, SLEEP_TIMER, SOURCES, ASPECT, STREAM_INFO, ENGINE), movie)
        val live = notflixAvailablePlayerActions(complete(), true, true, true)
        assertFalse(CHAPTERS in live); assertTrue(SOURCES in live)
        assertFalse(EXTERNAL in notflixAvailablePlayerActions(complete(), false, true, false))
    }

    @Test fun nextEpisodeKeepsTheExistingAiringAndCountdownRules() {
        val ui = complete()
        assertFalse(NEXT_EPISODE in notflixAvailablePlayerActions(ui.copy(nextEpisode = ui.nextEpisode!!.copy(hasAired = false)), false, true, true))
        assertFalse(NEXT_EPISODE in notflixAvailablePlayerActions(ui.copy(postPlayMode = PostPlayMode.AutoPlay(ui.nextEpisode!!, countdownSec = 5)), false, true, true))
        assertFalse(SKIP_SEGMENT in notflixAvailablePlayerActions(ui.copy(skipIntervalDismissed = true), false, true, true))
    }
}
