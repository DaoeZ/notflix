package com.nuvio.tv.ui.appearance

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.nuvio.tv.R

/** Notflix wordmark (upright letters, mirrored N). Announced as "NOTFLIX" to accessibility services. */
@Composable
fun NotflixWordmark(height: Dp, modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color? = null) {
    Image(
        painter = painterResource(R.drawable.notflix_wordmark),
        contentDescription = stringResource(R.string.notflix_brand),
        contentScale = ContentScale.FillHeight,
        colorFilter = tint?.let { androidx.compose.ui.graphics.ColorFilter.tint(it) },
        modifier = modifier.height(height)
    )
}
