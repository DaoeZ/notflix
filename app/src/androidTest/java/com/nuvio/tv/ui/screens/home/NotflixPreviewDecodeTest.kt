package com.nuvio.tv.ui.screens.home

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.nuvio.tv.core.player.TrailerPlayerPool
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import com.nuvio.tv.data.local.ProfileDataStore
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import com.nuvio.tv.data.local.TrailerSettingsDataStore
import com.squareup.moshi.Moshi
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Two explicitly selected legal-only trials. Never included in the fake/default instrumentation list. */
@OptIn(ExperimentalTestApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NotflixPreviewDecodeTest {
    @get:Rule val compose=createComposeRule()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val args=InstrumentationRegistry.getArguments()

    private class OwnPool(base:Context) {
        val root=File(base.cacheDir,"nf003-m15/${UUID.randomUUID()}").apply{mkdirs()}
        val context=object:ContextWrapper(base){override fun getApplicationContext():Context=this;override fun getFilesDir():File=root}
        private val job=SupervisorJob()
        private val disk=PreferenceDataStoreFactory.create(scope=CoroutineScope(Dispatchers.IO+job)){File(root,"profile.preferences_pb")}
        private val factory=ProfileDataStoreFactory(context)
        private val manager=ProfileManager(ProfileDataStore(context,Moshi.Builder().build(),disk),factory,emptySet(),context)
        private val settings=PlayerSettingsDataStore(factory,manager,context)
        val pool=TrailerPlayerPool(context,settings,TrailerSettingsDataStore(context,factory,manager))
        fun closeScopes()=runBlocking {
            for((target,name) in listOf(manager to "scope",pool to "settingsScope",settings to "ioScope")) {
                val scope=target::class.java.getDeclaredField(name).apply{isAccessible=true}.get(target) as CoroutineScope
                scope.coroutineContext[Job]?.cancelAndJoin()
            }
            val cache=ProfileDataStoreFactory::class.java.getDeclaredField("cache").apply{isAccessible=true}.get(factory) as MutableMap<*,*>
            cache.values.toList().forEach { scoped ->(scoped!!::class.java.getDeclaredField("job").apply{isAccessible=true}.get(scoped) as Job).cancelAndJoin()};cache.clear()
            job.cancelAndJoin()
        }
    }

    @Test fun realLegalPreviewChangesArtworkAndReleasesWhenFocusLeaves()=runLegal("surface",false)
    @Test fun realLegalPreviewStopsOnBackgroundAndDoesNotResumeTheVisit()=runLegal("background",true)

    private fun runLegal(case:String,background:Boolean) {
        assertEquals("allow",args.getString("nf_m15_real_preview"))
        assertEquals(case,args.getString("nf_m15_case"))
        val ordinal=requireNotNull(args.getString("nf_m15_ordinal")).toInt();assertTrue(ordinal in 1..3)
        val run=requireNotNull(args.getString("nf_m15_run"));require(Regex("[a-zA-Z0-9_-]{1,64}").matches(run))
        val directory=File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)),"nf003-m15/$run/$case")
        check(!directory.exists());check(directory.mkdirs())
        val own=OwnPool(instrumentation.targetContext)
        val s=PreviewFixtureState().apply{renderer=OfficialNotflixPreviewRenderer(own.pool)}
        val gate=CompletableDeferred<PreviewSource?>()
        // This URL exists only in androidTest. Later focus visits deliberately have no source.
        s.resolver.answer={lease->if(lease.serial==1L)gate.await()else null}
        var budget:Job?=null;val budgetScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        var start=0L;var stop=0L;var first=0L;var second=0L
        fun capture(name:String) {
            val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(directory,"$name.png").outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))};bitmap.recycle()
        }
        val rectangles=JSONObject()
        try {
            compose.setContent{PreviewFixture(s)}
            compose.waitUntil(10_000){s.memory.focus?.expanded==true && s.resolver.calls.isNotEmpty()};compose.waitForIdle()
            val location=IntArray(2);compose.runOnUiThread{s.view.getLocationOnScreen(location)}
            fun record(label:String,tag:String) {
                val r:Rect=compose.onNodeWithTag(tag,true).fetchSemanticsNode().boundsInRoot
                rectangles.put(label,JSONArray(listOf(r.left+location[0],r.top+location[1],r.right+location[0],r.bottom+location[1])))
            }
            record("art","notflix-card-art-${s.identity()}");record("panel","notflix-card-panel-${s.identity()}");record("neighbour",s.tag(0,1))
            capture("static")
            start=SystemClock.elapsedRealtime()
            // Stop ten seconds after the sole legal source is released, leaving cleanup margin in the15s host reservation.
            budget=budgetScope.launch{delay(10_000);s.environment=s.environment.copy(enabled=false);if(s.hasSession())s.session.cancel()}
            compose.runOnIdle{gate.complete(PreviewSource("http://10.0.2.2:18764/media/bbb.mp4"))}
            compose.waitUntil(8_000){(s.session.coordinator.state.value as? PreviewPhase.Playing)?.firstFrame==true}
            compose.waitForIdle()
            compose.runOnUiThread{first=own.pool.acquire()!!.currentPosition}
            capture("first-frame")
            if(background) {
                compose.runOnUiThread{s.lifecycle.pause()};compose.waitForIdle()
                compose.runOnUiThread{s.lifecycle.resume()};compose.waitForIdle();SystemClock.sleep(700)
                compose.runOnIdle{assertEquals(1,s.resolver.calls.size)}
            } else {
                compose.waitUntil(6_000){var position=0L;compose.runOnUiThread{position=own.pool.acquire()!!.currentPosition};position>=first+2_000}
                compose.runOnUiThread{second=own.pool.acquire()!!.currentPosition};capture("later-frame")
                compose.onNodeWithTag(s.tag()).performKeyInput{pressKey(Key.DirectionRight)};compose.waitForIdle()
                compose.onNodeWithTag(s.tag(0,1)).assertIsFocused()
            }
            compose.runOnIdle{s.environment=s.environment.copy(enabled=false)};compose.waitForIdle()
            compose.runOnUiThread{val player=own.pool.acquire()!!;assertFalse(player.playWhenReady);assertEquals(0,player.mediaItemCount);assertFalse(own.pool.isTrailerPlaying.value)}
            stop=SystemClock.elapsedRealtime();assertTrue("Preview exceeded15s reservation",stop-start<=15_000)
            capture("stopped")
            val result=JSONObject().put("case",case).put("run",run).put("ordinal",ordinal)
                .put("real_component","TrailerPlayer + TrailerPlayerPool, original sources unchanged")
                .put("resolver","androidTest controlled legal-only URL; no production catalog/addon mutation")
                .put("media_sha256","f78f39603e6774907f2faafabf26a667f4a6fc31769ec304a8a8f7c62d280508")
                .put("license","Big Buck Bunny (c)2008 Blender Foundation, CC BY3.0; existing M4 sample")
                .put("url","http://10.0.2.2:18764/media/bbb.mp4").put("default_muted",true)
                .put("source_release_to_stop_ms",stop-start).put("first_position_ms",first).put("later_position_ms",second)
                .put("media_items_after",0).put("pool_playing_after",false).put("rectangles_screen_px",rectangles)
                .put("own_store_root",own.root.absolutePath).put("limit","Preview seam only; no main player, progress, audible AV, EOF or physical performance proof.")
            File(directory,"result.json").writeText(result.toString(2))
            Log.d("NotflixPreviewTest","artifact_dir=${directory.absolutePath}")
            println("NF_M15_ARTIFACT_DIR=${directory.absolutePath}")
        } finally {
            budget?.cancel();budgetScope.cancel();gate.complete(null)
            compose.runOnUiThread {if(s.hasSession())s.session.cancel();own.pool.stop();own.pool.release()}
            own.closeScopes()
            File(directory,"cleanup.json").writeText(JSONObject().put("finished_elapsed_ms",SystemClock.elapsedRealtime())
                .put("source_release_elapsed_ms",start).put("observed_stop_elapsed_ms",stop).put("own_pool_released",true)
                .put("limit","Own test pool and IO jobs only; shared application pool/data were not released/reset.").toString(2))
            Log.d("NotflixPreviewTest","cleanup_dir=${directory.absolutePath}")
        }
    }
}
