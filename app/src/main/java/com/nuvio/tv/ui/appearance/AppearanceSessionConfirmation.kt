package com.nuvio.tv.ui.appearance

import com.nuvio.tv.domain.model.AppearanceContext
import com.nuvio.tv.domain.model.PersistedAppearanceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppearanceSessionContext(
    val persisted: PersistedAppearanceProfile,
    val storage: AppearanceContext
)

// Identity is intentional: a token from a disposed controller must not compare
// equal to a new controller's request with the same numeric attempt/context.
class AppearanceConfirmationRequest internal constructor(
    val context: AppearanceSessionContext,
    val attempt: Long
)

/**
 * UI-session capability, closed initially and never persisted. M1 must begin an
 * attempt at the official selection boundary, cancel on exit/PIN cancellation,
 * and confirm only its successful hasSelectedProfileThisSession decision.
 * No PIN, remember-profile or auto-selection rules are reproduced here.
 */
class AppearanceSessionConfirmation {
    private var candidate: AppearanceSessionContext? = null
    private var pending: AppearanceConfirmationRequest? = null
    private var nextAttempt = 0L
    private val _confirmed = MutableStateFlow<AppearanceConfirmationRequest?>(null)
    val confirmed = _confirmed.asStateFlow()

    @Synchronized
    fun synchronize(context: AppearanceSessionContext?) {
        if (candidate != context) {
            candidate = context
            pending = null
            _confirmed.value = null
        }
    }

    @Synchronized
    fun begin(context: AppearanceSessionContext?): AppearanceConfirmationRequest? {
        synchronize(context)
        revoke()
        return context?.let { AppearanceConfirmationRequest(it, ++nextAttempt).also { pending = it } }
    }

    @Synchronized
    fun confirm(request: AppearanceConfirmationRequest, current: AppearanceSessionContext?): Boolean {
        synchronize(current)
        if (pending != request || current != request.context) return false
        pending = null
        _confirmed.value = request
        return true
    }

    @Synchronized
    fun authorized(current: AppearanceSessionContext?): AppearanceConfirmationRequest? {
        synchronize(current)
        return _confirmed.value?.takeIf { it.context == current }
    }

    @Synchronized
    fun revoke() {
        nextAttempt++
        pending = null
        _confirmed.value = null
    }
}
