package com.nuvio.tv.ui.appearance

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class NotflixIntroPresentationTest {
    @get:Rule val compose=createComposeRule()
    private fun show(s:IntroFixtureState){compose.setContent{IntroFixture(s)};compose.waitForIdle()}
    private fun native(s:IntroFixtureState,code:Int,action:Int,repeat:Int=0){compose.runOnUiThread{val time=SystemClock.uptimeMillis();s.view.dispatchKeyEvent(KeyEvent(time,time,action,code,repeat))};compose.waitForIdle()}
    private fun fresh(s:IntroFixtureState,code:Int){native(s,code,0);native(s,code,1)}
    private fun destination()=compose.onNodeWithTag("intro_underlying_action")
    private fun activateTag(tag:String){val n=compose.onNodeWithTag(tag);n.performSemanticsAction(SemanticsActions.RequestFocus);compose.waitForIdle();n.assertIsFocused();n.performKeyInput{pressKey(androidx.compose.ui.input.key.Key.DirectionCenter)};compose.waitForIdle()}

    @Test fun naturalCompletionReleasesOnceAndKeepsSameDestinationFocus() {
        val s=IntroFixtureState();show(s)
        destination().assertDoesNotExist();compose.onNodeWithContentDescription("Skip intro").assertIsDisplayed()
        compose.runOnIdle{assertEquals(1,s.decoder.created);s.decoder.complete()}
        destination().assertIsFocused();compose.runOnIdle{assertEquals(1,s.decoder.released);assertEquals(0,s.actions);assertEquals(1,s.destinationOwners.size)}
    }
    @Test fun centerHoldSkipDrainsReleaseWithoutClickingUnderlyingTitle()=skip(KeyEvent.KEYCODE_DPAD_CENTER)
    @Test fun backHoldSkipDrainsReleaseWithoutLeavingDestination()=skip(KeyEvent.KEYCODE_BACK)
    @Test fun enterHoldSkipDrainsReleaseWithoutClickingUnderlyingTitle()=skip(KeyEvent.KEYCODE_ENTER)
    private fun skip(code:Int) {
        val s=IntroFixtureState();show(s);native(s,code,0)
        for(repeat in 1..5)native(s,code,0,repeat);native(s,code,1)
        destination().assertIsFocused();compose.runOnIdle{assertEquals(0,s.actions);assertEquals(1,s.decoder.released);assertTrue(s.destinationKeys.isEmpty())}
        fresh(s,KeyEvent.KEYCODE_DPAD_CENTER);compose.runOnIdle{assertEquals(1,s.actions)}
    }
    @Test fun incomingSelectionReleaseAndRepeatDoNotImmediatelySkipIntro() {
        val s=IntroFixtureState();show(s);native(s,KeyEvent.KEYCODE_DPAD_CENTER,0,1);native(s,KeyEvent.KEYCODE_DPAD_CENTER,1)
        compose.runOnIdle{assertTrue(s.coordinator.blocking);assertEquals(0,s.decoder.released);assertEquals(0,s.actions);s.decoder.complete()}
        destination().assertIsFocused()
    }
    @Test fun directionHeldAcrossEndNeverReachesContentUntilFreshPress() {
        val s=IntroFixtureState();show(s);native(s,KeyEvent.KEYCODE_DPAD_RIGHT,0)
        compose.runOnIdle{s.decoder.complete()};native(s,KeyEvent.KEYCODE_DPAD_RIGHT,0,3);native(s,KeyEvent.KEYCODE_DPAD_RIGHT,1)
        compose.runOnIdle{assertTrue(s.destinationKeys.isEmpty())};fresh(s,KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.runOnIdle{assertTrue(KeyEvent.KEYCODE_DPAD_RIGHT in s.destinationKeys);assertEquals(0,s.actions)}
    }
    @Test fun disabledStartupDoesNotConstructDecoderOrCoverUsefulDestination() {
        val s=IntroFixtureState().apply{input=input.copy(enabled=false)};show(s);destination().assertIsFocused()
        compose.runOnIdle{assertEquals(0,s.decoder.created);assertEquals(IntroPhase.Finished(IntroFinishReason.DISABLED),s.coordinator.state.value)}
        fresh(s,KeyEvent.KEYCODE_DPAD_CENTER);compose.runOnIdle{assertEquals(1,s.actions)}
    }
    @Test fun mutedPreferencePassesFalseToDecoderAndClosesOnce() {
        val s=IntroFixtureState().apply{input=input.copy(sound=false)};show(s)
        compose.runOnIdle{assertEquals(listOf(false),s.decoder.sounds);s.decoder.complete()}
        destination().assertIsFocused();compose.runOnIdle{assertEquals(1,s.decoder.released)}
    }
    @Test fun playerErrorAndLateCompleteNeverDoubleCloseOrBlockDestination() {
        val s=IntroFixtureState();show(s);compose.runOnIdle{s.decoder.error();s.decoder.complete()}
        destination().assertIsFocused();compose.runOnIdle{assertEquals(1,s.decoder.released);assertEquals(IntroPhase.Finished(IntroFinishReason.ERROR),s.coordinator.state.value)}
    }
    @Test fun synchronousPreparationErrorClosesHandleRegisteredAfterFailure() {
        val s=IntroFixtureState().apply{decoder.earlyError=true};show(s);destination().assertIsFocused()
        compose.runOnIdle{assertEquals(1,s.decoder.created);assertEquals(1,s.decoder.released);assertFalse(s.coordinator.blocking)}
    }
    @Test fun missingSurfaceDeadlineRestoresDestinationAndReleasesFake() {
        val s=IntroFixtureState().apply{deadline=150};show(s)
        compose.waitUntil(3_000){s.coordinator.state.value is IntroPhase.Finished};compose.waitForIdle();destination().assertIsFocused()
        compose.runOnIdle{assertEquals(1,s.decoder.released);assertEquals(IntroPhase.Finished(IntroFinishReason.DEADLINE),s.coordinator.state.value)}
    }
    @Test fun unresolvedSplashDeadlineDoesNotCreateDecoderOrLeaveInputBlocked() {
        val s=IntroFixtureState().apply{deadline=150;input=input.copy(surfaceReady=false)};show(s)
        compose.waitUntil(3_000){s.coordinator.state.value is IntroPhase.Finished};compose.waitForIdle();destination().assertIsFocused()
        compose.runOnIdle{assertEquals(0,s.decoder.created);assertFalse(s.coordinator.blocking)}
    }
    @Test fun officialSplashCompletionStartsReservedLeaseWithoutRecreatingDestination() {
        val s=IntroFixtureState().apply{input=input.copy(surfaceReady=false)};show(s)
        compose.runOnIdle{assertEquals(0,s.decoder.created);assertTrue(s.coordinator.state.value is IntroPhase.Armed)}
        compose.runOnIdle{s.input=s.input.copy(surfaceReady=true)}
        compose.runOnIdle{assertEquals(1,s.decoder.created);assertEquals(1,s.destinationOwners.size);s.decoder.complete()}
        destination().assertIsFocused();compose.runOnIdle{assertEquals(1,s.decoder.released);assertEquals(0,s.actions)}
    }
    @Test fun reservedDestinationStaysBlackDuringOfficialFadeWithoutLosingItsOwner() {
        val s=IntroFixtureState().apply{input=input.copy(surfaceReady=false)};show(s)
        val pixels=compose.onRoot().captureToImage().toPixelMap()
        for((x,y) in listOf(10 to 10,pixels.width/2 to pixels.height/2,(pixels.width-10) to (pixels.height-10)))
            assertEquals(Color.Black,pixels[x,y])
        compose.runOnIdle{assertEquals(0,s.decoder.created);s.input=s.input.copy(surfaceReady=true)}
        compose.runOnIdle{s.decoder.complete()};destination().assertIsFocused()
        compose.runOnIdle{assertEquals(1,s.destinationOwners.size);assertEquals(0,s.actions)}
    }
    @Test fun officialSplashWaitDoesNotConsumeMountedDecoderDeadline() {
        val s=IntroFixtureState().apply{deadline=2_000;input=input.copy(surfaceReady=false)};show(s)
        SystemClock.sleep(1_200)
        compose.runOnIdle{s.input=s.input.copy(surfaceReady=true)}
        compose.runOnIdle{assertEquals(1,s.decoder.created)}
        SystemClock.sleep(1_000)
        compose.runOnIdle{assertTrue(s.coordinator.state.value is IntroPhase.Running);assertEquals(0,s.decoder.released);s.decoder.complete()}
        destination().assertIsFocused();compose.runOnIdle{assertEquals(1,s.decoder.released)}
    }
    @Test fun backgroundThenForegroundDoesNotResumeOrReconstructIntro() {
        val s=IntroFixtureState();show(s);compose.runOnUiThread{s.lifecycle.pause()};compose.waitForIdle()
        compose.runOnUiThread{s.lifecycle.resume()};compose.waitForIdle();destination().assertIsFocused()
        compose.runOnIdle{assertEquals(1,s.decoder.created);assertEquals(1,s.decoder.released);assertFalse(s.coordinator.blocking)}
    }
    @Test fun recreatedOwnerAndChangedProfileDoNotConstructSecondDecoder() {
        val s=IntroFixtureState();show(s);compose.runOnIdle{s.owner=Any()};compose.waitForIdle();destination().assertIsFocused()
        compose.runOnIdle{s.input=s.input.copy(binding=IntroBinding(com.nuvio.tv.domain.model.AppearanceContext(1,9),11))}
        compose.runOnIdle{assertEquals(1,s.decoder.created);assertEquals(1,s.decoder.released);assertEquals(1,s.destinationOwners.size)}
    }
    @Test fun firstNuvioDecisionNeverPlaysAfterSwitchingToNotflix() {
        val s=IntroFixtureState().apply{input=input.copy(notflix=false)};show(s)
        compose.runOnIdle{s.input=s.input.copy(notflix=true)};destination().assertIsFocused();compose.runOnIdle{assertEquals(0,s.decoder.created)}
    }
    @Test fun systemMotionAndExistingMediaRespectSafeOmission() {
        val s=IntroFixtureState().apply{systemBusy=true};show(s);destination().assertIsFocused();compose.runOnIdle{assertEquals(0,s.decoder.created)}
    }
    @Test fun motionDisabledDoesNotConstructDecoder() {
        val s=IntroFixtureState().apply{motionAllowed=false};show(s);destination().assertIsFocused();compose.runOnIdle{assertEquals(0,s.decoder.created)}
    }
    @Test fun spanishSkipLabelUsesOnlyLocalTestConfiguration() {
        val s=IntroFixtureState().apply{spanish=true};show(s)
        compose.onNodeWithContentDescription("Omitir intro").assertIsDisplayed().performClick()
        destination().assertIsFocused();compose.runOnIdle{assertEquals(1,s.decoder.released);assertEquals(0,s.actions)}
    }
    @Test fun settingsOptionsAreIndependentAndUseActualCallbacks()=settings(false)
    @Test fun spanishSettingsKeepIndependentIntroAndSoundLabels()=settings(true)
    private fun settings(spanish:Boolean) {
        val s=IntroFixtureState().apply{this.spanish=spanish;settings=true;input=IntroDecisionInput()};show(s)
        compose.onNodeWithText(if(spanish)"Intro al iniciar" else "Intro at startup").assertIsDisplayed()
        compose.onNodeWithText(if(spanish)"Sonido de la intro" else "Intro sound").assertIsDisplayed()
        activateTag("intro-sound-false");compose.onNodeWithTag("intro-sound-false").assertIsSelected();compose.onNodeWithTag("intro-enabled-true").assertIsSelected()
        activateTag("intro-enabled-false");compose.onNodeWithTag("intro-enabled-false").assertIsSelected();compose.onNodeWithTag("intro-sound-false").assertIsSelected()
        compose.runOnIdle{assertEquals(listOf(false),s.introChoices);assertEquals(listOf(false),s.soundChoices);assertEquals(0,s.decoder.created)}
    }
}
