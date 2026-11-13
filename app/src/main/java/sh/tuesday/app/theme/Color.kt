package sh.tuesday.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.DevicePresenceState
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.models.TrustZone

data class BrandPalette(
    val bg: Color,
    val surface: Color,
    val elevated: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val border: Color,
    val accent: Color,
    val brandPrimary: Color = accent,
    val borderStrong: Color = border,
    val chassisRecessed: Color = bg,
    val accentText: Color = accent,
    val isStn: Boolean = false
)

object TuesdayTheme {
    val colors: BrandPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandPalette.current

    val flavor: AppBrandFlavor
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandFlavor.current

    // Preset A: Tuesday.sh Brand Orange & Carbon Rack (STN-Pro)
    val Background = Color(0xFF111215)
    val AppBar = Color(0xFF181A1F)
    val CardSurface = Color(0xFF181A1F)
    val ElevatedPill = Color(0xFF20232A)
    val ProChassisRecessed = Color(0xFF0D0F14)
    val ProBrandPrimary = Color(0xFFE57819)
    val ProAccent = Color(0xFFE8772E)

    // Preset A: Daylight Home (SureThingNet STN-Home)
    val StnBackground = Color(0xFFF1F5F9)
    val StnSurface = Color(0xFFFFFFFF)
    val StnElevated = Color(0xFFF8FAFC)
    val StnBorder = Color(0xFFE2E8F0)
    val StnBorderStrong = Color(0xFFCBD5E1)
    val StnAccent = Color(0xFF0284C7)
    val StnAccentText = Color(0xFF0369A1) // >= 4.5:1 AA text contrast on #FFFFFF

    // Canonical 6-State LED Colors (5 Active States + 1 Unassigned Dark Slot)
    val LedOkGreen = Color(0xFF2EBD6B)
    val LedCveRed = Color(0xFFEF4444)
    val LedEolAmber = Color(0xFFF59E0B)
    val LedGuessedGrey = Color(0xFF8E95A2)
    val LedUpdateBlue = Color(0xFF3B82F6)
    val LedUnassignedPro = Color(0xFF1B1E26)
    val LedUnassignedStn = Color(0xFFE2E8F0)

    // Post-EOL active overhang crimson/rose
    val OverhangRosePro = Color(0xFFE11D48)
    val OverhangRoseStn = Color(0xFFBE123C)

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
            brandPrimary = ProBrandPrimary,
            borderStrong = Color(0xFF2E323E),
            chassisRecessed = ProChassisRecessed,
            accentText = ProAccent,
            isStn = false
        )
        AppBrandFlavor.SURETHING_HOME -> BrandPalette(
            bg = StnBackground,
            surface = StnSurface,
            elevated = StnElevated,
            textPrimary = Color(0xFF0F172A),
            textMuted = Color(0xFF475569),
            border = StnBorder,
            accent = StnAccent,
            brandPrimary = StnAccent,
            borderStrong = StnBorderStrong,
            chassisRecessed = StnBackground,
            accentText = StnAccentText,
            isStn = true
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

    fun zoneColorFor(zone: TrustZone, isStn: Boolean = false): Color = when (zone) {
        TrustZone.Z0_CORE -> if (isStn) Color(0xFF0891B2) else Color(0xFF06B6D4)
        TrustZone.Z1_TRUSTED -> if (isStn) Color(0xFF0284C7) else Color(0xFF38BDF8)
        TrustZone.Z2_LAB -> if (isStn) Color(0xFF7C3AED) else Color(0xFFA78BFA)
        TrustZone.Z3_IOT -> if (isStn) Color(0xFFD97706) else Color(0xFFF59E0B)
        TrustZone.Z4_GUEST -> if (isStn) Color(0xFF64748B) else Color(0xFF94A3B8)
    }

    fun presenceColorFor(state: DevicePresenceState, isStn: Boolean = false): Color = when (state) {
        DevicePresenceState.ACTIVE -> ledColorFor(PortLifecycleState.OK, isStn)
        DevicePresenceState.RETIRED -> if (isStn) Color(0xFF64748B) else LedGuessedGrey
        DevicePresenceState.LOST -> if (isStn) Color(0xFF9333EA) else Color(0xFFC084FC)
        DevicePresenceState.STOPPED_WORKING -> ledColorFor(PortLifecycleState.CVE_KEV, isStn)
    }

    fun overhangColor(isStn: Boolean = false): Color =
        if (isStn) OverhangRoseStn else OverhangRosePro
}
