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
    val accent: Color,
    val brandPrimary: Color = accent
)

object TuesdayTheme {
    // Preset A: Tuesday.sh Brand Orange & Carbon Rack (STN-Pro)
    val Background = Color(0xFF111215)
    val AppBar = Color(0xFF181A1F)
    val CardSurface = Color(0xFF181A1F)
    val ElevatedPill = Color(0xFF20232A)
    val ProBrandPrimary = Color(0xFFE57819)
    val ProAccent = Color(0xFFE8772E)

    // Preset A: Daylight Home (SureThingNet STN-Home)
    val StnBackground = Color(0xFFF1F5F9)
    val StnSurface = Color(0xFFFFFFFF)
    val StnElevated = Color(0xFFF8FAFC)

    // Canonical 6-State LED Colors (5 Active States + 1 Unassigned Dark Slot)
    val LedOkGreen = Color(0xFF2EBD6B)
    val LedCveRed = Color(0xFFEF4444)
    val LedEolAmber = Color(0xFFF59E0B)
    val LedGuessedGrey = Color(0xFF8E95A2)
    val LedUpdateBlue = Color(0xFF3B82F6)
    val LedUnassignedPro = Color(0xFF1B1E26)
    val LedUnassignedStn = Color(0xFFE2E8F0)

    // Legacy compatibility aliases
    val HealthyGreen = LedOkGreen
    val WarningAmber = LedEolAmber
    val CriticalRed = LedCveRed
    val HacsCyan = ProAccent

    fun paletteFor(flavor: AppBrandFlavor): BrandPalette = when (flavor) {
        AppBrandFlavor.TUESDAY_PRO -> BrandPalette(
            bg = Background,
            surface = CardSurface,
            elevated = ElevatedPill,
            textPrimary = Color(0xFFF3F4F6),
            textMuted = Color(0xFF8E95A2),
            border = Color(0xFF242730),
            accent = ProAccent,
            brandPrimary = ProBrandPrimary
        )
        AppBrandFlavor.SURETHING_HOME -> BrandPalette(
            bg = StnBackground,
            surface = StnSurface,
            elevated = StnElevated,
            textPrimary = Color(0xFF0F172A),
            textMuted = Color(0xFF475569),
            border = Color(0xFFCBD5E1),
            accent = Color(0xFF0284C7),
            brandPrimary = Color(0xFF0284C7)
        )
    }

    fun ledColorFor(state: PortLifecycleState, isStn: Boolean = false): Color = when (state) {
        PortLifecycleState.OK -> if (isStn) Color(0xFF059669) else LedOkGreen
        PortLifecycleState.CVE_KEV -> if (isStn) Color(0xFFDC2626) else LedCveRed
        PortLifecycleState.EOL -> if (isStn) Color(0xFFD97706) else LedEolAmber
        PortLifecycleState.GUESSED -> if (isStn) Color(0xFF64748B) else LedGuessedGrey
        PortLifecycleState.UPDATE -> if (isStn) Color(0xFF0284C7) else LedUpdateBlue
        PortLifecycleState.UNASSIGNED -> if (isStn) LedUnassignedStn else LedUnassignedPro
    }
}
