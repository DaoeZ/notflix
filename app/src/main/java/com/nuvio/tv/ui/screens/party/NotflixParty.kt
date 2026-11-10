package com.nuvio.tv.ui.screens.party

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nuvio.tv.core.ui.rememberNotflixConfirmSound
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

/**
 * Cabecera de la fiesta con apariencia Notflix: marca en Bebas 56 y una línea de contexto en
 * Secondary. La navegación y el estado siguen en PartyScreen.
 */
@Composable
internal fun NotflixPartyHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            color = Spec.Text,
            fontFamily = Spec.BrandFont,
            fontSize = 56.sp,
            lineHeight = 60.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = Spec.Secondary,
            fontFamily = Spec.BodyFont,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Tecla plana Notflix: 52×52 por defecto, esquinas de 2 dp, foco invertido (fondo Text y texto
 * negro) y borde Accent cuando el botón principal ya está relleno de blanco.
 */
@Composable
internal fun NotflixKeyButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    iconDescription: String? = null,
    primary: Boolean = false,
    testTag: String? = null
) {
    var focused by remember { mutableStateOf(false) }
    val confirmSound = rememberNotflixConfirmSound()
    val shape = RoundedCornerShape(2.dp)
    val inverted = focused || primary
    Box(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else .4f }
            .clip(shape)
            .background(if (inverted) Spec.Text else Spec.Surface)
            .border(
                width = 2.dp,
                color = when {
                    focused && primary -> Spec.Accent
                    focused -> Spec.Text
                    else -> Color.Transparent
                },
                shape = shape
            )
            .focusProperties { canFocus = enabled }
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = { confirmSound(); onClick() })
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val contentColor = if (inverted) Spec.Background else Spec.Text
        when {
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            label != null -> Text(
                text = label,
                color = contentColor,
                fontFamily = Spec.BodyFont,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
