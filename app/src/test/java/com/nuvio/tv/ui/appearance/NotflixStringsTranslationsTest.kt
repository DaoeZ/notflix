package com.nuvio.tv.ui.appearance

import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.xml.sax.InputSource

// Include unnumbered arguments, numeric conversions, dates and escaped percent signs as well.
private val FORMAT_ARGUMENT = Regex("%(?:\\d+\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?[tT]?[a-zA-Z%]")

private fun placeholders(text: String): List<String> =
    FORMAT_ARGUMENT.findAll(text).map { it.value }.sorted().toList()

private data class NotflixText(
    val key: String,
    val type: String,
    val file: String,
    val items: Map<String, String>,
    val translatable: Boolean
)

/** M62: resource contracts, including Notflix settings whose keys have no notflix_ prefix. */
class NotflixStringsTranslationsTest {
    private val res: File = listOf(File("src/main/res"), File("app/src/main/res"))
        .firstOrNull { it.isDirectory }
        ?: error("main resources not found from ${File(".").absolutePath}")

    private val settingsKeys = setOf(
        "autoplay_priority_audio_languages_title",
        "autoplay_priority_audio_languages_desc",
        "autoplay_priority_audio_languages_any",
        "autoplay_priority_priority_title",
        "autoplay_priority_priority_desc",
        "autoplay_priority_priority_quality",
        "autoplay_priority_priority_language",
        "autoplay_priority_audio_order_title",
        "layout_cw_card_style_title"
    )

    @Test fun everyLanguageCarriesEveryNotflixResourceContract() {
        val english = resources(File(res, "values")).filter { isNotflixKey(it.key) && it.translatable }
        assertTrue("English Notflix resources not found", english.size > 240)
        languages().forEach { dir ->
            val local = resources(dir).associateBy { it.key }
            english.forEach { entry ->
                val translated = local[entry.key]
                assertTrue("${dir.name}: missing ${entry.key}", translated != null)
                assertEquals("${dir.name}: ${entry.key}", emptyList<String>(), errors(entry, translated!!))
            }
        }
    }

    @Test fun everyResourceFileKeepsEnglishNotflixOrder() {
        val english = resources(File(res, "values")).filter { isNotflixKey(it.key) && it.translatable }
        // Spanish is a pre-existing source and M62 must not rewrite it.
        languages().filter { it.name != "values-es" }.forEach { dir ->
            val local = resources(dir).filter { isNotflixKey(it.key) && it.translatable }
            english.groupBy { it.file }.forEach { (file, entries) ->
                assertEquals("${dir.name}/$file: resource order", entries.map { it.key },
                    local.filter { it.file == file }.map { it.key })
            }
        }
    }

    @Test fun everyPluralIncludesTheLanguageSpecificIntegerForms() {
        val quantities = mapOf(
            "values-ar" to setOf("zero", "one", "two", "few", "many", "other"),
            "values-iw" to setOf("one", "two", "other"),
            "values-lt" to setOf("one", "few", "many", "other"),
            "values-sl" to setOf("one", "two", "few", "other"),
            "values-ro" to setOf("one", "few", "other")
        )
        quantities.forEach { (language, required) ->
            resources(File(res, language)).filter { isNotflixKey(it.key) && it.type == "plurals" }
                .forEach { entry ->
                    assertTrue("$language: ${entry.key} missing ${required - entry.items.keys}",
                        entry.items.keys.containsAll(required))
                }
        }
    }

    @Test fun serviceNamesArePreserved() {
        val brands = listOf("Notflix", "Nuvio", "TMDB", "IMDb", "Trakt", "IntroDB", "MDBList",
            "Rotten Tomatoes", "Wikipedia", "Wikidata", "Xperience", "Simkl", "HBO Max", "Paramount+", "Disney+")
        val english = resources(File(res, "values")).filter { isNotflixKey(it.key) && it.translatable }
        languages().forEach { dir ->
            val local = resources(dir).associateBy { it.key }
            english.forEach { entry ->
                entry.items.forEach { (item, source) ->
                    brands.filter { source.contains(it, ignoreCase = true) }.forEach { brand ->
                        assertTrue("${dir.name}: ${entry.key}/$item must retain $brand",
                            local[entry.key]?.items?.get(item).orEmpty().contains(brand, ignoreCase = true))
                    }
                }
            }
        }
    }

    @Test fun validatorRejectsChangedUnnumberedNumericAndDateArguments() {
        listOf("%s", "%d", "%1\$f", "%1\$tY").forEach { marker ->
            val source = parse("<resources><string name=\"notflix_test\">$marker</string></resources>").single()
            assertTrue(errors(source, source.copy(items = mapOf("value" to "missing"))).isNotEmpty())
        }
        assertEquals(listOf("%%", "%1\$s", "%2\$d"), placeholders("%2\$d / %1\$s %%"))
    }

