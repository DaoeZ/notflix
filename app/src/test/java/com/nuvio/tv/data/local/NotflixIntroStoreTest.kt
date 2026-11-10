package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import com.nuvio.tv.domain.model.AppAppearance
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Real disk/Preferences DataStore, strictly temporary mock Context directories. */
class NotflixIntroStoreTest {
    private val dir=Files.createTempDirectory("notflix-intro-store-").toFile()
    private val factories=mutableListOf<ProfileDataStoreFactory>()
    private fun factory(folder:File=dir):ProfileDataStoreFactory {
        val context=mockk<Context>(relaxed=true);every{context.filesDir} returns folder;every{context.applicationContext} returns context
        return ProfileDataStoreFactory(context).also{factories+=it}
    }
    private fun cap(store:NotflixAppearanceStore,id:Int=2)=requireNotNull(store.contextFor(id,true,setOf(1,2)))
    private suspend fun close(factory:ProfileDataStoreFactory) {
        val cache=ProfileDataStoreFactory::class.java.getDeclaredField("cache").apply{isAccessible=true}.get(factory) as MutableMap<*,*>
        cache.values.toList().forEach { scoped -> (scoped!!::class.java.getDeclaredField("job").apply{isAccessible=true}.get(scoped) as Job).cancelAndJoin() };cache.clear()
    }
    @After fun cleanup()=runBlocking { factories.forEach{close(it)};dir.deleteRecursively();Unit }

