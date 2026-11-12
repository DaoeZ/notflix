package com.nuvio.tv.domain.model

/** M64: per-profile card size for Notflix; Large keeps the current measurements untouched. */
enum class NotflixCardSize(val percent: Int) {
    SMALL(75),
    MEDIUM(87),
    LARGE(100);

    val scale: Float get() = percent / 100f
    val next: NotflixCardSize get() = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = LARGE

        /** Unknown or corrupt stored values keep the approved current size untouched. */
        fun decode(value: Any?): NotflixCardSize =
            entries.firstOrNull { it.percent == (value as? Number)?.toInt() } ?: DEFAULT
    }
}
