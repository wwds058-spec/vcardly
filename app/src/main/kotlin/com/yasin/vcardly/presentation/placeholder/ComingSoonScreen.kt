package com.yasin.vcardly.presentation.placeholder

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.EmptyState
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar

/** Honest stand-in for tabs whose feature lands in a later phase. */
@Composable
fun ComingSoonScreen(title: String) {
    Column {
        VCardlyTopBar(title = title)
        EmptyState(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.coming_soon_title),
            message = stringResource(R.string.coming_soon_message),
        )
    }
}
