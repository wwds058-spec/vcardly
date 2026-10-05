package com.yasin.vcardly.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.yasin.vcardly.R

/** Route strings. Later phases add detail routes (contact/{id}, scanner, ...) here. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CONTACTS = "contacts"
    const val FOLLOW_UPS = "followups"
    const val SETTINGS = "settings"
    const val ORGANIZE = "organize"

    const val CONTACT_DETAIL = "contact/{contactId}"
    const val CONTACT_EDIT = "contact/edit/{contactId}?fromScan={fromScan}"

    const val FOLLOWUP_EDIT = "followup/edit/{followUpId}?contactId={contactId}"
    /** id = 0 creates a new follow-up; [contactId] optionally pre-selects its contact. */
    fun followUpEdit(id: Long, contactId: Long = 0) = "followup/edit/$id?contactId=$contactId"

    const val MY_CARD = "mycard"
    const val MY_CARD_EDIT = "mycard/edit"
    const val TRANSFER = "transfer"
    const val CONTACT_QR = "qr/{contactId}"
    fun contactQr(id: Long) = "qr/$id"

    const val SCAN_GRAPH = "scan"
    const val SCAN_CAPTURE = "scan/capture"
    const val SCAN_CROP = "scan/crop"
    fun contactDetail(id: Long) = "contact/$id"
    /** id = 0 opens the form for a new contact. */
    fun contactEdit(id: Long, fromScan: Boolean = false) = "contact/edit/$id?fromScan=$fromScan"
}

enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    CONTACTS(Routes.CONTACTS, R.string.nav_contacts, Icons.Filled.Person),
    FOLLOW_UPS(Routes.FOLLOW_UPS, R.string.nav_follow_ups, Icons.Filled.Notifications),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
}
