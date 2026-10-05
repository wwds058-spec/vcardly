package com.yasin.vcardly.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.yasin.vcardly.presentation.contacts.ContactDetailScreen
import com.yasin.vcardly.presentation.contacts.ContactEditScreen
import com.yasin.vcardly.presentation.contacts.ContactsScreen
import com.yasin.vcardly.presentation.dashboard.DashboardScreen
import com.yasin.vcardly.presentation.organize.OrganizeScreen
import com.yasin.vcardly.presentation.onboarding.OnboardingScreen
import com.yasin.vcardly.presentation.placeholder.ComingSoonScreen
import com.yasin.vcardly.presentation.settings.SettingsScreen
import com.yasin.vcardly.R

/**
 * Navigation shell. [onboardingCompleted] is resolved before this is shown, so the start
 * destination is correct on the first frame.
 */
@Composable
fun AppRoot(onboardingCompleted: Boolean) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val label = stringResource(destination.labelRes)
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.navigateTopLevel(destination.route) },
                            // The label is always shown, so the icon itself stays decorative.
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (onboardingCompleted) Routes.HOME else Routes.ONBOARDING,
            // Screens own their top bars and status-bar insets; only the bottom bar's space is applied here.
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                DashboardScreen(
                    onOpenContacts = { navController.navigateTopLevel(Routes.CONTACTS) },
                    onOpenFollowUps = { navController.navigateTopLevel(Routes.FOLLOW_UPS) },
                )
            }
            composable(Routes.CONTACTS) {
                ContactsScreen(
                    onOpenContact = { navController.navigate(Routes.contactDetail(it)) },
                    onAddContact = { navController.navigate(Routes.contactEdit(0)) },
                )
            }
            composable(Routes.CONTACT_DETAIL, arguments = contactIdArgs) {
                ContactDetailScreen(
                    onNavigateUp = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.contactEdit(it)) },
                )
            }
            composable(Routes.CONTACT_EDIT, arguments = contactIdArgs) {
                ContactEditScreen(
                    onNavigateUp = { navController.popBackStack() },
                    onSaved = { id, wasNew ->
                        if (wasNew) {
                            // Replace the form with the new contact's details so Back returns to the list.
                            navController.navigate(Routes.contactDetail(id)) {
                                popUpTo(Routes.CONTACT_EDIT) { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                )
            }
            composable(Routes.ORGANIZE) { OrganizeScreen(onNavigateUp = { navController.popBackStack() }) }
            composable(Routes.FOLLOW_UPS) {
                ComingSoonScreen(title = stringResource(R.string.nav_follow_ups))
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenOrganize = { navController.navigate(Routes.ORGANIZE) })
            }
        }
    }
}

private val contactIdArgs = listOf(navArgument("contactId") { type = NavType.LongType })

/** Standard bottom-nav behaviour: one instance per tab, state restored, no back-stack growth. */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
