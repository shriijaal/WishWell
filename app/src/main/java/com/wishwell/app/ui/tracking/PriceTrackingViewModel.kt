package com.wishwell.app.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wishwell.app.data.model.ItemWithStats
import com.wishwell.app.di.AppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrackingSummary(
    val trackedCount: Int = 0,
    val atLowCount: Int = 0,
    val targetReachedCount: Int = 0,
    val checkFailedCount: Int = 0,
    val belowPeakTotal: Double = 0.0,
    val currencyCode: String = "USD",
    val mixedCurrencies: Boolean = false
)

data class PriceTrackingUiState(
    val allItems: List<ItemWithStats> = emptyList(),
    val atLow: List<ItemWithStats> = emptyList(),
    val targetReached: List<ItemWithStats> = emptyList(),
    val onSale: List<ItemWithStats> = emptyList(),
    val checkFailed: List<ItemWithStats> = emptyList(),
    val refreshing: Boolean = false,
    val message: String? = null,
    val loading: Boolean = true,
    val summary: TrackingSummary = TrackingSummary(),
    val selectedIds: Set<Long> = emptySet(),
    val categories: List<com.wishwell.app.data.model.CategoryWithCount> = emptyList()
) {
    val hasAnyItems: Boolean get() = allItems.isNotEmpty()
    val hasSignals: Boolean get() =
        atLow.isNotEmpty() || targetReached.isNotEmpty() || onSale.isNotEmpty() || checkFailed.isNotEmpty()
    val selectionMode: Boolean get() = selectedIds.isNotEmpty()
}

class PriceTrackingViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<PriceTrackingUiState> = combine(
        repo.observeItems(),
        repo.observeCategories(),
        refreshing,
        message,
        selectedIds
    ) { items, categories, isRefreshing, msg, selected ->
        val validIds = items.mapTo(mutableSetOf()) { it.item.id }
        val prunedSelected = selected.intersect(validIds)
        val atLow = items.filter { it.isAtLowest }.sortedByDescending { it.item.updatedAt }
        val targetReached = items.filter { item ->
            val target = item.item.targetPrice
            val price = item.price
            target != null && price != null && price <= target + 0.005
        }.sortedBy { (it.price ?: Double.MAX_VALUE) - (it.item.targetPrice ?: 0.0) }
        val featuredIds = (atLow + targetReached).mapTo(mutableSetOf()) { it.item.id }
        val onSale = items
            .filter { it.item.id !in featuredIds && (it.isOnSale || (it.discountPercent ?: 0) >= 1) }
            .sortedWith(
                compareByDescending<ItemWithStats> { it.discountPercent ?: 0 }
                    .thenByDescending { item ->
                        val top = item.peak ?: return@thenByDescending 0.0
                        (top - (item.price ?: top)).coerceAtLeast(0.0)
                    }
            )
        val checkFailed = items.filter { it.item.lastCheckFailed }

        PriceTrackingUiState(
            allItems = items,
            atLow = atLow,
            targetReached = targetReached,
            onSale = onSale,
            checkFailed = checkFailed,
            refreshing = isRefreshing,
            message = msg,
            loading = false,
            summary = summarize(items),
            selectedIds = prunedSelected,
            categories = categories
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PriceTrackingUiState()
    )

    fun clearMessage() {
        message.value = null
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

    fun enterSelection(id: Long) { selectedIds.value = setOf(id) }

    fun toggleSelection(id: Long) {
        val current = selectedIds.value.toMutableSet()
        if (!current.add(id)) current.remove(id)
        selectedIds.value = current
    }

    fun clearSelection() { selectedIds.value = emptySet() }

    fun selectAll(ids: Collection<Long>) { selectedIds.value = ids.toSet() }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            runCatching { repo.deleteItem(id) }
                .onFailure { postMessage(it.message ?: "Couldn't remove that item.") }
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
                    summary.checked == 0 -> "Nothing saved yet — add something to track."
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

    private fun summarize(items: List<ItemWithStats>): TrackingSummary {
        if (items.isEmpty()) return TrackingSummary()
        val currency = items
            .groupingBy { it.item.currencyCode }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: "USD"
        val priced = items.filter { it.item.currencyCode == currency && it.price != null }
        val belowPeak = priced.sumOf { item ->
            val peak = item.peak ?: return@sumOf 0.0
            (peak - (item.price ?: peak)).coerceAtLeast(0.0)
        }
        val targetReached = items.count { item ->
            val target = item.item.targetPrice
            val price = item.price
            target != null && price != null && price <= target + 0.005
        }
        return TrackingSummary(
            trackedCount = items.size,
            atLowCount = items.count { it.isAtLowest },
            targetReachedCount = targetReached,
            checkFailedCount = items.count { it.item.lastCheckFailed },
            belowPeakTotal = belowPeak,
            currencyCode = currency,
            mixedCurrencies = items.map { it.item.currencyCode }.distinct().size > 1
        )
    }
}
