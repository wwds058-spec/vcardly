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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navigation
import com.yasin.vcardly.presentation.scan.ScanCaptureScreen
import com.yasin.vcardly.presentation.scan.ScanCropScreen
import com.yasin.vcardly.presentation.scan.ScanSessionViewModel
import androidx.navigation.navArgument
import com.yasin.vcardly.presentation.contacts.ContactDetailScreen
import com.yasin.vcardly.presentation.contacts.ContactEditScreen
import com.yasin.vcardly.presentation.contacts.ContactsScreen
import com.yasin.vcardly.presentation.backup.BackupScreen
import com.yasin.vcardly.presentation.dashboard.DashboardScreen
import com.yasin.vcardly.presentation.search.SearchScreen
import com.yasin.vcardly.presentation.mycard.MyCardEditScreen
import com.yasin.vcardly.presentation.mycard.MyCardScreen
import com.yasin.vcardly.presentation.organize.OrganizeScreen
import com.yasin.vcardly.presentation.reports.ReportsScreen
import com.yasin.vcardly.presentation.share.ContactQrScreen
import com.yasin.vcardly.presentation.transfer.TransferScreen
import com.yasin.vcardly.presentation.onboarding.OnboardingScreen
import com.yasin.vcardly.presentation.followups.FollowUpEditScreen
import com.yasin.vcardly.presentation.followups.FollowUpsScreen
import com.yasin.vcardly.presentation.settings.SettingsScreen
import com.yasin.vcardly.R

/**
 * Navigation shell. [onboardingCompleted] is resolved before this is shown, so the start
 * destination is correct on the first frame.
 */
@Composable
fun AppRoot(onboardingCompleted: Boolean, openContactId: Long? = null, onOpenContactHandled: () -> Unit = {}) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    // A tapped reminder notification asks to open its contact.
    LaunchedEffect(openContactId) {
        if (openContactId != null) {
            if (onboardingCompleted) navController.navigate(Routes.contactDetail(openContactId))
            onOpenContactHandled()
        }
    }
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
                    onOpenMyCard = { navController.navigate(Routes.MY_CARD) },
                    onOpenReports = { navController.navigate(Routes.REPORTS) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenContacts = { navController.navigateTopLevel(Routes.CONTACTS) },
                    onOpenFollowUps = { navController.navigateTopLevel(Routes.FOLLOW_UPS) },
                )
            }
            composable(Routes.CONTACTS) {
                ContactsScreen(
                    onOpenContact = { navController.navigate(Routes.contactDetail(it)) },
                    onAddContact = { navController.navigate(Routes.contactEdit(0)) },
                    onScanCard = { navController.navigate(Routes.SCAN_GRAPH) },
                )
            }
            composable(Routes.CONTACT_DETAIL, arguments = contactIdArgs) {
                ContactDetailScreen(
                    onNavigateUp = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.contactEdit(it)) },
                    onShare = { navController.navigate(Routes.contactQr(it)) },
                    onAddFollowUp = { navController.navigate(Routes.followUpEdit(0, contactId = it)) },
                    onOpenFollowUp = { navController.navigate(Routes.followUpEdit(it)) },
                )
            }
            composable(Routes.CONTACT_EDIT, arguments = contactIdArgs + fromScanArg) { entry ->
                val fromScan = entry.arguments?.getBoolean("fromScan") ?: false
                ContactEditScreen(
                    onNavigateUp = { navController.popBackStack() },
                    onSaved = { id, wasNew ->
                        if (wasNew) {
                            // Replace the form (and, after a scan, the whole scan flow) with the new
                            // contact's details so Back returns to the list.
                            navController.navigate(Routes.contactDetail(id)) {
                                popUpTo(if (fromScan) Routes.SCAN_GRAPH else Routes.CONTACT_EDIT) { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                )
            }
            navigation(route = Routes.SCAN_GRAPH, startDestination = Routes.SCAN_CAPTURE) {
                composable(Routes.SCAN_CAPTURE) { entry ->
                    val session = scanSession(navController, entry)
                    ScanCaptureScreen(
                        session = session,
                        onCancel = { navController.popBackStack(Routes.SCAN_GRAPH, inclusive = true) },
                        onCaptured = { navController.navigate(Routes.SCAN_CROP) },
                    )
                }
                composable(Routes.SCAN_CROP) { entry ->
                    val session = scanSession(navController, entry)
                    ScanCropScreen(
                        session = session,
                        onRetake = { navController.popBackStack() },
                        onBackToCapture = { navController.popBackStack(Routes.SCAN_CAPTURE, inclusive = false) },
                        onReview = { navController.navigate(Routes.contactEdit(0, fromScan = true)) },
                    )
                }
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onNavigateUp = { navController.popBackStack() },
                    onOpenContact = { navController.navigate(Routes.contactDetail(it)) },
                    onOpenFollowUp = { navController.navigate(Routes.followUpEdit(it)) },
                )
            }
            composable(Routes.BACKUP) { BackupScreen(onNavigateUp = { navController.popBackStack() }) }
            composable(Routes.REPORTS) { ReportsScreen(onNavigateUp = { navController.popBackStack() }) }
            composable(Routes.MY_CARD) {
                MyCardScreen(onNavigateUp = { navController.popBackStack() }, onEdit = { navController.navigate(Routes.MY_CARD_EDIT) })
            }
            composable(Routes.MY_CARD_EDIT) { MyCardEditScreen(onNavigateUp = { navController.popBackStack() }) }
            composable(Routes.TRANSFER) { TransferScreen(onNavigateUp = { navController.popBackStack() }) }
            composable(Routes.CONTACT_QR, arguments = contactIdArgs) { ContactQrScreen(onNavigateUp = { navController.popBackStack() }) }
            composable(Routes.ORGANIZE) { OrganizeScreen(onNavigateUp = { navController.popBackStack() }) }
            composable(Routes.FOLLOW_UPS) {
                FollowUpsScreen(
                    onAddFollowUp = { navController.navigate(Routes.followUpEdit(0)) },
                    onOpenFollowUp = { navController.navigate(Routes.followUpEdit(it)) },
                )
            }
            composable(Routes.FOLLOWUP_EDIT, arguments = followUpArgs) {
                FollowUpEditScreen(
                    onNavigateUp = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenOrganize = { navController.navigate(Routes.ORGANIZE) },
                    onOpenMyCard = { navController.navigate(Routes.MY_CARD) },
                    onOpenTransfer = { navController.navigate(Routes.TRANSFER) },
                    onOpenReports = { navController.navigate(Routes.REPORTS) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                )
            }
        }
    }
}

private val contactIdArgs = listOf(navArgument("contactId") { type = NavType.LongType })
private val followUpArgs = listOf(
    navArgument("followUpId") { type = NavType.LongType },
    navArgument("contactId") {
        type = NavType.LongType
        defaultValue = 0L
    },
)
private val fromScanArg = navArgument("fromScan") {
    type = NavType.BoolType
    defaultValue = false
}

/** The scan session lives as long as the scan graph is on the back stack. */
@Composable
private fun scanSession(navController: NavHostController, entry: NavBackStackEntry): ScanSessionViewModel {
    val parent = remember(entry) { navController.getBackStackEntry(Routes.SCAN_GRAPH) }
    return hiltViewModel(parent)
}

/** Standard bottom-nav behaviour: one instance per tab, state restored, no back-stack growth. */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
