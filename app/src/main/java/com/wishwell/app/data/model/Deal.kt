package com.wishwell.app.data.model

enum class DealVerdict { ALL_TIME_LOW, GOOD, FAIR, HIGH, UNKNOWN }

data class DealAssessment(
    val verdict: DealVerdict,
    val headline: String,
    val detail: String
)

/** Turns raw history into a plain-language "should I buy now?" signal. */
fun assessDeal(item: ItemWithStats): DealAssessment {
    val price = item.price
    val average = item.average
    val highest = item.highest
    val lowest = item.lowest

    val unknown = DealAssessment(
        DealVerdict.UNKNOWN,
        "Still learning this price",
        "Keep checking back — a few more readings reveal whether this is a good deal."
    )

    if (price == null) return unknown

    if (item.hasHistory && item.hasPriceVariation && lowest != null && price <= lowest + 0.01) {
        return DealAssessment(
            DealVerdict.ALL_TIME_LOW,
            "Lowest price we've seen",
            "This is the cheapest ${item.item.title.take(40)} has been since you started tracking."
        )
    }

    // The store's own struck-through price is a reliable sale signal even when our history is
    // still flat (or was filtered down to the sale price).
    val listPrice = item.item.listPrice
    val salePercent = item.discountPercent
    if (item.isOnSale && listPrice != null && salePercent != null && salePercent >= 5) {
        return DealAssessment(
            DealVerdict.GOOD,
            "On sale right now",
            "The store lists this at ${money(listPrice, item.item.currencyCode)} — you'd pay " +
                "${money(price, item.item.currencyCode)} today, about $salePercent% less."
        )
    }

    if (!item.hasHistory || average == null || lowest == null || highest == null) {
        return unknown
    }

    val vsAverage = (price - average) / average
    return when {
        vsAverage <= -0.10 -> DealAssessment(
            DealVerdict.GOOD,
            "Below the usual price",
            "About ${percent(-vsAverage)} under the average of ${money(average, item.item.currencyCode)}."
        )
        vsAverage >= 0.08 -> DealAssessment(
            DealVerdict.HIGH,
            "Near its peak — worth waiting",
            "Sitting about ${percent(vsAverage)} above the average of ${money(average, item.item.currencyCode)}."
        )
        else -> DealAssessment(
            DealVerdict.FAIR,
            "Around the usual price",
            "Right in line with its average of ${money(average, item.item.currencyCode)}."
        )
    }
}

private fun percent(fraction: Double): String = "${(fraction * 100).toInt()}%"

private fun money(amount: Double, currencyCode: String): String =
    com.wishwell.app.util.formatMoney(amount, currencyCode)
