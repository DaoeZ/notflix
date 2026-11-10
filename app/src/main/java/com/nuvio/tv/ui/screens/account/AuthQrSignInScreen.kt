@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.account

import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.appearance.NotflixWordmark

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.AuthState
import com.nuvio.tv.ui.appearance.NotflixVisualSpec
import com.nuvio.tv.ui.components.BrandWordmark
import com.nuvio.tv.ui.components.SkeletonBar
import com.nuvio.tv.ui.components.rememberShimmerBrush
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private val AuthTextPrimary = Color(0xFFF5F7F8)
private val AuthTextSecondary = Color(0xFF969CA3)
private val AuthPaneBackground = Color.White.copy(alpha = 0.022f)
private val AuthPaneBorder = Color.White.copy(alpha = 0.07f)
private val AuthSecondaryButtonBackground = Color.White.copy(alpha = 0.05f)
private val AuthSecondaryButtonBorder = Color.White.copy(alpha = 0.09f)

/** Estilo visual de la pantalla QR; oficial intacto cuando [notflix] es false. */
internal data class AuthQrSkin(
    val notflix: Boolean,
    val background: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val surface: Color,
    val paneBackground: Color,
    val paneBorder: Color,
    val secondaryButtonBackground: Color,
    val secondaryButtonBorder: Color,
    val bodyFont: FontFamily?
)

internal fun authQrSkin(notflix: Boolean): AuthQrSkin = if (notflix) {
    AuthQrSkin(
        notflix = true,
        background = NotflixVisualSpec.Background,
        textPrimary = NotflixVisualSpec.Text,
        textSecondary = NotflixVisualSpec.Secondary,
        accent = NotflixVisualSpec.Accent,
        surface = NotflixVisualSpec.Surface,
        paneBackground = NotflixVisualSpec.Surface.copy(alpha = 0.65f),
        paneBorder = NotflixVisualSpec.Secondary.copy(alpha = 0.16f),
        secondaryButtonBackground = NotflixVisualSpec.Secondary.copy(alpha = 0.16f),
        secondaryButtonBorder = NotflixVisualSpec.Secondary.copy(alpha = 0.32f),
        bodyFont = NotflixVisualSpec.BodyFont
    )
} else {
    AuthQrSkin(
        notflix = false,
        background = Color.Black,
        textPrimary = AuthTextPrimary,
        textSecondary = AuthTextSecondary,
        accent = Color(0xFF7CFF9B),
        surface = AuthPaneBackground,
        paneBackground = AuthPaneBackground,
        paneBorder = AuthPaneBorder,
        secondaryButtonBackground = AuthSecondaryButtonBackground,
        secondaryButtonBorder = AuthSecondaryButtonBorder,
        bodyFont = null
    )
}

