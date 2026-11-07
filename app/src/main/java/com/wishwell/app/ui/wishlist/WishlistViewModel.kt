package com.wishwell.app.ui.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wishwell.app.data.model.CategoryWithCount
import com.wishwell.app.data.model.ItemWithStats
import com.wishwell.app.di.AppContainer
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
    val categoryId: Long? = null,
    val sort: SortMode = SortMode.RECENT,
    val attentionFilter: AttentionFilter = AttentionFilter.ALL,
    val refreshing: Boolean = false,
    val message: String? = null,
    val loading: Boolean = true,
    val summary: WishlistSummary = WishlistSummary(0, "USD", 0.0, 0.0, 0, 0, 0, 0, false)
) {
    val hasAnyItems: Boolean get() = allItems.isNotEmpty()
    val isFiltered: Boolean get() = query.isNotBlank() || categoryId != null || attentionFilter != AttentionFilter.ALL
}

private data class Filters(
    val query: String,
    val categoryId: Long?,
    val sort: SortMode,
    val attentionFilter: AttentionFilter
)

class WishlistViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository

    private val query = MutableStateFlow("")
    private val categoryFilter = MutableStateFlow<Long?>(null)
    private val sort = MutableStateFlow(SortMode.RECENT)
    private val attentionFilter = MutableStateFlow(AttentionFilter.ALL)
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val filters = combine(query, categoryFilter, sort, attentionFilter) { q, c, s, a ->
        Filters(q, c, s, a)
    }
    private val status = combine(refreshing, message) { r, m -> r to m }

    val uiState: StateFlow<WishlistUiState> = combine(
        repo.observeItems(),
        repo.observeCategories(),
        filters,
        status
    ) { items, categories, f, s ->
        val filtered = items.filter { item ->
            val matchesQuery = f.query.isBlank() || listOf(
                item.item.title,
                item.item.storeName,
                item.item.notes,
                item.item.brand.orEmpty(),
                item.categoryName.orEmpty()
            ).any { it.contains(f.query, ignoreCase = true) }

            val matchesCategory = when (f.categoryId) {
                null -> true
                -1L -> item.item.categoryId == null
                else -> item.item.categoryId == f.categoryId
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

        WishlistUiState(
            items = sortItems(filtered, f.sort),
            allItems = items,
            categories = categories,
            query = f.query,
            categoryId = f.categoryId,
            sort = f.sort,
            attentionFilter = f.attentionFilter,
            refreshing = s.first,
            message = s.second,
            loading = false,
            summary = summarize(items)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WishlistUiState()
    )

    fun setQuery(value: String) {
        query.value = value
    }

    fun setCategory(id: Long?) {
        categoryFilter.value = id
    }

    fun setSort(mode: SortMode) {
        sort.value = mode
    }

    fun setAttentionFilter(filter: AttentionFilter) {
        attentionFilter.value = filter
    }

    fun clearFilters() {
        query.value = ""
        categoryFilter.value = null
        attentionFilter.value = AttentionFilter.ALL
    }

    fun clearMessage() {
        message.value = null
    }

    fun refreshAll() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val summary = repo.refreshAll()
            message.value = when {
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
            refreshing.value = false
        }
    }

    fun loadDemoData() {
        viewModelScope.launch {
            val added = repo.seedDemoData()
            message.value = if (added) {
                "Demo list added. Delete it any time in Settings."
            } else {
                "You already have items saved."
            }
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
