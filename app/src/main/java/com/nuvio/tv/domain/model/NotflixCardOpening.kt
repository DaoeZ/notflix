package com.nuvio.tv.domain.model

/** M56: how long a focused title waits before its card expands; a local per-profile choice. */
enum class NotflixCardOpening(val delayMs: Int) {
    INSTANT(150),
    NORMAL(1200),
    SLOW(2000);

    val next: NotflixCardOpening get() = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = NORMAL

        /** Unknown or corrupt stored values keep the approved 1.2 s default untouched. */
        fun decode(value: Any?): NotflixCardOpening =
            entries.firstOrNull { it.delayMs == (value as? Number)?.toInt() } ?: DEFAULT
    }
}
