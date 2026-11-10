package com.nuvio.tv.ui.appearance.notflix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.ui.screens.ambient.AmbientDialogEffect
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames

/** One row of a right hand panel: `✓` in Accent marks the current choice. */
@Immutable
data class NotflixPanelChoice(
    val key: String,
    val label: String,
    val selected: Boolean = false
)

/**
 * Right hand Notflix panel: 380 dp by default, Surface at 96 %, slides 24 dp with a fade. Back
 * closes it; the caller decides where focus returns.
 */
@Composable
fun NotflixSidePanel(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 380.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val reducedMotion = LocalNotflixPresentation.current.reduceHomeMotion
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AmbientDialogEffect(onDismiss)
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        val offsetPx = with(LocalDensity.current) { 24.dp.roundToPx() }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f))) {
            AnimatedVisibility(
                visible = visible,
                enter = if (reducedMotion) {
                    EnterTransition.None
                } else {
                    fadeIn(tween(Spec.EXPAND_MS)) + slideInHorizontally(tween(Spec.EXPAND_MS)) { offsetPx }
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Column(
                    modifier = modifier
                        .width(width)
                        .fillMaxHeight()
                        .background(Spec.Surface.copy(alpha = .96f))
                        .padding(horizontal = 26.dp, vertical = 22.dp)
                        .focusGroup()
                        .testTag("notflix-side-panel")
                ) {
                    Text(
                        text = title,
                        color = Spec.Text,
                        fontFamily = Spec.BrandFont,
                        fontSize = 28.sp,
                        lineHeight = 32.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Choice list for [NotflixSidePanel]. It owns its focus requesters: the current option (or the
 * first) takes focus as soon as the panel opens.
 */
@Composable
fun NotflixPanelChoices(
    choices: List<NotflixPanelChoice>,
    onSelect: (NotflixPanelChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = remember(choices) { choices.map { it.key } }
    val requesters = remember(keys) { keys.associateWith { FocusRequester() } }
    val initialKey = remember(keys) {
        choices.firstOrNull { it.selected }?.key ?: choices.firstOrNull()?.key
    }
    LaunchedEffect(keys) {
        initialKey?.let { key -> requesters[key]?.requestFocusAfterFrames(frames = 2) }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth().heightIn(max = 460.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(choices, key = { it.key }) { choice ->
            NotflixPanelChoiceRow(
                choice = choice,
                modifier = Modifier.focusRequester(requesters[choice.key] ?: remember(choice.key) { FocusRequester() }),
                onClick = { onSelect(choice) }
            )
        }
    }
}

@Composable
fun NotflixPanelChoiceRow(
    choice: NotflixPanelChoice,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(
                color = if (focused) Spec.Text else Color.Transparent,
                shape = RoundedCornerShape(2.dp)
            )
            .border(
                width = 2.dp,
                color = if (focused) Spec.Text else Color.Transparent,
                shape = RoundedCornerShape(2.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("notflix-panel-choice-${choice.key}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = choice.label,
            color = if (focused) Spec.Background else Spec.Text,
            fontFamily = Spec.BodyFont,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = if (choice.selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (choice.selected) {
            Text(
                text = "✓",
                color = if (focused) Spec.Background else Spec.Accent,
                fontFamily = Spec.BodyFont,
                fontSize = 18.sp
            )
        }
    }
}

/** Group caption inside a panel (Género, Año, Vistos). */
@Composable
fun NotflixPanelGroupTitle(text: String) {
    Text(
        text = text,
        color = Spec.Secondary,
        fontFamily = Spec.BodyFont,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    )
}
