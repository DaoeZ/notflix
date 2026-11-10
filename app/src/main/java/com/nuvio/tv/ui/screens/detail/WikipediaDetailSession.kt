package com.nuvio.tv.ui.screens.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nuvio.tv.data.wikipedia.WikipediaBinding
import com.nuvio.tv.data.wikipedia.WikipediaExtract
import com.nuvio.tv.data.wikipedia.WikipediaIdentity
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.ui.appearance.AppearancePolicy
import com.nuvio.tv.ui.appearance.LocalAppearanceViewModel
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation

@Composable
internal fun rememberWikipediaDetailExtract(meta: Meta?): WikipediaExtract? {
    val appearance = LocalAppearanceViewModel.current
    if (!LocalNotflixPresentation.current.active || !AppearancePolicy.NOTFLIX_AVAILABLE || appearance == null) return null
    val selection by appearance.state.collectAsStateWithLifecycle()
    val identity = remember(meta?.id, meta?.imdbId, meta?.apiType) {
        meta?.let { WikipediaIdentity.resolve(it.id, it.imdbId, it.apiType) }
    }
    val context = selection.context
    val binding = if (context != null && identity != null && appearance.authorizesWikipedia(context, selection.selectionGeneration))
        WikipediaBinding(context, selection.selectionGeneration, identity) else null
    val model: WikipediaExtractViewModel = hiltViewModel()
    val state by model.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(model, owner, binding) {
        if (binding == null) model.revoke()
        else owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.show(binding) { appearance.authorizesWikipedia(binding.context, binding.selectionGeneration) }
        }
    }
    return state.extract.takeIf { binding != null && state.binding == binding &&
        appearance.authorizesWikipedia(binding.context, binding.selectionGeneration) }
}
