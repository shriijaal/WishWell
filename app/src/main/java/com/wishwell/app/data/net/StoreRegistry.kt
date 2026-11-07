package com.wishwell.app.data.net

import java.net.URLEncoder

data class StoreSearchLink(val storeName: String, val url: String)

private data class StoreDefinition(
    val key: String,
    val displayName: String,
    val hostFragments: List<String>,
    val searchTemplate: String
)

private data class RegionalStore(
    val storeName: String,
    val template: String,
    val currency: String
)

/**
 * Knows how to name stores and how to build "compare this elsewhere" search links.
 * Everything is local — no API keys required.
 */
class StoreRegistry {

    private val stores = listOf(
        StoreDefinition("amazon", "Amazon", listOf("amazon.", "amzn."), "https://www.amazon.com/s?k=%s"),
        StoreDefinition("ebay", "eBay", listOf("ebay."), "https://www.ebay.com/sch/i.html?_nkw=%s"),
        StoreDefinition("walmart", "Walmart", listOf("walmart."), "https://www.walmart.com/search?q=%s"),
        StoreDefinition("target", "Target", listOf("target."), "https://www.target.com/s?searchTerm=%s"),
        StoreDefinition("bestbuy", "Best Buy", listOf("bestbuy."), "https://www.bestbuy.com/site/searchpage.jsp?st=%s"),
        StoreDefinition("etsy", "Etsy", listOf("etsy."), "https://www.etsy.com/search?q=%s"),
        StoreDefinition("daraz", "Daraz", listOf("daraz."), "https://www.daraz.com.np/catalog/?q=%s"),
        StoreDefinition("flipkart", "Flipkart", listOf("flipkart."), "https://www.flipkart.com/search?q=%s"),
        StoreDefinition("nike", "Nike", listOf("nike."), "https://www.nike.com/w?q=%s"),
        StoreDefinition("adidas", "Adidas", listOf("adidas."), "https://www.adidas.com/us/search?q=%s"),
        StoreDefinition("ikea", "IKEA", listOf("ikea."), "https://www.ikea.com/us/en/search/?q=%s"),
        StoreDefinition("wayfair", "Wayfair", listOf("wayfair."), "https://www.wayfair.com/keyword.php?keyword=%s"),
        StoreDefinition("newegg", "Newegg", listOf("newegg."), "https://www.newegg.com/p/pl?d=%s"),
        StoreDefinition("sephora", "Sephora", listOf("sephora."), "https://www.sephora.com/search?keyword=%s"),
        StoreDefinition("ulta", "Ulta", listOf("ulta."), "https://www.ulta.com/search?search=%s"),
        StoreDefinition("myntra", "Myntra", listOf("myntra."), "https://www.myntra.com/%s"),
        StoreDefinition("ajio", "AJIO", listOf("ajio."), "https://www.ajio.com/search/?text=%s"),
        StoreDefinition("asos", "ASOS", listOf("asos."), "https://www.asos.com/us/search/?q=%s"),
        StoreDefinition("nordstrom", "Nordstrom", listOf("nordstrom."), "https://www.nordstrom.com/sr?keyword=%s"),
        StoreDefinition("macys", "Macy's", listOf("macys."), "https://www.macys.com/shop/search?keyword=%s"),
        StoreDefinition("homedepot", "Home Depot", listOf("homedepot."), "https://www.homedepot.com/s/%s"),
        StoreDefinition("lowes", "Lowe's", listOf("lowes."), "https://www.lowes.com/search?searchTerm=%s"),
        StoreDefinition("apple", "Apple", listOf("apple."), "https://www.apple.com/search/%s"),
        StoreDefinition("zara", "Zara", listOf("zara."), "https://www.zara.com/us/en/search?searchTerm=%s"),
        StoreDefinition("hm", "H&M", listOf("hm.", "2.hm."), "https://www2.hm.com/en_us/search-results.html?q=%s"),
        StoreDefinition("uniqlo", "Uniqlo", listOf("uniqlo."), "https://www.uniqlo.com/us/en/search?q=%s"),
        // Nepal / regional stores (best-effort search templates)
        StoreDefinition("gadgetbytenepal", "GadgetByte Nepal", listOf("gadgetbytenepal."), "https://www.gadgetbytenepal.com/?s=%s&post_type=product"),
        StoreDefinition("itti", "ITTI", listOf("itti.com.np"), "https://itti.com.np/?s=%s&post_type=product"),
        StoreDefinition("sharmazacoustics", "Sharmaz Acoustics", listOf("sharmazacoustics."), "https://sharmazacoustics.com/?s=%s&post_type=product"),
        StoreDefinition("jeevee", "Jeevee", listOf("jeevee."), "https://www.jeevee.com/search?q=%s"),
        StoreDefinition("booksmandala", "Booksmandala", listOf("booksmandala."), "https://booksmandala.com/search?q=%s"),
        StoreDefinition("ergokeysnepal", "ErgoKeys Nepal", listOf("ergokeysnepal."), "https://ergokeysnepal.com/?s=%s&post_type=product"),
        StoreDefinition("rzzyofficial", "RZZY Official", listOf("rzzyofficial."), "https://www.rzzyofficial.com/?s=%s&post_type=product"),
        StoreDefinition("backseatgaming", "Backseat Gaming", listOf("backseatgaming."), "https://backseatgaming.co/search?q=%s")
    )

    private val regionalDefaults = listOf(
        RegionalStore("Amazon", "https://www.amazon.com/s?k=%s", "USD"),
        RegionalStore("eBay", "https://www.ebay.com/sch/i.html?_nkw=%s", "USD"),
        RegionalStore("Walmart", "https://www.walmart.com/search?q=%s", "USD"),
        RegionalStore("Google Shopping", "https://www.google.com/search?tbm=shop&q=%s", "USD")
    )

