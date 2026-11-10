package com.nuvio.tv.ui.appearance

/** A successful official UI decision, not an observation of the persisted ID. */
data class OfficialAppearanceDecision(
    val serial: Long,
    val profileId: Int,
    val accountKey: String,
    val storageRevision: Long
)

/**
 * Keeps a decision pending when its disk publication arrives after the callback.
 * A confirmed/retired decision cannot authorize a later A -> B -> A or reset.
 * The controller is owned above the presentation host, so chrome is irrelevant.
 */
class OfficialAppearanceSessionBinding(
    private val begin: (Int) -> AppearanceConfirmationRequest?,
    private val confirm: (AppearanceConfirmationRequest) -> Boolean,
    private val cancel: () -> Unit
) {
    private var decision: OfficialAppearanceDecision? = null
    private var confirmed: AppearanceSessionContext? = null
    private var retired = false

    /** Returns true when the saved official decision must also be retired. */
    fun update(
        next: OfficialAppearanceDecision?,
        available: AppearanceSessionContext?,
        storageRevision: Long,
        accountKey: String?
    ): Boolean {
        if (next != decision) {
            cancel()
            decision = next
            confirmed = null
            retired = false
        }
        val selected = decision ?: return false
        if (retired) return true
        if (accountKey != selected.accountKey || storageRevision != selected.storageRevision ||
            (confirmed != null && confirmed != available)) {
            close()
            return true
        }
        if (confirmed != null || available?.persisted?.profileId != selected.profileId) return false
        val request = begin(selected.profileId) ?: return false
        if (confirm(request)) confirmed = request.context
        return false
    }

    fun close() {
        cancel()
        confirmed = null
        retired = true
    }
}
