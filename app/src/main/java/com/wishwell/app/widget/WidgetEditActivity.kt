package com.wishwell.app.widget

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import com.wishwell.app.ui.components.WishPill
import com.wishwell.app.ui.theme.LocalWishExtraColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class WidgetEditActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetKey = intent.getStringExtra("widget_id") ?: "default"
        setContent {
            WidgetEditScreen(widgetKey = widgetKey) {
                finish()
            }
        }
    }
}

@Composable
private fun WidgetEditScreen(
    widgetKey: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme
    var settings by remember { mutableStateOf(WidgetSettingsStore.load(context, widgetKey)) }
    val accent = WidgetSettingsStore.parseAccentColor(settings.accentColor) ?: colors.primary
    var showingExport by remember { mutableStateOf(false) }
    var exportJson by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var importResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(accent.copy(alpha = 0.12f))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Edit Widget",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Customize the appearance of this widget.",
            style = MaterialTheme.typography.bodyMedium,
            color = extra.muted
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Theme
        Text(
            text = "Theme",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "system" to "System",
                "light" to "Light",
                "dark" to "Dark"
            ).forEach { (key, label) ->
                val selected = settings.theme == key
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) accent else extra.hairline
                    ),
                    modifier = Modifier
                        .clickable { settings = settings.copy(theme = key) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) accent else colors.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Style
        Text(
            text = "Style",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "list" to "List",
                "card" to "Card",
                "compact" to "Compact"
            ).forEach { (key, label) ->
                val selected = settings.style == key
                WishPill(
                    text = label,
                    selected = selected,
                    onClick = { settings = settings.copy(style = key) },
                    accentColor = accent
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Widget size
        Text(
            text = "Widget size",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "small" to "Small",
                "medium" to "Medium",
                "large" to "Large"
            ).forEach { (key, label) ->
                val selected = settings.widgetSize == key
                val entryCount = when (key) {
                    "small" -> 3
                    "large" -> 12
                    else -> 8
                }
                WishPill(
                    text = "$label ($entryCount)",
                    selected = selected,
                    onClick = { settings = settings.copy(widgetSize = key) },
                    accentColor = accent
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Content
        Text(
            text = "Content",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = accent.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, extra.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Max items
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
                                .clickable { settings = settings.copy(limit = count) },
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

                // Sort by
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
                            onClick = { settings = settings.copy(sortOrder = key) },
                            accentColor = accent
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ToggleOption(
                        checked = settings.showPrice,
                        label = "Show price",
                        onChange = { settings = settings.copy(showPrice = it) },
                        colors = colors,
                        extra = extra,
                        accentColor = accent
                    )
                    ToggleOption(
                        checked = settings.showStore,
                        label = "Show store",
                        onChange = { settings = settings.copy(showStore = it) },
                        colors = colors,
                        extra = extra,
                        accentColor = accent
                    )
                    ToggleOption(
                        checked = settings.dropsOnly,
                        label = "Deals only",
                        onChange = { settings = settings.copy(dropsOnly = it) },
                        colors = colors,
                        extra = extra,
                        accentColor = accent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Appearance
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = accent.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, extra.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                    accentSwatches.forEachIndexed { index, (hex, label) ->
                        val selected = settings.accentColor == hex
                        val swatchColor = WidgetSettingsStore.parseAccentColor(hex) ?: colors.primary
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(if (selected) Color.White else Color.Transparent, shape = CircleShape)
                                .clickable { settings = settings.copy(accentColor = hex) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 20.dp else 24.dp)
                                    .background(swatchColor, shape = CircleShape)
                            )
                        }
                        if (index < accentSwatches.lastIndex) {
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Text size
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
                        onValueChange = { settings = settings.copy(fontSize = it) },
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

        Spacer(modifier = Modifier.height(24.dp))

        // Transparency
        Text(
            text = "Transparency",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
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
                onValueChange = { settings = settings.copy(transparency = (it * 100).roundToInt()) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    activeTrackColor = accent,
                    inactiveTrackColor = extra.hairline,
                    thumbColor = accent
                )
            )
        }
        Text(
            text = "Lower = more transparent (background shows through)",
            style = MaterialTheme.typography.labelSmall,
            color = extra.muted
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Blur
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = settings.blur,
                onCheckedChange = { settings = settings.copy(blur = it) },
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

        Spacer(modifier = Modifier.height(24.dp))

        // Import / Export
        Text(
            text = "Import / Export",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = accent.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, extra.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            exportJson = WidgetSettingsStore.exportToJson(settings)
                            showingExport = true
                        }
                    ) {
                        Text(text = "Export", style = MaterialTheme.typography.labelLarge, color = accent)
                    }
                    TextButton(
                        onClick = { importText = ""; importResult = null; showingExport = false }
                    ) {
                        Text(text = "Import…", style = MaterialTheme.typography.labelLarge, color = accent)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Export saves your widget settings as JSON you can paste into another widget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.muted
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Reset
        Text(
            text = "Reset",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = accent.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, extra.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val defaults = WidgetSettingsStore.defaultSettings()
                            settings = defaults
                            WidgetSettingsStore.save(context, widgetKey, defaults)
                            CoroutineScope(Dispatchers.IO).launch {
                                runCatching { WishwellWidgetUpdater.updateAll(context) }
                            }
                        }
                    ) {
                        Text(text = "Reset to defaults", style = MaterialTheme.typography.labelLarge, color = accent)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Restores all settings to their factory values.",
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.muted
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Live preview
        Text(
            text = "Live preview",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        WidgetPreview(
            context = context,
            settings = settings,
            accent = accent,
            colors = colors,
            extra = extra
        )

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
                    onClose()
                }
            ) {
                Text(
                    text = "Save",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = accent
                )
            }
        }

        // Export dialog
        if (showingExport) {
            ExportImportDialog(
                show = true,
                mode = "export",
                json = exportJson,
                onDismiss = { showingExport = false }
            )
        }
        // Import dialog
        if (!showingExport && importText.isNotEmpty()) {
            ExportImportDialog(
                show = true,
                mode = "import",
                json = importText,
                result = importResult,
                onDismiss = { importText = ""; importResult = null },
                onApply = { text ->
                    val imported = WidgetSettingsStore.importFromJson(text)
                    settings = imported
                    WidgetSettingsStore.save(context, widgetKey, imported)
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching { WishwellWidgetUpdater.updateAll(context) }
                    }
                    importResult = "Imported"
                    importText = ""
                }
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

/**
 * Mini live preview of the widget with current settings applied.
 */
@Composable
private fun WidgetPreview(
    context: Context,
    settings: WidgetSettingsStore.WidgetSettings,
    accent: Color,
    colors: androidx.compose.material3.ColorScheme,
    extra: com.wishwell.app.ui.theme.WishExtraColors
) {
    val previewEntries = listOf(
        WidgetEntry(1, "Wireless Headphones", "TechStore", "$99.99", "NEW", false),
        WidgetEntry(2, "Coffee Maker", "Kitchen Co.", "$45.00", "-20%", true),
        WidgetEntry(3, "Running Shoes", "SportZone", "$120.00", null, false)
    )

    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, extra.hairline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Widget preview frame
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (settings.blur) Color.LightGray else Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Wishlisted items",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = colors.onSurface,
                            fontSize = (16.sp * settings.fontSize.coerceIn(0.75f, 1.5f)),
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${previewEntries.size} saved",
                        style = MaterialTheme.typography.bodySmall.copy(color = extra.muted),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    previewEntries.take(when (settings.widgetSize) {
                        "small" -> 2
                        "large" -> 12
                        else -> 3
                    }).forEachIndexed { index, entry ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = accent.copy(alpha = 0.08f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = entry.title.take(28),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = colors.onSurface,
                                        fontSize = (14f * settings.fontSize.coerceIn(0.75f, 1.5f)).sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    maxLines = 1
                                )
                                if (settings.showStore) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = entry.storeName,
                                        style = MaterialTheme.typography.bodySmall.copy(color = extra.muted),
                                        maxLines = 1
                                    )
                                }
                                if (settings.showPrice) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = entry.priceText,
                                        style = MaterialTheme.typography.bodySmall.copy(color = extra.muted),
                                        maxLines = 1
                                    )
                                }
                                entry.badge?.let { badge ->
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = accent,
                                        modifier = Modifier
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = badge,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        if (index < 2) Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Preview shows how your widget will look on the home screen.",
                style = MaterialTheme.typography.bodySmall,
                color = extra.muted
            )
        }
    }
}

/**
 * Simple dialog for export/import of widget settings JSON.
 */
@Composable
private fun ExportImportDialog(
    show: Boolean,
    mode: String,
    json: String,
    result: String? = null,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit = {}
) {
    if (!show) return
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val extra = LocalWishExtraColors.current
    var editText by remember { mutableStateOf(json) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Dim background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x80000000))
                .clickable(onClick = onDismiss)
        ) { }

        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = colors.surface,
            modifier = Modifier
                .padding(32.dp)
                .width(300.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (mode == "export") "Export settings" else "Import settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (mode == "export") {
                    Text(
                        text = "Copy this JSON and paste it into another widget's import:",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.muted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF5F5F5))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = json,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(text = "Done", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        }
                    }
                } else {
                    Text(
                        text = "Paste widget settings JSON here:",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.muted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color(0xFFF5F5F5))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = editText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    if (result != null) {
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(text = "Cancel", style = MaterialTheme.typography.labelLarge, color = extra.muted)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { onApply(editText) }) {
                            Text(text = "Apply", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        }
                    }
                }
            }
        }
    }
}
