package com.nuvio.tv.ui.screens.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

@Composable
internal fun NotflixCompanionQrDialog(
    url: String?,
    pairCode: String?,
    onDismiss: () -> Unit
) {
    val closeRequester = remember { FocusRequester() }
    val qrPayload = remember(url, pairCode) {
        if (url.isNullOrBlank()) null
        else if (!pairCode.isNullOrBlank()) "$url?code=$pairCode" else url
    }
    val qrBitmap = remember(qrPayload) {
        qrPayload?.let { runCatching { QrCodeGenerator.generate(it, 360) }.getOrNull() }
    }
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        runCatching { closeRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF141414))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                .padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.companion_title),
                        modifier = Modifier.size(190.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.companion_title),
                    color = Spec.Text,
                    fontFamily = Spec.BrandFont,
                    fontSize = 30.sp
                )
                Text(
                    text = if (url.isNullOrBlank()) {
                        stringResource(R.string.companion_no_wifi)
                    } else {
                        stringResource(R.string.companion_subtitle)
                    },
                    color = Spec.Secondary,
                    fontFamily = Spec.BodyFont,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                if (!url.isNullOrBlank()) {
                    Text(
                        text = url,
                        color = Spec.Text,
                        fontFamily = Spec.BodyFont,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!pairCode.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.companion_pair_code, pairCode),
                        color = Spec.Accent,
                        fontFamily = Spec.BodyFont,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(6.dp))
                var focused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .focusRequester(closeRequester)
                        .onFocusChanged { focused = it.isFocused }
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (focused) Spec.Text else Color.White.copy(alpha = 0.12f))
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(android.R.string.ok),
                        color = if (focused) Spec.Background else Spec.Text,
                        fontFamily = Spec.BodyFont,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
