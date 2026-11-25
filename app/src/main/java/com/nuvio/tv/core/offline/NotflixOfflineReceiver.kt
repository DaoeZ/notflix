package com.nuvio.tv.core.offline

import fi.iki.elonen.NanoHTTPD
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Cipher
import javax.crypto.AEADBadTagException
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.withLock

internal data class OfflineReceiveProgress(val received: Long = 0, val size: Long = 0, val complete: Boolean = false)

/** One video per code. Every authenticated frame identifies its offset in the video. */
internal class NotflixOfflineReceiver(
    address: String,
    private val directory: File,
    private val accept: (partial: File, filename: String) -> Unit,
) : NanoHTTPD(address, 0), AutoCloseable {
    private val random = SecureRandom()
    val code: String = buildString {
        val alphabet = "abcdefghijkmnpqrstuvwxyz23456789"
        repeat(16) { append(alphabet[random.nextInt(alphabet.length)]) }
    }
    private val uploadKey = offlineTransferKey(code, "upload")
    private val receiptKey = offlineTransferKey(code, "receipt")
    private val lock = ReentrantLock()
    private val state = MutableStateFlow(OfflineReceiveProgress())
    val progress = state.asStateFlow()
    private var partial: File? = null
    private var filename: String? = null
    private var lastFrameHash: ByteArray? = null
    private var lastReceipt: ByteArray? = null
    @Volatile private var closed = false

    override fun serve(session: IHTTPSession): Response {
        val length = session.headers["content-length"]?.toIntOrNull()
        if (session.method != Method.POST || session.uri != "/notflix-offline/v1" ||
            length == null || length !in 36..(2 * 1024 * 1024 + 36) ||
            session.headers.containsKey("transfer-encoding")
        ) return reject()
        if (!lock.tryLock()) return reject(Response.Status.SERVICE_UNAVAILABLE)
        try {
            if (closed) return reject(Response.Status.GONE)
            val payload = ByteArray(length)
            DataInputStream(session.inputStream).readFully(payload)
            val hash = MessageDigest.getInstance("SHA-256").digest(payload)
            lastReceipt?.takeIf { hash.contentEquals(lastFrameHash) }?.let { return receipt(it) }
            if (state.value.complete) return reject(Response.Status.GONE)
            val nonce = payload.copyOfRange(0, 12)
            val plain = cipher(Cipher.DECRYPT_MODE, uploadKey, nonce).doFinal(payload, 12, payload.size - 12)
            val data = ByteBuffer.wrap(plain)
            val offset = data.long
            if (offset == -1L) begin(data) else append(offset, data)
            val progress = state.value
            val acker = ByteBuffer.allocate(16).putLong(progress.received).putLong(progress.size).array()
            val ackNonce = ByteArray(12).also(random::nextBytes)
            val ack = ackNonce + cipher(Cipher.ENCRYPT_MODE, receiptKey, ackNonce).doFinal(acker)
            lastFrameHash = hash
            lastReceipt = ack
            return receipt(ack)
        } catch (_: AEADBadTagException) {
            return reject(Response.Status.UNAUTHORIZED)
        } catch (rejection: TransferRejected) {
            return reject(rejection.status)
        } catch (_: java.io.IOException) {
            return reject(Response.Status.INTERNAL_ERROR)
        } catch (_: Exception) {
            return reject()
        } finally {
            lock.unlock()
        }
    }

    private fun begin(data: ByteBuffer) {
        check(partial == null)
        require(data.remaining() in 9..256)
        val size = data.long
        val name = ByteArray(data.remaining()).also(data::get).decodeToString(throwOnInvalidSequence = true)
        require(name.none { it == '/' || it == '\\' || it.isISOControl() })
        if (offlineVideoExtension(null, name) == null) throw TransferRejected(Response.Status.UNSUPPORTED_MEDIA_TYPE)
        require(size > 0)
        if (size > directory.usableSpace - 16L * 1024 * 1024) throw TransferRejected(Response.Status.PAYLOAD_TOO_LARGE)
        directory.listFiles { f -> f.name.startsWith("receive-") && f.name.endsWith(".part") }
            ?.forEach { runCatching { it.delete() } }
        partial = File.createTempFile("receive-", ".part", directory)
        filename = name
        state.value = OfflineReceiveProgress(size = size)
    }

    private fun append(offset: Long, data: ByteBuffer) {
        val file = checkNotNull(partial)
        val current = state.value
        // Compare both the authenticated offset and the on-disk length.
        check(offset == current.received && file.length() == offset)
        val bytes = data.remaining()
        require(bytes > 0 && bytes.toLong() <= current.size - offset)
        try {
            FileOutputStream(file, true).use { it.write(data.array(), data.position(), bytes) }
            val received = offset + bytes
            val complete = received == current.size
            if (complete) {
                check(!closed)
                accept(file, checkNotNull(filename))
                partial = null
            }
            state.value = current.copy(received = received, complete = complete)
        } catch (error: Exception) {
            file.delete()
            closed = true
            throw error
        }
    }

    private fun cipher(mode: Int, key: SecretKeySpec, nonce: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, key, GCMParameterSpec(128, nonce)) }

    override fun close() {
        closed = true
        stop()
        lock.withLock {
            partial?.delete()
            partial = null
        }
    }

    private fun receipt(ack: ByteArray): Response = newFixedLengthResponse(
        Response.Status.OK, "application/octet-stream", ack.inputStream(), ack.size.toLong(),
    ).apply { addHeader("Cache-Control", "no-store") }

    private fun reject(status: Response.Status = Response.Status.BAD_REQUEST): Response =
        newFixedLengthResponse(status, MIME_PLAINTEXT, "").apply { closeConnection(true) }

    private class TransferRejected(val status: Response.Status) : RuntimeException()
}

internal fun offlineTransferKey(code: String, purpose: String): SecretKeySpec = SecretKeySpec(
    MessageDigest.getInstance("SHA-256").digest("notflix-offline-v1:$purpose:$code".toByteArray()),
    "AES",
)
