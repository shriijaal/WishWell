package com.wishwell.app.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import com.wishwell.app.WishwellApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.glance.GlanceId

class PriceDropsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, glanceId: GlanceId) {
        val prefs = (context.applicationContext as? WishwellApp)?.container?.prefs
        val config = readWidgetConfig(context, glanceId, this)
        val accentKey = prefs?.accentColor?.first()
        val borderColor = resolveWidgetBorderColor(
            context,
            config.borderStyle,
            config.borderColor,
            accentKey
        )
        val entries = WidgetData.load(
            context = context,
            dropsOnly = true,
            sortOrder = "discount",
            limit = 8,
            loadImages = config.showImages
        )
        provideContent {
            WidgetListContent(
                context = context,
                title = "Price drops",
                subtitle = if (entries.isEmpty()) "" else "${entries.size} on sale",
                entries = entries,
                emptyMessage = "No price drops right now",
                textScale = widgetTextScale(config.textSize),
                showImages = config.showImages,
                backgroundOpacity = config.backgroundOpacity / 100f,
                borderColor = borderColor,
                borderWidth = config.borderWidth,
                syncAction = actionRunCallback<SyncPriceDropsAction>()
            )
        }
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
