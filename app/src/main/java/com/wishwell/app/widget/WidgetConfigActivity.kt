package com.wishwell.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wishwell.app.WishwellApp
import com.wishwell.app.ui.components.SettingsCard
import com.wishwell.app.ui.components.WishPill
import com.wishwell.app.ui.settings.AccentColorPickerDialog
import com.wishwell.app.ui.theme.LocalWishExtraColors
import com.wishwell.app.ui.theme.WishwellTheme
import com.wishwell.app.ui.theme.colorToHex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Reconfiguration screen for the home-screen widget. Declared as the receiver's
 * `android:configure` activity, so the launcher shows a settings entry next to Remove on
 * long-press. A live preview mirrors the widget as the options change.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)

        // Cancelling the first-time setup must not place the widget on the home screen.
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE) {
            setResult(RESULT_CANCELED, resultIntent)
        }

        val container = (application as WishwellApp).container

        setContent {
            val accent by container.prefs.accentColor.collectAsStateWithLifecycle(initialValue = "green")
            val uiTheme by container.prefs.uiTheme.collectAsStateWithLifecycle(initialValue = "system")
            val darkTheme = when (uiTheme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            val textSize by container.prefs.widgetTextSize.collectAsStateWithLifecycle(initialValue = "default")
            val showImages by container.prefs.widgetShowImages.collectAsStateWithLifecycle(initialValue = true)
            val opacity by container.prefs.widgetBackgroundOpacity.collectAsStateWithLifecycle(initialValue = 100)
            val borderStyle by container.prefs.widgetBorderStyle.collectAsStateWithLifecycle(initialValue = "none")
            val borderHex by container.prefs.widgetBorderColor.collectAsStateWithLifecycle(initialValue = "#1F3D2E")
            val borderWidth by container.prefs.widgetBorderWidth.collectAsStateWithLifecycle(initialValue = 2)
            val borderColor = resolveWidgetBorderColor(applicationContext, borderStyle, borderHex, accent)
            val scope = rememberCoroutineScope()

            WishwellTheme(darkTheme = darkTheme, accentKey = accent) {
                WidgetConfigContent(
                    textSize = textSize,
                    showImages = showImages,
                    backgroundOpacity = opacity,
                    borderStyle = borderStyle,
                    borderColor = borderColor,
                    borderWidth = borderWidth,
                    onTextSizeChange = { size ->
                        scope.launch {
                            container.prefs.setWidgetTextSize(size)
                            WishwellWidgetUpdater.updateAll(container.appContext)
                        }
                    },
                    onShowImagesChange = { show ->
                        scope.launch {
                            container.prefs.setWidgetShowImages(show)
                            WishwellWidgetUpdater.updateAll(container.appContext)
                        }
                    },
                    onBackgroundOpacityChange = { percent ->
                        scope.launch {
                            container.prefs.setWidgetBackgroundOpacity(percent)
                            WishwellWidgetUpdater.updateAll(container.appContext)
                        }
                    },
                    onBorderStyleChange = { style ->
                        scope.launch {
                            container.prefs.setWidgetBorderStyle(style)
                            WishwellWidgetUpdater.updateAll(container.appContext)
                        }
                    },
                    onBorderColorChange = { hex ->
                        scope.launch {
                            container.prefs.setWidgetBorderColor(hex)
                            container.prefs.setWidgetBorderStyle("custom")
                            WishwellWidgetUpdater.updateAll(container.appContext)
                        }
                    },
                    onBorderWidthChange = { dp ->
                        scope.launch {
                            container.prefs.setWidgetBorderWidth(dp)
                            WishwellWidgetUpdater.updateAll(container.appContext)
                        }
                    },
                    onDone = {
                        setResult(RESULT_OK, resultIntent)
                        // The system skips the initial update while a configuration activity is
                        // open, so ask Glance for a repaint before leaving.
                        scope.launch { WishwellWidgetUpdater.updateAll(container.appContext) }
                        finish()
                    },
                    onCancel = {
                        setResult(RESULT_CANCELED, resultIntent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun WidgetConfigContent(
    textSize: String,
    showImages: Boolean,
    backgroundOpacity: Int,
    borderStyle: String,
    borderColor: Color?,
    borderWidth: Int,
    onTextSizeChange: (String) -> Unit,
    onShowImagesChange: (Boolean) -> Unit,
    onBackgroundOpacityChange: (Int) -> Unit,
    onBorderStyleChange: (String) -> Unit,
    onBorderColorChange: (String) -> Unit,
    onBorderWidthChange: (Int) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    var sliderOpacity by remember(backgroundOpacity) {
        mutableFloatStateOf(backgroundOpacity.toFloat())
    }
    var sliderBorderWidth by remember(borderWidth) {
        mutableFloatStateOf(borderWidth.toFloat())
    }
    var showBorderPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "WIDGET",
            style = MaterialTheme.typography.labelSmall,
            color = extra.muted
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Widget settings",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Changes apply as you adjust them.",
            style = MaterialTheme.typography.bodySmall,
            color = extra.muted
        )

        Spacer(Modifier.height(16.dp))
        WidgetPreview(
            textSize = textSize,
            showImages = showImages,
            opacityPercent = sliderOpacity.roundToInt(),
            borderColor = borderColor,
            borderWidthDp = if (borderStyle == "none") 0 else sliderBorderWidth.roundToInt()
        )
        Spacer(Modifier.height(18.dp))

        SettingsCard {
            Text(
                text = "Text size",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "How large item names and prices appear in the widget.",
                style = MaterialTheme.typography.bodySmall,
                color = extra.muted
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("small" to "Small", "default" to "Default", "large" to "Large")
                    .forEach { (key, label) ->
                        WishPill(
                            text = label,
                            selected = textSize == key,
                            onClick = { onTextSizeChange(key) }
                        )
                    }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Item images",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Show a thumbnail for each saved item",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.muted
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = showImages,
                    onCheckedChange = onShowImagesChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onPrimary,
                        checkedTrackColor = colors.primary,
                        uncheckedThumbColor = extra.muted,
                        uncheckedTrackColor = extra.sunken
                    )
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Background opacity",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${sliderOpacity.roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted
                )
            }
            Slider(
                value = sliderOpacity,
                onValueChange = { sliderOpacity = it },
                onValueChangeFinished = { onBackgroundOpacityChange(sliderOpacity.roundToInt()) },
                valueRange = 20f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = colors.primary,
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = extra.hairline
                )
            )
            Text(
                text = "Lower values let more of your wallpaper show through.",
                style = MaterialTheme.typography.labelSmall,
                color = extra.muted
            )

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Border",
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Outline around the edge of the widget.",
                style = MaterialTheme.typography.bodySmall,
                color = extra.muted
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("none" to "None", "accent" to "Accent", "custom" to "Custom")
                    .forEach { (key, label) ->
                        WishPill(
                            text = label,
                            selected = borderStyle == key,
                            onClick = {
                                onBorderStyleChange(key)
                                if (key == "custom") showBorderPicker = true
                            }
                        )
                    }
            }
            if (borderStyle == "custom") {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(borderColor ?: colors.primary)
                            .border(1.dp, extra.hairline, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Pick any color",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                        modifier = Modifier.clickable { showBorderPicker = true }
                    )
                }
            }
            if (borderStyle != "none") {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Thickness",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${sliderBorderWidth.roundToInt()} dp",
                        style = MaterialTheme.typography.labelSmall,
                        color = extra.muted
                    )
                }
                Slider(
                    value = sliderBorderWidth,
                    onValueChange = { sliderBorderWidth = it },
                    onValueChangeFinished = {
                        onBorderWidthChange(sliderBorderWidth.roundToInt())
                    },
                    valueRange = 1f..8f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.primary,
                        activeTrackColor = colors.primary,
                        inactiveTrackColor = extra.hairline
                    )
                )
            }
        }

        if (showBorderPicker) {
            AccentColorPickerDialog(
                initialHex = if (borderStyle == "custom") borderColor?.let { colorToHex(it) } else null,
                onDismiss = { showBorderPicker = false },
                onApply = { hex ->
                    onBorderColorChange(hex)
                    showBorderPicker = false
                },
                onResetDefault = { showBorderPicker = false },
                title = "Widget border color",
                subtitle = "Pick any color for the widget outline.",
                showReset = false
            )
        }

        Spacer(Modifier.height(18.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done", fontWeight = FontWeight.Medium)
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = extra.muted)
        }
        Spacer(Modifier.height(8.dp))
    }
}

