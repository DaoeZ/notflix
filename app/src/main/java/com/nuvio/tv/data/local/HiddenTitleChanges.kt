package com.nuvio.tv.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nuvio.tv.domain.repository.HiddenTitleMetadata

/**
 * S3: dated hide/show per title. Stored as JSON in "hidden_changes":
 * {"movie:tt1": {"h": true, "t": 1794781683470}}; h = hidden, t = epoch ms of the change.
 * Merge rule shared with the mobile app: per title the newer change wins; a title with no
 * dated change (hidden before S3) stays hidden if either side hides it.
 */
internal data class HiddenTitleChange(val h: Boolean, val t: Long)

internal data class HiddenTitlesMerge(val keys: Set<String>, val metadataJson: String, val changesJson: String)

internal object HiddenTitleChanges {
    /** Shows older than this are forgotten so the list does not grow forever. */
    const val SHOW_RETENTION_MS = 180L * 24 * 60 * 60 * 1000

    private val gson = Gson()
    private val type = object : TypeToken<Map<String, HiddenTitleChange>>() {}.type

    fun decode(raw: String?): Map<String, HiddenTitleChange> =
        if (raw.isNullOrBlank()) emptyMap() else runCatching {
            gson.fromJson<Map<String, HiddenTitleChange>?>(raw, type).orEmpty()
        }.getOrDefault(emptyMap())

    fun record(raw: String?, key: String, hidden: Boolean, now: Long = System.currentTimeMillis()): String =
        gson.toJson(decode(raw) + (key to HiddenTitleChange(hidden, now)))

    fun merge(
        localKeys: Set<String>, localMetadata: String?, localChanges: String?,
        remoteKeys: Set<String>, remoteMetadata: String?, remoteChanges: String?,
        now: Long = System.currentTimeMillis(),
    ): HiddenTitlesMerge {
        val local = decode(localChanges)
        val remote = decode(remoteChanges)
        val changes = (local.keys + remote.keys).associateWith { key ->
            listOfNotNull(local[key], remote[key]).maxBy { it.t }
        }.filterValues { it.h || now - it.t < SHOW_RETENTION_MS }
        val keys = (localKeys + remoteKeys + changes.keys).filterTo(mutableSetOf()) { key ->
            changes[key]?.h ?: (key in localKeys || key in remoteKeys)
        }
        val metadata = HiddenTitlesStore.decodeMetadata(remoteMetadata) + HiddenTitlesStore.decodeMetadata(localMetadata)
        return HiddenTitlesMerge(keys, gson.toJson(metadata.filterKeys { it in keys }), gson.toJson(changes))
    }
}
