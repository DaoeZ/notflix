@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.annotation.RawRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardColors
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.SettingsUiStyle
import com.nuvio.tv.ui.components.FocusMarqueeText
import com.nuvio.tv.ui.components.BrandWordmark
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixWordmark
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.ui.appearance.notflix.NotflixPanelChoice
import com.nuvio.tv.ui.appearance.notflix.NotflixPanelChoiceRow
import com.nuvio.tv.ui.appearance.notflix.NotflixPanelChoices
import com.nuvio.tv.ui.appearance.notflix.NotflixPanelGroupTitle
import com.nuvio.tv.ui.appearance.notflix.NotflixSidePanel
import com.nuvio.tv.ui.util.contentTextDirection
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioComponents
import com.nuvio.tv.ui.theme.NuvioRadii
import com.nuvio.tv.ui.util.rememberRawSvgPainter

internal val SettingsContainerRadius = NuvioComponents.tokens.settings.containerRadius
internal val SettingsPillRadius = NuvioRadii.tokens.full
internal val SettingsSecondaryCardRadius = NuvioComponents.tokens.settings.secondaryCardRadius
internal val SettingsRailItemHeight = NuvioComponents.tokens.settings.railItemHeight

internal val SettingsZenRowShape = RoundedCornerShape(12.dp)
internal val SettingsHorizonRowShape = RoundedCornerShape(10.dp)
internal val SettingsHorizonGroupShape = RoundedCornerShape(16.dp)

/**
 * Marks a row that holds several options side by side. Without it, moving into the row uses a
 * geometric search and lands on whichever option sits nearest the full width row above, which is
 * the middle one. With it, coming back to the row takes the option last used, and anything else
 * takes [fallbackOption], which callers point at the first option.
 *
 * The fallback has to be named. Left at the default it hands the choice back to the same geometric
 * search, which puts focus in the middle again.
 */
internal fun Modifier.settingsOptionRow(fallbackOption: FocusRequester): Modifier = this
    .focusRestorer(fallbackOption)
    .focusGroup()

@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun isFlatSettingsStyle(): Boolean =
    NuvioTheme.settingsUiStyle != SettingsUiStyle.CLASSIC || notflixSettingsActive()

@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun settingsFocusFillColor(): Color = NuvioTheme.colors.TextPrimary.copy(alpha = 0.1f)

@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun settingsHeaderInset(): Dp = when (NuvioTheme.settingsUiStyle) {
    SettingsUiStyle.CLASSIC -> 0.dp
    SettingsUiStyle.ZEN -> NuvioTheme.spacing.md
    SettingsUiStyle.HORIZON -> NuvioTheme.spacing.xs
}

@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun settingsRowShape(): RoundedCornerShape = when (NuvioTheme.settingsUiStyle) {
    SettingsUiStyle.CLASSIC -> RoundedCornerShape(SettingsPillRadius)
    SettingsUiStyle.ZEN -> SettingsZenRowShape
    SettingsUiStyle.HORIZON -> SettingsHorizonRowShape
}

@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun settingsTallRowShape(): RoundedCornerShape =
    if (isFlatSettingsStyle()) settingsRowShape() else RoundedCornerShape(SettingsSecondaryCardRadius)

@Composable
internal fun settingsRowColors(): CardColors = if (isFlatSettingsStyle()) {
    CardDefaults.colors(
        containerColor = Color.Transparent,
        focusedContainerColor = settingsFocusFillColor()
    )
} else {
    CardDefaults.colors(
        containerColor = NuvioTheme.colors.Background,
        focusedContainerColor = NuvioTheme.colors.Background
    )
}

@Composable
internal fun settingsRowBorder(
    contentAlpha: Float = 1f,
    shape: RoundedCornerShape = settingsRowShape()
): CardBorder = if (isFlatSettingsStyle()) {
    CardDefaults.border(border = Border.None, focusedBorder = Border.None)
} else {
    CardDefaults.border(
        focusedBorder = Border(
            border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs, alpha = contentAlpha),
            shape = shape
        )
    )
}

internal object SettingsTestTags {
    const val ROW_ICON = "settings_row_icon"
}

@Composable
private fun SettingsRowLeadingIcon(
    icon: ImageVector,
    enabled: Boolean,
    focused: Boolean
) {
    val contentAlpha = if (enabled) 1f else 0.4f
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = (if (focused && enabled) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary)
            .copy(alpha = contentAlpha),
        modifier = Modifier
            .size(NuvioTheme.spacing.xl)
            .testTag(SettingsTestTags.ROW_ICON)
    )
}

internal data class SettingsPickerOption<T>(
    val value: T,
    val title: String,
    val description: String? = null,
    val trailing: String? = null,
    val titleFontFamily: FontFamily? = null
)

