package com.wishwell.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.unit.ColorProvider

class PriceDropsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entries = WidgetData.load(context, dropsOnly = true, limit = 8)
        provideContent {
            WidgetListContent(
                title = "Price drops",
                subtitle = if (entries.isEmpty()) "No items on sale or at a low right now." else "${entries.size} deal${if (entries.size == 1) "" else "s"}",
                entries = entries,
                emptyMessage = "No price drops right now",
                accent = ColorProvider(WidgetColors.moss)
            )
        }
    }
}

class PriceDropsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PriceDropsWidget()
}
