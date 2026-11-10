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
        val fontSize: Float       // text size multiplier 0.75f..1.5f
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
            fontSize = 1.0f
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
            fontSize = p.getFloat(fontSizeKey(widgetKey), 1.0f)
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
        editor.apply()
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
}
