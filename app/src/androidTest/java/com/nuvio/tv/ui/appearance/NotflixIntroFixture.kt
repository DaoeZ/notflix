package com.nuvio.tv.ui.appearance

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.ui.screens.settings.AppearanceSelectionContent
import java.util.Locale

internal class IntroFixtureLifecycle : LifecycleOwner {
    private val registry=LifecycleRegistry.createUnsafe(this).apply { currentState=Lifecycle.State.RESUMED }
    override val lifecycle:Lifecycle get()=registry
    fun pause(){registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)}
    fun resume(){registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)}
}
internal class FakeIntroDecoder : IntroDecoderFactory {
    var created=0;var released=0;val sounds=mutableListOf<Boolean>();var earlyError=false
    lateinit var complete:()->Unit;lateinit var error:()->Unit
    override fun create(view:PlayerView,sound:Boolean,complete:()->Unit,error:()->Unit):IntroHandle {
        created++;sounds+=sound;this.complete=complete;this.error=error
        if(earlyError)error()
        return IntroHandle { released++ }
    }
}
/** Synthetic destination, lifecycle, timeline-free fake decoder; no decode, media request or stores. */
internal class IntroFixtureState {
    val coordinator=NotflixIntroCoordinator();val decoder=FakeIntroDecoder();val lifecycle=IntroFixtureLifecycle()
    var owner by mutableStateOf(Any())
    var input by mutableStateOf(IntroDecisionInput(true,IntroBinding(AppearanceContext(2,2),7),true,true,true,true,true))
    var motionAllowed=true;var systemBusy=false;var deadline=5_000L;var spanish=false;var settings=false
    lateinit var view:android.view.View
    var actions=0;val destinationKeys=mutableListOf<Int>();val destinationOwners=mutableSetOf<Any>()
    val introChoices=mutableListOf<Boolean>();val soundChoices=mutableListOf<Boolean>()
    var appearance by mutableStateOf(AppearanceState(context=AppearanceContext(2,2),requested=AppAppearance.NOTFLIX,
        effective=AppAppearance.NOTFLIX,introEnabled=true,introSoundEnabled=true,introOptionsValid=true))
}

@Composable
internal fun IntroFixture(s:IntroFixtureState) {
    s.view=LocalView.current
    val base=LocalContext.current
    val context=remember(s.spanish){if(s.spanish)base.createConfigurationContext(Configuration(base.resources.configuration).apply{setLocale(Locale("es"))})else base}
    CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides context.resources.configuration,
        LocalLifecycleOwner provides s.lifecycle) {
        NotflixIntroLifetime(s.owner,s.coordinator)
        NotflixIntroScene(s.owner,s.input,s.coordinator,s.motionAllowed,s.systemBusy)
        Box(Modifier.fillMaxSize()) {
            NotflixIntroContentBoundary(s.coordinator) {
                val focus=remember{FocusRequester()};val destinationOwner=remember{Any()}
                SideEffect {s.destinationOwners+=destinationOwner}
                if(s.settings) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                    AppearanceSelectionContent(s.appearance,focus,
                        onSelectIntroEnabled={s.introChoices+=it;s.appearance=s.appearance.copy(introEnabled=it)},
                        onSelectIntroSoundEnabled={s.soundChoices+=it;s.appearance=s.appearance.copy(introSoundEnabled=it)},onSelect={})
                    }
                } else Button(onClick={s.actions++},modifier=Modifier.focusRequester(focus).testTag("intro_underlying_action")
                    .onPreviewKeyEvent{ s.destinationKeys+=it.nativeKeyEvent.keyCode;false }) {Text("Continue synthetic destination")}
                LaunchedEffect(Unit){focus.requestFocus()}
            }
            NotflixIntroOverlay(s.owner,s.coordinator,s.decoder,s.deadline)
        }
    }
}