@Composable
fun AuthQrSignInScreen(
    onBackPress: () -> Unit = {},
    onContinue: (() -> Unit)? = null,
    notflixSkin: Boolean = false,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val skin = remember(notflixSkin) { authQrSkin(notflixSkin) }
    val uiState by viewModel.uiState.collectAsState()
    val fullAccount = uiState.authState as? AuthState.FullAccount
    val isSignedIn = fullAccount != null
    val isOnboardingMode = onContinue != null
    val useEmailLogin = viewModel.usesEmailPasswordLogin
    val useQrLogin = !useEmailLogin
    val isApproved = remember(uiState.qrLoginStatus) {
        uiState.qrLoginStatus?.contains("approved", ignoreCase = true) == true
    }
    var onboardingTransitionHandled by remember(isOnboardingMode) { mutableStateOf(false) }
    var exitRequested by remember { mutableStateOf(false) }
    var showSignOutConfirmation by remember { mutableStateOf(false) }
    val loginFocusRequester = remember { FocusRequester() }

    fun leaveAuthScreen() {
        exitRequested = true
        viewModel.clearQrLoginSession()
        onBackPress()
    }

    fun continueFromAuthScreen() {
        exitRequested = true
        if (onContinue != null && !isSignedIn) {
            viewModel.signOut()
        }
        viewModel.clearQrLoginSession()
        if (onContinue != null) {
            onContinue()
        } else {
            onBackPress()
        }
    }

    BackHandler {
        leaveAuthScreen()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearQrLoginSession()
        }
    }

    LaunchedEffect(uiState.authState, isSignedIn, uiState.qrLoginCode, uiState.isLoading, uiState.error, exitRequested) {
        if (
            useQrLogin &&
            !exitRequested &&
            uiState.authState !is AuthState.Loading &&
            !isSignedIn &&
            uiState.qrLoginCode.isNullOrBlank() &&
            uiState.error.isNullOrBlank() &&
            !uiState.isLoading
        ) {
            viewModel.startQrLogin()
        }
    }

    LaunchedEffect(isSignedIn) {
        if (useQrLogin && isSignedIn && !uiState.qrLoginCode.isNullOrBlank()) {
            viewModel.clearQrLoginSession()
        }
    }

    LaunchedEffect(isApproved, uiState.isLoading) {
        if (useQrLogin && isApproved && !uiState.isLoading) {
            viewModel.exchangeQrLogin()
        }
    }

    LaunchedEffect(isOnboardingMode, isSignedIn) {
        if (!isOnboardingMode || onboardingTransitionHandled) return@LaunchedEffect
        if (isSignedIn) {
            onboardingTransitionHandled = true
            exitRequested = true
            viewModel.clearQrLoginSession()
            onContinue.invoke()
        }
    }

    val nowMillis by produceState(initialValue = System.currentTimeMillis(), key1 = uiState.qrLoginCode) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val remainingMillis = uiState.qrLoginExpiresAtMillis?.let { (it - nowMillis).coerceAtLeast(0L) } ?: 0L

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(skin.background)
            .then(if (skin.notflix) Modifier else Modifier.authGradientBackground())
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            AuthQrBrandPanel(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 56.dp, end = 56.dp),
                isSignedIn = isSignedIn,
                fullAccount = fullAccount,
                useEmailLogin = useEmailLogin,
                skin = skin
            )

            AuthQrLoginPane(
                modifier = Modifier
                    .width(460.dp)
                    .fillMaxHeight()
                    .background(skin.paneBackground)
                    .drawBehind {
                        drawLine(
                            color = skin.paneBorder,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    },
                uiState = uiState,
                isSignedIn = isSignedIn,
                isOnboardingMode = isOnboardingMode,
                useEmailLogin = useEmailLogin,
                remainingMillis = remainingMillis,
                onSignIn = viewModel::signIn,
                onRefreshOrSignOut = {
                    if (isSignedIn) {
                        showSignOutConfirmation = true
                    } else {
                        viewModel.startQrLogin()
                    }
                },
                onBackOrContinue = {
                    if (isOnboardingMode) {
                        continueFromAuthScreen()
                    } else {
                        leaveAuthScreen()
                    }
                },
                initialFocusRequester = loginFocusRequester,
                skin = skin
            )
        }

        if (BuildConfig.FEATURE_CUSTOM_SERVER_CONNECTIONS_ENABLED) {
            ServerOptionsMenuHost(
                viewModel = hiltViewModel(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(28.dp)
            )
        }
    }

    LaunchedEffect(Unit) {
        loginFocusRequester.requestFocusAfterFrames(frames = 3)
    }

    if (showSignOutConfirmation) {
        AccountSignOutConfirmationDialog(
            onConfirm = {
                viewModel.signOut()
                showSignOutConfirmation = false
            },
            onDismiss = { showSignOutConfirmation = false }
        )
    }
}

