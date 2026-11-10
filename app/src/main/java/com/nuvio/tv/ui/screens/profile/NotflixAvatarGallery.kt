package com.nuvio.tv.ui.screens.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.ui.appearance.NotflixWordmark

/*
 * M53: photo picker shared by «Añadir perfil» and «Editar perfil».
 * Private build: full-screen gallery of the Xperience catalogue (third-party material, only
 * packaged with -PnotflixPrivateAvatars=true). Public build: QR to that catalogue, no URLs.
 */

/** «Elegir foto» button drawn like the other Notflix profile choices. */
@Composable
internal fun NotflixChoosePhotoChoice(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NotflixChoice(selected = false, onClick = onClick, modifier = modifier) {
        Text(
            text = stringResource(R.string.notflix_avatar_choose_photo),
            color = Spec.Text,
            fontFamily = Spec.BodyFont,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Opens the private gallery when the build packaged the list and the asset exists; otherwise the
 * public QR panel, so a private build without the asset never shows a broken gallery.
 */
@Composable
internal fun NotflixAvatarPicker(
    privateAvatars: List<NotflixAvatar>,
    selectedUrl: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    if (privateAvatars.isEmpty()) {
        NotflixXperienceAvatarPanel(onDismiss = onDismiss)
    } else {
        NotflixAvatarGalleryOverlay(
            avatars = privateAvatars,
            selectedUrl = selectedUrl,
            onDismiss = onDismiss,
            onSelect = onSelect
        )
    }
}

/** Full-screen private gallery: category chips, lazy grid of 96 dp circles and «Sin foto». */
@Composable
internal fun NotflixAvatarGalleryOverlay(
    avatars: List<NotflixAvatar>,
    selectedUrl: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    BackHandler(onBack = onDismiss)
    var category by remember { mutableStateOf<NotflixAvatarCategory?>(null) }
    val categories = remember(avatars) {
        NotflixAvatarCategory.values().filter { candidate -> avatars.any { it.category == candidate } }
    }
    val filtered = remember(avatars, category) {
        if (category == null) avatars else avatars.filter { it.category == category }
    }
    val firstTileFocus = remember { FocusRequester() }
    LaunchedEffect(category) {
        repeat(2) { withFrameNanos { } }
        runCatching { firstTileFocus.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Spec.Background)
            .notflixFocusTrap()
            .testTag("notflix-avatar-gallery")
    ) {
        NotflixWordmark(22.dp, Modifier.padding(start = Spec.GUTTER.dp, top = 20.dp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, top = 68.dp, bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.notflix_avatar_choose_photo),
                    color = Spec.Text,
                    fontFamily = Spec.BodyFont,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                NotflixChoice(
                    selected = false,
                    onClick = { onSelect(null) },
                    modifier = Modifier.testTag("notflix-avatar-no-photo")
                ) {
                    Text(
                        text = stringResource(R.string.notflix_avatar_no_photo),
                        color = Spec.Text,
                        fontFamily = Spec.BodyFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NotflixChoice(
                    selected = category == null,
                    onClick = { category = null },
                    compact = true
                ) {
                    Text(
                        text = stringResource(R.string.notflix_avatar_gallery_all),
                        color = if (category == null) Spec.Background else Spec.Text,
                        fontFamily = Spec.BodyFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                categories.forEach { candidate ->
                    NotflixChoice(
                        selected = category == candidate,
                        onClick = { category = candidate },
                        compact = true
                    ) {
                        Text(
                            text = candidate.label(),
                            color = if (category == candidate) Spec.Background else Spec.Text,
                            fontFamily = Spec.BodyFont,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 118.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 10.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    items = filtered,
                    key = { _, avatar -> avatar.url },
                    contentType = { _, _ -> "notflix-avatar" }
                ) { index, avatar ->
                    NotflixAvatarTile(
                        avatar = avatar,
                        selected = avatar.url == selectedUrl,
                        onConfirm = { onSelect(avatar.url) },
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstTileFocus)
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotflixAvatarTile(
    avatar: NotflixAvatar,
    selected: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onConfirm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .border(
                    3.dp,
                    when {
                        focused -> Spec.Text
                        selected -> Spec.Accent
                        else -> Color.Transparent
                    },
                    CircleShape
                )
                .padding(4.dp)
                .clip(CircleShape)
                .background(Spec.Surface, CircleShape)
        ) {
            AsyncImage(
                model = avatar.url,
                contentDescription = avatar.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        Text(
            text = avatar.name,
            color = if (focused) Spec.Text else Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** Public build: QR to the Xperience gallery; no avatar list or third-party URLs are bundled. */
@Composable
internal fun NotflixXperienceAvatarPanel(onDismiss: () -> Unit) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(2) { withFrameNanos { } }
        runCatching { closeFocus.requestFocus() }
    }
    val qrBitmap = remember {
        runCatching { QrCodeGenerator.generate(NOTFLIX_XPERIENCE_AVATARS_URL, 420, margin = 1) }.getOrNull()
    }
    NotflixProfilePanel(
        title = stringResource(R.string.notflix_avatar_choose_photo),
        onDismiss = onDismiss
    ) {
        Text(
            text = stringResource(R.string.notflix_avatar_xperience_hint),
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(240.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 14.dp)
            )
        }
        NotflixChoice(
            selected = false,
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .focusRequester(closeFocus)
        ) {
            Text(
                text = stringResource(R.string.notflix_avatar_close),
                color = Spec.Text,
                fontFamily = Spec.BodyFont,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun NotflixAvatarCategory.label(): String = stringResource(
    when (this) {
        NotflixAvatarCategory.HBO_MAX -> R.string.notflix_avatar_category_hbo_max
        NotflixAvatarCategory.PARAMOUNT_PLUS -> R.string.notflix_avatar_category_paramount_plus
        NotflixAvatarCategory.DISNEY_PLUS -> R.string.notflix_avatar_category_disney_plus
        NotflixAvatarCategory.NUVIO -> R.string.notflix_avatar_category_nuvio
        NotflixAvatarCategory.FOOTBALL -> R.string.notflix_avatar_category_football
        NotflixAvatarCategory.OTHER -> R.string.notflix_avatar_category_other
    }
)
