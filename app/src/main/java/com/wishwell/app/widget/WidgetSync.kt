package com.wishwell.app.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.wishwell.app.work.WorkScheduler
import kotlinx.coroutines.delay

/** Per-widget flag that makes the sync button show its spinner while a refresh is running. */
internal val syncingKey = booleanPreferencesKey("widget_syncing")

private const val MIN_SPIN_MS = 900L

/**
 * Queues a background price check and flashes the native spinner on the sync button for tap
 * feedback. The worker repaints the widget with fresh prices when the check finishes.
 */
private suspend fun runWidgetSync(
    context: Context,
    glanceId: GlanceId,
    widget: GlanceAppWidget
) {
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

class SyncWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        runWidgetSync(context, glanceId, WishlistedItemsWidget())
    }
}

class SyncPriceDropsAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        runWidgetSync(context, glanceId, PriceDropsWidget())
    }
}
