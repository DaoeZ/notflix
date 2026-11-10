package com.nuvio.tv.data.wikipedia

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

// Only synthetic prose authored for tests (CC0); no third-party article text is stored here.
internal fun wikipediaPayload(html:String="<h2 id='Critical_response'>Crítica</h2><p>$WIKI_FIXTURE_TEXT</p>",
    language:String="es", sections:List<Map<String,Any>> = listOf(mapOf("line" to if(language=="es")"Crítica" else "Critical response","anchor" to "Critical_response","index" to "not-an-api-index"))):String =
    Gson().toJson(mapOf("parse" to mapOf("title" to "NF-M18 synthetic fixture","pageid" to 10,"revid" to 20,"text" to html,"tocdata" to mapOf("sections" to sections))))

class WikipediaHtmlParserTest {
    private val parser=WikipediaHtmlParser()
    private fun parse(payload:String,language:String="es")=parser.parse(payload,WikipediaIdentity("tt0000001","movie"),"Q123",language,
        "https://$language.wikipedia.org/wiki/NF-M18_synthetic_fixture",1_700_000_000_000)
    @Test fun commercialAndAwardParagraphsCannotBecomeCriticismEvenUnderACriticalHeading() {
        val examples=listOf(
            "La película sintética recaudó fondos en taquilla y superó ampliamente el presupuesto de su producción ficticia.",
            "La película sintética obtuvo nominaciones a premios durante un certamen inventado para esta prueba de filtrado.",
            "The synthetic movie grossed more than its production budget despite the critical reviews discussed elsewhere.",
            "The synthetic movie received several award nominations in this invented test without providing any critical opinion.")
        for(text in examples) assertNull(parse(wikipediaPayload("<h2 id='Critical_response'>Crítica</h2><p>$text</p>")))
        val ranking="Un crítico de esta obra sintética la clasificó entre sus películas preferidas de aquella década ficticia."
        assertEquals(listOf(ranking),parse(wikipediaPayload("<h2 id='Critical_response'>Crítica</h2><p>$ranking</p>"))!!.paragraphs)
    }
    @Test fun genericReceptionRequiresAnExplicitCriticalContextInsteadOfUnrelatedFacts() {
        for(lang in listOf("es","en")) {
            val heading=if(lang=="es")"Recepción" else "Reception"
            val sections=listOf(mapOf("line" to heading,"anchor" to "Reception"))
            val unrelated=if(lang=="es")"La obra sintética se proyectó en una sala de una ciudad inventada y su equipo se reunió allí durante varios días."
                else "The synthetic work appeared at an invented venue and its team remained in the same town for several days."
            assertNull(parse(wikipediaPayload("<h2 id='Reception'>$heading</h2><p>$unrelated</p>",lang,sections),lang))
            val critical=if(lang=="es")"Las reseñas de esta obra sintética elogiaron su presentación visual según esta descripción ficticia creada para la prueba."
                else "Critical reviews of this synthetic work praised its visual presentation in this fictional description written for the test."
            assertEquals(listOf(critical),parse(wikipediaPayload("<h2 id='Reception'>$heading</h2><p>$critical</p>",lang,sections),lang)!!.paragraphs)
        }
    }
    @Test fun exactSpanishHeadingProducesPlainAttributedRevision() {
        val result=requireNotNull(parse(wikipediaPayload()));assertEquals(listOf(WIKI_FIXTURE_TEXT),result.paragraphs)
        assertEquals("es",result.language);assertEquals("Crítica",result.section);assertEquals(20L,result.revisionId);assertEquals(10L,result.pageId)
        assertTrue(result.adapted);assertTrue(result.revisionUrl()!!.contains("oldid=20"))
    }
    @Test fun englishSectionPreservesItsLanguageWithoutTranslation() {
        val result=parse(wikipediaPayload("<h2 id='Critical_response'>Critical response</h2><p>$WIKI_FIXTURE_TEXT</p>","en"),"en")!!
        assertEquals("en",result.language);assertEquals(WIKI_FIXTURE_TEXT,result.paragraphs.single())
    }
    @Test fun criticalSubsectionWinsAndPlotBoxOfficeAwardsAreNotMerged() {
        val html="<p>Plot $WIKI_FIXTURE_TEXT</p><h2 id='Reception'>Reception</h2><h3 id='Box_office'>Box office</h3><p>Revenue $WIKI_FIXTURE_TEXT</p>"+
            "<h3 id='Critical_response'>Critical response</h3><p>$WIKI_FIXTURE_TEXT</p><h3 id='Awards'>Awards</h3><p>Award $WIKI_FIXTURE_TEXT</p>"
        val sections=listOf(mapOf("line" to "Reception","anchor" to "Reception"),mapOf("line" to "Critical response","anchor" to "Critical_response"))
        assertEquals(listOf(WIKI_FIXTURE_TEXT),parse(wikipediaPayload(html,"en",sections),"en")!!.paragraphs)
    }
    @Test fun quotationsAndConsensusReferencesDiscardWholeParagraphsRatherThanOnlyQuoteNodes() {
        val html="<h2 id='Critical_response'>Crítica</h2>"+
            "<p>Rotten Tomatoes $WIKI_FIXTURE_TEXT</p><p>Metacritic $WIKI_FIXTURE_TEXT</p>"+
            "<p>Context <q>$WIKI_FIXTURE_TEXT</q> trailing words to avoid silently rewriting the sentence.</p>"+
            "<p>Context <cite>$WIKI_FIXTURE_TEXT</cite></p><p>«$WIKI_FIXTURE_TEXT»</p><p>$WIKI_FIXTURE_TEXT</p>"
        assertEquals(listOf(WIKI_FIXTURE_TEXT),parse(wikipediaPayload(html))!!.paragraphs)
    }
    @Test fun tablesFiguresHiddenMarkupAndActiveHtmlCannotBecomeProse() {
        val html="<h2 id='Critical_response'>Crítica</h2><table><tr><td><p>Table $WIKI_FIXTURE_TEXT</p></td></tr></table>"+
            "<figure><p>Figure $WIKI_FIXTURE_TEXT</p></figure><div hidden><p>Hidden $WIKI_FIXTURE_TEXT</p></div>"+
            "<div style='display:none'><p>CSS hidden $WIKI_FIXTURE_TEXT</p></div><script>not executed</script>"+
            "<blockquote><p>Quote $WIKI_FIXTURE_TEXT</p></blockquote><p>$WIKI_FIXTURE_TEXT<sup class='reference'>[123]</sup></p>"
        assertEquals(listOf(WIKI_FIXTURE_TEXT),parse(wikipediaPayload(html))!!.paragraphs)
    }
    @Test fun atMostThreeParagraphsAndNineHundredCharactersIncludingSeparators() {
        val paragraph=(WIKI_FIXTURE_TEXT+" ").repeat(3).trim()
        val html="<h2 id='Critical_response'>Crítica</h2>"+"<p>$paragraph</p>".repeat(8)
        val result=parse(wikipediaPayload(html))!!
        assertTrue(result.paragraphs.size in 1..3);assertTrue(result.paragraphs.joinToString("\n\n").length<=900)
        assertTrue(result.paragraphs.all{it.last()=='.'});assertFalse(result.paragraphs.last().endsWith("…"))
    }
    @Test fun longSingleSentenceWithoutSafeBoundaryIsOmitted() {
        val html="<h2 id='Critical_response'>Crítica</h2><p>"+"synthetic ".repeat(150)+".</p>"
        assertNull(parse(wikipediaPayload(html)))
    }
    @Test fun missingSafeSectionNeverFallsBackToSynopsisOrObsoleteSections() {
        assertNull(parse(wikipediaPayload("<h2 id='Plot'>Argumento</h2><p>$WIKI_FIXTURE_TEXT</p>",sections=listOf(mapOf("line" to "Argumento","anchor" to "Plot")))))
        val legacy=Gson().toJson(mapOf("parse" to mapOf("title" to "NF-M18 synthetic fixture","pageid" to 10,"revid" to 20,"text" to WIKI_FIXTURE_TEXT,"sections" to emptyList<String>())))
        assertNull(parse(legacy));assertNull(parse("not JSON"))
    }
    @Test fun droppedContextDoesNotLeaveAnaphoricFragmentAsTheExtract() {
        val html="<h2 id='Critical_response'>Crítica</h2><p>Rotten Tomatoes $WIKI_FIXTURE_TEXT</p>"+
            "<p>Sin embargo, $WIKI_FIXTURE_TEXT</p><p>$WIKI_FIXTURE_TEXT</p>"
        assertEquals(listOf(WIKI_FIXTURE_TEXT),parse(wikipediaPayload(html))!!.paragraphs)
    }
    @Test fun hostileIdentityRevisionHeadingOrTemplateOriginIsRejected() {
        assertNull(parse(wikipediaPayload().replace("NF-M18 synthetic fixture","Other identity")))
        assertNull(parse(wikipediaPayload().replace("\"revid\":20","\"revid\":0")))
        assertNull(parse(wikipediaPayload("<h2 id='Critical_response'>Other heading</h2><p>$WIKI_FIXTURE_TEXT</p>")))
        val sections=listOf(mapOf("line" to "Crítica","anchor" to "Critical_response","fromTitle" to "Template:Other"))
        assertNull(parse(wikipediaPayload(sections=sections)))
    }
    @Test fun unsafeBidiTextAndAllQuotedContentLeaveNoEmptyPanelPayload() {
        assertNull(parse(wikipediaPayload("<h2 id='Critical_response'>Crítica</h2><p>\u202e$WIKI_FIXTURE_TEXT</p>")))
        assertNull(parse(wikipediaPayload("<h2 id='Critical_response'>Crítica</h2><p>\"$WIKI_FIXTURE_TEXT\"</p>")))
    }
    @Test fun longSingleQuotedMaterialIsDiscardedWithoutRejectingAnUnpairedApostrophe() {
        for(quoted in listOf("'$WIKI_FIXTURE_TEXT'","‘$WIKI_FIXTURE_TEXT’")) {
            assertNull(parse(wikipediaPayload("<h2 id='Critical_response'>Crítica</h2><p>$quoted</p>")))
        }
        val own="The synthetic filmmaker's presentation is discussed in this test paragraph without reproducing anyone's quotation or consensus."
        // Two distant apostrophes are conservatively rejected; an isolated possessive is permitted.
        val isolated=own.replace("anyone's","a third-party")
        assertEquals(listOf(isolated),parse(wikipediaPayload("<h2 id='Critical_response'>Crítica</h2><p>$isolated</p>"))!!.paragraphs)
    }
}
