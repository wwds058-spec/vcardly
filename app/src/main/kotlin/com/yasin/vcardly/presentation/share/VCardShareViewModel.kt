package com.yasin.vcardly.presentation.share

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.core.share.VCardFileSharer
import com.yasin.vcardly.domain.vcard.ShareCard
import com.yasin.vcardly.domain.vcard.ShareField
import com.yasin.vcardly.domain.vcard.VCardWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class VCardShareViewModel @Inject constructor(
    private val sharer: VCardFileSharer,
) : ViewModel() {
    /** Builds the vCard from the chosen fields only and hands the share intent to [onReady] (null = failed). */
    fun share(card: ShareCard, selected: Set<ShareField>, chooserTitle: String, onReady: (Intent?) -> Unit) {
        viewModelScope.launch {
            onReady(sharer.createShareIntent(VCardWriter.write(card.toVCard(selected)), chooserTitle))
        }
    }
}
