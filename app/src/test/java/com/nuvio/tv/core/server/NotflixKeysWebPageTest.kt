package com.nuvio.tv.core.server

import org.junit.Assert.assertTrue
import org.junit.Test

class NotflixKeysWebPageTest {
    @Test
    fun pageFollowsTheAppLanguageAndDirection() {
        val arabic = NotflixKeysWebPage.html(mapOf("lang" to "ar", "builtIn" to "Notflix"))
        assertTrue(arabic.contains("""<html lang="ar" dir="rtl">"""))
        assertTrue(arabic.contains("builtIn: 'Notflix'"))

        val spanish = NotflixKeysWebPage.html(mapOf("lang" to "es-ES"))
        assertTrue(spanish.contains("""<html lang="es-ES" dir="ltr">"""))
    }

    @Test
    fun builtInKeyIsShownInsteadOfNotSet() {
        val page = NotflixKeysWebPage.html(emptyMap())
        assertTrue(page.contains("entry.builtIn) return labels.current + ' ' + labels.builtIn"))
    }
}
