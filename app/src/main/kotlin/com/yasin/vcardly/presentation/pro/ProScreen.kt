package com.yasin.vcardly.presentation.pro

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.BuildConfig
import com.yasin.vcardly.R
import com.yasin.vcardly.core.billing.BillingStatus
import com.yasin.vcardly.core.billing.ProductKind
import com.yasin.vcardly.core.designsystem.component.PrimaryButton
import com.yasin.vcardly.core.designsystem.component.SecondaryButton
import com.yasin.vcardly.core.designsystem.component.SectionHeader
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.domain.entitlement.EntitlementPolicy
import com.yasin.vcardly.domain.entitlement.Feature

@Composable
fun ProScreen(onNavigateUp: () -> Unit, viewModel: ProViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(title = stringResource(R.string.pro_title), onNavigateUp = onNavigateUp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        ) {
            if (state.isPro) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(MaterialTheme.spacing.md)) {
                        Text(stringResource(R.string.pro_active_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.pro_active_message), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                VCardlyTextButton(stringResource(R.string.pro_manage), onClick = { context.openUrl("https://play.google.com/store/account/subscriptions") })
            } else {
                Text(stringResource(R.string.pro_intro), style = MaterialTheme.typography.bodyLarge)
            }

            SectionHeader(stringResource(R.string.pro_includes))
            Feature.entries.forEach { BenefitRow(it.labelRes()) }
            Text(
                stringResource(R.string.pro_free_note, EntitlementPolicy.FREE_SCANS_PER_MONTH),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!state.isPro) {
                if (state.purchasePending) {
                    Text(stringResource(R.string.pro_pending), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyLarge)
                }
                when (state.status) {
                    BillingStatus.Connecting -> CircularProgressIndicator()
                    BillingStatus.Unavailable -> Text(stringResource(R.string.pro_unavailable), color = MaterialTheme.colorScheme.error)
                    BillingStatus.ProductsNotConfigured -> {
                        Text(stringResource(R.string.pro_not_configured), color = MaterialTheme.colorScheme.error)
                        // Developer-facing hint, only in debug builds.
                        if (BuildConfig.DEBUG) Text(stringResource(R.string.pro_dev_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                    BillingStatus.Ready -> state.products.forEach { p ->
                        val label = when (p.kind) {
                            ProductKind.LIFETIME -> stringResource(R.string.pro_buy_lifetime, p.price)
                            ProductKind.YEARLY -> stringResource(R.string.pro_buy_yearly, p.price)
                        }
                        val button: @Composable () -> Unit = {
                            val onClick = { (context.findActivity())?.let { viewModel.purchase(it, p.id) }; Unit }
                            if (p.kind == ProductKind.YEARLY) PrimaryButton(label, onClick, Modifier.fillMaxWidth())
                            else SecondaryButton(label, onClick, Modifier.fillMaxWidth())
                        }
                        button()
                    }
                }
                if (state.launchFailed) Text(stringResource(R.string.pro_launch_failed), color = MaterialTheme.colorScheme.error)
                SecondaryButton(stringResource(R.string.pro_restore), onClick = viewModel::restore, enabled = !state.restoring, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.pro_payment_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BenefitRow(@StringRes text: Int) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = MaterialTheme.spacing.sm))
    }
}

@StringRes
private fun Feature.labelRes(): Int = when (this) {
    Feature.PDF_REPORT -> R.string.pro_feature_pdf
    Feature.EXCEL_EXPORT -> R.string.pro_feature_excel
    Feature.UNLIMITED_SCANS -> R.string.pro_feature_scans
    Feature.AD_FREE -> R.string.pro_feature_no_ads
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: android.content.ActivityNotFoundException) {
        // no browser installed
    }
}