@Composable
internal fun AuthQrBrandPanel(
    modifier: Modifier,
    isSignedIn: Boolean,
    fullAccount: AuthState.FullAccount?,
    useEmailLogin: Boolean,
    skin: AuthQrSkin
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        if (skin.notflix) {
            NotflixWordmark(height = 48.dp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.notflix_auth_qr_tagline),
                modifier = Modifier.widthIn(max = 440.dp),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = skin.textSecondary,
                    fontFamily = skin.bodyFont ?: MaterialTheme.typography.bodyLarge.fontFamily,
                    fontSize = 24.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.Normal
                )
            )
        } else {
            BrandWordmark(
                contentDescription = stringResource(R.string.cd_nuvio),
                modifier = Modifier.height(60.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.auth_qr_tagline),
                modifier = Modifier.widthIn(max = 440.dp),
                style = MaterialTheme.typography.displayLarge.copy(
                    color = skin.textPrimary,
                    fontSize = 40.sp,
                    lineHeight = 45.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
        if (isSignedIn || useEmailLogin) {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = if (isSignedIn) {
                    stringResource(R.string.auth_qr_connected)
                } else {
                    stringResource(R.string.auth_email_hint)
                },
                modifier = Modifier.widthIn(max = 400.dp),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = skin.textSecondary,
                    fontFamily = skin.bodyFont ?: MaterialTheme.typography.bodyLarge.fontFamily,
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Normal
                )
            )
        }
        if (isSignedIn && fullAccount != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = fullAccount.email,
                style = MaterialTheme.typography.titleMedium,
                color = if (skin.notflix) skin.textPrimary else Color(0xFF7CFF9B)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = fullAccount.userId,
                style = MaterialTheme.typography.bodySmall,
                color = skin.textSecondary
            )
        }
    }
}

