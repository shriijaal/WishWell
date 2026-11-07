package com.wishwell.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wishwell.app.ui.theme.colorToHex
import com.wishwell.app.ui.theme.parseHexColor
import kotlin.math.roundToInt

@Composable
fun AccentColorPickerDialog(
    initialHex: String?,
    onDismiss: () -> Unit,
    onApply: (hex: String) -> Unit,
    onResetDefault: () -> Unit
) {
    val init = parseHexColor(initialHex) ?: Color(0xFF1F3D2E)
    val initHsv = remember(initialHex) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(init.toArgbCompose(), it) }
    }
    var hue by remember(initialHex) { mutableFloatStateOf(initHsv[0]) }
    var sat by remember(initialHex) { mutableFloatStateOf(initHsv[1]) }
    var value by remember(initialHex) { mutableFloatStateOf(initHsv[2]) }
    var hexInput by remember(initialHex) { mutableStateOf(colorToHex(init).removePrefix("#")) }

    val selected = hsvCompose(hue, sat, value)
    val hueColor = hsvCompose(hue, 1f, 1f)
    val colors = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colors.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Custom accent color",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Pick a color (spectrum + hue). Applied as the app highlight.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))

                SvPalette(
                    hue = hue,
                    sat = sat,
                    value = value,
                    hueColor = hueColor,
                    onChange = { s, v ->
                        sat = s; value = v
                        hexInput = colorToHex(hsvCompose(hue, s, v)).removePrefix("#")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                )
                Spacer(Modifier.height(12.dp))
                HueSlider(
                    hue = hue,
                    onChange = { h ->
                        hue = h
                        hexInput = colorToHex(hsvCompose(h, sat, value)).removePrefix("#")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                )

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(selected)
                            .border(1.dp, colors.outlineVariant, CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { raw ->
                            val cleaned = raw.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.take(6)
                            hexInput = cleaned
                            if (cleaned.length == 6) {
                                val c = parseHexColor("#$cleaned")
                                if (c != null) {
                                    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgbCompose(), it) }
                                    hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Hex") },
                        prefix = { Text("#") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onResetDefault) { Text("Default (Green)") }
                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = { onApply(colorToHex(selected)) }) {
                            Text("Apply", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SvPalette(
    hue: Float,
    sat: Float,
    value: Float,
    hueColor: Color,
    onChange: (sat: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    fun update(pos: Offset) {
        if (size.width <= 0 || size.height <= 0) return
        val s = (pos.x / size.width).coerceIn(0f, 1f)
        val v = 1f - (pos.y / size.height).coerceIn(0f, 1f)
        onChange(s, v)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures { update(it) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { update(it) },
                    onDrag = { change, _ -> update(change.position) }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // base hue
            drawRect(color = hueColor)
            // saturation: white -> transparent
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
            // value: transparent -> black
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            // indicator
            val x = sat * this.size.width
            val y = (1f - value) * this.size.height
            val indicatorColor = if (value > 0.55f) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.9f)
            drawCircle(color = Color.White, radius = 8.dp.toPx(), center = Offset(x, y), style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = indicatorColor, radius = 6.dp.toPx(), center = Offset(x, y), style = Stroke(width = 1.5.dp.toPx()))
        }
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val hueColors = remember {
        listOf(
            Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
            Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
        )
    }
    fun update(pos: Offset) {
        if (size.width <= 0) return
        onChange(((pos.x / size.width).coerceIn(0f, 1f)) * 360f)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .onSizeChanged { size = it }
            .pointerInput(Unit) { detectTapGestures { update(it) } }
            .pointerInput(Unit) { detectDragGestures(onDragStart = { update(it) }, onDrag = { change, _ -> update(change.position) }) }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(brush = Brush.horizontalGradient(hueColors))
            val x = (hue / 360f).coerceIn(0f, 1f) * this.size.width
            drawCircle(color = Color.White, radius = this.size.height * 0.55f, center = Offset(x, this.size.height / 2f), style = Stroke(width = 2.dp.toPx()))
            drawCircle(color = Color.Black.copy(alpha = 0.25f), radius = this.size.height * 0.38f, center = Offset(x, this.size.height / 2f))
        }
    }
}

private fun hsvCompose(h: Float, s: Float, v: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

private fun Color.toArgbCompose(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt()
)
