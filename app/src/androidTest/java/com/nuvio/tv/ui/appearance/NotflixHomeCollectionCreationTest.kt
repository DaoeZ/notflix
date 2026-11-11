package com.nuvio.tv.ui.appearance

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Text
import com.nuvio.tv.data.local.CollectionsDataStore
import com.nuvio.tv.data.local.NotflixAppearanceStore
import com.nuvio.tv.domain.model.*
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.ui.screens.settings.AppearanceSelectionContent
import com.nuvio.tv.ui.screens.settings.NotflixHomeCollectionCreationContent
import com.nuvio.tv.ui.theme.NuvioTheme
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*

/** Actual creator VM, authorization VM, production UI and official store in isolated files. No live auth/transport. */
@OptIn(ExperimentalTestApi::class)
class NotflixHomeCollectionCreationTest {
    @get:Rule val compose=createComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private var harness:IsolatedProfileHarness?=null
    private val owner=ViewModelStore()
    private lateinit var collections:CollectionsDataStore
    private lateinit var appearance:AppearanceViewModel
    private lateinit var creator:NotflixHomeCollectionViewModel
    private val auth=MutableStateFlow<AuthState>(AuthState.SignedOut)
    private lateinit var nav:NavHostController
    private var managerBacks=0
    private var language by mutableStateOf("en")

