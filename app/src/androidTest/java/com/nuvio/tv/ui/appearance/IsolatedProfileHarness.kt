package com.nuvio.tv.ui.appearance

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.ProfileDataStore
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import com.squareup.moshi.Moshi
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*

/** No delegated DataStore: the caller owns each file, instance and IO scope. */
internal class IsolatedProfileHarness(base: Context, failStorage: Boolean = false) : AutoCloseable {
    class FilesContext(base: Context, private val directory: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
    }
    val root = File(base.cacheDir, "nf003-m5-harness/${UUID.randomUUID()}").apply { mkdirs() }
    val profileFile = File(root, "profile.preferences_pb")
    val context = FilesContext(base, File(root, "profiles").apply { mkdirs() })
    private val job = SupervisorJob()
    val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { profileFile }
    val profiles = ProfileDataStore(context, Moshi.Builder().build(), dataStore)
    private val featurePath = File(root, "features").apply {
        if (failStorage) writeText("Intentional isolated storage failure") else mkdirs()
    }
    val factory = ProfileDataStoreFactory(FilesContext(base, featurePath))
    val manager = ProfileManager(profiles, factory, emptySet(), context)

    override fun close() = runBlocking {
        // These production classes have no lifecycle API. Only release this fixture's
        // own jobs; never reset/delete application data or widen production APIs for cleanup.
        val scope = ProfileManager::class.java.getDeclaredField("scope").apply { isAccessible = true }
            .get(manager) as CoroutineScope
        scope.coroutineContext[Job]?.cancelAndJoin()
        val cache = ProfileDataStoreFactory::class.java.getDeclaredField("cache").apply { isAccessible = true }
            .get(factory) as MutableMap<*, *>
        cache.values.toList().forEach { value ->
            val storeJob = value!!::class.java.getDeclaredField("job").apply { isAccessible = true }.get(value) as Job
            storeJob.cancelAndJoin()
        }
        cache.clear()
        job.cancelAndJoin()
    }
}
