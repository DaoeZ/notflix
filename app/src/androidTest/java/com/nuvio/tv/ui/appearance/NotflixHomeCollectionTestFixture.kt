package com.nuvio.tv.ui.appearance

/** English preset fixture for instrumented tests; the production screen resolves the same texts. */
internal val notflixCollectionTestTexts = NotflixHomeCollectionTexts(
    title = "Notflix Home",
    popularMovies = "Popular movies",
    popularSeries = "Popular series",
    animatedMovies = "Animated movies",
    newMovies = "New movies",
    newSeries = "New series",
    familyMovies = "Family movies",
    action = "Action",
    comedy = "Comedy",
    sciFi = "Sci-fi",
    dramaSeries = "Drama series",
    featuredMovies = "Featured movies"
)

internal fun notflixCollectionTestPreset(year: Int = java.time.Year.now().value) =
    NotflixHomeCollectionPreset.create(notflixCollectionTestTexts, year)
