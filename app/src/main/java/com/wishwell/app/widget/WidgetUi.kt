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

private fun syncWidgetIntent(): Intent =
    Intent("com.wishwell.app.action.SYNC_WIDGET").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        setPackage("com.wishwell.app")
    }

private fun editWidgetIntent(widgetKey: String): Intent =
    Intent(Intent.ACTION_VIEW).setClassName("com.wishwell.app", "com.wishwell.app.widget.WidgetEditActivity").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("widget_id", widgetKey)
    }

@Composable
internal fun WidgetListContent(
    context: Context,
    title: String,
    subtitle: String,
    entries: List<WidgetEntry>,
    emptyMessage: String,
    settings: WidgetSettingsStore.WidgetSettings
) {
    val colors = resolveColors(context, settings.theme)
    val alpha = settings.transparency.coerceIn(0, 100) / 100f
    val accent = WidgetSettingsStore.parseAccentColor(settings.accentColor) ?: colors.forest

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(colors.paper.copy(alpha = alpha)))
            .padding(16.dp)
            .clickable(actionStartActivity(appIntent()))
    ) {
        // ── Blur overlay (visual indicator when blur is enabled) ──
        if (settings.blur) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(accent.copy(alpha = 0.05f)))
            ) { }
        }
        // ── Header ──
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = title,
                    style = TextStyle(
                        color = ColorProvider(colors.ink),
                        fontSize = (16.sp * settings.fontSize.coerceIn(0.75f, 1.5f)),
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = TextStyle(color = ColorProvider(colors.muted), fontSize = 10.sp),
                    maxLines = 2
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Box(
                modifier = GlanceModifier
                    .background(ColorProvider(accent))
                    .cornerRadius(10.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clickable(actionStartActivity(syncWidgetIntent()))
            ) {
                Text(
                    text = "↻ Sync",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(12.dp))
        // ── Divider ──
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorProvider(colors.hairline))
        ) { }
        Spacer(modifier = GlanceModifier.height(16.dp))

        if (entries.isEmpty()) {
            WidgetSkeletonLoader(colors = colors, alpha = alpha, style = settings.style, count = 3)
        } else {
            val maxEntries = when (settings.widgetSize) {
                "small" -> 3
                "large" -> 12
                else -> 8
            }
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(items = entries.take(maxEntries)) { entry ->
                    EntryRow(
                        entry = entry,
                        colors = colors,
                        alpha = alpha,
                        style = settings.style,
                        accent = accent,
                        showPrice = settings.showPrice,
                        showStore = settings.showStore,
                        fontSize = settings.fontSize
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: WidgetEntry,
    colors: WidgetColorsHolder,
    alpha: Float,
    style: String = "list",
    accent: Color = Color(0xFF1F3D2E),
    showPrice: Boolean = true,
    showStore: Boolean = true,
    fontSize: Float = 1.0f
) {
    val isCompact = style == "compact"
    val isCard = style == "card"
    val baseSize = if (isCompact) 11f else 14f
    val titleSize = baseSize * fontSize.coerceIn(0.75f, 1.5f)
    val subtitleSize = 10f * fontSize.coerceIn(0.75f, 1.5f)

    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(
                if (isCard) ColorProvider(accent.copy(alpha = 0.10f)) else ColorProvider(colors.sunken.copy(alpha = alpha))
            )
            .cornerRadius(16.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable(actionStartActivity(appIntent(entry.id))),
        contentAlignment = Alignment.Center
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = entry.title,
                    style = TextStyle(
                        color = ColorProvider(colors.ink),
                        fontSize = titleSize.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = if (isCompact) 1 else 2
                )
                if (!isCompact) {
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showStore) {
                            Text(
                                text = entry.storeName,
                                style = TextStyle(color = ColorProvider(colors.muted), fontSize = subtitleSize.sp),
                                maxLines = 1
                            )
                            Spacer(modifier = GlanceModifier.width(8.dp))
                        }
                        if (showPrice) {
                            Text(
                                text = entry.priceText,
                                style = TextStyle(color = ColorProvider(colors.muted), fontSize = subtitleSize.sp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            entry.badge?.let { badge ->
                Spacer(modifier = GlanceModifier.width(8.dp))
                Box(
                    modifier = GlanceModifier
                        .background(ColorProvider(if (entry.isDrop) Color(0xFFDCE8D6) else accent))
                        .cornerRadius(8.dp)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badge,
                        style = TextStyle(
                            color = ColorProvider(
                                if (entry.isDrop) Color(0xFF1F3D2E) else Color.White
                            ),
                            fontSize = if (isCompact) 8.sp else 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetSkeletonLoader(
    colors: WidgetColorsHolder,
    alpha: Float,
    style: String = "list",
    count: Int = 3
) {
    val isCompact = style == "compact"

    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(count) { _ ->
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(colors.sunken.copy(alpha = alpha)))
                    .cornerRadius(16.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        // Title skeleton line
                        Box(
                            modifier = GlanceModifier
                                .width(if (isCompact) 150.dp else 200.dp)
                                .height(if (isCompact) 11.dp else 14.dp)
                                .background(ColorProvider(colors.muted.copy(alpha = 0.35f)))
                                .cornerRadius(4.dp)
                        ) { }
                        if (!isCompact) {
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            // Subtitle skeleton line
                            Box(
                                modifier = GlanceModifier
                                    .width(100.dp)
                                    .height(10.dp)
                                    .background(ColorProvider(colors.muted.copy(alpha = 0.25f)))
                                    .cornerRadius(4.dp)
                            ) { }
                        }
                    }
                    // Badge skeleton box
                    Box(
                        modifier = GlanceModifier
                            .width(34.dp)
                            .height(if (isCompact) 16.dp else 20.dp)
                            .background(ColorProvider(colors.hairline.copy(alpha = 0.5f)))
                            .cornerRadius(8.dp)
                    ) { }
                }
            }
            Spacer(modifier = GlanceModifier.height(12.dp))
        }
    }
}
