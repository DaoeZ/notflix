package com.nuvio.tv.core.di

import com.nuvio.tv.core.profile.AgeRatingCache
import com.nuvio.tv.core.profile.TmdbAgeRatingSource
import com.nuvio.tv.core.profile.TmdbAvailability
import com.nuvio.tv.core.tmdb.IntegrationTmdbAvailability
import com.nuvio.tv.core.tmdb.TmdbAgeRatingSourceImpl
import com.nuvio.tv.data.local.AgeRatingCacheStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AgeRatingModule {
    @Binds
    @Singleton
    abstract fun bindAgeRatingCache(impl: AgeRatingCacheStore): AgeRatingCache

    @Binds
    @Singleton
    abstract fun bindTmdbAgeRatingSource(impl: TmdbAgeRatingSourceImpl): TmdbAgeRatingSource

    @Binds
    @Singleton
    abstract fun bindTmdbAvailability(impl: IntegrationTmdbAvailability): TmdbAvailability
}
