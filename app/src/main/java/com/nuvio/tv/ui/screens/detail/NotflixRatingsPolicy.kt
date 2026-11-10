package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.domain.model.MDBListRatings
import java.text.NumberFormat
import java.util.Locale

/** Native scales of the existing MDBList DTO. No acquisition or preferences live here. */
internal enum class NotflixRatingProvider(val id: String, val maximum: Int, val percent: Boolean = false) {
    IMDB("imdb",10), TRAKT("trakt",100), TMDB("tmdb",100),
    TOMATOES("tomatoes",100,true), AUDIENCE("audience",100,true),
    LETTERBOXD("letterboxd",5), METACRITIC("metacritic",100), MAL("mal",10)
}

internal data class NotflixRating(val provider: NotflixRatingProvider, val value: Double, val fromMDBList: Boolean)

internal fun notflixDetailRatings(
    ratings: MDBListRatings?, order: List<String>, standardImdb: Float? = null, standardTmdb: Float? = null
): List<NotflixRating> {
    val shown = linkedMapOf<NotflixRatingProvider,NotflixRating>()
    fun add(provider: NotflixRatingProvider, value: Double?, mdb: Boolean) {
        if (value != null && value.isFinite() && value in 0.0..provider.maximum.toDouble() && provider !in shown) {
            shown[provider] = NotflixRating(provider,value,mdb)
        }
    }
    order.distinct().forEach { id ->
        val provider = NotflixRatingProvider.entries.find { it.id == id } ?: return@forEach
        val value = when(provider) {
            NotflixRatingProvider.IMDB -> ratings?.imdb
            NotflixRatingProvider.TRAKT -> ratings?.trakt
            NotflixRatingProvider.TMDB -> ratings?.tmdb
            NotflixRatingProvider.TOMATOES -> ratings?.tomatoes
            NotflixRatingProvider.AUDIENCE -> ratings?.audience
            NotflixRatingProvider.LETTERBOXD -> ratings?.letterboxd
            NotflixRatingProvider.METACRITIC -> ratings?.metacritic
            NotflixRatingProvider.MAL -> ratings?.mal
        }
        add(provider,value,true)
    }
    // The caller supplies these only when the existing standard-visibility policy permits them.
    add(NotflixRatingProvider.IMDB,standardImdb?.toDouble(),false)
    add(NotflixRatingProvider.TMDB,standardTmdb?.toDouble()?.times(10.0),false)
    return shown.values.toList()
}

internal fun NotflixRating.formatted(locale: Locale): String {
    val number = NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = 1
        minimumFractionDigits = if(provider.maximum<=10)1 else 0
    }.format(value)
    return if(provider.percent) "$number%" else "$number/${provider.maximum}"
}
