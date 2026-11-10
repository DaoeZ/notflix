package com.nuvio.tv.data.wikipedia

import android.content.Context
import android.util.Log
import com.nuvio.tv.data.local.NotflixAppearanceStore
import com.nuvio.tv.ui.appearance.AppearancePolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WikipediaModule {
    @Provides @Singleton
    fun repository(@ApplicationContext context: Context, store: NotflixAppearanceStore): WikipediaExtractRepository = WikipediaFullRepository(
        WikipediaHttpTransport(), WikipediaExtractCache(File(context.cacheDir, "notflix_wikipedia_v1")),
        WikipediaLabBudget(File(context.filesDir, "notflix_wikipedia_evaluation_v1/budget.json")),
        WikipediaCacheWriter { binding, authorized, write -> store.withWikipediaWriteContext(binding.context, authorized, write) },
        available = AppearancePolicy.NOTFLIX_AVAILABLE,
        metric = { stage, count -> Log.d("NotflixWikipedia", "stage=$stage reserved_requests=$count") })
}
