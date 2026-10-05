package com.yasin.vcardly.presentation.privacy

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.SectionHeader
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing

private data class PrivacySection(@StringRes val title: Int, @StringRes val body: Int)

/**
 * Plain-language statement of what the app does with data. It must stay true: when a feature changes what is collected or
 * sent (ads, billing, cloud backup), this text is updated in the same change.
 */
private val sections = listOf(
    PrivacySection(R.string.privacy_stays_title, R.string.privacy_stays_body),
    PrivacySection(R.string.privacy_no_account_title, R.string.privacy_no_account_body),
    PrivacySection(R.string.privacy_permissions_title, R.string.privacy_permissions_body),
    PrivacySection(R.string.privacy_ocr_title, R.string.privacy_ocr_body),
    PrivacySection(R.string.privacy_sharing_title, R.string.privacy_sharing_body),
    PrivacySection(R.string.privacy_backup_title, R.string.privacy_backup_body),
    PrivacySection(R.string.privacy_lock_title, R.string.privacy_lock_body),
    PrivacySection(R.string.privacy_control_title, R.string.privacy_control_body),
)

@Composable
fun PrivacyScreen(onNavigateUp: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(title = stringResource(R.string.privacy_title), onNavigateUp = onNavigateUp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
        ) {
            Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyLarge)
            sections.forEach { s ->
                SectionHeader(stringResource(s.title))
                Text(stringResource(s.body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
