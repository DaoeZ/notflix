package com.nuvio.tv.ui.components.posteroptions

import android.util.Log
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.core.tracking.TrackingMembershipRemovalConfirmation
import com.nuvio.tv.core.tracking.mergeTrackingMembershipWithTabs
import com.nuvio.tv.core.tracking.toggleTrackingMembershipSelection
import com.nuvio.tv.data.local.WatchedSeriesStateHolder
import com.nuvio.tv.data.repository.parseContentIds
import com.nuvio.tv.domain.model.LibraryEntryInput
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.ListMembershipChanges
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * M67: estado oficial de los botones Mi lista y Visto de una tarjeta de colección. `null` = todavía
 * no se conoce; el panel lo dibuja como «Comprobando…» hasta que llega el dato.
 */
data class PosterCardStatus(
    val inLibrary: Boolean? = null,
    val watched: Boolean? = null,
    val libraryPending: Boolean = false,
    val watchedPending: Boolean = false
)

/** Clave estable del estado por título de una tarjeta (M67). */
fun posterCardStatusKey(apiType: String, id: String): String = "${apiType.trim().lowercase()}|${id.trim()}"

/**
 * Reusable controller for the long-press / hold-down poster options menu.
 *
 * Inject into a ViewModel via Hilt and call [bind] from the VM's init with
 * `viewModelScope`. The screen renders [PosterOptionsHost] with [state] and
 * wires [show] to each card's `onLongPress`.
 */
