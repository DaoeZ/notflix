package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.ui.screens.profile.NotflixChoice
import com.nuvio.tv.ui.screens.profile.NotflixProfilePanel

/**
 * M54: short in-app privacy explanation shown while the public Notflix repository does not exist.
 * The account, profiles and sync stay Nuvio's, so the panel links to their policy; the add-ons the
 * user installs keep their own. Back closes it, focus lands on «Cerrar».
 */
@Composable
internal fun NotflixAboutPrivacyPanel(
    onDismiss: () -> Unit,
    onOpenNuvioPrivacy: () -> Unit
) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(2) { withFrameNanos { } }
        runCatching { closeFocus.requestFocus() }
    }

    NotflixProfilePanel(
        title = stringResource(R.string.about_privacy_policy),
        onDismiss = onDismiss,
        width = 560.dp
    ) {
        Text(
            text = stringResource(R.string.notflix_settings_privacy_body),
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Text(
            text = stringResource(R.string.notflix_settings_privacy_plugins),
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 16.sp,
            lineHeight = 22.sp
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 18.dp)
        ) {
            NotflixChoice(selected = false, onClick = onOpenNuvioPrivacy) {
                Text(
                    text = stringResource(R.string.notflix_settings_privacy_nuvio_link),
                    color = Spec.Text,
                    fontFamily = Spec.BodyFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            NotflixChoice(
                selected = false,
                onClick = onDismiss,
                modifier = Modifier.focusRequester(closeFocus)
            ) {
                Text(
                    text = stringResource(R.string.notflix_settings_privacy_close),
                    color = Spec.Text,
                    fontFamily = Spec.BodyFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
