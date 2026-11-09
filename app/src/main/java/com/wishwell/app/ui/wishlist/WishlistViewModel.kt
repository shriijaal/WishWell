package com.wishwell.app.ui.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wishwell.app.data.model.CategoryWithCount
import com.wishwell.app.data.model.ItemWithStats
import com.wishwell.app.di.AppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortMode(val label: String) {
    RECENT("Recently added"),
    BIGGEST_DROP("Biggest drop"),
    PRICE_LOW("Price: low to high"),
    PRICE_HIGH("Price: high to low"),
    NAME("Name A–Z")
}

enum class AttentionFilter(val label: String) {
    ALL("All items"),
    ON_SALE("On sale"),
    AT_LOW("All-time lows"),
    BELOW_TARGET("Below target"),
    CHECK_FAILED("Check failed")
}

data class WishlistSummary(
    val itemCount: Int,
    val currencyCode: String,
    val trackedValue: Double,
    val belowPeak: Double,
    val atLowCount: Int,
    val onSaleCount: Int,
    val belowTargetCount: Int,
    val checkFailedCount: Int,
    val mixedCurrencies: Boolean
)

data class WishlistUiState(
    val items: List<ItemWithStats> = emptyList(),
    val allItems: List<ItemWithStats> = emptyList(),
    val categories: List<CategoryWithCount> = emptyList(),
    val query: String = "",
    val categoryIds: Set<Long> = emptySet(),
    val sort: SortMode = SortMode.RECENT,
    val attentionFilter: AttentionFilter = AttentionFilter.ALL,
    val refreshing: Boolean = false,
    val message: String? = null,
    val loading: Boolean = true,
    val summary: WishlistSummary = WishlistSummary(0, "USD", 0.0, 0.0, 0, 0, 0, 0, false),
    val selectedIds: Set<Long> = emptySet(),
    val readNotificationKeys: Set<String> = emptySet()
) {
    val hasAnyItems: Boolean get() = allItems.isNotEmpty()
    val isFiltered: Boolean get() = query.isNotBlank() || categoryIds.isNotEmpty() || attentionFilter != AttentionFilter.ALL
    val selectionMode: Boolean get() = selectedIds.isNotEmpty()
}

private data class Filters(
    val query: String,
    val categoryIds: Set<Long>,
    val sort: SortMode,
    val attentionFilter: AttentionFilter
)

class WishlistViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val prefs = container.prefs

    private val query = MutableStateFlow("")
    private val categoryFilters = MutableStateFlow<Set<Long>>(emptySet())
    private val sort = MutableStateFlow(SortMode.RECENT)
    private val attentionFilter = MutableStateFlow(AttentionFilter.ALL)
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    private val filters = combine(query, categoryFilters, sort, attentionFilter) { q, c, s, a ->
        Filters(q, c, s, a)
    }
    private val status = combine(refreshing, message, prefs.readNotificationKeys) { r, m, read -> Triple(r, m, read) }

    val uiState: StateFlow<WishlistUiState> = combine(
        repo.observeItems(),
        repo.observeCategories(),
        filters,
        status,
        selectedIds
    ) { items, categories, f, s, selected ->
        val filtered = items.filter { item ->
            val matchesQuery = f.query.isBlank() || listOf(
                item.item.title,
                item.item.storeName,
                item.item.notes,
                item.item.brand.orEmpty(),
                item.categoryName.orEmpty()
            ).any { it.contains(f.query, ignoreCase = true) }

            val matchesCategory = when {
                f.categoryIds.isEmpty() -> true
                else -> {
                    val itemCat = item.item.categoryId
                    if (itemCat == null) f.categoryIds.contains(-1L)
                    else f.categoryIds.contains(itemCat)
                }
            }

            val matchesAttention = when (f.attentionFilter) {
                AttentionFilter.ALL -> true
                AttentionFilter.ON_SALE -> (item.discountPercent ?: 0) > 0 || item.isAtLowest
                AttentionFilter.AT_LOW -> item.isAtLowest
                AttentionFilter.BELOW_TARGET -> {
                    val t = item.item.targetPrice
                    val p = item.price
                    t != null && p != null && p <= t + 0.005
                }
                AttentionFilter.CHECK_FAILED -> item.item.lastCheckFailed
            }
            matchesQuery && matchesCategory && matchesAttention
        }

        val validIds = items.mapTo(mutableSetOf()) { it.item.id }
        val prunedSelected = selected.intersect(validIds)
        WishlistUiState(
            items = sortItems(filtered, f.sort),
            allItems = items,
            categories = categories,
            query = f.query,
            categoryIds = f.categoryIds,
            sort = f.sort,
            attentionFilter = f.attentionFilter,
            refreshing = s.first,
            message = s.second,
            loading = false,
            summary = summarize(items),
            selectedIds = prunedSelected,
            readNotificationKeys = s.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WishlistUiState()
    )

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleCategory(id: Long) {
        if (id == -999L) {
            // sentinel: clear all category filters
            categoryFilters.value = emptySet()
            return
        }
        val current = categoryFilters.value.toMutableSet()
        if (!current.add(id)) current.remove(id)
        categoryFilters.value = current
    }

    fun clearCategories() {
        categoryFilters.value = emptySet()
    }

    fun setSort(mode: SortMode) {
        sort.value = mode
    }

    fun setAttentionFilter(filter: AttentionFilter) {
        attentionFilter.value = filter
    }

    fun clearFilters() {
        query.value = ""
        categoryFilters.value = emptySet()
        attentionFilter.value = AttentionFilter.ALL
    }

    fun clearMessage() {
        message.value = null
    }

    fun markAllNotificationsRead(keys: Set<String>) {
        if (keys.isEmpty()) return
        viewModelScope.launch { prefs.markNotificationsRead(keys) }
    }

    private fun postMessage(text: String?, autoHideMs: Long = 4000L) {
        message.value = text
        if (text != null) {
            viewModelScope.launch {
                delay(autoHideMs)
                if (message.value == text) message.value = null
            }
        }
    }

    fun enterSelection(id: Long) {
        selectedIds.value = setOf(id)
    }

    fun toggleSelection(id: Long) {
        val current = selectedIds.value.toMutableSet()
        if (!current.add(id)) current.remove(id)
        selectedIds.value = current
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun selectAll(ids: Collection<Long>) {
        selectedIds.value = ids.toSet()
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            runCatching { repo.deleteItem(id) }
                .onFailure { message.value = it.message ?: "Couldn't remove that item." }
            selectedIds.value = selectedIds.value - id
        }
    }

    fun deleteSelected() {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { repo.deleteItems(ids) }
                .onSuccess { postMessage("Removed ${ids.size} item${if (ids.size == 1) "" else "s"}.") }
                .onFailure { postMessage(it.message ?: "Couldn't remove selected items.") }
            selectedIds.value = emptySet()
        }
    }

    fun moveSelectedToCategory(categoryId: Long?) {
        val ids = selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { repo.setCategoryForItems(ids, categoryId) }
                .onSuccess { postMessage("Moved ${ids.size} item${if (ids.size == 1) "" else "s"}.") }
                .onFailure { postMessage(it.message ?: "Couldn't move selected items.") }
            selectedIds.value = emptySet()
        }
    }

    fun reorderSelected(direction: Int, orderedVisibleIds: List<Long>) {
        val ids = selectedIds.value
        if (ids.size != 1) {
            postMessage("Select one item to reorder.")
            return
        }
        val id = ids.first()
        viewModelScope.launch {
            val moved = runCatching { repo.reorderItems(id, direction, orderedVisibleIds) }.getOrDefault(false)
            if (!moved) postMessage("Can't move that item further.")
        }
    }

    fun persistVisibleOrder(orderedIds: List<Long>) {
        viewModelScope.launch {
            runCatching { repo.persistItemOrder(orderedIds) }
                .onSuccess { postMessage("Order saved.") }
                .onFailure { postMessage(it.message ?: "Couldn't save order.") }
        }
    }

    fun refreshAll() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val summary = repo.refreshAll()
            postMessage(
                when {
                    summary.checked == 0 -> "Nothing saved yet — add something to watch."
                    summary.failed == 0 && summary.updated == 0 ->
                        "Checked ${summary.checked} items. Prices are unchanged."
                    summary.failed == 0 ->
                        "Checked ${summary.checked} · ${summary.updated} updated" +
                            if (summary.lowCount > 0) " · ${summary.lowCount} at an all-time low" else ""
                    summary.failed == summary.checked ->
                        "Couldn't read any store pages. Many retailers block price checks."
                    else ->
                        "Checked ${summary.checked} · ${summary.failed} couldn't be read"
                }
            )
            refreshing.value = false
        }
    }

    fun loadDemoData() {
        viewModelScope.launch {
            val added = repo.seedDemoData()
            postMessage(
                if (added) {
                    "Demo list added. Delete it any time in Settings."
                } else {
                    "You already have items saved."
                }
            )
        }
    }

    private fun summarize(items: List<ItemWithStats>): WishlistSummary {
        if (items.isEmpty()) return WishlistSummary(0, "USD", 0.0, 0.0, 0, 0, 0, 0, false)
        val currency = items
            .groupingBy { it.item.currencyCode }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: "USD"
        val priced = items.filter { it.item.currencyCode == currency && it.price != null }
        val tracked = priced.sumOf { it.price ?: 0.0 }
        val belowPeak = priced.sumOf { item ->
            val peak = item.peak ?: return@sumOf 0.0
            (peak - (item.price ?: peak)).coerceAtLeast(0.0)
        }
        val belowTargetCount = items.count { item ->
            val t = item.item.targetPrice
            val p = item.price
            t != null && p != null && p <= t + 0.005
        }
        return WishlistSummary(
            itemCount = items.size,
            currencyCode = currency,
            trackedValue = tracked,
            belowPeak = belowPeak,
            atLowCount = items.count { it.isAtLowest },
            onSaleCount = items.count { (it.discountPercent ?: 0) > 0 || it.isAtLowest },
            belowTargetCount = belowTargetCount,
            checkFailedCount = items.count { it.item.lastCheckFailed },
            mixedCurrencies = items.map { it.item.currencyCode }.distinct().size > 1
        )
    }

    private fun sortItems(items: List<ItemWithStats>, mode: SortMode): List<ItemWithStats> =
        when (mode) {
            SortMode.RECENT -> items.sortedByDescending { it.item.createdAt }
            SortMode.BIGGEST_DROP -> items.sortedByDescending { it.discountPercent ?: -1 }
            SortMode.PRICE_LOW -> items.sortedWith(
                compareBy<ItemWithStats, Double?>(nullsLast()) { it.price }
            )

            SortMode.PRICE_HIGH -> items.sortedWith(
                compareByDescending<ItemWithStats, Double?>(nullsFirst()) { it.price }
            )

            SortMode.NAME -> items.sortedBy { it.item.title.lowercase() }
        }
}
