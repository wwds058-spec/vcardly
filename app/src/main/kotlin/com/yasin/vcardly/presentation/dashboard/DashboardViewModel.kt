package com.yasin.vcardly.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.domain.model.FollowUpCounts
import com.yasin.vcardly.domain.repository.CategoryRepository
import com.yasin.vcardly.domain.repository.ContactRepository
import com.yasin.vcardly.domain.repository.FollowUpRepository
import com.yasin.vcardly.domain.usecase.CategoryBreakdownItem
import com.yasin.vcardly.domain.usecase.buildCategoryBreakdown
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val isLoading: Boolean = true,
    val totalContacts: Int = 0,
    val favorites: Int = 0,
    val addedRecently: Int = 0,
    val followUps: FollowUpCounts = FollowUpCounts(0, 0, 0, 0),
    val categories: List<CategoryBreakdownItem> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && totalContacts == 0
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    contacts: ContactRepository,
    categories: CategoryRepository,
    followUps: FollowUpRepository,
    clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        contacts.observeStats(addedSince = clock.millis() - RECENT_WINDOW_MILLIS),
        categories.observeAll(),
        followUps.observeCounts(),
    ) { stats, allCategories, counts ->
        DashboardUiState(
            isLoading = false,
            totalContacts = stats.total,
            favorites = stats.favorites,
            addedRecently = stats.addedSince,
            followUps = counts,
            categories = buildCategoryBreakdown(stats.byCategory, allCategories),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private companion object {
        val RECENT_WINDOW_MILLIS = TimeUnit.DAYS.toMillis(30)
    }
}
