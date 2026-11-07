package com.wishwell.app.widget

import android.content.Context
import com.wishwell.app.WishwellApp
import com.wishwell.app.data.model.ItemWithStats
import com.wishwell.app.util.formatMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class WidgetEntry(
    val id: Long,
    val title: String,
    val storeName: String,
    val priceText: String,
    val badge: String?,
    val isDrop: Boolean
)

internal object WidgetData {

    suspend fun load(context: Context, dropsOnly: Boolean, limit: Int): List<WidgetEntry> =
        withContext(Dispatchers.IO) {
            runCatching {
                val app = context.applicationContext as? WishwellApp ?: return@runCatching emptyList()
                val items = app.container.repository.getAllItems()
                    .asSequence()
                    .filter { !it.item.isArchived }
                    .let { seq ->
                        if (dropsOnly) {
                            seq.filter { (it.discountPercent ?: 0) > 0 || it.isAtLowest }
                                .sortedWith(
                                    compareByDescending<ItemWithStats> { it.isAtLowest }
                                        .thenByDescending { it.discountPercent ?: 0 }
                                        .thenByDescending { it.item.updatedAt }
                                )
                        } else {
                            seq.sortedByDescending { maxOf(it.item.updatedAt, it.item.createdAt) }
                        }
                    }
                    .take(limit)
                    .toList()
                items.map { it.toEntry() }
            }.getOrDefault(emptyList())
        }

    private fun ItemWithStats.toEntry(): WidgetEntry {
        val badge = when {
            isAtLowest -> "LOW"
            (discountPercent ?: 0) > 0 -> "-${discountPercent}%"
            else -> null
        }
        return WidgetEntry(
            id = item.id,
            title = item.title.ifBlank { item.storeName }.take(80),
            storeName = item.storeName.take(40),
            priceText = formatMoney(price, item.currencyCode),
            badge = badge,
            isDrop = (discountPercent ?: 0) > 0 || isAtLowest
        )
    }
}
