package com.yasin.vcardly.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.theme.VCardlyTheme
import com.yasin.vcardly.domain.model.ContactFilter
import com.yasin.vcardly.domain.model.FollowUpBucket
import com.yasin.vcardly.domain.usecase.ContactField
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
import com.yasin.vcardly.presentation.dashboard.DashboardActions
import com.yasin.vcardly.presentation.dashboard.DashboardContent
import com.yasin.vcardly.presentation.dashboard.DashboardUiState
import com.yasin.vcardly.presentation.followups.FollowUpsActions
import com.yasin.vcardly.presentation.followups.FollowUpsContent
import com.yasin.vcardly.presentation.followups.FollowUpsUiState
import com.yasin.vcardly.presentation.mycard.MyCardContent
import com.yasin.vcardly.presentation.onboarding.OnboardingContent
import com.yasin.vcardly.presentation.share.QrSharePanelContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Interaction tests on the stateless screen content: what the user taps reaches the right callback. */
@RunWith(AndroidJUnit4::class)
class ScreenBehaviourTest {
    @get:Rule val rule = createComposeRule()
    private val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
    private fun s(id: Int, vararg args: Any) = res.getString(id, *args)

    @Test fun onboarding_skipAndGetStartedFinish() {
        var finished = 0
        rule.setContent { VCardlyTheme { OnboardingContent(onFinish = { finished++ }) } }
        rule.onNodeWithText(s(R.string.onboarding_skip)).performClick()
        assertEquals(1, finished)
        rule.onNodeWithText(s(R.string.onboarding_next)).performClick()
        rule.onNodeWithText(s(R.string.onboarding_next)).performClick()
        rule.onNodeWithText(s(R.string.onboarding_get_started)).performClick()
        assertEquals(2, finished)
    }

    @Test fun home_quickActionsAndStatsNavigate() {
        val calls = mutableListOf<String>()
        val state = DashboardUiState(isLoading = false, firstName = "Yasin", totalContacts = 128, favorites = 24, followUps = SampleData.counts, upcoming = SampleData.followUps.take(1), recent = SampleData.contacts)
        rule.setContent {
            VCardlyTheme {
                DashboardContent(state, DashboardActions(onScan = { calls += "scan" }, onAddContact = { calls += "add" }, onOpenSearch = { calls += "search" }, onCompleteFollowUp = { calls += "done$it" }))
            }
        }
        rule.onNodeWithText("Yasin 👋").assertIsDisplayed()
        rule.onNodeWithText("128").assertIsDisplayed()
        rule.onNodeWithText(s(R.string.contacts_scan)).performClick()
        rule.onNodeWithText(s(R.string.contacts_add)).performClick()
        rule.onNodeWithText(s(R.string.home_search_placeholder)).performClick()
        rule.onNodeWithContentDescription(s(R.string.followup_complete_item, "Discuss wholesale proposal")).performClick()
        assertEquals(listOf("scan", "add", "search", "done1"), calls)
    }

    @Test fun contacts_searchFilterFavoriteAndOpen() {
        var query = ""
        var favoritesOnly = 0
        var toggled: Pair<Long, Boolean>? = null
        var opened = 0L
        rule.setContent {
            var filter by androidx.compose.runtime.remember { mutableStateOf(ContactFilter()) }
            VCardlyTheme {
                ContactsContent(
                    ContactsUiState(isLoading = false, filter = filter, contacts = SampleData.contacts, categories = SampleData.categories),
                    ContactsActions(
                        onQueryChange = { query = it; filter = filter.copy(query = it) },
                        onFavoritesOnly = { favoritesOnly++ },
                        onToggleFavorite = { id, cur -> toggled = id to cur },
                        onOpenContact = { opened = it },
                    ),
                )
            }
        }
        rule.onNodeWithContentDescription(s(R.string.home_search_placeholder)).performTextInput("raj")
        assertEquals("raj", query)
        rule.onNodeWithText(s(R.string.contacts_filter_favorites)).performClick()
        assertEquals(1, favoritesOnly)
        rule.onNodeWithContentDescription(s(R.string.contact_add_favorite, "Mohammed Ali")).performClick()
        assertEquals(3L to false, toggled)
        rule.onNodeWithText("Priya Sharma").performClick()
        assertEquals(2L, opened)
    }

