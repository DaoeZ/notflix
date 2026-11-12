package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M67: el panel de la tarjeta ofrece las mismas acciones, en el mismo orden y con los mismos
 * escritores en Inicio, en la carpeta de colección y en «Ver todo». Las tres pantallas construyen
 * el host con [notflixCardActionHost] y la política de [notflixCardActionOrder].
 */
class NotflixCardActionParityTest {

    private fun meta(id: String, type: String = "movie"): MetaPreview = MetaPreview(
        id = id,
        type = ContentType.fromString(type),
        rawType = type,
        name = "Título $id",
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = null,
        imdbRating = null,
        genres = emptyList()
    )

    @Test
    fun `the panel shows every action in the same order`() {
        assertEquals(
            listOf(
                NotflixCardAction.PLAY,
                NotflixCardAction.INFO,
                NotflixCardAction.LIBRARY,
                NotflixCardAction.WATCHED,
                NotflixCardAction.SOUND,
                NotflixCardAction.HIDE
            ),
            notflixCardActionOrder(canPlay = true, watchable = true, hasSound = true)
        )
    }

    @Test
    fun `only what the title or the screen cannot do is missing`() {
        // Una pantalla que no puede reproducir no dibuja Reproducir; Mi lista y Ocultar siguen.
        assertEquals(
            listOf(NotflixCardAction.INFO, NotflixCardAction.LIBRARY, NotflixCardAction.WATCHED, NotflixCardAction.HIDE),
            notflixCardActionOrder(canPlay = false, watchable = true, hasSound = false)
        )
        // Un título que no es película ni serie no ofrece Visto; el resto no cambia.
        assertEquals(
            listOf(NotflixCardAction.PLAY, NotflixCardAction.INFO, NotflixCardAction.LIBRARY, NotflixCardAction.HIDE),
            notflixCardActionOrder(canPlay = true, watchable = false, hasSound = false)
        )
    }

    @Test
    fun `no screen can cut the panel down to fewer actions`() {
        // M67: la paridad es estructural; el host ya no tiene banderas que recorten sus acciones.
        val fields = NotflixCardActionsHost::class.java.declaredFields.map { it.name }
        assertFalse("el host no debe volver a tener banderas de recorte: $fields", fields.any { it.startsWith("supports") })
    }

    @Test
    fun `the three screens hand their official writers to the same host`() {
        val calls = mutableListOf<String>()
        val status = NotflixCardStatus(inLibrary = true, watched = false)
        fun host(): NotflixCardActionsHost = notflixCardActionHost(
            status = { status },
            manageLists = true,
            onExpanded = { calls += "expanded:${it.id}" },
            onLibrary = { item, base -> calls += "library:${item.id}:$base" },
            onWatched = { calls += "watched:${it.id}" },
            onHide = { calls += "hide:${it.id}" }
        )
        val item = meta("tt1")
        // Inicio, carpeta de colección y «Ver todo» reciben el mismo contrato.
        for (screen in listOf("inicio", "coleccion", "ver-todo")) {
            val host = host()
            assertEquals(status, host.status(item))
            assertTrue(host.manageLists)
            host.onExpanded(item)
            host.onLibrary(item, "https://addon")
            host.onWatched(item)
            host.onHide(item)
            calls += "screen:$screen"
        }
        assertEquals(
            listOf(
                "expanded:tt1", "library:tt1:https://addon", "watched:tt1", "hide:tt1", "screen:inicio",
                "expanded:tt1", "library:tt1:https://addon", "watched:tt1", "hide:tt1", "screen:coleccion",
                "expanded:tt1", "library:tt1:https://addon", "watched:tt1", "hide:tt1", "screen:ver-todo"
            ),
            calls
        )
    }

    @Test
    fun `the long-press menu status maps straight onto the card panel`() {
        val mapped = com.nuvio.tv.ui.components.posteroptions.PosterCardStatus(
            inLibrary = false, watched = true, libraryPending = true, watchedPending = false
        ).toNotflixCardStatus()
        assertEquals(NotflixCardStatus(inLibrary = false, libraryPending = true, watched = true, watchedPending = false), mapped)
        // La clave del estado es la misma que usa el controlador del menú largo.
        assertEquals(
            com.nuvio.tv.ui.components.posteroptions.posterCardStatusKey("Movie", " tt1 "),
            com.nuvio.tv.ui.components.posteroptions.posterCardStatusKey("movie", "tt1")
        )
    }
}
