package dev.spectra.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Spectra's one theme colour is orange, as on iOS, and it is the scheme's
 * `primary`: buttons, switches, selection, links and decorative icons read it
 * on their own. A view writes `MaterialTheme.colorScheme.primary`, never a
 * literal orange, so the colour changes in one place. There is no dynamic
 * colour: the app's accent is its own, not the wallpaper's.
 *
 * The schemes are Material 3 tonal palettes around that orange, with primary
 * kept bright enough to read as the brand while white on it stays legible.
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFFB35900),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFF725A42),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCBE),
    onSecondaryContainer = Color(0xFF291806),
    tertiary = Color(0xFF58633A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE8B4),
    onTertiaryContainer = Color(0xFF161E01),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF211A14),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF211A14),
    surfaceVariant = Color(0xFFF2DFD1),
    onSurfaceVariant = Color(0xFF51443A),
    outline = Color(0xFF837469),
    outlineVariant = Color(0xFFD5C3B5),
    inverseSurface = Color(0xFF372F28),
    inverseOnSurface = Color(0xFFFCEEE3),
    inversePrimary = Color(0xFFFFB870),
    surfaceDim = Color(0xFFE6D7CD),
    surfaceBright = Color(0xFFFFF8F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1E8),
    surfaceContainer = Color(0xFFFAEBE0),
    surfaceContainerHigh = Color(0xFFF4E6DB),
    surfaceContainerHighest = Color(0xFFEEE0D5),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB870),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF693C00),
    onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFE1C1A4),
    onSecondary = Color(0xFF402C18),
    secondaryContainer = Color(0xFF59422C),
    onSecondaryContainer = Color(0xFFFFDCBE),
    tertiary = Color(0xFFC0CC9A),
    onTertiary = Color(0xFF2A3410),
    tertiaryContainer = Color(0xFF414B24),
    onTertiaryContainer = Color(0xFFDCE8B4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF14110F),
    onBackground = Color(0xFFEEE0D5),
    surface = Color(0xFF14110F),
    onSurface = Color(0xFFEEE0D5),
    surfaceVariant = Color(0xFF51443A),
    onSurfaceVariant = Color(0xFFD5C3B5),
    outline = Color(0xFF9D8E81),
    outlineVariant = Color(0xFF51443A),
    inverseSurface = Color(0xFFEEE0D5),
    inverseOnSurface = Color(0xFF372F28),
    inversePrimary = Color(0xFF8B5000),
    surfaceDim = Color(0xFF14110F),
    surfaceBright = Color(0xFF3F3832),
    surfaceContainerLowest = Color(0xFF0F0C0A),
    surfaceContainerLow = Color(0xFF1D1915),
    surfaceContainer = Color(0xFF221D19),
    surfaceContainerHigh = Color(0xFF2C2723),
    surfaceContainerHighest = Color(0xFF37322D),
)

/**
 * Colours that mean something and do not follow the theme: green for success,
 * confirmed and received; [warning] for pending, incomplete and warnings; the
 * scheme's `error` for failures, sends and destructive actions.
 */
@Immutable
data class SpectraSemanticColors(
    val success: Color,
    val warning: Color,
    val failure: Color,
    /** A prompt for the user's attention that is neither good nor bad. */
    val neutral: Color,
)

private val LightSemantic = SpectraSemanticColors(
    success = Color(0xFF1E7B34),
    warning = Color(0xFFC56A00),
    failure = Color(0xFFBA1A1A),
    neutral = Color(0xFF837469),
)

private val DarkSemantic = SpectraSemanticColors(
    success = Color(0xFF7FD891),
    warning = Color(0xFFFFB45E),
    failure = Color(0xFFFFB4AB),
    neutral = Color(0xFF9D8E81),
)

private val LocalSemanticColors = staticCompositionLocalOf { LightSemantic }

/**
 * The three radii of `SpectraLayout.Radius`, as Material's shape scale: a
 * card is `large`, a surface inside one `medium`, a dense control `small`.
 */
private val SpectraShapes = Shapes(
    extraSmall = RoundedCornerShape(SpectraLayout.Radius.control / 2),
    small = RoundedCornerShape(SpectraLayout.Radius.control),
    medium = RoundedCornerShape(SpectraLayout.Radius.inner),
    large = RoundedCornerShape(SpectraLayout.Radius.card),
    extraLarge = RoundedCornerShape(SpectraLayout.Radius.sheet),
)

/** Light, dark, or the system's choice: the appearance preference. */
@Composable
fun SpectraTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (darkTheme) DarkScheme else LightScheme
    androidx.compose.runtime.CompositionLocalProvider(
        LocalSemanticColors provides if (darkTheme) DarkSemantic else LightSemantic,
    ) {
        MaterialTheme(colorScheme = scheme, shapes = SpectraShapes, content = content)
    }
}

/** The semantic colours for the current theme. */
object SpectraColors {
    val semantic: SpectraSemanticColors
        @Composable @ReadOnlyComposable get() = LocalSemanticColors.current
}