    @After fun close(){
        instrumentation.runOnMainSync{owner.clear()}
        harness?.close()
    }
    private fun show(seed:Collection?=null) {
        val own=IsolatedProfileHarness(instrumentation.targetContext).also{harness=it}
        runBlocking {
            withTimeout(10000){own.manager.profiles.first{it.isNotEmpty()}}
            own.profiles.upsertProfile(UserProfile(2,"Synthetic collection profile","#000000",usesPrimaryAddons=true))
            own.profiles.setActiveProfile(2)
            withTimeout(10000){own.manager.persistedAppearanceProfile.first{it?.profileId==2}}
            withTimeout(10000){own.manager.profiles.first{it.any{p->p.id==2}}}
        }
        collections=CollectionsDataStore(own.context,own.factory,own.manager)
        seed?.let{runBlocking{collections.addCollection(it)}}
        instrumentation.runOnMainSync{
            appearance=AppearanceViewModel(own.manager,NotflixAppearanceStore(own.factory));owner.put("appearance",appearance)
            creator=NotflixHomeCollectionViewModel(collections,own.manager,auth);owner.put("creator",creator)
            assertTrue(appearance.confirmSession(requireNotNull(appearance.beginSessionConfirmation(2))))
        }
        compose.waitUntil(10000){appearance.state.value.context!=null}
        instrumentation.runOnMainSync{appearance.select(AppAppearance.NOTFLIX)}
        compose.waitUntil(10000){appearance.state.value.effective==AppAppearance.NOTFLIX}
        compose.setContent {
            val base=instrumentation.targetContext
            val localized=remember(language){base.createConfigurationContext(Configuration(base.resources.configuration).apply{
                setLocale(Locale.forLanguageTag(language))
            })}
            CompositionLocalProvider(LocalContext provides localized,LocalConfiguration provides localized.resources.configuration){
                NuvioTheme {
                    val ap by appearance.state.collectAsState()
                    val creating by creator.state.collectAsState()
                    val access by creator.access.collectAsState()
                    nav=rememberNavController()
                    NavHost(nav,startDestination="appearance",
                        enterTransition={EnterTransition.None},exitTransition={ExitTransition.None},
                        popEnterTransition={EnterTransition.None},popExitTransition={ExitTransition.None}) {
                        composable("appearance"){entry->
                            val token by entry.savedStateHandle.getStateFlow("return",0).collectAsState()
                            var handled by rememberSaveable{mutableIntStateOf(0)}
                            val entryFocus=remember{FocusRequester()}
                            val restoringAtEntry=remember{token!=handled}
                            // The real Settings parent also requests entry focus after the child restores.
                            // This requester must stay attached to Manage instead of falling back by direction.
                            LaunchedEffect(Unit){
                                if(restoringAtEntry){
                                    repeat(6){withFrameNanos{}}
                                    assertTrue("Parent entry requester must stay attached",entryFocus.requestFocus())
                                }
                            }
                            Column(Modifier.verticalScroll(rememberScrollState())){
                                val texts=notflixHomeCollectionTexts()
                                AppearanceSelectionContent(ap,onSelect=appearance::select,collectionCreator={
                                    NotflixHomeCollectionCreationContent(ap,creating.forAppearance(ap),
                                        homeName=texts.title,
                                        onCreate={creator.create(ap,texts,java.time.Year.now().value,appearance::authorizesCollectionCreation)},
                                        onManage={nav.navigate("inert-manager")},
                                        manageFocusRequester=if(restoringAtEntry)entryFocus else null,
                                        restoreFocusToken=if(token!=handled)token else 0,onFocusRestored={handled=it},access=access)
                                })
                            }
                        }
                        composable("inert-manager"){
                            BackHandler{
                                managerBacks++
                                nav.previousBackStackEntry!!.savedStateHandle["return"]=managerBacks
                                nav.popBackStack()
                            }
                            Text("Synthetic manager destination; no mutation")
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }
    private fun focus(tag:String){
        compose.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle();compose.onNodeWithTag(tag).assertIsFocused()
    }
    private fun activate(tag:String){
        focus(tag);compose.onNodeWithTag(tag).performKeyInput{keyDown(Key.DirectionCenter);keyUp(Key.DirectionCenter)}
        compose.waitForIdle()
    }
    private fun created(){compose.waitUntil(10000){creator.state.value.result==NotflixCollectionResult.CREATED_LOCAL};compose.waitForIdle()}
    private fun all()=runBlocking{collections.getCurrentCollections()}

    @Test fun createAndRepeatUseActualStoreAndKeepFocusedButtonWithoutDuplication(){
        show();activate("notflix-create-home");created()
        assertEquals(listOf(notflixCollectionTestPreset()),all())
        compose.onNodeWithTag("notflix-create-home").assertIsFocused()
        activate("notflix-create-home")
        compose.waitUntil(10000){creator.state.value.result==NotflixCollectionResult.EXISTS}
        assertEquals(listOf(notflixCollectionTestPreset()),all())
        compose.onNodeWithTag("notflix-collection-result",useUnmergedTree=true).assertTextContains("already exists",substring=true)
    }
    @Test fun managerBackRestoresProductiveManageControlWithoutDoubleNavigation(){
        show();activate("notflix-manage-collections")
        compose.runOnIdle{assertEquals("inert-manager",nav.currentDestination?.route)}
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);compose.waitForIdle()
        compose.onNodeWithTag("notflix-manage-collections").assertIsFocused().assertIsDisplayed()
        compose.runOnIdle{assertEquals(1,managerBacks);assertEquals("appearance",nav.currentDestination?.route)}
        assertTrue(all().isEmpty())
    }
    @Test fun nuvioHidesCreatorAndPreservesOfficialCollectionData(){
        show();activate("notflix-create-home");created()
        instrumentation.runOnMainSync{appearance.select(AppAppearance.NUVIO)}
        compose.waitUntil(10000){appearance.state.value.effective==AppAppearance.NUVIO};compose.waitForIdle()
        compose.onNodeWithTag("notflix-create-home").assertDoesNotExist()
        compose.onNodeWithTag("notflix-manage-collections").assertDoesNotExist()
        val stored=all().single();assertEquals(11,stored.folders.size);assertEquals(FolderViewMode.ROWS,stored.viewMode)
    }
    @Test fun spanishLabelsAndCollisionPreserveExistingEditsWithoutGlobalLocaleChange(){
        val original=instrumentation.targetContext.resources.configuration.locales.toLanguageTags()
        val edited=notflixCollectionTestPreset().copy(pinToTop=false,folders=notflixCollectionTestPreset().folders.reversed())
        show(edited);compose.runOnIdle{language="es"};compose.waitForIdle()
        compose.onNodeWithTag("notflix-create-home").assertTextContains("Crear Inicio Notflix",substring=true)
        compose.onNodeWithTag("notflix-manage-collections").assertTextContains("Gestionar colecciones",substring=true)
        activate("notflix-create-home")
        compose.waitUntil(10000){creator.state.value.result==NotflixCollectionResult.EXISTS};compose.waitForIdle()
        compose.onNodeWithTag("notflix-collection-result",useUnmergedTree=true).assertTextContains("Se han conservado sus cambios.",substring=true)
        assertEquals(listOf(edited),all())
        assertEquals(original,instrumentation.targetContext.resources.configuration.locales.toLanguageTags())
    }
    @Test fun authenticatedRequestShowsPendingAndDoesNotWriteOrPretendToSync(){
        show();auth.value=AuthState.Loading
        compose.waitUntil(10000){creator.access.value==NotflixCollectionAccess.CHECKING};compose.waitForIdle()
        compose.onNodeWithTag("notflix-create-home").assertIsNotEnabled()
        auth.value=AuthState.FullAccount("fixture","fixture@example.invalid")
        compose.waitUntil(10000){creator.access.value==NotflixCollectionAccess.ACCOUNT_SYNC_PENDING};compose.waitForIdle()
        compose.onNodeWithTag("notflix-create-home").assertIsNotEnabled()
        instrumentation.runOnMainSync{creator.create(appearance.state.value,notflixCollectionTestTexts,java.time.Year.now().value,appearance::authorizesCollectionCreation)}
        compose.waitUntil(10000){creator.state.value.result==NotflixCollectionResult.ACCOUNT_SYNC_PENDING};compose.waitForIdle()
        assertTrue(all().isEmpty())
        compose.onNodeWithTag("notflix-collection-result",useUnmergedTree=true).assertTextContains("No collection has been changed.",substring=true)
    }
    @Test fun withdrawnAuthorizationRejectsAnAlreadyCapturedClick(){
        show();focus("notflix-create-home")
        val click=compose.onNodeWithTag("notflix-create-home").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        instrumentation.runOnMainSync{appearance.cancelSessionConfirmation();click()}
        compose.waitForIdle();assertTrue(all().isEmpty())
        assertNull(creator.state.value.result)
    }
}
