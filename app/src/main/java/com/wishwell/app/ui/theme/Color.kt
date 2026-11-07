package com.wishwell.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Light
val Paper = Color(0xFFF4F1E8)
val PaperElevated = Color(0xFFFFFCF4)
val Ink = Color(0xFF171A14)
val Forest = Color(0xFF1F3D2E)
val Moss = Color(0xFF5B7141)
val Lime = Color(0xFFDDF06B)
val Terracotta = Color(0xFFB3562A)
val Hairline = Color(0xFFE3DCCC)
val MutedInk = Color(0xFF63675A)
val Sand = Color(0xFFEDE7D7)

// Dark
val NightBackground = Color(0xFF11140F)
val NightSurface = Color(0xFF191D16)
val NightSurfaceHigh = Color(0xFF22271D)
val NightInk = Color(0xFFF1EEE2)
val NightMuted = Color(0xFFA9AF9D)
val NightHairline = Color(0xFF333A2C)
val Mint = Color(0xFFCFE39C)

@Immutable
data class WishExtraColors(
    val lime: Color,
    val limeInk: Color,
    val hairline: Color,
    val muted: Color,
    val sunken: Color,
    val moss: Color,
    val terracotta: Color,
    val isDark: Boolean
)

val LightExtraColors = WishExtraColors(
    lime = Lime,
    limeInk = Ink,
    hairline = Hairline,
    muted = MutedInk,
    sunken = Sand,
    moss = Moss,
    terracotta = Terracotta,
    isDark = false
)

val DarkExtraColors = WishExtraColors(
    lime = Mint,
    limeInk = NightBackground,
    hairline = NightHairline,
    muted = NightMuted,
    sunken = NightSurfaceHigh,
    moss = Color(0xFFA9C27D),
    terracotta = Color(0xFFE09A6F),
    isDark = true
)

val LocalWishExtraColors = staticCompositionLocalOf { LightExtraColors }
