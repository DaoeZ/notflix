package com.nuvio.tv.ui.screens.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class NotflixSettingsResourcesTest {
    @Test fun allLocalesHaveTheSameOrderedKeysAndFormattingMarkers() {
        val root = File("src/main/res").takeIf { it.isDirectory } ?: File("app/src/main/res")
        fun strings(file: File): List<Pair<String, String>> {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            return (0 until nodes.length).map { i -> nodes.item(i).attributes.getNamedItem("name").nodeValue to nodes.item(i).textContent }
        }
        val english = strings(File(root, "values/notflix_m72_strings.xml"))
        assertEquals(11, english.size)
        val locales = root.listFiles()!!.filter { it.isDirectory && (it.name == "values" || it.name.startsWith("values-")) && File(it, "strings.xml").exists() }
        assertEquals(38, locales.size)
        val marker = Regex("%[0-9]+[$][a-z]")
        locales.forEach { directory ->
            val translated = strings(File(directory, "notflix_m72_strings.xml"))
            assertEquals(directory.name, english.map { it.first }, translated.map { it.first })
            translated.zip(english).forEach { (local, original) ->
                assertTrue(directory.name + local.first, local.second.isNotBlank())
                assertEquals(marker.findAll(original.second).map { it.value }.toList(), marker.findAll(local.second).map { it.value }.toList())
            }
        }
    }
}
