package com.nuvio.tv.data.wikipedia

import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest

/** Only bounded, validated public extracts are stored. No HTML or official preference files. */
class WikipediaExtractCache(private val directory: File, private val now: () -> Long = System::currentTimeMillis) {
    data class Entry(val schema: Int, val parser: Int, val key: String, val created: Long, val expires: Long,
        val result: WikipediaExtract?, val outcome: String)
    private val gson = Gson()
    companion object {
        const val POSITIVE_TTL = 3L * 24 * 60 * 60 * 1000
        const val NEGATIVE_TTL = 15L * 60 * 1000
        const val ERROR_TTL = 2L * 60 * 1000
        const val MAX_ENTRY = 16 * 1024
        const val MAX_BYTES = 256 * 1024
        const val MAX_ENTRIES = 48
    }
    private fun file(key: String): File {
        val name = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(directory, "$name.json").also { require(it.canonicalFile.parentFile == directory.canonicalFile) }
    }
    fun read(binding: WikipediaBinding): Entry? = runCatching {
        prune()
        val f = file(binding.cacheKey())
        if (!f.isFile || f.length() !in 1..MAX_ENTRY.toLong()) return null
        val entry = gson.fromJson(f.readText(Charsets.UTF_8), Entry::class.java) ?: return null
        if (entry.schema != 1 || entry.parser != WikipediaPolicy.PARSER_VERSION || entry.key != binding.cacheKey() || entry.created <= 0 ||
            entry.created > now() || entry.expires <= now() || entry.expires < entry.created ||
            entry.expires - entry.created > POSITIVE_TTL || entry.outcome !in listOf("found", "absent", "error") ||
            (entry.outcome == "found") != (entry.result != null) ||
            (entry.result != null && (!entry.result.validFor(binding.identity) || entry.result.fetchedAtEpochMs > entry.created ||
                entry.created - entry.result.fetchedAtEpochMs > 60_000))) null else entry
    }.getOrNull()

    fun write(binding: WikipediaBinding, result: WikipediaExtract?, outcome: String, authorized: () -> Boolean): Boolean = runCatching {
        if (!authorized() || !binding.identity.valid() || outcome !in listOf("found", "absent", "error") ||
            (outcome == "found") != (result != null) || (result != null && !result.validFor(binding.identity))) return false
        val ttl = when (outcome) { "found" -> POSITIVE_TTL; "absent" -> NEGATIVE_TTL; else -> ERROR_TTL }
        val time = now(); val bytes = gson.toJson(Entry(1, WikipediaPolicy.PARSER_VERSION, binding.cacheKey(), time, time + ttl, result, outcome)).toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_ENTRY || !authorized()) return false
        if (!directory.exists() && !directory.mkdirs()) return false
        val destination = file(binding.cacheKey())
        if (!install(destination, bytes, authorized)) return false
        prune(); true
    }.getOrDefault(false)

    private fun install(destination: File, bytes: ByteArray, authorized: () -> Boolean): Boolean {
        val temporary = File(directory, destination.name + ".tmp")
        require(temporary.canonicalFile.parentFile == directory.canonicalFile)
        try {
            temporary.writeBytes(bytes)
            if (!authorized() || (destination.exists() && !destination.delete())) return false
            return temporary.renameTo(destination)
        } finally { temporary.delete() }
    }

    private fun prune() {
        directory.listFiles().orEmpty().filter {
            it.isFile && Regex("[a-f0-9]{64}\\.json\\.tmp").matches(it.name) && it.canonicalFile.parentFile == directory.canonicalFile
        }.forEach { it.delete() }
        val files = directory.listFiles().orEmpty().filter {
            it.isFile && Regex("[a-f0-9]{64}\\.json").matches(it.name) && it.canonicalFile.parentFile == directory.canonicalFile
        }.sortedBy { it.lastModified() }.toMutableList()
        var bytes = files.sumOf { it.length() }
        while (files.size > MAX_ENTRIES || bytes > MAX_BYTES) {
            val oldest = files.removeAt(0)
            val size = oldest.length(); if (oldest.delete()) bytes -= size
        }
    }
}
