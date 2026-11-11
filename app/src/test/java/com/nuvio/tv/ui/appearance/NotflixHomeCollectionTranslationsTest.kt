package com.nuvio.tv.ui.appearance

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M55: the folder names are stored inside the created collection, so every app language needs them. */
class NotflixHomeCollectionTranslationsTest {
    private val keys = listOf(
        "notflix_home_collection_title",
        "notflix_home_folder_popular_movies",
        "notflix_home_folder_popular_series",
        "notflix_home_folder_animated_movies",
        "notflix_home_folder_new_movies",
        "notflix_home_folder_new_series",
        "notflix_home_folder_family_movies",
        "notflix_home_folder_action",
        "notflix_home_folder_comedy",
        "notflix_home_folder_sci_fi",
        "notflix_home_folder_drama_series",
        "notflix_home_folder_featured_movies",
        "notflix_collection_help",
        "notflix_collection_create",
        "notflix_collection_creating"
    )
    private val collectionKeys = keys.takeLast(3)

    private val res: File = listOf(File("src/main/res"), File("app/src/main/res"))
        .firstOrNull { it.isDirectory }
        ?: error("main resources not found from ${File(".").absolutePath}")

    @Test fun everyLanguageDefinesTheTitleFoldersAndTheNamedCollectionTexts() {
        val languages = res.listFiles().orEmpty()
            .filter { it.isDirectory && (it.name == "values" || it.name.startsWith("values-")) }
            .filter { File(it, "strings.xml").isFile }
        assertTrue("values/strings.xml is missing", languages.any { it.name == "values" })
        assertTrue("only ${languages.size} values folders with strings.xml", languages.size >= 36)
        languages.forEach { dir ->
            val xml = File(dir, "strings.xml").readText()
            keys.forEach { key ->
                val value = stringValue(xml, key)
                assertTrue("${dir.name}: missing or empty $key", !value.isNullOrBlank())
            }
            collectionKeys.forEach { key ->
                assertTrue("${dir.name}: $key must name the collection with %1\$s",
                    stringValue(xml, key).orEmpty().contains("%1\$s"))
            }
        }
    }

    @Test fun englishAndSpanishKeepTheApprovedWording() {
        val english = File(res, "values/strings.xml").readText()
        val spanish = File(res, "values-es/strings.xml").readText()
        assertEquals("Notflix Home", stringValue(english, "notflix_home_collection_title"))
        assertEquals("Popular movies", stringValue(english, "notflix_home_folder_popular_movies"))
        assertEquals("Sci-fi", stringValue(english, "notflix_home_folder_sci_fi"))
        assertEquals("Create Notflix Home",
            stringValue(english, "notflix_collection_create")!!.replace("%1\$s", "Notflix Home"))
        assertEquals("Inicio Notflix", stringValue(spanish, "notflix_home_collection_title"))
        assertEquals("Películas populares", stringValue(spanish, "notflix_home_folder_popular_movies"))
        assertEquals("Crear Inicio Notflix",
            stringValue(spanish, "notflix_collection_create")!!.replace("%1\$s", "Inicio Notflix"))
    }

    @Test fun otherLanguagesActuallyChangeTheStoredNames() {
        val english = stringValue(File(res, "values/strings.xml").readText(), "notflix_home_folder_popular_movies")
        val italian = stringValue(File(res, "values-it/strings.xml").readText(), "notflix_home_folder_popular_movies")
        val french = stringValue(File(res, "values-fr/strings.xml").readText(), "notflix_home_folder_popular_movies")
        assertEquals("Film popolari", italian)
        assertEquals("Films populaires", french)
        assertTrue("Italian must differ from English", italian != english)
        assertTrue("French must differ from English", french != english)
    }

    private fun stringValue(xml: String, key: String): String? {
        val start = xml.indexOf("<string name=\"$key\"")
        if (start < 0) return null
        val open = xml.indexOf('>', start)
        if (open < 0) return null
        val end = xml.indexOf("</string>", open)
        if (end < 0) return null
        return xml.substring(open + 1, end)
    }
}
