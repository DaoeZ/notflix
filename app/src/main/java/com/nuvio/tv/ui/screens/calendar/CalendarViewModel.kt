package com.nuvio.tv.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.ActiveProfileAgeGate
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.profile.isAllowed
import com.nuvio.tv.data.local.hiddenTitleKey
import com.nuvio.tv.data.repository.CALENDAR_WINDOW_DAYS
import com.nuvio.tv.data.repository.CalendarDay
import com.nuvio.tv.data.repository.CalendarEntry
import com.nuvio.tv.data.repository.CalendarNuvioShow
import com.nuvio.tv.data.repository.CalendarRepository
import com.nuvio.tv.data.repository.CalendarSource
import com.nuvio.tv.data.repository.groupByLocalDay
import com.nuvio.tv.domain.repository.HiddenTitlesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val ageGate: ActiveProfileAgeGate,
    private val profileManager: ProfileManager,
    private val hiddenTitlesRepository: HiddenTitlesRepository
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val source: CalendarSource = CalendarSource.NUVIO,
        val traktFailed: Boolean = false,
        val days: List<CalendarDay> = emptyList(),
        val error: Boolean = false,
        /** Pósters de Trakt cargados al pintar cada fila (id de contenido → URL). */
        val posters: Map<String, String> = emptyMap()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var nuvioUpcoming: List<CalendarNuvioShow> = emptyList()
    private var loadJob: Job? = null
    private val posterJobs = mutableMapOf<String, Job>()

    fun load(nuvioUpcoming: List<CalendarNuvioShow>, force: Boolean = false) {
        val current = _uiState.value
        if (!force && nuvioUpcoming == this.nuvioUpcoming && !current.loading && !current.error) return
        this.nuvioUpcoming = nuvioUpcoming
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = false) }
            try {
                val result = repository.upcoming(
                    days = CALENDAR_WINDOW_DAYS,
                    nuvioUpcoming = nuvioUpcoming
                )
                val allowed = filterEntries(result.entries)
                val days = groupByLocalDay(allowed, ZoneId.systemDefault(), LocalDate.now())
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = false,
                        source = result.source,
                        traktFailed = result.traktFailed,
                        days = days
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false, error = true) }
            }
        }
    }

    fun retry() = load(nuvioUpcoming, force = true)

    /** Carga perezosa: solo se pide el póster de las tarjetas que llegan a pintarse. */
    fun ensurePoster(entry: CalendarEntry) {
        if (!entry.poster.isNullOrBlank()) return
        if (entry.tmdbId == null) return
        val key = entry.contentId
        if (_uiState.value.posters.containsKey(key) || posterJobs.containsKey(key)) return
        posterJobs[key] = viewModelScope.launch {
            try {
                val url = runCatching { repository.posterFor(entry) }.getOrNull()
                if (url != null) {
                    _uiState.update { it.copy(posters = it.posters + (key to url)) }
                }
            } finally {
                posterJobs.remove(key)
            }
        }
    }

    private suspend fun filterEntries(entries: List<CalendarEntry>): List<CalendarEntry> {
        if (entries.isEmpty()) return emptyList()
        val profileId = profileManager.activeProfileId.value
        val hidden = runCatching { hiddenTitlesRepository.observe(profileId).first() }
            .getOrDefault(emptySet())
        val visible = if (hidden.isEmpty()) {
            entries
        } else {
            entries.filterNot { isHiddenCalendarEntry(it, hidden) }
        }
        val limit = ageGate.currentLimit() ?: return visible
        return coroutineScope {
            visible.map { entry ->
                async(Dispatchers.IO) {
                    val rating = ageGate.ratingFor(
                        apiType = entry.contentType,
                        id = entry.contentId,
                        ownRating = entry.ageRating
                    )
                    entry to isAllowed(limit, rating)
                }
            }.awaitAll().filter { it.second }.map { it.first }
        }
    }
}

/**
 * Los títulos ocultos se guardan como «apiType:id» y la ficha de calendario puede no conocer el
 * apiType con el que se ocultó (series/tv). Se comprueban las variantes de serie, nunca «movie»:
 * «movie:id» y «series:id» son claves distintas y una película oculta no debe esconder una serie.
 */
internal fun isHiddenCalendarEntry(entry: CalendarEntry, hidden: Set<String>): Boolean {
    if (hidden.isEmpty()) return false
    val types = setOf(entry.contentType.lowercase(), "series", "tv", "anime")
    return types.any { hiddenTitleKey(it, entry.contentId) in hidden }
}