    @Test fun validatorRejectsChangedTypeEmptyTextAndBrokenExtraPlural() {
        val source = parse("""<resources><plurals name="notflix_test">
            <item quantity="one">%1${'$'}d title</item><item quantity="other">%1${'$'}d titles</item>
            </plurals></resources>""").single()
        assertTrue(errors(source, source.copy(type = "string")).isNotEmpty())
        assertTrue(errors(source, source.copy(items = source.items + ("two" to "%1\$s titles"))).isNotEmpty())
        assertTrue(errors(source, source.copy(items = source.items + ("two" to ""))).isNotEmpty())
        assertTrue(errors(source, source.copy(items = source.items - "other")).isNotEmpty())
    }

    @Test fun validatorRejectsShortenedAndReorderedArrays() {
        val source = parse("""<resources><string-array name="notflix_test">
            <item>%1${'$'}s</item><item>%1${'$'}d</item></string-array></resources>""").single()
        assertTrue(errors(source, source.copy(items = mapOf("0" to "%1\$s"))).isNotEmpty())
        assertTrue(errors(source, source.copy(items = mapOf("0" to "%1\$d", "1" to "%1\$s"))).isNotEmpty())
    }

    @Test fun parserReadsXmlRatherThanCommentsAndAttributeOrder() {
        val entries = parse("""<resources><!-- <string name="ignored">fake</string> -->
            <string formatted="true" name="notflix_test">A &amp; B %s</string>
            <string translatable="false" name="notflix_brand">Notflix</string></resources>""")
        assertEquals(listOf("notflix_test", "notflix_brand"), entries.map { it.key })
        assertEquals("A & B %s", entries.first().items["value"])
        assertTrue(!entries.last().translatable)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parserRejectsDuplicateKeys() {
        parse("""<resources><string name="notflix_test">one</string>
            <string name="notflix_test">two</string></resources>""")
    }

    private fun errors(source: NotflixText, local: NotflixText): List<String> = buildList {
        if (source.type != local.type) add("resource type differs")
        if (source.file != local.file) add("resource file differs")
        if (!local.items.keys.containsAll(source.items.keys)) add("missing items")
        if (source.type != "plurals" && source.items.keys != local.items.keys) add("item count differs")
        local.items.forEach { (item, text) ->
            if (text.isBlank()) add("empty $item")
            val reference = source.items[item] ?: source.items["other"]
            if (reference != null && placeholders(reference) != placeholders(text)) add("placeholders differ in $item")
        }
    }

    private fun isNotflixKey(key: String) =
        key.startsWith("notflix_") || key.startsWith("cd_notflix_") || key in settingsKeys

    private fun languages(): List<File> = res.listFiles().orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values-") && File(it, "strings.xml").isFile }
        .sortedBy { it.name }
        .also { assertTrue("only ${it.size} language folders", it.size >= 36) }

    private fun resources(dir: File): List<NotflixText> {
        val entries = dir.listFiles().orEmpty().filter { it.extension == "xml" }.sortedBy { it.name }
            .flatMap { parse(it.readText(), it.name) }
        require(entries.map { it.key }.distinct().size == entries.size) { "Duplicate text resources in $dir" }
        return entries
    }

    private fun parse(xml: String, file: String = "fixture.xml"): List<NotflixText> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val root = factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
        val entries = (0 until root.childNodes.length).mapNotNull { index ->
            val element = root.childNodes.item(index) as? Element ?: return@mapNotNull null
            if (element.tagName !in setOf("string", "plurals", "string-array")) return@mapNotNull null
            val items = if (element.tagName == "string") listOf("value" to element.textContent) else {
                (0 until element.childNodes.length).mapNotNull { childIndex ->
                    val child = element.childNodes.item(childIndex) as? Element ?: return@mapNotNull null
                    if (child.tagName != "item") return@mapNotNull null
                    (if (element.tagName == "plurals") child.getAttribute("quantity") else childIndex.toString()) to child.textContent
                }.mapIndexed { itemIndex, item ->
                    (if (element.tagName == "string-array") itemIndex.toString() else item.first) to item.second
                }
            }
            require(items.map { it.first }.distinct().size == items.size) { "Duplicate items in ${element.getAttribute("name")}" }
            NotflixText(element.getAttribute("name"), element.tagName, file, items.toMap(), element.getAttribute("translatable") != "false")
        }
        require(entries.map { it.key }.distinct().size == entries.size) { "Duplicate text resources in $file" }
        return entries
    }
}
