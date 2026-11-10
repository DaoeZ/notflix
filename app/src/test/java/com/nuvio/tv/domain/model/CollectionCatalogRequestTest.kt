package com.nuvio.tv.domain.model

import org.junit.Test
import org.junit.Assert.*

class CollectionCatalogRequestTest {
    private fun addon(id: String, catalogs: List<CatalogDescriptor> = emptyList()) =
        Addon(id, id, version="1", description=null, logo=null, baseUrl="https://$id.invalid", catalogs=catalogs, types=listOf(ContentType.MOVIE), resources=emptyList())
    private val top=CatalogDescriptor(ContentType.MOVIE,id="top",name="Top",extraSupported=listOf("skip"),pageSize=30)
    @Test fun exactSourceRetainsYearAndOfficialPagination() {
        val request=AddonCatalogCollectionSource("a","movie","top","2026").collectionCatalogRequest(listOf(addon("a",listOf(top))))!!
        assertEquals(mapOf("genre" to "2026"),request.extraArgs)
        assertEquals("top",request.catalogId);assertEquals("movie",request.type)
        assertTrue(request.supportsSkip);assertEquals(30,request.skipStep)
    }
    @Test fun commaDescriptorFallbackPreservesRequestedCatalogId() {
        val request=AddonCatalogCollectionSource("a","movie","top,variant").collectionCatalogRequest(listOf(addon("a",listOf(top))))!!
        assertEquals(top,request.catalog);assertEquals("top,variant",request.catalogId)
    }
    @Test fun fallbackSearchesOtherInstalledAddonLikeOfficialFolder() {
        val request=AddonCatalogCollectionSource("a","movie","top").collectionCatalogRequest(listOf(addon("a"),addon("b",listOf(top))))!!
        assertEquals("b",request.addon.id)
    }
    @Test fun missingDeclaredAddonDoesNotAdoptAnother() {
        assertNull(AddonCatalogCollectionSource("missing","movie","top").collectionCatalogRequest(listOf(addon("b",listOf(top)))))
    }
    @Test fun absentDescriptorStillUsesExplicitSourceAndDisablesSkip() {
        val request=AddonCatalogCollectionSource("a","series","custom").collectionCatalogRequest(listOf(addon("a")))!!
        assertEquals("series",request.type);assertFalse(request.supportsSkip);assertEquals(100,request.skipStep)
    }
    @Test fun noneAndBlankGenresStayAbsentButRealGenreIsNotTranslated() {
        for(genre in listOf(null,""," ","None","nOnE"))assertTrue(AddonCatalogCollectionSource("a","movie","top",genre).collectionExtraArgs().isEmpty())
        assertEquals(mapOf("genre" to "Sci-Fi"),AddonCatalogCollectionSource("a","movie","top","Sci-Fi").collectionExtraArgs())
    }
}
