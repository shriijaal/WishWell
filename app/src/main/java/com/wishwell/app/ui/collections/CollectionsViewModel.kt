package com.wishwell.app.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wishwell.app.data.model.Category
import com.wishwell.app.data.model.CategoryWithCount
import com.wishwell.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CollectionsUiState(
    val categories: List<CategoryWithCount> = emptyList(),
    val message: String? = null,
    val loading: Boolean = true
)

class CollectionsViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<CollectionsUiState> = combine(
        repo.observeCategories(),
        message
    ) { categories, notice ->
        CollectionsUiState(categories = categories, message = notice, loading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CollectionsUiState()
    )

    fun add(name: String, colorArgb: Int, emoji: String?) {
        viewModelScope.launch {
            runCatching { repo.addCategory(name, colorArgb, emoji) }
                .onFailure { message.value = it.message ?: "Couldn't create that collection." }
        }
    }

    fun update(category: Category, name: String, colorArgb: Int, emoji: String?) {
        viewModelScope.launch {
            runCatching { repo.updateCategory(category, name, colorArgb, emoji) }
                .onFailure { message.value = it.message ?: "Couldn't save that collection." }
        }
    }

    fun delete(category: Category) {
        viewModelScope.launch {
            repo.deleteCategory(category)
            message.value = "Removed \"${category.name}\". Its items moved to Unsorted."
        }
    }

    fun clearMessage() {
        message.value = null
    }
}