@Composable
private fun AuthQrLoginPane(
    modifier: Modifier,
    uiState: AccountUiState,
    isSignedIn: Boolean,
    isOnboardingMode: Boolean,
    useEmailLogin: Boolean,
    remainingMillis: Long,
    onSignIn: (String, String) -> Unit,
    onRefreshOrSignOut: () -> Unit,
    onBackOrContinue: () -> Unit,
    initialFocusRequester: FocusRequester,
    skin: AuthQrSkin
) {
    val focusEmail = useEmailLogin && !isSignedIn
    val focusMainAction = !focusEmail && !uiState.isLoading
    val primaryColors = if (skin.notflix) {
        ButtonDefaults.colors(
            containerColor = Color.White,
            focusedContainerColor = Color.White,
            contentColor = Color.Black,
            focusedContentColor = Color.Black,
            disabledContainerColor = Color.White.copy(alpha = 0.38f),
            disabledContentColor = Color.Black.copy(alpha = 0.55f)
        )
    } else {
        ButtonDefaults.colors(
            containerColor = AuthSecondaryButtonBackground,
            focusedContainerColor = Color.White,
            contentColor = AuthTextPrimary,
            focusedContentColor = Color.Black,
            disabledContainerColor = AuthSecondaryButtonBackground.copy(alpha = 0.45f)
        )
    }
    val secondaryColors = if (skin.notflix) {
        ButtonDefaults.colors(
            containerColor = skin.secondaryButtonBackground,
            focusedContainerColor = skin.surface,
            contentColor = skin.textPrimary,
            focusedContentColor = skin.textPrimary
        )
    } else {
        ButtonDefaults.colors(
            containerColor = AuthSecondaryButtonBackground,
            focusedContainerColor = Color.White,
            contentColor = AuthTextPrimary,
            focusedContentColor = Color.Black
        )
    }
    val shape = RoundedCornerShape(if (skin.notflix) 4.dp else 16.dp)
    val primaryBorder = if (skin.notflix) {
        ButtonDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, NotflixVisualSpec.Text),
                shape = shape
            )
        )
    } else {
        ButtonDefaults.border(
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, AuthSecondaryButtonBorder),
                shape = shape
            )
        )
    }
    val secondaryBorder = if (skin.notflix) {
        ButtonDefaults.border(
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, skin.secondaryButtonBorder),
                shape = shape
            ),
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, NotflixVisualSpec.Text),
                shape = shape
            )
        )
    } else {
        primaryBorder
    }
    Column(
        modifier = modifier.padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isSignedIn) {
                stringResource(R.string.auth_qr_synced_data)
            } else if (useEmailLogin) {
                stringResource(R.string.auth_email_instruction)
            } else {
                stringResource(R.string.auth_qr_scan_instruction)
            },
            style = MaterialTheme.typography.bodyLarge.copy(
                color = skin.textSecondary,
                fontFamily = skin.bodyFont ?: MaterialTheme.typography.bodyLarge.fontFamily,
                fontSize = 15.sp,
                lineHeight = 21.sp
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        if (isSignedIn && !isOnboardingMode) {
            AccountConnectedStatsStrip(
                stats = uiState.connectedStats,
                isLoading = uiState.isStatsLoading,
                skin = skin
            )
        } else if (isSignedIn && isOnboardingMode) {
            StatusPill(
                text = stringResource(R.string.auth_qr_finishing),
                containerColor = skin.surface,
                contentColor = skin.textSecondary,
                fontFamily = skin.bodyFont
            )
        } else if (useEmailLogin) {
            AuthEmailLoginForm(
                uiState = uiState,
                onSignIn = onSignIn,
                initialFocusRequester = initialFocusRequester
            )
        } else {
            AuthQrCodeBlock(uiState = uiState, remainingMillis = remainingMillis, skin = skin)
        }

        Spacer(modifier = Modifier.height(28.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSignedIn || !useEmailLogin) {
                Button(
                    onClick = onRefreshOrSignOut,
                    enabled = !uiState.isLoading,
                    modifier = if (focusMainAction) Modifier.focusRequester(initialFocusRequester) else Modifier,
                    colors = primaryColors,
                    border = primaryBorder,
                    shape = if (skin.notflix) ButtonDefaults.shape(RoundedCornerShape(4.dp)) else ButtonDefaults.shape()
                ) {
                    if (skin.notflix) {
                        Text(
                            text = when {
                                isSignedIn -> stringResource(R.string.account_sign_out)
                                uiState.isLoading -> stringResource(R.string.auth_qr_please_wait)
                                else -> stringResource(R.string.auth_qr_refresh)
                            },
                            color = Color.Black,
                            fontFamily = skin.bodyFont,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            when {
                                isSignedIn -> stringResource(R.string.account_sign_out)
                                uiState.isLoading -> stringResource(R.string.auth_qr_please_wait)
                                else -> stringResource(R.string.auth_qr_refresh)
                            }
                        )
                    }
                }
            }
            Button(
                onClick = onBackOrContinue,
                modifier = if (!focusEmail && !focusMainAction) {
                    Modifier.focusRequester(initialFocusRequester)
                } else {
                    Modifier
                },
                colors = secondaryColors,
                border = secondaryBorder,
                shape = if (skin.notflix) ButtonDefaults.shape(RoundedCornerShape(4.dp)) else ButtonDefaults.shape()
            ) {
                val label = if (isOnboardingMode) {
                    if (isSignedIn) stringResource(R.string.auth_qr_continue) else stringResource(R.string.auth_qr_continue_without_account)
                } else {
                    stringResource(R.string.auth_qr_back)
                }
                if (skin.notflix) {
                    Text(
                        text = label,
                        color = skin.textPrimary,
                        fontFamily = skin.bodyFont,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun AuthEmailLoginForm(
    uiState: AccountUiState,
    onSignIn: (String, String) -> Unit,
    initialFocusRequester: FocusRequester
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val canSignIn = email.isNotBlank() && password.isNotBlank() && !uiState.isLoading
    val submit = {
        if (canSignIn) {
            onSignIn(email.trim(), password)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        InputField(
            value = email,
            onValueChange = { email = it },
            placeholder = stringResource(R.string.auth_email_placeholder),
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            modifier = Modifier.focusRequester(initialFocusRequester)
        )
        InputField(
            value = password,
            onValueChange = { password = it },
            placeholder = stringResource(R.string.auth_password_placeholder),
            keyboardType = KeyboardType.Password,
            isPassword = true,
            imeAction = ImeAction.Done,
            onImeAction = submit
        )
        Button(
            onClick = submit,
            enabled = canSignIn,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.colors(
                containerColor = Color.White,
                focusedContainerColor = Color.White,
                contentColor = Color.Black,
                focusedContentColor = Color.Black,
                disabledContainerColor = Color.White.copy(alpha = 0.12f),
                disabledContentColor = AuthTextPrimary.copy(alpha = 0.58f)
            ),
            shape = ButtonDefaults.shape(RoundedCornerShape(16.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (uiState.isLoading) {
                        stringResource(R.string.auth_email_signing_in)
                    } else {
                        stringResource(R.string.auth_email_sign_in)
                    },
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
        AuthTermsAcknowledgement()
        uiState.error?.takeIf { it.isNotBlank() }?.let { error ->
            StatusPill(
                text = error,
                containerColor = Color(0x33C62828),
                contentColor = Color(0xFFFF6E6E)
            )
        }
    }
}

@Composable
private fun AuthQrCodeBlock(
    uiState: AccountUiState,
    remainingMillis: Long,
    skin: AuthQrSkin
) {
    val qrBitmap = uiState.qrLoginBitmap
    if (qrBitmap != null) {
        Image(
            bitmap = qrBitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.cd_qr_login),
            modifier = Modifier
                .size(206.dp)
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(if (skin.notflix) 16.dp else 8.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier = Modifier
                .size(206.dp)
                .background(
                    if (skin.notflix) skin.surface else AuthSecondaryButtonBackground,
                    RoundedCornerShape(8.dp)
                )
                .border(
                    1.dp,
                    if (skin.notflix) skin.secondaryButtonBorder else AuthSecondaryButtonBorder,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (uiState.isLoading) stringResource(R.string.auth_qr_generating) else stringResource(R.string.auth_qr_unavailable),
                color = skin.textSecondary,
                fontFamily = skin.bodyFont,
                textAlign = TextAlign.Center
            )
        }
    }

    AuthQrManualCodeDetails(
        verificationUri = uiState.qrLoginVerificationUri,
        qrLoginCode = uiState.qrLoginUserCode ?: uiState.qrLoginCode,
        expiresAtMillis = uiState.qrLoginExpiresAtMillis,
        remainingMillis = remainingMillis,
        isLoading = uiState.isLoading,
        skin = skin
    )

    Spacer(modifier = Modifier.height(12.dp))
    AuthTermsAcknowledgement(skin = skin)

    val statusText = uiState.error ?: uiState.qrLoginStatus
    if (!statusText.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(14.dp))
        if (uiState.error != null) {
            StatusPill(
                text = statusText,
                containerColor = if (skin.notflix) skin.surface else Color(0x33C62828),
                contentColor = if (skin.notflix) skin.accent else Color(0xFFFF6E6E),
                borderColor = if (skin.notflix) skin.secondaryButtonBorder else null,
                fontFamily = skin.bodyFont
            )
        } else {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = skin.bodyFont ?: MaterialTheme.typography.bodySmall.fontFamily
                ),
                color = skin.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AuthQrManualCodeDetails(
    verificationUri: String?,
    qrLoginCode: String?,
    expiresAtMillis: Long?,
    remainingMillis: Long,
    isLoading: Boolean,
    skin: AuthQrSkin
) {
    val displayUri = verificationUri?.takeIf { it.isNotBlank() }
    val displayCode = qrLoginCode?.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (displayUri != null && displayCode != null) {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.auth_qr_manual_instruction, displayVerificationUri(displayUri)),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = skin.bodyFont ?: MaterialTheme.typography.bodyMedium.fontFamily
                ),
                color = skin.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = formatDeviceLoginCode(displayCode),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = skin.bodyFont ?: MaterialTheme.typography.headlineSmall.fontFamily,
                    fontSize = if (displayCode.length == 6) 24.sp else 14.sp,
                    letterSpacing = if (displayCode.length == 6) 3.sp else 0.sp
                ),
                color = skin.textPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            if (expiresAtMillis != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.auth_qr_expires, formatDuration(remainingMillis)),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = skin.bodyFont ?: MaterialTheme.typography.bodySmall.fontFamily
                    ),
                    color = skin.textSecondary
                )
            }
        } else if (isLoading) {
            val shimmerBrush = rememberShimmerBrush(backdropAware = true)
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                SkeletonBar(width = 238.dp, height = 12.dp, brush = shimmerBrush, cornerRadius = 6.dp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                SkeletonBar(width = 116.dp, height = 22.dp, brush = shimmerBrush, cornerRadius = 8.dp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                contentAlignment = Alignment.Center
            ) {
                SkeletonBar(width = 84.dp, height = 8.dp, brush = shimmerBrush, cornerRadius = 4.dp)
            }
        }
    }
}

private fun formatDeviceLoginCode(value: String): String =
    if (value.length == 6) "${value.take(3)}-${value.drop(3)}" else value

private fun displayVerificationUri(value: String): String = value
    .removePrefix("https://")
    .removePrefix("http://")
    .trimEnd('/')

@Composable
private fun AuthTermsAcknowledgement(skin: AuthQrSkin = authQrSkin(notflix = false)) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.auth_qr_terms_prefix),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = skin.textSecondary,
                fontFamily = skin.bodyFont ?: MaterialTheme.typography.bodyMedium.fontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.auth_qr_terms_link),
            modifier = Modifier.clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://nuvio.tv/terms")))
            },
            style = MaterialTheme.typography.bodyMedium.copy(
                color = skin.textPrimary,
                fontFamily = skin.bodyFont ?: MaterialTheme.typography.bodyMedium.fontFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color? = null,
    fontFamily: FontFamily? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                NuvioTheme.spacing.hairline,
                borderColor ?: NuvioTheme.colors.Border.copy(alpha = 0.35f),
                RoundedCornerShape(NuvioTheme.radii.md)
            )
            .background(containerColor, RoundedCornerShape(NuvioTheme.radii.md))
            .padding(horizontal = NuvioTheme.spacing.md, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = fontFamily ?: MaterialTheme.typography.bodySmall.fontFamily
            ),
            color = contentColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.wrapContentHeight()
        )
    }
}

