package com.left.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.left.app.core.designsystem.DarkColorScheme
import com.left.app.core.designsystem.DarkExtendedColors
import com.left.app.core.designsystem.ExtendedColors
import com.left.app.core.designsystem.LeftElevation
import com.left.app.core.designsystem.LeftShapes
import com.left.app.core.designsystem.LeftTypography
import com.left.app.core.designsystem.LightColorScheme
import com.left.app.core.designsystem.LightExtendedColors
import com.left.app.core.designsystem.LocalElevation
import com.left.app.core.designsystem.LocalExtendedColors
import com.left.app.core.designsystem.LocalSpacing
import com.left.app.core.designsystem.Spacing

/**
 * The single theme entry point. Screens never construct their own colors,
 * text styles, or spacing values (Master Prompt §7: no hardcoded styling
 * scattered across screens).
 *
 * Dynamic color is intentionally OFF: Left ships its own calm, brand-stable
 * palette rather than adopting wallpaper colors.
 */
@Composable
fun LeftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalElevation provides LeftElevation(),
        LocalExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = LeftTypography,
            shapes = LeftShapes,
            content = content,
        )
    }
}

/** Token accessors: LeftTheme.spacing.md, LeftTheme.extendedColors.income, ... */
object LeftTheme {
    val spacing: Spacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current

    val elevation: LeftElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalElevation.current

    val extendedColors: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}
