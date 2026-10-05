package com.yasin.vcardly

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.designsystem.theme.VCardlyTheme
import com.yasin.vcardly.core.image.CardImageStore
import com.yasin.vcardly.core.notifications.ReminderNotifier
import com.yasin.vcardly.core.security.AuthGate
import com.yasin.vcardly.core.security.BiometricGate
import com.yasin.vcardly.domain.model.ThemeMode
import com.yasin.vcardly.presentation.common.LocalAppLocked
import com.yasin.vcardly.presentation.common.LocalAuthGate
import com.yasin.vcardly.presentation.common.LocalCardImageStore
import com.yasin.vcardly.presentation.lock.LockScreen
import com.yasin.vcardly.presentation.navigation.AppRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** FragmentActivity (a ComponentActivity subclass) because BiometricPrompt requires it. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var cardImageStore: CardImageStore

    private lateinit var authGate: AuthGate

    /** Set when the user taps a reminder notification; consumed once by the navigation shell. */
    private var openContactId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        authGate = BiometricGate(this) // must exist before the activity is started
        splash.setKeepOnScreenCondition {
            viewModel.themeMode.value == null || viewModel.onboardingCompleted.value == null || viewModel.security.value == null
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) readOpenContact(intent)
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
            val security by viewModel.security.collectAsStateWithLifecycle()
            val locked by viewModel.locked.collectAsStateWithLifecycle()
            val lockRemoved by viewModel.lockWasRemoved.collectAsStateWithLifecycle()

            val isPro by viewModel.isPro.collectAsStateWithLifecycle()
            val unlockedAndReady = security != null && !(security?.appLockEnabled == true && locked)
            androidx.compose.runtime.LaunchedEffect(unlockedAndReady, isPro) {
                if (unlockedAndReady && !isPro) viewModel.startAdsIfAppropriate(this@MainActivity)
            }

            // Hide the app in the recents list and block screenshots when the user asked for it.
            val secure = security?.secureScreen ?: true
            androidx.compose.runtime.LaunchedEffect(secure) {
                if (secure) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            CompositionLocalProvider(LocalCardImageStore provides cardImageStore, LocalAuthGate provides authGate, LocalAppLocked provides (security?.appLockEnabled == true && locked)) {
                VCardlyTheme(themeMode = themeMode ?: ThemeMode.SYSTEM) {
                    val showLock = security?.appLockEnabled == true && locked
                    Box(Modifier.fillMaxSize()) {
                        // Held by the splash screen until loaded. The start destination is captured once, so
                        // finishing onboarding (which flips the flag) does not rebuild the graph.
                        onboardingCompleted?.let { initial ->
                            val startCompleted = remember { initial }
                            // While locked the app stays composed (so no state is lost) but is invisible to screen readers.
                            Box(if (showLock) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) {
                                AppRoot(
                                    onboardingCompleted = startCompleted,
                                    openContactId = openContactId,
                                    onOpenContactHandled = { openContactId = null },
                                )
                            }
                        }
                        if (showLock) {
                            LockScreen(gate = authGate, onUnlocked = viewModel::onUnlocked, onDeviceAuthMissing = viewModel::onDeviceAuthMissing)
                        }
                    }
                    if (lockRemoved && !showLock) {
                        ConfirmDialog(
                            title = stringResource(R.string.lock_removed_title),
                            message = stringResource(R.string.lock_removed_message),
                            confirmText = stringResource(R.string.common_ok),
                            onConfirm = viewModel::dismissLockRemovedNotice,
                            onDismiss = viewModel::dismissLockRemovedNotice,
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForegrounded()
    }

    override fun onStop() {
        // A rotation also stops the activity; that is not "leaving the app".
        if (!isChangingConfigurations) viewModel.onBackgrounded()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readOpenContact(intent)
    }

    private fun readOpenContact(intent: Intent) {
        val id = intent.getLongExtra(ReminderNotifier.EXTRA_OPEN_CONTACT_ID, 0L)
        if (id > 0L) openContactId = id
        // Consumed: a later rotation/recreation must not re-open the contact.
        intent.removeExtra(ReminderNotifier.EXTRA_OPEN_CONTACT_ID)
    }
}
