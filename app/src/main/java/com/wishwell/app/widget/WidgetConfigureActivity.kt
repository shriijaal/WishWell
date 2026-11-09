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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import com.wishwell.app.ui.theme.LocalWishExtraColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Configuration activity for widgets.
 *
 * Declared as `android:configure` in both widget-info XML files, so Android launches this
 * when the user adds a new widget or long-presses an existing widget and chooses "Widget
 * options" / reconfigures. Receives `AppWidgetManager.EXTRA_APPWIDGET_ID` and returns
 * `RESULT_OK` with that ID so the system completes the add/reconfigure flow.
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
        Spacer(modifier = Modifier.height(24.dp))

        WidgetSettingsEditor(
            widgetKey = appWidgetId.toString(),
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

    val themes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
    val styles = listOf("list" to "List", "card" to "Card", "compact" to "Compact")

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
                color = if (selected) colors.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clickable { settings = settings.copy(theme = key) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) colors.primary else colors.onSurface
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
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        styles.forEach { (key, label) ->
            val selected = settings.style == key
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { settings = settings.copy(style = key) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(if (selected) colors.primary else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier.size(8.dp).background(colors.onPrimary)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(
                color = extra.hairline.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            )
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
            valueRange = 0f..1f
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

    Spacer(modifier = Modifier.height(32.dp))

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
