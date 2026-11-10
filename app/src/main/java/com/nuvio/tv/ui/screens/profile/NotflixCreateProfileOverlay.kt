package com.nuvio.tv.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.core.profile.AgeLevel
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.ui.appearance.NotflixWordmark

/**
 * The full-screen profile overlays are drawn over the profile grid, which stays composed and focusable.
 * Without this the D-pad picks the hidden grid (focus seems to vanish, then lands on Cancel).
 */
internal fun Modifier.notflixFocusTrap(): Modifier =
    focusProperties { onExit = { cancelFocusChange() } }.focusGroup()

internal val NotflixProfileColors = listOf("#E50914", "#1E88E5", "#F5B700", "#2EBD59", "#8E44EF", "#FF7A1A")

/** Kids preset (promoter 08-11): the "all ages" level. */
internal val NOTFLIX_KIDS_AGE_LEVEL = AgeLevel.TP

/**
 * Notflix's own "Add profile" screen: name, colour, a KIDS preset and the same age levels as
 * Settings for teenagers. Notflix profiles share the main profile's add-ons so Home is never empty.
 */
@Composable
internal fun NotflixCreateProfileOverlay(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    privateAvatars: List<NotflixAvatar> = emptyList(),
    onCreate: (name: String, colorHex: String, ageLimit: AgeLevel?, avatarUrl: String?) -> Unit
) {
    BackHandler(onBack = onDismiss)
    var name by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf(NotflixProfileColors.first()) }
    var ageLimit by remember { mutableStateOf<AgeLevel?>(null) }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var showPhotoPicker by remember { mutableStateOf(false) }
    val kids = ageLimit == NOTFLIX_KIDS_AGE_LEVEL
    val photo = avatarUrl
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(2) { withFrameNanos { } }
        runCatching { nameFocus.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(Spec.Background).notflixFocusTrap().testTag("notflix-create-profile")) {
        NotflixWordmark(22.dp, Modifier.padding(start = Spec.GUTTER.dp, top = 20.dp))
        Row(Modifier.align(Alignment.Center).padding(horizontal = 80.dp), horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically) {
            // Live preview of the profile tile.
            Box(Modifier.size(150.dp).background(Color(android.graphics.Color.parseColor(colorHex)), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center) {
                // The tile is the profile colour (red by default), so the accent K would vanish on it.
                when {
                    photo != null -> AsyncImage(
                        model = photo,
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    kids -> KidsLogo(40.sp, kColor = Color.White)
                    else -> Text(name.trim().take(1).uppercase().ifEmpty { "?" }, color = Color.White, fontFamily = Spec.BrandFont, fontSize = 72.sp)
                }
            }
            Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.notflix_profile_add_title), color = Spec.Text, fontFamily = Spec.BodyFont,
                    fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.notflix_profile_add_subtitle), color = Spec.Secondary, fontFamily = Spec.BodyFont, fontSize = 15.sp)
                ProfileNameField(value = name, onValueChange = { name = it.take(24) }, focusRequester = nameFocus, notflixSkin = true)
                // A filled white chip would hide this row's white text, so the preset shows a check instead.
                NotflixKidsToggle(
                    kids = kids,
                    onClick = { ageLimit = if (kids) null else NOTFLIX_KIDS_AGE_LEVEL },
                    modifier = Modifier.testTag("notflix-create-kids")
                )
                Text(stringResource(R.string.notflix_profile_age_label), color = Spec.Secondary, fontFamily = Spec.BodyFont, fontSize = 13.sp)
                NotflixAgeLimitRow(ageLimit = ageLimit, onAgeLimitChange = { ageLimit = it })
                Text(stringResource(R.string.notflix_profile_age_hint), color = Spec.Secondary, fontFamily = Spec.BodyFont, fontSize = 12.sp)
                Text(stringResource(R.string.notflix_profile_color_label), color = Spec.Secondary, fontFamily = Spec.BodyFont, fontSize = 13.sp)
                NotflixProfileColorRow(colorHex = colorHex, onColorChange = { colorHex = it })
                NotflixChoosePhotoChoice(
                    onClick = { showPhotoPicker = true },
                    modifier = Modifier.testTag("notflix-create-photo")
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NotflixChoice(selected = true, enabled = !isCreating, onClick = { onCreate(name, colorHex, ageLimit, avatarUrl) },
                        modifier = Modifier.testTag("notflix-create-save")) {
                        Text(stringResource(if (isCreating) R.string.notflix_profile_creating else R.string.notflix_profile_save),
                            color = Spec.Background, fontFamily = Spec.BodyFont, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    NotflixChoice(selected = false, onClick = onDismiss) {
                        Text(stringResource(R.string.notflix_profile_cancel), color = Spec.Text, fontFamily = Spec.BodyFont,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (showPhotoPicker) {
            NotflixAvatarPicker(
                privateAvatars = privateAvatars,
                selectedUrl = avatarUrl,
                onDismiss = { showPhotoPicker = false },
                onSelect = { picked ->
                    avatarUrl = picked
                    showPhotoPicker = false
                }
            )
        }
    }
}

/** «KIDS» in the brand face with a mirrored K, echoing the mirrored N of the logo. */
@Composable
internal fun KidsLogo(size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier, kColor: Color = Spec.Accent) {
    Row(modifier) {
        Text("K", color = kColor, fontFamily = Spec.BrandFont, fontSize = size, modifier = Modifier.graphicsLayer { scaleX = -1f })
        Text("IDS", color = Spec.Text, fontFamily = Spec.BrandFont, fontSize = size)
    }
}

@Composable
internal fun NotflixChoice(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    Box(modifier
        .onFocusChanged { focused = it.isFocused }
        .border(2.dp, if (focused) Spec.Text else Color.Transparent, shape)
        .padding(3.dp)
        .background(if (selected) Spec.Text else Spec.Surface, shape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 6.dp else 10.dp)) {
        content()
    }
}
