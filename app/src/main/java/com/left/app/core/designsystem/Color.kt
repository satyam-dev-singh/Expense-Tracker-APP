package com.left.app.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Left color tokens — an original palette built around deep spruce green
 * (calm, financially trustworthy) on warm neutral surfaces. No gradients, no
 * loud accents (UX/UI Spec §1).
 */

// --- Raw palette (internal; UI references the schemes/tokens below) ---
internal val Spruce700 = Color(0xFF0E5B4A)
internal val Spruce900 = Color(0xFF06231B)
internal val Spruce100 = Color(0xFFC7E9DC)
internal val Spruce300 = Color(0xFF9AD6C0)
internal val Spruce800 = Color(0xFF144A3D)

internal val Sage500 = Color(0xFF4E635C)
internal val Sage100 = Color(0xFFD0E5DC)
internal val Sage900 = Color(0xFF12211C)
internal val Sage300 = Color(0xFFA9C4B8)
internal val Sage800 = Color(0xFF2E443B)

internal val Clay500 = Color(0xFF6C5A45)
internal val Clay100 = Color(0xFFE9DCCB)
internal val Clay900 = Color(0xFF241A10)
internal val Clay300 = Color(0xFFD4BC9E)
internal val Clay800 = Color(0xFF4A3A28)

internal val Error600 = Color(0xFFB3261E)
internal val Error100 = Color(0xFFF9DEDC)
internal val Error900 = Color(0xFF410E0B)
internal val Error300 = Color(0xFFF2B8B5)
internal val Error800 = Color(0xFF8C1D18)

internal val Neutral50 = Color(0xFFFAFDFA)
internal val Neutral100 = Color(0xFFEFF4F1)
internal val Neutral200 = Color(0xFFDCE5DF)
internal val Neutral400 = Color(0xFF8A938C)
internal val Neutral600 = Color(0xFF404944)
internal val Neutral700 = Color(0xFF2A322D)
internal val Neutral900 = Color(0xFF171D1A)
internal val Neutral950 = Color(0xFF0F1512)
internal val White = Color(0xFFFFFFFF)

val LightColorScheme = lightColorScheme(
    primary = Spruce700,
    onPrimary = White,
    primaryContainer = Spruce100,
    onPrimaryContainer = Spruce900,
    secondary = Sage500,
    onSecondary = White,
    secondaryContainer = Sage100,
    onSecondaryContainer = Sage900,
    tertiary = Clay500,
    onTertiary = White,
    tertiaryContainer = Clay100,
    onTertiaryContainer = Clay900,
    error = Error600,
    onError = White,
    errorContainer = Error100,
    onErrorContainer = Error900,
    background = Neutral50,
    onBackground = Neutral900,
    surface = Neutral50,
    onSurface = Neutral900,
    surfaceVariant = Neutral200,
    onSurfaceVariant = Neutral600,
    outline = Neutral400,
    outlineVariant = Neutral200,
)

val DarkColorScheme = darkColorScheme(
    primary = Spruce300,
    onPrimary = Spruce900,
    primaryContainer = Spruce800,
    onPrimaryContainer = Spruce100,
    secondary = Sage300,
    onSecondary = Sage900,
    secondaryContainer = Sage800,
    onSecondaryContainer = Sage100,
    tertiary = Clay300,
    onTertiary = Clay900,
    tertiaryContainer = Clay800,
    onTertiaryContainer = Clay100,
    error = Error300,
    onError = Error900,
    errorContainer = Error800,
    onErrorContainer = Error100,
    background = Neutral950,
    onBackground = Neutral200,
    surface = Neutral950,
    onSurface = Neutral200,
    surfaceVariant = Neutral600,
    onSurfaceVariant = Neutral200,
    outline = Neutral400,
    outlineVariant = Neutral700,
)

/**
 * Left-specific semantic colors that MaterialTheme does not model.
 * Income/positive amounts use [income]; expenses stay in plain on-surface
 * color (calm, no alarming red for everyday spending). Never rely on color
 * alone (UX/UI Spec §8) — pair with sign and label.
 */
data class ExtendedColors(
    val income: Color,
    val warning: Color,
)

internal val LightExtendedColors = ExtendedColors(
    income = Color(0xFF2E7D57),
    warning = Color(0xFF8C6D1F),
)

internal val DarkExtendedColors = ExtendedColors(
    income = Color(0xFF8FD6AE),
    warning = Color(0xFFE5C05C),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
