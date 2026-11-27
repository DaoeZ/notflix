package com.nuvio.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class NotflixTitleFeedback { NONE, LIKE, DISLIKE }

/**
 * Per-profile "Like" / "Not interested" feedback on titles.
 * - Like boosts the title (and its related titles) in "For You".
 * - Dislike removes the title from "For You" and downweights similar titles (both related items and shared genres).
 */
object NotflixTitleFeedbackStore {
    private const val PREFS_NAME = "notflix_title_feedback"

    private var prefs: SharedPreferences? = null
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun initialize(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _revision.update { it + 1 }
    }

    private fun likesKey(profileId: Int) = "p${profileId}_likes"
    private fun dislikesKey(profileId: Int) = "p${profileId}_dislikes"

    private fun normalizeType(type: String): String = when (type.trim().lowercase()) {
        "tv", "show", "tvshow", "series" -> "series"
        else -> type.trim().lowercase().ifBlank { "movie" }
    }

    private fun normalizeId(id: String): String = id.trim().lowercase()

    private fun normalizeGenres(genres: List<String>): String = genres.asSequence()
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() && '|' !in it && ',' !in it }
        .distinct()
        .take(6)
        .joinToString(",")

    private fun encode(type: String, id: String, genres: List<String>): String =
        "${normalizeType(type)}|${normalizeId(id)}|${normalizeGenres(genres)}"

    private data class Entry(val type: String, val id: String, val genres: Set<String>)

    private fun decode(raw: String): Entry? {
        val parts = raw.split('|')
        if (parts.size < 2) return null
        val type = parts[0].trim().lowercase().ifBlank { return null }
        val id = parts[1].trim().lowercase().ifBlank { return null }
        val genres = parts.getOrNull(2).orEmpty()
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        return Entry(type, id, genres)
    }

    private fun readSet(key: String): Set<String> =
        prefs?.getStringSet(key, null)?.toSet().orEmpty()

    private fun writeSets(likesKey: String, likes: Set<String>, dislikesKey: String, dislikes: Set<String>) {
        prefs?.edit()
            ?.putStringSet(likesKey, likes)
            ?.putStringSet(dislikesKey, dislikes)
            ?.apply()
        _revision.update { it + 1 }
    }

    fun get(profileId: Int, type: String, id: String): NotflixTitleFeedback {
        val normType = normalizeType(type)
        val normId = normalizeId(id)
        if (normId.isEmpty()) return NotflixTitleFeedback.NONE
        if (readSet(likesKey(profileId)).any { decode(it)?.let { e -> e.type == normType && e.id == normId } == true }) {
            return NotflixTitleFeedback.LIKE
        }
        if (readSet(dislikesKey(profileId)).any { decode(it)?.let { e -> e.type == normType && e.id == normId } == true }) {
            return NotflixTitleFeedback.DISLIKE
        }
        return NotflixTitleFeedback.NONE
    }

    fun toggleLike(profileId: Int, type: String, id: String, genres: List<String> = emptyList()) {
        val normType = normalizeType(type)
        val normId = normalizeId(id)
        if (normId.isEmpty()) return
        val lk = likesKey(profileId)
        val dk = dislikesKey(profileId)
        val likes = readSet(lk).toMutableSet()
        val dislikes = readSet(dk).toMutableSet()
        val wasLiked = likes.removeAll { decode(it)?.let { e -> e.type == normType && e.id == normId } == true }
        dislikes.removeAll { decode(it)?.let { e -> e.type == normType && e.id == normId } == true }
        if (!wasLiked) {
            likes += encode(normType, normId, genres)
        }
        writeSets(lk, likes, dk, dislikes)
    }

    fun toggleDislike(profileId: Int, type: String, id: String, genres: List<String> = emptyList()) {
        val normType = normalizeType(type)
        val normId = normalizeId(id)
        if (normId.isEmpty()) return
        val lk = likesKey(profileId)
        val dk = dislikesKey(profileId)
        val likes = readSet(lk).toMutableSet()
        val dislikes = readSet(dk).toMutableSet()
        likes.removeAll { decode(it)?.let { e -> e.type == normType && e.id == normId } == true }
        val wasDisliked = dislikes.removeAll { decode(it)?.let { e -> e.type == normType && e.id == normId } == true }
        if (!wasDisliked) {
            dislikes += encode(normType, normId, genres)
        }
        writeSets(lk, likes, dk, dislikes)
    }

    fun likedEntries(profileId: Int): List<Pair<String, String>> =
        readSet(likesKey(profileId)).mapNotNull { decode(it)?.let { e -> e.type to e.id } }

    fun dislikedEntries(profileId: Int): List<Pair<String, String>> =
        readSet(dislikesKey(profileId)).mapNotNull { decode(it)?.let { e -> e.type to e.id } }

    fun dislikedIds(profileId: Int): Set<String> =
        readSet(dislikesKey(profileId)).mapNotNullTo(mutableSetOf()) { decode(it)?.id }

    fun likedGenres(profileId: Int): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        readSet(likesKey(profileId)).forEach { raw ->
            decode(raw)?.genres?.forEach { g -> counts[g] = (counts[g] ?: 0) + 1 }
        }
        return counts
    }

    fun dislikedGenres(profileId: Int): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        readSet(dislikesKey(profileId)).forEach { raw ->
            decode(raw)?.genres?.forEach { g -> counts[g] = (counts[g] ?: 0) + 1 }
        }
        return counts
    }
}
