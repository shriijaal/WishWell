package com.wishwell.app.data.suggest

import com.wishwell.app.data.model.ItemWithStats
import com.wishwell.app.data.model.WishItem
import com.wishwell.app.data.net.StoreRegistry
import com.wishwell.app.data.net.StoreSearchLink
import java.util.Locale
import kotlin.math.roundToInt

data class SimilarPick(
    val item: ItemWithStats,
    val reason: String,
    val score: Double,
    val isBetter: Boolean = false
)

data class CrossStoreMatch(
    val item: ItemWithStats,
    val reason: String
)

/**
 * Finds better-value alternatives without a server.
 *
 *  - "Better value in your list" is scored from saved items: title overlap, shared collection
 *    and how far each candidate sits below its own observed peak.
 *  - "Compare stores" builds search links so the current product can be checked elsewhere.
 */
class RecommendationEngine(private val stores: StoreRegistry) {

    private val stopWords = setOf(
        "the", "and", "with", "for", "from", "this", "that", "your", "our", "new", "latest",
        "inch", "inches", "pcs", "pack", "set", "size", "color", "colour", "black", "white",
        "blue", "red", "green", "pink", "grey", "gray", "men", "mens", "women", "womens",
        "unisex", "edition", "series", "model", "original", "gen", "gb", "tb", "ml", "oz",
        "official", "store", "global", "version", "free", "shipping", "sale", "offer"
    )

    private val categoryKeywords = mapOf(
        "tech" to setOf("phone", "laptop", "tablet", "headphone", "earbud", "camera", "tv", "monitor",
            "charger", "cable", "gaming", "console", "speaker", "smartwatch", "computer", "macbook",
            "ipad", "pixel", "galaxy", "iphone", "sony", "dell", "hp", "lenovo", "battery", "power bank",
            "sd card", "usb", "adapter", "mouse", "keyboard", "webcam"),
        "beauty" to setOf("cream", "serum", "lotion", "makeup", "lipstick", "sunscreen", "moisturizer",
            "cleanser", "cosmetic", "skincare", "foundation", "mascara", "eyeliner", "nail", "perfume",
            "fragrance", "shampoo", "conditioner", "soap", "deodorant", "toner", "mask"),
        "fashion" to setOf("shirt", "dress", "shoe", "shoes", "jeans", "jacket", "t-shirt", "sneakers", "bag",
            "watch", "jewelry", "necklace", "ring", "bracelet", "earring", "suit", "blazer", "skirt",
            "shorts", "hat", "cap", "scarf", "belt", "wallet", "purse", "backpack"),
        "home" to setOf("blender", "pot", "pan", "pillow", "lamp", "curtain", "towel", "mattress",
            "furniture", "chair", "table", "bed", "sofa", "rug", "vase", "candle", "mirror", "clock",
            "storage", "organizer", "cleaner", "vacuum", "iron", "cookware"),
        "sports" to setOf("ball", "racket", "dumbbell", "treadmill", "bicycle", "helmet", "jersey",
            "sport", "fitness", "gym", "yoga", "running", "hiking", "camping", "fishing", "golf"),
        "books" to setOf("book", "novel", "textbook", "magazine", "notebook", "journal", "diary",
            "comic", "manga", "manual"),
        "auto" to setOf("tire", "oil filter", "battery", "car", "auto", "motor oil", "brake", "spark plug",
            "headlight", "wiper", "car accessory"),
        "grocery" to setOf("rice", "oil", "snack", "beverage", "cereal", "milk", "spice", "flour",
            "sugar", "salt", "pasta", "noodle", "coffee", "tea", "juice", "food")
    )

    private val storeTypeTerms = mapOf(
        "tech" to listOf("gadget shop", "electronics store", "computer store"),
        "beauty" to listOf("pharmacy", "beauty store", "cosmetics shop", "drugstore"),
        "fashion" to listOf("clothing store", "fashion boutique", "department store"),
        "home" to listOf("home goods store", "kitchen store", "department store"),
        "sports" to listOf("sporting goods store", "fitness store"),
        "books" to listOf("bookstore", "stationery shop"),
        "auto" to listOf("auto parts store", "car shop"),
        "grocery" to listOf("supermarket", "grocery store", "convenience store")
    )

    private val audioTypeTerms = setOf(
        "iem", "iems", "earphone", "earphones", "earbud", "earbuds", "in-ear", "inear",
        "headphone", "headphones", "headset", "tws", "earpiece", "audiophile", "hi-res", "hires"
    )
    private val audioCategoryHints = setOf("audio", "earphone", "earbud", "headphone", "iem", "music", "sound")
    private val audioBrands = setOf(
        "moondrop", "7hz", "tangzu", "kz", "cca", "truthear", "simgot", "tanchjim", "fiio",
        "letshuoer", "tripowin", "blon", "tin", "hidizs", "dunu", "thieaudio", "kiwi", "qkz"
    )

