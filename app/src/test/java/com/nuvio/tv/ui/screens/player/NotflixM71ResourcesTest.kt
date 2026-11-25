package com.nuvio.tv.ui.screens.player

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class NotflixM71ResourcesTest {
    @Test fun allTranslationsHaveTheEnglishOrderAndExactFormatArguments() {
        val root = File("src/main/res").takeIf { it.isDirectory } ?: File("app/src/main/res")
        val folders = root.listFiles()!!.filter { File(it, "notflix_strings.xml").isFile }
        assertEquals(38, folders.size)
        fun rows(dir: File): List<Pair<String, String>> {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(dir, "notflix_m71_strings.xml"))
            val strings = doc.getElementsByTagName("string")
            return (0 until strings.length).map { i -> strings.item(i).let { it.attributes.getNamedItem("name").nodeValue to it.textContent } }
        }
        val english = rows(File(root, "values"))
        val placeholders = Regex("%[0-9]+\\$[sd]")
        assertEquals(7, english.size)
        folders.forEach { folder ->
            val translated = rows(folder)
            assertEquals(folder.name, english.map { it.first }, translated.map { it.first })
            translated.zip(english).forEach { (local, base) ->
                assertTrue(folder.name, local.second.isNotBlank())
                assertEquals(folder.name, placeholders.findAll(base.second).map { it.value }.toList(), placeholders.findAll(local.second).map { it.value }.toList())
            }
        }
    }
}
