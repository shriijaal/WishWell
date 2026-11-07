package com.wishwell.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wishwell.app.data.model.CategoryWithCount
import com.wishwell.app.data.model.ItemWithStats
import com.wishwell.app.data.model.PricePoint
import com.wishwell.app.data.net.CompareSearchState
import com.wishwell.app.data.net.StoreSearchLink
import com.wishwell.app.data.repo.RefreshResult
import com.wishwell.app.data.suggest.CrossStoreMatch
import com.wishwell.app.data.suggest.SimilarPick
import com.wishwell.app.di.AppContainer
import com.wishwell.app.util.formatMoney
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI

class ItemDetailViewModel(
    private val container: AppContainer,
    private val itemId: Long
) : ViewModel() {

    private val repo = container.repository

    val item: StateFlow<ItemWithStats?> = repo.observeItem(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<PricePoint>> = repo.observeHistory(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryWithCount>> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val similar: StateFlow<List<SimilarPick>> = combine(item, repo.observeItems()) { target, all ->
        if (target == null) emptyList() else container.recommendations.similarSaved(target, all)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val comparisons: StateFlow<List<StoreSearchLink>> = item.map { current ->
        if (current == null) emptyList() else container.recommendations.storeComparisons(current.item)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val crossStoreMatches: StateFlow<List<CrossStoreMatch>> = combine(item, repo.observeItems()) { target, all ->
        if (target == null) emptyList() else container.recommendations.crossStoreMatches(target, all)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _compareStates = MutableStateFlow<Map<String, CompareSearchState>>(emptyMap())
    val compareStates: StateFlow<Map<String, CompareSearchState>> = _compareStates.asStateFlow()

    /** Tracks auto-fetch attempts per store+query to avoid spam while allowing context-ready retries. */
    private val autoRequested = mutableSetOf<String>()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            combine(item, comparisons) { target, links -> target to links }
                .collect { (target, links) ->
                    if (target == null || links.isEmpty()) return@collect
                    val query = container.recommendations.searchQuery(target.item)
                    for (link in links.take(12)) {
                        val reqKey = requestKey(link.storeName, query)
                        if (reqKey in autoRequested) continue
                        val cur = _compareStates.value[link.storeName]
                        if (cur is CompareSearchState.Loading) continue
                        // Auto-fetch when missing, or retry once when previous attempt found nothing/errored
                        // and we now have full target context (avoids the early item-null race).
                        val shouldFetch = cur == null ||
                            cur is CompareSearchState.Empty ||
                            cur is CompareSearchState.Error
                        if (!shouldFetch) {
                            autoRequested += reqKey
                            continue
                        }
                        autoRequested += reqKey
                        fetchStoreProducts(link, target, query)
                        delay(450)
                    }
                }
        }
    }

    private fun requestKey(storeName: String, query: String): String =
        "$storeName|${query.lowercase().trim()}"

    fun refresh() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            when (val result = repo.refreshItem(itemId)) {
                is RefreshResult.Updated -> {
                    val price = formatMoney(result.newPrice, result.currencyCode)
                    _message.value = if (result.isAllTimeLow) {
                        "Checked · $price — a new all-time low"
                    } else {
                        "Checked · now $price"
                    }
                }

                is RefreshResult.Failed -> _message.value = result.message
                RefreshResult.NotFound -> _message.value = "That item is no longer saved."
            }
            _busy.value = false
        }
    }

    fun setTargetPrice(value: Double?) {
        viewModelScope.launch {
            repo.setTargetPrice(itemId, value)
            _message.value = if (value == null) {
                "Target price cleared."
            } else {
                val currency = item.value?.item?.currencyCode ?: "USD"
                "Target set to ${formatMoney(value, currency)}."
            }
        }
    }

    fun setCategory(categoryId: Long?) {
        viewModelScope.launch { repo.setCategory(itemId, categoryId) }
    }

    fun setNotes(notes: String) {
        viewModelScope.launch {
            repo.setNotes(itemId, notes)
            _message.value = "Note saved."
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.deleteItem(itemId)
            onDeleted()
        }
    }

    fun parsePrice(text: String): Double? = container.repository.parsePriceInput(text)

    fun importHistory(text: String) {
        viewModelScope.launch {
            val added = runCatching { repo.importHistoryFromText(itemId, text) }.getOrElse {
                _message.value = it.message ?: "Couldn't import that history."
                return@launch
            }
            _message.value = if (added > 0) "Imported $added price point(s)." else "No new price points found to import."
        }
    }

    fun findStoreProducts(link: StoreSearchLink) {
        val key = link.storeName
        val current = _compareStates.value[key]
        if (current is CompareSearchState.Loading) return
        viewModelScope.launch {
            val target = item.value
            val query = target?.let { container.recommendations.searchQuery(it.item) }.orEmpty()
            fetchStoreProducts(link, target, query)
        }
    }

    private suspend fun fetchStoreProducts(
        link: StoreSearchLink,
        target: ItemWithStats?,
        @Suppress("UNUSED_PARAMETER") query: String
    ) {
        val key = link.storeName
        val current = _compareStates.value[key]
        if (current is CompareSearchState.Loading) return
        val host = runCatching { URI(link.url).host }.getOrNull()
        val currency = when {
            !target?.item?.currencyCode.isNullOrBlank() -> target!!.item.currencyCode
            host != null -> container.storeRegistry.defaultCurrencyFor(host)
            else -> "USD"
        }
        _compareStates.update { it + (key to CompareSearchState.Loading) }
        val products = runCatching {
            container.storeSearchScraper.search(
                storeName = link.storeName,
                searchUrl = link.url,
                currencyCode = currency,
                targetTitle = target?.item?.title,
                targetBrand = target?.item?.brand,
                targetCategoryHint = target?.categoryName
            )
        }.getOrDefault(emptyList())
        val state = if (products.isNotEmpty()) {
            CompareSearchState.Success(products)
        } else {
            CompareSearchState.Empty("No closely matching live results found. Open search in-app to compare.")
        }
        _compareStates.update { it + (key to state) }
    }

    fun clearMessage() {
        _message.value = null
    }
}
