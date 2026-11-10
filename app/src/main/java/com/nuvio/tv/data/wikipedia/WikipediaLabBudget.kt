package com.nuvio.tv.data.wikipedia

import com.google.gson.Gson
import java.io.File

/** Private FullDebug evaluation guard: durable reservations before sending, never reset on errors. */
class WikipediaLabBudget(private val file: File, private val now: () -> Long = System::currentTimeMillis) {
    data class Snapshot(val schema: Int = 1, val requests: Int = 0, val identities: List<String> = emptyList(), val notBefore: Long = 0)
    private val gson = Gson()
    @Synchronized fun snapshot(): Snapshot? = runCatching {
        if (!file.exists()) return if (File(file.parentFile, file.name + ".guard").exists()) null else Snapshot()
        if (!file.isFile || file.length() !in 1..8192L) return null
        val s = gson.fromJson(file.readText(Charsets.UTF_8), Snapshot::class.java) ?: return null
        if (s.schema != 1 || s.requests !in 0..30 || s.identities.size > 6 ||
            s.identities.distinct().size != s.identities.size || s.requests < s.identities.size ||
            s.identities.any { !WikipediaPolicy.IMDB.matches(it) } || s.notBefore < 0) null else s
    }.getOrNull()

    @Synchronized fun reserve(imdb: String): Snapshot? {
        if (!WikipediaPolicy.IMDB.matches(imdb)) return null
        val previous = snapshot() ?: return null
        if (previous.requests >= 30 || now() < previous.notBefore || (imdb !in previous.identities && previous.identities.size >= 6)) return null
        val next = previous.copy(requests = previous.requests + 1, identities = (previous.identities + imdb).distinct())
        return if (persist(next)) next else null
    }

    @Synchronized fun deferUntil(time: Long): Boolean {
        val previous = snapshot() ?: return false
        return persist(previous.copy(notBefore = maxOf(previous.notBefore, time)))
    }
    private fun persist(value: Snapshot): Boolean = runCatching {
        val parent = requireNotNull(file.parentFile)
        if (!parent.exists() && !parent.mkdirs()) return false
        require(file.canonicalFile.parentFile == parent.canonicalFile)
        // Missing/corrupt state after a crash must close the budget, never silently reset it.
        val guard = File(parent, file.name + ".guard"); require(guard.canonicalFile.parentFile == parent.canonicalFile)
        if (!guard.exists() && !guard.createNewFile()) return false
        val tmp = File(parent, file.name + ".tmp"); require(tmp.canonicalFile.parentFile == parent.canonicalFile)
        tmp.writeText(gson.toJson(value), Charsets.UTF_8)
        if (file.exists() && !file.delete()) { tmp.delete(); return false }
        if (!tmp.renameTo(file)) { tmp.delete(); return false }
        true
    }.getOrDefault(false)
}
