package sh.tuesday.app.theme

import androidx.compose.ui.graphics.Color
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.PortLifecycleState

data class BrandPalette(
    val bg: Color,
    val surface: Color,
    val elevated: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val border: Color,
    val accent: Color
)

object TuesdayTheme {
    // Preset A: Anodized Rack (Tuesday.sh STN-Pro)
    val Background = Color(0xFF0B0F17)
    val AppBar = Color(0xFF121824)
    val CardSurface = Color(0xFF121824)
    val ElevatedPill = Color(0xFF192231)

    // Preset A: Anodized Rack (SureThingNet STN-Home Daylight)
    val StnBackground = Color(0xFFF1F5F9)
    val StnSurface = Color(0xFFFFFFFF)
    val StnElevated = Color(0xFFE2E8F0)

    // Canonical 5-State LED Colors
    val LedOkGreen = Color(0xFF10B981)
    val LedCveRed = Color(0xFFF43F5E)
    val LedEolAmber = Color(0xFFF59E0B)
    val LedGuessedGrey = Color(0xFF64748B)
    val LedUpdateBlue = Color(0xFF38BDF8)

    // Legacy compatibility aliases
    val HealthyGreen = LedOkGreen
    val WarningAmber = LedEolAmber
    val CriticalRed = LedCveRed
    val HacsCyan = Color(0xFF06B6D4)

    fun paletteFor(flavor: AppBrandFlavor): BrandPalette = when (flavor) {
        AppBrandFlavor.TUESDAY_PRO -> BrandPalette(
            bg = Background,
            surface = CardSurface,
            elevated = ElevatedPill,
            textPrimary = Color(0xFFF8FAFC),
            textMuted = Color(0xFF94A3B8),
            border = Color(0xFF26334A),
            accent = Color(0xFF06B6D4)
        )
        AppBrandFlavor.SURETHING_HOME -> BrandPalette(
            bg = StnBackground,
            surface = StnSurface,
            elevated = StnElevated,
            textPrimary = Color(0xFF0F172A),
            textMuted = Color(0xFF334155),
            border = Color(0xFFCBD5E1),
            accent = Color(0xFF0284C7)
        )
    }

    fun ledColorFor(state: PortLifecycleState): Color = when (state) {
        PortLifecycleState.OK -> LedOkGreen
        PortLifecycleState.CVE_KEV -> LedCveRed
        PortLifecycleState.EOL -> LedEolAmber
        PortLifecycleState.GUESSED -> LedGuessedGrey
        PortLifecycleState.UPDATE -> LedUpdateBlue
    }
}
