package com.nuvio.tv.ui.screens.profile

import android.content.Context

/*
 * M53: private avatar gallery. The list is third-party material, lives only in
 * app/src/notflixPrivate/assets (git-ignored) and is never part of the public build.
 * [parseNotflixAvatars] is pure so the JVM tests do not need Android.
 */

/** Visible order of the gallery categories; also the sort order of the catalogue. */
internal enum class NotflixAvatarCategory {
    HBO_MAX,
    PARAMOUNT_PLUS,
    DISNEY_PLUS,
    NUVIO,
    FOOTBALL,
    OTHER
}

internal data class NotflixAvatar(
    val name: String,
    val url: String,
    val category: NotflixAvatarCategory
)

/** Asset packaged only with -PnotflixPrivateAvatars=true (already the copied .txt, line by line). */
internal const val NOTFLIX_PRIVATE_AVATARS_ASSET = "notflix_private_avatars.txt"

/** Public gallery Xperience offers to every build; the QR panel points here. */
internal const val NOTFLIX_XPERIENCE_AVATARS_URL = "https://xperience-app.com/avatars"

private const val LINE_SEPARATOR = " - "
private val AvatarUrlPattern = Regex("""^https?://\S+$""")
private val AvatarCategoryPattern = Regex("""/avatars/([^/?#]+)""")

private fun notflixAvatarCategoryOf(url: String): NotflixAvatarCategory =
    when (AvatarCategoryPattern.find(url)?.groupValues?.getOrNull(1)?.lowercase()) {
        "hbo-max" -> NotflixAvatarCategory.HBO_MAX
        "paramount-plus" -> NotflixAvatarCategory.PARAMOUNT_PLUS
        "disney-plus" -> NotflixAvatarCategory.DISNEY_PLUS
        "nuvio" -> NotflixAvatarCategory.NUVIO
        "football" -> NotflixAvatarCategory.FOOTBALL
        // «custom» and any unknown segment fall into the visible «Otros» group.
        else -> NotflixAvatarCategory.OTHER
    }

/**
 * Reads the «Nombre - URL» lines, drops malformed ones and sorts by category (visible order) and
 * name. Repeated names with a different URL are kept: two profiles can legitimately share one.
 */
internal fun parseNotflixAvatars(raw: String): List<NotflixAvatar> =
    raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val separator = line.indexOf(LINE_SEPARATOR)
            if (separator <= 0) return@mapNotNull null
            val name = line.substring(0, separator).trim()
            val url = line.substring(separator + LINE_SEPARATOR.length).trim()
            if (name.isEmpty() || !AvatarUrlPattern.matches(url)) return@mapNotNull null
            NotflixAvatar(name = name, url = url, category = notflixAvatarCategoryOf(url))
        }
        .sortedWith(
            compareBy<NotflixAvatar> { it.category.ordinal }
                .thenBy { it.name.lowercase() }
                .thenBy { it.url }
        )
        .toList()

/** Reads the private asset when the build packaged it; an absent asset yields an empty gallery. */
internal fun loadNotflixPrivateAvatars(context: Context): List<NotflixAvatar> =
    runCatching {
        context.assets.open(NOTFLIX_PRIVATE_AVATARS_ASSET).bufferedReader(Charsets.UTF_8).use { reader ->
            parseNotflixAvatars(reader.readText())
        }
    }.getOrDefault(emptyList())