    @Test fun `legacy schema1 and absent flags default on without silent migration`()=runBlocking {
        val f=factory();val s=NotflixAppearanceStore(f);val c=cap(s);s.select(c,AppAppearance.NOTFLIX).getOrThrow()
        val read=s.observe(c).first();assertTrue(read.introEnabled);assertTrue(read.introSoundEnabled);assertTrue(read.introOptionsValid)
        val prefs=f.get(2,NotflixAppearanceStore.FEATURE).data.first();assertNull(prefs[booleanPreferencesKey(NotflixAppearanceStore.INTRO_ENABLED)])
        assertNull(prefs[booleanPreferencesKey(NotflixAppearanceStore.INTRO_SOUND_ENABLED)]);assertEquals(1,prefs[intPreferencesKey("schema_version")])
    }
    @Test fun `flags are strict booleans and malformed input cannot enable either option`()=runBlocking {
        val f=factory();val s=NotflixAppearanceStore(f);val c=cap(s);s.select(c,AppAppearance.NOTFLIX).getOrThrow()
        val disk=f.get(2,NotflixAppearanceStore.FEATURE)
        for(key in listOf(NotflixAppearanceStore.INTRO_ENABLED,NotflixAppearanceStore.INTRO_SOUND_ENABLED)) {
            disk.edit{it[stringPreferencesKey(key)]="true"}
            val read=s.observe(c).first();assertFalse(read.introEnabled);assertFalse(read.introSoundEnabled);assertFalse(read.introOptionsValid)
            assertEquals("true",disk.data.first()[stringPreferencesKey(key)])
            disk.edit{it.remove(stringPreferencesKey(key))}
        }
    }
    @Test fun `unknown schema remains unknown through optional flag writes`()=runBlocking {
        val f=factory();val s=NotflixAppearanceStore(f);val c=cap(s);s.select(c,AppAppearance.NOTFLIX).getOrThrow()
        val disk=f.get(2,NotflixAppearanceStore.FEATURE);disk.edit{it[intPreferencesKey("schema_version")]=99}
        s.selectIntroEnabled(c,true).getOrThrow();s.selectIntroSoundEnabled(c,true).getOrThrow()
        val read=s.observe(c).first();assertEquals(AppAppearance.NUVIO,read.requested);assertFalse(read.introEnabled);assertFalse(read.introOptionsValid)
        assertEquals(99,disk.data.first()[intPreferencesKey("schema_version")])
    }
    @Test fun `options are independent and preserve appearance motion and official bytes`()=runBlocking {
        val f=factory();val s=NotflixAppearanceStore(f);val c=cap(s,1)
        val official=f.get(1,"theme_settings");official.edit{it[stringPreferencesKey("sentinel")]="official"}
        val file=File(dir,"datastore/theme_settings.preferences_pb");val bytes=file.readBytes()
        s.select(c,AppAppearance.NOTFLIX).getOrThrow();s.selectHomeMotion(c,true).getOrThrow()
        s.selectIntroSoundEnabled(c,false).getOrThrow();assertTrue(s.observe(c).first().introEnabled)
        s.selectIntroEnabled(c,false).getOrThrow();s.selectIntroEnabled(c,true).getOrThrow()
        val read=s.observe(c).first();assertTrue(read.introEnabled);assertFalse(read.introSoundEnabled);assertTrue(read.reduceHomeMotion)
        assertEquals(AppAppearance.NOTFLIX,read.requested);assertArrayEquals(bytes,file.readBytes())
    }
    @Test fun `flags persist per profile installation and real reopen`()=runBlocking {
        val f=factory();val s=NotflixAppearanceStore(f);s.selectIntroEnabled(cap(s),false).getOrThrow();s.selectIntroSoundEnabled(cap(s),false).getOrThrow()
        assertTrue(s.observe(cap(s,1)).first().introEnabled);close(f)
        val reopened=NotflixAppearanceStore(factory());assertFalse(reopened.observe(cap(reopened)).first().introEnabled);assertFalse(reopened.observe(cap(reopened)).first().introSoundEnabled)
        val other=NotflixAppearanceStore(factory(File(dir,"other").apply{mkdirs()}));assertTrue(other.observe(cap(other)).first().introEnabled)
    }
    @Test fun `queued edit revalidates session inside mutation`()=runBlocking {
        withTimeout(10_000) {
            val f=factory();val s=NotflixAppearanceStore(f);val c=cap(s);val disk=f.get(2,NotflixAppearanceStore.FEATURE)
            val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var authorized=true
            val blocker=async{disk.edit{entered.complete(Unit);release.await()}};entered.await()
            val save=async(start=CoroutineStart.UNDISPATCHED){s.selectIntroSoundEnabled(c,false){authorized}}
            assertFalse(save.isCompleted);authorized=false;release.complete(Unit);blocker.await();assertTrue(save.await().isFailure)
            assertNull(disk.data.first()[booleanPreferencesKey(NotflixAppearanceStore.INTRO_SOUND_ENABLED)])
        }
    }
    @Test fun `cancellation never commits queued intro option`()=runBlocking {
        withTimeout(10_000) {
            val f=factory();val s=NotflixAppearanceStore(f);val c=cap(s);val disk=f.get(2,NotflixAppearanceStore.FEATURE)
            val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            val blocker=async{disk.edit{entered.complete(Unit);release.await()}};entered.await()
            val save=async(start=CoroutineStart.UNDISPATCHED){s.selectIntroEnabled(c,false)}
            save.cancelAndJoin();assertTrue(save.isCancelled);release.complete(Unit);blocker.await()
            assertNull(disk.data.first()[booleanPreferencesKey(NotflixAppearanceStore.INTRO_ENABLED)])
        }
    }
    @Test fun `retired context cannot write or leak old flags into recreated profile`()=runBlocking {
        val f=factory();val s=NotflixAppearanceStore(f);val old=cap(s);s.selectIntroEnabled(old,false).getOrThrow()
        f.clearProfile(2);assertTrue(s.selectIntroSoundEnabled(old,false).isFailure)
        f.markProfileCreated(2);f.prepareAppearanceProfile(2);val fresh=cap(s);assertNotEquals(old,fresh);assertTrue(s.observe(fresh).first().introEnabled)
    }
    @Test fun `read or write failure fails silent without activating intro`()=runBlocking {
        val file=File(dir,"datastore/notflix_presentation_local_p2.preferences_pb");file.mkdirs();File(file,"occupied").writeText("fixture")
        val s=NotflixAppearanceStore(factory());val c=cap(s)
        assertTrue(s.selectIntroEnabled(c,true).isFailure);val read=s.observe(c).first()
        assertTrue(read.failed);assertFalse(read.introEnabled);assertFalse(read.introSoundEnabled);assertFalse(read.introOptionsValid)
    }
}
