package com.yasin.vcardly.presentation.lock

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.VCardlyLogo
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.designsystem.theme.Brand
import com.yasin.vcardly.core.security.AuthAvailability
import com.yasin.vcardly.core.security.AuthGate
import com.yasin.vcardly.core.security.AuthResult

/**
 * Opaque cover shown while locked. It swallows all touches, and the screen underneath is removed from the accessibility tree
 * by the caller, so nothing behind it can be read or operated.
 */
@Composable
fun LockScreen(gate: AuthGate, onUnlocked: () -> Unit, onDeviceAuthMissing: () -> Unit) {
    var failed by remember { mutableStateOf(false) }
    val title = stringResource(R.string.lock_prompt_title)

    fun unlock() {
        when (gate.availability()) {
            AuthAvailability.NotSetUp -> onDeviceAuthMissing()
            AuthAvailability.Temporary -> failed = true
            AuthAvailability.Available -> {
                failed = false
                gate.authenticate(title) { result ->
                    when (result) {
                        AuthResult.Success -> onUnlocked()
                        AuthResult.Cancelled, AuthResult.Error -> failed = result == AuthResult.Error
                    }
                }
            }
        }
    }

    // Ask straight away when the lock appears; after a cancel the user taps the button.
    LaunchedEffect(Unit) { unlock() }
    LockContent(failed = failed, onUnlock = { unlock() })
}

/** Stateless lock cover (used directly by UI tests). Always the deep-navy brand look, in both themes. */
@Composable
fun LockContent(failed: Boolean, onUnlock: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "lockPulse")
    val glow by pulse.animateFloat(0.85f, 1.08f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "glow")
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Brand.NavyDeep, Color(0xFF14276B), Color(0xFF1D3A8F))))
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
            .systemBarsPadding()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // White wordmark on navy.
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onSurface = Color.White)) {
            VCardlyLogo(stringResource(R.string.app_name), Modifier.padding(top = 24.dp))
        }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(150.dp).graphicsLayer { scaleX = glow; scaleY = glow }.clip(CircleShape).background(Brand.BlueBright.copy(alpha = 0.16f)))
                Box(Modifier.size(104.dp).clip(CircleShape).background(Brand.BlueBright.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                }
            }
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 32.dp))
            Text(
                stringResource(R.string.lock_message),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            if (failed) {
                Text(
                    stringResource(R.string.lock_error),
                    color = Color(0xFFFFB3C3),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        VCardlyPrimaryButton(
            stringResource(R.string.lock_unlock),
            onClick = onUnlock,
            leadingIcon = Icons.Rounded.Fingerprint,
            containerColor = Color.White,
            contentColor = Brand.Navy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
