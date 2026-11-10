package com.wishwell.app.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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

    val themes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
    val styles = listOf("list" to "List", "card" to "Card", "compact" to "Compact")

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
            themes.forEach { (key, label) ->
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
            styles.forEach { (key, label) ->
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

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = {
                    WidgetSettingsStore.save(context, widgetKey, settings)
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
