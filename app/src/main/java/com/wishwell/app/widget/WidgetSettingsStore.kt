package com.wishwell.app.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color

/**
 * Per-widget appearance & content settings stored in SharedPreferences.
 * Keys are prefixed with the widget key so multiple widgets can have different looks.
 */
object WidgetSettingsStore {

    data class WidgetSettings(
        val theme: String,        // "system" | "light" | "dark"
        val style: String,        // "list" | "card" | "compact"
        val transparency: Int,    // 0..100 (100 = opaque)
        val blur: Boolean,
        val limit: Int,           // max entries shown (3, 5, 8, 10)
        val sortOrder: String,    // "recent" | "name" | "price"
        val showPrice: Boolean,   // show price on entries
        val showStore: Boolean,   // show store name on entries
        val dropsOnly: Boolean,   // show only deals / price drops
        val accentColor: String?, // hex "#RRGGBB", null = theme primary
        val fontSize: Float,      // text size multiplier 0.75f..1.5f
        val widgetSize: String    // "small" | "medium" | "large"
    )

    fun defaultSettings(): WidgetSettings =
        WidgetSettings(
            theme = "system",
            style = "list",
            transparency = 100,
            blur = false,
            limit = 8,
            sortOrder = "recent",
            showPrice = true,
            showStore = true,
            dropsOnly = false,
            accentColor = null,
            fontSize = 1.0f,
            widgetSize = "medium"
        )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)

    private fun themeKey(widgetKey: String) = "widget_${widgetKey}_theme"
    private fun styleKey(widgetKey: String) = "widget_${widgetKey}_style"
    private fun transparencyKey(widgetKey: String) = "widget_${widgetKey}_transparency"
    private fun blurKey(widgetKey: String) = "widget_${widgetKey}_blur"
    private fun limitKey(widgetKey: String) = "widget_${widgetKey}_limit"
    private fun sortOrderKey(widgetKey: String) = "widget_${widgetKey}_sortOrder"
    private fun showPriceKey(widgetKey: String) = "widget_${widgetKey}_showPrice"
    private fun showStoreKey(widgetKey: String) = "widget_${widgetKey}_showStore"
    private fun dropsOnlyKey(widgetKey: String) = "widget_${widgetKey}_dropsOnly"
    private fun accentColorKey(widgetKey: String) = "widget_${widgetKey}_accentColor"
    private fun fontSizeKey(widgetKey: String) = "widget_${widgetKey}_fontSize"
    private fun widgetSizeKey(widgetKey: String) = "widget_${widgetKey}_widgetSize"

    fun load(context: Context, widgetKey: String): WidgetSettings {
        val p = prefs(context)
        return WidgetSettings(
            theme = p.getString(themeKey(widgetKey), "system") ?: "system",
            style = p.getString(styleKey(widgetKey), "list") ?: "list",
            transparency = p.getInt(transparencyKey(widgetKey), 100),
            blur = p.getBoolean(blurKey(widgetKey), false),
            limit = p.getInt(limitKey(widgetKey), 8),
            sortOrder = p.getString(sortOrderKey(widgetKey), "recent") ?: "recent",
            showPrice = p.getBoolean(showPriceKey(widgetKey), true),
            showStore = p.getBoolean(showStoreKey(widgetKey), true),
            dropsOnly = p.getBoolean(dropsOnlyKey(widgetKey), false),
            accentColor = p.getString(accentColorKey(widgetKey), null),
            fontSize = p.getFloat(fontSizeKey(widgetKey), 1.0f),
            widgetSize = p.getString(widgetSizeKey(widgetKey), "medium") ?: "medium"
        )
    }

    fun save(context: Context, widgetKey: String, settings: WidgetSettings) {
        val editor = prefs(context).edit()
        editor.putString(themeKey(widgetKey), settings.theme)
        editor.putString(styleKey(widgetKey), settings.style)
        editor.putInt(transparencyKey(widgetKey), settings.transparency)
        editor.putBoolean(blurKey(widgetKey), settings.blur)
        editor.putInt(limitKey(widgetKey), settings.limit)
        editor.putString(sortOrderKey(widgetKey), settings.sortOrder)
        editor.putBoolean(showPriceKey(widgetKey), settings.showPrice)
        editor.putBoolean(showStoreKey(widgetKey), settings.showStore)
        editor.putBoolean(dropsOnlyKey(widgetKey), settings.dropsOnly)
        editor.putString(accentColorKey(widgetKey), settings.accentColor)
        editor.putFloat(fontSizeKey(widgetKey), settings.fontSize)
        editor.putString(widgetSizeKey(widgetKey), settings.widgetSize)
        editor.commit()
    }

    /** Parses a hex "#RRGGBB" string to a Compose Color, or null if invalid. */
    fun parseAccentColor(hex: String?): Color? {
        if (hex == null || hex.isEmpty()) return null
        var clean = hex.trimStart('#')
        if (clean.length == 6) clean = "FF$clean"
        return try {
            Color(java.lang.Long.parseLong(clean, 16).toInt())
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** Exports settings to a simple JSON string (no external dependencies). */
    fun exportToJson(settings: WidgetSettings): String {
        return buildString {
            append("{")
            append("\"theme\":\"${escapeJson(settings.theme)}\",")
            append("\"style\":\"${escapeJson(settings.style)}\",")
            append("\"transparency\":${settings.transparency},")
            append("\"blur\":${settings.blur},")
            append("\"limit\":${settings.limit},")
            append("\"sortOrder\":\"${escapeJson(settings.sortOrder)}\",")
            append("\"showPrice\":${settings.showPrice},")
            append("\"showStore\":${settings.showStore},")
            append("\"dropsOnly\":${settings.dropsOnly},")
            append("\"accentColor\":${if (settings.accentColor == null) "null" else "\"${escapeJson(settings.accentColor)}\""},")
            append("\"fontSize\":${settings.fontSize},")
            append("\"widgetSize\":\"${escapeJson(settings.widgetSize)}\"")
            append("}")
        }
    }

    /** Imports settings from a JSON string, falling back to defaults for missing fields. */
    fun importFromJson(json: String): WidgetSettings {
        return try {
            val map = parseSimpleJson(json)
            val defaults = defaultSettings()
            WidgetSettings(
                theme = map["theme"] as? String ?: defaults.theme,
                style = map["style"] as? String ?: defaults.style,
                transparency = (map["transparency"] as? Number)?.toInt() ?: defaults.transparency,
                blur = map["blur"] as? Boolean ?: defaults.blur,
                limit = (map["limit"] as? Number)?.toInt() ?: defaults.limit,
                sortOrder = map["sortOrder"] as? String ?: defaults.sortOrder,
                showPrice = map["showPrice"] as? Boolean ?: defaults.showPrice,
                showStore = map["showStore"] as? Boolean ?: defaults.showStore,
                dropsOnly = map["dropsOnly"] as? Boolean ?: defaults.dropsOnly,
                accentColor = map["accentColor"] as? String,
                fontSize = (map["fontSize"] as? Number)?.toFloat() ?: defaults.fontSize,
                widgetSize = map["widgetSize"] as? String ?: defaults.widgetSize
            )
        } catch (_: Exception) {
            defaultSettings()
        }
    }

    /** All widget keys currently stored. */
    fun allWidgetKeys(context: Context): Set<String> {
        val p = prefs(context)
        return p.all.keys
            .filter { it.startsWith("widget_") && it.endsWith("_theme") }
            .map { it.removePrefix("widget_").removeSuffix("_theme") }
            .toSet()
    }

    // ── Minimal JSON helpers (no external dependencies) ──
    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    private fun parseSimpleJson(json: String): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        var i = 1 // skip opening {
        while (i < json.length) {
            // skip whitespace
            while (i < json.length && json[i].isWhitespace()) i++
            if (i >= json.length || json[i] == '}') break
            // read key
            if (json[i] != '"') { i++; continue }
            i++
            val keyStart = i
            while (i < json.length && json[i] != '"') i++
            val key = json.substring(keyStart, i)
            i++ // skip closing quote
            while (i < json.length && json[i] != ':') i++
            i++ // skip colon
            while (i < json.length && json[i].isWhitespace()) i++
            // read value
            when {
                json[i] == '"' -> {
                    i++
                    val valStart = i
                    while (i < json.length && json[i] != '"') {
                        if (json[i] == '\\') i++ // skip escape
                        i++
                    }
                    result[key] = json.substring(valStart, i)
                    i++ // skip closing quote
                }
                json[i] == 'n' -> { result[key] = null; i += 4 } // null
                json[i] == 't' -> { result[key] = true; i += 4 } // true
                json[i] == 'f' -> { result[key] = false; i += 5 } // false
                else -> {
                    // number
                    val valStart = i
                    while (i < json.length && (json[i].isDigit() || json[i] == '.' || json[i] == '-' || json[i] == 'e' || json[i] == 'E' || json[i] == '+')) i++
                    val numStr = json.substring(valStart, i)
                    result[key] = if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) numStr.toDouble() else numStr.toInt()
                }
            }
            while (i < json.length && json[i].isWhitespace()) i++
            if (i < json.length && json[i] == ',') i++
        }
        return result
    }
}
