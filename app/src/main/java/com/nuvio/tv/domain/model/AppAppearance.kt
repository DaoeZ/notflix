package com.nuvio.tv.domain.model

enum class AppAppearance(val storedValue: String) {
    NUVIO("nuvio"), NOTFLIX("notflix");

    companion object {
        fun decode(value: Any?, schema: Any?): AppAppearance =
            if (schema != null && schema != 1) NUVIO
            else if (value == null) NOTFLIX
            else entries.firstOrNull { it.storedValue == value } ?: NUVIO
    }
}

/** A local session capability; never persisted or sent to a server. */
data class AppearanceContext(val profileId: Int, val generation: Long)

/** A disk selection observation, never an authorization or a PIN decision. */
data class PersistedAppearanceProfile(val profileId: Int, val revision: Long)
