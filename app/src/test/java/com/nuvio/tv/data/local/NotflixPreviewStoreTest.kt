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

/** Real temporary Preferences DataStore; no application/profile preferences are modified. */
class NotflixPreviewStoreTest {
    private val dir=Files.createTempDirectory("notflix-preview-store-").toFile()
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
    @After fun cleanup()=runBlocking {factories.forEach{close(it)};dir.deleteRecursively();Unit}

    @Test fun `legacy flags default preview on sound off without migration or intro changes`()=runBlocking {
        val f=factory();val s=NotflixAppearanceStore(f);val c=cap(s);s.select(c,AppAppearance.NOTFLIX).getOrThrow()
        val read=s.observe(c).first();assertTrue(read.previewEnabled);assertFalse(read.previewSoundEnabled);assertTrue(read.previewOptionsValid)
        assertTrue(read.introEnabled);assertTrue(read.introSoundEnabled);assertTrue(read.introOptionsValid)
        val prefs=f.get(2,NotflixAppearanceStore.FEATURE).data.first()
        assertNull(prefs[booleanPreferencesKey(NotflixAppearanceStore.PREVIEW_ENABLED)]);assertNull(prefs[booleanPreferencesKey(NotflixAppearanceStore.PREVIEW_SOUND_ENABLED)])
    }
    @Test fun `malformed preview option cannot activate audio or change valid intro flags`()=runBlocking {
        val f=factory();val s=NotflixAppearanceStore(f);val c=cap(s);s.select(c,AppAppearance.NOTFLIX).getOrThrow();val disk=f.get(2,NotflixAppearanceStore.FEATURE)
        for(key in listOf(NotflixAppearanceStore.PREVIEW_ENABLED,NotflixAppearanceStore.PREVIEW_SOUND_ENABLED)) {
            disk.edit{it[stringPreferencesKey(key)]="true"};val read=s.observe(c).first()
            assertFalse(read.previewEnabled);assertFalse(read.previewSoundEnabled);assertFalse(read.previewOptionsValid)
            assertTrue(read.introEnabled);assertTrue(read.introSoundEnabled);assertEquals("true",disk.data.first()[stringPreferencesKey(key)])
            disk.edit{it.remove(stringPreferencesKey(key))}
        }
    }
    @Test fun `unknown schema remains unknown and flags cannot enable a preview`()=runBlocking {
        val f=factory();val s=NotflixAppearanceStore(f);val c=cap(s);s.select(c,AppAppearance.NOTFLIX).getOrThrow();val disk=f.get(2,NotflixAppearanceStore.FEATURE)
        disk.edit{it[intPreferencesKey("schema_version")]=99};s.selectPreviewEnabled(c,true).getOrThrow();s.selectPreviewSoundEnabled(c,true).getOrThrow()
        val read=s.observe(c).first();assertEquals(AppAppearance.NUVIO,read.requested);assertFalse(read.previewEnabled);assertFalse(read.previewSoundEnabled);assertFalse(read.previewOptionsValid)
        assertEquals(99,disk.data.first()[intPreferencesKey("schema_version")])
    }
    @Test fun `preview choices preserve intro motion appearance and official bytes`()=runBlocking {
        val f=factory();val s=NotflixAppearanceStore(f);val c=cap(s)
        val official=f.get(2,"theme_settings");official.edit{it[stringPreferencesKey("sentinel")]="official"}
        val file=File(dir,"datastore/theme_settings_p2.preferences_pb");val bytes=file.readBytes()
        s.select(c,AppAppearance.NOTFLIX).getOrThrow();s.selectHomeMotion(c,true).getOrThrow();s.selectIntroEnabled(c,false).getOrThrow();s.selectIntroSoundEnabled(c,true).getOrThrow()
        s.selectPreviewEnabled(c,false).getOrThrow();s.selectPreviewSoundEnabled(c,true).getOrThrow();val disabled=s.observe(c).first()
        assertFalse(disabled.previewEnabled);assertTrue(disabled.previewSoundEnabled)
        s.selectPreviewEnabled(c,true).getOrThrow();val read=s.observe(c).first()
        assertTrue(read.previewEnabled);assertTrue(read.previewSoundEnabled);assertTrue(read.reduceHomeMotion)
        assertFalse(read.introEnabled);assertTrue(read.introSoundEnabled);assertEquals(AppAppearance.NOTFLIX,read.requested);assertArrayEquals(bytes,file.readBytes())
    }
    @Test fun `preview options isolate profiles installations and real reopen`()=runBlocking {
        val f=factory();val s=NotflixAppearanceStore(f);s.selectPreviewEnabled(cap(s),false).getOrThrow();s.selectPreviewSoundEnabled(cap(s),true).getOrThrow()
        assertTrue(s.observe(cap(s,1)).first().previewEnabled);assertFalse(s.observe(cap(s,1)).first().previewSoundEnabled);close(f)
        val reopened=NotflixAppearanceStore(factory());val read=reopened.observe(cap(reopened)).first();assertFalse(read.previewEnabled);assertTrue(read.previewSoundEnabled)
        val other=NotflixAppearanceStore(factory(File(dir,"other").apply{mkdirs()}));assertTrue(other.observe(cap(other)).first().previewEnabled);assertFalse(other.observe(cap(other)).first().previewSoundEnabled)
    }
    @Test fun `queued preview edit revalidates authorization inside the mutation`()=runBlocking {
        withTimeout(10_000) {
            val f=factory();val s=NotflixAppearanceStore(f);val c=cap(s);val disk=f.get(2,NotflixAppearanceStore.FEATURE)
            val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var authorized=true
            val blocker=async{disk.edit{entered.complete(Unit);release.await()}};entered.await()
            val save=async(start=CoroutineStart.UNDISPATCHED){s.selectPreviewSoundEnabled(c,true){authorized}}
            assertFalse(save.isCompleted);authorized=false;release.complete(Unit);blocker.await();assertTrue(save.await().isFailure)
            assertNull(disk.data.first()[booleanPreferencesKey(NotflixAppearanceStore.PREVIEW_SOUND_ENABLED)])
        }
    }
    @Test fun `cancelled queued option never commits`()=runBlocking {
        withTimeout(10_000) {
            val f=factory();val s=NotflixAppearanceStore(f);val c=cap(s);val disk=f.get(2,NotflixAppearanceStore.FEATURE)
            val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            val blocker=async{disk.edit{entered.complete(Unit);release.await()}};entered.await()
            val save=async(start=CoroutineStart.UNDISPATCHED){s.selectPreviewEnabled(c,false)};save.cancelAndJoin();release.complete(Unit);blocker.await()
            assertTrue(save.isCancelled);assertNull(disk.data.first()[booleanPreferencesKey(NotflixAppearanceStore.PREVIEW_ENABLED)])
        }
    }
    @Test fun `retired profile cannot write or leak flags into reused id`()=runBlocking {
        val f=factory();val s=NotflixAppearanceStore(f);val old=cap(s);s.selectPreviewSoundEnabled(old,true).getOrThrow()
        f.clearProfile(2);assertTrue(s.selectPreviewEnabled(old,false).isFailure);f.markProfileCreated(2);f.prepareAppearanceProfile(2)
        val fresh=cap(s);assertNotEquals(old,fresh);val read=s.observe(fresh).first();assertTrue(read.previewEnabled);assertFalse(read.previewSoundEnabled)
    }
    @Test fun `disk failure neither enables preview nor changes intro failure policy`()=runBlocking {
        val file=File(dir,"datastore/notflix_presentation_local_p2.preferences_pb");file.mkdirs();File(file,"occupied").writeText("fixture")
        val s=NotflixAppearanceStore(factory());val c=cap(s);assertTrue(s.selectPreviewEnabled(c,true).isFailure)
        val read=s.observe(c).first();assertTrue(read.failed);assertFalse(read.previewEnabled);assertFalse(read.previewSoundEnabled);assertFalse(read.previewOptionsValid);assertFalse(read.introEnabled)
    }
}
