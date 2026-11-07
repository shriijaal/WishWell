package com.wishwell.app.data.repo

import android.content.Context
import com.wishwell.app.data.db.CategoryDao
import com.wishwell.app.data.db.ItemDao
import com.wishwell.app.data.model.Category
import com.wishwell.app.data.model.CategoryWithCount
import com.wishwell.app.data.model.ItemWithStats
import com.wishwell.app.data.model.PricePoint
import com.wishwell.app.data.model.WishItem
import com.wishwell.app.data.net.HistoricalPrice
import com.wishwell.app.data.net.MoneyParser
import com.wishwell.app.data.net.ProductScraper
import com.wishwell.app.data.net.ScrapeResult
import com.wishwell.app.data.net.StoreRegistry
import com.wishwell.app.data.prefs.UserPreferences
import com.wishwell.app.notify.Notifier
import com.wishwell.app.util.Palette
import com.wishwell.app.widget.WishwellWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.URI
import kotlin.math.abs

data class NewItemDraft(
    val title: String,
    val storeName: String,
    val productUrl: String,
    val imageUrl: String?,
    val imageUrls: List<String> = emptyList(),
    val price: Double?,
    val listPrice: Double?,
    val currencyCode: String,
    val targetPrice: Double?,
    val categoryId: Long?,
    val notes: String,
    val brand: String?,
    val priceHistory: List<HistoricalPrice> = emptyList(),
    val description: String = "",
    val specs: List<Pair<String, String>> = emptyList()
)

sealed interface RefreshResult {
    data class Updated(
        val title: String,
        val oldPrice: Double?,
        val newPrice: Double,
        val isAllTimeLow: Boolean,
        val currencyCode: String
    ) : RefreshResult

    data class Failed(val message: String) : RefreshResult
    data object NotFound : RefreshResult
}

data class RefreshSummary(
    val checked: Int,
    val updated: Int,
    val failed: Int,
    val lowCount: Int
)