class PosterOptionsController @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val libraryRepository: LibraryRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val metaRepository: MetaRepository,
    private val watchedSeriesStateHolder: WatchedSeriesStateHolder,
    private val tmdbService: TmdbService
) {
    private val _state = MutableStateFlow(PosterOptionsState())
    val state: StateFlow<PosterOptionsState> = _state.asStateFlow()

    /**
     * M67: estado por título de los botones Mi lista y Visto de la tarjeta abierta. El menú largo
     * solo conoce su objetivo; una rejilla necesita el estado del título que abre cada tarjeta.
     */
    private val _cardStatus = MutableStateFlow<Map<String, PosterCardStatus>>(emptyMap())
    val cardStatus: StateFlow<Map<String, PosterCardStatus>> = _cardStatus.asStateFlow()

    private fun cardKey(item: MetaPreview): String = posterCardStatusKey(item.apiType, item.id)

    private val targetFlow = MutableStateFlow<MetaPreview?>(null)

    private var scope: CoroutineScope? = null
    private var bound = false
    private var showJob: kotlinx.coroutines.Job? = null
    private var pendingMembershipChanges: ListMembershipChanges? = null
    private var pickerLoadJob: Job? = null
    private var pickerVisit = 0L
    private var cardStatusJob: Job? = null
    private var cardStatusVisit = 0L
    private var observedCard: MetaPreview? = null
    private var canonicalCard: Pair<String, MetaPreview>? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    fun bind(scope: CoroutineScope) {
        if (bound) return
        bound = true
        this.scope = scope

        libraryRepository.sourceMode
            .distinctUntilChanged()
            .onEach { mode ->
                _state.update { current ->
                    val resetPicker = mode == LibrarySourceMode.LOCAL
                    if (resetPicker) {
                        current.copy(
                            librarySourceMode = mode,
                            listPickerActive = false,
                            listPickerPending = false,
                            listPickerError = null,
                            listPickerTitle = null,
                            listPickerContentType = null,
                            listPickerMembership = emptyMap()
                        )
                    } else {
                        current.copy(librarySourceMode = mode)
                    }
                }
            }
            .launchIn(scope)

        libraryRepository.membershipListTabs
            .distinctUntilChanged()
            .onEach { tabs ->
                _state.update { current ->
                    current.copy(
                        libraryListTabs = tabs,
                        listPickerMembership = mergeTrackingMembershipWithTabs(
                            tabs = tabs,
                            membership = current.listPickerMembership
                        )
                    )
                }
            }
            .launchIn(scope)

        targetFlow
            .flatMapLatest { item ->
                if (item == null) {
                    flowOf(false to false)
                } else {
                    val isSeries = item.apiType.equals("series", ignoreCase = true) ||
                        item.apiType.equals("tv", ignoreCase = true) ||
                        item.apiType.equals("anime", ignoreCase = true)
                    if (isSeries) {
                        combine(
                            libraryRepository.isInLibrary(item.id, item.apiType),
                            watchedSeriesStateHolder.fullyWatchedSeriesIds
                        ) { lib, watchedIds -> lib to (item.id in watchedIds) }
                    } else {
                        combine(
                            libraryRepository.isInLibrary(item.id, item.apiType),
                            watchProgressRepository.isWatched(item.id, videoId = item.imdbId)
                        ) { lib, watched -> lib to watched }
                    }
                }
            }
            .onEach { (isInLibrary, isWatched) ->
                _state.update { current ->
                    current.copy(
                        isInLibrary = isInLibrary,
                        isWatched = isWatched
                    )
                }
            }
            .launchIn(scope)
    }

    fun show(item: MetaPreview, addonBaseUrl: String?) {
        val launchScope = this.scope ?: return
        showJob?.cancel()
        showJob = launchScope.launch {
            val canonical = canonicalize(item)
            val isSeries = canonical.apiType.equals("series", ignoreCase = true) ||
                canonical.apiType.equals("tv", ignoreCase = true) ||
                canonical.apiType.equals("anime", ignoreCase = true)
            val initialIsInLibrary = runCatching {
                libraryRepository.isInLibrary(canonical.id, canonical.apiType).first()
            }.getOrDefault(false)
            val initialIsWatched = if (isSeries) {
                canonical.id in watchedSeriesStateHolder.fullyWatchedSeriesIds.value
            } else {
                runCatching {
                    watchProgressRepository.isWatched(canonical.id, videoId = canonical.imdbId).first()
                }.getOrDefault(false)
            }

            _state.update { current ->
                current.copy(
                    target = canonical,
                    originalItemId = item.id,
                    addonBaseUrl = addonBaseUrl.orEmpty(),
                    isInLibrary = initialIsInLibrary,
                    isWatched = initialIsWatched,
                    isLibraryPending = false,
                    isWatchedPending = false
                )
            }
            targetFlow.value = canonical
        }
    }

    private suspend fun canonicalize(item: MetaPreview): MetaPreview {
        if (item.id.startsWith("tt", ignoreCase = false)) return item
        val tmdbNumber = parseContentIds(item.id).tmdb ?: item.id.toIntOrNull() ?: return item
        val mediaType = if (item.apiType.equals("series", ignoreCase = true) ||
            item.apiType.equals("tv", ignoreCase = true) ||
            item.apiType.equals("anime", ignoreCase = true)
        ) "tv" else "movie"
        val imdb = runCatching { tmdbService.tmdbToImdb(tmdbNumber, mediaType) }.getOrNull()
        return if (!imdb.isNullOrBlank()) item.copy(id = imdb) else item
    }

    private suspend fun ensureCanonical(): MetaPreview? {
        val current = _state.value.target ?: return null
        val canonical = canonicalize(current)
        if (canonical.id != current.id && _state.value.target?.id == current.id) {
            _state.update { it.copy(target = canonical) }
            targetFlow.value = canonical
        }
        return canonical
    }

    fun dismiss() {
        showJob?.cancel()
        showJob = null
        targetFlow.value = null
        _state.update { it.copy(target = null) }
    }

    fun toggleLibrary() {
        val state = _state.value
        if (state.target == null) return
        if (state.isLibraryPending) return
        val scope = this.scope ?: return
        val pendingKeys = menuCardKeys(state)
        if (pendingKeys.any { _cardStatus.value[it]?.libraryPending == true }) return
        setMenuCardPending(pendingKeys, library = true, pending = true)

        _state.update { it.copy(isLibraryPending = true) }
        scope.launch {
            try {
                val canonical = ensureCanonical() ?: return@launch
                runCatching {
                    libraryRepository.toggleDefault(
                        canonical.toLibraryEntryInput(state.addonBaseUrl.takeIf { it.isNotBlank() })
                    )
                }.onFailure { error ->
                    Log.w(TAG, "Failed to toggle library for ${canonical.id}: ${error.message}")
                }
            } finally {
                _state.update { it.copy(isLibraryPending = false) }
                setMenuCardPending(pendingKeys, library = true, pending = false)
            }
        }
    }

    fun openListPicker() {
        val state = _state.value
        val item = state.target ?: return
        if (state.librarySourceMode == LibrarySourceMode.LOCAL) {
            toggleLibrary()
            dismiss()
            return
        }
        startListPicker(item, state.addonBaseUrl)
    }

    /**
     * M67: el selector de listas de la cuenta que ya usa el menú largo (mismo estado, mismo
     * `PosterOptionsHost`), abierto tanto desde el menú como desde el botón Mi lista de la tarjeta.
     */
    private fun startListPicker(item: MetaPreview, addonBaseUrl: String) {
        val scope = this.scope ?: return
        pickerLoadJob?.cancel()
        val visit = ++pickerVisit
        activeListPickerInput = null
        pendingMembershipChanges = null
        showJob?.cancel()
        showJob = null

        _state.update { current ->
            current.copy(
                target = null,
                removalConfirmations = emptyList(),
                listPickerActive = true,
                listPickerTitle = item.name,
                listPickerContentType = item.apiType,
                listPickerPending = true,
                listPickerError = null,
                listPickerMembership = mergeTrackingMembershipWithTabs(
                    tabs = current.libraryListTabs,
                    membership = emptyMap()
                )
            )
        }
        targetFlow.value = null

        pickerLoadJob = scope.launch {
            val canonical = canonicalize(item)
            coroutineContext.ensureActive()
            if (!pickerCurrent(visit)) return@launch
            val input = canonical.toLibraryEntryInput(addonBaseUrl.takeIf { it.isNotBlank() })
            activeListPickerInput = input
            runCatching {
                libraryRepository.getMembershipSnapshot(input)
            }.onSuccess { snapshot ->
                if (!pickerCurrent(visit)) return@onSuccess
                _state.update { current ->
                    current.copy(
                        listPickerPending = false,
                        listPickerError = null,
                        listPickerMembership = mergeTrackingMembershipWithTabs(
                            tabs = current.libraryListTabs,
                            membership = snapshot.listMembership
                        )
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (!pickerCurrent(visit)) return@onFailure
                Log.w(TAG, "Failed to load list picker for ${canonical.id}: ${error.message}")
                _state.update { current ->
                    current.copy(
                        listPickerPending = false,
                        listPickerError = error.message ?: appContext.getString(com.nuvio.tv.R.string.poster_options_error_load_lists_failed)
                    )
                }
            }
        }
    }

    fun toggleListMembership(listKey: String) {
        _state.update { current ->
            val nextMembership = toggleTrackingMembershipSelection(
                tabs = current.libraryListTabs,
                membership = current.listPickerMembership,
                listKey = listKey,
                contentType = current.listPickerContentType
            ) ?: return@update current
            current.copy(
                listPickerMembership = nextMembership,
                listPickerError = null
            )
        }
    }

    fun saveListPicker() {
        val state = _state.value
        if (state.listPickerPending) return
        if (state.librarySourceMode == LibrarySourceMode.LOCAL) return
        val input = activeListPickerInput ?: return
        val scope = this.scope ?: return
        val visit = pickerVisit
        val desiredMembership = state.listPickerMembership

        _state.update { it.copy(listPickerPending = true, listPickerError = null) }
        scope.launch {
            runCatching {
                libraryRepository.applyMembershipChanges(
                    item = input,
                    changes = ListMembershipChanges(
                        desiredMembership = desiredMembership
                    )
                )
            }.onSuccess { result ->
                if (!pickerCurrent(visit)) return@onSuccess
                if (result.requiresRemovalConfirmation) {
                    pendingMembershipChanges = ListMembershipChanges(
                        desiredMembership = desiredMembership
                    )
                    _state.update {
                        it.copy(
                            listPickerPending = false,
                            removalConfirmations = result.requiredRemovalConfirmations
                        )
                    }
                } else {
                    closeListPickerAfterSave()
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (!pickerCurrent(visit)) return@onFailure
                Log.w(TAG, "Failed to save list picker: ${error.message}")
                _state.update {
                    it.copy(
                        listPickerPending = false,
                        listPickerError = error.message ?: appContext.getString(com.nuvio.tv.R.string.poster_options_error_update_lists_failed)
                    )
                }
            }
        }
    }

    private fun pickerCurrent(visit: Long): Boolean =
        pickerVisit == visit && _state.value.listPickerActive

    fun dismissListPicker() {
        pickerVisit++
        pickerLoadJob?.cancel()
        pickerLoadJob = null
        activeListPickerInput = null
        pendingMembershipChanges = null
        _state.update {
            it.copy(
                listPickerActive = false,
                listPickerPending = false,
                listPickerError = null,
                listPickerTitle = null,
                listPickerContentType = null,
                removalConfirmations = emptyList()
            )
        }
    }

    fun confirmDestructiveRemoval() {
        val input = activeListPickerInput ?: return
        val changes = pendingMembershipChanges ?: return
        val confirmations = _state.value.removalConfirmations
        val scope = this.scope ?: return
        val visit = pickerVisit
        _state.update { it.copy(listPickerPending = true) }
        scope.launch {
            runCatching {
                libraryRepository.applyMembershipChanges(
                    item = input,
                    changes = changes,
                    confirmedRemovalProviders = confirmations.mapTo(linkedSetOf(), TrackingMembershipRemovalConfirmation::providerId)
                )
            }.onSuccess { result ->
                if (!pickerCurrent(visit)) return@onSuccess
                if (result.requiresRemovalConfirmation) {
                    _state.update {
                        it.copy(
                            listPickerPending = false,
                            removalConfirmations = result.requiredRemovalConfirmations
                        )
                    }
                } else {
                    closeListPickerAfterSave()
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (!pickerCurrent(visit)) return@onFailure
                _state.update {
                    it.copy(
                        listPickerPending = false,
                        removalConfirmations = emptyList(),
                        listPickerError = error.message
                            ?: appContext.getString(com.nuvio.tv.R.string.poster_options_error_update_lists_failed)
                    )
                }
            }
        }
    }

    fun cancelDestructiveRemoval() {
        pendingMembershipChanges = null
        _state.update { it.copy(removalConfirmations = emptyList()) }
    }

    private fun closeListPickerAfterSave() {
        pickerVisit++
        activeListPickerInput = null
        pendingMembershipChanges = null
        _state.update {
            it.copy(
                listPickerActive = false,
                listPickerPending = false,
                listPickerError = null,
                listPickerTitle = null,
                listPickerContentType = null,
                removalConfirmations = emptyList()
            )
        }
    }

    fun toggleMovieWatched() {
        val state = _state.value
        val item = state.target ?: return
        if (!item.apiType.equals("movie", ignoreCase = true)) return
        if (state.isWatchedPending) return
        val scope = this.scope ?: return
        val pendingKeys = menuCardKeys(state)
        if (pendingKeys.any { _cardStatus.value[it]?.watchedPending == true }) return
        setMenuCardPending(pendingKeys, library = false, pending = true)

        val currentlyWatched = state.isWatched
        // Collect all known ID variants: original UI id (e.g. "tmdb:123"),
        // canonical id (e.g. "tt1979376"), and imdbId field if available.
        val optimisticIds = buildSet {
            add(item.id)
            state.originalItemId?.takeIf { it != item.id }?.let(::add)
            item.imdbId?.takeIf(String::isNotBlank)?.let(::add)
        }

        watchProgressRepository.applyOptimisticWatchedMovie(optimisticIds, add = !currentlyWatched)

        _state.update { it.copy(isWatchedPending = true) }
        scope.launch {
            try {
                val canonical = ensureCanonical() ?: return@launch
                runCatching {
                    if (currentlyWatched) {
                        watchProgressRepository.removeFromHistory(canonical.id, videoId = canonical.imdbId)
                    } else {
                        watchProgressRepository.markAsCompleted(buildCompletedMovieProgress(canonical))
                    }
                }.onFailure { error ->
                    Log.w(TAG, "Failed to toggle watched for ${canonical.id}: ${error.message}")
                    // Revert optimistic update on failure
                    watchProgressRepository.revertOptimisticWatchedMovie(optimisticIds, add = !currentlyWatched)
                }
            } finally {
                _state.update { it.copy(isWatchedPending = false) }
                setMenuCardPending(pendingKeys, library = false, pending = false)
            }
        }
    }

    fun toggleSeriesWatched() {
        val state = _state.value
        val item = state.target ?: return
        val isSeries = item.apiType.equals("series", ignoreCase = true) ||
            item.apiType.equals("tv", ignoreCase = true) ||
            item.apiType.equals("anime", ignoreCase = true)
        if (!isSeries) return
        if (state.isWatchedPending) return
        val scope = this.scope ?: return
        val pendingKeys = menuCardKeys(state)
        if (pendingKeys.any { _cardStatus.value[it]?.watchedPending == true }) return
        setMenuCardPending(pendingKeys, library = false, pending = true)

        val currentlyWatched = state.isWatched

        // Optimistic UI update — badge changes immediately
        val currentIds = watchedSeriesStateHolder.fullyWatchedSeriesIds.value
        val optimisticIds = if (currentlyWatched) currentIds - item.id else currentIds + item.id
        watchedSeriesStateHolder.update(optimisticIds)

        _state.update { it.copy(isWatchedPending = true) }
        scope.launch {
            try {
                val canonical = ensureCanonical() ?: return@launch
                runCatching {
                    if (currentlyWatched) {
                        unmarkSeriesWatched(canonical)
                    } else {
                        markSeriesWatched(canonical)
                    }
                }.onFailure { error ->
                    Log.w(TAG, "Failed to toggle series watched for ${canonical.id}: ${error.message}")
                    // Revert only this title; another card may have changed a different series.
                    watchedSeriesStateHolder.update(
                        (watchedSeriesStateHolder.fullyWatchedSeriesIds.value - item.id) +
                            currentIds.intersect(setOf(item.id))
                    )
                }
            } finally {
                _state.update { it.copy(isWatchedPending = false) }
                setMenuCardPending(pendingKeys, library = false, pending = false)
            }
        }
    }

    // ---- M67: Mi lista y Visto desde el botón de la tarjeta. Mismos escritores oficiales que el
    // menú largo (no se duplica ningún almacén ni se abre un segundo camino de escritura). ----

    /** Observe just the expanded title, under the same canonical ID used by the official menu. */
    fun refreshCardStatus(item: MetaPreview) {
        val scope = this.scope ?: return
        val key = cardKey(item)
        val visit = ++cardStatusVisit
        observedCard = item
        cardStatusJob?.cancel()
        canonicalCard = null
        cardStatusJob = scope.launch {
            val canonical = canonicalize(item)
            coroutineContext.ensureActive()
            if (visit != cardStatusVisit) return@launch
            canonicalCard = key to canonical
            val library = libraryRepository.isInLibrary(canonical.id, canonical.apiType)
                .map { it as Boolean? }.catch { emit(null) }
            val watched = if (canonical.isSeriesType()) {
                watchedSeriesStateHolder.fullyWatchedSeriesIds.map { ids ->
                    (canonical.id in ids || item.id in ids) as Boolean?
                }
            } else {
                watchProgressRepository.isWatched(canonical.id, videoId = canonical.imdbId)
                    .map { it as Boolean? }
            }.catch { emit(null) }
            combine(library, watched) { inLibrary, isWatched -> inLibrary to isWatched }
                .collect { (inLibrary, isWatched) ->
                    if (visit == cardStatusVisit) setCardStatus(key) { previous ->
                        previous.copy(
                            inLibrary = if (previous.libraryPending) previous.inLibrary else inLibrary,
                            watched = if (previous.watchedPending) previous.watched else isWatched
                        )
                    }
                }
        }
    }

    fun stopCardStatus() {
        cardStatusVisit++
        cardStatusJob?.cancel()
        cardStatusJob = null
        canonicalCard = null
        observedCard = null
    }

    private suspend fun canonicalCardItem(item: MetaPreview): MetaPreview =
        canonicalCard?.takeIf { it.first == cardKey(item) }?.second?.let { item.copy(id = it.id) }
            ?: canonicalize(item)

    /** Card and long press use the same repository and the same account list picker. */
    fun toggleLibraryFor(item: MetaPreview, addonBaseUrl: String?) {
        if (_state.value.librarySourceMode != LibrarySourceMode.LOCAL) {
            startListPicker(item, addonBaseUrl.orEmpty())
            return
        }
        val key = cardKey(item)
        if (_cardStatus.value[key]?.libraryPending == true) return
        val scope = this.scope ?: return
        setCardStatus(key) { it.copy(libraryPending = true) }
        scope.launch {
            try {
                val canonical = canonicalCardItem(item)
                coroutineContext.ensureActive()
                libraryRepository.toggleDefault(canonical.toLibraryEntryInput(addonBaseUrl?.takeIf { it.isNotBlank() }))
                val inLibrary = libraryRepository.isInLibrary(canonical.id, canonical.apiType).first()
                setCardStatus(key) { it.copy(inLibrary = inLibrary) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w(TAG, "Failed to toggle card library for ${item.id}: ${error.message}")
            } finally {
                setCardStatus(key) { it.copy(libraryPending = false) }
            }
        }
    }

    /** Official watched writes, preserving unrelated optimistic changes if this write fails. */
    fun toggleWatchedFor(item: MetaPreview) {
        val key = cardKey(item)
        val status = _cardStatus.value[key] ?: PosterCardStatus()
        if (status.watchedPending) return
        val scope = this.scope ?: return
        setCardStatus(key) { it.copy(watchedPending = true) }
        scope.launch {
            var optimisticIds = emptySet<String>()
            var seriesIdsBefore = emptySet<String>()
            var currentlyWatched = status.watched == true
            val isSeries = item.isSeriesType()
            try {
                val canonical = canonicalCardItem(item)
                coroutineContext.ensureActive()
                seriesIdsBefore = watchedSeriesStateHolder.fullyWatchedSeriesIds.value
                currentlyWatched = if (isSeries) canonical.id in seriesIdsBefore || item.id in seriesIdsBefore
                    else watchProgressRepository.isWatched(canonical.id, videoId = canonical.imdbId).first()
                optimisticIds = movieWatchedIds(item) + movieWatchedIds(canonical)
                if (isSeries) {
                    watchedSeriesStateHolder.update(
                        if (currentlyWatched) seriesIdsBefore - optimisticIds else seriesIdsBefore + optimisticIds
                    )
                } else watchProgressRepository.applyOptimisticWatchedMovie(optimisticIds, add = !currentlyWatched)
                setCardStatus(key) { it.copy(watched = !currentlyWatched) }
                when {
                    currentlyWatched -> if (isSeries) unmarkSeriesWatched(canonical)
                        else watchProgressRepository.removeFromHistory(canonical.id, videoId = canonical.imdbId)
                    isSeries -> markSeriesWatched(canonical)
                    else -> watchProgressRepository.markAsCompleted(buildCompletedMovieProgress(canonical))
                }
            } catch (error: Exception) {
                if (optimisticIds.isNotEmpty()) {
                    if (isSeries) watchedSeriesStateHolder.update(
                        (watchedSeriesStateHolder.fullyWatchedSeriesIds.value - optimisticIds) +
                            seriesIdsBefore.intersect(optimisticIds)
                    ) else watchProgressRepository.revertOptimisticWatchedMovie(optimisticIds, add = !currentlyWatched)
                }
                setCardStatus(key) { it.copy(watched = currentlyWatched) }
                if (error is CancellationException) throw error
                Log.w(TAG, "Failed to toggle card watched for ${item.id}: ${error.message}")
            } finally {
                setCardStatus(key) { it.copy(watchedPending = false) }
            }
        }
    }

    private fun menuCardKeys(state: PosterOptionsState): Set<String> {
        val item = state.target ?: return emptySet()
        return setOf(cardKey(item), posterCardStatusKey(item.apiType, state.originalItemId ?: item.id))
    }

    /** Long press and the expanded card share the in-flight guard, including ID aliases. */
    private fun setMenuCardPending(keys: Set<String>, library: Boolean, pending: Boolean) {
        keys.forEach { key ->
            setCardStatus(key) {
                if (library) it.copy(libraryPending = pending) else it.copy(watchedPending = pending)
            }
        }
        if (!pending) observedCard?.takeIf { cardKey(it) in keys }?.let(::refreshCardStatus)
    }

    private inline fun setCardStatus(key: String, update: (PosterCardStatus) -> PosterCardStatus) {
        _cardStatus.update { current ->
            val next = current.toMutableMap()
            next.remove(key)
            next[key] = update(current[key] ?: PosterCardStatus())
            while (next.size > MAX_CARD_STATUSES) {
                val oldest = next.keys.firstOrNull { candidate ->
                    candidate != key && candidate != observedCard?.let(::cardKey) && next[candidate]?.let { !it.libraryPending && !it.watchedPending } == true
                } ?: break
                next.remove(oldest)
            }
            next
        }
    }

    private fun movieWatchedIds(item: MetaPreview): Set<String> = buildSet {
        add(item.id)
        item.imdbId?.takeIf(String::isNotBlank)?.let(::add)
    }

    private suspend fun markSeriesWatched(item: MetaPreview) {
        val episodes = fetchSeriesEpisodes(item).filter { it.season != null && it.episode != null && it.season != 0 }
        // Without episodes the only mark left is a show-level one, which trackers answer by marking
        // every episode watched. Fail instead so the optimistic badge is reverted.
        check(episodes.isNotEmpty()) { "No episodes found for ${item.id}" }

        val progressList = episodes.map { video ->
            WatchProgress(
                contentId = item.id,
                contentType = item.apiType,
                name = item.name,
                poster = item.poster,
                backdrop = item.backdropUrl,
                logo = item.logo,
                videoId = video.id,
                season = video.season,
                episode = video.episode,
                episodeTitle = video.title,
                position = 1L,
                duration = 1L,
                lastWatched = System.currentTimeMillis(),
                progressPercent = 100f
            )
        }
        watchProgressRepository.markAsCompletedBatch(progressList)
    }

    private suspend fun unmarkSeriesWatched(item: MetaPreview) {
        val episodes = fetchSeriesEpisodes(item).filter { it.season != null && it.episode != null && it.season != 0 }
        if (episodes.isEmpty()) {
            watchProgressRepository.removeFromHistory(item.id, videoId = item.imdbId)
            return
        }

        watchProgressRepository.removeFromHistoryBatch(
            contentId = item.id,
            videoId = item.imdbId,
            episodes = episodes.map { Triple(it.season!!, it.episode!!, it.id) }
        )
    }

    private suspend fun fetchSeriesEpisodes(item: MetaPreview): List<Video> {
        val type = if (item.apiType.equals("tv", ignoreCase = true) ||
            item.apiType.equals("anime", ignoreCase = true)
        ) "series" else item.apiType
        var episodes: List<Video> = emptyList()
        metaRepository.getMetaFromPrimaryAddon(type, item.id)
            .collect { networkResult ->
                if (networkResult is NetworkResult.Success) {
                    episodes = networkResult.data.videos
                }
            }
        return episodes
    }

    private var activeListPickerInput: LibraryEntryInput? = null

    companion object {
        private const val MAX_CARD_STATUSES = 64
        private const val TAG = "PosterOptionsCtrl"
    }
}

private fun buildCompletedMovieProgress(item: MetaPreview): WatchProgress {
    return WatchProgress(
        contentId = item.id,
        contentType = item.apiType,
        name = item.name,
        poster = item.poster,
        backdrop = item.backdropUrl,
        logo = item.logo,
        videoId = item.id,
        season = null,
        episode = null,
        episodeTitle = null,
        position = 1L,
        duration = 1L,
        lastWatched = System.currentTimeMillis(),
        progressPercent = 100f
    )
}

/** M67: el mismo criterio de serie que usa el menú largo para elegir su escritor de Visto. */
private fun MetaPreview.isSeriesType(): Boolean =
    apiType.equals("series", ignoreCase = true) ||
        apiType.equals("tv", ignoreCase = true) ||
        apiType.equals("anime", ignoreCase = true)

private fun MetaPreview.toLibraryEntryInput(addonBaseUrl: String?): LibraryEntryInput {
    val year = Regex("(\\d{4})").find(releaseInfo ?: "")
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    val parsedIds = parseContentIds(id)
    // The library renders as portrait. If the source MetaPreview was built for landscape
    // display (e.g. TMDB collection / more-like-this), its `poster` field holds the backdrop.
    // Prefer `rawPosterUrl` (the proper portrait) when available so the saved entry isn't a
    // stretched/cropped landscape inside a portrait card.
    val isLandscapeSource = posterShape == com.nuvio.tv.domain.model.PosterShape.LANDSCAPE
    val portraitPoster = rawPosterUrl?.takeIf { it.isNotBlank() }
    val savedPoster = if (isLandscapeSource && portraitPoster != null) portraitPoster else poster
    val savedShape = if (isLandscapeSource) com.nuvio.tv.domain.model.PosterShape.POSTER else posterShape
    return LibraryEntryInput(
        itemId = id,
        itemType = apiType,
        title = name,
        year = year,
        traktId = parsedIds.trakt,
        simklId = parsedIds.simkl,
        imdbId = parsedIds.imdb,
        tmdbId = parsedIds.tmdb,
        poster = savedPoster,
        posterShape = savedShape,
        background = background,
        logo = logo,
        description = description,
        releaseInfo = releaseInfo,
        imdbRating = imdbRating,
        genres = genres,
        addonBaseUrl = addonBaseUrl
    )
}
