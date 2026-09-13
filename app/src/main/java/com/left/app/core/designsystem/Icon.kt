package com.left.app.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icon conventions (Master Prompt §7 "Icon conventions"):
 *  - Material Symbols Outlined style everywhere; no filled/outlined mixing.
 *  - Decorative icons use contentDescription = null; meaningful ones get a
 *    localized description (UX/UI Spec §8 accessibility).
 *  - Category glyphs are referenced by stable string keys ([category]) — the
 *    same keys stored on Category.iconKey in the database.
 *  - Unknown keys resolve to a neutral fallback so a bad iconKey can never
 *    crash a screen.
 */
object LeftIcons {

    // App chrome
    val Add: ImageVector get() = Icons.Outlined.Add
    val Back: ImageVector get() = Icons.AutoMirrored.Outlined.ArrowBack
    val Home: ImageVector get() = Icons.Outlined.Home
    val Transactions: ImageVector get() = Icons.AutoMirrored.Outlined.ReceiptLong
    val Analytics: ImageVector get() = Icons.Outlined.BarChart
    val Settings: ImageVector get() = Icons.Outlined.Settings

    /** Maps a Category.iconKey to a glyph; unknown keys fall back to [Category]. */
    fun category(iconKey: String): ImageVector = when (iconKey) {
        "restaurant" -> Icons.Outlined.Restaurant
        "directions_car" -> Icons.Outlined.DirectionsCar
        "shopping_bag" -> Icons.Outlined.ShoppingBag
        "receipt_long" -> Icons.AutoMirrored.Outlined.ReceiptLong
        "movie" -> Icons.Outlined.Movie
        "health_and_safety" -> Icons.Outlined.HealthAndSafety
        "school" -> Icons.Outlined.School
        "flight" -> Icons.Outlined.Flight
        "person" -> Icons.Outlined.Person
        else -> Icons.Outlined.Category // includes "other" and unknown keys
    }
}