@Composable
internal fun SettingsStandaloneScaffold(
    title: String,
    subtitle: String,
    classicContainer: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    if (notflixSettingsActive()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NuvioTheme.colors.Background)
                .padding(horizontal = Spec.GUTTER.dp, vertical = NuvioTheme.spacing.xl)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
            ) {
                content()
            }
        }
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = NuvioTheme.spacing.xxl, vertical = NuvioTheme.spacing.xl)
    ) {
        SettingsWorkspaceSurface(
            modifier = Modifier
                .fillMaxSize(),
            classicContainer = classicContainer
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun SettingsBrandPanel(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    showBuiltInHeader: Boolean = true
) {
    if (notflixSettingsActive()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = NotflixSettingsStyle.RowHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            NotflixWordmark(height = 20.dp)
            Text(
                text = title,
                color = if (showBuiltInHeader) Spec.Text else Color.Transparent,
                fontFamily = Spec.BodyFont,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = if (showBuiltInHeader) Spec.Secondary else Color.Transparent,
                fontFamily = Spec.BodyFont,
                fontSize = NotflixSettingsStyle.RowSubtitleSize,
                lineHeight = NotflixSettingsStyle.RowSubtitleLineHeight,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }
    val titleColor = if (showBuiltInHeader) NuvioTheme.colors.TextPrimary else Color.Transparent
    val subtitleColor = if (showBuiltInHeader) NuvioTheme.colors.TextSecondary else Color.Transparent

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(SettingsContainerRadius))
            .background(NuvioTheme.colors.BackgroundElevated)
            .border(
                width = NuvioTheme.spacing.hairline,
                color = NuvioTheme.colors.Border,
                shape = RoundedCornerShape(SettingsContainerRadius)
            )
            .padding(26.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NuvioTheme.colors.BackgroundCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = titleColor
                )
            }
            Spacer(modifier = Modifier.width(NuvioTheme.spacing.md))
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.titleLarge,
                color = titleColor
            )
        }

        Spacer(modifier = Modifier.height(26.dp))

        BrandWordmark(
            contentDescription = stringResource(R.string.cd_nuvio_logo),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(72.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = titleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = subtitleColor,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = stringResource(R.string.settings_rounded_ui),
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.2.sp,
            color = subtitleColor
        )
    }
}

@Composable
internal fun SettingsWorkspaceSurface(
    modifier: Modifier = Modifier,
    classicContainer: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    if (notflixSettingsActive()) {
        // Plain Background: the Notflix settings have no elevated workspace surface at all.
        Box(modifier = modifier, content = content)
        return
    }
    if (isFlatSettingsStyle() || !classicContainer) {
        Box(
            modifier = modifier.padding(horizontal = NuvioTheme.spacing.md, vertical = NuvioTheme.spacing.sm),
            content = content
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(SettingsContainerRadius))
                .background(NuvioTheme.colors.BackgroundElevated)
                .border(
                    width = NuvioTheme.spacing.hairline,
                    color = NuvioTheme.colors.Border,
                    shape = RoundedCornerShape(SettingsContainerRadius)
                )
                .padding(20.dp),
            content = content
        )
    }
}

@Composable
internal fun SettingsRailButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    icon: ImageVector? = null,
    rawIconRes: Int? = null,
    suppressSelectedVisual: Boolean = false
) {
    if (notflixSettingsActive()) {
        NotflixSettingsRailItem(
            title = title,
            isSelected = isSelected,
            onClick = onClick,
            modifier = modifier,
            focusRequester = focusRequester,
            onFocused = onFocused,
            icon = icon,
            rawIconRes = rawIconRes,
            suppressSelectedVisual = suppressSelectedVisual
        )
        return
    }
    var isFocused by remember { mutableStateOf(false) }
    val zen = isFlatSettingsStyle()
    val railShape = if (zen) SettingsZenRowShape else RoundedCornerShape(SettingsPillRadius)
    val appliedModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else {
        modifier
    }
    val showSelected = isSelected && !suppressSelectedVisual

    Card(
        onClick = onClick,
        modifier = appliedModifier
            .padding(top = NuvioTheme.spacing.xxs, bottom = NuvioTheme.spacing.xxs)
            .fillMaxWidth()
            .heightIn(min = SettingsRailItemHeight)
            .onFocusChanged { state ->
                val nowFocused = state.isFocused
                if (isFocused != nowFocused) {
                    isFocused = nowFocused
                    if (nowFocused) onFocused()
                }
            },
        colors = CardDefaults.colors(
            containerColor = when {
                zen -> Color.Transparent
                showSelected -> NuvioTheme.colors.BackgroundCard
                else -> NuvioTheme.colors.Background
            },
            focusedContainerColor = if (zen) settingsFocusFillColor() else NuvioTheme.colors.BackgroundCard
        ),
        border = if (zen) {
            CardDefaults.border(border = Border.None, focusedBorder = Border.None)
        } else {
            CardDefaults.border(
                border = if (showSelected) Border(
                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.hairline),
                    shape = RoundedCornerShape(SettingsPillRadius)
                ) else Border.None,
                focusedBorder = Border(
                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                    shape = RoundedCornerShape(SettingsPillRadius)
                )
            )
        },
        shape = CardDefaults.shape(railShape),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SettingsRailItemHeight)
                .clearAndSetSemantics {
                    contentDescription = title
                    role = Role.Tab
                    this.selected = isSelected
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (zen) 14.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (zen) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(SettingsPillRadius))
                                .background(
                                    if (showSelected) NuvioTheme.colors.Secondary else Color.Transparent
                                )
                        )
                        Spacer(modifier = Modifier.width(NuvioTheme.spacing.md))
                    }
                    if (rawIconRes != null) {
                        Image(
                            painter = rememberRawSvgPainter(rawIconRes),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(
                                if (isSelected || isFocused) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary
                            )
                        )
                    } else if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected || isFocused) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (rawIconRes != null || icon != null) {
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    FocusMarqueeText(
                        text = title,
                        focused = isFocused,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = if (isSelected || isFocused) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
                    )
                }

                if (!zen) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = NuvioTheme.colors.TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotflixSettingsRailItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    icon: ImageVector? = null,
    rawIconRes: Int? = null,
    suppressSelectedVisual: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val appliedModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else {
        modifier
    }
    val showBar = notflixSettingsRailIndicator(isSelected, suppressSelectedVisual)
    val contentColor = when {
        isFocused -> Spec.Background
        isSelected -> Spec.Text
        else -> Spec.Secondary
    }

    Card(
        onClick = onClick,
        modifier = appliedModifier
            .fillMaxWidth()
            .heightIn(min = SettingsRailItemHeight)
            .onFocusChanged { state ->
                val nowFocused = state.isFocused
                if (isFocused != nowFocused) {
                    isFocused = nowFocused
                    if (nowFocused) onFocused()
                }
            },
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Spec.Text
        ),
        border = CardDefaults.border(border = Border.None, focusedBorder = Border.None),
        shape = CardDefaults.shape(RoundedCornerShape(2.dp)),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SettingsRailItemHeight)
                .clearAndSetSemantics {
                    contentDescription = title
                    role = Role.Tab
                    this.selected = isSelected
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(NotflixSettingsStyle.RailBarWidth)
                        .height(NotflixSettingsStyle.RailBarHeight)
                        .background(
                            if (showBar) Spec.Accent else Color.Transparent,
                            RoundedCornerShape(2.dp)
                        )
                )
                Spacer(modifier = Modifier.width(11.dp))
                if (rawIconRes != null) {
                    Image(
                        painter = rememberRawSvgPainter(rawIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(NotflixSettingsStyle.RailIconSize),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(contentColor)
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(NotflixSettingsStyle.RailIconSize)
                    )
                }
                if (rawIconRes != null || icon != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                }
                FocusMarqueeText(
                    text = title,
                    focused = isFocused,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = Spec.BodyFont,
                        fontSize = NotflixSettingsStyle.RailTextSize,
                        lineHeight = NotflixSettingsStyle.RailTextLineHeight,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = contentColor
                )
            }
        }
    }
}