    @Test fun contacts_emptyStateOffersScanAndAdd() {
        var scan = 0; var add = 0
        rule.setContent { VCardlyTheme { ContactsContent(ContactsUiState(isLoading = false), ContactsActions(onScanCard = { scan++ }, onAddContact = { add++ })) } }
        rule.onNodeWithText(s(R.string.empty_network_title)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.empty_network_action)).performClick()
        rule.onNodeWithText(s(R.string.contacts_add)).performClick()
        assertEquals(1, scan); assertEquals(1, add)
    }

    @Test fun contactDetail_showsInfoTabsAndMenu() {
        var edited = 0; var deleted = 0; var fav = 0
        rule.setContent {
            VCardlyTheme {
                ContactDetailContent(
                    ContactDetailUiState.Content(SampleData.rajesh), SampleData.riyaFollowUps,
                    ContactDetailActions(onEdit = { edited++ }, onDelete = { deleted++ }, onToggleFavorite = { fav++ }),
                )
            }
        }
        rule.onNodeWithText("Rajesh Kumar").assertIsDisplayed()
        rule.onAllNodesWithText("+91 98765 43210")[0].assertIsDisplayed()
        rule.onNodeWithContentDescription(s(R.string.contact_remove_favorite, "Rajesh Kumar")).performClick()
        assertEquals(1, fav)
        rule.onNodeWithText(s(R.string.field_notes)).performClick()
        rule.onNodeWithText(SampleData.rajesh.contact.notes).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.common_more)).performClick()
        rule.onNodeWithText(s(R.string.contact_edit)).performClick()
        assertEquals(1, edited)
        rule.onNodeWithText(s(R.string.common_more)).performClick()
        rule.onNodeWithText(s(R.string.contact_delete)).performClick()
        assertEquals(1, deleted)
    }

    @Test fun addContact_nextIsBlockedUntilStepIsValid() {
        var valid = false
        var steps = 0
        rule.setContent {
            VCardlyTheme {
                ContactEditContent(
                    ContactEditUiState(isLoading = false),
                    ContactEditActions(onValidate = { fields -> steps++; assertTrue(ContactField.FULL_NAME in fields); valid }),
                )
            }
        }
        rule.onNodeWithText(s(R.string.onboarding_next)).performClick()
        // Still on the first step: the name field is shown.
        rule.onNodeWithText(s(R.string.hint_full_name)).assertIsDisplayed()
        valid = true
        rule.onNodeWithText(s(R.string.onboarding_next)).performClick()
        rule.onNodeWithText(s(R.string.hint_website)).assertIsDisplayed()
        assertEquals(2, steps)
    }

    @Test fun editContact_saveFromTopBar() {
        var saves = 0
        rule.setContent {
            VCardlyTheme { ContactEditContent(ContactEditUiState(isLoading = false, isNew = false, form = ContactForm(fullName = "Priya Sharma")), ContactEditActions(onSave = { saves++ })) }
        }
        rule.onNodeWithText(s(R.string.contact_edit_title)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.common_save)).performClick()
        assertEquals(1, saves)
    }

    @Test fun ocrReview_showsFoundCountAndOffersRescanAndSave() {
        var rescans = 0; var saves = 0
        rule.setContent {
            VCardlyTheme {
                ContactEditContent(
                    ContactEditUiState(isLoading = false, isFromScan = true, detectedCount = 7, form = ContactForm(fullName = "Rajesh Kumar")),
                    ContactEditActions(onRescan = { rescans++ }, onSave = { saves++ }),
                )
            }
        }
        rule.onNodeWithText(s(R.string.scan_success_title)).assertIsDisplayed()
        rule.onNodeWithText(res.getQuantityString(R.plurals.scan_success_found, 7, 7)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.scan_rescan)).performClick()
        rule.onNodeWithText(s(R.string.contact_save_contact)).performClick()
        assertEquals(1, rescans); assertEquals(1, saves)
    }

    @Test fun ocrFailure_isHonestAndOffersManualEntry() {
        rule.setContent { VCardlyTheme { ContactEditContent(ContactEditUiState(isLoading = false, isFromScan = true, ocrFailed = true), ContactEditActions(onRescan = {})) } }
        rule.onNodeWithText(s(R.string.scan_failed_title)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.scan_enter_manually)).performClick()
        rule.onNodeWithText(s(R.string.hint_full_name)).assertIsDisplayed()
    }

    @Test fun followUps_tabsAndComplete() {
        var bucket: FollowUpBucket? = null
        var done = 0L
        rule.setContent {
            VCardlyTheme {
                FollowUpsContent(
                    FollowUpsUiState(isLoading = false, counts = SampleData.counts, items = SampleData.followUps.take(2)),
                    FollowUpsActions(onSelect = { bucket = it }, onToggleDone = { done = it.followUp.id }),
                )
            }
        }
        rule.onNodeWithText(s(R.string.followup_completed)).performClick()
        assertEquals(FollowUpBucket.COMPLETED, bucket)
        rule.onNodeWithContentDescription(s(R.string.followup_complete_item, "Send quotation")).performClick()
        assertEquals(2L, done)
    }

    @Test fun myCard_qrFieldPickerAndShare() {
        var shared: Set<com.yasin.vcardly.domain.vcard.ShareField>? = null
        rule.setContent {
            VCardlyTheme { MyCardContent(SampleData.myCard, {}, {}, sharePanel = { QrSharePanelContent(it, null, onShare = { sel -> shared = sel }, onSaveQr = {}) }) }
        }
        rule.onNodeWithText(s(R.string.share_qr_title)).assertIsDisplayed()
        // Untick the address-less default "Website" and share: the shared set must not contain it.
        rule.onNodeWithText(s(R.string.field_website)).performClick()
        rule.onNodeWithText(s(R.string.contact_share)).performClick()
        val sel = checkNotNull(shared)
        assertTrue(com.yasin.vcardly.domain.vcard.ShareField.NAME in sel)
        assertTrue(com.yasin.vcardly.domain.vcard.ShareField.WEBSITE !in sel)
    }

    @Test fun contacts_allChipSelectedByDefault() {
        rule.setContent { VCardlyTheme { ContactsContent(ContactsUiState(isLoading = false, contacts = SampleData.contacts), ContactsActions()) } }
        rule.onNodeWithText(s(R.string.contacts_filter_all)).assertIsSelected()
    }
}
