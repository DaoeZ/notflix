package com.nuvio.tv.core.server

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nuvio.tv.data.local.NotflixIntegrationCredentials
import com.nuvio.tv.data.local.normalizeNotflixProviderUrl
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

/**
 * Serves the phone page that types the Notflix provider keys. Every request must carry the token that
 * is printed in the QR for this session, so other devices on the network cannot read or change keys.
 * The server runs only while the QR overlay is open.
 */
class NotflixKeysConfigServer(
    private val token: String,
    private val currentProvider: () -> NotflixIntegrationCredentials,
    private val onSave: (NotflixIntegrationCredentials) -> Unit,
    private val labels: () -> Map<String, String>,
    port: Int
) : NanoHTTPD(port) {
    private val gson = Gson()
    private val bodyType = object : TypeToken<Map<String, Any?>>() {}.type

    override fun serve(session: IHTTPSession): Response {
        if (session.parms["t"] != token) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden")
        }
        return when {
            session.method == Method.GET && session.uri == "/" -> newFixedLengthResponse(
                Response.Status.OK,
                "text/html; charset=utf-8",
                NotflixKeysWebPage.html(labels())
            )
            session.method == Method.GET && session.uri == "/api/keys" -> status()
            session.method == Method.POST && session.uri == "/api/keys" -> save(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    /** Only hints are sent back to the phone, never the stored secrets. */
    private fun status(): Response {
        val current = currentProvider()
        val body = mapOf(
            "tmdb" to mapOf("configured" to current.tmdbApiKey.isNotBlank(), "hint" to hint(current.tmdbApiKey)),
            "trakt" to mapOf(
                "configured" to current.traktClientId.isNotBlank(),
                "hint" to hint(current.traktClientId)
            ),
            "introDb" to mapOf("value" to current.introDbBaseUrl)
        )
        return json(Response.Status.OK, body)
    }

    private fun save(session: IHTTPSession): Response {
        val body = readUtf8Body(session)
        val parsed = runCatching { gson.fromJson<Map<String, Any?>>(body, bodyType) }.getOrNull()
            ?: return json(Response.Status.BAD_REQUEST, mapOf("error" to "invalid"))
        val current = currentProvider()

        val tmdb = text(parsed, "tmdbApiKey")
        val traktId = text(parsed, "traktClientId")
        val traktSecret = text(parsed, "traktClientSecret")
        val introDbInput = text(parsed, "introDbBaseUrl")
        if (listOf(tmdb, traktId, traktSecret, introDbInput).any { it.length > MAX_VALUE_LENGTH }) {
            return json(Response.Status.BAD_REQUEST, mapOf("error" to "too-long"))
        }
        if (introDbInput.isNotBlank() && normalizeNotflixProviderUrl(introDbInput).isEmpty()) {
            return json(Response.Status.BAD_REQUEST, mapOf("error" to "invalid-url"))
        }

        val clearTrakt = parsed["clearTrakt"] == true
        val updated = NotflixIntegrationCredentials(
            tmdbApiKey = when {
                parsed["clearTmdb"] == true -> ""
                tmdb.isNotBlank() -> tmdb
                else -> current.tmdbApiKey
            },
            introDbBaseUrl = when {
                parsed["clearIntroDb"] == true -> ""
                introDbInput.isNotBlank() -> normalizeNotflixProviderUrl(introDbInput)
                else -> current.introDbBaseUrl
            },
            traktClientId = when {
                clearTrakt -> ""
                traktId.isNotBlank() -> traktId
                else -> current.traktClientId
            },
            traktClientSecret = when {
                clearTrakt -> ""
                traktSecret.isNotBlank() -> traktSecret
                else -> current.traktClientSecret
            }
        ).normalized()
        onSave(updated)
        return json(Response.Status.OK, mapOf("status" to "saved"))
    }

    private fun text(parsed: Map<String, Any?>, key: String): String =
        (parsed[key] as? String)?.trim().orEmpty()

    private fun hint(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.isEmpty()) "" else "••••" + trimmed.takeLast(4)
    }

    private fun json(status: Response.Status, body: Map<String, Any?>): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", gson.toJson(body))

    private fun readUtf8Body(session: IHTTPSession): String {
        val length = session.headers["content-length"]?.toIntOrNull() ?: return ""
        if (length <= 0 || length > MAX_BODY_BYTES) return ""
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = session.inputStream.read(buffer, offset, length - offset)
            if (read <= 0) break
            offset += read
        }
        return String(buffer, 0, offset, StandardCharsets.UTF_8)
    }

    companion object {
        private const val MAX_VALUE_LENGTH = 512
        private const val MAX_BODY_BYTES = 8 * 1024
        private const val TOKEN_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
        private const val DEFAULT_PORT = 8095

        /** A new token for each QR session. Twelve characters from an unambiguous alphabet. */
        fun newToken(random: SecureRandom = SecureRandom()): String =
            buildString { repeat(12) { append(TOKEN_ALPHABET[random.nextInt(TOKEN_ALPHABET.length)]) } }

        fun startOnAvailablePort(
            token: String,
            currentProvider: () -> NotflixIntegrationCredentials,
            onSave: (NotflixIntegrationCredentials) -> Unit,
            labels: () -> Map<String, String>,
            startPort: Int = DEFAULT_PORT,
            maxAttempts: Int = 10
        ): NotflixKeysConfigServer? {
            for (port in startPort until startPort + maxAttempts) {
                try {
                    val server = NotflixKeysConfigServer(token, currentProvider, onSave, labels, port)
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    return server
                } catch (_: IOException) {
                    // Port taken: try the next one.
                }
            }
            return null
        }
    }
}
