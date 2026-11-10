package com.nuvio.tv.data.wikipedia

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** No jsoup or public extract provider in the Playstore source set. */
@Module
@InstallIn(SingletonComponent::class)
object WikipediaModule {
    @Provides @Singleton fun repository(): WikipediaExtractRepository = object : WikipediaExtractRepository {
        override val available = false
        override suspend fun find(binding: WikipediaBinding, authorized: () -> Boolean): WikipediaExtract? = null
    }
}
