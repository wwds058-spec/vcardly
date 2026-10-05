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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the redesigned screens with fictional sample data in light and dark themes on a real emulator and saves
 * PNGs through TestStorage (AGP pulls them into build/outputs/connected_android_test_additional_output). CI publishes
 * them so the visual design can be reviewed; the test also fails if any screen crashes while composing.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            VCardlyTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        TestStorage().openOutputFile("screenshots/$name.png").use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
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
}
