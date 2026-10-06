package com.yasin.vcardly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.services.storage.TestStorage
import com.yasin.vcardly.core.designsystem.component.BottomNavItem
import com.yasin.vcardly.core.designsystem.component.VCardlyBottomNavigation
import com.yasin.vcardly.core.designsystem.theme.VCardlyTheme
import com.yasin.vcardly.domain.model.FollowUpBucket
import com.yasin.vcardly.domain.model.ThemeMode
import com.yasin.vcardly.presentation.contacts.ContactDetailActions
import com.yasin.vcardly.presentation.contacts.ContactDetailContent
import com.yasin.vcardly.presentation.contacts.ContactDetailUiState
import com.yasin.vcardly.presentation.contacts.ContactEditActions
import com.yasin.vcardly.presentation.contacts.ContactEditContent
import com.yasin.vcardly.presentation.contacts.ContactEditUiState
import com.yasin.vcardly.presentation.contacts.ContactForm
import com.yasin.vcardly.presentation.contacts.ContactsActions
import com.yasin.vcardly.presentation.contacts.ContactsContent
import com.yasin.vcardly.presentation.contacts.ContactsUiState
import com.yasin.vcardly.presentation.contacts.DetailTab
import com.yasin.vcardly.presentation.contacts.FormStep
import com.yasin.vcardly.presentation.dashboard.DashboardActions
import com.yasin.vcardly.presentation.dashboard.DashboardContent
import com.yasin.vcardly.presentation.dashboard.DashboardUiState
import com.yasin.vcardly.presentation.dashboard.Greeting
import com.yasin.vcardly.presentation.followups.FollowUpsActions
import com.yasin.vcardly.presentation.followups.FollowUpsContent
import com.yasin.vcardly.presentation.followups.FollowUpsUiState
import com.yasin.vcardly.presentation.mycard.MyCardContent
import com.yasin.vcardly.presentation.navigation.TopLevelDestination
import com.yasin.vcardly.presentation.onboarding.OnboardingContent
import com.yasin.vcardly.presentation.share.QrSharePanelContent
import com.yasin.vcardly.core.billing.BillingStatus
import com.yasin.vcardly.domain.backup.BackupCounts
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.model.FollowUpStatus
import com.yasin.vcardly.domain.model.FollowUpType
import com.yasin.vcardly.domain.report.ReportBuilder
import com.yasin.vcardly.domain.report.ReportRange
import com.yasin.vcardly.presentation.backup.BackupActions
import com.yasin.vcardly.presentation.backup.BackupContent
import com.yasin.vcardly.presentation.backup.BackupUiState
import com.yasin.vcardly.presentation.backup.CreateState
import com.yasin.vcardly.presentation.lock.LockContent
import com.yasin.vcardly.presentation.pro.ProActions
import com.yasin.vcardly.presentation.pro.ProContent
import com.yasin.vcardly.presentation.pro.ProUiState
import com.yasin.vcardly.presentation.reports.ReportsActions
import com.yasin.vcardly.presentation.reports.ReportsContent
import com.yasin.vcardly.presentation.reports.ReportsUiState
import com.yasin.vcardly.presentation.settings.SettingsContent
import com.yasin.vcardly.presentation.settings.SettingsUiState
import com.yasin.vcardly.presentation.settings.SettingsActions
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.model.Contact
import com.yasin.vcardly.domain.model.TagWithCount
import com.yasin.vcardly.domain.vcard.ImportCandidate
import com.yasin.vcardly.domain.vcard.ImportStatus
import com.yasin.vcardly.presentation.organize.OrganizeActions
import com.yasin.vcardly.presentation.organize.OrganizeContent
import com.yasin.vcardly.presentation.organize.OrganizeUiState
import com.yasin.vcardly.presentation.transfer.ExportState
import com.yasin.vcardly.presentation.transfer.ImportState
import com.yasin.vcardly.presentation.transfer.TransferActions
import com.yasin.vcardly.presentation.transfer.TransferContent
import com.yasin.vcardly.presentation.transfer.TransferUiState
import org.junit.Assert.assertTrue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Renders the redesigned screens with fictional sample data in light and dark themes on a real emulator and saves
 * PNGs through TestStorage (AGP pulls them into build/outputs/connected_android_test_additional_output). CI publishes
 * them so the visual design can be reviewed; the test also fails if any screen crashes while composing.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    /**
     * [frozen]: for screens with an endless animation, which never let the UI go idle; the clock is stepped manually.
     * [fontScale]: 2f is Android's largest font size. [audit]: also run the accessibility checks ([A11yAudit]) and fail on errors.
     */
    private fun shoot(
        name: String,
        dark: Boolean = false,
        frozen: Boolean = false,
        fontScale: Float = 1f,
        audit: Boolean = !frozen && fontScale == 1f,
        content: @Composable () -> Unit,
    ) {
        if (frozen) rule.mainClock.autoAdvance = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale * density.fontScale)) {
                VCardlyTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
        if (frozen) rule.mainClock.advanceTimeBy(800) else rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        TestStorage().openOutputFile("screenshots/$name.png").use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        if (audit) {
            val errors = A11yAudit.check(name)
            assertTrue("Accessibility errors:\n" + errors.joinToString("\n"), errors.isEmpty())
        }
    }

    /** Top-level screens are shown with the real bottom bar under them, as in the app. */
    @Composable
    private fun WithBottomBar(selected: TopLevelDestination, content: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { content() }
            VCardlyBottomNavigation(
                items = TopLevelDestination.entries.map { BottomNavItem(it.route, stringResource(it.labelRes), it.icon, it.selectedIcon) },
                selectedKey = selected.route,
                onSelect = {},
                scanLabel = "Scan card",
                onScan = {},
                scanIcon = Icons.Rounded.DocumentScanner,
            )
        }
    }

    private val home = DashboardUiState(
        isLoading = false, greeting = Greeting.MORNING, firstName = "Yasin", myCardName = "Yasin Khan",
        totalContacts = 128, addedThisMonth = 12, favorites = 24, followUps = SampleData.counts,
        upcoming = SampleData.followUps.take(2), recent = SampleData.contacts, categories = SampleData.breakdown,
    )

    @Test fun onboarding() = shoot("01_onboarding") { OnboardingContent(onFinish = {}) }

    @Test fun homeLight() = shoot("02_home") { WithBottomBar(TopLevelDestination.HOME) { DashboardContent(home, DashboardActions()) } }

    @Test fun homeDark() = shoot("12_home_dark", dark = true) { WithBottomBar(TopLevelDestination.HOME) { DashboardContent(home.copy(greeting = Greeting.EVENING), DashboardActions()) } }

    @Test fun homeEmpty() = shoot("02b_home_empty") {
        WithBottomBar(TopLevelDestination.HOME) { DashboardContent(DashboardUiState(isLoading = false, greeting = Greeting.AFTERNOON), DashboardActions()) }
    }

    @Test fun contacts() = shoot("03_contacts") {
        WithBottomBar(TopLevelDestination.CONTACTS) {
            ContactsContent(ContactsUiState(isLoading = false, contacts = SampleData.contacts, categories = SampleData.categories, tags = SampleData.tags), ContactsActions())
        }
    }

    @Test fun contactsDark() = shoot("03b_contacts_dark", dark = true) {
        WithBottomBar(TopLevelDestination.CONTACTS) {
            ContactsContent(ContactsUiState(isLoading = false, contacts = SampleData.contacts, categories = SampleData.categories, tags = SampleData.tags), ContactsActions())
        }
    }

    @Test fun contactsEmpty() = shoot("03c_contacts_empty") {
        WithBottomBar(TopLevelDestination.CONTACTS) { ContactsContent(ContactsUiState(isLoading = false), ContactsActions()) }
    }

    @Test fun contactDetail() = shoot("04_contact_details") {
        ContactDetailContent(ContactDetailUiState.Content(SampleData.rajesh), SampleData.riyaFollowUps, ContactDetailActions())
    }

    @Test fun contactDetailDark() = shoot("04b_contact_details_dark", dark = true) {
        ContactDetailContent(ContactDetailUiState.Content(SampleData.rajesh), SampleData.riyaFollowUps, ContactDetailActions())
    }

    @Test fun contactDetailFollowUps() = shoot("04c_contact_followups") {
        ContactDetailContent(ContactDetailUiState.Content(SampleData.priya), SampleData.riyaFollowUps, ContactDetailActions(), initialTab = DetailTab.FOLLOW_UPS)
    }

    @Test fun addContact() = shoot("05_add_contact") {
        ContactEditContent(ContactEditUiState(isLoading = false, categories = SampleData.categories, tags = SampleData.tags), ContactEditActions())
    }

    @Test fun addContactBusiness() = shoot("05b_add_contact_business") {
        ContactEditContent(
            ContactEditUiState(isLoading = false, categories = SampleData.categories, tags = SampleData.tags, form = ContactForm(fullName = "Rajesh Kumar", categoryId = 1, tagIds = setOf(1))),
            ContactEditActions(),
            initialStep = FormStep.BUSINESS,
        )
    }

    @Test fun ocrReview() = shoot("07_ocr_review") {
        ContactEditContent(
            ContactEditUiState(
                isLoading = false, isFromScan = true, detectedCount = 7, categories = SampleData.categories,
                form = ContactForm(
                    fullName = "Rajesh Kumar", company = "ABC Technologies", jobTitle = "Business Development", phone = "+91 98765 43210",
                    email = "rajesh@abctech.example", website = "www.abctech.example", address = "Hyderabad, Telangana, India",
                ),
            ),
            ContactEditActions(onRescan = {}),
        )
    }

    @Test fun ocrFailed() = shoot("07b_ocr_failed") {
        ContactEditContent(ContactEditUiState(isLoading = false, isFromScan = true, ocrFailed = true), ContactEditActions(onRescan = {}))
    }

    @Test fun followUps() = shoot("08_follow_ups") {
        WithBottomBar(TopLevelDestination.FOLLOW_UPS) {
            FollowUpsContent(FollowUpsUiState(isLoading = false, bucket = FollowUpBucket.TODAY, counts = SampleData.counts, items = SampleData.followUps.take(2)), FollowUpsActions())
        }
    }

    @Test fun followUpsUpcomingDark() = shoot("08b_follow_ups_upcoming_dark", dark = true) {
        WithBottomBar(TopLevelDestination.FOLLOW_UPS) {
            FollowUpsContent(FollowUpsUiState(isLoading = false, bucket = FollowUpBucket.UPCOMING, counts = SampleData.counts, items = SampleData.followUps.drop(2)), FollowUpsActions())
        }
    }

    @Test fun myCard() = shoot("09_my_card") {
        MyCardContent(SampleData.myCard, onNavigateUp = {}, onEdit = {}, sharePanel = { QrSharePanelContent(it, null, {}, {}) })
    }

    @Test fun myCardDark() = shoot("09b_my_card_dark", dark = true) {
        MyCardContent(SampleData.myCard, onNavigateUp = {}, onEdit = {}, sharePanel = { QrSharePanelContent(it, null, {}, {}) })
    }

    /** A year of fictional activity so the charts have something to show. */
    private fun sampleReport() = run {
        val cats = SampleData.categories + listOf<com.yasin.vcardly.domain.model.Category?>(null)
        val month = 30L * 86_400_000L
        val contacts = (1..96).map { i ->
            val base = SampleData.contacts[i % SampleData.contacts.size]
            val cat = cats[(i * 7) % cats.size]
            ContactDetails(
                base.contact.copy(id = i.toLong(), createdAt = SampleData.now - (i % 11) * month - (i % 5) * 86_400_000L, categoryId = cat?.id, isFavorite = i % 4 == 0,
                    source = com.yasin.vcardly.domain.model.ContactSource.entries[i % 3]),
                cat, if (i % 3 == 0) listOf(SampleData.vip) else emptyList(),
            )
        }
        val followUps = (1..40).map { i ->
            FollowUp(i.toLong(), (i % 96 + 1).toLong(), FollowUpType.pickable[i % 7], if (i % 5 == 0) FollowUpStatus.PENDING else FollowUpStatus.COMPLETED,
                "Follow-up $i", dueAt = SampleData.now - i * 4 * 86_400_000L, completedAt = SampleData.now - i * 4 * 86_400_000L + 3_600_000L)
        }
        ReportBuilder.build(contacts, followUps, ReportRange.LAST_12_MONTHS, Instant.now(), ZoneId.systemDefault())
    }

    @Test fun reports() = shoot("10_reports") { ReportsContent(ReportsUiState(report = sampleReport()), isPro = false, actions = ReportsActions()) }

    @Test fun reportsDark() = shoot("10b_reports_dark", dark = true) { ReportsContent(ReportsUiState(report = sampleReport()), isPro = true, actions = ReportsActions()) }

    private val settings = SettingsUiState(version = "0.2.0", notificationsOn = true, exactAlarmsOn = false, canOpenExactSettings = true)

    @Test fun settingsLight() = shoot("11_settings") { WithBottomBar(TopLevelDestination.SETTINGS) { SettingsContent(settings, SettingsActions()) } }

    @Test fun settingsDark() = shoot("11b_settings_dark", dark = true) { WithBottomBar(TopLevelDestination.SETTINGS) { SettingsContent(settings, SettingsActions()) } }

    @Test fun lock() = shoot("13_lock", frozen = true) { LockContent(failed = false, onUnlock = {}) }

    @Test fun pro() = shoot("14_pro") { ProContent(ProUiState(status = BillingStatus.ProductsNotConfigured), ProActions()) }

    @Test fun backup() = shoot("15_backup") {
        BackupContent(BackupUiState(lastBackupAt = SampleData.now - 3 * 86_400_000L, create = CreateState.Done(BackupCounts(contacts = 128, followUps = 34, images = 96))), 8, BackupActions())
    }

    @Test fun backupDark() = shoot("15b_backup_dark", dark = true) { BackupContent(BackupUiState(), 8, BackupActions()) }

    @Test fun organize() = shoot("16_organize") {
        val investors = Category(9, "Investors", 0xFF00897BL, null)
        OrganizeContent(
            OrganizeUiState(
                categories = SampleData.categories + investors,
                tags = listOf(TagWithCount(SampleData.vip, 2), TagWithCount(SampleData.potential, 1)),
                categoryCounts = mapOf(1L to 2, 2L to 1, 3L to 1, 9L to 0, null to 1),
                loaded = true,
            ),
            OrganizeActions(),
        )
    }

    @Test fun organizeDark() = shoot("16b_organize_dark", dark = true) {
        OrganizeContent(OrganizeUiState(categories = SampleData.categories, categoryCounts = mapOf(1L to 2), loaded = true), OrganizeActions())
    }

    @Test fun transfer() = shoot("17_transfer") { TransferContent(TransferUiState(), TransferActions()) }

    @Test fun transferPreview() = shoot("17b_transfer_preview") {
        val entries = listOf(
            ImportCandidate(0, ImportStatus.NEW, Contact(fullName = "Kavya Iyer", company = "Bright Labs"), emptyList()),
            ImportCandidate(1, ImportStatus.DUPLICATE, Contact(fullName = "Priya Sharma", email = "priya@globalsol.example"), emptyList()),
            ImportCandidate(2, ImportStatus.NEW, Contact(fullName = "Daniel Okafor", email = "daniel@okafor.example"), emptyList()),
            ImportCandidate(3, ImportStatus.UNUSABLE, null, emptyList()),
        )
        TransferContent(TransferUiState(import = ImportState.Preview(entries, setOf(0, 2)), export = ExportState.Done(5)), TransferActions())
    }

    // Android's largest font size (2x). Checked visually; text must wrap or grow, never overlap or vanish.
    @Test fun largeHome() = shoot("30_large_home", fontScale = 2f) { WithBottomBar(TopLevelDestination.HOME) { DashboardContent(home, DashboardActions()) } }

    @Test fun largeContacts() = shoot("30_large_contacts", fontScale = 2f) {
        WithBottomBar(TopLevelDestination.CONTACTS) {
            ContactsContent(ContactsUiState(isLoading = false, contacts = SampleData.contacts, categories = SampleData.categories, tags = SampleData.tags), ContactsActions())
        }
    }

    @Test fun largeContactDetail() = shoot("30_large_contact_details", fontScale = 2f) {
        ContactDetailContent(ContactDetailUiState.Content(SampleData.rajesh), SampleData.riyaFollowUps, ContactDetailActions())
    }

    @Test fun largeAddContact() = shoot("30_large_add_contact", fontScale = 2f) {
        ContactEditContent(ContactEditUiState(isLoading = false, categories = SampleData.categories, tags = SampleData.tags), ContactEditActions())
    }

    @Test fun largeFollowUps() = shoot("30_large_follow_ups", fontScale = 2f) {
        WithBottomBar(TopLevelDestination.FOLLOW_UPS) {
            FollowUpsContent(FollowUpsUiState(isLoading = false, bucket = FollowUpBucket.TODAY, counts = SampleData.counts, items = SampleData.followUps.take(2)), FollowUpsActions())
        }
    }

    @Test fun largeSettings() = shoot("30_large_settings", fontScale = 2f) { WithBottomBar(TopLevelDestination.SETTINGS) { SettingsContent(settings, SettingsActions()) } }

    @Test fun largeMyCard() = shoot("30_large_my_card", fontScale = 2f) {
        MyCardContent(SampleData.myCard, onNavigateUp = {}, onEdit = {}, sharePanel = { QrSharePanelContent(it, null, {}, {}) })
    }

    @Test fun largeReports() = shoot("30_large_reports", fontScale = 2f) { ReportsContent(ReportsUiState(report = sampleReport()), isPro = false, actions = ReportsActions()) }
}

