package com.yasin.vcardly.presentation.followups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.domain.model.FollowUpBucket
import com.yasin.vcardly.domain.model.FollowUpCounts
import com.yasin.vcardly.domain.model.FollowUpWithContact
import com.yasin.vcardly.domain.repository.FollowUpRepository
import com.yasin.vcardly.domain.usecase.FollowUpManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FollowUpsUiState(
    val isLoading: Boolean = true,
    val bucket: FollowUpBucket = FollowUpBucket.TODAY,
    val counts: FollowUpCounts = FollowUpCounts(0, 0, 0, 0),
    val items: List<FollowUpWithContact> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FollowUpsViewModel @Inject constructor(
    repository: FollowUpRepository,
    private val manager: FollowUpManager,
) : ViewModel() {
    private val bucket = MutableStateFlow(FollowUpBucket.TODAY)

    val uiState: StateFlow<FollowUpsUiState> = combine(
        bucket,
        repository.observeCounts(),
        bucket.flatMapLatest { repository.observe(it) },
    ) { b, counts, items ->
        FollowUpsUiState(isLoading = false, bucket = b, counts = counts, items = items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FollowUpsUiState())

    fun select(b: FollowUpBucket) { bucket.value = b }
    fun complete(id: Long) { viewModelScope.launch { manager.complete(id) } }
    fun reopen(id: Long) { viewModelScope.launch { manager.reopen(id) } }
    fun delete(id: Long) { viewModelScope.launch { manager.delete(id) } }
}
