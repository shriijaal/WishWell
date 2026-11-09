package com.wishwell.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.wishwell.app.MainActivity
import com.wishwell.app.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WishlistedItemsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, glanceId: GlanceId) {
        val entries = WidgetData.load(context, dropsOnly = false, limit = 8)
        val settings = WidgetSettingsStore.load(context, "default")
        provideContent {
            WidgetListContent(
                context = context,
                title = "Wishlisted items",
                subtitle = if (entries.isEmpty()) "Save items in WishWell to see them here." else "${entries.size} saved",
                entries = entries,
                emptyMessage = "No wishlisted items yet",
                settings = settings
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