private data class PreviewEntry(
    val title: String,
    val store: String,
    val price: String,
    val badge: String?
)

private val previewEntries = listOf(
    PreviewEntry("Noise cancelling headphones", "Amazon", "$129.00", "-18%"),
    PreviewEntry("Everyday running shoes", "Nike", "$84.50", "LOW")
)

@Composable
private fun WidgetPreview(
    textSize: String,
    showImages: Boolean,
    opacityPercent: Int,
    borderColor: Color?,
    borderWidthDp: Int
) {
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    val scale = when (textSize) {
        "small" -> 0.88f
        "large" -> 1.18f
        else -> 1f
    }
    val alpha = opacityPercent / 100f
    fun scaled(value: Float): TextUnit = (value * scale).sp

    Column {
        Text(
            text = "PREVIEW",
            style = MaterialTheme.typography.labelSmall,
            color = extra.muted
        )
        Spacer(Modifier.height(8.dp))
        // Faux wallpaper so transparency is visible in the preview.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            extra.sunken,
                            colors.primary.copy(alpha = 0.35f),
                            extra.terracotta.copy(alpha = 0.25f)
                        )
                    )
                )
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.background.copy(alpha = alpha))
                    .then(
                        if (borderColor != null && borderWidthDp > 0) {
                            Modifier.border(
                                width = borderWidthDp.dp,
                                color = borderColor.copy(alpha = alpha),
                                shape = RoundedCornerShape(16.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Wishlisted items",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = scaled(13f),
                        color = colors.onBackground,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "2 saved",
                        fontSize = scaled(10f),
                        color = extra.muted
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(colors.primary)
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "↻",
                            fontSize = scaled(13f),
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimary
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Sync",
                            fontSize = scaled(10f),
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimary
                        )
                    }
                }
                Spacer(Modifier.height(9.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(extra.hairline)
                )
                Spacer(Modifier.height(7.dp))

                previewEntries.forEach { entry ->
                    PreviewRow(
                        entry = entry,
                        showImage = showImages,
                        scale = scale,
                        cardColor = extra.sunken,
                        ink = colors.onBackground,
                        muted = extra.muted,
                        badgeBg = colors.primary.copy(alpha = 0.14f),
                        badgeInk = colors.primary
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun PreviewRow(
    entry: PreviewEntry,
    showImage: Boolean,
    scale: Float,
    cardColor: Color,
    ink: Color,
    muted: Color,
    badgeBg: Color,
    badgeInk: Color
) {
    fun scaled(value: Float): TextUnit = (value * scale).sp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(cardColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showImage) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(muted.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "♡", fontSize = 13.sp, color = muted)
            }
            Spacer(Modifier.width(9.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                fontSize = scaled(12f),
                fontWeight = FontWeight.Medium,
                color = ink,
                maxLines = 1
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = entry.store,
                fontSize = scaled(10f),
                color = muted,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = entry.price,
                fontSize = scaled(12f),
                fontWeight = FontWeight.Bold,
                color = ink,
                maxLines = 1
            )
            entry.badge?.let { badge ->
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = scaled(9f),
                        fontWeight = FontWeight.Bold,
                        color = badgeInk
                    )
                }
            }
        }
    }
}
