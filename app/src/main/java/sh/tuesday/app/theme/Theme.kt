package sh.tuesday.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import sh.tuesday.app.models.AppBrandFlavor

val LocalBrandFlavor = staticCompositionLocalOf { AppBrandFlavor.TUESDAY_PRO }
val LocalBrandPalette = staticCompositionLocalOf { TuesdayTheme.paletteFor(AppBrandFlavor.TUESDAY_PRO) }

private val ProDarkColorScheme = darkColorScheme(
    primary = TuesdayTheme.ProAccent,
    onPrimary = Color.White,
    secondary = TuesdayTheme.HealthyGreen,
    onSecondary = Color.Black,
    tertiary = TuesdayTheme.WarningAmber,
    background = TuesdayTheme.Background,
    surface = TuesdayTheme.CardSurface,
    surfaceVariant = TuesdayTheme.ElevatedPill,
    onBackground = Color(0xFFF3F4F6),
    onSurface = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF8E95A2),
    outline = Color(0xFF242730),
    error = TuesdayTheme.CriticalRed,
    onError = Color.White
)

private val StnLightColorScheme = lightColorScheme(
    primary = TuesdayTheme.StnAccent,
    onPrimary = Color.White,
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    tertiary = Color(0xFFD97706),
    background = TuesdayTheme.StnBackground,
    surface = TuesdayTheme.StnSurface,
    surfaceVariant = TuesdayTheme.StnElevated,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    outline = TuesdayTheme.StnBorder,
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun TuesdayAppTheme(
    flavor: AppBrandFlavor = AppBrandFlavor.TUESDAY_PRO,
    content: @Composable () -> Unit
) {
    val palette = TuesdayTheme.paletteFor(flavor)
    val colorScheme = when (flavor) {
        AppBrandFlavor.TUESDAY_PRO -> ProDarkColorScheme
        AppBrandFlavor.SURETHING_HOME -> StnLightColorScheme
    }
    CompositionLocalProvider(
        LocalBrandFlavor provides flavor,
        LocalBrandPalette provides palette
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TuesdayTypography,
            content = content
        )
    }
}
