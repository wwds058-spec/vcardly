package com.yasin.vcardly.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.BottomNavItem
import com.yasin.vcardly.core.designsystem.component.VCardlyBottomNavigation
import com.yasin.vcardly.presentation.backup.BackupScreen
import com.yasin.vcardly.presentation.contacts.ContactDetailScreen
import com.yasin.vcardly.presentation.contacts.ContactEditScreen
import com.yasin.vcardly.presentation.contacts.ContactsScreen
import com.yasin.vcardly.presentation.dashboard.DashboardScreen
import com.yasin.vcardly.presentation.followups.FollowUpEditScreen
import com.yasin.vcardly.presentation.followups.FollowUpsScreen
import com.yasin.vcardly.presentation.mycard.MyCardEditScreen
import com.yasin.vcardly.presentation.mycard.MyCardScreen
import com.yasin.vcardly.presentation.onboarding.OnboardingScreen
import com.yasin.vcardly.presentation.organize.OrganizeScreen
import com.yasin.vcardly.presentation.privacy.PrivacyScreen
import com.yasin.vcardly.presentation.pro.ProScreen
import com.yasin.vcardly.presentation.reports.ReportsScreen
import com.yasin.vcardly.presentation.scan.ScanCaptureScreen
import com.yasin.vcardly.presentation.scan.ScanCropScreen
import com.yasin.vcardly.presentation.scan.ScanSessionViewModel
import com.yasin.vcardly.presentation.scan.rememberScanLauncher
import com.yasin.vcardly.presentation.search.SearchScreen
import com.yasin.vcardly.presentation.settings.SettingsScreen
import com.yasin.vcardly.presentation.share.ContactQrScreen
import com.yasin.vcardly.presentation.transfer.TransferScreen

/**
 * Navigation shell. [onboardingCompleted] is resolved before this is shown, so the start
 * destination is correct on the first frame. Screens never see the NavController: every
 * navigation decision is a callback wired here.
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
    // One gated entry point for scanning (free-plan monthly allowance), used by the centre button, Home and Contacts.
    val startScan = rememberScanLauncher(
        onAllowed = { navController.navigate(Routes.SCAN_GRAPH) },
        onUpgrade = { navController.navigate(Routes.PRO) },
    )
    val navItems = TopLevelDestination.entries.map {
        BottomNavItem(key = it.route, label = stringResource(it.labelRes), icon = it.icon, selectedIcon = it.selectedIcon)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                VCardlyBottomNavigation(
                    items = navItems,
                    selectedKey = currentRoute,
                    onSelect = { navController.navigateTopLevel(it.key) },
                    scanLabel = stringResource(R.string.contacts_scan),
                    scanIcon = Icons.Rounded.DocumentScanner,
                    onScan = startScan,
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (onboardingCompleted) Routes.HOME else Routes.ONBOARDING,
            // Screens own their top bars and status-bar insets; only the bottom bar's space is applied here.
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = innerPadding.calculateBottomPadding()),
            enterTransition = { enter() },
            exitTransition = { exit() },
            popEnterTransition = { popEnter() },
            popExitTransition = { popExit() },
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
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenContacts = { navController.navigateTopLevel(Routes.CONTACTS) },
                    onOpenFollowUps = { navController.navigateTopLevel(Routes.FOLLOW_UPS) },
                    onOpenContact = { navController.navigate(Routes.contactDetail(it)) },
                    onOpenFollowUp = { navController.navigate(Routes.followUpEdit(it)) },
                    onScan = startScan,
                    onAddContact = { navController.navigate(Routes.contactEdit(0)) },
                    onOpenMyCard = { navController.navigate(Routes.MY_CARD) },
                    onAddFollowUp = { navController.navigate(Routes.followUpEdit(0)) },
                    onOpenReports = { navController.navigate(Routes.REPORTS) },
                )
            }
            composable(Routes.CONTACTS) {
                ContactsScreen(
                    onOpenContact = { navController.navigate(Routes.contactDetail(it)) },
                    onAddContact = { navController.navigate(Routes.contactEdit(0)) },
                    onScanCard = startScan,
                    onOpenOrganize = { navController.navigate(Routes.ORGANIZE) },
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
                // Only after a scan: "Rescan" restarts the scan session that is still on the back stack.
                val session: ScanSessionViewModel? = if (fromScan) scanSessionOrNull(navController) else null
                ContactEditScreen(
                    onNavigateUp = { navController.popBackStack() },
                    onRescan = if (session != null) {
                        {
                            session.restart()
                            navController.popBackStack(Routes.SCAN_CAPTURE, inclusive = false)
                        }
                    } else null,
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
                composable(
                    Routes.SCAN_CAPTURE,
                    enterTransition = { fadeIn(tween(250)) },
                    exitTransition = { fadeOut(tween(200)) },
                ) { entry ->
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
            composable(Routes.PRIVACY) { PrivacyScreen(onNavigateUp = { navController.popBackStack() }) }
            composable(Routes.BACKUP) { BackupScreen(onNavigateUp = { navController.popBackStack() }) }
            composable(Routes.REPORTS) { ReportsScreen(onNavigateUp = { navController.popBackStack() }, onUpgrade = { navController.navigate(Routes.PRO) }) }
            composable(Routes.PRO) { ProScreen(onNavigateUp = { navController.popBackStack() }) }
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
                    onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onOpenPro = { navController.navigate(Routes.PRO) },
                )
            }
        }
    }
}

private val topLevelRoutes = TopLevelDestination.entries.map { it.route }.toSet()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in topLevelRoutes && targetState.destination.route in topLevelRoutes

// Tabs cross-fade; pushed screens rise slightly while fading in. Short durations keep navigation snappy.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    if (isTabSwitch()) fadeIn(tween(180))
    else fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 14 }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    if (isTabSwitch()) fadeOut(tween(120)) else fadeOut(tween(160))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition = fadeIn(tween(200))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    if (isTabSwitch()) fadeOut(tween(120)) else fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 14 }

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

/** The session of a scan still on the back stack (the review form sits above it), or null. */
@Composable
private fun scanSessionOrNull(navController: NavHostController): ScanSessionViewModel? {
    val parent = remember { runCatching { navController.getBackStackEntry(Routes.SCAN_GRAPH) }.getOrNull() } ?: return null
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