    fun similarSaved(target: ItemWithStats, all: List<ItemWithStats>): List<SimilarPick> {
        val targetTokens = tokens(target.item.title)
        return all.asSequence()
            .filter { it.item.id != target.item.id }
            .mapNotNull { candidate ->
                val similarity = jaccard(targetTokens, tokens(candidate.item.title))
                val sameCategory = target.item.categoryId != null && candidate.item.categoryId == target.item.categoryId
                val priceBand = priceBandScore(target.price, candidate.price)

                // exclude near-identical cross-store matches (shown separately)
                if (similarity >= 0.52 && differentListing(target, candidate)) return@mapNotNull null
                if (similarity < 0.14 && !sameCategory) return@mapNotNull null

                val valueScore = (candidate.discountPercent ?: 0) / 100.0
                val cheaper = isCheaper(candidate, target)
                val betterValue = cheaper || (candidate.discountPercent ?: 0) >= 10 || candidate.isAtLowest
                val score = similarity * 1.15 + (if (sameCategory) 0.35 else 0.0) +
                    priceBand * 0.1 + valueScore * 0.25 + (if (betterValue) 0.18 else 0.0)
                SimilarPick(
                    item = candidate,
                    reason = reasonFor(target, candidate, sameCategory, betterValue),
                    score = score,
                    isBetter = betterValue
                )
            }
            .sortedWith(compareByDescending<SimilarPick> { if (it.isBetter) 1 else 0 }.thenByDescending { it.score })
            .take(4)
            .toList()
    }

    /** Same/similar product saved from different stores (real price comparison from your list). */
    fun crossStoreMatches(target: ItemWithStats, all: List<ItemWithStats>): List<CrossStoreMatch> {
        val targetTokens = tokens(target.item.title)
        return all.asSequence()
            .filter { it.item.id != target.item.id }
            .mapNotNull { candidate ->
                val similarity = jaccard(targetTokens, tokens(candidate.item.title))
                if (similarity < 0.34 || !differentListing(target, candidate)) return@mapNotNull null
                val reason = buildString {
                    append(candidate.item.storeName)
                    when {
                        isCheaper(candidate, target) -> append(" · cheaper")
                        isCheaper(target, candidate) -> append(" · higher here")
                        else -> append(" · compare price")
                    }
                    if (similarity >= 0.55) append(" · close match")
                }
                CrossStoreMatch(candidate, reason)
            }
            .sortedBy { it.item.price ?: Double.MAX_VALUE }
            .take(4)
            .toList()
    }

    private fun differentListing(a: ItemWithStats, b: ItemWithStats): Boolean {
        if (a.item.productUrl.isNotBlank() && a.item.productUrl.equals(b.item.productUrl, ignoreCase = true)) return false
        val hostA = runCatching { java.net.URI(a.item.productUrl).host?.lowercase() }.getOrNull()
        val hostB = runCatching { java.net.URI(b.item.productUrl).host?.lowercase() }.getOrNull()
        return hostA == null || hostB == null || hostA != hostB || a.item.title != b.item.title
    }

    private fun isCheaper(a: ItemWithStats, b: ItemWithStats): Boolean {
        val ap = a.price ?: return false
        val bp = b.price ?: return false
        if (!a.item.currencyCode.equals(b.item.currencyCode, ignoreCase = true)) return false
        return ap < bp * 0.99
    }

    fun storeComparisons(item: WishItem): List<StoreSearchLink> {
        val query = searchQuery(item)
        return stores.searchLinks(query, item.currencyCode, item.productUrl)
    }

    fun searchQuery(item: WishItem): String {
        val brand = item.brand?.trim()?.takeIf { it.isNotEmpty() }
        val ordered = orderedTokens(item.title)
        val audioIntent = isAudioIntent(item, ordered)

        // Detect product category for store-type aware Google Shopping search
        val category = detectProductCategory(item)
        val textLower = item.title.lowercase(Locale.ROOT)

        // Shoes-only shortcut: if searching for shoes, keep it to a single keyword
        if (category == "fashion" && textLower.contains("shoe") || textLower.contains("sneaker")) {
            return "shoes"
        }

        val parts = linkedSetOf<String>()
        if (brand != null) parts += brand

        // prioritize model/type-bearing tokens in original order
        ordered.forEach { word ->
            if (parts.size >= 7) return@forEach
            if (brand != null && word.equals(brand.lowercase(Locale.ROOT), true)) return@forEach
            if (brand != null && brand.lowercase(Locale.ROOT).contains(word) && word.length < 4) return@forEach
            parts += word
        }

        // add meaningful description keywords to broaden cross-store matching
        if (parts.size < 7 && item.description.isNotBlank()) {
            tokens(item.description).forEach { word ->
                if (parts.size >= 7) return@forEach
                if (parts.contains(word)) return@forEach
                parts += word
            }
        }

        // append store-type terms for Google Shopping to find any relevant retailer type
        if (category != null && parts.size < 7) {
            val terms = storeTypeTermsFor(category)
            terms.forEach { term ->
                if (parts.size >= 7) return@forEach
                val termTokens = term.split(" ")
                termTokens.forEach { t ->
                    if (parts.size >= 7) return@forEach
                    if (!parts.contains(t)) parts += t
                }
            }
        }

        // for audio gear, ensure a clear type anchor if missing (helps avoid laptop/keyboard results)
        if (audioIntent && parts.none { it in audioTypeTerms || it == "iem" || it == "earphones" || it == "earbuds" }) {
            val anchor = when {
                ordered.any { it.contains("tws") } -> "tws earbuds"
                ordered.any { it.contains("headphone") || it.contains("headset") } -> "headphones"
                else -> "iem earphones"
            }
            anchor.split(" ").forEach { if (parts.size < 8) parts += it }
        }

        val q = parts.joinToString(" ").trim()
        return q.ifBlank { item.title.replace(Regex("\\s+"), " ").trim().take(80) }
    }

