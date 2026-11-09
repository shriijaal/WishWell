package com.wishwell.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
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
            onSave = onFinished
        )
    }
}

@Composable
private fun WidgetSettingsEditor(
    widgetKey: String,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val extra = LocalWishExtraColors.current
    val colors = MaterialTheme.colorScheme
    var settings by remember { mutableStateOf(WidgetSettingsStore.load(context, widgetKey)) }

    // ── Theme ──
    SectionLabel("Theme")
    Spacer(modifier = Modifier.height(10.dp))
    SettingsCard {
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
                    onClick = { settings = settings.copy(theme = key) }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // ── Style ──
    SectionLabel("Style")
    Spacer(modifier = Modifier.height(10.dp))
    SettingsCard {
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
                    onClick = { settings = settings.copy(style = key) }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // ── Transparency ──
    SectionLabel("Transparency")
    Spacer(modifier = Modifier.height(10.dp))
    SettingsCard {
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
                onValueChange = { settings = settings.copy(transparency = (it * 100).roundToInt()) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = extra.hairline,
                    thumbColor = colors.primary
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // ── Blur ──
    SectionLabel("Blur background")
    Spacer(modifier = Modifier.height(10.dp))
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = settings.blur,
                onCheckedChange = { settings = settings.copy(blur = it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimary,
                    checkedTrackColor = colors.primary,
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
                color = colors.primary
            )
        }
    }
}
