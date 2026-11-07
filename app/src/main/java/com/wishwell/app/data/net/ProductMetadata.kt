package com.wishwell.app.data.net

data class HistoricalPrice(
    val price: Double,
    val currencyCode: String,
    /** Epoch millis when the price was observed by the source, if known. */
    val recordedAt: Long? = null
)

data class ProductMetadata(
    val title: String?,
    val storeName: String?,
    val imageUrl: String?,
    val imageUrls: List<String> = emptyList(),
    val price: Double?,
    val listPrice: Double?,
    val currencyCode: String?,
    val brand: String?,
    val finalUrl: String,
    val priceHistory: List<HistoricalPrice> = emptyList(),
    val description: String? = null,
    /** Ordered key/value specs (best-effort). */
    val specs: List<Pair<String, String>> = emptyList()
)

sealed interface ScrapeResult {
    data class Success(val metadata: ProductMetadata) : ScrapeResult
    data class Failure(val message: String) : ScrapeResult
}

/** Small helper that turns the many price formats stores use into a Double. */
object MoneyParser {

    fun parse(raw: String?): Double? {
        if (raw == null) return null
        var text = raw.trim()
        if (text.isEmpty()) return null

        // Keep digits and separators only.
        text = text.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
            .trim('.', ',', '-')
        if (text.isEmpty() || text == "-") return null

        val lastDot = text.lastIndexOf('.')
        val lastComma = text.lastIndexOf(',')

        val normalized: String = when {
            lastDot >= 0 && lastComma >= 0 -> {
                val decimal = if (lastDot > lastComma) "." else ","
                val thousands = if (decimal == ".") "," else "."
                text.replace(thousands, "")
                    .let { s ->
                        val idx = s.lastIndexOf(decimal)
                        if (idx < 0) s else s.substring(0, idx).replace(decimal, "") + "." + s.substring(idx + 1)
                    }
            }

            lastDot >= 0 -> {
                val digitsAfter = text.length - lastDot - 1
                if (digitsAfter == 3 && text.substringBefore('.').length in 1..3 && text.count { it == '.' } == 1) {
                    text.replace(".", "") // 1.299 => 1299
                } else if (text.count { it == '.' } > 1) {
                    text.filterIndexed { index, c -> c != '.' || index == lastDot }
                } else {
                    text
                }
            }

            lastComma >= 0 -> {
                val digitsAfter = text.length - lastComma - 1
                if (digitsAfter == 2) text.replace(",", ".")
                else text.replace(",", "")
            }

            else -> text
        }

        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    }
}
