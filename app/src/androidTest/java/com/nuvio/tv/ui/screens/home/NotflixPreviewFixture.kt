package com.nuvio.tv.ui.screens.home

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.ui.appearance.*
import com.nuvio.tv.ui.screens.settings.AppearanceSelectionContent
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

internal class PreviewFixtureLifecycle:LifecycleOwner {
    private val registry=LifecycleRegistry.createUnsafe(this).apply{currentState=Lifecycle.State.RESUMED}
    override val lifecycle:Lifecycle get()=registry
    fun pause()=registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun resume()=registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
}
internal class ControlledPreviewResolver:NotflixPreviewResolver {
    val calls=mutableListOf<PreviewLease>();val cancelled=mutableListOf<PreviewLease>()
    var answer:suspend(PreviewLease)->PreviewSource?={PreviewSource("https://fixture.invalid/${it.serial}.mp4")}
    override suspend fun resolve(lease:PreviewLease,authorized:()->Boolean):PreviewSource? {
        check(authorized());calls+=lease;return answer(lease)
    }
    override fun cancel(lease:PreviewLease){cancelled+=lease}
}
/** Colored rectangle, not a video/decoder. Production card, focus and coordinator remain real. */
internal class FakePreviewRenderer:NotflixPreviewRenderer {
    val mounts=mutableListOf<PreviewLease>();var closes=0
    @Composable override fun Content(phase:PreviewPhase.Playing,coordinator:NotflixPreviewCoordinator,modifier:Modifier,startPositionMs:Long) {
        val lease=phase.lease
        Box(modifier.background(Color(0xFF145DA0)).testTag("preview-video-${lease.visit.identity}"))
        DisposableEffect(lease) {
            mounts+=lease;val closed=AtomicBoolean(false)
            val close={if(closed.compareAndSet(false,true))closes++;Unit}
            coordinator.registerCleanup(lease,close)
            onDispose{close()}
        }
        LaunchedEffect(lease){coordinator.firstFrame(lease)}
    }
}
internal fun previewFixtureItem(id:String)=MetaPreview(id,ContentType.MOVIE,name="Synthetic $id",poster=null,
    posterShape=PosterShape.POSTER,background=null,logo=null,description="Declared synthetic metadata",
    releaseInfo="2026",imdbRating=7.1f,genres=listOf("Drama"))
internal fun previewFixtureRow(key:String):NotflixCatalogRow {
    val data=CatalogRow("fixture","Synthetic fixture","https://fixture.invalid",key,"Synthetic row $key",ContentType.MOVIE,
        items=listOf(previewFixtureItem("same"),previewFixtureItem("${key}2"),previewFixtureItem("${key}3"),previewFixtureItem("${key}4"),previewFixtureItem("${key}5")),hasMore=false)
    return NotflixCatalogRow(key,key,"Synthetic row $key",NotflixRowPhase.READY,data)
}
internal class PreviewFixtureState {
    val binding=PreviewBinding(AppearanceContext(2,7),11)
    val resolver=ControlledPreviewResolver();val fake=FakePreviewRenderer();var renderer:NotflixPreviewRenderer=fake
    val lifecycle=PreviewFixtureLifecycle();val memory=NotflixHomeMemory();val navigation=NotflixNavigationState().apply{selectedRoute="home"}
    val rows=listOf(previewFixtureRow("A"),previewFixtureRow("B"))
    var state by mutableStateOf(HomeUiState(notflixPresentationActive=true,notflixCatalog=NotflixCatalogSnapshot(2,1,true,rows),
        isLoading=false,layoutPreferencesReady=true,heroSectionEnabled=false,continueWatchingEnabled=false))
    var environment by mutableStateOf(PreviewEnvironment(binding,true,false,true,false,true,true,false))
    var destination by mutableStateOf<String?>(null)
    var liveBlocked by mutableStateOf(false)
    val details=mutableListOf<String>();val holds=mutableListOf<String>();val navigated=mutableListOf<String>()
    var spanish=false;var settings=false
    val previewChoices=mutableListOf<Boolean>();val soundChoices=mutableListOf<Boolean>()
    var appearance by mutableStateOf(AppearanceState(context=binding.context,requested=AppAppearance.NOTFLIX,
        effective=AppAppearance.NOTFLIX,selectionGeneration=11,introEnabled=true,introSoundEnabled=true,introOptionsValid=true,
        previewEnabled=true,previewSoundEnabled=false,previewOptionsValid=true))
    lateinit var session:NotflixPreviewSession
    lateinit var view:android.view.View
    fun hasSession()=::session.isInitialized
    fun identity(row:Int=0,index:Int=0)="${rows[row].key}|${rows[row].catalog!!.stableItemKeys()[index]}"
    fun tag(row:Int=0,index:Int=0)="notflix-card-${identity(row,index)}"
}

