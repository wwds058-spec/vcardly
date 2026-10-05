package com.yasin.vcardly.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.domain.entitlement.EntitlementPolicy
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.FollowUpBucket
import com.yasin.vcardly.domain.model.FollowUpCounts
import com.yasin.vcardly.domain.model.FollowUpWithContact
import com.yasin.vcardly.domain.repository.CategoryRepository
import com.yasin.vcardly.domain.repository.ContactRepository
import com.yasin.vcardly.domain.repository.FollowUpRepository
import com.yasin.vcardly.domain.repository.MyCardRepository
import com.yasin.vcardly.domain.usecase.CategoryBreakdownItem
import com.yasin.vcardly.domain.usecase.FollowUpManager
import com.yasin.vcardly.domain.usecase.buildCategoryBreakdown
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Greeting { MORNING, AFTERNOON, EVENING }

/** Everything on Home comes from Room/DataStore; nothing is hard-coded. */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val greeting: Greeting = Greeting.MORNING,
    /** The user's own name from "My digital card" (first word), blank when not set. */
    val firstName: String = "",
    val myCardName: String = "",
    val totalContacts: Int = 0,
    val addedThisMonth: Int = 0,
    val favorites: Int = 0,
    val followUps: FollowUpCounts = FollowUpCounts(0, 0, 0, 0),
    /** Next few active follow-ups, overdue first. */
    val upcoming: List<FollowUpWithContact> = emptyList(),
    val recent: List<ContactDetails> = emptyList(),
    val categories: List<CategoryBreakdownItem> = emptyList(),
) {
    val pendingFollowUps: Int get() = followUps.today + followUps.upcoming + followUps.overdue
    val isEmpty: Boolean get() = !isLoading && totalContacts == 0
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    contacts: ContactRepository,
    categories: CategoryRepository,
    followUps: FollowUpRepository,
    myCard: MyCardRepository,
    private val followUpManager: FollowUpManager,
    clock: Clock,
) : ViewModel() {

    private val greeting: Greeting = clock.instant().atZone(clock.zone).hour.let { h ->
        when {
            h < 12 -> Greeting.MORNING
            h < 17 -> Greeting.AFTERNOON
            else -> Greeting.EVENING
        }
    }

    private val nextUp = combine(
        followUps.observe(FollowUpBucket.OVERDUE),
        followUps.observe(FollowUpBucket.TODAY),
        followUps.observe(FollowUpBucket.UPCOMING),
    ) { overdue, today, upcoming -> (overdue + today + upcoming).take(UPCOMING_LIMIT) }

    val uiState: StateFlow<DashboardUiState> = combine(
        contacts.observeStats(addedSince = EntitlementPolicy.monthStart(clock.instant(), clock.zone)),
        combine(categories.observeAll(), myCard.card, ::Pair),
        followUps.observeCounts(),
        nextUp,
        contacts.observeRecent(RECENT_LIMIT),
    ) { stats, (allCategories, card), counts, next, recent ->
        DashboardUiState(
            isLoading = false,
            greeting = greeting,
            firstName = card.fullName.trim().substringBefore(' '),
            myCardName = card.fullName,
            totalContacts = stats.total,
            addedThisMonth = stats.addedSince,
            favorites = stats.favorites,
            followUps = counts,
            upcoming = next,
            recent = recent,
            categories = buildCategoryBreakdown(stats.byCategory, allCategories),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun complete(id: Long) { viewModelScope.launch { followUpManager.complete(id) } }

    private companion object {
        const val UPCOMING_LIMIT = 3
        const val RECENT_LIMIT = 8
    }
}
