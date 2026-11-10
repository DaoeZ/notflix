package com.nuvio.tv.core.di

import com.nuvio.tv.data.local.NotflixIntegrationConfig
import com.nuvio.tv.data.local.NotflixIntegrationStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Reads the user-provided provider keys from the device store on every call. */
@Module
@InstallIn(SingletonComponent::class)
object NotflixIntegrationModule {

    @Provides
    @Singleton
    fun provideNotflixIntegrationConfig(store: NotflixIntegrationStore): NotflixIntegrationConfig =
        NotflixIntegrationConfig(store)
}