@Composable
private fun AccountConnectedStatsStrip(
    stats: AccountConnectedStats?,
    isLoading: Boolean,
    skin: AuthQrSkin
) {
    val values = if (isLoading) {
        listOf("...", "...", "...", "...")
    } else {
        listOf(
            (stats?.addons ?: 0).toString(),
            (stats?.plugins ?: 0).toString(),
            (stats?.library ?: 0).toString(),
            (stats?.watchProgress ?: 0).toString()
        )
    }
    val labels = listOf(
        stringResource(R.string.account_stat_addons),
        stringResource(R.string.account_stat_plugins),
        stringResource(R.string.account_stat_library),
        stringResource(R.string.account_stat_progress)
    )
    val valueColor = if (skin.notflix) skin.textPrimary else NuvioTheme.colors.TextPrimary
    val labelColor = if (skin.notflix) skin.textSecondary else NuvioTheme.colors.TextSecondary
    val dividerColor = if (skin.notflix) skin.paneBorder else NuvioTheme.colors.Border.copy(alpha = 0.8f)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NuvioTheme.spacing.hairline)
                .background(dividerColor)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(values.size) { index ->
                AccountStatItem(
                    value = values[index],
                    label = labels[index],
                    valueColor = valueColor,
                    labelColor = labelColor,
                    fontFamily = skin.bodyFont,
                    modifier = Modifier.weight(1f)
                )
                if (index != values.lastIndex) {
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .width(NuvioTheme.spacing.hairline)
                            .background(dividerColor)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NuvioTheme.spacing.hairline)
                .background(dividerColor)
        )
    }
}

