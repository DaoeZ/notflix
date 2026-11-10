package com.nuvio.tv.ui.screens.detail

import androidx.lifecycle.ViewModel
import com.nuvio.tv.data.local.NotflixAppearanceStore
import com.nuvio.tv.data.wikipedia.WikipediaBinding
import com.nuvio.tv.data.wikipedia.WikipediaExtract
import com.nuvio.tv.data.wikipedia.WikipediaExtractRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WikipediaDetailState(val binding: WikipediaBinding? = null, val extract: WikipediaExtract? = null)

/** Own optional feature only. The official detail VM/actions never wait for this request. */
@HiltViewModel
class WikipediaExtractViewModel @Inject constructor(
    private val repository: WikipediaExtractRepository,
    private val store: NotflixAppearanceStore
) : ViewModel() {
    private data class Lease(val binding: WikipediaBinding, val job: Job?)
    @Volatile private var active: Lease? = null
    private val _state = MutableStateFlow(WikipediaDetailState())
    val state = _state.asStateFlow()

    suspend fun show(binding: WikipediaBinding, authorized: () -> Boolean) {
        val mine = Lease(binding, currentCoroutineContext()[Job])
        val previous = active; active = mine
        if (previous?.job !== mine.job) previous?.job?.cancel()
        _state.value = WikipediaDetailState()
        fun allowed() = active === mine && repository.available && binding.identity.valid() &&
            store.isCurrent(binding.context) && authorized()
        try {
            if (!allowed()) return
            val result = repository.find(binding, ::allowed)
            currentCoroutineContext().ensureActive()
            if (allowed() && result?.validFor(binding.identity) == true) _state.value = WikipediaDetailState(binding, result)
            awaitCancellation()
        } finally {
            if (active === mine) { active = null; _state.value = WikipediaDetailState() }
        }
    }
    fun revoke() {
        val previous = active; active = null; _state.value = WikipediaDetailState(); previous?.job?.cancel()
    }
    override fun onCleared() { revoke(); super.onCleared() }
}
