package com.yasin.vcardly.presentation.privacy

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.NoAccounts
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.BuildConfig
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.IconBadge
import com.yasin.vcardly.core.designsystem.component.VCardlyCard
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.Tone
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors

private data class PrivacySection(val icon: ImageVector, @StringRes val title: Int, @StringRes val body: Int)

/**
 * Plain-language statement of what the app does with data. It must stay true: when a feature changes what is collected or
 * sent (ads, billing, cloud backup), this text is updated in the same change.
 */
// A build without ads and without Pro for sale (the first Play release) says exactly that instead of describing ads and
// purchases it does not have.
private val monetised = BuildConfig.ADS_ENABLED || BuildConfig.PRO_FOR_SALE

private val sections = listOfNotNull(
    PrivacySection(Icons.Rounded.PhoneAndroid, R.string.privacy_stays_title, R.string.privacy_stays_body),
    PrivacySection(
        Icons.Rounded.NoAccounts, R.string.privacy_no_account_title,
        if (monetised) R.string.privacy_no_account_body else R.string.privacy_no_account_body_basic,
    ),
    PrivacySection(
        Icons.Rounded.VerifiedUser, R.string.privacy_permissions_title,
        if (monetised) R.string.privacy_permissions_body else R.string.privacy_permissions_body_basic,
    ),
    if (monetised) PrivacySection(Icons.Rounded.Campaign, R.string.privacy_ads_title, R.string.privacy_ads_body) else null,
    PrivacySection(Icons.Rounded.DocumentScanner, R.string.privacy_ocr_title, R.string.privacy_ocr_body),
    PrivacySection(Icons.Rounded.Share, R.string.privacy_sharing_title, R.string.privacy_sharing_body),
    PrivacySection(Icons.Rounded.Backup, R.string.privacy_backup_title, R.string.privacy_backup_body),
    PrivacySection(Icons.Rounded.Fingerprint, R.string.privacy_lock_title, R.string.privacy_lock_body),
    PrivacySection(Icons.Rounded.Tune, R.string.privacy_control_title, R.string.privacy_control_body),
)

@Composable
fun PrivacyScreen(onNavigateUp: () -> Unit) {
    val colors = MaterialTheme.vcColors
    val tones: List<Tone> = listOf(colors.blue, colors.lavender, colors.mint, colors.orange, colors.blue, colors.rose, colors.mint, colors.navy, colors.lavender)
    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(title = stringResource(R.string.privacy_title), onNavigateUp = onNavigateUp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = MaterialTheme.spacing.screen).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VCardlyCard(Modifier.fillMaxWidth(), color = colors.blue.container) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Shield, colors.blue, size = 48.dp, iconSize = 24.dp, circle = true, solid = true)
                    Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 14.dp))
                }
            }
            sections.forEachIndexed { i, s ->
                VCardlyCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        IconBadge(s.icon, tones[i % tones.size], size = 40.dp, circle = true)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(stringResource(s.title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                            Text(stringResource(s.body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
