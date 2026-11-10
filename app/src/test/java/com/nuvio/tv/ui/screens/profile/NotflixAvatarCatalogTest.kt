package com.nuvio.tv.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M53: pure parser of the private Xperience avatar list («Nombre - URL»). */
class NotflixAvatarCatalogTest {

    @Test
    fun readsWellFormedLines() {
        val result = parseNotflixAvatars(
            """
            Aang - https://cdn.xperience-app.com/avatars/disney-plus/aang.webp
            AC Milan - https://cdn.xperience-app.com/avatars/football/ac-milan.webp
            """.trimIndent()
        )

        assertEquals(2, result.size)
        assertEquals("AC Milan", result[1].name)
        assertEquals("https://cdn.xperience-app.com/avatars/football/ac-milan.webp", result[1].url)
        assertEquals(NotflixAvatarCategory.FOOTBALL, result[1].category)
    }

    @Test
    fun ignoresMalformedLines() {
        val result = parseNotflixAvatars(
            """
            no separator at all
            - https://cdn.xperience-app.com/avatars/disney-plus/no-name.webp
            No Url - not-a-url
            Wrong Scheme - ftp://cdn.xperience-app.com/avatars/disney-plus/x.webp
            Blank - 
            Valid - https://cdn.xperience-app.com/avatars/nuvio/valid.webp
            """.trimIndent()
        )

        assertEquals(1, result.size)
        assertEquals("Valid", result.single().name)
    }

    @Test
    fun keepsQuerySuffixAndResolvesTheCategoryBeforeIt() {
        val result = parseNotflixAvatars(
            "Monica - https://cdn.xperience-app.com/avatars/hbo-max/monica.webp?v=2"
        )

        assertEquals("https://cdn.xperience-app.com/avatars/hbo-max/monica.webp?v=2", result.single().url)
        assertEquals(NotflixAvatarCategory.HBO_MAX, result.single().category)
    }

    @Test
    fun keepsDuplicateNamesWithDifferentUrls() {
        val result = parseNotflixAvatars(
            """
            Abby - https://cdn.xperience-app.com/avatars/hbo-max/abby.webp
            Abby - https://cdn.xperience-app.com/avatars/hbo-max/abby-sesame-street.webp
            """.trimIndent()
        )

        assertEquals(2, result.size)
        assertEquals(listOf("Abby", "Abby"), result.map { it.name })
        assertEquals(2, result.map { it.url }.toSet().size)
    }

    @Test
    fun emptyInputYieldsAnEmptyList() {
        assertTrue(parseNotflixAvatars("").isEmpty())
        assertTrue(parseNotflixAvatars("\n\n   \n").isEmpty())
    }

    @Test
    fun sortsByVisibleCategoryOrderAndThenByName() {
        val result = parseNotflixAvatars(
            """
            Zorro - https://cdn.xperience-app.com/avatars/custom/zorro.webp
            Zuma - https://cdn.xperience-app.com/avatars/disney-plus/zuma.webp
            Aang - https://cdn.xperience-app.com/avatars/disney-plus/aang.webp
            AC Milan - https://cdn.xperience-app.com/avatars/football/ac-milan.webp
            Monica - https://cdn.xperience-app.com/avatars/hbo-max/monica.webp
            """.trimIndent()
        )

        assertEquals(
            listOf("Monica", "Aang", "Zuma", "AC Milan", "Zorro"),
            result.map { it.name }
        )
        assertEquals(
            listOf(
                NotflixAvatarCategory.HBO_MAX,
                NotflixAvatarCategory.DISNEY_PLUS,
                NotflixAvatarCategory.DISNEY_PLUS,
                NotflixAvatarCategory.FOOTBALL,
                NotflixAvatarCategory.OTHER
            ),
            result.map { it.category }
        )
    }

    @Test
    fun everyDocumentedCategoryIsMapped() {
        val result = parseNotflixAvatars(
            listOf(
                "HBO - https://cdn.xperience-app.com/avatars/hbo-max/b.webp",
                "Paramount - https://cdn.xperience-app.com/avatars/paramount-plus/c.webp",
                "Disney - https://cdn.xperience-app.com/avatars/disney-plus/d.webp",
                "Nuvio - https://cdn.xperience-app.com/avatars/nuvio/e.webp",
                "Football - https://cdn.xperience-app.com/avatars/football/f.webp",
                "Custom - https://cdn.xperience-app.com/avatars/custom/g.webp",
                "Unknown - https://cdn.xperience-app.com/avatars/whatever/h.webp"
            ).joinToString("\n")
        )

        assertEquals(
            listOf(
                NotflixAvatarCategory.HBO_MAX,
                NotflixAvatarCategory.PARAMOUNT_PLUS,
                NotflixAvatarCategory.DISNEY_PLUS,
                NotflixAvatarCategory.NUVIO,
                NotflixAvatarCategory.FOOTBALL,
                NotflixAvatarCategory.OTHER,
                NotflixAvatarCategory.OTHER
            ),
            result.map { it.category }
        )
    }
}
