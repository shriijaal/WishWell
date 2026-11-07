package com.wishwell.app.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wishwell.app.data.model.ItemWithStats
import com.wishwell.app.di.AppContainer
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
    val biggestDrops: List<ItemWithStats> = emptyList(),
    val checkFailed: List<ItemWithStats> = emptyList(),
    val refreshing: Boolean = false,
    val message: String? = null,
    val loading: Boolean = true,
    val summary: TrackingSummary = TrackingSummary()
) {
    val hasAnyItems: Boolean get() = allItems.isNotEmpty()
    val hasSignals: Boolean get() =
        atLow.isNotEmpty() || targetReached.isNotEmpty() || biggestDrops.isNotEmpty() || checkFailed.isNotEmpty()
}

class PriceTrackingViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PriceTrackingUiState> = combine(
        repo.observeItems(),
        refreshing,
        message
    ) { items, isRefreshing, msg ->
        val atLow = items.filter { it.isAtLowest }.sortedByDescending { it.item.updatedAt }
        val targetReached = items.filter { item ->
            val target = item.item.targetPrice
            val price = item.price
            target != null && price != null && price <= target + 0.005
        }.sortedBy { (it.price ?: Double.MAX_VALUE) - (it.item.targetPrice ?: 0.0) }
        val biggestDrops = items
            .filter { (it.discountPercent ?: 0) >= 1 }
            .sortedByDescending { it.discountPercent ?: 0 }
            .take(12)
        val checkFailed = items.filter { it.item.lastCheckFailed }

        PriceTrackingUiState(
            allItems = items,
            atLow = atLow,
            targetReached = targetReached,
            biggestDrops = biggestDrops,
            checkFailed = checkFailed,
            refreshing = isRefreshing,
            message = msg,
            loading = false,
            summary = summarize(items)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PriceTrackingUiState()
    )

    fun clearMessage() {
        message.value = null
    }

    fun refreshAll() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val summary = repo.refreshAll()
            message.value = when {
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
