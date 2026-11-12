package com.wishwell.app.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import com.wishwell.app.WishwellApp
import kotlinx.coroutines.flow.first

// Appearance keys live in each widget's own Glance state, so two widgets on the home screen can
// look completely different.
internal val widgetTextSizeKey = stringPreferencesKey("widget_text_size")
internal val widgetShowImagesKey = booleanPreferencesKey("widget_show_images")
internal val widgetBackgroundOpacityKey = intPreferencesKey("widget_background_opacity")
internal val widgetBorderStyleKey = stringPreferencesKey("widget_border_style")
internal val widgetBorderColorKey = stringPreferencesKey("widget_border_color")
internal val widgetBorderWidthKey = intPreferencesKey("widget_border_width")

internal data class WidgetConfig(
    val textSize: String = "default",
    val showImages: Boolean = true,
    val backgroundOpacity: Int = 100,
    val borderStyle: String = "none",
    val borderColor: String = "#1F3D2E",
    val borderWidth: Int = 2
)

internal fun widgetTextScale(size: String): Float = when (size) {
    "small" -> 0.88f
    "large" -> 1.18f
    else -> 1f
}

internal fun Preferences.toWidgetConfig(fallback: WidgetConfig): WidgetConfig = WidgetConfig(
    textSize = this[widgetTextSizeKey] ?: fallback.textSize,
    showImages = this[widgetShowImagesKey] ?: fallback.showImages,
    backgroundOpacity = (this[widgetBackgroundOpacityKey] ?: fallback.backgroundOpacity)
        .coerceIn(20, 100),
    borderStyle = this[widgetBorderStyleKey] ?: fallback.borderStyle,
    borderColor = this[widgetBorderColorKey] ?: fallback.borderColor,
    borderWidth = (this[widgetBorderWidthKey] ?: fallback.borderWidth).coerceIn(1, 8)
)

/**
 * Defaults for a widget that has never been configured. These come from the old global widget
 * preferences so existing home-screen widgets keep the look they had before configuration became
 * per-widget.
 */
internal suspend fun legacyWidgetDefaults(context: Context): WidgetConfig {
    val prefs = (context.applicationContext as? WishwellApp)?.container?.prefs
        ?: return WidgetConfig()
    return WidgetConfig(
        textSize = prefs.widgetTextSize.first(),
        showImages = prefs.widgetShowImages.first(),
        backgroundOpacity = prefs.widgetBackgroundOpacity.first(),
        borderStyle = prefs.widgetBorderStyle.first(),
        borderColor = prefs.widgetBorderColor.first(),
        borderWidth = prefs.widgetBorderWidth.first()
    )
}

internal suspend fun readWidgetConfig(
    context: Context,
    glanceId: GlanceId,
    widget: GlanceAppWidget
): WidgetConfig {
    val fallback = legacyWidgetDefaults(context)
    val prefs: Preferences = runCatching {
        widget.getAppWidgetState<Preferences>(context, glanceId)
    }.getOrDefault(emptyPreferences())
    return prefs.toWidgetConfig(fallback)
}

/** Writes appearance keys to this widget's own state and repaints it. */
internal suspend fun writeWidgetConfig(
    context: Context,
    glanceId: GlanceId,
    widget: GlanceAppWidget,
    update: suspend (MutablePreferences) -> Unit
) {
    updateAppWidgetState(context, glanceId, update)
    // The launcher may not have bound the widget yet during first-time setup; the system repaints
    // it once the configuration result is returned.
    runCatching { widget.update(context, glanceId) }
}
