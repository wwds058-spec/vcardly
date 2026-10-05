package com.yasin.vcardly.presentation.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.PrimaryButton
import com.yasin.vcardly.core.designsystem.theme.spacing
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

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
            .systemBarsPadding()
            .padding(MaterialTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = MaterialTheme.spacing.md))
        Text(
            stringResource(R.string.lock_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MaterialTheme.spacing.sm, bottom = MaterialTheme.spacing.lg),
        )
        if (failed) {
            Text(stringResource(R.string.lock_error), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = MaterialTheme.spacing.md))
        }
        PrimaryButton(stringResource(R.string.lock_unlock), onClick = { unlock() })
    }
}
