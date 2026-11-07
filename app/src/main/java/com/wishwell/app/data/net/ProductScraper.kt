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
import java.util.concurrent.TimeUnit

/**
 * Reads product details from a public product page.
 *
 * It checks structured data (JSON-LD / Open Graph / microdata) in that order, which works on
 * most storefronts. Some stores aggressively block automated requests — when that happens the
 * UI falls back to letting the user enter the details by hand.
 */
class ProductScraper(
    private val client: OkHttpClient = defaultClient(),
    private val stores: StoreRegistry = StoreRegistry()
) {

    suspend fun scrape(rawUrl: String): ScrapeResult = withContext(Dispatchers.IO) {
        val url = normalizeUrl(rawUrl)
            ?: return@withContext ScrapeResult.Failure("That doesn't look like a product link.")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cache-Control", "no-cache")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use ScrapeResult.Failure(
                        "The store returned HTTP ${response.code}. It may be blocking price checks — you can enter the details by hand."
                    )
                }
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    return@use ScrapeResult.Failure("The page came back empty. Try the link again or add details by hand.")
                }
                val finalUrl = response.request.url.toString()
                val host = response.request.url.host
                val document = Jsoup.parse(body, finalUrl)
                val metadata = extract(document, finalUrl, host, rawUrl)
                if (metadata.title == null && metadata.price == null) {
                    ScrapeResult.Failure("We couldn't read this page. Add the details by hand and we'll still track the price.")
                } else {
                    ScrapeResult.Success(metadata)
                }
            }
        } catch (e: Exception) {
            ScrapeResult.Failure(
                "Couldn't reach that page (${e.javaClass.simpleName}). Check the link or enter the details by hand."
            )
        }
    }

    private fun extract(document: Document, finalUrl: String, host: String, rawUrl: String): ProductMetadata {
        val jsonLd = document.select("script[type=application/ld+json]")
            .asSequence()
            .mapNotNull { runCatching { JSONObject(it.data()) }.getOrNull() }
            .mapNotNull { findProductNode(it, 0) }
            .firstOrNull()

        val offers = jsonLd?.opt("offers")
        val offerPrice = priceForOffers(offers)
        val offerCurrency = currencyForOffers(offers)
        val aggregateHigh = highPriceForOffers(offers)
        val originalPrice = extractOriginalPrice(document)

        val ogTitle = document.metaContent("og:title")
        val metaTitle = document.metaContent("twitter:title")
        val jsonLdTitle = jsonLd?.optString("name")?.takeIf { it.isNotBlank() }
        val htmlTitle = document.title().takeIf { it.isNotBlank() }

        val rawTitle = jsonLdTitle ?: ogTitle ?: metaTitle ?: htmlTitle
        val storeName = stores.displayNameForHost(host)

        val scriptData = extractScriptPricing(document)
        val imageUrls = extractImageUrls(jsonLd, document, scriptData?.imageUrls ?: emptyList())

        val metaPrice = document.metaContent("product:price:amount")
            ?: document.metaContent("og:price:amount")
            ?: document.metaContent("twitter:data1")
            ?: document.selectFirst("[itemprop=price]")?.attr("content")
                ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("meta[itemprop=price]")?.attr("content")
            ?: document.metaContent("price")
            ?: darazPriceText(document)

        val currency = normalizeCurrency(offerCurrency)
            ?: normalizeCurrency(document.metaContent("product:price:currency"))
            ?: normalizeCurrency(document.metaContent("og:price:currency"))
            ?: inferCurrency(document, host)

        val urlHints = extractPriceHintsFromUrl(rawUrl)
        val currencyResolved = currency ?: scriptData?.currencyCode ?: stores.defaultCurrencyFor(host)

        var price = offerPrice
            ?: MoneyParser.parse(metaPrice)
            ?: MoneyParser.parse(document.selectFirst("[itemprop=price]")?.attr("content"))
            ?: darazPriceText(document)?.let(MoneyParser::parse)
            ?: scriptData?.price

        var listPrice = originalPrice
            ?: aggregateHigh
            ?: scriptData?.listPrice
            ?: urlHints.second

        val urlPrice = urlHints.first
        if (urlPrice != null) {
            when {
                price == null -> price = urlPrice
                urlPrice < price - 0.01 -> {
                    // Share/campaign URL often carries the discounted current price; page may show base price.
                    if (listPrice == null || listPrice < price - 0.01) listPrice = price
                    price = urlPrice
                }
                listPrice == null && urlPrice > price + 0.01 -> listPrice = urlPrice
            }
        }

        if (listPrice != null && price != null && listPrice <= price + 0.01) listPrice = null

        val brand = when (val b = jsonLd?.opt("brand")) {
            is String -> b
            is JSONObject -> b.optString("name").takeIf { it.isNotBlank() }
            else -> null
        } ?: document.metaContent("product:brand")

        val resolvedTitle = rawTitle ?: scriptData?.title
        val resolvedImageUrls = imageUrls.ifEmpty { scriptData?.imageUrls ?: listOfNotNull(scriptData?.imageUrl) }
        val resolvedImage = resolvedImageUrls.firstOrNull() ?: scriptData?.imageUrl
        val resolvedBrand = brand ?: scriptData?.brand
        val description = extractDescription(jsonLd, document)
        val specs = extractSpecs(jsonLd, document)

        return ProductMetadata(
            title = resolvedTitle?.let { cleanTitle(it, storeName) },
            storeName = storeName,
            imageUrl = resolvedImage,
            imageUrls = resolvedImageUrls.ifEmpty { listOfNotNull(resolvedImage) },
            price = price,
            listPrice = listPrice,
            currencyCode = currencyResolved,
            brand = resolvedBrand?.takeIf { it.length in 1..60 },
            finalUrl = finalUrl,
            priceHistory = extractPriceHistory(document, currencyResolved, rawUrl),
            description = description,
            specs = specs
        )
    }

    private fun extractOriginalPrice(document: Document): Double? {
        val candidates = listOf(
            "product:original_price:amount",
            "og:price:original_amount",
            "product:list_price:amount",
            "product:price:standard_amount"
        )
        return candidates.firstNotNullOfOrNull { key -> MoneyParser.parse(document.metaContent(key)) }
    }

    private fun Document.metaContent(key: String): String? =
        when (key) {
            "price" -> selectFirst("meta[property=product:price]")?.attr("content")
            else -> selectFirst("meta[property=$key]")?.attr("content")
                ?: selectFirst("meta[name=$key]")?.attr("content")
        }?.trim()?.takeIf { it.isNotEmpty() }

    private fun extractImageUrls(jsonLd: JSONObject?, document: Document, scriptImages: List<String> = emptyList()): List<String> {
        val urls = linkedSetOf<String>()
        fun add(raw: String?) {
            val u = normalizeImageUrl(raw) ?: return
            if (u.length > 2000) return
            // skip tiny/placeholder/data
            if (u.startsWith("data:")) return
            val lower = u.lowercase()
            if (lower.contains("1x1") || lower.contains("pixel") || lower.contains("placeholder") || lower.contains("sprite") || lower.contains("icon")) return
            urls += u
        }

        jsonLd?.opt("image")?.let { image ->
            when (image) {
                is String -> add(image)
                is JSONArray -> for (i in 0 until image.length()) {
                    when (val v = image.opt(i)) {
                        is String -> add(v)
                        is JSONObject -> add(v.optString("url").ifBlank { v.optString("contentUrl").ifBlank { v.optString("@id") } })
                    }
                }
                is JSONObject -> add(image.optString("url").ifBlank { image.optString("contentUrl") })
            }
        }
        // meta / itemprop (collect multiple where possible)
        document.select("meta[property=og:image], meta[property=og:image:secure_url], meta[name=og:image], meta[name=twitter:image], meta[property=product:image]")
            .forEach { add(it.attr("content").ifBlank { it.attr("value") }) }
        document.select("[itemprop=image]").forEach { el ->
            add(el.attr("content").ifBlank { el.attr("src").ifBlank { el.attr("href") } })
        }
        document.select("link[rel=image_src]").forEach { add(it.attr("href")) }
        scriptImages.forEach(::add)

        // Generic gallery selectors (best-effort) - always try to capture full carousel
        if (urls.size < 12) {
            val gallerySelectors = listOf(
                ".pdp-mod-common-image img", ".gallery-preview-panel img", ".next-slick-slide img",
                "[class*=gallery-preview] img", "[class*=item-gallery] img", "[class*=image-gallery] img",
                "[class*=gallery] img", "[class*=carousel] img", "[class*=slider] img",
                "[class*=product-image] img", "[class*=thumbnail] img",
                "img[data-zoom-image]", "img[data-large-image]", "img[data-big]", "img[data-src]"
            )
            for (sel in gallerySelectors) {
                document.select(sel).take(24).forEach { img ->
                    add(
                        img.attr("data-zoom-image").ifBlank {
                            img.attr("data-large-image").ifBlank {
                                img.attr("data-big").ifBlank { img.attr("data-src").ifBlank { img.attr("src").ifBlank { img.attr("data-lazy-src") } } }
                            }
                        }
                    )
                }
                if (urls.size >= 12) break
            }
        }
        return urls.toList().take(12)
    }

    private fun normalizeImageUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var u = raw.trim().trim('"', '\'')
        if (u.isEmpty() || u.equals("null", true)) return null
        if (u.startsWith("//")) u = "https:$u"
        if (!u.startsWith("http://") && !u.startsWith("https://")) return null
        return u
    }

    private fun findProductNode(node: JSONObject, depth: Int): JSONObject? {
        if (depth > 6) return null
        val type = node.opt("@type")
        val types = when (type) {
            is String -> listOf(type)
            is JSONArray -> (0 until type.length()).map { type.optString(it) }
            else -> emptyList()
        }
        if (types.any { it.equals("Product", ignoreCase = true) }) return node

        val keys = node.keys()
        while (keys.hasNext()) {
            val value = node.opt(keys.next())
            when (value) {
                is JSONObject -> findProductNode(value, depth + 1)?.let { return it }
                is JSONArray -> {
                    for (i in 0 until value.length()) {
                        val child = value.opt(i)
                        if (child is JSONObject) findProductNode(child, depth + 1)?.let { return it }
                    }
                }
            }
        }
        return null
    }

    private fun priceForOffers(offers: Any?): Double? {
        if (offers == null || offers == JSONObject.NULL) return null
        return when (offers) {
            is JSONObject -> {
                MoneyParser.parse(offers.optString("price"))
                    ?: MoneyParser.parse(offers.optString("lowPrice"))
                    ?: MoneyParser.parse(offers.optString("highPrice"))
                    ?: priceForOffers(offers.opt("offers"))
            }
            is JSONArray -> {
                (0 until offers.length())
                    .mapNotNull { priceForOffers(offers.opt(it)) }
                    .minOrNull()
            }
            else -> null
        }
    }

    private fun highPriceForOffers(offers: Any?): Double? {
        if (offers is JSONObject) {
            MoneyParser.parse(offers.optString("highPrice"))?.let { return it }
            return highPriceForOffers(offers.opt("offers"))
        }
        if (offers is JSONArray) {
            return (0 until offers.length()).mapNotNull { highPriceForOffers(offers.opt(it)) }.maxOrNull()
        }
        return null
    }

    private fun currencyForOffers(offers: Any?): String? {
        if (offers == null || offers == JSONObject.NULL) return null
        return when (offers) {
            is JSONObject -> offers.optString("priceCurrency").takeIf { it.isNotBlank() }
                ?: currencyForOffers(offers.opt("offers"))
            is JSONArray -> (0 until offers.length()).firstNotNullOfOrNull { currencyForOffers(offers.opt(it)) }
            else -> null
        }
    }

    private fun normalizeCurrency(value: String?): String? {
        val raw = value?.trim() ?: return null
        val upper = raw.uppercase()
        if (upper.length == 3 && upper.all { it.isLetter() }) return upper
        return when {
            upper == "RS" || upper == "RS." || upper.contains("NPR") -> "NPR"
            raw.contains("रु") -> "NPR"
            else -> null
        }
    }

    private fun inferCurrency(document: Document, host: String): String {
        val metaBlob = listOfNotNull(
            document.metaContent("product:price:currency"),
            document.metaContent("og:price:currency"),
            document.metaContent("priceCurrency")
        ).joinToString(" ")
        normalizeCurrency(metaBlob)?.let { return it }
        val hint = document.selectFirst(".pdp-price, [class*=pdp-price], [class*=price]")?.text().orEmpty()
        if (hint.contains("Rs", ignoreCase = true) || hint.contains("NPR", ignoreCase = true) || hint.contains("रु")) {
            return "NPR"
        }
        return stores.defaultCurrencyFor(host)
    }

    private data class ScriptPricing(
        val title: String? = null,
        val imageUrl: String? = null,
        val imageUrls: List<String> = emptyList(),
        val price: Double? = null,
        val listPrice: Double? = null,
        val currencyCode: String? = null,
        val brand: String? = null
    )

    private fun darazPriceText(document: Document): String? {
        val selectors = listOf(
            ".pdp-price",
            ".pdp-price_type_normal",
            "[class*=pdp-price] span",
            "[class*=pdp-price]",
            ".pdp-product-price span",
            ".pdp-product-price",
            "[data-spm=price] span",
            "span.price"
        )
        for (selector in selectors) {
            val text = document.selectFirst(selector)?.text()?.trim().orEmpty()
            if (text.isNotEmpty() && MoneyParser.parse(text) != null) return text
        }
        return null
    }

    /** Returns (currentPrice, listPrice) hints parsed from share URLs (e.g., Daraz/Lazada priceCompare). */
    private fun extractPriceHintsFromUrl(rawUrl: String): Pair<Double?, Double?> {
        val decoded = runCatching { java.net.URLDecoder.decode(rawUrl, "UTF-8") }.getOrElse { rawUrl }
        var current: Double? = null
        var list: Double? = null

        // priceCompare contains displayPrice/originPrice in minor units (e.g., 347000 => 3470.00)
        val compare = Regex("""priceCompare=([^&]+)""").find(decoded)?.groupValues?.getOrNull(1).orEmpty()
        val displayMinor = Regex("""displayPrice\s*[:=]\s*(\d+)""").find(compare)?.groupValues?.getOrNull(1)?.toLongOrNull()
        val originMinor = Regex("""originPrice\s*[:=]\s*(\d+)""").find(compare)?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (displayMinor != null && displayMinor > 0) current = displayMinor / 100.0
        if (originMinor != null && originMinor > 0) list = originMinor / 100.0

        // price=3.47E 3 / 3470 style hints
        if (current == null) {
            val p = Regex("""[?&]price=([^&]+)""").find(decoded)?.groupValues?.getOrNull(1)?.replace(" ", "")
            current = p?.toDoubleOrNull()?.takeIf { it > 0 } ?: MoneyParser.parse(p)
        }

        if (current != null && list != null && list <= current + 0.01) list = null
        return current to list
    }

    private fun extractPriceHistory(document: Document, currencyCode: String?, rawUrl: String): List<HistoricalPrice> {
        val code = currencyCode ?: stores.defaultCurrencyFor(document.location()?.let { runCatching { java.net.URI(it).host }.getOrNull() })
        val out = linkedMapOf<Long, HistoricalPrice>() // de-dupe by timestamp
        val html = document.outerHtml()

        // Generic: look for arrays/objects containing date+price pairs in scripts (best-effort).
        // Matches patterns like {"date":"2024-01-01","price":123.4} or {"timestamp":1700000000000,"value":123}
        val pointRegex = Regex("""\{[^{}]{0,120}?(?:"date"|"time"|"timestamp"|"recordedAt")\s*:\s*("?)([^"}]{1,30})\1[^{}]{0,120}?(?:"price"|"amount"|"value")\s*:\s*"?([0-9][0-9,]*(?:\.[0-9]+)?)"?[^{}]{0,40}\}""")
        pointRegex.findAll(html).take(200).forEach { m ->
            val tsRaw = m.groupValues[2]
            val priceRaw = m.groupValues[3]
            val price = MoneyParser.parse(priceRaw) ?: return@forEach
            val ts = parseHistoryTimestamp(tsRaw) ?: return@forEach
            // sanity: avoid ancient/future garbage, keep reasonable range
            if (ts < 946684800000L || ts > System.currentTimeMillis() + 86_400_000L) return@forEach
            if (price <= 0.0 || price > 1_000_000_000.0) return@forEach
            out[ts] = HistoricalPrice(price = price, currencyCode = code, recordedAt = ts)
        }

        // Daraz/Lazada: scan module data for explicit history arrays if present (rare; best-effort)
        val historyArray = Regex(""""(?:priceHistory|price_history|history)"\s*:\s*(\[[^\]]{0,5000}\])""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.getOrNull(1)
        if (historyArray != null) {
            runCatching {
                val arr = JSONArray(historyArray)
                for (i in 0 until minOf(arr.length(), 500)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val price = MoneyParser.parse(o.optString("price").ifBlank { o.optString("value").ifBlank { o.optString("amount") } })
                        ?: o.optDouble("price").takeIf { it > 0.0 }
                        ?: o.optDouble("value").takeIf { it > 0.0 }
                        ?: continue
                    val ts = parseHistoryTimestamp(o.optString("date").ifBlank { o.optString("time").ifBlank { o.optString("timestamp").ifBlank { o.optString("x") } } })
                        ?: o.optLong("timestamp").takeIf { it > 0 }
                        ?: continue
                    val tsNorm = if (ts < 10_000_000_000L) ts * 1000 else ts
                    if (tsNorm in 946684800000L..(System.currentTimeMillis() + 86_400_000L)) {
                        out[tsNorm] = HistoricalPrice(price = price, currencyCode = code, recordedAt = tsNorm)
                    }
                }
            }
        }

        return out.values.sortedBy { it.recordedAt ?: 0L }
    }

    private fun parseHistoryTimestamp(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val t = raw.trim()
        t.toLongOrNull()?.let { n ->
            return if (n < 10_000_000_000L) n * 1000 else n
        }
        // ISO-ish date (yyyy-MM-dd...)
        return runCatching {
            val datePart = Regex("""(\d{4})-(\d{2})-(\d{2})""").find(t) ?: return null
            val (y, m, d) = datePart.destructured
            java.time.LocalDate.of(y.toInt(), m.toInt(), d.toInt())
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun extractScriptPricing(document: Document): ScriptPricing? {
        val html = document.outerHtml()
        // Daraz/Lazada: pdpTrackingData = "{\"pdt_price\":\"Rs. 4,065\",...}"
        val tracking = Regex("""pdpTrackingData\s*=\s*"((?:\\.|[^"\\])*)"""")
            .find(html)?.groupValues?.getOrNull(1)?.let(::unescapeJsString)
            ?: Regex("""pdpTrackingData\s*=\s*(\{.*?\})\s*;""", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.getOrNull(1)

        if (tracking != null) {
            val obj = runCatching { JSONObject(tracking) }.getOrNull()
            if (obj != null) {
                val price = MoneyParser.parse(obj.optString("pdt_price"))
                    ?: MoneyParser.parse(obj.optString("salePrice"))
                    ?: MoneyParser.parse(obj.optString("price"))
                val listPrice = MoneyParser.parse(obj.optString("pdt_original_price"))
                    ?: MoneyParser.parse(obj.optString("originalPrice"))
                    ?: MoneyParser.parse(obj.optString("listPrice"))
                val currency = normalizeCurrency(obj.optJSONObject("core")?.optString("currencyCode"))
                    ?: normalizeCurrency(obj.optString("currencyCode"))
                val title = obj.optString("pdt_name").takeIf { it.isNotBlank() && it != "null" }
                val image = obj.optString("pdt_photo").takeIf { it.isNotBlank() && it != "null" }
                val brand = obj.optString("brand_name").takeIf { it.isNotBlank() && it != "null" && !it.equals("No Brand", true) }
                val scriptImages = extractScriptImageUrls(html)
                val allImages = (listOfNotNull(image) + scriptImages).distinct().take(12)
                if (price != null || title != null || allImages.isNotEmpty()) {
                    return ScriptPricing(title, image ?: allImages.firstOrNull(), allImages, price, listPrice, currency, brand)
                }
            }
        }

        val scriptImages = extractScriptImageUrls(html)
        // Generic fallback: look for JSON-like price fields in scripts
        val generic = Regex(""""(?:salePrice|price|currentPrice|amount)"\s*:\s*"?([0-9][0-9,]*(?:\.[0-9]+)?)"?""")
            .find(html)?.groupValues?.getOrNull(1)
        val parsed = MoneyParser.parse(generic)
        if (parsed != null || scriptImages.isNotEmpty()) {
            return ScriptPricing(imageUrl = scriptImages.firstOrNull(), imageUrls = scriptImages, price = parsed)
        }
        return null
    }

    private fun extractScriptImageUrls(html: String): List<String> {
        val urls = linkedSetOf<String>()
        fun add(raw: String?) {
            val u = normalizeImageUrl(raw?.replace("\\/", "/")?.replace("\\u002F", "/")) ?: return
            if (u.startsWith("data:")) return
            val lower = u.lowercase()
            if (lower.contains("1x1") || lower.contains("pixel") || lower.contains("placeholder") || lower.contains("sprite")) return
            urls += u
        }
        // Common gallery/image arrays in embedded JSON (Daraz/Lazada, Shopify, generic)
        val patterns = listOf(
            Regex(""""(?:images|gallery|imageUrls|image_urls|thumbnails)"\s*:\s*(\[[^\]]{2,4000}\])""", RegexOption.DOT_MATCHES_ALL),
            Regex(""""image"\s*:\s*(\[[^\]]{2,4000}\])""", RegexOption.DOT_MATCHES_ALL),
            Regex(""""(?:src|url|large|original|image)"\s*:\s*"(https?:\\?/\\?/[^"]+\.(?:jpg|jpeg|png|webp)[^"]*)"""", RegexOption.IGNORE_CASE),
            Regex("""https?://[^"'\s<>)]+\.(?:jpg|jpeg|png|webp)(?:\?[^"'\s<>)]*)?""", RegexOption.IGNORE_CASE)
        )
        patterns.take(3).forEach { r ->
            r.findAll(html).take(12).forEach { m ->
                val g = m.groupValues.getOrNull(1).orEmpty()
                if (g.startsWith("[")) {
                    runCatching {
                        val arr = JSONArray(g)
                        for (i in 0 until minOf(arr.length(), 20)) {
                            when (val v = arr.opt(i)) {
                                is String -> add(v)
                                is JSONObject -> add(v.optString("src").ifBlank { v.optString("url").ifBlank { v.optString("large").ifBlank { v.optString("original").ifBlank { v.optString("image") } } } })
                            }
                        }
                    }
                } else add(g.ifBlank { m.value })
            }
        }
        // last-resort: pick plausible product CDN image URLs (limited)
        if (urls.size < 2) {
            patterns.last().findAll(html).take(60).forEach { m ->
                val u = m.value
                val l = u.lowercase()
                if (l.contains("logo") || l.contains("icon") || l.contains("banner") || l.contains("sprite") || l.contains("payment")) return@forEach
                if (l.contains("slatic") || l.contains("alicdn") || l.contains("lazcdn") || l.contains("shopify") || l.contains("cloudfront") || l.contains("/product") || l.contains("image")) add(u)
                if (urls.size >= 12) return@forEach
            }
        }
        return urls.toList().take(12)
    }

    private fun unescapeJsString(value: String): String = value
        .replace("\\\"", "\"")
        .replace("\\/", "/")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\\", "\\")

    private fun extractDescription(jsonLd: JSONObject?, document: Document): String? {
        val fromJson = jsonLd?.optString("description")?.takeIf { it.isNotBlank() && it != "null" }
        val fromMeta = fromJson
            ?: document.metaContent("og:description")
            ?: document.metaContent("twitter:description")
            ?: document.metaContent("description")
            ?: document.selectFirst("[itemprop=description]")?.text()?.takeIf { it.isNotBlank() }

        if (!fromMeta.isNullOrBlank()) return cleanText(fromMeta, 4000)

        val selectors = listOf(
            "#productDescription", "#feature-bullets", ".pdp-product-desc", ".pdp-mod-product-badge-wrapper + div",
            ".html-content.pdp-product-highlights", ".html-content.detail-content", "[class*=product-description]",
            "[class*=ProductDescription]", "[class*=description][class*=product]", ".product-description",
            ".product__description", ".rte", "[data-product-description]", ".woocommerce-product-details__short-description",
            ".product-single__description", ".entry-content", ".pdp-product-detail"
        )
        for (sel in selectors) {
            val el = document.selectFirst(sel) ?: continue
            val text = cleanText(el.text(), 4000)
            if (text != null && text.length >= 20) return text
        }
        return null
    }

    private fun extractSpecs(jsonLd: JSONObject?, document: Document): List<Pair<String, String>> {
        val out = LinkedHashMap<String, String>()

        fun put(k: String?, v: String?) {
            val key = cleanText(k, 80) ?: return
            val value = cleanText(v, 240) ?: return
            if (key.length < 2 || value.length < 1) return
            if (key.equals("description", true)) return
            out.putIfAbsent(key, value)
        }

        // JSON-LD additionalProperty / PropertyValue
        val props = jsonLd?.opt("additionalProperty")
        when (props) {
            is JSONArray -> for (i in 0 until props.length()) {
                val p = props.optJSONObject(i) ?: continue
                put(p.optString("name").ifBlank { p.optString("propertyID") }, p.optString("value").ifBlank { p.optString("valueReference") })
            }
            is JSONObject -> put(props.optString("name"), props.optString("value"))
        }
        // JSON-LD other spec-ish fields
        listOf("sku", "mpn", "gtin13", "gtin", "model", "color", "material", "size", "weight").forEach { k ->
            val v = jsonLd?.optString(k)?.takeIf { it.isNotBlank() && it != "null" }
            if (v != null) put(k.replaceFirstChar { c -> c.uppercase() }, v)
        }

        // Tables (key/value rows)
        document.select("table").take(10).forEach { table ->
            val rows = table.select("tr")
            if (rows.size !in 2..120) return@forEach
            rows.forEach { tr ->
                val cells = tr.select("th, td")
                if (cells.size >= 2) {
                    put(cells[0].text(), cells.drop(1).joinToString(" ") { it.text() })
                }
            }
        }

        // Definition lists
        document.select("dl").take(8).forEach { dl ->
            val dts = dl.select("dt")
            val dds = dl.select("dd")
            if (dts.isNotEmpty() && dts.size == dds.size) {
                dts.forEachIndexed { i, dt -> put(dt.text(), dds.getOrNull(i)?.text()) }
            }
        }

        // Common spec list blocks (Daraz/generic): "key: value" or split label/value elements
        val specSelectors = listOf(
            ".pdp-general-features li", ".specification-keys li", "[class*=specification] li",
            "[class*=spec] li", "[class*=attribute] li", ".product-attributes li",
            ".woocommerce-product-attributes tr", ".key-value li"
        )
        for (sel in specSelectors) {
            document.select(sel).take(80).forEach { el ->
                val label = el.selectFirst(".key, [class*=key], [class*=label], [class*=name], th")?.text()
                val value = el.selectFirst(".value, [class*=value], td")?.text()
                if (!label.isNullOrBlank() && !value.isNullOrBlank()) {
                    put(label, value)
                } else {
                    val text = el.text().trim()
                    val parts = text.split(Regex("\\s*[:：]\\s*"), limit = 2)
                    if (parts.size == 2) put(parts[0], parts[1])
                }
            }
            if (out.size >= 20) break
        }

        // Daraz/Lazada often has "Brand", "SKU" etc in highlight/spec areas
        document.select("[class*=pdp-mod-specification] .key-title, [class*=specification] .key").take(60).forEach { keyEl ->
            val key = keyEl.text()
            val valEl = keyEl.nextElementSibling() ?: keyEl.parent()?.selectFirst(".key-value, [class*=value]")
            put(key, valEl?.text())
        }

        return out.entries
            .filter { (k, v) -> k.isNotBlank() && v.isNotBlank() && k.length <= 80 && v.length <= 300 }
            .take(60)
            .map { it.key to it.value }
    }

    private fun cleanText(raw: String?, maxLen: Int): String? {
        if (raw.isNullOrBlank()) return null
        val text = raw
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.isEmpty() || text.equals("null", true)) return null
        return text.take(maxLen)
    }

    private fun cleanTitle(raw: String, storeName: String): String {
        var title = raw.replace(Regex("\\s+"), " ").trim()
        title = title.substringBefore(" | ").trim()
        title = title.substringBefore(" - ").trim().ifBlank { title }
        val storeSuffixes = listOf(
            " : $storeName", " - $storeName", " | $storeName", " – $storeName",
            " | Amazon.com", " - Amazon.com", " | eBay"
        )
        storeSuffixes.forEach { suffix ->
            if (title.endsWith(suffix, ignoreCase = true)) {
                title = title.dropLast(suffix.length).trim()
            }
        }
        return title.ifBlank { raw.trim() }.take(180)
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> "https://$trimmed"
        }
        return withScheme.toHttpUrlOrNull()?.toString()
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
