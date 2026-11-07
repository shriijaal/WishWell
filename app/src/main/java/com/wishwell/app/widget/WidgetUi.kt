package com.wishwell.app.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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

internal object WidgetColors {
    val paper = Color(0xFFFFFCF4)
    val ink = Color(0xFF171A14)
    val muted = Color(0xFF63675A)
    val hairline = Color(0xFFE3DCCC)
    val forest = Color(0xFF1F3D2E)
    val moss = Color(0xFF5B7141)
    val sunken = Color(0xFFF4F1E8)
    val dealBg = Color(0xFFDCE8D6)
}

private fun appIntent(contextId: Long? = null): Intent =
    Intent(Intent.ACTION_VIEW).setClassName("com.wishwell.app", "com.wishwell.app.MainActivity").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (contextId != null && contextId > 0) putExtra(Notifier.EXTRA_ITEM_ID, contextId)
    }

@Composable
internal fun WidgetListContent(
    title: String,
    subtitle: String,
    entries: List<WidgetEntry>,
    emptyMessage: String,
    accent: ColorProvider
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetColors.paper))
            .padding(12.dp)
            .clickable(actionStartActivity(appIntent()))
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = TextStyle(
                    color = ColorProvider(WidgetColors.ink),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(WidgetColors.sunken))
                    .cornerRadius(10.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Open",
                    style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = subtitle,
            style = TextStyle(color = ColorProvider(WidgetColors.muted), fontSize = 11.sp),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        if (entries.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(WidgetColors.sunken))
                    .cornerRadius(12.dp)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyMessage,
                    style = TextStyle(color = ColorProvider(WidgetColors.muted), fontSize = 12.sp)
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(entries, itemId = { it.id }) { entry ->
                    EntryRow(entry = entry, accent = accent)
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: WidgetEntry, accent: ColorProvider) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(WidgetColors.sunken))
            .cornerRadius(12.dp)
            .padding(8.dp)
            .clickable(actionStartActivity(appIntent(entry.id))),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = entry.title,
                style = TextStyle(
                    color = ColorProvider(WidgetColors.ink),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(1.dp))
            Text(
                text = "${entry.storeName} · ${entry.priceText}",
                style = TextStyle(color = ColorProvider(WidgetColors.muted), fontSize = 11.sp),
                maxLines = 1
            )
        }
        entry.badge?.let { badge ->
            Spacer(modifier = GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(if (entry.isDrop) WidgetColors.dealBg else WidgetColors.hairline))
                    .cornerRadius(8.dp)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badge,
                    style = TextStyle(
                        color = if (entry.isDrop) ColorProvider(WidgetColors.forest) else ColorProvider(WidgetColors.muted),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
