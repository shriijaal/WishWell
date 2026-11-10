package com.wishwell.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wishwell.app.ui.components.SectionLabel
import com.wishwell.app.ui.components.SettingsCard
import com.wishwell.app.ui.components.WishPill
import com.wishwell.app.ui.theme.LocalWishExtraColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Configuration activity for widgets.
 *
 * Declared as `android:configure` in the widget-info XML, so Android launches this
 * when the user adds a new widget or long-presses an existing widget and chooses
 * "Widget options" / reconfigures. Receives `AppWidgetManager.EXTRA_APPWIDGET_ID`
 * and returns `RESULT_OK` with that ID so the system completes the add/reconfigure flow.
 */
class WidgetConfigureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        setContent {
            WidgetConfigureScreen(
                appWidgetId = appWidgetId,
                onFinished = {
                    val resultIntent = Intent().putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId
                    )
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
            )
        }
    }

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context, WidgetConfigureActivity::class.java)
    }
}

@Composable
private fun WidgetConfigureScreen(
    appWidgetId: Int,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme
    var settings by remember { mutableStateOf(WidgetSettingsStore.load(context, "default")) }
    val accent = WidgetSettingsStore.parseAccentColor(settings.accentColor) ?: colors.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(accent.copy(alpha = 0.12f))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Configure Widget",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Customise the appearance of this widget. Tap Save when done.",
            style = MaterialTheme.typography.bodyMedium,
            color = extra.muted
        )
        Spacer(modifier = Modifier.height(18.dp))

        WidgetSettingsEditor(
            widgetKey = "default",
            settings = settings,
            onSettingsChange = { settings = it },
            onSave = onFinished
        )
    }
}