@Composable
private fun AccountStatItem(
    value: String,
    label: String,
    valueColor: Color,
    labelColor: Color,
    fontFamily: FontFamily?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = fontFamily ?: MaterialTheme.typography.titleMedium.fontFamily
            ),
            color = valueColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(NuvioTheme.spacing.xxs))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = fontFamily ?: MaterialTheme.typography.bodySmall.fontFamily
            ),
            color = labelColor,
            textAlign = TextAlign.Center
        )
    }
}

private fun Modifier.authGradientBackground(): Modifier = drawWithCache {
    val angleRadians = 122.0 * PI / 180.0
    val directionX = sin(angleRadians).toFloat()
    val directionY = (-cos(angleRadians)).toFloat()
    val halfLength = (abs(size.width * directionX) + abs(size.height * directionY)) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val start = Offset(
        x = center.x - directionX * halfLength,
        y = center.y - directionY * halfLength
    )
    val end = Offset(
        x = center.x + directionX * halfLength,
        y = center.y + directionY * halfLength
    )
    val brush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color(0xFF21113B),
            0.14f to Color(0xFF21113B),
            0.26f to Color(0xFF1A0E2F),
            0.36f to Color(0xFF130A23),
            0.48f to Color(0xFF0A060F),
            0.60f to Color(0xFF050408),
            0.70f to Color.Black,
            1f to Color.Black
        ),
        start = start,
        end = end
    )
    onDrawBehind {
        drawRect(brush = brush)
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
