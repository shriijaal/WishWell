package com.wishwell.app.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.wishwell.app.R
import com.wishwell.app.notify.Notifier
import com.wishwell.app.ui.theme.parseHexColor
import com.wishwell.app.ui.theme.resolveAccent

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

    // Badges look the same in both themes: a soft green chip with readable green ink.
    val lightBadgeBg = Color(0xFFDCE8D6)
    val lightBadgeInk = Color(0xFF1F3D2E)
    val darkBadgeBg = Color(0xFF2C3A24)
    val darkBadgeInk = Color(0xFFCFE39C)
}

private fun isDarkTheme(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    return false
}

private data class WidgetColorsHolder(
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val hairline: Color,
    val sunken: Color,
    val forest: Color,
    val badgeBg: Color,
    val badgeInk: Color,
    val isDark: Boolean
)

private fun resolveColors(context: Context): WidgetColorsHolder {
    val systemDark = isDarkTheme(context)
    return if (systemDark) {
        WidgetColorsHolder(
            paper = WidgetColors.darkBackground,
            ink = WidgetColors.darkInk,
            muted = WidgetColors.darkMuted,
            hairline = WidgetColors.darkHairline,
            sunken = WidgetColors.darkSunken,
            forest = WidgetColors.darkForest,
            badgeBg = WidgetColors.darkBadgeBg,
            badgeInk = WidgetColors.darkBadgeInk,
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
            badgeBg = WidgetColors.lightBadgeBg,
            badgeInk = WidgetColors.lightBadgeInk,
            isDark = false
        )
    }
}

/**
 * Resolves the widget outline color from its stored style: "none", "accent" (follows the app
 * accent, picked for the current light/dark theme) or "custom" (user-picked hex).
 */
internal fun resolveWidgetBorderColor(
    context: Context,
    style: String,
    customHex: String?,
    accentKey: String?
): Color? = when (style) {
    "accent" -> {
        val preset = resolveAccent(accentKey)
        if (isDarkTheme(context)) preset.darkPrimary else preset.lightPrimary
    }
    "custom" -> parseHexColor(customHex)
    else -> null
}

/** The sync pill keeps the same dark forest fill in both themes so its white content stays readable. */
private val syncPillColor = Color(0xFF1F3D2E)
private val syncPillContent = Color(0xFFF4F1E8)

private fun appIntent(contextId: Long? = null): Intent =
    Intent(Intent.ACTION_VIEW).setClassName("com.wishwell.app", "com.wishwell.app.MainActivity").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (contextId != null && contextId > 0) putExtra(Notifier.EXTRA_ITEM_ID, contextId)
    }

private fun scaledSize(value: Float, scale: Float) = (value * scale).sp

@Composable
internal fun WidgetListContent(
    context: Context,
    title: String,
    subtitle: String,
    entries: List<WidgetEntry>,
    emptyMessage: String,
    textScale: Float = 1f,
    showImages: Boolean = true,
    backgroundOpacity: Float = 1f,
    borderColor: Color? = null,
    borderWidth: Int = 0,
    syncAction: Action
) {
    val outline = if (borderColor == null) 0 else borderWidth.coerceIn(1, MAX_BORDER_DP)

    if (outline > 0 && borderColor != null) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(
                    ColorProvider(borderColor.copy(alpha = backgroundOpacity.coerceIn(0.2f, 1f)))
                )
                .cornerRadius(WIDGET_CORNER_DP.dp)
        ) {
            WidgetCard(
                context = context,
                title = title,
                subtitle = subtitle,
                entries = entries,
                emptyMessage = emptyMessage,
                textScale = textScale,
                showImages = showImages,
                backgroundOpacity = backgroundOpacity,
                syncAction = syncAction,
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(outline.dp),
                cornerRadiusDp = WIDGET_CORNER_DP - outline
            )
        }
    } else {
        WidgetCard(
            context = context,
            title = title,
            subtitle = subtitle,
            entries = entries,
            emptyMessage = emptyMessage,
            textScale = textScale,
            showImages = showImages,
            backgroundOpacity = backgroundOpacity,
            syncAction = syncAction,
            modifier = GlanceModifier.fillMaxSize(),
            cornerRadiusDp = 0
        )
    }
}

internal const val WIDGET_CORNER_DP = 20
internal const val MAX_BORDER_DP = 8

