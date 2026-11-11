package com.nuvio.tv.ui.screens.home

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nuvio.tv.domain.model.AppearanceContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Product card/rows/preview controller with explicitly synthetic metadata, resolver and colored renderer. */
@OptIn(ExperimentalTestApi::class)
class NotflixPreviewPresentationTest {
    @get:Rule val compose=createComposeRule()
    private fun show(s:PreviewFixtureState){compose.setContent{PreviewFixture(s)};compose.waitForIdle()}
    private fun card(s:PreviewFixtureState,row:Int=0,index:Int=0)=compose.onNodeWithTag(s.tag(row,index))
    private fun key(s:PreviewFixtureState,key:Key,row:Int=0,index:Int=0){card(s,row,index).performKeyInput{pressKey(key)};compose.waitForIdle()}
    private fun mounted(s:PreviewFixtureState,count:Int=1){compose.waitUntil(10_000){s.fake.mounts.size==count};compose.waitForIdle()}
    private fun waitExpanded(s:PreviewFixtureState){compose.waitUntil(10_000){s.memory.focus?.expanded==true};compose.waitForIdle()}
    private fun active(s:PreviewFixtureState)=requireNotNull(s.session.coordinator.state.value.lease())
    private fun native(s:PreviewFixtureState,action:Int,repeat:Int=0){compose.runOnUiThread{
        val now=SystemClock.uptimeMillis();s.view.dispatchKeyEvent(KeyEvent(now,now,action,KeyEvent.KEYCODE_DPAD_CENTER,repeat,0,-1,0,if(repeat>0)KeyEvent.FLAG_LONG_PRESS else 0))
    };compose.waitForIdle()}

