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
import org.jsoup.nodes.Element
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
        val initialUrl = normalizeUrl(rawUrl)
            ?: return@withContext ScrapeResult.Failure("That doesn't look like a product link.")

        try {
            var url = initialUrl
            var body: String? = null
            var finalUrl = initialUrl
            var host = ""
            var attempts = 0
            // Some share links (e.g. s.daraz.com.np/s.XXXX) answer with an HTML "share bridge"
            // page that redirects via JavaScript / <meta refresh> instead of an HTTP redirect.
            // OkHttp cannot follow those, so resolve the embedded target and fetch it ourselves.
            while (attempts < 3 && body == null) {
                attempts++
                val request = browserRequest(url).build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext ScrapeResult.Failure(
                            "The store returned HTTP ${response.code}. It may be blocking price checks — you can enter the details by hand."
                        )
                    }
                    val pageBody = response.body?.string()
                    if (pageBody.isNullOrBlank()) {
                        return@withContext ScrapeResult.Failure("The page came back empty. Try the link again or add details by hand.")
                    }
                    finalUrl = response.request.url.toString()
                    host = response.request.url.host
                    val bridgeTarget = resolveShareBridgeTarget(pageBody, finalUrl)
                    if (bridgeTarget != null && bridgeTarget != finalUrl) {
                        url = bridgeTarget
                    } else {
                        body = pageBody
                    }
                }
            }

            val page = body
                ?: return@withContext ScrapeResult.Failure(
                    "Couldn't resolve that share link. Try opening it and copying the product URL."
                )
            val document = Jsoup.parse(page, finalUrl)
            val metadata = extract(document, finalUrl, host, rawUrl)
            // Hukut renders its prices client-side, so the HTML only carries the current price
            // (JSON-LD). Its product API exposes the struck-through original price too.
            // Daraz has the same problem: its PDP price block is hydrated by JS, so the catalog
            // AJAX endpoint is used to recover both the selling and original price.
            val enriched = when {
                host.endsWith("hukut.com", ignoreCase = true) -> enrichHukut(metadata, finalUrl)
                host.contains("daraz.", ignoreCase = true) ||
                    host.contains("lazada.", ignoreCase = true) -> enrichDaraz(metadata, finalUrl)
                else -> metadata
            }
            if (enriched.title == null && enriched.price == null) {
                ScrapeResult.Failure("We couldn't read this page. Add the details by hand and we'll still track the price.")
            } else {
                ScrapeResult.Success(enriched)
            }
        } catch (e: Exception) {
            ScrapeResult.Failure(
                "Couldn't reach that page (${e.javaClass.simpleName}). Check the link or enter the details by hand."
            )
        }
    }

    /**
     * Hukut (hukut.com) is a Next.js storefront that loads product data from its own API after
     * hydration. The server HTML only exposes the current price through JSON-LD, so the original
     * (struck-through) price is missing. Its product API carries both `price` (list) and
     * `sellingPrice`/`salePrice` (current) per variant, which is exactly what we need.
     */
    private fun enrichHukut(base: ProductMetadata, pageUrl: String): ProductMetadata {
        val slug = runCatching {
            java.net.URI(pageUrl).path.orEmpty()
                .trim('/')
                .substringAfterLast('/')
                .substringBefore(".html")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return base

        val identifier = fetchJsonObject(
            "https://hukut.com/api-server/v1/slug/${java.net.URLEncoder.encode(slug, "UTF-8")}"
        )?.optJSONObject("data")?.optString("identifier")?.takeIf { it.isNotBlank() } ?: return base
        val data = fetchJsonObject("https://hukut.com/api-server/v1/product/$identifier")
            ?.optJSONObject("data") ?: return base

        fun num(obj: JSONObject?, key: String): Double? {
            if (obj == null || !obj.has(key) || obj.isNull(key)) return null
            val value = obj.optDouble(key, Double.NaN)
            return value.takeIf { !it.isNaN() && it > 0.0 }
        }

        fun instant(raw: String?): Long? {
            val value = raw?.takeIf { it.isNotBlank() && !it.equals("null", true) } ?: return null
            return runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
        }

        fun active(startKey: String, endKey: String, obj: JSONObject?): Boolean {
            val now = System.currentTimeMillis()
            val start = instant(obj?.optString(startKey))
            val end = instant(obj?.optString(endKey))
            return (start == null || now >= start) && (end == null || now <= end)
        }

        val variants = data.optJSONArray("variants")
        val variant = if (variants == null) {
            null
        } else {
            (0 until variants.length())
                .mapNotNull { variants.optJSONObject(it) }
                .firstOrNull { it.optBoolean("isDefault") }
                ?: variants.optJSONObject(0)
        }

        val listPrice = num(variant, "price") ?: num(data, "price")
        val sellingPrice = num(variant, "sellingPrice") ?: num(data, "sellingPrice")
        val salePrice = num(variant, "salePrice") ?: num(data, "salePrice")

        var price = when {
            sellingPrice != null && (listPrice == null || sellingPrice <= listPrice) -> sellingPrice
            listPrice != null -> listPrice
            else -> null
        }
        if (salePrice != null && listPrice != null && salePrice < listPrice - 0.01 &&
            active("saleStart", "saleEnd", variant)
        ) {
            price = salePrice
        }

        // An active product-level offer can cut the price further (percentage or flat amount).
        val offer = data.optJSONObject("offer")
        val offerDiscount = num(offer, "discount")
        if (offer != null && offer.optBoolean("active", true) && offerDiscount != null &&
            active("startDate", "endDate", offer) && listPrice != null
        ) {
            val discounted = if (offer.optString("discountType").equals("percent", true)) {
                listPrice - listPrice * offerDiscount / 100.0
            } else {
                (listPrice - offerDiscount).coerceAtLeast(0.0)
            }
            if (discounted > 0.0 && discounted < listPrice - 0.01) {
                price = price?.let { minOf(it, discounted) } ?: discounted
            }
        }

        val resolvedList = listPrice?.takeIf { price != null && it > price + 0.01 }

        val imageUrls = buildList {
            val images = data.optJSONArray("productImages")
            if (images != null) {
                for (i in 0 until images.length()) {
                    val image = images.optJSONObject(i)?.optJSONObject("image") ?: continue
                    val url = image.optString("cdn").takeIf { it.isNotBlank() }
                        ?: image.optString("url").takeIf { it.isNotBlank() }
                    if (url != null) add(url)
                }
            }
            val variantImages = variant?.optJSONArray("variantImages")
            if (variantImages != null) {
                for (i in 0 until variantImages.length()) {
                    val image = variantImages.optJSONObject(i)?.optJSONObject("image") ?: continue
                    val url = image.optString("cdn").takeIf { it.isNotBlank() }
                        ?: image.optString("url").takeIf { it.isNotBlank() }
                    if (url != null) add(url)
                }
            }
        }.distinct().take(12)

        val apiTitle = data.optString("name").takeIf { it.isNotBlank() }
        val apiBrand = data.optJSONObject("brand")?.optString("name")?.takeIf { it.isNotBlank() }
        val marketStatus = variant?.optString("marketStatus")
        val inStock = when {
            marketStatus.equals("inStock", true) -> true
            marketStatus.equals("outOfStock", true) || marketStatus.equals("preOrder", true) -> false
            data.optBoolean("isUnavailable") -> false
            else -> base.inStock
        }

        return base.copy(
            title = apiTitle?.let { cleanTitle(it, base.storeName ?: "Hukut") } ?: base.title,
            imageUrl = imageUrls.firstOrNull() ?: base.imageUrl,
            imageUrls = imageUrls.ifEmpty { base.imageUrls },
            price = price ?: base.price,
            listPrice = resolvedList ?: if (price == null) base.listPrice else null,
            currencyCode = "NPR",
            brand = apiBrand ?: base.brand,
            inStock = inStock
        )
    }

    /**
     * Daraz/Lazada product pages render the price block client-side, so the server HTML often
     * carries only the original price (in `pdpTrackingData`) or an empty price div. The catalog
     * AJAX endpoint returns the current selling price, original price and discount for an item,
     * which is the reliable source for both the current price and the struck-through list price.
     */
    private fun enrichDaraz(base: ProductMetadata, pageUrl: String): ProductMetadata {
        val itemId = Regex("""-i(\d+)""", RegexOption.IGNORE_CASE)
            .find(pageUrl)?.groupValues?.getOrNull(1)
            ?: Regex("""[?&]itemId=(\d+)""", RegexOption.IGNORE_CASE)
                .find(pageUrl)?.groupValues?.getOrNull(1)
            ?: return base

        val host = runCatching { java.net.URI(pageUrl).host }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return base
        val catalogUrl =
            "https://$host/catalog/?q=${java.net.URLEncoder.encode(itemId, "UTF-8")}&ajax=true"
        val root = fetchJsonObject(catalogUrl, desktop = true) ?: return base
        val items = root.optJSONObject("mods")?.optJSONArray("listItems") ?: return base

        val item = (0 until items.length())
            .mapNotNull { items.optJSONObject(it) }
            .firstOrNull {
                it.optString("itemId") == itemId || it.optString("nid") == itemId
            } ?: return base

        fun money(vararg keys: String): Double? {
            for (key in keys) {
                if (!item.has(key) || item.isNull(key)) continue
                MoneyParser.parse(item.optString(key))?.let { return it }
                val value = item.optDouble(key, Double.NaN)
                if (!value.isNaN() && value > 0.0) return value
            }
            return null
        }

        val price = money("priceShow", "price", "salePrice", "sellingPrice") ?: return base
        val original = money(
            "originalPrice", "original_price", "mrp", "wasPrice", "was_price",
            "compareAtPrice", "compare_at_price", "listPrice"
        ) ?: listPriceFromDiscount(
            price,
            item.optString("discount").ifBlank { item.optString("discountText") }
        )

        val listPrice = original?.takeIf { it > price + 0.01 }
        val image = item.optString("image").takeIf { it.isNotBlank() }
        val title = item.optString("name").takeIf { it.isNotBlank() }
        return base.copy(
            title = title?.let { cleanTitle(it, base.storeName ?: "Daraz") } ?: base.title,
            imageUrl = image ?: base.imageUrl,
            imageUrls = if (image != null && base.imageUrls.isEmpty()) listOf(image) else base.imageUrls,
            price = price,
            listPrice = listPrice,
            currencyCode = base.currencyCode ?: "NPR"
        )
    }

    /** Fetches a small JSON document, returning null on any network/parse failure. */
    private fun fetchJsonObject(url: String, desktop: Boolean = false): JSONObject? = try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", if (desktop) DESKTOP_USER_AGENT else USER_AGENT)
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                response.body?.string()?.let { runCatching { JSONObject(it) }.getOrNull() }
            }
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Resolves the product URL embedded in a "share bridge" page (e.g. Daraz `s.daraz.com.np/s.*`).
     *
     * These pages return HTTP 200 and only redirect client-side, so [okhttp3.OkHttpClient] leaves us
     * on the bridge page. The real destination is exposed through `<link rel="origin">`, a
     * `<meta http-equiv="refresh">`, or the inline `REDIRECTURL` script.
     */
    private fun resolveShareBridgeTarget(html: String, pageUrl: String): String? {
        val isBridge = html.contains("share_bridge_page", ignoreCase = true) ||
            Regex("""<link[^>]+rel\s*=\s*["']origin["']""", RegexOption.IGNORE_CASE).containsMatchIn(html)
        if (!isBridge) return null

        fun absolute(raw: String?): String? {
            val value = raw?.trim()?.replace("&amp;", "&")?.takeIf { it.isNotEmpty() } ?: return null
            normalizeUrl(value)?.let { return it }
            return runCatching { java.net.URI(pageUrl).resolve(value).toString() }
                .getOrNull()
                ?.let(::normalizeUrl)
        }

        Regex("""<link[^>]+rel\s*=\s*["']origin["'][^>]*href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.let { return absolute(it) }
        Regex("""<link[^>]+href\s*=\s*["']([^"']+)["'][^>]*rel\s*=\s*["']origin["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.let { return absolute(it) }
        Regex(
            """<meta[^>]+http-equiv\s*=\s*["']refresh["'][^>]*content\s*=\s*["'][^"']*?url\s*=\s*([^"']+)["']""",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)?.let { return absolute(it) }
        Regex("""REDIRECTURL\s*=\s*new\s+URL\(\s*["']([^"']+)["']\s*\)""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.let { return absolute(it) }
        return null
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
        // Only treat JSON-LD highPrice as a list/original price when offers is a single object
        // (e.g. Daraz single-product PDP). For variant arrays, highPrice reflects a price range,
        // not a discount — see commit af9e0fb.
        val offersIsSingle = offers is JSONObject && offers != JSONObject.NULL
        val aggregateHigh = if (offersIsSingle) highPriceForOffers(offers) else null
        val originalPrice = extractOriginalPrice(document)

        val ogTitle = document.metaContent("og:title")
        val metaTitle = document.metaContent("twitter:title")
        val jsonLdTitle = jsonLd?.optString("name")?.takeIf { it.isNotBlank() }
        val htmlTitle = document.title().takeIf { it.isNotBlank() }

        val rawTitle = jsonLdTitle ?: ogTitle ?: metaTitle ?: htmlTitle
        val storeName = stores.displayNameForHost(host)

        val scriptData = extractScriptPricing(document, finalUrl)
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

        val urlHints = extractPriceHintsFromUrl(finalUrl)
        val (domCurrent, domOriginal) = extractDomProductPrices(document)
        // Store-specific visible price blocks (Amazon / Daraz) reliably distinguish the current
        // selling price from the struck-through original price, which the generic DOM parser
        // does not recognise on those storefronts.
        val (storeCurrent, storeOriginal) = extractStoreSpecificPrices(document, host)
        val currencyResolved = currency ?: scriptData?.currencyCode ?: stores.defaultCurrencyFor(host)

        var price = offerPrice
            ?: MoneyParser.parse(metaPrice)
            ?: MoneyParser.parse(document.selectFirst("[itemprop=price]")?.attr("content"))
            ?: darazPriceText(document)?.let(MoneyParser::parse)
            ?: scriptData?.price
            ?: domCurrent

        // `aggregateHigh` (JSON-LD highPrice/offer range) is used as list/original price only for
        // single-offer JSON-LD (e.g. Daraz). On variant/fabric products it reflects a price range,
        // not a discount — those cases return null above.
        var listPrice = originalPrice
            ?: scriptData?.listPrice
            ?: urlHints.second
            ?: domOriginal
            ?: aggregateHigh

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

        // DOM sale/original pair (e.g., WooCommerce <del>/<ins>, Ergokeys) is a strong product-scoped signal.
        val domHasSale = domCurrent != null && domOriginal != null && domOriginal > domCurrent + 0.01
        if (domHasSale) {
            val sale = domCurrent!!
            val orig = domOriginal!!
            fun normalizeUnits(value: Double?, ref: Double): Double? {
                val v = value ?: return null
                if (v > ref * 50.0 && kotlin.math.abs(v / 100.0 - ref) / kotlin.math.max(ref, 0.01) < 0.2) return v / 100.0
                if (v > ref * 50.0 && v % 1.0 == 0.0) return v / 100.0
                return v
            }
            price = normalizeUnits(price, sale) ?: price
            listPrice = normalizeUnits(listPrice, orig)
            when {
                price == null -> price = sale
                price!! >= orig - 0.01 && sale < orig - 0.01 -> {
                    // We likely captured the original/base price as current; correct to sale and keep original.
                    price = sale
                }
                sale < price!! - 0.01 -> {
                    // DOM indicates a lower sale/current price on the product.
                    if (listPrice == null || listPrice!! < price!! - 0.01) listPrice = price
                    price = sale
                }
            }
            val p = price
            // Prefer the confident DOM original for the displayed "was/list" price; drop wildly mismatched values.
            if (p != null && orig > p + 0.01) {
                val lp = listPrice
                if (lp == null || lp <= p + 0.01 || lp > orig * 5.0 || kotlin.math.abs(lp - orig) / kotlin.math.max(orig, 0.01) > 1.5) {
                    listPrice = orig
                } else if (lp < orig - 0.01) {
                    listPrice = orig
                }
            }
        }

        // Store-specific price pair is authoritative for the visible selling/struck price.
        if (storeCurrent != null) {
            val cur = storeCurrent
            val orig = storeOriginal?.takeIf { it > cur + 0.01 }
            val existing = price
            when {
                existing == null -> price = cur
                // We picked up the struck-through original as the current price — correct it.
                orig != null && existing >= orig - 0.01 && cur < orig - 0.01 -> price = cur
                cur < existing - 0.01 -> {
                    if (listPrice == null || listPrice!! < existing - 0.01) listPrice = existing
                    price = cur
                }
            }
            if (orig != null) {
                val p = price
                if (p != null && orig > p + 0.01 &&
                    (listPrice == null || listPrice!! <= p + 0.01 || listPrice!! > orig * 5.0)
                ) {
                    listPrice = orig
                }
            }
        }

        if (listPrice != null && price != null && listPrice <= price + 0.01) listPrice = null
        fun roundMoney(v: Double?): Double? = v?.let { kotlin.math.round(it * 100.0) / 100.0 }?.takeIf { it > 0.0 }
        price = roundMoney(price)
        listPrice = roundMoney(listPrice)

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
        val specs = extractSpecs(jsonLd, document, description)
        val inStock = extractStockStatus(jsonLd, document)

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
            specs = specs,
            inStock = inStock
        )
    }

    private fun extractOriginalPrice(document: Document): Double? {
        val candidates = listOf(
            "product:original_price:amount",
            "og:price:original_amount",
            "og:price:standard_amount",
            "product:regular_price:amount",
            "product:list_price:amount",
            "product:price:standard_amount",
            "product:price:regular_amount"
        )
        return candidates.firstNotNullOfOrNull { key -> MoneyParser.parse(document.metaContent(key)) }
    }

    private fun isInExcludedPriceContext(el: Element): Boolean {
        var cur: Element? = el
        var depth = 0
        while (cur != null && depth < 12) {
            val tag = cur.tagName().lowercase()
            if (tag in setOf("header", "footer", "nav", "aside")) return true
            val id = cur.id().lowercase()
            val cls = cur.className().lowercase()
            val hay = "$id $cls"
            val excludedTokens = listOf(
                "related", "recommend", "upsell", "cross-sell", "cross_sell", "crosssell",
                "you-may", "also-like", "also_like", "similar-product", "similar_product",
                "recently-viewed", "recently_viewed", "product-recommend", "complementary",
                "bought-together", "frequently-bought", "customers-also", "more-from", "more_from",
                "widget", "sidebar", "cart", "mini-cart", "minicart", "header-cart"
            )
            if (excludedTokens.any { hay.contains(it) }) return true
            if (cls.contains("related") && cls.contains("products")) return true
            if (cls.contains("upsells") || cls.contains("cross-sells")) return true
            cur = cur.parent()
            depth++
        }
        return false
    }

    /**
     * Returns (currentOrSalePrice, originalOrListPrice) from DOM product price blocks.
     * Handles WooCommerce `<del>/<ins>`, screen-reader text, and common sale/original selectors.
     */
    private fun extractDomProductPrices(document: Document): Pair<Double?, Double?> {
        fun parseAmount(el: Element?): Double? = MoneyParser.parse(el?.attr("data-price-amount"))
            ?: MoneyParser.parse(el?.text())
            ?: MoneyParser.parse(el?.attr("content"))
            ?: MoneyParser.parse(el?.attr("data-price"))

        // Magento 2 (e.g. mudita.com.np): the authoritative values are in data-price-amount
        // attributes on [data-price-type=finalPrice] / [data-price-type=oldPrice]. The visible
        // text uses locale grouping ("NPR 2,31,999"), so prefer the raw attribute.
        val typedPrices = document.select("[data-price-type]")
        fun typedAmount(type: String): Double? = typedPrices
            .firstOrNull { it.attr("data-price-type").equals(type, ignoreCase = true) }
            ?.let { el ->
                MoneyParser.parse(el.attr("data-price-amount")) ?: MoneyParser.parse(el.text())
            }
        val magentoFinal = typedAmount("finalPrice")
        if (magentoFinal != null) {
            val magentoOld = typedAmount("oldPrice")
            return if (magentoOld != null && magentoOld > magentoFinal + 0.01) {
                magentoFinal to magentoOld
            } else {
                magentoFinal to null
            }
        }

        val containerSelectors = listOf(
            ".summary.entry-summary .price",
            ".entry-summary .price",
            ".product-summary .price",
            "[class*=product-summary] .price",
            ".single-product .summary .price",
            "main .product .price",
            "main .entry-summary p.price",
            "p.price",
            ".product .woocommerce-Price-amount",
            ".pdp-product-price",
            ".pdp-price",
            "[class*=pdp-price]",
            ".product-price",
            "[class*=ProductPrice]",
            ".price-box",
            "[data-role=priceBox]",
            ".product-info-main .price-box",
            "[itemprop=offers] [itemprop=price]"
        )

        for (sel in containerSelectors) {
            for (container in document.select(sel).take(8)) {
                if (isInExcludedPriceContext(container)) continue

                // WooCommerce / common: <del> original, <ins> sale
                val del = container.selectFirst("del .amount, del .woocommerce-Price-amount, del [class*=amount], del bdi, del")
                val ins = container.selectFirst("ins .amount, ins .woocommerce-Price-amount, ins [class*=amount], ins bdi, ins")
                val delPrice = parseAmount(del)
                val insPrice = parseAmount(ins)
                if (delPrice != null && insPrice != null && delPrice > insPrice + 0.01) {
                    return insPrice to delPrice
                }

                // Screen-reader accessibility text (WooCommerce)
                var srOriginal: Double? = null
                var srCurrent: Double? = null
                for (sr in container.select(".screen-reader-text, .sr-only, [class*=screen-reader]")) {
                    val t = sr.text().lowercase()
                    val amount = parseAmount(sr.nextElementSibling()) ?: run {
                        val parent = sr.parent() ?: return@run null
                        parseAmount(parent.selectFirst(".amount, .woocommerce-Price-amount, bdi"))
                    } ?: MoneyParser.parse(t)
                    when {
                        t.contains("original price") || t.contains("regular price") -> if (amount != null) srOriginal = amount
                        t.contains("current price") || t.contains("sale price") || t.contains("discounted price") -> if (amount != null) srCurrent = amount
                    }
                }
                if (srOriginal != null && srCurrent != null && srOriginal > srCurrent + 0.01) {
                    return srCurrent to srOriginal
                }
                if (srCurrent != null && srOriginal == null && insPrice == null && delPrice != null && delPrice > srCurrent + 0.01) {
                    return srCurrent to delPrice
                }

                // Explicit sale/original classes within container
                val saleEl = container.selectFirst(".sale-price .amount, .price--sale .amount, [class*=sale-price] .amount, [class*=current-price] .amount, [class*=special-price] .amount, .special-price .price, ins .price")
                val origEl = container.selectFirst(".regular-price .amount, .price--regular .amount, [class*=regular-price] .amount, [class*=original-price] .amount, [class*=compare-price] .amount, [class*=old-price] .amount, [class*=list-price] .amount, [class*=was-price] .amount, del .price")
                val saleP = parseAmount(saleEl) ?: insPrice
                val origP = parseAmount(origEl) ?: delPrice
                if (saleP != null && origP != null && origP > saleP + 0.01) {
                    return saleP to origP
                }
                if (saleP != null && origP == null) {
                    // current-only in a sale-marked element; keep looking for original in same container
                    val extraOrig = container.select(".amount, .woocommerce-Price-amount, bdi").mapNotNull { parseAmount(it) }
                        .filter { it > saleP + 0.01 }.maxOrNull()
                    if (extraOrig != null) return saleP to extraOrig
                }

                // Two amount elements where first is struck-through context? (handled by del above). Otherwise ambiguous.
            }
        }
        return null to null
    }

    private fun Document.metaContent(key: String): String? =
        when (key) {
            "price" -> selectFirst("meta[property=product:price]")?.attr("content")
            else -> selectFirst("meta[property=$key]")?.attr("content")
                ?: selectFirst("meta[name=$key]")?.attr("content")
        }?.trim()?.takeIf { it.isNotEmpty() }

    private fun extractImageUrls(jsonLd: JSONObject?, document: Document, scriptImages: List<String> = emptyList()): List<String> {
        val urls = linkedSetOf<String>()

        fun addUrl(raw: String?, el: Element? = null, allowMetaLogoRisk: Boolean = false) {
            val u = normalizeImageUrl(raw) ?: return
            if (u.length > 2000 || u.startsWith("data:")) return
            if (isJunkOrLogoImage(u, el, allowMetaLogoRisk)) return
            urls += u
        }

        fun addFromEl(img: Element) {
            val candidate = img.attr("data-zoom-image").ifBlank {
                img.attr("data-large-image").ifBlank {
                    img.attr("data-big").ifBlank {
                        img.attr("data-src").ifBlank {
                            img.attr("data-lazy-src").ifBlank {
                                img.attr("data-original").ifBlank { img.attr("src") }
                            }
                        }
                    }
                }
            }
            addUrl(candidate, img)
        }

        // 1) JSON-LD Product image (highest confidence)
        jsonLd?.opt("image")?.let { image ->
            when (image) {
                is String -> addUrl(image)
                is JSONArray -> for (i in 0 until image.length()) {
                    when (val v = image.opt(i)) {
                        is String -> addUrl(v)
                        is JSONObject -> addUrl(v.optString("url").ifBlank { v.optString("contentUrl").ifBlank { v.optString("@id") } })
                    }
                }
                is JSONObject -> addUrl(image.optString("url").ifBlank { image.optString("contentUrl") })
            }
        }

        // 2) Product-scoped gallery images (avoid related/recommended/header/footer/logo blocks)
        val productGallerySelectors = listOf(
            // Daraz / Lazada PDP
            ".pdp-mod-common-image img", ".gallery-preview-panel img", ".next-slick-slide img",
            ".pdp-block__main-image img", "[class*=pdp-mod-common-image] img",
            // WooCommerce / common storefront galleries
            ".woocommerce-product-gallery__image img", ".woocommerce-product-gallery__wrapper img",
            "figure.woocommerce-product-gallery__wrapper img", ".woocommerce-product-gallery img",
            ".product-gallery img", "[class*=product-gallery__image] img", "[data-product-gallery] img",
            ".product__media img", ".product-single__media img", ".product__photo img",
            ".Product__SlideItem img", "[class*=ProductGallery] img", "[class*=productGallery] img",
            // Generic product image/gallery containers (still filtered by exclusion context)
            "[class*=gallery-preview] img", "[class*=item-gallery] img", "[class*=image-gallery] img"
        )
        for (sel in productGallerySelectors) {
            document.select(sel).take(30).forEach { img ->
                if (isInExcludedImageContext(img)) return@forEach
                addFromEl(img)
            }
            if (urls.size >= 12) break
        }

        // 3) Meta / itemprop fallbacks (filtered for logos/junk)
        if (urls.size < 8) {
            document.select("meta[property=og:image], meta[property=og:image:secure_url], meta[name=og:image], meta[name=twitter:image], meta[property=product:image]")
                .forEach { addUrl(it.attr("content").ifBlank { it.attr("value") }) }
            document.select("[itemprop=image]").forEach { el ->
                if (!isInExcludedImageContext(el)) addUrl(el.attr("content").ifBlank { el.attr("src").ifBlank { el.attr("href") } }, el)
            }
            document.select("link[rel=image_src]").forEach { addUrl(it.attr("href")) }
        }

        // 4) Script-embedded images (Daraz/Lazada/Shopify/generic) - filtered
        scriptImages.forEach { addUrl(it) }

        // 5) Conservative fallback: scoped gallery/carousel/zoom images only, never site-wide loose img[data-src]
        if (urls.size < 3) {
            val scopedSelectors = listOf(
                ".pdp-mod-common-image img", ".gallery-preview-panel img",
                ".woocommerce-product-gallery img", ".product-gallery img",
                "[class*=product-gallery] img", "[class*=image-gallery] img",
                "[class*=gallery-preview] img", "[class*=item-gallery] img",
                "main [class*=gallery] img", "main [class*=carousel] img", "main [class*=slider] img",
                "[data-gallery-role=gallery-placeholder] img", "img[data-zoom-image]", "img[data-large-image]"
            )
            for (sel in scopedSelectors) {
                document.select(sel).take(24).forEach { img ->
                    if (isInExcludedImageContext(img)) return@forEach
                    addFromEl(img)
                }
                if (urls.size >= 8) break
            }
        }

        return urls.toList().take(12)
    }

    private fun isJunkOrLogoImage(url: String, el: Element?, allowMetaLogoRisk: Boolean): Boolean {
        val lower = url.lowercase()
        if (lower.startsWith("data:")) return true
        val urlSignals = listOf(
            "1x1", "pixel", "placeholder", "sprite.gif", "spacer", "blank.gif", "transparent.",
            "/logo", "_logo", "-logo", "logo.", "logo/", "site-logo", "header-logo", "footer-logo",
            "brandlogo", "store-logo", "favicon", "apple-touch-icon", "payment", "badge", "trust",
            "social-share", "spinner", "loading", "ajax-loader", "avatar"
        )
        if (urlSignals.any { lower.contains(it) }) return true
        // "icon" is noisy in CDNs; keep strong forms
        if (lower.contains("/icon") || lower.contains("_icon") || lower.contains("-icon.") || lower.contains("icon-") || lower.contains("icons/")) return true

        val alt = el?.attr("alt")?.lowercase().orEmpty()
        val cls = (el?.className().orEmpty() + " " + el?.id().orEmpty() + " " + el?.parent()?.className().orEmpty()).lowercase()
        val domSignals = listOf("logo", "site-branding", "brand-logo", "header-logo", "favicon", "icon", "payment", "badge", "trust")
        if (domSignals.any { alt.contains(it) || cls.contains(it) }) return true
        if (!allowMetaLogoRisk && (alt == "logo" || cls.contains("navbar-brand"))) return true
        return false
    }

    private fun isInExcludedImageContext(el: Element): Boolean {
        var cur: Element? = el
        var depth = 0
        while (cur != null && depth < 10) {
            val tag = cur.tagName().lowercase()
            if (tag in setOf("header", "footer", "nav", "aside")) return true
            val id = cur.id().lowercase()
            val cls = cur.className().lowercase()
            val hay = "$id $cls"
            val excludedTokens = listOf(
                "logo", "site-header", "page-header", "masthead", "site-footer", "page-footer",
                "related", "recommend", "upsell", "cross-sell", "cross_sell", "crosssell",
                "you-may", "also-like", "also_like", "similar-product", "similar_product",
                "recently-viewed", "recently_viewed", "product-recommend", "complementary",
                "bought-together", "frequently-bought", "customers-also", "more-from", "more_from"
            )
            if (excludedTokens.any { hay.contains(it) }) return true
            // common WooCommerce blocks
            if (cls.contains("related") && cls.contains("products")) return true
            if (cls.contains("upsells") || cls.contains("cross-sells")) return true
            cur = cur.parent()
            depth++
        }
        return false
    }

    private fun normalizeImageUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var u = raw.trim().trim('"', '\'').replace("\\/", "/").replace("\\u002F", "/")
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

    private fun extractStoreSpecificPrices(document: Document, host: String): Pair<Double?, Double?> {
        val h = host.lowercase()
        return when {
            h.contains("amazon.") || h.contains("amzn.") -> extractAmazonPrices(document)
            h.contains("daraz.") || h.contains("lazada.") -> extractDarazPrices(document)
            else -> null to null
        }
    }

    /**
     * Amazon renders the selling price and the struck-through list price in separate `.a-price`
     * blocks. `.a-text-price` / `.basisPrice` mark the original price, everything else is current.
     */
    private fun extractAmazonPrices(document: Document): Pair<Double?, Double?> {
        fun amount(el: Element?): Double? {
            el ?: return null
            val text = el.text()
            if (text.contains('%')) return null
            // Amazon's hidden `.a-offscreen` text occasionally loses its decimal separator
            // ("$26121" instead of "$261.21"), so rebuild from the visible whole/fraction parts.
            val wholeDigits = el.selectFirst(".a-price-whole")?.text()
                ?.filter { it.isDigit() }.orEmpty()
            if (wholeDigits.isNotEmpty()) {
                val whole = wholeDigits.toDoubleOrNull()
                val fractionDigits = el.selectFirst(".a-price-fraction")?.text()
                    ?.filter { it.isDigit() }.orEmpty()
                if (whole != null) {
                    return if (fractionDigits.isNotEmpty()) {
                        whole + fractionDigits.toDouble() /
                            Math.pow(10.0, fractionDigits.length.toDouble())
                    } else {
                        whole
                    }
                }
            }
            return MoneyParser.parse(el.selectFirst(".a-offscreen")?.text())
                ?: MoneyParser.parse(text)
                ?: MoneyParser.parse(el.attr("content"))
        }

        fun isOriginalBlock(el: Element): Boolean {
            val cls = el.className().lowercase()
            if (cls.contains("a-text-price") || cls.contains("basisprice")) return true
            return el.parents().any { parent ->
                val c = parent.className().lowercase()
                c.contains("a-text-price") || c.contains("basisprice")
            }
        }

        fun firstAmount(vararg selectors: String): Double? {
            for (sel in selectors) {
                for (el in document.select(sel).take(3)) {
                    amount(el)?.let { return it }
                }
            }
            return null
        }

        fun firstCurrent(vararg selectors: String): Double? {
            for (sel in selectors) {
                for (el in document.select(sel).take(3)) {
                    if (isOriginalBlock(el)) continue
                    amount(el)?.let { return it }
                }
            }
            return null
        }

        var current = firstCurrent(
            "#corePrice_feature_div .priceToPay",
            "#corePriceDisplay_desktop_feature_div .priceToPay",
            "#corePriceDisplay_mobile_feature_div .priceToPay",
            "#corePrice_feature_div .a-price",
            "#corePriceDisplay_desktop_feature_div .a-price",
            "#corePriceDisplay_mobile_feature_div .a-price",
            "#priceblock_ourprice",
            "#priceblock_dealprice",
            "#priceblock_saleprice",
            "#tp_price_block_total_price_ww .a-price",
            "#price_inside_buybox",
            "#newBuyBoxPrice",
            "#apex_desktop .priceToPay",
            "#buybox .priceToPay"
        )

        var original = firstAmount(
            "#corePrice_feature_div .basisPrice",
            "#corePriceDisplay_desktop_feature_div .basisPrice",
            "#corePriceDisplay_mobile_feature_div .basisPrice",
            "#corePrice_feature_div .a-text-price",
            "#corePriceDisplay_desktop_feature_div .a-text-price",
            "#corePriceDisplay_mobile_feature_div .a-text-price",
            "#listPrice",
            "#priceblock_listprice"
        )

        // Some Amazon variants only expose a savings badge ("-25%"); derive the original from it.
        if (original == null && current != null) {
            val pctText = document.selectFirst("#corePrice_feature_div .savingsPercentage, .savingsPercentage")
                ?.text()
            original = listPriceFromDiscount(current, pctText)
        }

        if (current == null && original != null) {
            current = original
            original = null
        }
        if (original != null && current != null && original <= current + 0.01) original = null
        return current to original
    }

    /**
     * Daraz/Lazada price block: `pdp-price_type_normal` is the selling price, while
     * `pdp-price_type_delete` (struck-through) is the original. The block also carries a
     * discount badge ("-20%") we can fall back to when only the current price is rendered.
     */
    private fun extractDarazPrices(document: Document): Pair<Double?, Double?> {
        val blocks = linkedSetOf<Element>()
        listOf(
            ".pdp-product-price",
            ".pdp-mod-product-price",
            "[data-qa-locator=product-price]",
            "[class*=pdp-product-price]"
        ).forEach { sel -> document.select(sel).take(4).forEach { blocks += it } }
        if (blocks.isEmpty()) {
            document.selectFirst("[class*=pdp-price]")?.let { blocks += it }
        }

        fun isOriginal(el: Element): Boolean {
            val cls = el.className().lowercase()
            return cls.contains("delete") || cls.contains("strike") || cls.contains("original") ||
                cls.contains("old-price") || cls.contains("line-through")
        }

        fun priceOf(el: Element): Double? {
            val cls = el.className().lowercase()
            if (cls.contains("discount") || cls.contains("percent")) return null
            val text = el.text()
            if (text.contains('%')) return null
            return MoneyParser.parse(text)
        }

        for (block in blocks) {
            val priceEls = block.select("[class*=pdp-price], [class*=price]")
                .toList()
                .filter { priceOf(it) != null }
            if (priceEls.isEmpty()) continue

            val current = priceEls.firstOrNull { !isOriginal(it) }?.let { priceOf(it) }
                ?: priceEls.mapNotNull { priceOf(it) }.minOrNull()
                ?: continue
            var original = priceEls.firstOrNull { isOriginal(it) }
                ?.let { priceOf(it) }
                ?.takeIf { it > current + 0.01 }

            if (original == null) {
                val discountText = block.selectFirst("[class*=discount]")?.text()
                original = listPriceFromDiscount(current, discountText)
            }
            return current to original
        }
        return null to null
    }

    private fun darazPriceText(document: Document): String? {
        // Prefer the selling-price element and never read the struck-through original ("delete")
        // or the discount badge ("-20%").
        val scoped = document.select(".pdp-product-price, .pdp-mod-product-price, [class*=pdp-product-price]")
            .toList()
            .flatMap { block -> block.select(".pdp-price, [class*=pdp-price]").toList() }
        val candidates = if (scoped.isNotEmpty()) {
            scoped
        } else {
            document.select(".pdp-price, [class*=pdp-price]").toList()
        }
        for (el in candidates) {
            val cls = el.className().lowercase()
            if (cls.contains("delete") || cls.contains("strike") || cls.contains("original") ||
                cls.contains("old-price") || cls.contains("discount")
            ) continue
            val text = el.text().trim()
            if (text.isEmpty() || text.contains('%')) continue
            if (MoneyParser.parse(text) != null) return text
        }
        for (selector in listOf("[data-spm=price] span", "span.price")) {
            val text = document.selectFirst(selector)?.text()?.trim().orEmpty()
            if (text.isNotEmpty() && !text.contains('%') && MoneyParser.parse(text) != null) return text
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

    private fun extractTargetedScriptPricing(html: String, pageUrl: String): ScriptPricing? {
        val host = runCatching { java.net.URI(pageUrl).host?.lowercase().orEmpty() }.getOrElse { "" }
        val path = runCatching { java.net.URI(pageUrl).path.orEmpty() }.getOrElse { "" }

        extractJeeveePricing(html, host)?.let { return it }
        extractIttiPricing(html, host, path, pageUrl)?.let { return it }

        // DBS+/Next.js storefronts (e.g. sharmazacoustic.com): /product/<id> with RSC-escaped JSON
        // NOTE: use regular (non-raw) strings here so $productId / $field are interpolated.
        // Raw strings """...""" would treat them as literal text and the regex would never match.
        val productId = Regex("""/product/(\d+)""").find(path)?.groupValues?.getOrNull(1)
        if (productId != null) {
            val idPatterns = listOf(
                // \"id\" : \"<id>\"  or  "id" : "<id>"  (RSC-escaped or plain, quoted value)
                Regex("\\\\?\\\"id\\\\?\\\"\\s*:\\\\?\\\"$productId\\\\?\\\""),
                Regex("\"id\"\\s*:\\s*\"$productId\""),
                // \"id\" : <id>  or  "id" : <id>  (unquoted numeric value)
                Regex("\\\\?\\\"id\\\\?\\\"\\s*:\\s*$productId\\\\b"),
                Regex("\"id\"\\s*:\\s*$productId\\\\b")
            )
            for (idPattern in idPatterns) {
                val match = idPattern.find(html) ?: continue
                val start = (match.range.first - 120).coerceAtLeast(0)
                val end = (match.range.last + 3500).coerceAtMost(html.length)
                val window = html.substring(start, end)
                fun num(field: String): Double? {
                    val p = Regex("\\\\?\\\"$field\\\\?\\\"\\s*:\\s*\\\"?([0-9][0-9,]*(?:\\.[0-9]+)?)\\\"?")
                    return p.findAll(window).mapNotNull { MoneyParser.parse(it.groupValues[1]) }
                        .firstOrNull { it > 0.0 }
                }
                val name = Regex("""\\?"name\\?"\s*:\s*\\?"((?:\\.|[^"\\])*)\\?"""").find(window)
                    ?.groupValues?.getOrNull(1)
                    ?.let(::unescapeJsString)
                    ?.replace("\\\"", "\"")
                    ?.trim()
                    ?.takeIf { it.length in 2..180 && !it.equals("null", true) }
                val sale = num("price") ?: num("salePrice") ?: num("sellingPrice") ?: num("currentPrice")
                val list = num("originalPrice") ?: num("compareAtPrice") ?: num("regularPrice") ?: num("listPrice")
                if (sale != null) {
                    val fixedList = if (list != null && list > sale + 0.01) list else null
                    return ScriptPricing(
                        title = name,
                        imageUrl = null,
                        imageUrls = emptyList(),
                        price = sale,
                        listPrice = fixedList,
                        currencyCode = if (host.contains("sharmazacoustic")) "NPR" else null,
                        brand = null
                    )
                }
            }
        }
        return null
    }

    private fun extractIttiPricing(html: String, host: String, path: String, pageUrl: String): ScriptPricing? {
        if (!host.contains("itti.com.np")) return null
        val slug = path.trim('/').substringAfterLast('/').takeIf { it.length > 3 }
        fun parsePriceObject(window: String): Pair<Double, Double?>? {
            val mark = Regex("""\\?"mark_price\\?"\s*:\s*"?([0-9][0-9,]*(?:\.[0-9]+)?)"?""")
                .findAll(window).mapNotNull { MoneyParser.parse(it.groupValues[1]) }.firstOrNull { it > 0.0 }
            val selling = Regex("""\\?"selling_price\\?"\s*:\s*"?([0-9][0-9,]*(?:\.[0-9]+)?)"?""")
                .findAll(window).mapNotNull { MoneyParser.parse(it.groupValues[1]) }.firstOrNull { it > 0.0 }
            if (selling == null) return null
            val list = if (mark != null && mark > selling + 0.01) mark else null
            return selling to list
        }
        val anchors = buildList {
            if (slug != null) {
                Regex(Regex.escape(slug)).findAll(html).take(6).forEach { add(it.range.first) }
            }
            Regex("""["']?slug["']?\s*:\s*\\?"${Regex.escape(slug.orEmpty())}\\?"""", RegexOption.IGNORE_CASE)
                .findAll(html).take(4).forEach { add(it.range.first) }
        }.filter { it >= 0 }.distinct()
        for (anchor in anchors) {
            val start = (anchor - 200).coerceAtLeast(0)
            val end = (anchor + 8000).coerceAtMost(html.length)
            parsePriceObject(html.substring(start, end))?.let { (sale, list) ->
                val name = Regex("""\\?"(?:name|title|pdt_name)\\?"\s*:\s*\\?"((?:\\.|[^"\\])*)\\?"""")
                    .find(html.substring(start, (anchor + 2500).coerceAtMost(html.length)))
                    ?.groupValues?.getOrNull(1)
                    ?.let(::unescapeJsString)?.replace("\\\"", "\"")?.trim()
                    ?.takeIf { it.length in 2..180 && !it.equals("null", true) }
                val image = Regex("""\\?"(?:image|thumb_image|pdt_photo)\\?"\s*:\s*\\?"(https?:\\?/\\?/[^"\\\s]+)\\?"""")
                    .find(html.substring(start, end))?.groupValues?.getOrNull(1)
                    ?.replace("\\/", "/")
                return ScriptPricing(
                    title = name,
                    imageUrl = image,
                    imageUrls = listOfNotNull(image),
                    price = sale,
                    listPrice = list,
                    currencyCode = "NPR",
                    brand = null
                )
            }
        }
        // Fallback: any mark/selling pair (prefer plausible main-product range if slug context failed)
        parsePriceObject(html)?.let { (sale, list) ->
            return ScriptPricing(
                title = null,
                imageUrl = null,
                imageUrls = emptyList(),
                price = sale,
                listPrice = list,
                currencyCode = "NPR",
                brand = null
            )
        }
        return null
    }

    private fun extractJeeveePricing(html: String, host: String): ScriptPricing? {
        if (!host.contains("jeevee.")) return null
        val nextData = Regex("""<script[^>]+id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.getOrNull(1)?.trim()
        if (nextData.isNullOrEmpty()) return null
        val root = runCatching { JSONObject(nextData) }.getOrNull() ?: return null
        val data = root.optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("data") ?: return null

        fun optDouble(obj: JSONObject?, key: String): Double? {
            if (obj == null || !obj.has(key) || obj.isNull(key)) return null
            return MoneyParser.parse(obj.opt(key)?.toString())
        }
        fun round(v: Double?): Double? = v?.let { kotlin.math.round(it * 100.0) / 100.0 }?.takeIf { it > 0.0 }

        val info = data.optJSONArray("product_information")?.optJSONObject(0)
        val mrp = optDouble(info, "price") ?: optDouble(data, "price")
        if (mrp == null || mrp <= 0.0) return null

        val discountPct = optDouble(info?.optJSONObject("discount_object"), "discount")
            ?: optDouble(info, "discount")
            ?: optDouble(data.optJSONObject("discount_object"), "discount")
            ?: optDouble(data, "discount")

        val explicitSale = optDouble(info, "selling_price")
            ?: optDouble(info, "discounted_price")
            ?: optDouble(info, "current_price")
            ?: optDouble(info, "sale_price")
            ?: optDouble(info, "offer_price")
            ?: optDouble(data, "selling_price")
            ?: optDouble(data, "discounted_price")
            ?: optDouble(data, "current_price")

        val sale = when {
            explicitSale != null && explicitSale > 0.0 && explicitSale < mrp - 0.01 -> explicitSale
            discountPct != null && discountPct > 0.0 && discountPct < 100.0 -> {
                val computed = mrp * (1.0 - discountPct / 100.0)
                if (computed > 0.0 && computed < mrp - 0.01) computed else null
            }
            else -> null
        }

        val (price, listPrice) = if (sale != null) sale to mrp else mrp to null
        val image = data.optJSONArray("image")?.optJSONObject(0)?.let { img ->
            img.optString("1920").takeIf { it.isNotBlank() }
                ?: img.optString("1024").takeIf { it.isNotBlank() }
                ?: img.optString("512").takeIf { it.isNotBlank() }
        } ?: info?.optJSONArray("image")?.optJSONObject(0)?.let { img ->
            img.optString("1920").takeIf { it.isNotBlank() }
                ?: img.optString("1024").takeIf { it.isNotBlank() }
        }
        val brand = data.optJSONObject("brand")?.optString("name")?.takeIf { it.isNotBlank() }
        val title = data.optString("label").takeIf { it.isNotBlank() }
            ?: data.optString("name").takeIf { it.isNotBlank() }

        return ScriptPricing(
            title = title,
            imageUrl = image,
            imageUrls = listOfNotNull(image),
            price = round(price),
            listPrice = round(listPrice),
            currencyCode = "NPR",
            brand = brand
        )
    }

    private fun extractMuditaPricing(html: String, host: String): ScriptPricing? {
        if (!host.contains("mudita.com.np")) return null

        // Mudita is a WooCommerce store; look for wc-product data or JSON-LD price pairs.
        // Try __NEXT_DATA__ or similar embedded JSON first.
        val nextData = Regex("""<script[^>]+id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.getOrNull(1)?.trim()
        if (nextData?.isNotEmpty() == true) {
            val root = runCatching { JSONObject(nextData) }.getOrNull() ?: return null

            fun optPrice(obj: JSONObject, key: String): Double? {
                if (!obj.has(key) || obj.isNull(key)) return null
                return MoneyParser.parse(obj.opt(key)?.toString())?.takeIf { it > 0.0 }
            }

            fun walkPrice(obj: Any?): Pair<Double, Double?>? {
                when (obj) {
                    is JSONObject -> {
                        val sale = optPrice(obj, "sale_price")
                            ?: optPrice(obj, "selling_price")
                            ?: optPrice(obj, "current_price")
                            ?: optPrice(obj, "price")
                        val list = optPrice(obj, "regular_price")
                            ?: optPrice(obj, "original_price")
                            ?: optPrice(obj, "compare_at_price")
                            ?: optPrice(obj, "list_price")
                        if (sale != null && list != null && list > sale + 0.01) {
                            return sale to list
                        }
                        if (sale != null) return sale to null
                        // Recurse into nested objects/arrays
                        for (key in obj.keys()) {
                            val v = obj.opt(key)
                            walkPrice(v)?.let { return it }
                        }
                    }
                    is JSONArray -> {
                        for (i in 0 until obj.length()) {
                            walkPrice(obj.opt(i))?.let { return it }
                        }
                    }
                }
                return null
            }

            walkPrice(root)?.let { (sale, list) ->
                return ScriptPricing(
                    price = sale,
                    listPrice = list,
                    currencyCode = "NPR"
                )
            }
        }
        return null
    }

    /**
     * Daraz/Lazada PDPs embed the selected SKU's price pair as structured objects:
     * `"salePrice":{"value":1199}` and `"originalPrice":{"value":1500}`. The visible DOM is
     * preferred, but this rescues pages that render the price block client-side.
     */
    private fun extractDarazScriptPricing(html: String): ScriptPricing? {
        val anchor = html.indexOf("\"skuInfos\"")
        if (anchor < 0) return null
        val window = html.substring(anchor, (anchor + 12_000).coerceAtMost(html.length))

        fun money(pattern: String): Double? =
            Regex(pattern, RegexOption.IGNORE_CASE).find(window)
                ?.groupValues?.getOrNull(1)?.let(MoneyParser::parse)

        val sale = money(""""salePrice"\s*:\s*\{\s*"value"\s*:\s*"?([0-9][0-9,]*(?:\.[0-9]+)?)"?""")
            ?: money(""""price"\s*:\s*\{\s*"value"\s*:\s*"?([0-9][0-9,]*(?:\.[0-9]+)?)"?""")
        val original = money(""""originalPrice"\s*:\s*\{\s*"value"\s*:\s*"?([0-9][0-9,]*(?:\.[0-9]+)?)"?""")
            ?: money(""""marketPrice"\s*:\s*\{\s*"value"\s*:\s*"?([0-9][0-9,]*(?:\.[0-9]+)?)"?""")

        val price = sale ?: original ?: return null
        val list = original?.takeIf { sale != null && it > price + 0.01 }
        return ScriptPricing(price = price, listPrice = list)
    }

    private fun extractScriptPricing(document: Document, pageUrl: String = ""): ScriptPricing? {
        val html = document.outerHtml()
        val host = runCatching { java.net.URI(pageUrl).host?.lowercase().orEmpty() }.getOrElse { "" }

        extractTargetedScriptPricing(html, pageUrl)?.let { return it }
        extractJeeveePricing(html, host)?.let { return it }
        extractMuditaPricing(html, host)?.let { return it }
        extractDarazScriptPricing(html)?.let { return it }
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
                    ?: MoneyParser.parse(obj.optString("pdt_price_original"))
                    ?: MoneyParser.parse(obj.optString("pdt_market_price"))
                    ?: MoneyParser.parse(obj.optString("originalPrice"))
                    ?: MoneyParser.parse(obj.optString("listPrice"))
                    // Daraz/Lazada expose the discount as a percentage (e.g. "-15%") rather than
                    // a separate original price, so derive the list price from it when needed.
                    ?: listPriceFromDiscount(price, obj.optString("pdt_discount"))
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
        // Generic fallback: look for JSON-like price fields in scripts (best-effort)
        val salePattern = Regex(""""(?:salePrice|sale_price|currentPrice|current_price|specialPrice|special_price|finalPrice|final_price|price|amount)"\s*:\s*"?([0-9][0-9,]*(?:\.[0-9]+)?)"?""")
        val listPattern = Regex(""""(?:regularPrice|regular_price|originalPrice|original_price|listPrice|list_price|compareAtPrice|compare_at_price|oldPrice|old_price|wasPrice|was_price|basePrice|base_price|priceBeforeDiscount|previousPrice)"\s*:\s*"?([0-9][0-9,]*(?:\.[0-9]+)?)"?""")
        val saleCandidates = salePattern.findAll(html).take(30).mapNotNull { MoneyParser.parse(it.groupValues[1]) }.filter { it > 0.0 }.toList()
        val listCandidates = listPattern.findAll(html).take(30).mapNotNull { MoneyParser.parse(it.groupValues[1]) }.filter { it > 0.0 }.toList()

        // Prefer a coherent (sale, list) pair when list > sale
        var parsedSale = saleCandidates.minOrNull()
        var parsedList = listCandidates.maxOrNull()
        if (parsedSale != null && parsedList != null && parsedList <= parsedSale + 0.01) {
            val betterList = listCandidates.filter { it > parsedSale!! + 0.01 }.maxOrNull()
            parsedList = betterList
        }
        if (parsedSale == null) parsedSale = saleCandidates.firstOrNull()

        // Some platforms (e.g., Omnisend/Shopify-like payloads) emit integer minor units (x100).
        if (parsedSale != null && parsedList != null &&
            parsedSale >= 10000.0 && parsedList >= 10000.0 &&
            parsedSale % 1.0 == 0.0 && parsedList % 1.0 == 0.0
        ) {
            val s2 = parsedSale / 100.0
            val l2 = parsedList / 100.0
            if (s2 >= 1.0 && l2 > s2 + 0.01 && l2 < 2_000_000.0) {
                parsedSale = s2
                parsedList = l2
            }
        }

        if (parsedSale != null || parsedList != null || scriptImages.isNotEmpty()) {
            return ScriptPricing(
                imageUrl = scriptImages.firstOrNull(),
                imageUrls = scriptImages,
                price = parsedSale,
                listPrice = parsedList
            )
        }
        return null
    }

    /**
     * Derives an original/list price from a discount label when the page only exposes the current
     * price. Handles percentages ("-15%", "15% off", "20 %") and absolute amounts ("Rs. 500 off").
     */
    private fun listPriceFromDiscount(current: Double?, discountRaw: String?): Double? {
        if (current == null || current <= 0.0) return null
        val raw = discountRaw?.trim().orEmpty()
        if (raw.isEmpty() || raw.equals("null", ignoreCase = true)) return null

        val percent = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*%""").find(raw)
            ?.groupValues?.getOrNull(1)
            ?.replace(',', '.')
            ?.toDoubleOrNull()
        if (percent != null && percent > 0.0 && percent < 100.0) {
            val list = current / (1.0 - percent / 100.0)
            if (list > current + 0.01) return list
        }

        val amount = MoneyParser.parse(raw)
        if (amount != null && amount > 0.0) {
            val list = current + amount
            if (list > current + 0.01) return list
        }
        return null
    }

    private fun extractScriptImageUrls(html: String): List<String> {
        val urls = linkedSetOf<String>()
        fun add(raw: String?) {
            val u = normalizeImageUrl(raw) ?: return
            if (u.length > 2000) return
            if (isJunkOrLogoImage(u, el = null, allowMetaLogoRisk = false)) return
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
        // last-resort: pick plausible product CDN image URLs (limited), still rejecting logos/junk
        if (urls.size < 2) {
            patterns.last().findAll(html).take(80).forEach { m ->
                val candidate = normalizeImageUrl(m.value) ?: return@forEach
                val l = candidate.lowercase()
                val plausible = l.contains("slatic") || l.contains("alicdn") || l.contains("lazcdn") ||
                    l.contains("shopify") || l.contains("cloudfront") || l.contains("cloudinary") ||
                    l.contains("/product") || l.contains("/media/catalog") || l.contains("/uploads/")
                if (plausible) add(candidate)
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

    private fun extractSpecs(
        jsonLd: JSONObject?,
        document: Document,
        description: String? = null
    ): List<Pair<String, String>> {
        val out = LinkedHashMap<String, String>()
        val seenKeys = mutableSetOf<String>()

        fun normalizeKey(raw: String): String =
            raw.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

        fun looksLikeJunkKey(raw: String): Boolean {
            val key = raw.trim()
            if (key.length !in 2..64) return true
            if (key.contains(':') || key.contains('|') || key.contains("http", true)) return true
            val words = key.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size > 6) return true
            if (words.isNotEmpty() && words.all { w -> w.none { it.isLetter() } }) return true
            val lower = key.lowercase()
            val junk = listOf(
                "add to cart", "buy now", "add to wishlist", "wishlist", "compare", "share",
                "review", "reviews", "rating", "ratings", "description", "additional information",
                "related products", "you may also like", "select option", "choose an option",
                "quantity", "subtotal", "total", "checkout", "continue shopping", "menu", "search",
                "home", "shop", "category", "categories", "price", "sale", "new", "free shipping",
                "delivery", "shipping", "return", "returns", "follow us", "sign in", "log in",
                "register", "newsletter", "subscribe", "contact us", "about us", "quick view",
                "payment", "secure payment", "customer service",
                "sku", "mpn", "gtin", "gtin8", "gtin12", "gtin13", "gtin14", "ean", "upc",
                "isbn", "barcode", "product code", "item code", "product id", "item id",
                "part number", "model number"
            )
            if (junk.any { lower == it || lower.startsWith("$it ") }) return true
            if (lower.endsWith("...") || lower.endsWith("…")) return true
            return false
        }

        fun looksLikeJunkValue(raw: String): Boolean {
            val value = raw.trim()
            if (value.isEmpty()) return true
            if (value.equals("n/a", true) || value == "-" || value == "—" || value == "null") return true
            if (Regex("^https?://\\S+$").matches(value)) return true
            if (value.split(Regex("\\s+")).count { it.isNotBlank() } > 60) return true
            return false
        }

        fun put(k: String?, v: String?) {
            val key = cleanText(k, 64)
                ?.trim()
                ?.trimEnd(':', '：', '-', '–', '—')
                ?.trim()
                ?: return
            val value = cleanText(v, 260)?.trim() ?: return
            if (key.length < 2 || value.length < 1) return
            if (key.equals(value, ignoreCase = true)) return
            if (looksLikeJunkKey(key) || looksLikeJunkValue(value)) return
            val normalized = normalizeKey(key)
            if (normalized.isBlank() || !seenKeys.add(normalized)) return
            out[key] = value
        }

        /** Splits a description line like "Label: Value" or "Label - Value" when the label looks sane. */
        fun splitSpecLine(line: String): Pair<String, String>? {
            var text = line.trim()
            text = text.removePrefix("•").removePrefix("·").removePrefix("*").trim()
            if (text.startsWith("- ") || text.startsWith("– ") || text.startsWith("— ")) {
                text = text.substring(2).trim()
            }
            if (text.length < 5) return null

            fun accept(rawKey: String, rawValue: String): Pair<String, String>? {
                val key = rawKey.trim().trimEnd(':', '：', '-', '–', '—').trim()
                val value = rawValue.trim()
                if (key.length !in 2..60 || value.length < 2) return null
                if (key.split(Regex("\\s+")).count { it.isNotBlank() } > 6) return null
                if (key.none { it.isLetter() }) return null
                if (value.startsWith("//")) return null
                return key to value
            }

            val colon = Regex("\\s*[:：]\\s*").find(text)
            if (colon != null && colon.range.first > 0) {
                accept(
                    text.substring(0, colon.range.first),
                    text.substring(colon.range.last + 1)
                )?.let { return it }
            }
            for (separator in listOf(" – ", " — ", " - ")) {
                val index = text.indexOf(separator)
                if (index in 1..60) {
                    accept(
                        text.substring(0, index),
                        text.substring(index + separator.length)
                    )?.let { return it }
                }
            }
            return null
        }

        fun parseRowElement(el: Element) {
            val labelEl = el.selectFirst(".key, .key-title, .label, .name, .attribute-label, .spec-label, th, dt")
            val valueEl = el.selectFirst(".value, .val, .key-value, .attribute-value, .spec-value, td, dd")
            if (labelEl != null && valueEl != null && labelEl !== valueEl &&
                !labelEl.text().trim().equals(valueEl.text().trim(), ignoreCase = true)
            ) {
                put(labelEl.text(), valueEl.text())
                return
            }
            val text = el.text().trim()
            if (text.isNotEmpty()) splitSpecLine(text)?.let { (k, v) -> put(k, v) }
        }

        fun parseScope(scope: Element) {
            // Two-column table rows (WooCommerce attributes, generic spec tables).
            scope.select("tr").take(80).forEach { tr ->
                val cells = tr.select("th, td")
                when {
                    cells.size == 2 -> put(cells[0].text(), cells[1].text())
                    cells.size > 2 -> put(cells[0].text(), cells.drop(1).joinToString(" ") { it.text() })
                }
            }
            // Definition lists.
            val dts = scope.select("dt")
            val dds = scope.select("dd")
            if (dts.isNotEmpty() && dts.size == dds.size) {
                dts.forEachIndexed { i, dt -> put(dt.text(), dds.getOrNull(i)?.text()) }
            }
            // List / row style specs (label + value elements or "key: value" text).
            scope.select("li, .row, .item, .attribute, .spec, .key-value, .specification-row").take(120).forEach { el ->
                if (el.select("li, tr, .row, .attribute, .spec, .key-value, .specification-row").isNotEmpty()) {
                    return@forEach
                }
                parseRowElement(el)
            }
            // Daraz/Lazada style: key-title followed by key-value.
            scope.select(".key-title, .key").take(80).forEach { keyEl ->
                val valueEl = keyEl.nextElementSibling()
                    ?: keyEl.parent()?.selectFirst(".key-value, .value")
                if (valueEl != null) put(keyEl.text(), valueEl.text())
            }
        }

        fun mineFromText(text: String) {
            text.split('\n').take(200).forEach { line ->
                val clean = line.replace(Regex("\\s+"), " ").trim()
                if (clean.length in 4..320) splitSpecLine(clean)?.let { (k, v) -> put(k, v) }
            }
        }

        fun mineFromElement(el: Element) {
            val raw = el.html()
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("(?i)</(p|li|div|h[1-6]|tr|section)>"), "\n")
                .replace(Regex("<[^>]+>"), " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#215;", "×")
                .replace("&times;", "×")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
            mineFromText(raw)
            el.select("li").take(120).forEach { li ->
                val text = li.text().replace(Regex("\\s+"), " ").trim()
                if (text.length in 4..320) splitSpecLine(text)?.let { (k, v) -> put(k, v) }
            }
        }

        // 1) JSON-LD additionalProperty / PropertyValue.
        val props = jsonLd?.opt("additionalProperty")
        when (props) {
            is JSONArray -> for (i in 0 until props.length()) {
                val p = props.optJSONObject(i) ?: continue
                put(
                    p.optString("name").ifBlank { p.optString("propertyID") },
                    p.optString("value").ifBlank { p.optString("valueReference") }
                )
            }
            is JSONObject -> put(props.optString("name"), props.optString("value"))
        }
        // JSON-LD other spec-ish fields (skip internal identifiers like SKU/MPN/GTIN — they read as noise).
        listOf("model", "color", "material", "size", "weight").forEach { k ->
            val v = jsonLd?.optString(k)?.takeIf { it.isNotBlank() && it != "null" }
            if (v != null) put(k.replaceFirstChar { c -> c.uppercase() }, v)
        }

        // 2) Product-scoped structured spec containers (highest confidence).
        val containerSelectors = listOf(
            ".woocommerce-product-attributes",
            "#tab-additional_information",
            ".woocommerce-Tabs-panel--additional_information",
            ".product-specifications", ".product-specification", "[class*=product-spec]",
            "[class*=pdp-spec]", "[class*=pdp-mod-spec]", ".pdp-general-features",
            ".specification-keys", "[class*=specification]",
            ".product-attributes", "[class*=product-attributes]",
            "[class*=tech-spec]", "[class*=specs-table]", "#specifications", "#specification",
            "[id*=specification]", "[data-tab*=spec]", "[data-tab*=additional]"
        )
        val visitedContainers = mutableSetOf<Element>()
        for (selector in containerSelectors) {
            for (container in document.select(selector).take(6)) {
                if (!visitedContainers.add(container)) continue
                if (isInExcludedPriceContext(container)) continue
                if (container.select("tr, dt, li, .row, .attribute, .spec, .key-title").isEmpty()) continue
                parseScope(container)
            }
            if (out.size >= 40) break
        }

        // 3) Specs written inside the product description / "about this item" blocks.
        val descriptionSelectors = listOf(
            "#tab-description", ".woocommerce-Tabs-panel--description",
            ".woocommerce-product-details__short-description",
            "[class*=product-description]", "[class*=ProductDescription]",
            ".product-description", ".product__description", "#productDescription",
            "#feature-bullets", ".pdp-product-desc", ".html-content.detail-content",
            "[data-product-description]", ".rte", "#description", ".product-details"
        )
        val descriptionContainers = linkedSetOf<Element>()
        for (selector in descriptionSelectors) {
            document.select(selector).take(3).forEach { el ->
                if (!isInExcludedPriceContext(el)) descriptionContainers += el
            }
            if (descriptionContainers.size >= 4) break
        }
        descriptionContainers.forEach { mineFromElement(it) }
        description?.takeIf { it.isNotBlank() }?.let { mineFromText(it) }

        // 4) Conservative fallback: only when we found little, and only for clear key/value tables.
        if (out.size < 4) {
            document.select("table").take(12).forEach { table ->
                if (isInExcludedPriceContext(table)) return@forEach
                val rows = table.select("tr")
                if (rows.size !in 2..60) return@forEach
                val pairs = rows.mapNotNull { tr ->
                    val cells = tr.select("th, td")
                    if (cells.size == 2) cells[0].text().trim() to cells[1].text().trim() else null
                }
                if (pairs.size >= 2 && pairs.size >= rows.size / 2) {
                    pairs.forEach { (k, v) -> put(k, v) }
                }
            }
        }
        if (out.size < 4) {
            document.select("dl").take(8).forEach { dl ->
                if (isInExcludedPriceContext(dl)) return@forEach
                val dts = dl.select("dt")
                val dds = dl.select("dd")
                if (dts.isNotEmpty() && dts.size == dds.size) {
                    dts.forEachIndexed { i, dt -> put(dt.text(), dds.getOrNull(i)?.text()) }
                }
            }
        }

        return out.entries
            .filter { (k, v) -> k.isNotBlank() && v.isNotBlank() && k.length <= 64 && v.length <= 260 }
            .take(60)
            .map { it.key to it.value }
    }

    /**
     * Best-effort stock state. JSON-LD availability is authoritative; meta/microdata and visible
     * product-page signals are used as fallbacks. Returns null when the page gives no clear signal.
     */
    private fun extractStockStatus(jsonLd: JSONObject?, document: Document): Boolean? {
        fun availabilityOf(offer: JSONObject?): String? {
            val raw = offer?.opt("availability") ?: return null
            return when (raw) {
                is String -> raw
                is JSONArray -> raw.optString(0).takeIf { it.isNotBlank() }
                else -> null
            }
        }

        fun classify(raw: String?): Boolean? {
            val value = raw?.lowercase()?.replace("_", "")?.replace(" ", "") ?: return null
            if (value.isBlank()) return null
            return when {
                value.contains("outofstock") || value.contains("soldout") ||
                    value.contains("discontinued") || value.contains("unavailable") -> false
                value.contains("instock") || value.contains("limitedavailability") ||
                    value.contains("onlineonly") || value.contains("preorder") ||
                    value.contains("backorder") || value.contains("available") -> true
                else -> null
            }
        }

        // JSON-LD offers[].availability
        when (val offers = jsonLd?.opt("offers")) {
            is JSONObject -> classify(availabilityOf(offers))?.let { return it }
            is JSONArray -> for (i in 0 until offers.length()) {
                classify(availabilityOf(offers.optJSONObject(i)))?.let { return it }
            }
        }

        // Meta tags
        classify(
            document.metaContent("product:availability")
                ?: document.metaContent("og:availability")
                ?: document.metaContent("availability")
        )?.let { return it }

        // Microdata
        for (el in document.select("[itemprop=availability]").take(4)) {
            classify(el.attr("href") + " " + el.attr("content") + " " + el.text())?.let { return it }
        }

        // Visible stock blocks (e.g. WooCommerce "<p class=stock out-of-stock>Out of stock</p>")
        for (el in document.select(
            ".stock, .stock-status, .availability, .product-availability, " +
                ".woocommerce-variation-availability, [class*=stock-status], " +
                "[class*=availability], [class*=inventory]"
        ).take(12)) {
            val text = el.text().lowercase()
            if (text.isBlank()) continue
            if (text.contains("out of stock") || text.contains("out-of-stock") ||
                text.contains("sold out") || text.contains("unavailable") ||
                text.contains("notify me")
            ) return false
            if (text.contains("in stock") || text.contains("available now") ||
                text.contains("limited availability")
            ) return true
        }

        // Body / product wrapper classes
        val classHay = buildString {
            document.body()?.className()?.lowercase()?.let { append(it) }
            append(' ')
            document.selectFirst(".product, .product-detail, .single-product, main")
                ?.className()?.lowercase()?.let { append(it) }
        }
        if (classHay.contains("outofstock") || classHay.contains("out-of-stock") ||
            classHay.contains("sold-out")
        ) return false
        if (classHay.contains("instock") || classHay.contains("in-stock")) return true

        // Add-to-cart vs notify/sold-out buttons
        var sawAddToCart = false
        for (el in document.select("button, a").take(400)) {
            val text = el.text().trim().lowercase()
            if (text.isEmpty() || text.length > 40) continue
            val disabled = el.hasAttr("disabled") || el.className().contains("disabled", true)
            if (!disabled && (text == "add to cart" || text.startsWith("add to cart"))) {
                sawAddToCart = true
            }
            if (text.contains("notify me") || text == "sold out" || text == "out of stock") {
                return false
            }
        }
        if (sawAddToCart) return true

        return null
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

    private fun browserRequest(url: String): Request.Builder {
        val host = runCatching { java.net.URI(url).host?.lowercase().orEmpty() }.getOrDefault("")
        val desktop = host.contains("amazon.") || host.contains("amzn.")
        return Request.Builder()
            .url(url)
            .header("User-Agent", if (desktop) DESKTOP_USER_AGENT else USER_AGENT)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Upgrade-Insecure-Requests", "1")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Sec-Fetch-User", "?1")
            .header("Cache-Control", "no-cache")
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        // Amazon serves a far more parseable (server-rendered) price block to a desktop client.
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