@Composable
private fun WidgetCard(
    context: Context,
    title: String,
    subtitle: String,
    entries: List<WidgetEntry>,
    emptyMessage: String,
    textScale: Float,
    showImages: Boolean,
    backgroundOpacity: Float,
    syncAction: Action,
    modifier: GlanceModifier,
    cornerRadiusDp: Int
) {
    val colors = resolveColors(context)
    val isSyncing = currentState<Preferences>()[syncingKey] ?: false

    val cardModifier = modifier
        .background(ColorProvider(colors.paper.copy(alpha = backgroundOpacity.coerceIn(0.2f, 1f))))
        .let { if (cornerRadiusDp > 0) it.cornerRadius(cornerRadiusDp.dp) else it }

    Column(
        modifier = cardModifier
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity(appIntent()))
    ) {
        // ── Header ──
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        color = ColorProvider(colors.ink),
                        fontSize = scaledSize(13f, textScale),
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = subtitle,
                        style = TextStyle(
                            color = ColorProvider(colors.muted),
                            fontSize = scaledSize(10f, textScale)
                        ),
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = GlanceModifier.width(10.dp))
            Row(
                modifier = GlanceModifier
                    .background(ColorProvider(syncPillColor))
                    .cornerRadius(11.dp)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .clickable(syncAction),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSyncing) {
                    AndroidRemoteViews(
                        remoteViews = RemoteViews(context.packageName, R.layout.widget_sync_spinner),
                        modifier = GlanceModifier.size(12.dp)
                    )
                } else {
                    Text(
                        text = "↻",
                        style = TextStyle(
                            color = ColorProvider(syncPillContent),
                            fontSize = scaledSize(13f, textScale),
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.width(5.dp))
                Text(
                    text = if (isSyncing) "Syncing" else "Sync",
                    style = TextStyle(
                        color = ColorProvider(syncPillContent),
                        fontSize = scaledSize(10f, textScale),
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(9.dp))
        // ── Divider ──
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorProvider(colors.hairline))
        ) { }
        Spacer(modifier = GlanceModifier.height(7.dp))

        if (entries.isEmpty()) {
            EmptyState(colors = colors, message = emptyMessage)
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(items = entries.take(8)) { entry ->
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        EntryRow(
                            entry = entry,
                            colors = colors,
                            textScale = textScale,
                            showImages = showImages
                        )
                        Spacer(modifier = GlanceModifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: WidgetEntry,
    colors: WidgetColorsHolder,
    textScale: Float,
    showImages: Boolean
) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ColorProvider(colors.sunken))
            .cornerRadius(11.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(actionStartActivity(appIntent(entry.id))),
        contentAlignment = Alignment.Center
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            entry.image?.takeIf { showImages }?.let { bitmap ->
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.size(34.dp).cornerRadius(8.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = GlanceModifier.width(9.dp))
            }
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = entry.title,
                    style = TextStyle(
                        color = ColorProvider(colors.ink),
                        fontSize = scaledSize(12f, textScale),
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 2
                )
                Spacer(modifier = GlanceModifier.height(1.dp))
                Text(
                    text = entry.storeName,
                    style = TextStyle(
                        color = ColorProvider(colors.muted),
                        fontSize = scaledSize(10f, textScale)
                    ),
                    maxLines = 1
                )
            }
            Spacer(modifier = GlanceModifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = entry.priceText,
                    style = TextStyle(
                        color = ColorProvider(colors.ink),
                        fontSize = scaledSize(12f, textScale),
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                entry.badge?.let { badge ->
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Box(
                        modifier = GlanceModifier
                            .background(ColorProvider(colors.badgeBg))
                            .cornerRadius(7.dp)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            style = TextStyle(
                                color = ColorProvider(colors.badgeInk),
                                fontSize = scaledSize(9f, textScale),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(colors: WidgetColorsHolder, message: String) {
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "♡",
                style = TextStyle(color = ColorProvider(colors.muted), fontSize = 26.sp)
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = message,
                style = TextStyle(
                    color = ColorProvider(colors.ink),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                ),
                maxLines = 2
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "Open Wishwell and save an item to get started.",
                style = TextStyle(
                    color = ColorProvider(colors.muted),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 2
            )
        }
    }
}
