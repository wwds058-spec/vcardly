package com.yasin.vcardly.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.yasin.vcardly.R

/** Route strings. Top-level tabs are listed in [TopLevelDestination]. */
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

    const val PRO = "pro"
    const val PRIVACY = "privacy"
    const val SEARCH = "search"
    const val BACKUP = "backup"
    const val REPORTS = "reports"
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
    val selectedIcon: ImageVector,
) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Outlined.Home, Icons.Rounded.Home),
    CONTACTS(Routes.CONTACTS, R.string.nav_contacts, Icons.Outlined.People, Icons.Rounded.People),
    FOLLOW_UPS(Routes.FOLLOW_UPS, R.string.nav_follow_ups, Icons.Outlined.EventAvailable, Icons.Rounded.EventAvailable),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
}
