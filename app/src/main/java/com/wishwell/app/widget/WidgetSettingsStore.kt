package com.wishwell.app.widget

import android.content.Context
import android.content.SharedPreferences

/**
 * Per-widget appearance settings stored in SharedPreferences.
 * Keys are prefixed with the widget key so multiple widgets can have different looks.
 */
object WidgetSettingsStore {

    data class WidgetSettings(
        val theme: String,        // "system" | "light" | "dark"
        val style: String,        // "list" | "card" | "compact"
        val transparency: Int,    // 0..100 (100 = opaque)
        val blur: Boolean
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)

    private fun themeKey(widgetKey: String) = "widget_${widgetKey}_theme"
    private fun styleKey(widgetKey: String) = "widget_${widgetKey}_style"
    private fun transparencyKey(widgetKey: String) = "widget_${widgetKey}_transparency"
    private fun blurKey(widgetKey: String) = "widget_${widgetKey}_blur"

    fun load(context: Context, widgetKey: String): WidgetSettings {
        val p = prefs(context)
        return WidgetSettings(
            theme = p.getString(themeKey(widgetKey), "system") ?: "system",
            style = p.getString(styleKey(widgetKey), "list") ?: "list",
            transparency = p.getInt(transparencyKey(widgetKey), 100),
            blur = p.getBoolean(blurKey(widgetKey), false)
        )
    }

    fun save(context: Context, widgetKey: String, settings: WidgetSettings) {
        val editor = prefs(context).edit()
        editor.putString(themeKey(widgetKey), settings.theme)
        editor.putString(styleKey(widgetKey), settings.style)
        editor.putInt(transparencyKey(widgetKey), settings.transparency)
        editor.putBoolean(blurKey(widgetKey), settings.blur)
        editor.apply()
    }
}
