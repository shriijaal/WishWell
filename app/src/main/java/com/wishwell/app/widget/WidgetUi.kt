package com.wishwell.app.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
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
    // Light theme
    val lightPaper = Color(0xFFFFFCF4)
    val lightInk = Color(0xFF171A14)
    val lightMuted = Color(0xFF63675A)
    val lightHairline = Color(0xFFE3DCCC)
    val lightSunken = Color(0xFFF4F1E8)
    val lightForest = Color(0xFF1F3D2E)

    // Dark theme
    val darkBackground = Color(0xFF11140F)
    val darkInk = Color(0xFFF1EEE2)
    val darkMuted = Color(0xFFA9AF9D)
    val darkHairline = Color(0xFF333A2C)
    val darkSunken = Color(0xFF22271D)
    val darkForest = Color(0xFFCFE39C)
}

private fun isDarkTheme(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    return false
}

private fun resolveColors(context: Context, theme: String): WidgetColorsHolder {
    val systemDark = isDarkTheme(context)
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    return if (dark) {
        WidgetColorsHolder(
            paper = WidgetColors.darkBackground,
            ink = WidgetColors.darkInk,
            muted = WidgetColors.darkMuted,
            hairline = WidgetColors.darkHairline,
            sunken = WidgetColors.darkSunken,
            forest = WidgetColors.darkForest,
            isDark = true
        )
    } else {
        WidgetColorsHolder(
            paper = WidgetColors.lightPaper,
            ink = WidgetColors.lightInk,
            muted = WidgetColors.lightMuted,
            hairline = WidgetColors.lightHairline,
            sunken = WidgetColors.lightSunken,
            forest = WidgetColors.lightForest,
            isDark = false
        )
    }
}

private data class WidgetColorsHolder(
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val hairline: Color,
    val sunken: Color,
    val forest: Color,
    val isDark: Boolean
)

private fun appIntent(contextId: Long? = null): Intent =
    Intent(Intent.ACTION_VIEW).setClassName("com.wishwell.app", "com.wishwell.app.MainActivity").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (contextId != null && contextId > 0) putExtra(Notifier.EXTRA_ITEM_ID, contextId)
    }

private fun editWidgetIntent(widgetKey: String): Intent =
    Intent(Intent.ACTION_VIEW).setClassName("com.wishwell.app", "com.wishwell.app.widget.WidgetEditActivity").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("widget_id", widgetKey)
    }

@Composable
internal fun WidgetListContent(
    context: Context,
    widgetKey: String,
    title: String,
    subtitle: String,
    entries: List<WidgetEntry>,
    emptyMessage: String,
    accent: ColorProvider,
    settings: WidgetSettingsStore.WidgetSettings
) {
    val colors = resolveColors(context, settings.theme)
    val alpha = settings.transparency.coerceIn(0, 100) / 100f

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(colors.paper.copy(alpha = alpha)))
            .padding(12.dp)
            .clickable(actionStartActivity(appIntent()))
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = TextStyle(
                    color = ColorProvider(colors.ink),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(colors.sunken.copy(alpha = alpha)))
                    .cornerRadius(10.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Open",
                    style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                )
            }
            // Edit button - opens WidgetEditActivity
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(colors.sunken.copy(alpha = alpha)))
                    .cornerRadius(10.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .clickable(actionStartActivity(editWidgetIntent(widgetKey)))
            ) {
                Text(
                    text = "Edit",
                    style = TextStyle(color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = subtitle,
            style = TextStyle(color = ColorProvider(colors.muted), fontSize = 11.sp),
            maxLines = 1
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        if (entries.isEmpty()) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(colors.sunken.copy(alpha = alpha)))
                    .cornerRadius(12.dp)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyMessage,
                    style = TextStyle(color = ColorProvider(colors.muted), fontSize = 12.sp)
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(entries, itemId = { it.id }) { entry ->
                    EntryRow(entry = entry, accent = accent, colors = colors, alpha = alpha)
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: WidgetEntry,
    accent: ColorProvider,
    colors: WidgetColorsHolder,
    alpha: Float
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(colors.sunken.copy(alpha = alpha)))
            .cornerRadius(12.dp)
            .padding(8.dp)
            .clickable(actionStartActivity(appIntent(entry.id))),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = entry.title,
                style = TextStyle(
                    color = ColorProvider(colors.ink),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(1.dp))
            Text(
                text = "${entry.storeName} · ${entry.priceText}",
                style = TextStyle(color = ColorProvider(colors.muted), fontSize = 11.sp),
                maxLines = 1
            )
        }
        entry.badge?.let { badge ->
            Spacer(modifier = GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(if (entry.isDrop) Color(0xFFDCE8D6) else colors.hairline))
                    .cornerRadius(8.dp)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badge,
                    style = TextStyle(
                        color = if (entry.isDrop) ColorProvider(colors.forest) else ColorProvider(colors.muted),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
