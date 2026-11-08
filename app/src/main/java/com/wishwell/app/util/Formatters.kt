package com.wishwell.app.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

private fun isNpr(code: String): Boolean = code.equals("NPR", ignoreCase = true)

/** Locale-aware plain number formatting (grouping, no currency symbol). */
private fun formatAmountNumber(amount: Double, fractionDigits: Int): String {
    val format = NumberFormat.getNumberInstance(Locale.getDefault())
    format.maximumFractionDigits = fractionDigits
    format.minimumFractionDigits = 0
    return format.format(amount)
}

fun formatMoney(amount: Double?, currencyCode: String = "USD"): String {
    if (amount == null) return "—"
    val rounded = kotlin.math.round(amount * 100.0) / 100.0
    val hasCents = kotlin.math.abs(rounded - kotlin.math.round(rounded)) >= 0.005
    // Nepalese rupees read best as "Rs." rather than the locale-dependent NPR symbol.
    if (isNpr(currencyCode)) {
        return "Rs. ${formatAmountNumber(rounded, if (hasCents) 2 else 0)}"
    }
    val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
    runCatching { format.currency = Currency.getInstance(currencyCode.uppercase()) }
    format.maximumFractionDigits = if (hasCents) 2 else 0
    format.minimumFractionDigits = 0
    return format.format(rounded)
}

/** Always two decimals, used for line items where precision matters. */
fun formatMoneyExact(amount: Double?, currencyCode: String = "USD"): String {
    if (amount == null) return "—"
    if (isNpr(currencyCode)) {
        return "Rs. ${formatAmountNumber(amount, 2)}"
    }
    val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
    runCatching { format.currency = Currency.getInstance(currencyCode.uppercase()) }
    format.maximumFractionDigits = 2
    format.minimumFractionDigits = 2
    return format.format(amount)
}

fun formatPercent(value: Int): String = if (value < 0) "$value%" else "$value%"

fun formatSignedMoney(delta: Double, currencyCode: String): String {
    val sign = if (delta > 0) "+" else "-"
    return "$sign${formatMoney(kotlin.math.abs(delta), currencyCode)}"
}

fun formatDateShort(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
}

fun formatDateWithYear(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
}

fun formatRelativeDay(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days <= 0L -> "Today"
        days == 1L -> "Yesterday"
        days < 7L -> "$days days ago"
        days < 30L -> "${days / 7}w ago"
        else -> formatDateShort(epochMillis)
    }
}

fun currencySymbol(code: String): String {
    if (isNpr(code)) return "Rs."
    val currency = runCatching { Currency.getInstance(code.uppercase()) }.getOrNull()
        ?: return code
    return currency.symbol ?: code
}

fun validCurrencyOrNull(code: String): String? {
    val clean = code.trim().uppercase()
    if (clean.length != 3 || clean.any { !it.isLetter() }) return null
    return runCatching { Currency.getInstance(clean) }.getOrNull()?.currencyCode
}

/** Currency label for prose. NPR reads as "Rs. (NPR)"; others stay as their ISO code. */
fun currencyDisplayName(code: String): String =
    if (isNpr(code)) "Rs. (NPR)" else code.uppercase()
