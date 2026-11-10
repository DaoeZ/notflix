package com.nuvio.tv.core.di

import com.nuvio.tv.data.local.HiddenTitlesStore
import com.nuvio.tv.domain.repository.HiddenTitlesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HiddenTitlesModule {
    @Binds
    @Singleton
    abstract fun bindHiddenTitlesRepository(impl: HiddenTitlesStore): HiddenTitlesRepository
}
