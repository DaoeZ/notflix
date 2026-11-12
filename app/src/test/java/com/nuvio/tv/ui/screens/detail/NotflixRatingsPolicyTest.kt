package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.domain.model.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class NotflixRatingsPolicyTest {
    private val order=MDBListSettings.DEFAULT_RATING_ORDER
    private val five=MDBListRatings(imdb=8.1,trakt=82.0,tomatoes=91.0,letterboxd=4.2,metacritic=77.0)
    private fun ids(v:List<NotflixRating>)=v.map{it.provider.id}
    @Test fun fiveSourcesKeepNativeScalesAndOfficialOrder() {
        val r=notflixDetailRatings(five,order)
        assertEquals(listOf("imdb","trakt","tomatoes","letterboxd","metacritic"),ids(r))
        assertEquals(listOf("8.1/10","82/100","91%","4.2/5","77/100"),r.map{it.formatted(Locale.US)})
        assertTrue(r.all{it.fromMDBList})
    }
    @Test fun allAdditionalEnabledSourcesRemainVisible() {
        val r=notflixDetailRatings(five.copy(tmdb=79.0,audience=87.0,mal=7.5),order)
        assertEquals(order,ids(r));assertEquals(8,r.size)
        assertEquals("79/100",r.first{it.provider.id=="tmdb"}.formatted(Locale.US))
    }
    @Test fun userOrderAndFilteringWinOverRequestedFive() {
        val settings=MDBListSettings(showImdb=false,showTrakt=false,ratingOrder=listOf("metacritic","imdb","letterboxd","tomatoes","trakt"))
        assertEquals(listOf("metacritic","letterboxd","tomatoes"),ids(notflixDetailRatings(five,settings.enabledRatingOrder())))
    }
    @Test fun duplicateAndUnknownIdsNeverDuplicateProviders() {
        assertEquals(listOf("tomatoes","imdb"),ids(notflixDetailRatings(five,listOf("unknown","tomatoes","imdb","tomatoes","IMDB"))))
    }
    @Test fun absentRatingsDoNotBecomeZerosOrInventSources() {
        assertTrue(notflixDetailRatings(null,order).isEmpty())
        assertTrue(notflixDetailRatings(MDBListRatings(),order).isEmpty())
        assertEquals(listOf("metacritic"),ids(notflixDetailRatings(MDBListRatings(metacritic=22.0),order)))
    }
    @Test fun genuineZerosAndUpperBoundariesArePreserved() {
        val zero=MDBListRatings(imdb=0.0,trakt=0.0,tmdb=0.0,tomatoes=0.0,audience=0.0,letterboxd=0.0,metacritic=0.0,mal=0.0)
        assertEquals(8,notflixDetailRatings(zero,order).size)
        val max=MDBListRatings(imdb=10.0,trakt=100.0,tmdb=100.0,tomatoes=100.0,audience=100.0,letterboxd=5.0,metacritic=100.0,mal=10.0)
        assertEquals(8,notflixDetailRatings(max,order).size)
    }
    @Test fun nonfiniteAndOutOfRangeValuesAreNeverClampedOrShown() {
        val invalid=MDBListRatings(imdb=Double.NaN,trakt=Double.POSITIVE_INFINITY,tmdb=-1.0,tomatoes=100.1,audience=Double.NEGATIVE_INFINITY,letterboxd=5.1,metacritic=-.1,mal=10.1)
        assertTrue(notflixDetailRatings(invalid,order).isEmpty())
    }
    @Test fun fallbackUsesExistingVisibilityGateAndNativeTmdbScale() {
        fun resolve(visibility:HomeImdbRatingsVisibility,active:Boolean)=notflixDetailRatings(null,order,
            7.6f.takeIf{visibility.showStandardDetailRatings(active)},8.2f.takeIf{visibility.showStandardDetailRatings(active)})
        assertEquals(listOf("imdb","tmdb"),ids(resolve(HomeImdbRatingsVisibility.SHOW_ALL,false)))
        assertEquals(listOf("7.6/10","82/100"),resolve(HomeImdbRatingsVisibility.SHOW_ALL,false).map{it.formatted(Locale.US)})
        assertTrue(resolve(HomeImdbRatingsVisibility.HIDE_ALL,false).isEmpty())
        assertTrue(resolve(HomeImdbRatingsVisibility.SHOW_ALL,true).isEmpty())
    }
    @Test fun suppliedProviderWinsOverStandardFallbackWithoutDuplicateImdb() {
        val r=notflixDetailRatings(five,order,7.6f,8.2f)
        assertEquals(1,r.count{it.provider.id=="imdb"});assertEquals(8.1,r.first().value,0.0)
        assertTrue(r.first().fromMDBList);assertFalse(r.last().fromMDBList)
    }
    @Test fun invalidFallbackCannotLeakNaNOrOutOfBounds() {
        assertTrue(notflixDetailRatings(null,order,Float.NaN,10.1f).isEmpty())
        assertTrue(notflixDetailRatings(null,order,-1f,Float.POSITIVE_INFINITY).isEmpty())
    }
    @Test fun hiddenOrderDoesNotReintroduceDisabledMdbSources() {
        assertTrue(notflixDetailRatings(five,emptyList()).isEmpty())
        assertEquals(listOf("trakt"),ids(notflixDetailRatings(five,listOf("trakt"))))
    }
    @Test fun formattingDistinguishesFractionsPercentAndSpanishDecimal() {
        val r=notflixDetailRatings(five.copy(trakt=82.5),order)
        assertEquals("8,1/10",r[0].formatted(Locale("es")))
        assertEquals("82,5/100",r[1].formatted(Locale("es")))
        assertEquals("91%",r[2].formatted(Locale("es")))
    }
    @Test fun noMutationOfOriginalSettingsOrRatings() {
        val settings=MDBListSettings(ratingOrder=listOf("metacritic","imdb","metacritic"))
        val before=settings.copy();val data=five.copy()
        repeat(3){notflixDetailRatings(five,settings.enabledRatingOrder())}
        assertEquals(before,settings);assertEquals(data,five)
    }
    @Test fun noAveragingOrNormalizedScoreSubstitution() {
        val r=notflixDetailRatings(MDBListRatings(imdb=1.0,letterboxd=5.0,metacritic=99.0),order)
        assertEquals(listOf(1.0,5.0,99.0),r.map{it.value});assertEquals(3,r.size)
    }
    // M57: the standard value is routed by the origin it really has.
    @Test fun tmdbOriginGoesToTheTmdbSlotWithItsNativeScale() {
        val standards=notflixStandardRatings(8.5f,RatingSource.TMDB,null)
        val r=notflixDetailRatings(null,order,standards.imdb,standards.tmdb)
        assertNull(standards.imdb)
        assertEquals(listOf("tmdb"),ids(r))
        assertEquals("85/100",r.single().formatted(Locale.US))
    }
    @Test fun imdbOriginKeepsImdbAndTheSeparateTmdbRating() {
        val standards=notflixStandardRatings(7.7f,RatingSource.IMDB,8.4f)
        val r=notflixDetailRatings(null,order,standards.imdb,standards.tmdb)
        assertEquals(listOf("imdb","tmdb"),ids(r))
        assertEquals("7.7/10",r[0].formatted(Locale.US))
        assertEquals("84/100",r[1].formatted(Locale.US))
    }
    @Test fun hidingTheStandardValueStillShowsAFetchedTmdbRating() {
        val standards=notflixStandardRatings(null,RatingSource.TMDB,8.4f)
        assertNull(standards.imdb)
        assertEquals(8.4f,standards.tmdb)
    }
}
