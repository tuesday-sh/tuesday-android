package sh.tuesday.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Canonical Tuesday.sh & SureThingNet Typography Scale (`design-tokens.json` v1.2.0).
 * Enforces >= 11.sp across all general UI copy, metadata, badges, and interactive controls,
 * with explicit tokens for the fixed-geometry hardware faceplate counters.
 */
object TuesdayTypeTokens {
    val Headline = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 25.sp
    )
    val CardTitle = TextStyle(
        fontSize = 15.5.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 20.sp
    )
    val SectionHeader = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 19.sp
    )
    val DeviceRowName = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp
    )
    val Body = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp
    )
    val Label = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 15.sp
    )
    val MonoBadge = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        lineHeight = 14.sp
    )
    val MonoCode = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
        lineHeight = 15.sp
    )

    // Explicit fixed-geometry hardware counter exceptions (design-tokens.json fixedGeometryExceptions)
    val HardwareCounterFixed = TextStyle(
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )
    val SquircleCounterFixed = TextStyle(
        fontSize = 7.5.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )
}

val TuesdayTypography = Typography(
    headlineSmall = TuesdayTypeTokens.Headline,
    titleMedium = TuesdayTypeTokens.CardTitle,
    titleSmall = TuesdayTypeTokens.SectionHeader,
    bodyLarge = TuesdayTypeTokens.DeviceRowName,
    bodyMedium = TuesdayTypeTokens.Body,
    bodySmall = TuesdayTypeTokens.Body,
    labelLarge = TuesdayTypeTokens.Body.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = TuesdayTypeTokens.Label,
    labelSmall = TuesdayTypeTokens.Label
)
