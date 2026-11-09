package com.wishwell.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PriceDropsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, glanceId: GlanceId) {
        val entries = WidgetData.load(context, dropsOnly = true, limit = 8)
        val settings = WidgetSettingsStore.load(context, glanceId.toString())
        provideContent {
            WidgetListContent(
                context = context,
                title = "Price drops",
                subtitle = if (entries.isEmpty()) "No items on sale or at a low right now." else "${entries.size} deal${if (entries.size == 1) "" else "s"}",
                entries = entries,
                emptyMessage = "No price drops right now",
                accent = ColorProvider(widgetAccent(context, settings)),
                settings = settings
            )
        }
    }
}

private fun widgetAccent(context: Context, settings: WidgetSettingsStore.WidgetSettings): Color {
    return when (settings.theme) {
        "dark" -> Color(0xFFCFE39C)
        else -> Color(0xFF5B7141)
    }
}

class PriceDropsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PriceDropsWidget()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.appwidget.action.APPWIDGET_OPTION_CHANGED") {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { WishwellWidgetUpdater.updateAll(context) }
            }
        }
        super.onReceive(context, intent)
    }
}
