package com.nuvio.tv.ui.appearance

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R

/** Resolves the preset texts in the current app language; no Context reaches the ViewModel. */
@Composable
internal fun notflixHomeCollectionTexts(): NotflixHomeCollectionTexts = NotflixHomeCollectionTexts(
    title = stringResource(R.string.notflix_home_collection_title),
    popularMovies = stringResource(R.string.notflix_home_folder_popular_movies),
    popularSeries = stringResource(R.string.notflix_home_folder_popular_series),
    animatedMovies = stringResource(R.string.notflix_home_folder_animated_movies),
    newMovies = stringResource(R.string.notflix_home_folder_new_movies),
    newSeries = stringResource(R.string.notflix_home_folder_new_series),
    familyMovies = stringResource(R.string.notflix_home_folder_family_movies),
    action = stringResource(R.string.notflix_home_folder_action),
    comedy = stringResource(R.string.notflix_home_folder_comedy),
    sciFi = stringResource(R.string.notflix_home_folder_sci_fi),
    dramaSeries = stringResource(R.string.notflix_home_folder_drama_series),
    featuredMovies = stringResource(R.string.notflix_home_folder_featured_movies)
)
