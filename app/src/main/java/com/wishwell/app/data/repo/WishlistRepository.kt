package com.wishwell.app.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.wishwell.app.data.db.CategoryDao
import com.wishwell.app.data.db.ItemDao
import com.wishwell.app.data.model.Category
import com.wishwell.app.data.model.CategoryWithCount
import com.wishwell.app.data.model.ItemCategoryTag
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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
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
    val categoryIds: Set<Long> = emptySet(),
    val notes: String,
    val brand: String?,
    val priceHistory: List<HistoricalPrice> = emptyList(),
    val description: String = "",
    val specs: List<Pair<String, String>> = emptyList(),
    val inStock: Boolean? = null
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

    fun observeItemTags(): Flow<List<ItemCategoryTag>> = itemDao.observeItemTags()

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
                categoryId = draft.categoryIds.firstOrNull(),
                notes = draft.notes.trim(),
                description = draft.description.trim().take(4000),
                specsJson = WishItem.encodeSpecs(draft.specs),
                brand = draft.brand?.trim()?.takeIf { it.isNotEmpty() },
                inStock = draft.inStock,
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
        // persist many-to-many category tags
        if (draft.categoryIds.isNotEmpty()) {
            itemDao.insertItemTags(
                draft.categoryIds.map { ItemCategoryTag(itemId = itemId, categoryId = it) }
            )
        }
        notifyWidgetsChanged()
        itemId
    }

    suspend fun setTargetPrice(id: Long, target: Double?) = withContext(Dispatchers.IO) {
        val item = itemDao.getById(id) ?: return@withContext
        itemDao.update(item.copy(targetPrice = target, updatedAt = System.currentTimeMillis()))
        notifyWidgetsChanged()
    }

    suspend fun setItemTags(id: Long, categoryIds: Set<Long>) = withContext(Dispatchers.IO) {
        val item = itemDao.getById(id) ?: return@withContext
        itemDao.clearItemTags(id)
        if (categoryIds.isNotEmpty()) {
            itemDao.insertItemTags(
                categoryIds.map { ItemCategoryTag(itemId = id, categoryId = it) }
            )
        }
        itemDao.update(item.copy(categoryId = categoryIds.firstOrNull(), updatedAt = System.currentTimeMillis()))
        notifyWidgetsChanged()
    }

    suspend fun setItemsTags(ids: Collection<Long>, categoryIds: Set<Long>) = withContext(Dispatchers.IO) {
        val unique = ids.distinct()
        if (unique.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        unique.forEach { id ->
            val item = itemDao.getById(id) ?: return@forEach
            itemDao.clearItemTags(id)
            if (categoryIds.isNotEmpty()) {
                itemDao.insertItemTags(
                    categoryIds.map { ItemCategoryTag(itemId = id, categoryId = it) }
                )
            }
            itemDao.update(item.copy(categoryId = categoryIds.firstOrNull(), updatedAt = now))
        }
        notifyWidgetsChanged()
    }

    suspend fun setNotes(id: Long, notes: String) = withContext(Dispatchers.IO) {
        val item = itemDao.getById(id) ?: return@withContext
        itemDao.update(item.copy(notes = notes.trim().take(600), updatedAt = System.currentTimeMillis()))
        notifyWidgetsChanged()
    }

    suspend fun reorderItems(itemId: Long, direction: Int, orderedVisibleIds: List<Long>): Boolean =
        withContext(Dispatchers.IO) {
            val index = orderedVisibleIds.indexOf(itemId)
            if (index < 0) return@withContext false
            val targetIndex = index + direction
            if (targetIndex !in orderedVisibleIds.indices) return@withContext false
            val otherId = orderedVisibleIds[targetIndex]
            val item = itemDao.getById(itemId) ?: return@withContext false
            val other = itemDao.getById(otherId) ?: return@withContext false
            val now = System.currentTimeMillis()
            itemDao.update(item.copy(createdAt = other.createdAt, updatedAt = now))
            itemDao.update(other.copy(createdAt = item.createdAt, updatedAt = now))
            notifyWidgetsChanged()
            true
        }

    suspend fun persistItemOrder(orderedIds: List<Long>) = withContext(Dispatchers.IO) {
        if (orderedIds.isEmpty()) return@withContext
        val base = System.currentTimeMillis()
        val step = 1_000L
        orderedIds.forEachIndexed { index, id ->
            val item = itemDao.getById(id) ?: return@forEachIndexed
            val createdAt = base - (index.toLong() * step)
            if (item.createdAt != createdAt) {
                itemDao.update(item.copy(createdAt = createdAt, updatedAt = base))
            }
        }
        notifyWidgetsChanged()
    }

    suspend fun updateItem(
        id: Long,
        title: String,
        storeName: String,
        productUrl: String,
        imageUrls: List<String>,
        currencyCode: String,
        currentPrice: Double?,
        listPrice: Double?,
        targetPrice: Double?,
        categoryIds: Set<Long>,
        notes: String,
        description: String,
        specs: List<Pair<String, String>>,
        brand: String?
    ) = withContext(Dispatchers.IO) {
        val existing = itemDao.getById(id) ?: throw IllegalArgumentException("Item not found.")
        val cleanTitle = title.trim().take(180)
        require(cleanTitle.isNotEmpty()) { "Give the item a name first." }
        val now = System.currentTimeMillis()
        val normalizedImages = imageUrls.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(12)
        val normalizedCurrency = currencyCode.uppercase().trim().take(3).ifBlank { existing.currencyCode }
        val cleanStore = storeName.trim().ifBlank {
            runCatching { URI(productUrl.trim()).host }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { stores.displayNameForHost(it) }
                ?: existing.storeName
        }
        val oldPrice = existing.currentPrice
        val priceChanged = currentPrice != null && (oldPrice == null || abs(oldPrice - currentPrice) >= 0.005)
        if (priceChanged) {
            val lastPoint = itemDao.latestPoint(id)
            val sameRecently = lastPoint != null &&
                abs(lastPoint.price - currentPrice!!) < 0.005 &&
                lastPoint.currencyCode.equals(normalizedCurrency, ignoreCase = true) &&
                now - lastPoint.recordedAt < 12 * 60 * 60 * 1000L
            if (!sameRecently) {
                itemDao.insertPoint(
                    PricePoint(
                        itemId = id,
                        price = currentPrice!!,
                        currencyCode = normalizedCurrency,
                        recordedAt = now
                    )
                )
            }
        }
        val updated = existing.copy(
            title = cleanTitle,
            storeName = cleanStore,
            productUrl = productUrl.trim(),
            imageUrl = normalizedImages.firstOrNull(),
            imageUrlsJson = WishItem.encodeImageUrls(normalizedImages),
            currencyCode = normalizedCurrency,
            currentPrice = currentPrice,
            listPrice = listPrice,
            targetPrice = targetPrice,
            categoryId = categoryIds.firstOrNull(),
            notes = notes.trim().take(600),
            description = description.trim().take(4000),
            specsJson = WishItem.encodeSpecs(specs),
            brand = brand?.trim()?.takeIf { it.isNotEmpty() },
            updatedAt = now,
            lastCheckFailed = false
        )
        itemDao.update(updated)
        // persist many-to-many category tags (replace existing set)
        itemDao.clearItemTags(id)
        if (categoryIds.isNotEmpty()) {
            itemDao.insertItemTags(
                categoryIds.map { ItemCategoryTag(itemId = id, categoryId = it) }
            )
        }
        notifyWidgetsChanged()
    }

    suspend fun deleteItem(id: Long) = withContext(Dispatchers.IO) {
        val item = itemDao.getById(id)
        if (item != null) {
            itemDao.delete(item)
            notifyWidgetsChanged()
        }
    }

    suspend fun deleteItems(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        val unique = ids.distinct()
        if (unique.isEmpty()) return@withContext
        unique.chunked(900).forEach { chunk -> itemDao.deleteByIds(chunk) }
        notifyWidgetsChanged()
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
        val historyCountBefore = itemDao.countPoints(id)

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
            // Specs are read-only in the editor, so refresh always mirrors the latest extraction
            // (this also clears stale noise such as SKU/MPN rows).
            specsJson = WishItem.encodeSpecs(metadata.specs),
            brand = item.brand ?: metadata.brand,
            inStock = metadata.inStock ?: item.inStock,
            lastCheckedAt = now,
            lastCheckFailed = false,
            updatedAt = now
        )
        itemDao.update(updated)

        val isNewLow = previousLow != null && newPrice < previousLow - 0.005 && historyCountBefore >= 2
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
                    emoji = emoji?.trim()?.take(32)?.takeIf { it.isNotEmpty() },
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
                    emoji = emoji?.trim()?.take(32)?.takeIf { it.isNotEmpty() }
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
            val demoCategoryId = categories[demo.category]?.id
            if (demoCategoryId != null) {
                itemDao.insertItemTags(listOf(ItemCategoryTag(itemId, demoCategoryId)))
            }
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

    // ------------------------------------------------------------- backups

    data class BackupImportResult(
        val categoriesAdded: Int = 0,
        val itemsAdded: Int = 0,
        val itemsMerged: Int = 0,
        val historyPointsAdded: Int = 0
    )

    data class BackupFileInfo(
        val path: String,
        val name: String,
        val size: Long,
        val lastModified: Long
    )

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val categories = categoryDao.getAll()
        val items = itemDao.getAllItemsRaw()
        val pointsByItem = itemDao.getAllPoints().groupBy { it.itemId }
        val categoryNameById = categories.associate { it.id to it.name }
        val itemTagMap = itemDao.getAllItemTags().groupBy { it.itemId }.mapValues { it.value.map { it.categoryId } }

        val root = org.json.JSONObject()
            .put("format", "wishwell-backup")
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())

        val catsArr = org.json.JSONArray()
        categories.forEach { c ->
            catsArr.put(
                org.json.JSONObject()
                    .put("name", c.name)
                    .put("colorArgb", c.colorArgb)
                    .put("emoji", c.emoji)
                    .put("sortOrder", c.sortOrder)
                    .put("createdAt", c.createdAt)
            )
        }
        root.put("categories", catsArr)

        val itemsArr = org.json.JSONArray()
        items.forEach { item ->
            val o = org.json.JSONObject()
                .put("title", item.title)
                .put("storeName", item.storeName)
                .put("productUrl", item.productUrl)
                .put("imageUrl", item.imageUrl)
                .put("imageUrls", org.json.JSONArray(item.imageUrls()))
                .put("currencyCode", item.currencyCode)
                .put("targetPrice", item.targetPrice)
                .put("categoryName", item.categoryId?.let { categoryNameById[it] })
                .put("categoryIds", org.json.JSONArray().also { arr ->
                    (itemTagMap[item.id] ?: emptyList()).forEach { arr.put(it) }
                })
                .put("notes", item.notes)
                .put("description", item.description)
                .put("specs", org.json.JSONArray().also { arr ->
                    item.specs().forEach { (k, v) -> arr.put(org.json.JSONObject().put("k", k).put("v", v)) }
                })
                .put("brand", item.brand)
                .put("isArchived", item.isArchived)
                .put("inStock", item.inStock)
                .put("createdAt", item.createdAt)
                .put("updatedAt", item.updatedAt)
                .put("lastCheckedAt", item.lastCheckedAt)
                .put("lastCheckFailed", item.lastCheckFailed)
                .put("currentPrice", item.currentPrice)
                .put("listPrice", item.listPrice)

            val histArr = org.json.JSONArray()
            pointsByItem[item.id].orEmpty().forEach { p ->
                histArr.put(
                    org.json.JSONObject()
                        .put("price", p.price)
                        .put("currencyCode", p.currencyCode)
                        .put("recordedAt", p.recordedAt)
                )
            }
            o.put("history", histArr)
            itemsArr.put(o)
        }
        root.put("items", itemsArr)
        root.toString(2)
    }

    suspend fun importBackupJson(text: String): BackupImportResult = withContext(Dispatchers.IO) {
        val root = runCatching { org.json.JSONObject(text) }.getOrElse {
            throw IllegalArgumentException("That backup file isn't valid JSON.")
        }
        val format = root.optString("format")
        if (format.isNotBlank() && format != "wishwell-backup") {
            throw IllegalArgumentException("This file doesn't look like a WishWell backup.")
        }
        val now = System.currentTimeMillis()
        var categoriesAdded = 0
        var itemsAdded = 0
        var itemsMerged = 0
        var historyPointsAdded = 0

        // 1) categories (match by name, case-insensitive)
        val existingCategories = categoryDao.getAll().toMutableList()
        val categoryIdByName = existingCategories.associate { it.name.trim().lowercase() to it.id }.toMutableMap()
        val catsArr = root.optJSONArray("categories") ?: org.json.JSONArray()
        for (i in 0 until catsArr.length()) {
            val c = catsArr.optJSONObject(i) ?: continue
            val name = c.optString("name").trim().take(40)
            if (name.isBlank()) continue
            val key = name.lowercase()
            if (categoryIdByName.containsKey(key)) continue
            val category = Category(
                name = name,
                colorArgb = c.optInt("colorArgb", 0xFF4E8A6A.toInt()),
                emoji = c.optString("emoji").takeIf { it.isNotBlank() && it != "null" },
                sortOrder = c.optInt("sortOrder", existingCategories.size),
                createdAt = c.optLong("createdAt", now).takeIf { it > 0L } ?: now
            )
            val newId = categoryDao.insert(category)
            existingCategories += category.copy(id = newId)
            categoryIdByName[key] = newId
            categoriesAdded++
        }

        // Build old category ID → name map from backup so we can remap old IDs
        // (from the original DB) to new IDs (matched by name in this DB).
        val oldCategoryIdToName = mutableMapOf<Long, String>()
        for (i in 0 until catsArr.length()) {
            val c = catsArr.optJSONObject(i) ?: continue
            val id = c.optLong("id", -1L)
            val name = c.optString("name").takeIf { it.isNotBlank() && id > 0L }
            if (name != null) oldCategoryIdToName[id] = name.trim()
        }

        // 2) items + history (merge by normalized product URL when available)
        val existingItems = itemDao.getAllItemsRaw()
        val existingByUrl = existingItems
            .mapNotNull { item -> normalizeBackupUrl(item.productUrl)?.let { it to item } }
            .toMap(mutableMapOf())

        val itemsArr = root.optJSONArray("items") ?: org.json.JSONArray()
        for (i in 0 until itemsArr.length()) {
            val o = itemsArr.optJSONObject(i) ?: continue
            val title = o.optString("title").trim().take(180)
            if (title.isBlank()) continue
            val storeName = o.optString("storeName").trim().take(80).ifBlank { "Store" }
            val productUrl = o.optString("productUrl").trim()
            val currencyCode = o.optString("currencyCode").trim().uppercase().take(3).ifBlank { "USD" }
            val categoryName = o.optString("categoryName").takeIf { it.isNotBlank() && it != "null" }?.trim()
            val categoryIds = o.optJSONArray("categoryIds")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    if (arr.isNull(i)) null else arr.getLong(i)
                }.toSet()
            } ?: emptySet()
            // Map old category IDs from backup to new category IDs (matched by name)
            val newCategoryIds = categoryIds.mapNotNull { oldId ->
                oldCategoryIdToName[oldId]?.let { name ->
                    categoryIdByName[name.trim().lowercase()]
                }
            }.toSet()
            val effectiveCategoryIds = if (newCategoryIds.isNotEmpty()) newCategoryIds
                else categoryName?.let { categoryIdByName[it.lowercase()] }?.let { setOf(it) } ?: emptySet()

            val imageUrls = mutableListOf<String>()
            o.optJSONArray("imageUrls")?.let { arr ->
                for (j in 0 until arr.length()) arr.optString(j)?.takeIf { it.isNotBlank() }?.let(imageUrls::add)
            }
            if (imageUrls.isEmpty()) o.optString("imageUrl").takeIf { it.isNotBlank() && it != "null" }?.let { imageUrls += it }

            val specs = mutableListOf<Pair<String, String>>()
            o.optJSONArray("specs")?.let { arr ->
                for (j in 0 until arr.length()) {
                    val s = arr.optJSONObject(j) ?: continue
                    val k = s.optString("k").trim()
                    val v = s.optString("v").trim()
                    if (k.isNotEmpty() && v.isNotEmpty()) specs += k to v
                }
            }

            val history = mutableListOf<HistoricalPrice>()
            o.optJSONArray("history")?.let { arr ->
                for (j in 0 until arr.length()) {
                    val h = arr.optJSONObject(j) ?: continue
                    val price = h.optDouble("price", Double.NaN)
                    val ts = h.optLong("recordedAt", 0L)
                    if (!price.isFinite() || price <= 0.0 || ts <= 0L) continue
                    val cur = h.optString("currencyCode").ifBlank { currencyCode }.uppercase()
                    history += HistoricalPrice(price = price, currencyCode = cur, recordedAt = ts)
                }
            }

            val urlKey = normalizeBackupUrl(productUrl)
            val matched = urlKey?.let { existingByUrl[it] }
            if (matched != null) {
                // merge history into existing item; don't overwrite user-edited fields
                val before = itemDao.history(matched.id).size
                mergeImportedHistory(matched.id, history, matched.currencyCode, now, mutableListOf())
                val added = (itemDao.history(matched.id).size - before).coerceAtLeast(0)
                historyPointsAdded += added
                if (added > 0 || history.isNotEmpty()) itemsMerged++
                continue
            }

            val createdAt = o.optLong("createdAt", now).takeIf { it > 0L } ?: now
            val updatedAt = o.optLong("updatedAt", createdAt).takeIf { it > 0L } ?: createdAt
            val item = WishItem(
                title = title,
                storeName = storeName,
                productUrl = productUrl,
                imageUrl = imageUrls.firstOrNull(),
                imageUrlsJson = WishItem.encodeImageUrls(imageUrls),
                currencyCode = currencyCode,
                currentPrice = optNullableDouble(o, "currentPrice"),
                listPrice = optNullableDouble(o, "listPrice"),
                targetPrice = optNullableDouble(o, "targetPrice"),
                categoryId = effectiveCategoryIds.firstOrNull(),
                notes = o.optString("notes").take(600),
                description = o.optString("description").take(4000),
                specsJson = WishItem.encodeSpecs(specs),
                brand = o.optString("brand").takeIf { it.isNotBlank() && it != "null" }?.trim()?.take(80),
                isArchived = o.optBoolean("isArchived", false),
                inStock = if (o.has("inStock") && !o.isNull("inStock")) o.optBoolean("inStock") else null,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastCheckedAt = o.optLong("lastCheckedAt", 0L).takeIf { it > 0L },
                lastCheckFailed = o.optBoolean("lastCheckFailed", false)
            )
            val newId = itemDao.insert(item)
            if (effectiveCategoryIds.isNotEmpty()) {
                itemDao.insertItemTags(
                    effectiveCategoryIds.map { ItemCategoryTag(itemId = newId, categoryId = it) }
                )
            }
            if (urlKey != null) existingByUrl[urlKey] = item.copy(id = newId)
            itemsAdded++

            val beforePts = itemDao.history(newId).size
            mergeImportedHistory(newId, history, item.currencyCode, now, mutableListOf())
            historyPointsAdded += (itemDao.history(newId).size - beforePts).coerceAtLeast(0)
        }

        if (categoriesAdded > 0 || itemsAdded > 0 || itemsMerged > 0 || historyPointsAdded > 0) {
            notifyWidgetsChanged()
        }
        BackupImportResult(
            categoriesAdded = categoriesAdded,
            itemsAdded = itemsAdded,
            itemsMerged = itemsMerged,
            historyPointsAdded = historyPointsAdded
        )
    }

    private fun normalizeBackupUrl(raw: String?): String? {
        val t = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val u = java.net.URI(t)
            val scheme = (u.scheme ?: "https").lowercase()
            val host = (u.host ?: return@runCatching t.lowercase().trimEnd('/')).lowercase().removePrefix("www.")
            val path = (u.path ?: "").trimEnd('/')
            "$scheme://$host$path"
        }.getOrElse { t.lowercase().trimEnd('/') }
    }

    private fun optNullableDouble(o: org.json.JSONObject, key: String): Double? {
        if (!o.has(key) || o.isNull(key)) return null
        val d = o.optDouble(key, Double.NaN)
        return d.takeIf { it.isFinite() }
    }

    // =====================================================================
    // Backup & restore (file-based)
    // =====================================================================

    /** Returns the backup root directory based on the persisted location preference. */
    private suspend fun backupDir(): File {
        val location = prefs.backupLocation.first()
        return when (location) {
            "user_picked" -> {
                prefs.backupLocationUri.first()?.let { uriString ->
                    val uri = Uri.parse(uriString)
                    appContext.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    File(uri.path ?: appContext.filesDir.absolutePath + "/backups")
                } ?: File(appContext.filesDir, "backups")
            }
            else -> File(appContext.filesDir, "backups")
        }.apply { mkdirs() }
    }

    /** Builds a timestamped backup file name. */
    private fun backupFileName(): String =
        "WishWell_Backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"

    /** Rotates stored backup paths, keeping only the most recent 3. Deletes excess files. */
    private suspend fun rotateBackups() {
        val paths = prefs.backupFilePaths.first().toMutableList()
        val kept = paths.sortedDescending().take(3)
        val removed = paths - kept
        removed.forEach { path ->
            runCatching { File(path).delete() }
            prefs.removeBackupFilePath(path)
        }
    }

    /**
     * Writes a backup JSON file to the configured location, records it, and rotates old backups
     * so only the most recent 3 are kept. Returns the absolute path of the written file.
     */
    suspend fun backupToFile(): String = withContext(Dispatchers.IO) {
        val json = exportBackupJson()
        val dir = backupDir()
        val file = File(dir, backupFileName())
        file.writeBytes(json.toByteArray(Charsets.UTF_8))

        val absolutePath = file.absolutePath
        prefs.recordBackupFile(absolutePath)
        prefs.setLastBackupAt(System.currentTimeMillis())
        rotateBackups()
        absolutePath
    }

    /**
     * Reads a backup file from a content URI and imports it. Returns the import result.
     * Caller is responsible for picking the URI via SAF.
     */
    suspend fun restoreFromFile(uri: Uri): BackupImportResult = withContext(Dispatchers.IO) {
        appContext.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val input: InputStream = appContext.contentResolver.openInputStream(uri)
            ?: error("Couldn't open the selected backup file.")
        val text = input.bufferedReader().use { it.readText() }
        val result = importBackupJson(text)
        prefs.recordBackupFile(uri.toString())
        rotateBackups()
        result
    }

    /** Imports a backup file from an absolute filesystem path (e.g. app internal storage). */
    suspend fun importFromFile(path: String): BackupImportResult = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) error("Backup file not found: $path")
        val text = file.bufferedReader().use { it.readText() }
        importBackupJson(text)
    }

    /** Lists all backup files currently known (existing files only). */
    suspend fun listBackupFiles(): List<File> = withContext(Dispatchers.IO) {
        val dir = backupDir()
        val known = prefs.backupFilePaths.first()
        val files = known.mapNotNull { path ->
            val f = File(path)
            if (f.exists()) f else null
        }
        // Also scan the directory for any backup files not yet tracked
        val dirFiles = dir.listFiles()
            ?.filter { it.name.startsWith("WishWell_Backup_") && it.name.endsWith(".json") }
            ?.filter { !known.contains(it.absolutePath) }
            .orEmpty()
        (files + dirFiles).sortedByDescending { it.lastModified() }.distinctBy { it.absolutePath }
    }

    /** Deletes a backup file and removes it from the tracked paths. */
    suspend fun deleteBackupFile(path: String) = withContext(Dispatchers.IO) {
        runCatching { File(path).delete() }
        prefs.removeBackupFilePath(path)
    }

    /**
     * Storage usage summary for the settings screen.
     */
    data class StorageUsage(
        val databaseBytes: Long,
        val itemCount: Int,
        val categoryCount: Int,
        val pricePointCount: Int,
        val backupBytes: Long,
        val backupCount: Int
    )

    /** A single storage volume (internal, SD card, etc.) with used/free space. */
    data class DiskVolume(
        val label: String,
        val path: String,
        val totalBytes: Long,
        val freeBytes: Long,
        val isRemovable: Boolean
    ) {
        val usedBytes: Long get() = totalBytes - freeBytes
        val usedPercent: Float get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes * 100) else 0f
    }

    suspend fun getStorageUsage(): StorageUsage = withContext(Dispatchers.IO) {
        val dbFile = File(appContext.filesDir, "wishwell.db")
        val dbBytes = if (dbFile.exists()) dbFile.length() else 0L
        val itemCount = itemDao.count()
        val categoryCount = categoryDao.count()
        val pricePointCount = itemDao.getAllPoints().size
        val backups = listBackupFiles()
        val backupBytes = backups.sumOf { it.length() }
        StorageUsage(dbBytes, itemCount, categoryCount, pricePointCount, backupBytes, backups.size)
    }

    /**
     * Lists all mounted storage volumes (internal, SD card, etc.) with total/free space.
     * Prioritises the user-visible emulated internal storage (/storage/emulated/0) so the
     * settings screen shows the real device ROM (e.g. 48.44 GB total) rather than the small
     * /data system partition.
     * Falls back gracefully on scoped-storage devices where some roots are unreadable.
     */
    suspend fun getDiskVolumes(): List<DiskVolume> = withContext(Dispatchers.IO) {
        val volumes = mutableListOf<DiskVolume>()
        val seenPaths = mutableSetOf<String>()

        File.listRoots().forEach { root ->
            val path = root.absolutePath
            if (path in seenPaths) return@forEach
            seenPaths.add(path)

            val total = runCatching { root.totalSpace }.getOrDefault(0L)
            val free = runCatching { root.freeSpace }.getOrDefault(0L)
            if (total == 0L && free == 0L) return@forEach

            val isEmulated = path.startsWith("/storage/emulated/")
            val isSdcard = path.startsWith("/storage/") && path.contains("sdcard", ignoreCase = true)

            val label = when {
                isEmulated -> "Device storage"
                isSdcard -> "SD card"
                path.startsWith("/storage/") -> "Storage"
                path.startsWith("/data") -> "System data"
                else -> path.takeIf { it.isNotEmpty() } ?: "Storage"
            }

            val isRemovable = runCatching {
                android.os.Environment.isExternalStorageRemovable(root)
            }.getOrDefault(false)

            volumes += DiskVolume(label, path, total, free, isRemovable)
        }

        // Put emulated device storage first, then removable, then everything else
        volumes.sortedWith(compareBy<DiskVolume> { !it.label.equals("Device storage", ignoreCase = true) }
            .thenBy { it.isRemovable.not() })
    }
}
