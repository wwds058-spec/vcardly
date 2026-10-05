package com.yasin.vcardly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.core.designsystem.component.EmptyState
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.VCardlyTheme
import com.yasin.vcardly.domain.model.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.themeMode.value == null }
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            VCardlyTheme(themeMode = themeMode ?: ThemeMode.SYSTEM) {
                FoundationScreen()
            }
        }
    }
}

/** Phase 1 placeholder: proves theme, DI and strings are wired. Replaced by onboarding/home in Phase 2. */
@Composable
private fun FoundationScreen() {
    Scaffold(topBar = { VCardlyTopBar(title = stringResource(R.string.app_name)) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            EmptyState(
                icon = Icons.Filled.Person,
                title = stringResource(R.string.foundation_title),
                message = stringResource(R.string.foundation_message),
            )
        }
    }
}