@Composable
internal fun SettingsTopBarTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    icon: ImageVector? = null,
    rawIconRes: Int? = null,
    onFocusedTabPositioned: ((LayoutCoordinates) -> Unit)? = null,
    suppressSelectedVisual: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    var tabCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val glideIndicator = onFocusedTabPositioned != null
    val notflix = notflixSettingsActive()
    val appliedModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else {
        modifier
    }
    val showSelected = isSelected && !suppressSelectedVisual
    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused && notflix -> Spec.Background
            isFocused -> NuvioTheme.colors.OnSecondary
            showSelected && notflix -> Spec.Text
            showSelected -> NuvioTheme.colors.TextPrimary
            notflix -> Spec.Secondary
            else -> NuvioTheme.colors.TextSecondary
        },
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "topBarTabContentColor"
    )

    Card(
        onClick = onClick,
        modifier = appliedModifier
            .onGloballyPositioned { coordinates ->
                tabCoordinates = coordinates
                if (isFocused) onFocusedTabPositioned?.invoke(coordinates)
            }
            .onFocusChanged { state ->
                val nowFocused = state.isFocused
                if (isFocused != nowFocused) {
                    isFocused = nowFocused
                    if (nowFocused) {
                        onFocused()
                        tabCoordinates?.let { onFocusedTabPositioned?.invoke(it) }
                    }
                }
            },
        colors = CardDefaults.colors(
            containerColor = when {
                notflix -> Color.Transparent
                showSelected -> NuvioTheme.colors.Secondary.copy(alpha = 0.18f)
                else -> Color.Transparent
            },
            focusedContainerColor = when {
                notflix -> Spec.Text
                glideIndicator -> Color.Transparent
                else -> NuvioTheme.colors.Secondary
            }
        ),
        border = CardDefaults.border(border = Border.None, focusedBorder = Border.None),
        shape = CardDefaults.shape(RoundedCornerShape(if (notflix) 2.dp else SettingsPillRadius)),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rawIconRes != null) {
                Image(
                    painter = rememberRawSvgPainter(rawIconRes),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(contentColor)
                )
                Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (showSelected || isFocused) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun SettingsDetailHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    if (notflixSettingsActive()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = NotflixSettingsStyle.DetailHeaderGap),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = Spec.Text,
                fontFamily = Spec.BrandFont,
                fontSize = NotflixSettingsStyle.PageTitleSize,
                lineHeight = NotflixSettingsStyle.PageTitleLineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = Spec.Secondary,
                fontFamily = Spec.BodyFont,
                fontSize = NotflixSettingsStyle.SecondarySize,
                lineHeight = NotflixSettingsStyle.SecondaryLineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }
    when (NuvioTheme.settingsUiStyle) {
        SettingsUiStyle.ZEN -> Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = settingsHeaderInset(), bottom = NuvioTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = NuvioTheme.colors.TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextTertiary
            )
        }
        SettingsUiStyle.HORIZON -> Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = settingsHeaderInset(), bottom = NuvioTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
                color = NuvioTheme.colors.TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary
            )
        }
        SettingsUiStyle.CLASSIC -> Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = NuvioTheme.colors.TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary
            )
        }
    }
}

@Composable
internal fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (notflixSettingsActive()) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    color = Spec.Secondary,
                    fontFamily = Spec.BodyFont,
                    fontSize = NotflixSettingsStyle.GroupTitleSize,
                    lineHeight = NotflixSettingsStyle.GroupTitleLineHeight,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = Spec.Secondary,
                    fontFamily = Spec.BodyFont,
                    fontSize = NotflixSettingsStyle.GroupTitleSize,
                    lineHeight = NotflixSettingsStyle.GroupTitleLineHeight,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            content()
        }
        return
    }
    when (NuvioTheme.settingsUiStyle) {
        SettingsUiStyle.ZEN, SettingsUiStyle.HORIZON -> Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = NuvioTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.2.sp,
                    color = NuvioTheme.colors.TextTertiary,
                    modifier = Modifier.padding(start = NuvioTheme.spacing.lg)
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextTertiary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = NuvioTheme.spacing.lg)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SettingsHorizonGroupShape)
                    .background(NuvioTheme.colors.BackgroundCard.copy(alpha = 0.55f))
                    .padding(NuvioTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xxs)
            ) {
                content()
            }
        }
        SettingsUiStyle.CLASSIC -> Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SettingsSecondaryCardRadius))
                .background(NuvioTheme.colors.BackgroundCard)
                .border(
                    width = NuvioTheme.spacing.hairline,
                    color = NuvioTheme.colors.Border,
                    shape = RoundedCornerShape(SettingsSecondaryCardRadius)
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NuvioTheme.colors.TextPrimary
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextSecondary
                )
            }
            content()
        }
    }
}

