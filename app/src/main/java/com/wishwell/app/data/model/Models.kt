package com.wishwell.app.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.math.roundToInt

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val colorArgb: Int,
    val emoji: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/** Junction table for the many-to-many item <-> category (tag) relationship. */
@Entity(
    tableName = "item_category_tags",
    primaryKeys = ["itemId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = WishItem::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId")]
)
data class ItemCategoryTag(
    val itemId: Long,
    val categoryId: Long
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("categoryId"), Index("isArchived"), Index(value = ["isArchived", "createdAt"])]
)
data class WishItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val storeName: String,
    val productUrl: String,
    val imageUrl: String? = null,
    val imageUrlsJson: String = "[]",
    val currencyCode: String = "USD",
    val currentPrice: Double? = null,
    val listPrice: Double? = null,
    val targetPrice: Double? = null,
    /** Primary (first) tag, kept in sync with item_category_tags for single-tag display paths. */
    val categoryId: Long? = null,
    val notes: String = "",
    val description: String = "",
    val specsJson: String = "[]",
    val brand: String? = null,
    val isArchived: Boolean = false,
    /** Whether the item has been bought. Purchased items are hidden from the active list by default. */
    val isPurchased: Boolean = false,
    /** Timestamp when the item was marked as bought. */
    val purchasedAt: Long? = null,
    /** Best-effort stock state: true = in stock, false = out of stock, null = unknown. */
    val inStock: Boolean? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastCheckedAt: Long? = null,
    val lastCheckFailed: Boolean = false
) {
    fun imageUrls(): List<String> {
        val parsed = runCatching {
            val arr = org.json.JSONArray(imageUrlsJson)
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
        }.getOrDefault(emptyList())
        if (parsed.isNotEmpty()) return parsed
        return imageUrl?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
    }

    fun specs(): List<Pair<String, String>> = runCatching {
        val arr = org.json.JSONArray(specsJson)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val k = o.optString("k").trim()
            val v = o.optString("v").trim()
            if (k.isNotEmpty() && v.isNotEmpty()) k to v else null
        }
    }.getOrDefault(emptyList())

    companion object {
        fun encodeImageUrls(urls: List<String>): String =
            org.json.JSONArray(urls.filter { it.isNotBlank() }.distinct().take(12)).toString()

        fun encodeSpecs(specs: List<Pair<String, String>>): String {
            val arr = org.json.JSONArray()
            specs.filter { it.first.isNotBlank() && it.second.isNotBlank() }
                .distinctBy { it.first.lowercase() }
                .take(60)
                .forEach { (k, v) ->
                    arr.put(org.json.JSONObject().put("k", k.take(80)).put("v", v.take(240)))
                }
            return arr.toString()
        }
    }
}

@Entity(
    tableName = "price_points",
    foreignKeys = [
        ForeignKey(
            entity = WishItem::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("itemId"), Index(value = ["itemId", "recordedAt"])]
)
data class PricePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val itemId: Long,
    val price: Double,
    val currencyCode: String = "USD",
    val recordedAt: Long = System.currentTimeMillis()
)

/** A wishlist item joined with its price-history aggregates. */
data class ItemWithStats(
    @Embedded val item: WishItem,
    val lowest: Double? = null,
    val highest: Double? = null,
    val average: Double? = null,
    val firstPrice: Double? = null,
    val latestPrice: Double? = null,
    val pointCount: Int = 0,
    val categoryName: String? = null,
    val categoryColor: Int? = null
) {
    val price: Double? get() = item.currentPrice ?: latestPrice

    /** Reference price to compare against: the observed high, the store list price, or both. */
    val peak: Double?
        get() = listOfNotNull(highest, item.listPrice?.takeIf { it > 0.0 }).maxOrNull()

    /** True when the store currently shows a struck-through list price above the sale price. */
    val isOnSale: Boolean
        get() {
            val p = price ?: return false
            val list = item.listPrice ?: return false
            return list > p + 0.01
        }

    /** How far below the current reference price the item sits, in percent. */
    val discountPercent: Int?
        get() {
            val p = price ?: return null
            // Prefer the store's current struck-through price (a real sale). Only fall back to the
            // observed peak when the history actually moved, so flat/noisy readings never produce
            // a phantom discount badge.
            val list = item.listPrice?.takeIf { it > p + 0.01 }
            val historyPeak = highest?.takeIf { hasPriceVariation && it > p + 0.01 }
            val top = list ?: historyPeak ?: return null
            if (top <= 0.0 || p >= top) return null
            return (((top - p) / top) * 100).roundToInt().coerceIn(1, 99)
        }

    val hasHistory: Boolean get() = pointCount >= 3

    /**
     * True only when the recorded history contains an actual price movement. Repeated identical
     * readings (a flat price that is merely re-checked over time) are not a low/high signal —
     * otherwise every never-discounted item would sit permanently at its "all-time low".
     */
    val hasPriceVariation: Boolean
        get() {
            val low = lowest ?: return false
            val high = highest ?: return false
            val spread = high - low
            if (spread <= 0.01) return false
            // Ignore tiny scraping noise; require at least 0.5% of the peak.
            return spread >= high * 0.005
        }

    val isAtLowest: Boolean
        get() {
            val p = price ?: return false
            val low = lowest ?: return false
            return hasHistory && hasPriceVariation && p <= low + 0.005
        }

    val isAtHighest: Boolean
        get() {
            val p = price ?: return false
            val top = highest ?: return false
            return hasHistory && hasPriceVariation && p >= top - 0.005
        }

    /** 0f = at the all-time low, 1f = at the all-time high. */
    val rangePosition: Float?
        get() {
            val p = price ?: return null
            val low = lowest ?: return null
            val high = highest ?: return null
            if (high - low < 0.01) return null
            return ((p - low) / (high - low)).toFloat().coerceIn(0f, 1f)
        }
}

data class CategoryWithCount(
    @Embedded val category: Category,
    val itemCount: Int = 0,
    val totalValue: Double = 0.0
)
