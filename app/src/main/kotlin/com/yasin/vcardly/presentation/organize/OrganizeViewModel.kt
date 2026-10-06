package com.yasin.vcardly.presentation.organize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.model.CategoryPalette
import com.yasin.vcardly.domain.model.TagWithCount
import com.yasin.vcardly.domain.repository.CategoryRepository
import com.yasin.vcardly.domain.repository.ContactRepository
import com.yasin.vcardly.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OrganizeUiState(
    val categories: List<Category> = emptyList(),
    val tags: List<TagWithCount> = emptyList(),
    /** Contacts per category id (null = no category). */
    val categoryCounts: Map<Long?, Int> = emptyMap(),
    val loaded: Boolean = false,
)

enum class NameError { BLANK, TOO_LONG, DUPLICATE }

@HiltViewModel
class OrganizeViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    contactRepository: ContactRepository,
) : ViewModel() {

    val uiState: StateFlow<OrganizeUiState> = combine(
        categoryRepository.observeAll(),
        tagRepository.observeAll(),
        contactRepository.observeStats(addedSince = 0),
    ) { cats, tags, stats -> OrganizeUiState(cats, tags, stats.byCategory.associate { it.categoryId to it.count }, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrganizeUiState())

    /** Creates (id = 0) or renames a custom category. Returns an error or null on success via [onResult]. */
    fun saveCategory(id: Long, name: String, onResult: (NameError?) -> Unit) {
        val clean = name.trim()
        val state = uiState.value
        val error = when {
            clean.isEmpty() -> NameError.BLANK
            clean.length > MAX_NAME -> NameError.TOO_LONG
            state.categories.any { it.id != id && it.systemCategory == null && it.name.equals(clean, ignoreCase = true) } ->
                NameError.DUPLICATE
            else -> null
        }
        if (error != null) return onResult(error)
        viewModelScope.launch {
            val existing = state.categories.firstOrNull { it.id == id }
            val category = existing?.copy(name = clean)
                ?: Category(name = clean, colorArgb = CategoryPalette.colorFor(state.categories.size))
            categoryRepository.save(category)
            onResult(null)
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch { categoryRepository.delete(id) }
    }

    fun renameTag(id: Long, name: String, onResult: (NameError?) -> Unit) {
        val clean = name.trim().removePrefix("#").trim()
        val error = when {
            clean.isEmpty() -> NameError.BLANK
            clean.length > MAX_NAME -> NameError.TOO_LONG
            else -> null
        }
        if (error != null) return onResult(error)
        viewModelScope.launch {
            onResult(if (tagRepository.rename(id, clean)) null else NameError.DUPLICATE)
        }
    }

    fun deleteTag(id: Long) {
        viewModelScope.launch { tagRepository.delete(id) }
    }

    private companion object {
        const val MAX_NAME = 40
    }
}