@Composable
internal fun SettingsToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    enabled: Boolean = true,
    titleTrailingIcon: ImageVector? = null,
    titleTrailingIconTint: Color = NuvioTheme.colors.TextPrimary,
    expandSubtitleOnFocus: Boolean = false
) {
    if (notflixSettingsActive()) {
        NotflixSettingsToggleRowLayout(
            title = title,
            subtitle = subtitle,
            checked = checked,
            onToggle = onToggle,
            modifier = modifier,
            onFocused = onFocused,
            enabled = enabled,
            titleTrailingIcon = titleTrailingIcon,
            titleTrailingIconTint = titleTrailingIconTint,
            expandSubtitleOnFocus = expandSubtitleOnFocus
        )
        return
    }
    val contentAlpha = if (enabled) 1f else 0.4f
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = {
            if (enabled) onToggle()
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .focusProperties { canFocus = enabled }
            .semantics {
                if (!enabled) disabled()
                role = Role.Switch
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            }
            .onFocusChanged { state ->
                val nowFocused = state.isFocused
                if (isFocused != nowFocused) {
                    isFocused = nowFocused
                    if (nowFocused) onFocused()
                }
            },
        colors = settingsRowColors(),
        border = settingsRowBorder(contentAlpha),
        shape = CardDefaults.shape(settingsRowShape()),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = NuvioTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = NuvioTheme.colors.TextPrimary.copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (titleTrailingIcon != null) {
                        Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
                        Icon(
                            imageVector = titleTrailingIcon,
                            contentDescription = null,
                            tint = titleTrailingIconTint.copy(alpha = contentAlpha),
                            modifier = Modifier.size(NuvioTheme.spacing.lg)
                        )
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxs))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioTheme.colors.TextSecondary.copy(alpha = contentAlpha),
                        maxLines = if (expandSubtitleOnFocus && isFocused) 8 else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(NuvioTheme.spacing.md))
            SettingsTogglePill(
                checked = checked,
                enabled = enabled
            )
        }
    }
}

@Composable
internal fun SettingsActionRow(
    title: String,
    subtitle: String?,
    value: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    enabled: Boolean = true,
    trailingIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    titleTrailingIcon: ImageVector? = null,
    titleTrailingIconTint: Color = NuvioTheme.colors.TextPrimary,
    leadingIcon: ImageVector? = null,
    @RawRes leadingRawIconRes: Int? = null,
    leadingArtworkSize: Dp = NuvioTheme.spacing.xl,
    valueColor: Color = NuvioTheme.colors.TextSecondary,
    subtitleModifier: Modifier = Modifier
) {
    if (notflixSettingsActive()) {
        NotflixSettingsActionRowLayout(
            title = title,
            subtitle = subtitle,
            value = value,
            onClick = onClick,
            modifier = modifier,
            onFocused = onFocused,
            enabled = enabled,
            trailingIcon = trailingIcon,
            titleTrailingIcon = titleTrailingIcon,
            titleTrailingIconTint = titleTrailingIconTint,
            leadingIcon = leadingIcon,
            leadingRawIconRes = leadingRawIconRes,
            leadingArtworkSize = leadingArtworkSize,
            valueColor = valueColor,
            subtitleModifier = subtitleModifier
        )
        return
    }
    val contentAlpha = if (enabled) 1f else 0.4f
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .padding(top = NuvioTheme.spacing.xxs, bottom = NuvioTheme.spacing.xxs)
            .fillMaxWidth()
            .focusProperties { canFocus = enabled }
            .semantics {
                if (!enabled) disabled()
                role = Role.Button
            }
            .onFocusChanged { state ->
                val nowFocused = state.isFocused
                if (isFocused != nowFocused) {
                    isFocused = nowFocused
                    if (nowFocused) onFocused()
                }
            },
        colors = settingsRowColors(),
        border = settingsRowBorder(contentAlpha),
        shape = CardDefaults.shape(settingsRowShape()),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .padding(horizontal = 18.dp, vertical = NuvioTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingRawIconRes != null) {
                Image(
                    painter = rememberRawSvgPainter(leadingRawIconRes, leadingArtworkSize),
                    contentDescription = null,
                    modifier = Modifier
                        .size(leadingArtworkSize)
                        .alpha(contentAlpha),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(NuvioTheme.spacing.lg))
            } else if (leadingIcon != null) {
                SettingsRowLeadingIcon(icon = leadingIcon, enabled = enabled, focused = isFocused)
                Spacer(modifier = Modifier.width(NuvioTheme.spacing.lg))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = NuvioTheme.colors.TextPrimary.copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (titleTrailingIcon != null) {
                        Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
                        Icon(
                            imageVector = titleTrailingIcon,
                            contentDescription = null,
                            tint = titleTrailingIconTint.copy(alpha = contentAlpha),
                            modifier = Modifier.size(NuvioTheme.spacing.lg)
                        )
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxs))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioTheme.colors.TextSecondary.copy(alpha = contentAlpha),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = subtitleModifier
                    )
                }
            }

            if (!value.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = valueColor.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 260.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = NuvioTheme.colors.TextTertiary.copy(alpha = contentAlpha),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun NotflixSettingsRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(NotflixSettingsStyle.DividerHeight)
            .background(NotflixSettingsStyle.Divider)
    )
}

