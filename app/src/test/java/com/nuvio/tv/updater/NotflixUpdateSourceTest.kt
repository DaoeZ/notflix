package com.nuvio.tv.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * M54: the repository the updater reads must never fall back to NuvioTV while the Notflix
 * presentation is active. Empty means there is nothing to check at all.
 */
class NotflixUpdateSourceTest {

    @Test
    fun `notflix with an empty repository checks nothing`() {
        val source = NotflixUpdateSource.resolve(
            notflixActive = true,
            nuvioOwner = "NuvioMedia",
            nuvioRepo = "NuvioTV",
            notflixOwner = "",
            notflixRepo = ""
        )

        assertNull(source)
    }

    @Test
    fun `a half filled notflix repository counts as unpublished`() {
        assertNull(
            NotflixUpdateSource.resolve(
                notflixActive = true,
                nuvioOwner = "NuvioMedia",
                nuvioRepo = "NuvioTV",
                notflixOwner = "notflix",
                notflixRepo = "  "
            )
        )
    }

    @Test
    fun `notflix with a repository uses it instead of Nuvio`() {
        val source = NotflixUpdateSource.resolve(
            notflixActive = true,
            nuvioOwner = "NuvioMedia",
            nuvioRepo = "NuvioTV",
            notflixOwner = "notflix",
            notflixRepo = "NotflixTV"
        )

        assertEquals(NotflixUpdateSource.Repository(owner = "notflix", repo = "NotflixTV"), source)
    }

    @Test
    fun `a build with a Notflix repository updates from it in the nuvio appearance too`() {
        val source = NotflixUpdateSource.resolve(
            notflixActive = false,
            nuvioOwner = "NuvioMedia",
            nuvioRepo = "NuvioTV",
            notflixOwner = "notflix",
            notflixRepo = "NotflixTV"
        )

        assertEquals(NotflixUpdateSource.Repository(owner = "notflix", repo = "NotflixTV"), source)
    }

    @Test
    fun `nuvio appearance keeps the official repository when no Notflix one exists`() {
        val source = NotflixUpdateSource.resolve(
            notflixActive = false,
            nuvioOwner = "NuvioMedia",
            nuvioRepo = "NuvioTV",
            notflixOwner = "",
            notflixRepo = ""
        )

        assertEquals(NotflixUpdateSource.Repository(owner = "NuvioMedia", repo = "NuvioTV"), source)
    }

    @Test
    fun `repository links are built from the owner and repo`() {
        val repository = NotflixUpdateSource.Repository(owner = "notflix", repo = "NotflixTV")

        assertEquals("https://github.com/notflix/NotflixTV", repository.url)
        assertEquals("https://github.com/notflix/NotflixTV/blob/main/PRIVACY.md", repository.privacyUrl)
    }

    @Test
    fun `values are trimmed before use`() {
        val source = NotflixUpdateSource.resolve(
            notflixActive = true,
            notflixOwner = "  notflix ",
            notflixRepo = " NotflixTV  "
        )

        assertEquals(NotflixUpdateSource.Repository(owner = "notflix", repo = "NotflixTV"), source)
    }

    @Test
    fun `a release is only offered when it links to the selected repository`() {
        val notflix = NotflixUpdateSource.Repository(owner = "notflix", repo = "NotflixTV")
        val nuvio = NotflixUpdateSource.Repository(owner = "NuvioMedia", repo = "NuvioTV")

        assertEquals(
            true,
            NotflixUpdateSource.isFrom(notflix, "https://github.com/notflix/NotflixTV/releases/tag/1.0.0")
        )
        assertEquals(
            false,
            NotflixUpdateSource.isFrom(notflix, "https://github.com/NuvioMedia/NuvioTV/releases/tag/1.0.0")
        )
        // A repository whose name only extends the chosen one must not pass the prefix check.
        assertEquals(
            false,
            NotflixUpdateSource.isFrom(notflix, "https://github.com/notflix/NotflixTV2/releases/tag/1.0.0")
        )
        assertEquals(false, NotflixUpdateSource.isFrom(nuvio, null))
        assertEquals(false, NotflixUpdateSource.isFrom(null, "https://github.com/notflix/NotflixTV"))
    }
}
