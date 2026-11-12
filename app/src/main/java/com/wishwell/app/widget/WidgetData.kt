package com.wishwell.app.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
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
    val isDrop: Boolean,
    val imageUrl: String? = null,
    val image: Bitmap? = null
)

internal object WidgetData {

    suspend fun load(
        context: Context,
        dropsOnly: Boolean = false,
        sortOrder: String = "recent",
        limit: Int = 8,
        loadImages: Boolean = false
    ): List<WidgetEntry> =
        withContext(Dispatchers.IO) {
            runCatching {
                val app = context.applicationContext as? WishwellApp ?: return@runCatching emptyList()
                val items = app.container.repository.getAllItems()
                    .asSequence()
                    .filter { !it.item.isArchived }
                    .let { seq ->
                        val filtered = if (dropsOnly) {
                            seq.filter { (it.discountPercent ?: 0) > 0 || it.isAtLowest }
                        } else {
                            seq
                        }
                        when (sortOrder) {
                            "name" -> filtered.sortedBy { it.item.title }
                            "price" -> filtered.sortedByDescending { it.price }
                            "discount" -> filtered.sortedByDescending {
                                val discount = it.discountPercent ?: 0
                                if (it.isAtLowest) maxOf(discount, 1) else discount
                            }
                            else -> filtered.sortedByDescending { maxOf(it.item.updatedAt, it.item.createdAt) }
                        }
                    }
                    .take(limit)
                    .toList()
                val entries = items.map { it.toEntry() }
                if (!loadImages) {
                    entries
                } else {
                    entries.map { it.withImage(context) }
                }
            }.getOrDefault(emptyList())
        }

    private suspend fun WidgetEntry.withImage(context: Context): WidgetEntry {
        val url = imageUrl ?: return this
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(THUMB_SIZE_PX)
            .allowHardware(false)
            .build()
        val bitmap = runCatching {
            (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
        }.getOrNull()
        return copy(image = bitmap)
    }

    private const val THUMB_SIZE_PX = 128

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
            isDrop = (discountPercent ?: 0) > 0 || isAtLowest,
            imageUrl = item.imageUrls().firstOrNull()
        )
    }
}