    private val regionalByCurrency = mapOf(
        "INR" to listOf(
            RegionalStore("Amazon.in", "https://www.amazon.in/s?k=%s", "INR"),
            RegionalStore("Flipkart", "https://www.flipkart.com/search?q=%s", "INR"),
            RegionalStore("Myntra", "https://www.myntra.com/%s", "INR"),
            RegionalStore("Google Shopping", "https://www.google.com/search?tbm=shop&q=%s", "INR")
        ),
        "GBP" to listOf(
            RegionalStore("Amazon UK", "https://www.amazon.co.uk/s?k=%s", "GBP"),
            RegionalStore("eBay UK", "https://www.ebay.co.uk/sch/i.html?_nkw=%s", "GBP"),
            RegionalStore("Argos", "https://www.argos.co.uk/search/%s/", "GBP"),
            RegionalStore("Google Shopping", "https://www.google.com/search?tbm=shop&q=%s", "GBP")
        ),
        "EUR" to listOf(
            RegionalStore("Amazon.de", "https://www.amazon.de/s?k=%s", "EUR"),
            RegionalStore("eBay.de", "https://www.ebay.de/sch/i.html?_nkw=%s", "EUR"),
            RegionalStore("Google Shopping", "https://www.google.com/search?tbm=shop&q=%s", "EUR")
        ),
        "AUD" to listOf(
            RegionalStore("Amazon AU", "https://www.amazon.com.au/s?k=%s", "AUD"),
            RegionalStore("eBay AU", "https://www.ebay.com.au/sch/i.html?_nkw=%s", "AUD"),
            RegionalStore("Google Shopping", "https://www.google.com/search?tbm=shop&q=%s", "AUD")
        ),
        "CAD" to listOf(
            RegionalStore("Amazon CA", "https://www.amazon.ca/s?k=%s", "CAD"),
            RegionalStore("eBay CA", "https://www.ebay.ca/sch/i.html?_nkw=%s", "CAD"),
            RegionalStore("Google Shopping", "https://www.google.com/search?tbm=shop&q=%s", "CAD")
        ),
        "NPR" to listOf(
            RegionalStore("Daraz", "https://www.daraz.com.np/catalog/?q=%s", "NPR"),
            RegionalStore("GadgetByte Nepal", "https://www.gadgetbytenepal.com/?s=%s&post_type=product", "NPR"),
            RegionalStore("ITTI", "https://itti.com.np/?s=%s&post_type=product", "NPR"),
            RegionalStore("Sharmaz Acoustics", "https://sharmazacoustics.com/?s=%s&post_type=product", "NPR"),
            RegionalStore("Jeevee", "https://www.jeevee.com/search?q=%s", "NPR"),
            RegionalStore("Booksmandala", "https://booksmandala.com/search?q=%s", "NPR"),
            RegionalStore("ErgoKeys Nepal", "https://ergokeysnepal.com/?s=%s&post_type=product", "NPR"),
            RegionalStore("RZZY Official", "https://www.rzzyofficial.com/?s=%s&post_type=product", "NPR"),
            RegionalStore("Backseat Gaming", "https://backseatgaming.co/search?q=%s", "NPR"),
            RegionalStore("Google Shopping", "https://www.google.com/search?tbm=shop&q=%s", "NPR")
        )
    )

    fun displayNameForHost(host: String): String {
        val clean = host.lowercase().removePrefix("www.")
        stores.firstOrNull { definition ->
            definition.hostFragments.any { clean == it.trimEnd('.') || clean.contains(it) }
        }?.let { return it.displayName }
        val root = clean.split('.').firstOrNull()?.takeIf { it.isNotBlank() } ?: clean
        return root.replaceFirstChar { it.uppercase() }
    }

    fun searchLinks(query: String, currencyCode: String, originalUrl: String?): List<StoreSearchLink> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val originalHost = originalUrl?.let { runCatching { java.net.URI(it).host }.getOrNull() }
        val originalStore = originalHost?.let { host ->
            stores.firstOrNull { definition ->
                definition.hostFragments.any { host.contains(it) }
            }
        }

        val defaults = regionalByCurrency[currencyCode] ?: regionalDefaults
        val links = mutableListOf<StoreSearchLink>()

        if (originalStore != null) {
            links += StoreSearchLink(originalStore.displayName, originalStore.searchTemplate.format(encoded))
        }
        defaults.forEach { regional ->
            links += StoreSearchLink(regional.storeName, regional.template.format(encoded))
        }

        return links.distinctBy { it.storeName }
    }

    fun defaultCurrencyFor(host: String?): String = when {
        host == null -> "USD"
        host.contains("daraz.com.np") || host.contains("daraz.np") || host.contains("itti.com.np") -> "NPR"
        host.endsWith(".np") || host.contains("gadgetbytenepal.") || host.contains("sharmazacoustics.") ||
            host.contains("jeevee.") || host.contains("booksmandala.") || host.contains("ergokeysnepal.") -> "NPR"
        host.contains(".co.uk") || host.endsWith("amazon.co.uk") -> "GBP"
        host.contains("amazon.in") || host.contains("flipkart") || host.contains("myntra") || host.contains("ajio") -> "INR"
        host.contains("amazon.de") || host.contains("amazon.fr") || host.contains("amazon.it") || host.contains("amazon.es") -> "EUR"
        host.contains("amazon.ca") -> "CAD"
        host.contains("amazon.com.au") -> "AUD"
        else -> "USD"
    }
}
