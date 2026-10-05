package com.yasin.vcardly.presentation.share

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.SkeletonKind
import com.yasin.vcardly.core.designsystem.component.VCardlyLoadingState
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing

/** Share a saved contact: pick fields, show QR, send as a .vcf or save the QR. Private notes are never offered. */
@Composable
fun ContactQrScreen(onNavigateUp: () -> Unit, viewModel: ContactQrViewModel = hiltViewModel()) {
    val card by viewModel.card.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(title = stringResource(R.string.share_title), onNavigateUp = onNavigateUp)
        val c = card
        if (c == null) VCardlyLoadingState(kind = SkeletonKind.DETAIL)
        else QrSharePanel(
            card = c,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = MaterialTheme.spacing.screen, vertical = 8.dp),
        )
    }
}
