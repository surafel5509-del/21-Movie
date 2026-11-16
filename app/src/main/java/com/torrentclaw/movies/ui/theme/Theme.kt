package com.torrentclaw.movies.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.torrentclaw.movies.domain.model.ThemeMode

/** Extra semantic colours Material 3 doesn't model (swarm health, rating badges). */
data class ExtendedColors(
    val swarmStrong: Color,
    val swarmFair: Color,
    val swarmWeak: Color,
    val swarmDead: Color,
    val ratingBadge: Color,
    val onRatingBadge: Color,
    val qualityBadge: Color,
    val onQualityBadge: Color,
    val heroScrim: Color,
    val isDark: Boolean,
)

private val DarkExtended = ExtendedColors(
    swarmStrong = SwarmGreen,
    swarmFair = SwarmAmber,
    swarmWeak = SwarmRed,
    swarmDead = CreamFaint,
    ratingBadge = Gold,
    onRatingBadge = OnGold,
    qualityBadge = Color(0xFF262633),
    onQualityBadge = Cream,
    heroScrim = Ink.copy(alpha = 0.88f),
    isDark = true,
)

private val LightExtended = ExtendedColors(
    swarmStrong = Color(0xFF128A4B),
    swarmFair = Color(0xFFB46900),
    swarmWeak = Color(0xFFB3261E),
    swarmDead = InkOnPaperMuted,
    ratingBadge = GoldDeep,
    onRatingBadge = Color.White,
    qualityBadge = PaperOutline,
    onQualityBadge = InkOnPaper,
    heroScrim = Color.Black.copy(alpha = 0.70f),
    isDark = false,
)

val LocalExtendedColors = staticCompositionLocalOf { DarkExtended }

private val DarkScheme = darkColorScheme(
    primary = Gold,
    onPrimary = OnGold,
    primaryContainer = GoldDim,
    onPrimaryContainer = Cream,
    secondary = Teal,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF123B36),
    onSecondaryContainer = Color(0xFFB8F5EC),
    tertiary = Violet,
    onTertiary = Ink,
    background = Ink,
    onBackground = Cream,
    surface = Ink,
    onSurface = Cream,
    surfaceVariant = InkElevated,
    onSurfaceVariant = CreamMuted,
    surfaceContainerLowest = Color(0xFF07070B),
    surfaceContainerLow = InkRaised,
    surfaceContainer = InkElevated,
    surfaceContainerHigh = Color(0xFF22222E),
    surfaceContainerHighest = Color(0xFF2A2A38),
    surfaceTint = Gold,
    outline = InkOutline,
    outlineVariant = InkOutlineStrong,
    error = SwarmRed,
    onError = Color(0xFF2A0505),
    errorContainer = Color(0xFF3E1010),
    onErrorContainer = Color(0xFFFFD9D6),
    inverseSurface = Cream,
    inverseOnSurface = Ink,
    inversePrimary = GoldDim,
    scrim = Color.Black,
)

private val LightScheme = lightColorScheme(
    primary = GoldDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE7A8),
    onPrimaryContainer = Color(0xFF2A1D00),
    secondary = Color(0xFF00695F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB8F5EC),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF5B44B8),
    onTertiary = Color.White,
    background = PaperLight,
    onBackground = InkOnPaper,
    surface = PaperLight,
    onSurface = InkOnPaper,
    surfaceVariant = PaperRaised,
    onSurfaceVariant = InkOnPaperMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = PaperRaised,
    surfaceContainer = Color(0xFFF2EDE4),
    surfaceContainerHigh = Color(0xFFEBE5DA),
    surfaceContainerHighest = Color(0xFFE3DCD0),
    surfaceTint = GoldDeep,
    outline = PaperOutline,
    outlineVariant = Color(0xFFD3CBBC),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = InkOnPaper,
    inverseOnSurface = PaperLight,
    inversePrimary = Gold,
    scrim = Color.Black,
)

@Composable
fun TorrentClawTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && supportsDynamic && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    val extended = if (darkTheme) DarkExtended else LightExtended

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            // Dark app → light icons; light app → dark icons.
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TorrentClawTypography,
            content = content,
        )
    }
}

/** Accessor for the extended palette. */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current