    private fun detectProductCategory(item: WishItem): String? {
        val text = (item.title + " " + (item.description ?: "")).lowercase(Locale.ROOT)
        val brand = (item.brand ?: "").lowercase(Locale.ROOT)

        var bestCategory: String? = null
        var bestScore = 0

        categoryKeywords.forEach { (category, keywords) ->
            var score = 0
            keywords.forEach { keyword ->
                if (text.contains(keyword)) score += 1
                if (brand.contains(keyword)) score += 1
            }
            if (score > bestScore) {
                bestScore = score
                bestCategory = category
            }
        }

        return bestCategory
    }

    private fun storeTypeTermsFor(category: String): List<String> {
        return storeTypeTerms[category] ?: emptyList()
    }

    fun isAudioIntent(item: WishItem, ordered: List<String> = orderedTokens(item.title)): Boolean {
        val title = item.title.lowercase(Locale.ROOT)
        val brand = item.brand?.lowercase(Locale.ROOT).orEmpty()
        val cat = "" // category name not on WishItem; title/brand heuristics are primary
        if (ordered.any { it in audioTypeTerms }) return true
        if (audioBrands.any { brand.contains(it) || title.contains(it) }) return true
        if (Regex("""\b(iem|in[- ]?ear|ear ?(phone|bud)s?|tws|head(phone|set)s?|audiophile)\b""").containsMatchIn(title)) return true
        if (audioCategoryHints.any { cat.contains(it) }) return true
        return false
    }

    private fun orderedTokens(title: String): List<String> {
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        // keep tokens in source order; preserve short meaningful tokens (iem, dac, amp) and model numerics/roman combos in context
        val raw = title.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .split(Regex("\\s+"))
        raw.forEachIndexed { idx, t ->
            if (t.isBlank()) return@forEachIndexed
            val keep = when {
                t in stopWords -> false
                t in audioTypeTerms || t == "dac" || t == "amp" -> true
                t.length >= 3 && t.any { c -> c.isLetter() } -> true
                // keep short numeric/model tokens when adjacent context looks like a model (e.g. "chu 2", "v2", "iii" handled as letters)
                t.any { c -> c.isDigit() } && t.length in 1..4 -> {
                    val prev = raw.getOrNull(idx - 1).orEmpty()
                    val next = raw.getOrNull(idx + 1).orEmpty()
                    prev.length >= 2 || next.length >= 2 || prev in audioBrands
                }
                t == "ii" || t == "iii" || t == "iv" -> true
                else -> false
            }
            if (keep && seen.add(t)) out += t
        }
        return out
    }

    private fun reasonFor(
        target: ItemWithStats,
        candidate: ItemWithStats,
        sameCategory: Boolean,
        betterValue: Boolean
    ): String {
        val bits = mutableListOf<String>()
        if (betterValue) bits += "Similar but better"
        val targetPrice = target.price
        val candidatePrice = candidate.price
        if (targetPrice != null && candidatePrice != null && candidatePrice < targetPrice * 0.95) {
            val percent = (((targetPrice - candidatePrice) / targetPrice) * 100).roundToInt()
            if (bits.none { it.contains("% less") }) bits += "$percent% less"
        }
        candidate.discountPercent?.takeIf { it >= 10 }?.let { if (bits.size < 3) bits += "$it% below peak" }
        if (sameCategory && bits.size < 3) {
            bits += candidate.categoryName ?: "Same collection"
        } else if (bits.isEmpty()) {
            bits += "Similar item"
        }
        return bits.distinct().joinToString(" · ")
    }

    private fun priceBandScore(a: Double?, b: Double?): Double {
        if (a == null || b == null || a <= 0.0 || b <= 0.0) return 0.0
        val ratio = b / a
        return when {
            ratio in 0.6..1.6 -> 1.0
            ratio in 0.35..2.2 -> 0.5
            else -> 0.0
        }
    }

    private fun tokens(title: String): Set<String> =
        title.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .asSequence()
            .filter { it.length >= 3 && it !in stopWords && it.any { c -> c.isLetter() } }
            .toSet()

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.count { it in b }
        val union = (a + b).size
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }
}
