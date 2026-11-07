package com.wishwell.app.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale

fun formatMoney(amount: Double?, currencyCode: String = "USD"): String {
    if (amount == null) return "—"
    val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
    runCatching { format.currency = Currency.getInstance(currencyCode.uppercase()) }
    val exact = amount % 1.0 != 0.0
    format.maximumFractionDigits = if (exact) 2 else 0
    format.minimumFractionDigits = 0
    return format.format(amount)
}

/** Always two decimals, used for line items where precision matters. */
fun formatMoneyExact(amount: Double?, currencyCode: String = "USD"): String {
    if (amount == null) return "—"
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
    val currency = runCatching { Currency.getInstance(code.uppercase()) }.getOrNull()
        ?: return code
    return currency.symbol ?: code
}

fun validCurrencyOrNull(code: String): String? {
    val clean = code.trim().uppercase()
    if (clean.length != 3 || clean.any { !it.isLetter() }) return null
    return runCatching { Currency.getInstance(clean) }.getOrNull()?.currencyCode
}
