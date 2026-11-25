package com.nuvio.tv.core.offline

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotflixOfflineReceiverTest {
    @get:Rule val folder = TemporaryFolder()
    private val random = SecureRandom()

    @Test
    fun copiesTheActualFileInOrderAndAuthenticatesEveryReceipt() {
        val name = "Película.mkv"
        val split = 2 * 1024 * 1024
        val video = ByteArray(split + 53) { it.toByte() }
        val result = File(folder.root, "result.mkv")
        val receiver = NotflixOfflineReceiver("127.0.0.1", folder.root) { partial, receivedName ->
            assertEquals(name, receivedName)
            check(partial.renameTo(result))
        }
        receiver.use {
            it.start(2_000, true)
            val metadata = ByteBuffer.allocate(16 + name.toByteArray().size)
                .putLong(-1).putLong(video.size.toLong()).put(name.toByteArray()).array()
            val init = encrypted(it.code, metadata)
            assertEquals(401, send(it, encrypted("unrelated-code", metadata)).first)
            val (code, response) = send(it, init)
            assertEquals(200, code)
            assertTrue(response.isNotEmpty())
            assertEquals(401, send(it, response).first) // A receipt cannot be used as an upload.
            val first = ByteBuffer.allocate(8 + split).putLong(0).put(video, 0, split).array()
            val rest = ByteBuffer.allocate(8 + video.size - split).putLong(split.toLong())
                .put(video, split, video.size - split).array()
            assertEquals(400, send(it, encrypted(it.code, rest)).first)
            val firstFrame = encrypted(it.code, first)
            val (firstStatus, firstReceipt) = send(it, firstFrame)
            assertEquals(200, firstStatus)
            assertArrayEquals(firstReceipt, send(it, firstFrame).second)
            assertFalse(result.exists())
            assertEquals(400, send(it, encrypted(it.code, first)).first)
            val lastFrame = encrypted(it.code, rest)
            val (done, finalReceipt) = send(it, lastFrame)
            assertEquals(200, done)
            val receipt = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, offlineTransferKey(it.code, "receipt"), GCMParameterSpec(128, finalReceipt.copyOfRange(0, 12)))
            }.doFinal(finalReceipt.copyOfRange(12, finalReceipt.size))
            assertEquals(video.size.toLong(), ByteBuffer.wrap(receipt).long)
            assertTrue(it.progress.value.complete)
            assertArrayEquals(video, result.readBytes())
            assertArrayEquals(finalReceipt, send(it, lastFrame).second)
            assertEquals(410, send(it, init).first)
        }
    }

    @Test
    fun closingTheReceiverDeletesAnIncompleteCopy() {
        val receiver = NotflixOfflineReceiver("127.0.0.1", folder.root) { _, _ ->
            error("An incomplete copy must not be added")
        }
        receiver.use {
            it.start(2_000, true)
            val name = "movie.mp4".toByteArray()
            val frame = ByteBuffer.allocate(16 + name.size).putLong(-1).putLong(100).put(name).array()
            assertEquals(200, send(it, encrypted(it.code, frame)).first)
            assertTrue(folder.root.listFiles().orEmpty().any { file -> file.extension == "part" })
        }
        assertTrue(folder.root.listFiles().orEmpty().isEmpty())
    }

    private fun encrypted(code: String, data: ByteArray): ByteArray {
        val nonce = ByteArray(12).also(random::nextBytes)
        return nonce + Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, offlineTransferKey(code, "upload"), GCMParameterSpec(128, nonce))
        }.doFinal(data)
    }

    private fun send(receiver: NotflixOfflineReceiver, bytes: ByteArray): Pair<Int, ByteArray> {
        val connection = URL("http://127.0.0.1:${receiver.listeningPort}/notflix-offline/v1").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.readTimeout = 2_500
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val stream = if (code == 200) connection.inputStream else connection.errorStream
            code to (stream?.use { it.readBytes() } ?: byteArrayOf())
        } finally {
            connection.disconnect()
        }
    }
}
