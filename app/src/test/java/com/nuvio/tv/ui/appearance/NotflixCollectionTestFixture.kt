package com.nuvio.tv.ui.appearance

/** Neutral texts for tests that only need a valid preset, never the app resources. */
internal val notflixCollectionFixtureTexts = NotflixHomeCollectionTexts(
    title = "Fixture Home",
    popularMovies = "Fixture popular movies",
    popularSeries = "Fixture popular series",
    animatedMovies = "Fixture animated movies",
    newMovies = "Fixture new movies",
    newSeries = "Fixture new series",
    familyMovies = "Fixture family movies",
    action = "Fixture action",
    comedy = "Fixture comedy",
    sciFi = "Fixture sci-fi",
    dramaSeries = "Fixture drama series",
    featuredMovies = "Fixture featured movies"
)

internal fun notflixCollectionFixturePreset(year: Int = 2026) =
    NotflixHomeCollectionPreset.create(notflixCollectionFixtureTexts, year)