/** Title, optional trailing icon and subtitle, shared by the Notflix rows. */
@Composable
private fun NotflixSettingsRowText(
    title: String,
    subtitle: String?,
    focused: Boolean,
    contentAlpha: Float,
    titleTrailingIcon: ImageVector?,
    titleTrailingIconTint: Color,
    subtitleMaxLines: Int,
    subtitleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val titleColor = (if (focused) Spec.Background else Spec.Text).copy(alpha = contentAlpha)
    val subtitleColor = (if (focused) NotflixSettingsStyle.InvertedSecondary else Spec.Secondary)
        .copy(alpha = contentAlpha)
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = titleColor,
                fontFamily = Spec.BodyFont,
                fontSize = NotflixSettingsStyle.RowTitleSize,
                lineHeight = NotflixSettingsStyle.RowTitleLineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (titleTrailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = titleTrailingIcon,
                    contentDescription = null,
                    tint = (if (focused) Spec.Background else titleTrailingIconTint).copy(alpha = contentAlpha),
                    modifier = Modifier.size(NotflixSettingsStyle.RailIconSize)
                )
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = subtitleColor,
                fontFamily = Spec.BodyFont,
                fontSize = NotflixSettingsStyle.RowSubtitleSize,
                lineHeight = NotflixSettingsStyle.RowSubtitleLineHeight,
                maxLines = subtitleMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = subtitleModifier
            )
        }
    }
}

@Composable
private fun NotflixSettingsToggleRowLayout(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier,
    onFocused: () -> Unit,
    enabled: Boolean,
    titleTrailingIcon: ImageVector?,
    titleTrailingIconTint: Color,
    expandSubtitleOnFocus: Boolean
) {
    val contentAlpha = if (enabled) 1f else 0.4f
    var isFocused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            onClick = { if (enabled) onToggle() },
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = NotflixSettingsStyle.RowMinHeight)
                .focusProperties { canFocus = enabled }
                .semantics {
                    if (!enabled) disabled()
                    role = Role.Switch
                    toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                    this[Spec.InvertedRowFocus] = isFocused
                }
                .onFocusChanged { state ->
                    val nowFocused = state.isFocused
                    if (isFocused != nowFocused) {
                        isFocused = nowFocused
                        if (nowFocused) onFocused()
                    }
                },
            colors = CardDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Spec.Text
            ),
            border = CardDefaults.border(border = Border.None, focusedBorder = Border.None),
            shape = CardDefaults.shape(RoundedCornerShape(2.dp)),
            scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = NotflixSettingsStyle.RowHorizontal,
                        vertical = NotflixSettingsStyle.RowVertical
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NotflixSettingsRowText(
                    title = title,
                    subtitle = subtitle,
                    focused = isFocused,
                    contentAlpha = contentAlpha,
                    titleTrailingIcon = titleTrailingIcon,
                    titleTrailingIconTint = titleTrailingIconTint,
                    subtitleMaxLines = if (expandSubtitleOnFocus && isFocused) 8 else 3,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                NotflixSettingsTogglePill(checked = checked, focused = isFocused, enabled = enabled)
            }
        }
        NotflixSettingsRowDivider()
    }
}

@Composable
private fun NotflixSettingsActionRowLayout(
    title: String,
    subtitle: String?,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier,
    onFocused: () -> Unit,
    enabled: Boolean,
    trailingIcon: ImageVector,
    titleTrailingIcon: ImageVector?,
    titleTrailingIconTint: Color,
    leadingIcon: ImageVector?,
    leadingRawIconRes: Int?,
    leadingArtworkSize: Dp,
    valueColor: Color,
    subtitleModifier: Modifier
) {
    val contentAlpha = if (enabled) 1f else 0.4f
    var isFocused by remember { mutableStateOf(false) }
    val drawnValue = notflixSettingsRowValue(value)
    // A caller that names its own value colour (a tracker state, an error) keeps it; the default
    // secondary colour becomes the Notflix token, darkening when the row is inverted.
    val defaultValueColor = NuvioTheme.colors.TextSecondary
    val customValueColor = valueColor != defaultValueColor
    val drawnValueColor = when {
        customValueColor -> valueColor.copy(alpha = contentAlpha)
        isFocused -> Spec.Background.copy(alpha = contentAlpha)
        else -> Spec.Secondary.copy(alpha = contentAlpha)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            onClick = { if (enabled) onClick() },
            modifier = modifier
                .fillMaxWidth()
                .focusProperties { canFocus = enabled }
                .semantics {
                    if (!enabled) disabled()
                    role = Role.Button
                    this[Spec.InvertedRowFocus] = isFocused
                }
                .onFocusChanged { state ->
                    val nowFocused = state.isFocused
                    if (isFocused != nowFocused) {
                        isFocused = nowFocused
                        if (nowFocused) onFocused()
                    }
                },
            colors = CardDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Spec.Text
            ),
            border = CardDefaults.border(border = Border.None, focusedBorder = Border.None),
            shape = CardDefaults.shape(RoundedCornerShape(2.dp)),
            scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = NotflixSettingsStyle.RowMinHeight)
                    .padding(
                        horizontal = NotflixSettingsStyle.RowHorizontal,
                        vertical = NotflixSettingsStyle.RowVertical
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingRawIconRes != null) {
                    Image(
                        painter = rememberRawSvgPainter(leadingRawIconRes, leadingArtworkSize),
                        contentDescription = null,
                        modifier = Modifier
                            .size(leadingArtworkSize)
                            .alpha(contentAlpha),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                } else if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = (if (isFocused) Spec.Background else Spec.Secondary).copy(alpha = contentAlpha),
                        modifier = Modifier.size(NotflixSettingsStyle.RailIconSize)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                }
                NotflixSettingsRowText(
                    title = title,
                    subtitle = subtitle,
                    focused = isFocused,
                    contentAlpha = contentAlpha,
                    titleTrailingIcon = titleTrailingIcon,
                    titleTrailingIconTint = titleTrailingIconTint,
                    subtitleMaxLines = 3,
                    subtitleModifier = subtitleModifier,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                if (drawnValue != null) {
                    Text(
                        text = "$drawnValue ›",
                        color = drawnValueColor,
                        fontFamily = Spec.BodyFont,
                        fontSize = NotflixSettingsStyle.RowValueSize,
                        lineHeight = NotflixSettingsStyle.RowValueLineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 260.dp)
                    )
                } else {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = (if (isFocused) Spec.Background else Spec.Secondary).copy(alpha = contentAlpha),
                        modifier = Modifier.size(NotflixSettingsStyle.RailIconSize)
                    )
                }
            }
        }
        NotflixSettingsRowDivider()
    }
}

