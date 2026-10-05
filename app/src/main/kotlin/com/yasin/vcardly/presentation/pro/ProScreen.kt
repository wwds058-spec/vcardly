package com.yasin.vcardly.presentation.pro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.BuildConfig
import com.yasin.vcardly.R
import com.yasin.vcardly.core.billing.BillingStatus
import com.yasin.vcardly.core.billing.ProProduct
import com.yasin.vcardly.core.billing.ProductKind
import com.yasin.vcardly.core.designsystem.component.IconBadge
import com.yasin.vcardly.core.designsystem.component.VCardlyCard
import com.yasin.vcardly.core.designsystem.component.VCardlyNotice
import com.yasin.vcardly.core.designsystem.component.VCardlyPrimaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlySecondaryButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.CardShape
import com.yasin.vcardly.core.designsystem.theme.Tone
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.domain.entitlement.EntitlementPolicy
import com.yasin.vcardly.domain.entitlement.Feature

data class ProActions(
    val onNavigateUp: () -> Unit = {},
    val onBuy: (ProProduct) -> Unit = {},
    val onRestore: () -> Unit = {},
    val onManage: () -> Unit = {},
)

@Composable
fun ProScreen(onNavigateUp: () -> Unit, viewModel: ProViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ProContent(
        state,
        ProActions(
            onNavigateUp = onNavigateUp,
            onBuy = { p -> context.findActivity()?.let { viewModel.purchase(it, p.id) } },
            onRestore = viewModel::restore,
            onManage = { context.openUrl("https://play.google.com/store/account/subscriptions") },
        ),
    )
}

/**
 * Stateless Pro screen (used directly by UI tests). Every state is truthful: prices only come from Google Play, and when
 * products are not set up or Play is missing the screen says so instead of offering a fake purchase.
 */
@Composable
fun ProContent(state: ProUiState, actions: ProActions) {
    val colors = MaterialTheme.vcColors
    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(title = stringResource(R.string.pro_title), onNavigateUp = actions.onNavigateUp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = MaterialTheme.spacing.screen).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hero(state.isPro)
            VCardlyCard(Modifier.fillMaxWidth(), contentPadding = 8.dp) {
                Feature.entries.forEach { BenefitRow(it.icon, it.tone, it.labelRes()) }
            }
            Text(
                stringResource(R.string.pro_free_note, EntitlementPolicy.FREE_SCANS_PER_MONTH),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.isPro) {
                VCardlySecondaryButton(stringResource(R.string.pro_manage), onClick = actions.onManage, modifier = Modifier.fillMaxWidth())
            } else {
                if (state.purchasePending) VCardlyNotice(stringResource(R.string.pro_pending), colors.orange, Icons.Rounded.HourglassTop)
                when (state.status) {
                    BillingStatus.Connecting -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    BillingStatus.Unavailable -> VCardlyNotice(stringResource(R.string.pro_unavailable), colors.rose, Icons.Rounded.Block)
                    BillingStatus.ProductsNotConfigured -> {
                        VCardlyNotice(stringResource(R.string.pro_not_configured), colors.orange, Icons.Rounded.CloudOff)
                        // Developer-facing hint, only in debug builds.
                        if (BuildConfig.DEBUG) Text(stringResource(R.string.pro_dev_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BillingStatus.Ready -> state.products.sortedBy { if (it.kind == ProductKind.YEARLY) 0 else 1 }.forEach { p ->
                        val label = when (p.kind) {
                            ProductKind.LIFETIME -> stringResource(R.string.pro_buy_lifetime, p.price)
                            ProductKind.YEARLY -> stringResource(R.string.pro_buy_yearly, p.price)
                        }
                        if (p.kind == ProductKind.YEARLY) VCardlyPrimaryButton(label, { actions.onBuy(p) }, Modifier.fillMaxWidth())
                        else VCardlySecondaryButton(label, { actions.onBuy(p) }, Modifier.fillMaxWidth())
                    }
                }
                if (state.launchFailed) VCardlyNotice(stringResource(R.string.pro_launch_failed), colors.rose, Icons.Rounded.Block)
                VCardlyTextButton(stringResource(R.string.pro_restore), onClick = actions.onRestore, enabled = !state.restoring, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.pro_payment_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Hero(isPro: Boolean) {
    val colors = MaterialTheme.vcColors
    Box(Modifier.fillMaxWidth().clip(CardShape).background(Brush.linearGradient(listOf(colors.gradientPurple.first(), colors.gradientBlue.last())))) {
        Box(Modifier.size(180.dp).offset(x = 220.dp, y = (-60).dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(if (isPro) Icons.Rounded.CheckCircle else Icons.Rounded.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Text(
                stringResource(if (isPro) R.string.pro_active_title else R.string.pro_hero_title),
                style = MaterialTheme.typography.headlineSmall, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                stringResource(if (isPro) R.string.pro_active_message else R.string.pro_intro),
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun BenefitRow(icon: ImageVector, tone: Tone, @StringRes text: Int) {
    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tone, size = 40.dp, circle = true)
        Text(stringResource(text), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 14.dp).weight(1f))
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.vcColors.mint.accent)
    }
}

@StringRes
private fun Feature.labelRes(): Int = when (this) {
    Feature.PDF_REPORT -> R.string.pro_feature_pdf
    Feature.EXCEL_EXPORT -> R.string.pro_feature_excel
    Feature.UNLIMITED_SCANS -> R.string.pro_feature_scans
    Feature.AD_FREE -> R.string.pro_feature_no_ads
}

private val Feature.icon: ImageVector
    get() = when (this) {
        Feature.PDF_REPORT -> Icons.Rounded.PictureAsPdf
        Feature.EXCEL_EXPORT -> Icons.Rounded.TableChart
        Feature.UNLIMITED_SCANS -> Icons.Rounded.AllInclusive
        Feature.AD_FREE -> Icons.Rounded.Block
    }

private val Feature.tone: Tone
    @Composable get() = when (this) {
        Feature.PDF_REPORT -> MaterialTheme.vcColors.rose
        Feature.EXCEL_EXPORT -> MaterialTheme.vcColors.mint
        Feature.UNLIMITED_SCANS -> MaterialTheme.vcColors.blue
        Feature.AD_FREE -> MaterialTheme.vcColors.orange
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