@Composable
internal fun PreviewFixture(s:PreviewFixtureState) {
    s.view=LocalView.current
    val scope=rememberCoroutineScope()
    val session=remember(s){NotflixPreviewSession(s.binding,NotflixPreviewCoordinator(scope,s.resolver),s.renderer,{s.liveBlocked})}
    s.session=session
    val lifecycle by s.lifecycle.lifecycle.currentStateFlow.collectAsState()
    SideEffect{session.environment(s.environment.copy(resumed=s.environment.resumed&&lifecycle.isAtLeast(Lifecycle.State.RESUMED)))}
    // Match the production host's synchronous pause cancellation, rather than
    // relying only on a StateFlow emission that a quick pause/resume can conflate.
    DisposableEffect(session,s.lifecycle){
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_PAUSE || event==Lifecycle.Event.ON_STOP)session.cancel()
        }
        s.lifecycle.lifecycle.addObserver(observer)
        onDispose{s.lifecycle.lifecycle.removeObserver(observer);session.dispose()}
    }
    val base=LocalContext.current
    val context=remember(s.spanish){if(s.spanish)base.createConfigurationContext(Configuration(base.resources.configuration).apply{setLocale(Locale("es"))})else base}
    CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides context.resources.configuration,
        LocalLifecycleOwner provides s.lifecycle,LocalNotflixPresentation provides NotflixPresentation(true,s.navigation,s.environment.reduced)) {
        NotflixPresentationTheme(true) {
            if(s.settings) {
                val focus=remember{FocusRequester()}
                Column(Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState())) {
                    AppearanceSelectionContent(s.appearance,focus,
                        onSelectPreviewEnabled={s.previewChoices+=it;s.appearance=s.appearance.copy(previewEnabled=it)},
                        onSelectPreviewSoundEnabled={s.soundChoices+=it;s.appearance=s.appearance.copy(previewSoundEnabled=it)},onSelect={})
                }
                LaunchedEffect(Unit){focus.requestFocus()}
            } else {
                val focus=remember{FocusRequester()};val held=remember{mutableStateOf(false)}
                val shell=rememberNotflixNavigationShell(s.navigation,if(s.destination==null)"home" else "detail","home",setOf("home","search","library","settings"),"home",
                    listOf("home","search","library","settings").map{NotflixDestination(it,it)},focus,"Fixture",{},
                    {s.navigated+=it},{},held)
                PresentationShellHost(shell,focus) {
                    CompositionLocalProvider(LocalContentFocusRequester provides focus) {
                        if(s.destination==null) NotflixHomeContent(s.state,s.memory,navigation=s.navigation,
                            onDetail={id,_,_->s.details+=id;s.destination=id},onContinue={},onFolder={_,_->},onVisibleRow={},
                            onLongPress={meta,_->s.holds+=meta.id},previewSession=session,onRetry={})
                        else {
                            val back=remember{FocusRequester()}
                            Button(onClick={s.destination=null},modifier=Modifier.focusRequester(back).testTag("preview-fixture-detail-back")){Text("Return synthetic detail")}
                            BackHandler{s.destination=null};LaunchedEffect(Unit){back.requestFocus()}
                        }
                    }
                }
            }
        }
    }
}
