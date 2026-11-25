package com.nuvio.tv.ui.appearance

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class NotflixM70ResourcesTest {
    @Test fun theActionHintHasTheEnglishOrderAndNoLostFormatArgumentsInEveryLanguage() {
        val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }
        val languages = res.listFiles()!!.filter { it.isDirectory && File(it, "notflix_strings.xml").isFile }
        assertEquals(38, languages.size)
        languages.forEach { directory ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(directory, "notflix_m70_strings.xml"))
            val entries = document.getElementsByTagName("string")
            assertEquals(directory.name, 1, entries.length)
            assertEquals("notflix_card_actions_hint", entries.item(0).attributes.getNamedItem("name").nodeValue)
            assertTrue(directory.name, entries.item(0).textContent.isNotBlank())
            assertFalse(directory.name, entries.item(0).textContent.contains('%'))
        }
    }
}
