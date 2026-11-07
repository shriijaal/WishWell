package com.wishwell.app.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI

/**
 * Best-effort search-result extraction for "compare in other stores".
 * Static HTML/embedded JSON parsing only - no JS execution. Many stores block/JS-render;
 * callers should fall back to opening the search URL in-app.
 */
class StoreSearchScraper(
    private val client: OkHttpClient = ProductScraper.defaultClient(),
    private val stores: StoreRegistry = StoreRegistry()
) {

    suspend fun search(
        storeName: String,
        searchUrl: String,
        currencyCode: String,
        targetTitle: String? = null,
        targetBrand: String? = null,
        targetCategoryHint: String? = null
    ): List<FoundCompareProduct> =
        withContext(Dispatchers.IO) {
            val url = searchUrl.toHttpUrlOrNull()?.toString()
                ?: return@withContext emptyList()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                )
                .header("Accept-Language", "en-US,en;q=0.9,np;q=0.7")
                .header("Cache-Control", "no-cache")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@use emptyList()
                    val base = response.request.url.toString()
                    val host = runCatching { URI(base).host.orEmpty() }.getOrDefault("")
                    val doc = Jsoup.parse(body, base)
                    val products = mutableListOf<FoundCompareProduct>()

                    if (host.contains("daraz.") || storeName.contains("Daraz", ignoreCase = true)) {
                        products += parseDarazCatalog(body, base, storeName, currencyCode)
                    }
                    if (products.size < 3) {
                        products += parseGenericSearch(doc, body, base, storeName, currencyCode)
                    }
                    // Broader fallback: try generic container parsing even when Daraz returned a little (or mixed junk)
                    if (products.size < 4) {
                        val extra = mutableListOf<FoundCompareProduct>()
                        parseProductContainers(doc, base, storeName.ifBlank { stores.displayNameForHost(host) }, currencyCode.ifBlank { stores.defaultCurrencyFor(host) }, extra)
                        products += extra
                    }

                    val cleaned = products
                        .filter { it.title.isNotBlank() && it.productUrl.startsWith("http") }
                        .distinctBy { normalizeKey(it.productUrl, it.title) }

                    val relevant = if (!targetTitle.isNullOrBlank()) {
                        filterRelevant(cleaned, targetTitle, targetBrand, targetCategoryHint)
                    } else cleaned

                    relevant.take(10)
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    private fun parseDarazCatalog(html: String, baseUrl: String, storeName: String, currencyCode: String): List<FoundCompareProduct> {
        val out = mutableListOf<FoundCompareProduct>()
        val code = if (currencyCode.isNotBlank()) currencyCode else "NPR"
        val resolvedStore = storeName.ifBlank { stores.displayNameForHost(runCatching { URI(baseUrl).host.orEmpty() }.getOrDefault("")) }.ifBlank { "Daraz" }

        // 1) Embedded JSON blobs (Daraz/Lazada variants)
        val jsonBlobs = mutableListOf<String>()
        Regex("""window\.pageData\s*=\s*(\{[\s\S]*?\})\s*;?\s*(?:</script>|window\.)""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.let { jsonBlobs += it }
        Regex("""app\.run\(\s*(\{[\s\S]*?\})\s*\)\s*;?""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.let { jsonBlobs += it }
        // __NEXT_DATA__ / generic JSON script may contain listItems
        Regex("""<script[^>]*id=["']__NEXT_DATA__["'][^>]*>(\{[\s\S]*?\})</script>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.let { jsonBlobs += it }
        extractJsonArrayAfterKey(html, "listItems")?.let { jsonBlobs += """{"listItems":$it}""" }
        extractJsonArrayAfterKey(html, "items")?.let { arrJson ->
            if (arrJson.contains("\"itemId\"") || arrJson.contains("\"name\"") || arrJson.contains("\"image\"")) {
                jsonBlobs += """{"listItems":$arrJson}"""
            }
        }

        for (blob in jsonBlobs) {
            runCatching {
                val root = JSONObject(blob)
                val mods = root.optJSONObject("mods") ?: root.optJSONObject("data") ?: root
                val arr = mods.optJSONArray("listItems")
                    ?: root.optJSONArray("listItems")
                    ?: mods.optJSONArray("items")
                    ?: findArrayByKey(root, "listItems")
                    ?: findArrayByKey(root, "items")
                if (arr != null) extractDarazItems(arr, baseUrl, resolvedStore, code, out)
            }
        }

        // 2) Fallback: parse product cards/anchors from HTML (broader selectors)
        if (out.size < 2) {
            val doc = Jsoup.parse(html, baseUrl)
            val selectors = listOf(
                "div[data-qa-locator=product-item]", "[data-item-id]", ".Bm3ON", ".gridItem--Yd0sa",
                "[data-tracking=product-card]", "[data-spm-anchor-id] a[href*='/products/']",
                ".box--ujueT", ".ant-col-8 .RfADt", "a[href*='/products/'][title]", "a[href*='-i'][href*='.html']"
            )
            val seenHrefs = HashSet<String>()
            for (sel in selectors) {
                doc.select(sel).forEach { el ->
                    val anchor = if (el.tagName() == "a") el else el.selectFirst("a[href*='/products/'], a[href*='-i'][href*='.html'], a[href]") ?: return@forEach
                    val href = absUrl(baseUrl, anchor.attr("href")) ?: return@forEach
                    if (!seenHrefs.add(href)) return@forEach
                    if (!(href.contains("/products/") || (href.contains("-i") && href.contains(".html")))) return@forEach
                    val container = if (el.tagName() == "a") (el.parents().take(5).firstOrNull { it.text().length in 20..700 } ?: el) else el
                    val title = container.selectFirst("[class*=title], .RfADt a[title], a[title], [title], h2, h3, img[alt]")?.let { n ->
                        n.attr("title").ifBlank { n.attr("alt").ifBlank { n.text() } }
                    }?.trim().orEmpty().ifBlank { anchor.attr("title").ifBlank { anchor.text() } }.trim()
                        .replace(Regex("\\s+"), " ")
                    if (title.length < 4) return@forEach
                    val img = container.selectFirst("img")?.let { img ->
                        img.attr("src").ifBlank { img.attr("data-src").ifBlank { img.attr("data-ks-lazyload").ifBlank { img.attr("data-lazy-src") } } }
                    }
                    val price = findPriceNear(container) ?: MoneyParser.parse(container.selectFirst("[class*=price], .ooOxS, .hpIKP, .aBrP0")?.text().orEmpty())
                    out += FoundCompareProduct(
                        storeName = resolvedStore,
                        title = title.take(180),
                        price = price,
                        currencyCode = code,
                        imageUrl = normalizeImageUrl(img),
                        productUrl = href
                    )
                }
                if (out.size >= 8) break
            }
        }
        return out.distinctBy { normalizeKey(it.productUrl, it.title) }.take(10)
    }

    private fun extractJsonArrayAfterKey(text: String, key: String): String? {
        val marker = "\"$key\""
        var idx = text.indexOf(marker)
        while (idx >= 0) {
            val colon = text.indexOf(':', idx + marker.length)
            if (colon >= 0) {
                var i = colon + 1
                while (i < text.length && text[i].isWhitespace()) i++
                if (i < text.length && text[i] == '[') {
                    val arr = extractBalancedArray(text, i)
                    if (arr != null && arr.length > 2) return arr
                }
            }
            idx = text.indexOf(marker, idx + marker.length)
        }
        return null
    }

    private fun extractBalancedArray(text: String, start: Int): String? {
        if (start !in text.indices || text[start] != '[') return null
        var depth = 0
        var inString = false
        var escape = false
        var i = start
        val limit = kotlin.math.min(text.length, start + 200_000)
        while (i < limit) {
            val c = text[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
            i++
        }
        return null
    }

    private val audioTerms = setOf(
        "iem", "iems", "earphone", "earphones", "earbud", "earbuds", "inear", "in-ear", "tws",
        "headphone", "headphones", "headset", "audiophile", "dac", "amp", "dongle", "earpiece"
    )
    private val audioBrandTerms = setOf(
        "moondrop", "7hz", "tangzu", "kz", "cca", "truthear", "simgot", "tanchjim", "fiio",
        "letshuoer", "tripowin", "blon", "hidizs", "dunu", "thieaudio", "kiwi", "qkz", "sony", "sennheiser"
    )
    private val nonAudioHardTerms = setOf(
        "laptop", "notebook", "macbook", "keyboard", "keycaps", "keycap", "mouse", "monitor",
        "printer", "router", "gpu", "graphics", "ssd", "ram", "motherboard", "processor", "cpu",
        "smartwatch", "tablet", "ipad", "console", "controller", "tripod", "camera"
    )

    private fun filterRelevant(
        products: List<FoundCompareProduct>,
        targetTitle: String,
        targetBrand: String?,
        targetCategoryHint: String?
    ): List<FoundCompareProduct> {
        val targetNorm = targetTitle.lowercase()
        val targetTokens = relevanceTokens(targetTitle)
        val brand = targetBrand?.lowercase()?.trim().orEmpty()
        val isAudio = isAudioTarget(targetNorm, targetTokens, brand, targetCategoryHint)
        if (targetTokens.isEmpty()) return products

        val scored = products.mapNotNull { p ->
            val pt = p.title.lowercase()
            val pTokens = relevanceTokens(p.title)
            val overlap = targetTokens.count { it in pTokens }
            val jaccard = if (targetTokens.isEmpty() || pTokens.isEmpty()) 0.0 else overlap.toDouble() / (targetTokens + pTokens).size
            val brandHit = brand.isNotBlank() && pt.contains(brand)
            val modelHit = targetTokens.filter { it.length >= 3 && it.any(Char::isDigit) || it in setOf("ii", "iii", "iv", "chu", "waner", "zero", "aria", "starfield") }
                .count { it in pTokens }
            val hasAudioTerm = pTokens.any { it in audioTerms } || audioTerms.any { pt.contains(it) }
            val hasNonAudioHard = nonAudioHardTerms.any { pt.contains(it) }

            if (isAudio && hasNonAudioHard && !hasAudioTerm && modelHit == 0) return@mapNotNull null
            if (!isAudio && hasNonAudioHard.not() && false) { /* no-op */ }

            val score = (overlap * 1.4) + (jaccard * 2.2) + (if (brandHit) 1.6 else 0.0) + (modelHit * 1.1) + (if (isAudio && hasAudioTerm) 0.6 else 0.0)
            p to score
        }.sortedByDescending { it.second }

        if (scored.isEmpty()) return emptyList()
        val best = scored.first().second
        // Keep reasonably relevant results, but avoid dropping everything when scores are borderline
        // (common with Nepali/regional store titles). Audio hard non-audio items are already excluded above.
        val minScore = when {
            best >= 3.2 -> 1.35
            best >= 2.2 -> 1.0
            best >= 1.5 -> 0.8
            best >= 1.0 -> 0.55
            else -> 0.35
        }
        val filtered = scored.filter { it.second >= minScore }.map { it.first }
        if (filtered.isNotEmpty()) return filtered
        // Last resort: keep the top ranked result(s) if they show any plausible signal.
        val fallback = scored.take(3).filter { it.second >= 0.25 }.map { it.first }
        return fallback
    }

    private fun isAudioTarget(titleNorm: String, tokens: Set<String>, brand: String, categoryHint: String?): Boolean {
        val hint = (categoryHint ?: "").lowercase()
        if (audioTerms.any { it in tokens } || audioTerms.any { titleNorm.contains(it) }) return true
        if (audioBrandTerms.any { brand.contains(it) || titleNorm.contains(it) }) return true
        if (hint.contains("audio") || hint.contains("headphone") || hint.contains("earphone") || hint.contains("earbud") || hint.contains("iem")) return true
        return false
    }

    private fun relevanceTokens(text: String): Set<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
            .filterNot { it in setOf("the", "and", "for", "with", "from", "new", "official", "store", "original", "global", "version", "black", "white", "blue", "red", "pro", "plus") && it !in audioTerms }
            .toSet()

    private fun extractDarazItems(arr: JSONArray, baseUrl: String, storeName: String, currencyCode: String, out: MutableList<FoundCompareProduct>) {
        for (i in 0 until minOf(arr.length(), 20)) {
            val item = arr.optJSONObject(i) ?: continue
            val title = item.optString("name").ifBlank { item.optString("title") }.trim()
            if (title.isBlank()) continue
            val rawUrl = item.optString("itemUrl").ifBlank {
                item.optString("productUrl").ifBlank { item.optString("url") }
            }
            val href = absProductUrl(baseUrl, rawUrl) ?: continue
            val image = normalizeImageUrl(
                item.optString("image").ifBlank {
                    item.optString("thumb").ifBlank {
                        item.optJSONArray("thumbs")?.optString(0).orEmpty()
                    }
                }
            )
            val price = MoneyParser.parse(item.optString("priceShow"))
                ?: MoneyParser.parse(item.optString("price"))
                ?: item.optDouble("price").takeIf { it > 0 }
                ?: item.optDouble("salePrice").takeIf { it > 0 }
            val orig = MoneyParser.parse(item.optString("originalPrice"))
                ?: item.optDouble("originalPrice").takeIf { it > 0 }
            out += FoundCompareProduct(
                storeName = storeName.ifBlank { "Daraz" },
                title = title.take(180),
                price = price,
                currencyCode = normalizeCurrency(item.optString("currency")) ?: currencyCode,
                imageUrl = image,
                productUrl = href,
                listPrice = if (orig != null && price != null && orig > price + 0.01) orig else null
            )
        }
    }

    private fun parseGenericSearch(doc: Document, @Suppress("UNUSED_PARAMETER") html: String, baseUrl: String, storeName: String, currencyCode: String): List<FoundCompareProduct> {
        val out = mutableListOf<FoundCompareProduct>()
        val host = runCatching { URI(baseUrl).host.orEmpty() }.getOrDefault("")
        val resolvedStore = storeName.ifBlank { stores.displayNameForHost(host) }
        val code = currencyCode.ifBlank { stores.defaultCurrencyFor(host) }

        // JSON-LD ItemList / SearchResultsPage
        doc.select("script[type=application/ld+json]").forEach { script ->
            runCatching {
                val root = JSONObject(script.data())
                collectJsonLdProducts(root, baseUrl, resolvedStore, code, out, 0)
            }
            runCatching {
                val arr = JSONArray(script.data())
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    collectJsonLdProducts(o, baseUrl, resolvedStore, code, out, 0)
                }
            }
        }

        if (out.size < 3) {
            parseProductContainers(doc, baseUrl, resolvedStore, code, out)
        }

        if (out.size < 3) {
            // Heuristic: product-like anchors with image + price nearby
            val candidates = doc.select("a[href]").filter { a ->
                val h = a.attr("href").lowercase()
                h.contains("/product") || h.contains("/dp/") || h.contains("/item") || h.contains("/p/") ||
                    h.contains("/shop/") || h.contains("add-to-cart") || h.contains("product_id")
            }
            for (a in candidates.take(50)) {
                val href = absUrl(baseUrl, a.attr("href")) ?: continue
                if (isJunkUrl(href)) continue
                val container = a.parents().take(6).firstOrNull { it.select("img").isNotEmpty() && it.text().length in 10..500 } ?: a
                val title = a.attr("title").ifBlank {
                    a.selectFirst("h2, h3, h4, [class*=title], [class*=name]")?.text().orEmpty()
                        .ifBlank { a.selectFirst("img")?.attr("alt").orEmpty().ifBlank { a.text() } }
                }.trim().replace(Regex("\\s+"), " ")
                if (title.length < 4 || title.length > 220) continue
                val img = container.selectFirst("img")?.let {
                    it.attr("data-src").ifBlank { it.attr("data-lazy-src").ifBlank { it.attr("src").ifBlank { it.attr("data-original") } } }
                }
                val price = findPriceNear(container) ?: findPriceNear(a)
                if (price == null && out.size >= 3) continue
                out += FoundCompareProduct(
                    storeName = resolvedStore,
                    title = title.take(180),
                    price = price,
                    currencyCode = code,
                    imageUrl = normalizeImageUrl(img),
                    productUrl = href
                )
            }
        }
        return out.distinctBy { normalizeKey(it.productUrl, it.title) }.take(10)
    }

    private fun parseProductContainers(doc: Document, baseUrl: String, storeName: String, currencyCode: String, out: MutableList<FoundCompareProduct>) {
        val selectors = listOf(
            "li.product", "ul.products > li", ".product-item", ".product-card", ".product-grid-item",
            "[class*=ProductCard]", "[class*=product-card]", "[class*=productCard]",
            ".item-product", ".grid__item", ".collection-product", "article.product"
        )
        for (sel in selectors) {
            val nodes = doc.select(sel)
            if (nodes.size < 2) continue
            for (el in nodes.take(16)) {
                val anchor = el.selectFirst("a[href*='/product'], a[href*='/products/'], a[href*='/shop/'], a.woocommerce-LoopProduct-link, a[href]")
                    ?: continue
                val href = absUrl(baseUrl, anchor.attr("href")) ?: continue
                if (isJunkUrl(href)) continue
                val title = el.selectFirst("h2, h3, h4, .woocommerce-loop-product__title, [class*=title], [class*=name]")?.text()?.trim()
                    ?.ifBlank { anchor.attr("title").ifBlank { el.selectFirst("img")?.attr("alt").orEmpty() } }
                    ?.trim().orEmpty()
                if (title.length < 4) continue
                val img = el.selectFirst("img")?.let {
                    it.attr("data-src").ifBlank { it.attr("data-lazy-src").ifBlank { it.attr("srcset").substringBefore(" ").ifBlank { it.attr("src").ifBlank { it.attr("data-original") } } } }
                }
                val price = findPriceNear(el)
                out += FoundCompareProduct(storeName, title.take(180), price, currencyCode, normalizeImageUrl(img), href)
            }
            if (out.size >= 3) break
        }
    }

    private fun findPriceNear(el: org.jsoup.nodes.Element): Double? {
        val priceEl = el.selectFirst("[class*=price] .amount, .price, [class*=price], .woocommerce-Price-amount, [data-price]")
        val text = listOfNotNull(
            priceEl?.attr("data-price")?.takeIf { it.isNotBlank() },
            priceEl?.text(),
            el.selectFirst("ins .amount, .sale-price, [class*=sale]")?.text(),
            el.text().take(260)
        ).firstNotNullOfOrNull { MoneyParser.parse(it) }
        return text
    }

    private fun isJunkUrl(url: String): Boolean {
        val l = url.lowercase()
        return l.contains("/cart") || l.contains("/checkout") || l.contains("/account") || l.contains("/login") ||
            l.contains("add-to-cart") || l.contains("wishlist") || l.contains("facebook.com") || l.contains("javascript:")
    }

    private fun collectJsonLdProducts(node: JSONObject, baseUrl: String, store: String, currency: String, out: MutableList<FoundCompareProduct>, depth: Int) {
        if (depth > 5) return
        val type = node.opt("@type")
        val types = when (type) {
            is String -> listOf(type)
            is JSONArray -> (0 until type.length()).map { type.optString(it) }
            else -> emptyList()
        }
        if (types.any { it.equals("Product", true) }) {
            val title = node.optString("name").trim()
            val href = absUrl(baseUrl, node.optString("url").ifBlank { node.optString("@id") })
            if (title.isNotBlank() && href != null) {
                val (price, cur, listPrice) = extractOffer(node.opt("offers"))
                val img = when (val im = node.opt("image")) {
                    is String -> im
                    is JSONArray -> im.optString(0)
                    is JSONObject -> im.optString("url")
                    else -> null
                }
                out += FoundCompareProduct(store, title.take(180), price, normalizeCurrency(cur) ?: currency, normalizeImageUrl(img), href, listPrice)
            }
        }
        val itemList = node.optJSONArray("itemListElement") ?: node.optJSONObject("mainEntity")?.optJSONArray("itemListElement")
        if (itemList != null) {
            for (i in 0 until itemList.length()) {
                val el = itemList.opt(i)
                if (el is JSONObject) collectJsonLdProducts(el.optJSONObject("item") ?: el, baseUrl, store, currency, out, depth + 1)
            }
        }
        val keys = node.keys()
        while (keys.hasNext()) {
            when (val v = node.opt(keys.next())) {
                is JSONObject -> collectJsonLdProducts(v, baseUrl, store, currency, out, depth + 1)
                is JSONArray -> for (i in 0 until minOf(v.length(), 12)) {
                    val c = v.opt(i); if (c is JSONObject) collectJsonLdProducts(c, baseUrl, store, currency, out, depth + 1)
                }
            }
        }
    }

    private fun extractOffer(offers: Any?): Triple<Double?, String?, Double?> {
        val o = when (offers) {
            is JSONArray -> offers.optJSONObject(0)
            is JSONObject -> if (offers.has("offers")) (offers.opt("offers") as? JSONObject ?: (offers.opt("offers") as? JSONArray)?.optJSONObject(0)) else offers
            else -> null
        } ?: return Triple(null, null, null)
        val price = MoneyParser.parse(o.optString("price")) ?: MoneyParser.parse(o.optString("lowPrice"))
        val list = MoneyParser.parse(o.optString("highPrice"))
        val cur = o.optString("priceCurrency").takeIf { it.isNotBlank() }
        return Triple(price, cur, if (list != null && price != null && list > price + 0.01) list else null)
    }

    private fun findArrayByKey(obj: JSONObject, key: String): JSONArray? {
        if (obj.has(key)) return obj.optJSONArray(key)
        val keys = obj.keys()
        while (keys.hasNext()) {
            val v = obj.opt(keys.next())
            if (v is JSONObject) findArrayByKey(v, key)?.let { return it }
        }
        return null
    }

    private fun absProductUrl(base: String, raw: String): String? {
        if (raw.isBlank()) return null
        var u = raw.trim().replace("\\/", "/")
        if (u.startsWith("//")) u = "https:$u"
        if (u.startsWith("/")) {
            val host = runCatching { URI(base) }.getOrNull()
            if (host != null) u = "${host.scheme}://${host.host}$u"
        }
        return absUrl(base, u)
    }

    private fun absUrl(base: String, raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching { URI(base).resolve(raw.trim()).toString() }.getOrNull()
            ?.takeIf { it.startsWith("http") }
    }

    private fun normalizeImageUrl(raw: String?): String? {
        if (raw.isNullOrBlank() || raw.startsWith("data:")) return null
        var u = raw.trim().replace("\\/", "/")
        if (u.startsWith("//")) u = "https:$u"
        return u.takeIf { it.startsWith("http") }
    }

    private fun normalizeKey(url: String, title: String): String =
        runCatching { URI(url).path }.getOrDefault("").ifBlank { title.lowercase().take(60) }

    private fun normalizeCurrency(raw: String?): String? {
        val r = raw?.trim()?.uppercase().orEmpty()
        return if (r.length == 3 && r.all { it.isLetter() }) r else when {
            r.contains("NPR") || r.contains("RS") -> "NPR"
            else -> null
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
