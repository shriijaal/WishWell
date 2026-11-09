package com.wishwell.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.unit.ColorProvider

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
}