/** 40x22 Notflix switch; the knob travels 120 ms and jumps with reduced motion. */
@Composable
private fun NotflixSettingsTogglePill(
    checked: Boolean,
    focused: Boolean,
    enabled: Boolean
) {
    val reducedMotion = LocalNotflixPresentation.current.reduceHomeMotion
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(NotflixSettingsStyle.ToggleMotionMs),
        label = "notflix-settings-toggle"
    )
    val travel = with(LocalDensity.current) {
        (NotflixSettingsStyle.ToggleWidth -
            NotflixSettingsStyle.ToggleKnob -
            NotflixSettingsStyle.DividerHeight * 2).toPx()
    }
    Box(
        modifier = Modifier
            .width(NotflixSettingsStyle.ToggleWidth)
            .height(NotflixSettingsStyle.ToggleHeight)
            .clip(CircleShape)
            .background(notflixSettingsToggleTrack(checked, focused, enabled))
            .border(
                width = NotflixSettingsStyle.DividerHeight,
                color = if (focused) Spec.Background.copy(alpha = 0.25f) else Color.Transparent,
                shape = CircleShape
            )
            .padding(NotflixSettingsStyle.DividerHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset((travel * progress).toInt(), 0) }
                .size(NotflixSettingsStyle.ToggleKnob)
                .clip(CircleShape)
                .background(notflixSettingsToggleKnob(checked, enabled))
        )
    }
}

@Composable
internal fun <T> SettingsSingleChoiceRow(
    title: String,
    value: String,
    options: List<SettingsPickerOption<T>>,
    selectedValue: T,
    onOptionSelected: (T) -> Unit,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    stateKey: Any? = null,
    optionTestTag: ((T) -> String)? = null
) {
    val currentKey = stateKey?.toString().orEmpty()
    // rememberSaveable inputs reset live state, but do not validate a restored value's inputs.
    // Save the binding too so a dialog from another profile cannot reopen after recreation.
    var openedKey by rememberSaveable(currentKey) { mutableStateOf<String?>(null) }
    val open = openedKey == currentKey
    var restoreFocus by remember(stateKey) { mutableStateOf(false) }
    val ownFocus = remember { FocusRequester() }
    val rowFocus = focusRequester ?: ownFocus
    LaunchedEffect(open) {
        if (!open && restoreFocus) {
            rowFocus.requestFocusAfterFrames(frames = 2)
            restoreFocus = false
        }
    }
    SettingsActionRow(
        title = title,
        subtitle = subtitle,
        value = value,
        onClick = { openedKey = currentKey },
        modifier = modifier.focusRequester(rowFocus)
    )
    if (open) {
        SettingsSingleChoiceDialog(
            title = title,
            subtitle = subtitle,
            options = options,
            selectedValue = selectedValue,
            onOptionSelected = {
                openedKey = null
                restoreFocus = true
                onOptionSelected(it)
            },
            onDismiss = {
                openedKey = null
                restoreFocus = true
            },
            optionTestTag = optionTestTag
        )
    }
}