class WishlistRepository(
    private val itemDao: ItemDao,
    private val categoryDao: CategoryDao,
    private val scraper: ProductScraper,
    private val stores: StoreRegistry,
    private val prefs: UserPreferences,
    private val notifier: Notifier,
    private val appContext: Context
) {

    private suspend fun notifyWidgetsChanged() {
        runCatching { WishwellWidgetUpdater.updateAll(appContext) }
    }

    fun observeItems(): Flow<List<ItemWithStats>> = itemDao.observeActiveItems()

    fun observeCategories(): Flow<List<CategoryWithCount>> = categoryDao.observeAll()

    fun observeItem(id: Long): Flow<ItemWithStats?> = itemDao.observeItem(id)

    fun observeHistory(id: Long): Flow<List<PricePoint>> = itemDao.observeHistory(id)

    suspend fun getAllItems(): List<ItemWithStats> = withContext(Dispatchers.IO) { itemDao.getAllWithStats() }

    suspend fun fetchProduct(url: String): ScrapeResult = scraper.scrape(url)

    // ---------------------------------------------------------------- items

    suspend fun addItem(draft: NewItemDraft): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cleanTitle = draft.title.trim().take(180)
        require(cleanTitle.isNotEmpty()) { "Give the item a name first." }

        val cleanedUrl = draft.productUrl.trim()
        val storeName = draft.storeName.trim().ifBlank {
            runCatching { URI(cleanedUrl).host }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { stores.displayNameForHost(it) }
                ?: "Store"
        }

        val normalizedImages = draft.imageUrls.map { it.trim() }.filter { it.isNotEmpty() }.distinct().ifEmpty {
            listOfNotNull(draft.imageUrl?.trim()?.takeIf { it.isNotEmpty() })
        }
        val itemId = itemDao.insert(
            WishItem(
                title = cleanTitle,
                storeName = storeName,
                productUrl = cleanedUrl,
                imageUrl = normalizedImages.firstOrNull() ?: draft.imageUrl?.trim()?.takeIf { it.isNotEmpty() },
                imageUrlsJson = WishItem.encodeImageUrls(normalizedImages),
                currencyCode = draft.currencyCode.uppercase(),
                currentPrice = draft.price,
                listPrice = draft.listPrice,
                targetPrice = draft.targetPrice,
                categoryId = draft.categoryId,
                notes = draft.notes.trim(),
                description = draft.description.trim().take(4000),
                specsJson = WishItem.encodeSpecs(draft.specs),
                brand = draft.brand?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now,
                updatedAt = now,
                lastCheckedAt = if (draft.price != null) now else null
            )
        )

        val seeded = mutableListOf<Pair<Long, Double>>()
        mergeImportedHistory(itemId, draft.priceHistory, draft.currencyCode.uppercase(), now, seeded)
        draft.price?.let { price ->
            val dominated = seeded.any { (ts, p) -> abs(ts - now) < 6 * 60 * 60 * 1000L && abs(p - price) < 0.005 }
            if (!dominated) {
                itemDao.insertPoint(
                    PricePoint(
                        itemId = itemId,
                        price = price,
                        currencyCode = draft.currencyCode.uppercase(),
                        recordedAt = now
                    )
                )
            }
        }
        notifyWidgetsChanged()
        itemId
    }

    suspend fun setTargetPrice(id: Long, target: Double?) = withContext(Dispatchers.IO) {
        val item = itemDao.getById(id) ?: return@withContext
        itemDao.update(item.copy(targetPrice = target, updatedAt = System.currentTimeMillis()))
        notifyWidgetsChanged()
    }

    suspend fun setCategory(id: Long, categoryId: Long?) = withContext(Dispatchers.IO) {
        val item = itemDao.getById(id) ?: return@withContext
        itemDao.update(item.copy(categoryId = categoryId, updatedAt = System.currentTimeMillis()))
        notifyWidgetsChanged()
    }

    suspend fun setNotes(id: Long, notes: String) = withContext(Dispatchers.IO) {
        val item = itemDao.getById(id) ?: return@withContext
        itemDao.update(item.copy(notes = notes.trim().take(600), updatedAt = System.currentTimeMillis()))
        notifyWidgetsChanged()
    }

    suspend fun deleteItem(id: Long) = withContext(Dispatchers.IO) {
        val item = itemDao.getById(id)
        if (item != null) {
            itemDao.delete(item)
            notifyWidgetsChanged()
        }
    }

    // -------------------------------------------------------- price checks

    suspend fun refreshItem(id: Long): RefreshResult = withContext(Dispatchers.IO) {
        val item = itemDao.getById(id) ?: return@withContext RefreshResult.NotFound
        val now = System.currentTimeMillis()

        val result = scraper.scrape(item.productUrl)
        if (result is ScrapeResult.Failure) {
            itemDao.update(item.copy(lastCheckedAt = now, lastCheckFailed = true))
            notifyWidgetsChanged()
            return@withContext RefreshResult.Failed(result.message)
        }

        val metadata = (result as ScrapeResult.Success).metadata
        val newPrice = metadata.price
        if (newPrice == null) {
            itemDao.update(item.copy(lastCheckedAt = now, lastCheckFailed = true))
            notifyWidgetsChanged()
            return@withContext RefreshResult.Failed(
                "No price was visible on that page. Some stores hide prices until checkout."
            )
        }

        val previousLow = itemDao.minPrice(id)
        val lastPoint = itemDao.latestPoint(id)
        val historyBefore = itemDao.history(id)

        mergeImportedHistory(id, metadata.priceHistory, metadata.currencyCode ?: item.currencyCode, now, mutableListOf())

        val samePriceRecently = lastPoint != null &&
            abs(lastPoint.price - newPrice) < 0.005 &&
            now - lastPoint.recordedAt < 12 * 60 * 60 * 1000L

        if (!samePriceRecently) {
            itemDao.insertPoint(
                PricePoint(
                    itemId = id,
                    price = newPrice,
                    currencyCode = metadata.currencyCode ?: item.currencyCode,
                    recordedAt = now
                )
            )
        }

        val fetchedImages = metadata.imageUrls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val existingImages = item.imageUrls()
        val mergedImages = when {
            fetchedImages.isNotEmpty() -> fetchedImages
            existingImages.isNotEmpty() -> existingImages
            else -> listOfNotNull(item.imageUrl, metadata.imageUrl).filter { it.isNotBlank() }.distinct()
        }
        val updated = item.copy(
            title = item.title.ifBlank { metadata.title.orEmpty() },
            currentPrice = newPrice,
            currencyCode = metadata.currencyCode ?: item.currencyCode,
            listPrice = metadata.listPrice ?: item.listPrice,
            imageUrl = mergedImages.firstOrNull() ?: item.imageUrl ?: metadata.imageUrl,
            imageUrlsJson = if (mergedImages.isNotEmpty()) WishItem.encodeImageUrls(mergedImages) else item.imageUrlsJson,
            description = if (item.description.isBlank()) metadata.description?.trim()?.take(4000).orEmpty() else item.description,
            specsJson = if (item.specs().isEmpty() && metadata.specs.isNotEmpty()) WishItem.encodeSpecs(metadata.specs) else item.specsJson,
            brand = item.brand ?: metadata.brand,
            lastCheckedAt = now,
            lastCheckFailed = false,
            updatedAt = now
        )
        itemDao.update(updated)

        val isNewLow = previousLow != null && newPrice < previousLow - 0.005 && historyBefore.size >= 2
        handleAlerts(updated, item.currentPrice, newPrice, isNewLow)
        notifyWidgetsChanged()

        RefreshResult.Updated(
            title = updated.title,
            oldPrice = item.currentPrice,
            newPrice = newPrice,
            isAllTimeLow = isNewLow,
            currencyCode = updated.currencyCode
        )
    }

    private suspend fun handleAlerts(
        item: WishItem,
        oldPrice: Double?,
        newPrice: Double,
        isNewLow: Boolean
    ) {
        if (!prefs.notificationsEnabled.first()) return

        val target = item.targetPrice
        val targetHit = target != null && newPrice <= target &&
            (oldPrice == null || oldPrice > target)
        val lowAlertEnabled = prefs.notifyOnNewLow.first() && isNewLow
        val targetAlertEnabled = prefs.notifyOnTarget.first() && targetHit

        if (targetAlertEnabled || lowAlertEnabled) {
            notifier.notifyPriceChange(
                item = item,
                oldPrice = oldPrice,
                newPrice = newPrice,
                targetHit = targetAlertEnabled,
                newLow = lowAlertEnabled
            )
        }
    }

    suspend fun refreshAll(): RefreshSummary = withContext(Dispatchers.IO) {
        val items = itemDao.getActiveItems()
        var updated = 0
        var failed = 0
        var lows = 0

        items.forEach { item ->
            when (val result = refreshItem(item.id)) {
                is RefreshResult.Updated -> {
                    updated++
                    if (result.isAllTimeLow) lows++
                }

                is RefreshResult.Failed -> failed++
                RefreshResult.NotFound -> Unit
            }
            delay(700)
        }
        prefs.setLastRefresh(System.currentTimeMillis())
        notifyWidgetsChanged()
        RefreshSummary(items.size, updated, failed, lows)
    }

    // ----------------------------------------------------------- categories

    suspend fun ensureDefaultCategories() = withContext(Dispatchers.IO) {
        if (categoryDao.count() > 0) return@withContext
        val defaults = listOf(
            "Tech" to Palette.FOREST,
            "Home" to Palette.MUSTARD,
            "Fashion" to Palette.TERRACOTTA,
            "Fitness" to Palette.OCEAN,
            "Gifts" to Palette.PLUM
        )
        defaults.forEachIndexed { index, (name, color) ->
            categoryDao.insert(
                Category(name = name, colorArgb = color, emoji = null, sortOrder = index)
            )
        }
    }

    suspend fun addCategory(name: String, colorArgb: Int, emoji: String?): Long =
        withContext(Dispatchers.IO) {
            val clean = name.trim().take(30)
            require(clean.isNotEmpty()) { "Give the collection a name." }
            val existing = categoryDao.getAll()
            require(existing.none { it.name.equals(clean, ignoreCase = true) }) {
                "You already have a collection called \"$clean\"."
            }
            val order = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
            categoryDao.insert(
                Category(
                    name = clean,
                    colorArgb = colorArgb,
                    emoji = emoji?.trim()?.take(2)?.takeIf { it.isNotEmpty() },
                    sortOrder = order
                )
            )
        }

    suspend fun updateCategory(category: Category, name: String, colorArgb: Int, emoji: String?) =
        withContext(Dispatchers.IO) {
            val clean = name.trim().take(30)
            require(clean.isNotEmpty()) { "Give the collection a name." }
            val existing = categoryDao.getAll()
            require(existing.none { it.id != category.id && it.name.equals(clean, ignoreCase = true) }) {
                "You already have a collection called \"$clean\"."
            }
            categoryDao.update(
                category.copy(
                    name = clean,
                    colorArgb = colorArgb,
                    emoji = emoji?.trim()?.take(2)?.takeIf { it.isNotEmpty() }
                )
            )
        }

    suspend fun deleteCategory(category: Category) = withContext(Dispatchers.IO) {
        categoryDao.delete(category)
    }

    // ---------------------------------------------------------- demo data

    suspend fun seedDemoData(): Boolean = withContext(Dispatchers.IO) {
        if (itemDao.count() > 0) return@withContext false
        ensureDefaultCategories()
        val categories = categoryDao.getAll().associateBy { it.name }
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L

        data class Demo(
            val title: String,
            val store: String,
            val url: String,
            val image: String?,
            val currency: String,
            val listPrice: Double,
            val brand: String,
            val category: String,
            val target: Double?,
            val notes: String,
            val history: List<Pair<Int, Double>>
        )

        val demos = listOf(
            Demo(
                title = "Sony WH-1000XM5 Wireless Headphones",
                store = "Amazon",
                url = "https://www.amazon.com/dp/B09XS7JWHH",
                image = "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=900&q=80",
                currency = "USD",
                listPrice = 399.99,
                brand = "Sony",
                category = "Tech",
                target = 299.0,
                notes = "For the commute. Waiting for a sale.",
                history = listOf(
                    74 to 399.99, 60 to 379.99, 48 to 349.99, 33 to 349.99,
                    20 to 329.99, 12 to 349.99, 6 to 329.99, 1 to 299.99
                )
            ),
            Demo(
                title = "Uniqlo Linen Blend Overshirt",
                store = "Uniqlo",
                url = "https://www.uniqlo.com/us/en/products/E451615-000/00",
                image = null,
                currency = "USD",
                listPrice = 59.90,
                brand = "Uniqlo",
                category = "Fashion",
                target = 49.0,
                notes = "Size M, sand colour.",
                history = listOf(
                    66 to 59.90, 52 to 59.90, 40 to 49.90, 30 to 49.90,
                    18 to 59.90, 8 to 49.90, 2 to 39.90
                )
            ),
            Demo(
                title = "AeroPress Clear Coffee Maker",
                store = "Amazon",
                url = "https://www.amazon.com/dp/B0C4Q7K4S8",
                image = null,
                currency = "USD",
                listPrice = 49.95,
                brand = "AeroPress",
                category = "Home",
                target = 39.95,
                notes = "Replacing the old one.",
                history = listOf(
                    61 to 39.95, 47 to 39.95, 34 to 44.95, 22 to 44.95,
                    9 to 49.95, 2 to 49.95
                )
            ),
            Demo(
                title = "Nike Air Zoom Pegasus 41",
                store = "Nike",
                url = "https://www.nike.com/t/pegasus-41-mens-road-running-shoes",
                image = "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=900&q=80",
                currency = "USD",
                listPrice = 140.00,
                brand = "Nike",
                category = "Fitness",
                target = 110.0,
                notes = "Daily trainer. Size 10.",
                history = listOf(
                    58 to 140.00, 44 to 126.00, 36 to 126.00, 24 to 112.00,
                    15 to 112.00, 7 to 126.00, 1 to 112.00
                )
            ),
            Demo(
                title = "Kindle Paperwhite 16 GB",
                store = "Amazon",
                url = "https://www.amazon.com/dp/B08KTZ8249",
                image = null,
                currency = "USD",
                listPrice = 149.99,
                brand = "Amazon",
                category = "Tech",
                target = 119.0,
                notes = "Gift idea for the holidays.",
                history = listOf(
                    72 to 149.99, 55 to 139.99, 41 to 139.99, 27 to 124.99,
                    14 to 139.99, 3 to 124.99
                )
            )
        )

        demos.forEach { demo ->
            val history = demo.history
            val itemId = itemDao.insert(
                WishItem(
                    title = demo.title,
                    storeName = demo.store,
                    productUrl = demo.url,
                    imageUrl = demo.image,
                    currencyCode = demo.currency,
                    currentPrice = history.last().second,
                    listPrice = demo.listPrice,
                    targetPrice = demo.target,
                    categoryId = categories[demo.category]?.id,
                    notes = demo.notes,
                    brand = demo.brand,
                    createdAt = now - 80 * day,
                    updatedAt = now - day,
                    lastCheckedAt = now - day
                )
            )
            history.forEach { (daysAgo, price) ->
                itemDao.insertPoint(
                    PricePoint(
                        itemId = itemId,
                        price = price,
                        currencyCode = demo.currency,
                        recordedAt = now - daysAgo * day
                    )
                )
            }
        }
        notifyWidgetsChanged()
        true
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        itemDao.deleteAll()
        categoryDao.deleteAll()
        ensureDefaultCategories()
        notifyWidgetsChanged()
    }

    private suspend fun mergeImportedHistory(
        itemId: Long,
        imported: List<HistoricalPrice>,
        fallbackCurrency: String,
        now: Long,
        seededOut: MutableList<Pair<Long, Double>>
    ) {
        if (imported.isEmpty()) return
        val existing = itemDao.history(itemId).map { it.recordedAt to it.price }
        val seen = existing.toMutableList()
        imported.sortedBy { it.recordedAt ?: Long.MAX_VALUE }.forEach { point ->
            val price = point.price.takeIf { it.isFinite() && it > 0.0 } ?: return@forEach
            val ts = (point.recordedAt ?: return@forEach).let { if (it < 10_000_000_000L) it * 1000 else it }
            if (ts < 946684800000L || ts > now + 86_400_000L) return@forEach
            val currency = point.currencyCode.ifBlank { fallbackCurrency }.uppercase()
            // only merge same-currency history to keep stats coherent
            if (currency != fallbackCurrency.uppercase()) return@forEach
            val dup = seen.any { (ets, ep) -> abs(ets - ts) < 60 * 60 * 1000L && abs(ep - price) < 0.005 }
            if (dup) return@forEach
            itemDao.insertPoint(PricePoint(itemId = itemId, price = price, currencyCode = currency, recordedAt = ts))
            seen += ts to price
            seededOut += ts to price
        }
    }

    suspend fun importHistoryFromText(itemId: Long, text: String): Int = withContext(Dispatchers.IO) {
        val item = itemDao.getById(itemId) ?: return@withContext 0
        val now = System.currentTimeMillis()
        val imported = parseImportedHistoryText(text, item.currencyCode)
        val before = itemDao.history(itemId).size
        mergeImportedHistory(itemId, imported, item.currencyCode, now, mutableListOf())
        val added = (itemDao.history(itemId).size - before).coerceAtLeast(0)
        if (added > 0) notifyWidgetsChanged()
        added
    }

    private fun parseImportedHistoryText(text: String, fallbackCurrency: String): List<HistoricalPrice> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        // JSON array
        if (trimmed.startsWith("[")) {
            return runCatching {
                val arr = org.json.JSONArray(trimmed)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val price = MoneyParser.parse(o.optString("price").ifBlank { o.optString("amount").ifBlank { o.optString("value") } })
                        ?: o.optDouble("price").takeIf { it > 0.0 } ?: return@mapNotNull null
                    val dateRaw = o.optString("date").ifBlank { o.optString("time").ifBlank { o.optString("timestamp") } }
                    val ts = parseImportTimestamp(dateRaw) ?: return@mapNotNull null
                    val cur = o.optString("currency").ifBlank { fallbackCurrency }
                    HistoricalPrice(price = price, currencyCode = cur, recordedAt = ts)
                }
            }.getOrDefault(emptyList())
        }
        // CSV: date,price[,currency]
        val lines = trimmed.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()
        val start = if (lines.first().lowercase().contains("price") && lines.first().lowercase().contains("date")) 1 else 0
        return lines.drop(start).mapNotNull { line ->
            val parts = line.split(',', ';', '\t').map { it.trim().trim('"') }
            if (parts.size < 2) return@mapNotNull null
            val ts = parseImportTimestamp(parts[0]) ?: parseImportTimestamp(parts.getOrNull(1)) ?: return@mapNotNull null
            val price = MoneyParser.parse(parts.getOrNull(1)) ?: MoneyParser.parse(parts.getOrNull(0)) ?: return@mapNotNull null
            val cur = parts.getOrNull(2)?.takeIf { it.length in 3..4 } ?: fallbackCurrency
            HistoricalPrice(price = price, currencyCode = cur, recordedAt = ts)
        }
    }

    private fun parseImportTimestamp(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val t = raw.trim().trim('"')
        t.toLongOrNull()?.let { n -> return if (n < 10_000_000_000L) n * 1000 else n }
        val patterns = listOf("yyyy-MM-dd", "yyyy/MM/dd", "dd-MM-yyyy", "MM/dd/yyyy", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss")
        for (p in patterns) {
            val ts = runCatching {
                if (p.contains("H")) {
                    java.time.LocalDateTime.parse(t.take(19), java.time.format.DateTimeFormatter.ofPattern(p))
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                } else {
                    val dp = when {
                        p.startsWith("dd") -> t.take(10)
                        p.startsWith("MM") -> t.take(10)
                        else -> t.take(10)
                    }
                    java.time.LocalDate.parse(dp, java.time.format.DateTimeFormatter.ofPattern(p))
                        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            }.getOrNull()
            if (ts != null) return ts
        }
        return runCatching {
            java.time.Instant.parse(t).toEpochMilli()
        }.getOrNull()
    }

    /** Parses a user-typed price, tolerating symbols and commas. */
    fun parsePriceInput(text: String): Double? = MoneyParser.parse(text)
}
