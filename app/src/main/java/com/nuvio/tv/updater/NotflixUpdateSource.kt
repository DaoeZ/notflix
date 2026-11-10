package com.nuvio.tv.updater

import com.nuvio.tv.BuildConfig

/**
 * M54: «Acerca de» and the in-app updater never point at NuvioTV while the Notflix presentation is
 * active. The public Notflix repository is filled in at build time and stays empty until it exists;
 * with no repository there is nothing to check and nothing to link to.
 *
 * Pure on purpose: the selection is decided here so the ViewModel, the background job and the
 * settings screens share one answer and a JVM test can cover it without Android.
 */
object NotflixUpdateSource {

    data class Repository(val owner: String, val repo: String) {
        val url: String get() = "https://github.com/$owner/$repo"
        val privacyUrl: String get() = "$url/blob/main/PRIVACY.md"
    }

    /**
     * Repository to query for releases. A build with a Notflix repository always updates from it,
     * whatever the appearance: an official Nuvio APK is a different app. Without one, Notflix gets
     * null (nothing to check) and Nuvio keeps its historical repository.
     */
    fun resolve(
        notflixActive: Boolean,
        nuvioOwner: String = BuildConfig.GITHUB_OWNER,
        nuvioRepo: String = BuildConfig.GITHUB_REPO,
        notflixOwner: String = BuildConfig.NOTFLIX_GITHUB_OWNER,
        notflixRepo: String = BuildConfig.NOTFLIX_GITHUB_REPO
    ): Repository? =
        notflixRepository(notflixOwner, notflixRepo)
            ?: if (notflixActive) null else Repository(nuvioOwner.trim(), nuvioRepo.trim())

    /** Both values are required; a half-filled pair counts as not published. */
    fun notflixRepository(owner: String, repo: String): Repository? {
        val cleanOwner = owner.trim()
        val cleanRepo = repo.trim()
        return if (cleanOwner.isNotEmpty() && cleanRepo.isNotEmpty()) {
            Repository(cleanOwner, cleanRepo)
        } else {
            null
        }
    }

    /** The repository this build would publish for Notflix, from the compiled-in values. */
    fun notflixRepository(): Repository? =
        notflixRepository(BuildConfig.NOTFLIX_GITHUB_OWNER, BuildConfig.NOTFLIX_GITHUB_REPO)

    /**
     * True when a release links back to the given repository. A banner kept in memory from another
     * repository (for instance after switching appearance) is never offered.
     */
    fun isFrom(repository: Repository?, releaseUrl: String?): Boolean {
        val url = releaseUrl?.trim().orEmpty()
        if (repository == null) return false
        // The separator stops a sibling repository (owner/NotflixTV2) from passing a plain prefix check.
        return url == repository.url || url.startsWith(repository.url + "/")
    }
}
