package com.nuvio.tv.ui.appearance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.core.ui.rememberNotflixConfirmSound
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text

data class NotflixDestination(val route: String, val title: String)

@Composable
fun rememberNotflixNavigationShell(
    navigation: NotflixNavigationState,
    currentRoute: String?,
    selectedRoute: String?,
    rootRoutes: Set<String>,
    homeRoute: String,
    destinations: List<NotflixDestination>,
    contentFocusRequester: FocusRequester,
    profileName: String?,
    onSwitchProfile: () -> Unit,
    onNavigate: (String) -> Unit,
    onExit: () -> Unit,
    longPressBackHeld: MutableState<Boolean>
): PresentationShell {
    val root = currentRoute in rootRoutes
    SideEffect {
        navigation.selectedRoute = selectedRoute
        navigation.topOrder = destinations.map { it.route } + (if (profileName != null) listOf("profile") else emptyList())
    }
    BackHandler(root) {
        if (longPressBackHeld.value) return@BackHandler
        if (navigation.topFocused) onExit() else navigation.focusTop()
    }
    return PresentationShell(
        rootModifier = Modifier.background(NotflixVisualSpec.Background).onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) navigation.interacted = true
            if (event.key == Key.Back && root && event.nativeKeyEvent.isLongPress && event.type == KeyEventType.KeyDown) {
                longPressBackHeld.value = true
                navigation.focusTop()
                true
            } else if (event.key == Key.Back && longPressBackHeld.value) {
                if (event.type == KeyEventType.KeyUp) longPressBackHeld.value = false
                true
            } else false
        },
        contentModifier = (if (root && currentRoute != homeRoute) Modifier.padding(top = NotflixVisualSpec.NAV_HEIGHT.dp) else Modifier)
            .onFocusChanged { if (it.hasFocus) navigation.topFocused = false }
            // M29: only Home registered the content target, so Down from the bar was lost on Search/Library/Settings.
            // Other root screens: the content group is the target and returns to its last focused element.
            .then(if (root && currentRoute != homeRoute) Modifier.focusRequester(contentFocusRequester).focusRestorer() else Modifier)
            .focusGroup(),
        chrome = {
            if (root && !navigation.immersive) {
                Row(
                    Modifier.fillMaxWidth().height(NotflixVisualSpec.NAV_HEIGHT.dp)
                        .background(Brush.verticalGradient(listOf(NotflixVisualSpec.Background.copy(
                            alpha = if (currentRoute == homeRoute && navigation.homeAtTop) .84f else 1f),
                            NotflixVisualSpec.Background.copy(alpha = if (currentRoute == homeRoute && navigation.homeAtTop) .05f else 1f))))
                        .padding(horizontal = NotflixVisualSpec.GUTTER.dp, vertical = 4.dp)
                        .testTag("notflix-navigation"),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NotflixWordmark(height = 22.dp)
                    Spacer(Modifier.width(18.dp))
                    destinations.forEach { item ->
                        key(item.route) {
                            TopDestination(item.route, item.title, item.route == selectedRoute, navigation,
                                contentFocusRequester, currentRoute == homeRoute, { onNavigate(item.route) })
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (profileName != null) {
                        TopDestination("profile", profileName, false, navigation, contentFocusRequester,
                            currentRoute == homeRoute, onSwitchProfile)
                    }
                }
            }
        }
    )
}

@Composable
private fun TopDestination(
    route: String, title: String, active: Boolean, navigation: NotflixNavigationState,
    contentFocusRequester: FocusRequester, isHome: Boolean, onClick: () -> Unit
) {
    val requester = remember { FocusRequester() }
    val confirmSound = rememberNotflixConfirmSound()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(route, requester) {
        navigation.topRequesters[route] = requester
        onDispose { navigation.topRequesters.remove(route) }
    }
    Column(
        Modifier.widthIn(max = if (route == "profile") 120.dp else 150.dp).focusRequester(requester).onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) {
                if (navigation.topFocused || navigation.homeNeedsFocusRepair?.invoke() != true) {
                    navigation.topFocused = true
                    navigation.homeCancelFocus?.invoke()
                }
            }
        }.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                navigation.topFocused = false
                if (isHome) navigation.homeDown?.invoke() else runCatching { contentFocusRequester.requestFocus() }
                true
            } else if (event.type == KeyEventType.KeyDown && event.key in setOf(Key.DirectionLeft, Key.DirectionRight)) {
                navigation.topFocused = true
                navigation.homeCancelFocus?.invoke()
                val index = navigation.topOrder.indexOf(route)
                val direction = if ((event.key == Key.DirectionRight) != rtl) 1 else -1
                navigation.topOrder.getOrNull(index + direction)?.let { navigation.topRequesters[it]?.requestFocus() }
                true
            } else false
        }.border(2.dp, if (focused) NotflixVisualSpec.Text else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable(role = Role.Tab, onClick = {
                confirmSound()
                navigation.topFocused = true
                navigation.homeCancelFocus?.invoke()
                onClick()
            }).padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { selected = active }.testTag("notflix-destination-$route"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = if (active || focused) NotflixVisualSpec.Text else NotflixVisualSpec.Secondary,
            fontFamily = NotflixVisualSpec.BodyFont, fontSize = 15.sp, lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Box(Modifier.width(24.dp).height(2.dp).background(if (active) NotflixVisualSpec.Accent else Color.Transparent))
    }
}