@Composable
internal fun <T> SettingsSingleChoiceDialog(
    title: String,
    options: List<SettingsPickerOption<T>>,
    selectedValue: T,
    onOptionSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    width: Dp = 420.dp,
    maxHeight: Dp = 320.dp,
    optionTestTag: ((T) -> String)? = null
) {
    if (notflixSettingsActive()) {
        val keys = options.mapIndexed { index, option -> "$index-${option.value}" }
        NotflixSidePanel(
            title = title,
            onDismiss = onDismiss,
            width = notflixSettingsPanelWidth()
        ) {
            if (!subtitle.isNullOrBlank()) {
                NotflixPanelGroupTitle(subtitle)
            }
            NotflixPanelChoices(
                choices = options.mapIndexed { index, option ->
                    NotflixPanelChoice(
                        key = keys[index],
                        label = notflixSettingsOptionLabel(option.title, option.trailing, option.description),
                        selected = option.value == selectedValue,
                        testTag = optionTestTag?.invoke(option.value)
                    )
                },
                onSelect = { choice ->
                    val index = keys.indexOf(choice.key)
                    if (index >= 0) onOptionSelected(options[index].value)
                }
            )
        }
        return
    }
    val focusRequester = remember { FocusRequester() }
    val focusedIndex = options.indexOfFirst { it.value == selectedValue }
        .let { if (it >= 0) it else 0 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = focusedIndex)

    LaunchedEffect(focusedIndex) {
        focusRequester.requestFocusAfterFrames()
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = title,
        subtitle = subtitle,
        width = width,
        suppressFirstKeyUp = false
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
        ) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
                contentPadding = PaddingValues(vertical = NuvioTheme.spacing.xs)
            ) {
                itemsIndexed(
                    items = options,
                    key = { index, option -> "$index-${option.value}" }
                ) { index, option ->
                    val isSelected = option.value == selectedValue
                    val testTag = optionTestTag?.invoke(option.value)
                    Card(
                        onClick = { onOptionSelected(option.value) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                            .then(if (index == focusedIndex) Modifier.focusRequester(focusRequester) else Modifier),
                        colors = CardDefaults.colors(
                            containerColor = if (isSelected) NuvioTheme.colors.FocusBackground else NuvioTheme.colors.BackgroundCard,
                            focusedContainerColor = NuvioTheme.colors.FocusBackground
                        ),
                        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                        scale = CardDefaults.scale(focusedScale = 1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(NuvioTheme.spacing.lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    color = if (isSelected) NuvioTheme.colors.Primary else NuvioTheme.colors.TextPrimary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = option.titleFontFamily
                                )
                                if (!option.description.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(NuvioTheme.spacing.xs))
                                    Text(
                                        text = option.description,
                                        color = NuvioTheme.colors.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            if (!option.trailing.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(NuvioTheme.spacing.md))
                                Text(
                                    text = option.trailing,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NuvioTheme.colors.TextSecondary
                                )
                            }
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(NuvioTheme.spacing.md))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.cd_selected),
                                    tint = NuvioTheme.colors.Primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun <T> SettingsMultiChoiceDialog(
    title: String,
    options: List<SettingsPickerOption<T>>,
    selectedValues: List<T>,
    onValuesSelected: (List<T>) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    width: Dp = 520.dp,
    maxHeight: Dp = 420.dp,
    maxSelections: Int? = null
) {
    val focusRequester = remember { FocusRequester() }
    val selected = remember(selectedValues) { mutableStateListOf<T>().also { it.addAll(selectedValues) } }
    if (notflixSettingsActive()) {
        val keys = options.mapIndexed { index, option -> "$index-${option.value}" }
        val requesters = remember(keys) { keys.associateWith { FocusRequester() } }
        val firstSelected = options.indexOfFirst { selected.contains(it.value) }
        val initialKey = keys.getOrNull(if (firstSelected >= 0) firstSelected else 0)
        LaunchedEffect(keys) {
            initialKey?.let { key -> requesters[key]?.requestFocusAfterFrames(frames = 2) }
        }
        NotflixSidePanel(
            title = title,
            onDismiss = onDismiss,
            width = notflixSettingsPanelWidth()
        ) {
            if (!subtitle.isNullOrBlank()) {
                NotflixPanelGroupTitle(subtitle)
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(
                    items = options,
                    key = { index, _ -> keys[index] }
                ) { index, option ->
                    val isSelected = selected.contains(option.value)
                    val requester = requesters[keys[index]]
                    NotflixPanelChoiceRow(
                        choice = NotflixPanelChoice(
                            key = keys[index],
                            label = notflixSettingsOptionLabel(
                                option.title,
                                option.trailing,
                                option.description
                            ),
                            selected = isSelected
                        ),
                        modifier = if (requester != null) {
                            Modifier.focusRequester(requester)
                        } else {
                            Modifier
                        },
                        onClick = {
                            if (isSelected) {
                                selected.remove(option.value)
                            } else if (maxSelections == null || selected.size < maxSelections) {
                                selected.add(option.value)
                            }
                        }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NotflixSettingsPanelButton(
                    text = stringResource(R.string.action_clear),
                    onClick = { selected.clear() }
                )
                NotflixSettingsPanelButton(
                    text = stringResource(R.string.action_save),
                    primary = true,
                    onClick = {
                        onValuesSelected(options.map { it.value }.filter { selected.contains(it) })
                    }
                )
            }
        }
        return
    }
    val firstSelectedIndex = options.indexOfFirst { option -> selectedValues.contains(option.value) }
        .let { if (it >= 0) it else 0 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = firstSelectedIndex)

    LaunchedEffect(firstSelectedIndex) {
        focusRequester.requestFocusAfterFrames()
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = title,
        subtitle = subtitle,
        width = width,
        suppressFirstKeyUp = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
                    contentPadding = PaddingValues(vertical = NuvioTheme.spacing.xs)
                ) {
                    itemsIndexed(
                        items = options,
                        key = { index, option -> "$index-${option.value}" }
                    ) { index, option ->
                        val isSelected = selected.contains(option.value)
                        Card(
                            onClick = {
                                if (isSelected) {
                                    selected.remove(option.value)
                                } else if (maxSelections == null || selected.size < maxSelections) {
                                    selected.add(option.value)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == firstSelectedIndex) Modifier.focusRequester(focusRequester) else Modifier),
                            colors = CardDefaults.colors(
                                containerColor = if (isSelected) NuvioTheme.colors.FocusBackground else NuvioTheme.colors.BackgroundCard,
                                focusedContainerColor = NuvioTheme.colors.FocusBackground
                            ),
                            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                            scale = CardDefaults.scale(focusedScale = 1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(NuvioTheme.spacing.lg),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.title,
                                        color = if (isSelected) NuvioTheme.colors.Primary else NuvioTheme.colors.TextPrimary,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontFamily = option.titleFontFamily
                                    )
                                    if (!option.description.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(NuvioTheme.spacing.xs))
                                        Text(
                                            text = option.description,
                                            color = NuvioTheme.colors.TextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(NuvioTheme.spacing.md))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.cd_selected),
                                        tint = NuvioTheme.colors.Primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SettingsDialogActionRow {
                SettingsDialogActionButton(
                    text = stringResource(R.string.action_clear),
                    onClick = { selected.clear() }
                )
                SettingsDialogActionButton(
                    text = stringResource(R.string.action_save),
                    onClick = { onValuesSelected(options.map { it.value }.filter { selected.contains(it) }) },
                    primary = true
                )
            }
        }
    }
}

@Composable
internal fun SettingsDialogActionRow(
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm, horizontalAlignment),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Notflix action inside a side panel: the primary one is white with black text, the secondary one
 * a translucent grey. Focus inverts the secondary and outlines the primary, so focus never depends
 * on colour alone.
 */
@Composable
private fun NotflixSettingsPanelButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .graphicsLayer { alpha = if (enabled) 1f else .45f }
            .clip(RoundedCornerShape(2.dp))
            .background(
                when {
                    primary -> Spec.Text
                    focused -> Spec.Text
                    else -> Spec.Secondary.copy(alpha = 0.16f)
                }
            )
            .border(
                width = 2.dp,
                color = if (primary && focused) Spec.Accent else Color.Transparent,
                shape = RoundedCornerShape(2.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            color = when {
                primary -> Spec.Background
                focused -> Spec.Background
                else -> Spec.Text
            },
            fontFamily = Spec.BodyFont,
            fontSize = NotflixSettingsStyle.RowValueSize,
            lineHeight = NotflixSettingsStyle.RowValueLineHeight,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun SettingsDialogActionButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    if (notflixSettingsActive()) {
        NotflixSettingsPanelButton(
            text = text,
            onClick = onClick,
            primary = primary,
            enabled = enabled
        )
        return
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.colors(
            containerColor = if (primary) NuvioTheme.colors.FocusBackground else NuvioTheme.colors.BackgroundCard,
            contentColor = NuvioTheme.colors.TextPrimary
        )
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun SettingsChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    if (notflixSettingsActive()) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(3.dp))
                .background(if (selected) Spec.Text else Color.Transparent)
                .border(
                    width = 2.dp,
                    color = when {
                        isFocused && selected -> Spec.Accent
                        isFocused -> Spec.Text
                        else -> Color.Transparent
                    },
                    shape = RoundedCornerShape(3.dp)
                )
                .onFocusChanged { state ->
                    val nowFocused = state.isFocused
                    if (isFocused != nowFocused) {
                        isFocused = nowFocused
                        if (nowFocused) onFocused()
                    }
                }
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                text = label,
                color = if (selected) Spec.Background else Spec.Secondary,
                fontFamily = Spec.BodyFont,
                fontSize = NotflixSettingsStyle.SecondarySize,
                lineHeight = NotflixSettingsStyle.SecondaryLineHeight,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }
    val zen = isFlatSettingsStyle()

    Card(
        onClick = onClick,
        modifier = modifier.onFocusChanged { state ->
            val nowFocused = state.isFocused
            if (isFocused != nowFocused) {
                isFocused = nowFocused
                if (nowFocused) onFocused()
            }
        },
        colors = CardDefaults.colors(
            containerColor = when {
                zen && selected -> NuvioTheme.colors.Secondary.copy(alpha = 0.18f)
                zen -> Color.Transparent
                selected -> NuvioTheme.colors.FocusRing.copy(alpha = 0.2f)
                else -> NuvioTheme.colors.Background
            },
            focusedContainerColor = when {
                zen -> settingsFocusFillColor()
                selected -> NuvioTheme.colors.FocusRing.copy(alpha = 0.2f)
                else -> NuvioTheme.colors.Background
            }
        ),
        border = if (zen) {
            CardDefaults.border(
                border = if (selected) Border(
                    border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Secondary.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(SettingsPillRadius)
                ) else Border.None,
                focusedBorder = if (selected) Border(
                    border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Secondary.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(SettingsPillRadius)
                ) else Border.None
            )
        } else {
            CardDefaults.border(
                border = if (selected) Border(
                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.hairline),
                    shape = RoundedCornerShape(SettingsPillRadius)
                ) else Border.None,
                focusedBorder = Border(
                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.hairline),
                    shape = RoundedCornerShape(SettingsPillRadius)
                )
            )
        },
        shape = CardDefaults.shape(RoundedCornerShape(SettingsPillRadius)),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                textDirection = label.contentTextDirection()
            ),
            color = if (selected || isFocused) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
            modifier = Modifier.padding(horizontal = NuvioTheme.spacing.lg, vertical = 10.dp)
        )
    }
}

@Composable
private fun SettingsTogglePill(
    checked: Boolean,
    enabled: Boolean
) {
    val alpha = if (enabled) 1f else 0.35f
    val zen = isFlatSettingsStyle()
    val trackColor = when {
        zen && checked -> NuvioTheme.colors.Secondary.copy(alpha = 0.9f * alpha)
        checked -> NuvioTheme.colors.Secondary.copy(alpha = 0.35f * alpha)
        else -> NuvioTheme.colors.Border.copy(alpha = alpha)
    }
    val knobColor = if (zen && checked) {
        NuvioTheme.colors.OnSecondary.copy(alpha = alpha)
    } else {
        Color.White.copy(alpha = alpha)
    }
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(NuvioTheme.spacing.xl)
            .clip(RoundedCornerShape(SettingsPillRadius))
            .background(trackColor)
            .padding(NuvioTheme.spacing.xxs),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(knobColor)
        )
    }
}
