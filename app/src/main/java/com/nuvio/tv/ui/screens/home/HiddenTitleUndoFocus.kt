package com.nuvio.tv.ui.screens.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** UI-local bridge shared by the existing grid/Home and its existing notice. */
@Stable
internal class HiddenTitleUndoFocus {
    var requester: FocusRequester? = null
    var hasFocus by mutableStateOf(false)
    private var restore: (() -> Unit)? = null
    fun enter(onReturn: () -> Unit): Boolean {
        val target = requester ?: return false
        restore = onReturn
        val acquired = runCatching { target.requestFocus() }.getOrDefault(false)
        if (!acquired) restore = null else hasFocus = true
        return acquired
    }
    fun leave() { val target = restore; restore = null; hasFocus = false; runCatching { target?.invoke() } }
}

internal val LocalHiddenTitleUndoFocus = staticCompositionLocalOf<HiddenTitleUndoFocus?> { null }

@Composable
internal fun HiddenTitleUndoScope(active: Boolean = true, content: @Composable () -> Unit) {
    val focus = remember { HiddenTitleUndoFocus() }
    CompositionLocalProvider(LocalHiddenTitleUndoFocus provides if (active) focus else null, content = content)
}

/** Five visible seconds, restarted on leaving the notice; never expires while it owns focus. */
internal class HiddenTitleUndoTimeout(private val scope: CoroutineScope, private val expire: (HiddenTitleUndo) -> Unit) {
    private var job: Job? = null
    private var current: HiddenTitleUndo? = null
    private var focused = false
    private var version = 0L
    fun start(undo: HiddenTitleUndo) { current = undo; focused = false; arm() }
    fun focus(undo: HiddenTitleUndo, value: Boolean) {
        if (current != undo || focused == value) return
        focused = value; arm()
    }
    fun cancel() { version++; job?.cancel(); job = null; current = null }
    private fun arm() {
        val serial = ++version
        job?.cancel()
        val undo = current ?: return
        if (focused) return
        job = scope.launch {
            delay(5_000)
            if (serial == version && current == undo && !focused) { current = null; expire(undo) }
        }
    }
}