    @Test fun previewStaysInsideUpperArtworkAndPreservesPanelNeighboursAndFocus() {
        val s=PreviewFixtureState();show(s);mounted(s)
        val art=compose.onNodeWithTag("notflix-card-art-${s.identity()}",true).getUnclippedBoundsInRoot()
        val video=compose.onNodeWithTag("preview-video-${s.identity()}",true).getUnclippedBoundsInRoot()
        val panel=compose.onNodeWithTag("notflix-card-panel-${s.identity()}",true).getUnclippedBoundsInRoot()
        assertEquals(art.left.value,video.left.value,.5f);assertEquals(art.right.value,video.right.value,.5f)
        assertTrue(video.bottom<=panel.top);card(s).assertIsFocused();card(s,0,1).assertExists()
        compose.onAllNodesWithText("Drama",useUnmergedTree=true).assertCountEquals(1)
        compose.runOnIdle{assertEquals(1,s.fake.mounts.size);assertTrue(s.details.isEmpty());assertTrue(s.holds.isEmpty())}
    }
    @Test fun rapidRowSweepDoesNotResolveIntermediateTitles() {
        val s=PreviewFixtureState().apply{environment=environment.copy(enabled=false)};show(s)
        compose.runOnUiThread{
            s.environment=s.environment.copy(enabled=true)
            repeat(4){val time=SystemClock.uptimeMillis();s.view.dispatchKeyEvent(KeyEvent(time,time,0,22,it));s.view.dispatchKeyEvent(KeyEvent(time,time,1,22,0))}
        }
        mounted(s);compose.runOnIdle{assertEquals(1,s.resolver.calls.size);assertTrue(s.fake.mounts.single().item.id!="same")}
    }
    @Test fun sameCanonicalIdInAnotherRowChangesOwnerInsteadOfSharingItsSurface() {
        val s=PreviewFixtureState();show(s);mounted(s);key(s,Key.DirectionDown)
        mounted(s,2);card(s,1).assertIsFocused()
        compose.runOnIdle{assertEquals("same",s.fake.mounts[0].item.id);assertEquals("same",s.fake.mounts[1].item.id)
            assertNotEquals(s.fake.mounts[0].visit.identity,s.fake.mounts[1].visit.identity);assertEquals(1,s.fake.closes)}
    }
    @Test fun noncooperativeOldResolutionNeverOverlaysTheNewFocusedCard() {
        val s=PreviewFixtureState();val late=CompletableDeferred<PreviewSource?>()
        s.resolver.answer={if(it.serial==1L)withContext(NonCancellable){late.await()}else PreviewSource("https://fixture.invalid/current")}
        show(s)
        try {
            compose.waitUntil(10_000){s.resolver.calls.size==1};key(s,Key.DirectionRight);mounted(s)
            compose.runOnIdle{late.complete(PreviewSource("https://fixture.invalid/late"))};compose.waitForIdle()
            card(s,0,1).assertIsFocused();compose.runOnIdle{assertEquals(1,s.fake.mounts.size);assertEquals("A2",s.fake.mounts.single().item.id)}
        } finally {late.complete(null)}
    }
    @Test fun completionReturnsToStaticArtWithoutRetryOrLosingActionFocus() {
        val s=PreviewFixtureState();show(s);mounted(s);compose.runOnIdle{s.session.coordinator.finish(active(s),PreviewEnd.COMPLETE)}
        card(s).assertIsFocused();compose.onNodeWithTag("preview-video-${s.identity()}",true).assertDoesNotExist()
        SystemClock.sleep(700);compose.runOnIdle{assertEquals(1,s.fake.closes);assertEquals(1,s.resolver.calls.size)}
    }
    @Test fun detailClickDispatchesOnceAndReturnRestoresTheSameCardIdentity() {
        val s=PreviewFixtureState();show(s);mounted(s);key(s,Key.DirectionCenter)
        compose.onNodeWithTag("preview-fixture-detail-back").assertIsFocused()
        compose.runOnIdle{assertEquals(listOf("same"),s.details);assertEquals(1,s.fake.closes)}
        compose.onNodeWithTag("preview-fixture-detail-back").performKeyInput{pressKey(Key.DirectionCenter)}
        compose.waitUntil(10_000){runCatching{card(s).assertIsFocused();true}.getOrDefault(false)}
        card(s).assertIsFocused()
    }
    @Test fun heldCenterOpensOneOptionsCallbackWithoutASecondDetailAction() {
        val s=PreviewFixtureState();show(s);mounted(s);native(s,0);native(s,0,1);native(s,0,2);native(s,1)
        card(s).assertIsFocused();compose.runOnIdle{assertEquals(listOf("same"),s.holds);assertTrue(s.details.isEmpty());assertEquals(1,s.fake.closes)}
    }
    @Test fun backgroundCancelsAndResumeDoesNotReplayTheSameVisit() {
        val s=PreviewFixtureState();show(s);mounted(s);compose.runOnUiThread{s.lifecycle.pause()};compose.waitForIdle()
        compose.runOnIdle{assertEquals(1,s.fake.closes)}
        compose.runOnUiThread{s.lifecycle.resume()};compose.waitForIdle();SystemClock.sleep(700)
        card(s).assertIsFocused();compose.runOnIdle{assertEquals(1,s.fake.closes);assertEquals(1,s.fake.mounts.size)}
    }
    @Test fun profileRevocationClosesMediaAndCannotUseTheOldBindingAgain() {
        val s=PreviewFixtureState();show(s);mounted(s)
        compose.runOnIdle{s.environment=s.environment.copy(binding=PreviewBinding(AppearanceContext(1,9),12))}
        compose.waitForIdle();compose.runOnIdle{assertEquals(1,s.fake.closes)}
        compose.runOnIdle{s.environment=s.environment.copy(binding=s.binding)};compose.waitForIdle();SystemClock.sleep(700)
        compose.runOnIdle{assertEquals(1,s.fake.mounts.size)}
    }
    @Test fun absentSourceLeavesExpandedFactsAndClickUsefulWithoutRetries() {
        val s=PreviewFixtureState().apply{resolver.answer={null}};show(s)
        compose.waitUntil(10_000){s.session.coordinator.state.value is PreviewPhase.Finished};card(s).assertIsFocused()
        compose.runOnIdle{s.state=s.state.copy(error="Synthetic refresh")};compose.waitForIdle();SystemClock.sleep(700)
        compose.runOnIdle{assertTrue(s.fake.mounts.isEmpty());assertEquals(1,s.resolver.calls.size)}
        key(s,Key.DirectionCenter);compose.runOnIdle{assertEquals(listOf("same"),s.details)}
    }
    @Test fun resolutionErrorFallsBackToArtworkWithoutBlockingNavigation() {
        val s=PreviewFixtureState().apply{resolver.answer={throw IllegalStateException("synthetic")}};show(s)
        compose.waitUntil(10_000){s.session.coordinator.state.value is PreviewPhase.Finished};card(s).assertIsFocused()
        key(s,Key.DirectionRight);card(s,0,1).assertIsFocused();compose.runOnIdle{assertTrue(s.fake.mounts.isEmpty())}
    }
    @Test fun reducedMotionExpandsStaticallyAndNeverRequestsAVideo() {
        val s=PreviewFixtureState().apply{environment=environment.copy(reduced=true)};show(s);waitExpanded(s)
        card(s).assertIsFocused();compose.onNodeWithTag("notflix-card-panel-${s.identity()}",true).assertExists()
        compose.runOnIdle{assertTrue(s.resolver.calls.isEmpty());assertTrue(s.fake.mounts.isEmpty())}
    }
    @Test fun disabledPreviewDoesNotResolveEvenWithNormalMotionAndExpandedCard() {
        val s=PreviewFixtureState().apply{environment=environment.copy(enabled=false)};show(s);waitExpanded(s)
        compose.runOnIdle{assertTrue(s.resolver.calls.isEmpty());assertTrue(s.fake.mounts.isEmpty())}
    }
    @Test fun soundPermissionIsIndependentAndReducedStillOverridesPlayback() {
        val s=PreviewFixtureState().apply{environment=environment.copy(sound=true)};show(s);mounted(s)
        compose.runOnIdle{assertTrue(s.fake.mounts.single().sound);s.environment=s.environment.copy(reduced=true)};compose.waitForIdle()
        compose.runOnIdle{assertEquals(1,s.fake.closes);assertEquals(1,s.fake.mounts.size)}
    }
    @Test fun topBarCancelsAReservedFocusBeforeResolution() {
        val s=PreviewFixtureState().apply{environment=environment.copy(enabled=false)};show(s)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("notflix-destination-home").assertIsFocused()
        compose.runOnIdle{s.environment=s.environment.copy(enabled=true)};compose.waitForIdle();SystemClock.sleep(700)
        compose.runOnIdle{assertTrue(s.navigation.topFocused);assertTrue(s.resolver.calls.isEmpty())}
    }
    @Test fun dialogWindowOrIntroExclusionDropsTheOnlyOwner() {
        val s=PreviewFixtureState();show(s);mounted(s);compose.runOnIdle{s.environment=s.environment.copy(windowFocused=false,introActive=true)};compose.waitForIdle()
        compose.runOnIdle{assertEquals(1,s.fake.closes);s.environment=s.environment.copy(windowFocused=true,introActive=false)};compose.waitForIdle();SystemClock.sleep(700)
        compose.runOnIdle{assertEquals(1,s.fake.mounts.size)}
    }
    @Test fun externalReadOnlyGuardIsRecheckedOnTheNextRealFocusVisit() {
        val s=PreviewFixtureState().apply{liveBlocked=true};show(s);waitExpanded(s)
        compose.runOnIdle{assertTrue(s.resolver.calls.isEmpty());s.liveBlocked=false}
        key(s,Key.DirectionRight);mounted(s);card(s,0,1).assertIsFocused()
        compose.runOnIdle{assertEquals(1,s.resolver.calls.size);assertEquals("A2",s.fake.mounts.single().item.id)}
    }
    @Test fun englishOptionsUseIndependentPreviewCallbacksAndKeepIntroUnchanged()=settings(false)
    @Test fun spanishOptionsExplainReducedAndKeepIntroUnchanged()=settings(true)
    private fun activate(tag:String){val n=compose.onNodeWithTag(tag);n.performScrollTo();n.performSemanticsAction(SemanticsActions.RequestFocus);compose.waitForIdle();n.performKeyInput{pressKey(Key.DirectionCenter)};compose.waitForIdle()}
    private fun settings(spanish:Boolean) {
        val s=PreviewFixtureState().apply{this.spanish=spanish;settings=true};show(s)
        compose.onNodeWithText(if(spanish)"Tráilers en tarjetas" else "Card previews").performScrollTo().assertIsDisplayed()
        activate("preview-sound");compose.onNodeWithTag("preview-enabled").assertIsOn();compose.onNodeWithTag("preview-sound").assertIsOn()
        activate("preview-enabled");compose.onNodeWithTag("preview-enabled").assertIsOff();compose.onNodeWithTag("preview-sound").assertIsOn()
        compose.onNodeWithTag("intro-enabled").assertIsOn();compose.onNodeWithTag("intro-sound").assertIsOn()
        compose.runOnIdle{assertEquals(listOf(false),s.previewChoices);assertEquals(listOf(true),s.soundChoices);assertTrue(s.resolver.calls.isEmpty())}
    }
}