@Composable
private fun WidgetSettingsEditor(
    widgetKey: String,
    settings: WidgetSettingsStore.WidgetSettings,
    onSettingsChange: (WidgetSettingsStore.WidgetSettings) -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme
    val accent = WidgetSettingsStore.parseAccentColor(settings.accentColor) ?: colors.primary

    // ── Theme ──
    SectionLabel("Theme")
    Spacer(modifier = Modifier.height(10.dp))
    SettingsCard(accentColor = accent) {
        Text(
            text = "Choose how the widget looks. System follows your device's light/dark setting.",
            style = MaterialTheme.typography.bodySmall,
            color = extra.muted
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "system" to "System",
                "light" to "Light",
                "dark" to "Dark"
            ).forEach { (key, label) ->
                WishPill(
                    text = label,
                    selected = settings.theme == key,
                    onClick = { onSettingsChange(settings.copy(theme = key)) },
                    accentColor = accent
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // ── Style ──
    SectionLabel("Style")
    Spacer(modifier = Modifier.height(10.dp))
    SettingsCard(accentColor = accent) {
        Text(
            text = "Choose how items are laid out in the widget.",
            style = MaterialTheme.typography.bodySmall,
            color = extra.muted
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "list" to "List",
                "card" to "Card",
                "compact" to "Compact"
            ).forEach { (key, label) ->
                WishPill(
                    text = label,
                    selected = settings.style == key,
                    onClick = { onSettingsChange(settings.copy(style = key)) },
                    accentColor = accent
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // ── Content ──
    SectionLabel("Content")
    Spacer(modifier = Modifier.height(10.dp))
    SettingsCard(accentColor = accent) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Item count limit
            Text(
                text = "Max items",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(3, 5, 8, 10).forEach { count ->
                    val selected = settings.limit == count
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(if (selected) accent else Color.Transparent, shape = CircleShape)
                            .clickable { onSettingsChange(settings.copy(limit = count)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) Color.White else colors.onSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Sort order
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "recent" to "Recent",
                    "name" to "Name",
                    "price" to "Price"
                ).forEach { (key, label) ->
                    WishPill(
                        text = label,
                        selected = settings.sortOrder == key,
                        onClick = { onSettingsChange(settings.copy(sortOrder = key)) },
                        accentColor = accent
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Toggles row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ToggleOption(
                    checked = settings.showPrice,
                    label = "Show price",
                    onChange = { onSettingsChange(settings.copy(showPrice = it)) },
                    colors = colors,
                    extra = extra,
                    accentColor = accent
                )
                ToggleOption(
                    checked = settings.showStore,
                    label = "Show store",
                    onChange = { onSettingsChange(settings.copy(showStore = it)) },
                    colors = colors,
                    extra = extra,
                    accentColor = accent
                )
                ToggleOption(
                    checked = settings.dropsOnly,
                    label = "Deals only",
                    onChange = { onSettingsChange(settings.copy(dropsOnly = it)) },
                    colors = colors,
                    extra = extra,
                    accentColor = accent
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // ── Appearance ──
    SectionLabel("Appearance")
    Spacer(modifier = Modifier.height(10.dp))
    SettingsCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Accent color picker
            Text(
                text = "Accent color",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val accentSwatches = listOf(
                    null to "Auto",
                    "#1F3D2E" to "Forest",
                    "#B3562A" to "Terracotta",
                    "#5B7141" to "Moss",
                    "#DDF06B" to "Lime"
                )
                accentSwatches.forEach { (hex, label) ->
                    val selected = settings.accentColor == hex
                    val swatchColor = WidgetSettingsStore.parseAccentColor(hex) ?: colors.primary
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(if (selected) Color.White else Color.Transparent, shape = CircleShape)
                            .clickable { onSettingsChange(settings.copy(accentColor = hex)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (selected) 20.dp else 24.dp)
                                .background(swatchColor, shape = CircleShape)
                        )
                    }
                    if (accentSwatches.last() != accentSwatches.find { it.first == hex }) {
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Font size
            Text(
                text = "Text size",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${(settings.fontSize * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = extra.muted,
                    modifier = Modifier.width(40.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = settings.fontSize,
                    onValueChange = { onSettingsChange(settings.copy(fontSize = it)) },
                    valueRange = 0.75f..1.5f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = accent,
                        inactiveTrackColor = extra.hairline,
                        thumbColor = accent
                    )
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // ── Transparency ──
    SectionLabel("Transparency")
    Spacer(modifier = Modifier.height(10.dp))
    SettingsCard(accentColor = accent) {
        Text(
            text = "Lower = more transparent (background shows through).",
            style = MaterialTheme.typography.bodySmall,
            color = extra.muted
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${settings.transparency}%",
                style = MaterialTheme.typography.bodyMedium,
                color = extra.muted,
                modifier = Modifier.width(40.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Slider(
                value = settings.transparency / 100f,
                onValueChange = { onSettingsChange(settings.copy(transparency = (it * 100).roundToInt())) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    activeTrackColor = accent,
                    inactiveTrackColor = extra.hairline,
                    thumbColor = accent
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // ── Blur ──
    SectionLabel("Blur background")
    Spacer(modifier = Modifier.height(10.dp))
    SettingsCard(accentColor = accent) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = settings.blur,
                onCheckedChange = { onSettingsChange(settings.copy(blur = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimary,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = extra.muted,
                    uncheckedTrackColor = extra.hairline
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Blur background",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )
                Text(
                    text = "Applies a blur effect behind the widget content",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.muted
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = {
                WidgetSettingsStore.save(context, widgetKey, settings)
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { WishwellWidgetUpdater.updateAll(context) }
                }
                onSave()
            }
        ) {
            Text(
                text = "Save",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
        }
    }
}

/**
 * Compact toggle row: pill-style checkbox with label.
 */
@Composable
private fun ToggleOption(
    checked: Boolean,
    label: String,
    onChange: (Boolean) -> Unit,
    colors: androidx.compose.material3.ColorScheme,
    extra: com.wishwell.app.ui.theme.WishExtraColors,
    accentColor: Color
) {
    Row(
        modifier = Modifier.clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (checked) accentColor else Color.Transparent,
            border = BorderStroke(1.dp, if (checked) accentColor else extra.hairline),
            modifier = Modifier.size(20.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (checked) {
                    Box(
                        modifier = Modifier.size(8.dp).background(Color.White)
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (checked) accentColor else colors.onSurface
        )
    }
}
