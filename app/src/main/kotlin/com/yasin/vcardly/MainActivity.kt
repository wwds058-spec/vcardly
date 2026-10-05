package com.yasin.vcardly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.core.designsystem.theme.VCardlyTheme
import com.yasin.vcardly.domain.model.ThemeMode
import com.yasin.vcardly.presentation.navigation.AppRoot
import com.yasin.vcardly.core.image.CardImageStore
import com.yasin.vcardly.presentation.common.LocalCardImageStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var cardImageStore: CardImageStore

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition {
            viewModel.themeMode.value == null || viewModel.onboardingCompleted.value == null
        }
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalCardImageStore provides cardImageStore) {
                VCardlyTheme(themeMode = themeMode ?: ThemeMode.SYSTEM) {
                    // Held by the splash screen until loaded. The start destination is captured once, so
                    // finishing onboarding (which flips the flag) does not rebuild the graph.
                    onboardingCompleted?.let { initial ->
                        val startCompleted = remember { initial }
                        AppRoot(onboardingCompleted = startCompleted)
                    }
                }
            }
        }
    }
}
