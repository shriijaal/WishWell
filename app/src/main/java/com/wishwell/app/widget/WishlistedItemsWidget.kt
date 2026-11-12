package com.wishwell.app.widget

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import com.wishwell.app.WishwellApp
import com.wishwell.app.work.WorkScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Per-widget flag that makes the sync button show its spinner while a refresh is running. */
internal val syncingKey = booleanPreferencesKey("wishlisted_widget_syncing")

class WishlistedItemsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, glanceId: GlanceId) {
        val prefs = (context.applicationContext as? WishwellApp)?.container?.prefs
        val textSize = prefs?.widgetTextSize?.first() ?: "default"
        val showImages = prefs?.widgetShowImages?.first() ?: true
        val backgroundOpacity = (prefs?.widgetBackgroundOpacity?.first() ?: 100) / 100f
        val borderStyle = prefs?.widgetBorderStyle?.first() ?: "none"
        val borderHex = prefs?.widgetBorderColor?.first()
        val borderWidth = prefs?.widgetBorderWidth?.first() ?: 2
        val accentKey = prefs?.accentColor?.first()
        val borderColor = resolveWidgetBorderColor(context, borderStyle, borderHex, accentKey)
        val entries = WidgetData.load(
            context = context,
            dropsOnly = false,
            sortOrder = "recent",
            limit = 8,
            loadImages = showImages
        )
        provideContent {
            WidgetListContent(
                context = context,
                title = "Wishlisted items",
                subtitle = if (entries.isEmpty()) "" else "${entries.size} saved",
                entries = entries,
                emptyMessage = "No wishlisted items yet",
                textScale = prefs?.widgetTextScale(textSize) ?: 1f,
                showImages = showImages,
                backgroundOpacity = backgroundOpacity,
                borderColor = borderColor,
                borderWidth = borderWidth
            )
        }
    }
}

class WishlistedItemsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WishlistedItemsWidget()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.appwidget.action.APPWIDGET_OPTION_CHANGED") {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { WishwellWidgetUpdater.updateAll(context) }
            }
        }
        super.onReceive(context, intent)
    }
}

/**
 * Queues a background price check and flashes the native spinner on the sync button for tap
 * feedback. The worker repaints the widget with fresh prices when the check finishes.
 */
class SyncWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val widget = WishlistedItemsWidget()
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[syncingKey] = true
        }
        widget.update(context, glanceId)

        // Scraping every item takes far too long for a broadcast callback, so hand the actual
        // price refresh to WorkManager. It updates the widget again once the check completes.
        WorkScheduler(context.applicationContext).scheduleQuickCheck()

        // Keep the spinner up long enough to register as motion instead of a flicker.
        delay(MIN_SPIN_MS)

        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[syncingKey] = false
        }
        widget.update(context, glanceId)
    }

    private companion object {
        const val MIN_SPIN_MS = 900L
    }
}
