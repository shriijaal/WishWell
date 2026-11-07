package com.wishwell.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val Color0xF3DFD2 = Color(0xFFF3DFD2)
private val Color0x6B2F14 = Color(0xFF6B2F14)
private val Color0xC9C1AE = Color(0xFFC9C1AE)
private val Color0x9B2C1C = Color(0xFF9B2C1C)
private val Color0x99171410 = Color(0x99171410)
private val Color0xFF2A1408 = Color(0xFF2A1408)
private val Color0xFF3C2418 = Color(0xFF3C2418)
private val Color0xFFF2C8B0 = Color(0xFFF2C8B0)
private val Color0xFFE09A6F = Color(0xFFE09A6F)
private val Color0xFF4B5342 = Color(0xFF4B5342)
private val Color0xFFFFB4A6 = Color(0xFFFFB4A6)
private val Color0xFF561200 = Color(0xFF561200)
private val Color0xCC000000 = Color(0xCC000000)

data class AccentPreset(
    val key: String,
    val label: String,
    val lightPrimary: Color,
    val darkPrimary: Color
)

val AccentPresets: List<AccentPreset> = listOf(
    AccentPreset("green", "Green", Forest, Mint), // default
    AccentPreset("ocean", "Ocean", Color(0xFF1E4B6E), Color(0xFFA8D0F0)),
    AccentPreset("teal", "Teal", Color(0xFF1F5C5B), Color(0xFF9FDAD3)),
    AccentPreset("indigo", "Indigo", Color(0xFF3949AB), Color(0xFFBBC3FF)),
    AccentPreset("plum", "Plum", Color(0xFF6A3D7A), Color(0xFFD9B8E8)),
    AccentPreset("rose", "Rose", Color(0xFF8E3D57), Color(0xFFF2B8C6)),
    AccentPreset("terracotta", "Terracotta", Color(0xFFB3562A), Color(0xFFE09A6F)),
    AccentPreset("gold", "Gold", Color(0xFF7A5A1F), Color(0xFFE6D19A))
)

fun isCustomAccentKey(key: String?): Boolean {
    val v = key?.trim().orEmpty()
    return v.startsWith("#") && parseHexColor(v) != null
}

/** Parses #RGB, #RRGGBB, #AARRGGBB. */
fun parseHexColor(value: String?): Color? {
    val raw = value?.trim().orEmpty()
    if (!raw.startsWith("#")) return null
    return runCatching {
        val argb = android.graphics.Color.parseColor(
            when (raw.length) {
                4 -> "#FF${raw[1]}${raw[1]}${raw[2]}${raw[2]}${raw[3]}${raw[3]}"
                7 -> "#FF${raw.substring(1)}"
                9 -> raw
                else -> return null
            }
        )
        Color(argb)
    }.getOrNull()
}

fun colorToHex(color: Color, includeAlpha: Boolean = false): String {
    val a = (color.alpha * 255).roundToInt().coerceIn(0, 255)
    val r = (color.red * 255).roundToInt().coerceIn(0, 255)
    val g = (color.green * 255).roundToInt().coerceIn(0, 255)
    val b = (color.blue * 255).roundToInt().coerceIn(0, 255)
    return if (includeAlpha) "#%02X%02X%02X%02X".format(a, r, g, b) else "#%02X%02X%02X".format(r, g, b)
}

private fun Color.toHsv(): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    return hsv
}

private fun hsvToColor(h: Float, s: Float, v: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

/** Derive light/dark accent primaries from a single custom color. */
fun customAccentPreset(hexOrKey: String): AccentPreset? {
    val base = parseHexColor(hexOrKey) ?: return null
    val hsv = base.toHsv()
    val light = if (base.luminance() > 0.78f) {
        // keep hue, make it usable on light paper
        hsvToColor(hsv[0], (hsv[1] * 0.85f).coerceAtLeast(0.35f), (hsv[2] * 0.55f).coerceAtLeast(0.22f))
    } else base
    val dark = hsvToColor(
        hsv[0],
        (hsv[1] * 0.55f).coerceIn(0.18f, 0.75f),
        (hsv[2].coerceAtLeast(0.55f) + 0.30f).coerceIn(0.62f, 0.96f)
    )
    return AccentPreset(
        key = colorToHex(base),
        label = "Custom",
        lightPrimary = light,
        darkPrimary = dark
    )
}

fun resolveAccent(key: String?): AccentPreset =
    AccentPresets.firstOrNull { it.key == key }
        ?: customAccentPreset(key.orEmpty())
        ?: AccentPresets.first()

fun accentPreset(key: String?): AccentPreset = resolveAccent(key)

private fun onColorFor(color: Color): Color =
    if (color.luminance() > 0.45f) Color(0xFF1A1C18) else PaperElevated

private fun lightScheme(accent: AccentPreset): ColorScheme {
    val p = accent.lightPrimary
    val onP = onColorFor(p)
    val container = lerp(PaperElevated, p, 0.16f)
    val onContainer = lerp(Ink, p, 0.55f)
    val darkOnP = lerp(p, Ink, 0.72f)
    return lightColorScheme(
        primary = p,
        onPrimary = onP,
        primaryContainer = container,
        onPrimaryContainer = if (onP == PaperElevated) p else darkOnP,
        secondary = Terracotta,
        onSecondary = PaperElevated,
        secondaryContainer = Color0xF3DFD2,
        onSecondaryContainer = Color0x6B2F14,
        tertiary = Moss,
        onTertiary = PaperElevated,
        background = Paper,
        onBackground = Ink,
        surface = PaperElevated,
        onSurface = Ink,
        surfaceVariant = Sand,
        onSurfaceVariant = MutedInk,
        outline = Color0xC9C1AE,
        outlineVariant = Hairline,
        error = Color0x9B2C1C,
        onError = PaperElevated,
        scrim = Color0x99171410
    )
}

private fun darkScheme(accent: AccentPreset): ColorScheme {
    val p = accent.darkPrimary
    val onP = if (p.luminance() > 0.4f) lerp(NightBackground, p, 0.15f).copy(alpha = 1f).let { Color(0xFF182018) } else NightInk
    val onPrimary = if (p.luminance() > 0.45f) Color(0xFF132012) else NightBackground
    val container = lerp(NightSurfaceHigh, p, 0.18f)
    return darkColorScheme(
        primary = p,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = p,
        secondary = Color0xFFE09A6F,
        onSecondary = Color0xFF2A1408,
        secondaryContainer = Color0xFF3C2418,
        onSecondaryContainer = Color0xFFF2C8B0,
        tertiary = Color(0xFFA9C27D),
        onTertiary = Color(0xFF1F2A12),
        background = NightBackground,
        onBackground = NightInk,
        surface = NightSurface,
        onSurface = NightInk,
        surfaceVariant = NightSurfaceHigh,
        onSurfaceVariant = NightMuted,
        outline = Color0xFF4B5342,
        outlineVariant = NightHairline,
        error = Color0xFFFFB4A6,
        onError = Color0xFF561200,
        scrim = Color0xCC000000
    )
}

private val WishShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun WishwellTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentKey: String = "green",
    content: @Composable () -> Unit
) {
    val accent = accentPreset(accentKey)
    val scheme = if (darkTheme) darkScheme(accent) else lightScheme(accent)
    CompositionLocalProvider(
        LocalWishExtraColors provides if (darkTheme) DarkExtraColors else LightExtraColors
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = WishTypography,
            shapes = WishShapes,
            content = content
        )
    }
}


