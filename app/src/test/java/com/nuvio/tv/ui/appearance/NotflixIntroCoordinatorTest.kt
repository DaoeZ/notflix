package com.nuvio.tv.ui.appearance

import com.nuvio.tv.domain.model.AppearanceContext
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class NotflixIntroCoordinatorTest {
    private val owner = Any()
    private val binding = IntroBinding(AppearanceContext(2, 7), 11)
    private fun ready() = IntroDecisionInput(true,binding,true,true,true,true,true)
    private fun running(): NotflixIntroCoordinator = NotflixIntroCoordinator().also { it.evaluate(owner,ready()) }

    @Test fun `loading is waiting and not an initial Nuvio decision`() {
        val c=NotflixIntroCoordinator();repeat(5){c.evaluate(owner,IntroDecisionInput())}
        assertEquals(IntroPhase.Waiting,c.state.value);assertFalse(c.blocking)
        c.evaluate(owner,ready());assertTrue(c.state.value is IntroPhase.Running)
    }
    @Test fun `first confirmed Nuvio is consumed even while splash is showing`() {
        val c=NotflixIntroCoordinator();c.evaluate(owner,ready().copy(notflix=false,surfaceReady=false))
        assertEquals(IntroPhase.Finished(IntroFinishReason.NUVIO),c.state.value)
        c.evaluate(owner,ready());assertFalse(c.blocking)
    }
    @Test fun `disabled or invalid options never become a later intro`() {
        for(input in listOf(ready().copy(enabled=false),ready().copy(optionsValid=false))) {
            val c=NotflixIntroCoordinator();c.evaluate(owner,input);assertTrue(c.state.value is IntroPhase.Finished)
            c.evaluate(owner,ready());assertFalse(c.blocking)
        }
    }
    @Test fun `external entry or video consumes opportunity before profile loads`() {
        val c=NotflixIntroCoordinator();c.externalEntry();c.evaluate(owner,ready());assertFalse(c.blocking)
        val d=NotflixIntroCoordinator();d.evaluate(owner,IntroDecisionInput(blocked=true));d.evaluate(owner,ready());assertFalse(d.blocking)
    }
    @Test fun `confirmed Notflix reserves once until original splash is gone`() {
        val c=NotflixIntroCoordinator();c.evaluate(owner,ready().copy(surfaceReady=false))
        val lease=requireNotNull(c.state.value.lease());assertTrue(c.state.value is IntroPhase.Armed);assertTrue(c.blocking)
        repeat(4){c.evaluate(owner,ready().copy(surfaceReady=false))};assertSame(lease,c.state.value.lease())
        c.evaluate(owner,ready());assertTrue(c.state.value is IntroPhase.Running);assertSame(lease,c.state.value.lease())
    }
    @Test fun `foreground and motion policy never start an uncertain intro`() {
        val c=NotflixIntroCoordinator();c.evaluate(owner,ready().copy(foreground=false));assertEquals(IntroPhase.Waiting,c.state.value)
        c.evaluate(owner,ready().copy(motionAllowed=false));assertEquals(IntroPhase.Finished(IntroFinishReason.SYSTEM_MOTION),c.state.value)
    }
    @Test fun `completion skip error and deadline finish and clean only once`() {
        for(reason in listOf(IntroFinishReason.COMPLETE,IntroFinishReason.SKIPPED,IntroFinishReason.ERROR,IntroFinishReason.DEADLINE)) {
            val c=running();var releases=0;c.registerCleanup(requireNotNull(c.state.value.lease())){releases++}
            assertTrue(c.finish(reason));assertFalse(c.finish(IntroFinishReason.ERROR));c.evaluate(owner,ready())
            assertEquals(1,releases);assertEquals(IntroPhase.Finished(reason),c.state.value)
        }
    }
    @Test fun `late handle registration after error is closed immediately`() {
        val c=running();val lease=requireNotNull(c.state.value.lease());c.finish(IntroFinishReason.ERROR)
        var releases=0;c.registerCleanup(lease){releases++};assertEquals(1,releases);assertFalse(c.blocking)
    }
    @Test fun `throwing cleanup still gives destination and no second close`() {
        val c=running();var attempts=0;c.registerCleanup(requireNotNull(c.state.value.lease())){attempts++;throw IOException("synthetic close")}
        assertTrue(c.finish(IntroFinishReason.ERROR));assertTrue(c.cleanupFailed);assertFalse(c.finish(IntroFinishReason.DEADLINE));assertEquals(1,attempts)
    }
    @Test fun `new Activity owner consumes current lease instead of replaying`() {
        val c=running();var releases=0;c.registerCleanup(requireNotNull(c.state.value.lease())){releases++}
        val recreated=Any();c.attachHost(recreated);c.evaluate(recreated,ready());assertEquals(1,releases)
        assertEquals(IntroPhase.Finished(IntroFinishReason.RECREATED),c.state.value)
    }
    @Test fun `profile storage generation and confirmation generation revoke old lease`() {
        for(next in listOf(IntroBinding(AppearanceContext(1,7),11),IntroBinding(AppearanceContext(2,8),11),IntroBinding(AppearanceContext(2,7),12))) {
            val c=running();var releases=0;c.registerCleanup(requireNotNull(c.state.value.lease())){releases++}
            c.evaluate(owner,ready().copy(binding=next));assertEquals(1,releases);assertFalse(c.blocking)
            c.evaluate(owner,ready());assertFalse(c.blocking)
        }
    }
    @Test fun `revocation sound change and scene removal cannot resume intro`() {
        for(input in listOf(ready().copy(resolved=false,binding=null),ready().copy(sound=false),ready().copy(notflix=false))) {
            val c=running();c.evaluate(owner,input);assertFalse(c.blocking);c.evaluate(owner,ready());assertFalse(c.blocking)
        }
        val c=running();c.sceneRemoved(owner);c.evaluate(owner,ready());assertFalse(c.blocking)
    }
    @Test fun `background consumes waiting or active opportunity and closes handle`() {
        val waiting=NotflixIntroCoordinator();waiting.background();waiting.evaluate(owner,ready());assertFalse(waiting.blocking)
        val c=running();var releases=0;c.registerCleanup(requireNotNull(c.state.value.lease())){releases++}
        c.background();c.background();c.evaluate(owner,ready());assertEquals(1,releases);assertFalse(c.blocking)
    }
    @Test fun `skip down held repeats and release never escape into content`() {
        for(code in listOf(4,23,66,160,111)) {
            val c=running();assertTrue(c.key(0,code,0));assertFalse(c.blocking)
            repeat(8){assertTrue(c.key(0,code,it+1))};assertTrue(c.key(1,code,0))
            assertFalse(c.key(0,code,0));assertFalse(c.key(1,code,0))
        }
    }
    @Test fun `profile selection release and repeat do not skip newly mounted intro`() {
        val c=running();assertTrue(c.key(0,23,1));assertTrue(c.key(1,23,0));assertTrue(c.blocking)
        assertTrue(c.key(0,23,0));assertFalse(c.blocking)
    }
    @Test fun `direction held across natural end drains but next fresh press works`() {
        val c=running();assertTrue(c.key(0,22,0));c.finish(IntroFinishReason.COMPLETE)
        assertTrue(c.key(0,22,2));assertTrue(c.key(1,22,0));assertFalse(c.key(0,22,0))
        val d=running();d.key(0,23,0);assertFalse(d.key(0,23,0)) // missing old UP cannot swallow a fresh DOWN
    }
    @Test fun `system Home and volume remain system owned`() {
        val c=running();for(code in listOf(3,24,25,164)){assertFalse(c.key(0,code,0));assertFalse(c.key(1,code,0))};assertTrue(c.blocking)
    }
    @Test fun `audio request is local and fails silent for any system silence`() {
        assertEquals(1f,introRequestedVolume(true,false,false,false))
        for(sound in listOf(false,true)) for(media in listOf(false,true)) for(ringer in listOf(false,true)) for(interruption in listOf(false,true)) {
            if(!sound || media || ringer || interruption) assertEquals(0f,introRequestedVolume(sound,media,ringer,interruption))
        }
    }
}
