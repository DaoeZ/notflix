package com.nuvio.tv.core.offline

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
internal data class OfflineVideo(
    val key: String,
    val title: String,
    val filename: String,
    val downloadId: Long? = null,
    val transferred: Boolean = false,
)

internal data class OfflineVideoState(
    val video: OfflineVideo,
    val playableUri: Uri?,
    val isDownloading: Boolean,
    val percent: Int?,
)

private val extensions = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "mpg", "mpeg", "ts")

internal fun offlineVideoExtension(urlPath: String?, filename: String?): String? {
    val pathExtension = urlPath.orEmpty().substringAfterLast('.', "").lowercase()
    if (pathExtension in setOf("m3u8", "mpd") ||
        filename.orEmpty().substringAfterLast('.', "").lowercase() in setOf("m3u8", "mpd")) return null
    return filename.orEmpty().substringAfterLast('.', "").lowercase().takeIf { it in extensions }
        ?: pathExtension.takeIf { it in extensions }
}

@Singleton
class NotflixOfflineVideos @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager get() = checkNotNull(context.getSystemService(DownloadManager::class.java))
    private val preferences = context.getSharedPreferences("notflix_offline_videos", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val serializer = ListSerializer(OfflineVideo.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    internal suspend fun list(owner: String): List<OfflineVideoState> = transaction {
        read(owner).map { video ->
            val id = video.downloadId
            if (id == null) {
                val uri = localFile(video)?.takeIf { it.isFile }?.let(Uri::fromFile)
                OfflineVideoState(video, uri, false, null)
            } else {
                val status = manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val code = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val received = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val percent = if (total > 0) (received.toDouble() * 100 / total).toInt().coerceIn(0, 100) else null
                    code to percent
                }
                OfflineVideoState(
                    video,
                    if (status?.first == DownloadManager.STATUS_SUCCESSFUL) manager.getUriForDownloadedFile(id) else null,
                    status?.first in setOf(DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_RUNNING),
                    status?.second,
                )
            }
        }
    }

    internal suspend fun enqueue(
        owner: String, title: String, url: Uri, filename: String?, headers: Map<String, String>,
        stillOwnsDownloads: () -> Boolean,
    ) = transaction {
        check(stillOwnsDownloads())
        require(url.scheme in setOf("https", "http") && !url.host.isNullOrBlank())
        val extension = requireNotNull(offlineVideoExtension(url.path, filename))
        val key = UUID.randomUUID().toString()
        val target = "$key.$extension"
        val stored = read(owner)
        val request = DownloadManager.Request(url)
            .setTitle(title)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(true)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, target)
        headers.forEach { (name, value) -> request.addRequestHeader(name, value) }
        val id = manager.enqueue(request)
        try {
            write(owner, stored + OfflineVideo(key, title, target, downloadId = id))
        } catch (error: Exception) {
            manager.remove(id)
            throw error
        }
    }

    internal suspend fun acceptTransfer(
        owner: String, partial: File, filename: String, stillOwnsDownloads: () -> Boolean,
    ) = transaction {
        check(stillOwnsDownloads())
        val extension = requireNotNull(offlineVideoExtension(null, filename))
        val key = UUID.randomUUID().toString()
        val records = read(owner)
        val video = OfflineVideo(key, filename.substringBeforeLast('.'), "$key.$extension", transferred = true)
        val file = requireNotNull(localFile(video))
        check(partial.renameTo(file))
        try {
            write(owner, records + video)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    internal fun transferDirectory(): File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "movies"),
        "notflix-received",
    ).apply { check(isDirectory || mkdirs()) }

    private fun localFile(video: OfflineVideo): File? =
        if (video.transferred) File(transferDirectory(), video.filename) else null

    internal suspend fun remove(owner: String, key: String) = transaction {
        val records = read(owner)
        val video = records.firstOrNull { it.key == key } ?: return@transaction
        video.downloadId?.let { manager.remove(it) }
        localFile(video)?.delete()
        write(owner, records.filterNot { it.key == key })
    }

    suspend fun clearAccountFiles() = transaction { clearOwners(preferences.all.keys) }

    suspend fun clearProfile(profileId: Int) = transaction {
        clearOwners(preferences.all.keys.filter { it.endsWith(":$profileId") })
    }

    private fun clearOwners(owners: Collection<String>) {
        if (owners.isEmpty()) return
        val videos = owners.flatMap(::read)
        val transfers = videos.mapNotNull(OfflineVideo::downloadId)
        if (transfers.isNotEmpty()) manager.remove(*transfers.toLongArray())
        videos.forEach { localFile(it)?.delete() }
        val editor = preferences.edit()
        owners.forEach(editor::remove)
        check(editor.commit())
    }

    private suspend fun <T> transaction(action: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { action() } }

    private fun read(owner: String): List<OfflineVideo> = preferences.getString(owner, null)
        ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private fun write(owner: String, videos: List<OfflineVideo>) {
        check(preferences.edit().putString(owner, json.encodeToString(serializer, videos)).commit())
    }
}

internal fun offlineOwnerKey(accountId: String, profileId: Int): String =
    MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray())
        .joinToString("") { "%02x".format(it) } + ":$profileId"
