package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModelStore
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.ui.appearance.AppearanceViewModel
import io.mockk.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

/** Actual feature DataStore and existing M1 authorization; all files are disposable own fixtures. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotflixWikipediaPreferenceTest {
    private val root=Files.createTempDirectory("nf18-preference-").toFile()
    private val context=mockk<Context>(relaxed=true)
    private lateinit var factory:ProfileDataStoreFactory
    private lateinit var store:NotflixAppearanceStore
    private val owners=ViewModelStore()
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every{context.filesDir} returns root;every{context.applicationContext} returns context
        factory=ProfileDataStoreFactory(context);store=NotflixAppearanceStore(factory)
    }
    private fun capability(id:Int=2)=requireNotNull(store.contextFor(id,true,setOf(1,2)))
    @After fun cleanup()=runBlocking<Unit> {
        owners.clear()
        val cache=ProfileDataStoreFactory::class.java.getDeclaredField("cache").apply{isAccessible=true}.get(factory) as MutableMap<*,*>
        cache.values.toList().forEach{v->(v!!::class.java.getDeclaredField("job").apply{isAccessible=true}.get(v) as Job).cancelAndJoin()};cache.clear();Dispatchers.resetMain()
        check(root.canonicalFile.parentFile==File(System.getProperty("java.io.tmpdir")).canonicalFile && root.name.startsWith("nf18-preference-"));root.deleteRecursively()
    }
    @Test fun defaultOnIsSeparatePerProfileAndDoesNotChangeIntroPreviewOrOfficialTheme()=runBlocking<Unit> {
        val a=capability(1);val b=capability(2)
        assertTrue(store.observe(b).first().wikipediaEnabled)
        store.select(a,AppAppearance.NUVIO).getOrThrow();val primary=File(root,"datastore/${NotflixAppearanceStore.FEATURE}.preferences_pb");val bytes=primary.readBytes()
        val official=factory.get(2,"theme_settings");official.edit{it[stringPreferencesKey("fixture") ]="White"}
        val officialFile=File(root,"datastore/theme_settings_p2.preferences_pb");val before=officialFile.readBytes()
        store.select(b,AppAppearance.NOTFLIX).getOrThrow();store.selectIntroSoundEnabled(b,false).getOrThrow();store.selectPreviewSoundEnabled(b,true).getOrThrow()
        store.selectWikipediaEnabled(b,false).getOrThrow();val read=store.observe(b).first()
        assertFalse(read.wikipediaEnabled);assertTrue(read.wikipediaOptionsValid);assertFalse(read.introSoundEnabled);assertTrue(read.previewSoundEnabled)
        assertArrayEquals(bytes,primary.readBytes());assertArrayEquals(before,officialFile.readBytes());assertTrue(store.observe(a).first().wikipediaEnabled)
    }
    @Test fun wrongBooleanOrUnknownSchemaNeverEnablesRequests()=runBlocking<Unit> {
        val c=capability();val disk=factory.get(2,NotflixAppearanceStore.FEATURE)
        disk.edit{it[stringPreferencesKey(NotflixAppearanceStore.WIKIPEDIA_ENABLED)]="true"}
        assertFalse(store.observe(c).first().wikipediaEnabled);assertFalse(store.observe(c).first().wikipediaOptionsValid)
        disk.edit{it[booleanPreferencesKey(NotflixAppearanceStore.WIKIPEDIA_ENABLED)]=true;it[intPreferencesKey("schema_version")]=99}
        assertFalse(store.observe(c).first().wikipediaEnabled)
        store.selectWikipediaEnabled(c,true).getOrThrow();assertEquals(99,disk.data.first()[intPreferencesKey("schema_version")])
        assertFalse(store.observe(c).first().wikipediaEnabled)
    }
    @Test fun staleOrUnauthorizedWritesDoNotRecreateRetiredFeature()=runBlocking<Unit> {
        val c=capability();store.select(c,AppAppearance.NOTFLIX).getOrThrow()
        assertTrue(store.selectWikipediaEnabled(c,false){false}.isFailure);assertTrue(store.observe(c).first().wikipediaEnabled)
        factory.retireAppearanceProfile(2);assertTrue(store.selectWikipediaEnabled(c,false).isFailure)
        var written=false
        try{store.withWikipediaWriteContext(c,{true}){written=true};fail("Expected retired context rejection")}catch(_:IOException){}
        assertFalse(written)
    }
    @Test fun queuedCacheWriteRechecksAuthorizationInsideExistingRetirementBarrier()=runBlocking<Unit> {
        val c=capability();val held=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val blocker=async(Dispatchers.IO){factory.withAppearanceContext(c){held.complete(Unit);release.await()}};held.await()
        var allowed=true;var written=false
        val waiting=async(Dispatchers.IO){runCatching{store.withWikipediaWriteContext(c,{allowed}){written=true}}}
        allowed=false;release.complete(Unit);blocker.await();assertTrue(waiting.await().isFailure);assertFalse(written)
    }
    @Test fun confirmedSessionCanDisableImmediatelyAndReenableOnlyAfterSuccessfulSave()=runBlocking<Unit> {
        val c=capability();store.select(c,AppAppearance.NOTFLIX).getOrThrow()
        val persisted=MutableStateFlow<PersistedAppearanceProfile?>(null);val profiles=MutableStateFlow(listOf(UserProfile(1,"Synthetic1","#000000"),UserProfile(2,"Synthetic2","#000000")))
        val manager=mockk<ProfileManager>();every{manager.persistedAppearanceProfile} returns persisted;every{manager.profiles} returns profiles
        val vm=AppearanceViewModel(manager,store);owners.put("appearance",vm)
        assertFalse(vm.authorizesWikipedia(c,0));persisted.value=PersistedAppearanceProfile(2,1)
        val request=requireNotNull(vm.beginSessionConfirmation(2));assertTrue(vm.confirmSession(request))
        val selected=withTimeout(5000){vm.state.first{it.context==c && it.wikipediaEnabled}}
        assertTrue(vm.authorizesWikipedia(c,selected.selectionGeneration))
        vm.selectWikipediaEnabled(false);assertFalse(vm.authorizesWikipedia(c,selected.selectionGeneration))
        withTimeout(5000){store.observe(c).first{!it.wikipediaEnabled}}
        vm.selectWikipediaEnabled(true);withTimeout(5000){vm.state.first{it.wikipediaEnabled}}
        assertTrue(vm.authorizesWikipedia(c,selected.selectionGeneration));vm.cancelSessionConfirmation()
        assertFalse(vm.authorizesWikipedia(c,selected.selectionGeneration))
    }
}
