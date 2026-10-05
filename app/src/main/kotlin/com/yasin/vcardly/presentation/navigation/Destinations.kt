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
