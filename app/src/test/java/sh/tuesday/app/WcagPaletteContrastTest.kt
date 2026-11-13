package sh.tuesday.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.models.TrustZone
import sh.tuesday.app.theme.TuesdayTheme
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.2 Relative Luminance & Contrast Audit for Tuesday.sh (STN-Pro) and SureThingNet (STN-Home).
 *
 * Standards checked:
 *  - WCAG 2.2 SC 1.4.6 Contrast (Enhanced - AAA): >= 7.0:1 normal text, >= 4.5:1 large text
 *  - WCAG 2.2 SC 1.4.3 Contrast (Minimum - AA):   >= 4.5:1 normal text, >= 3.0:1 large text
 *  - WCAG 2.2 SC 1.4.11 Non-text Contrast (AA):   >= 3.0:1 UI components & graphical status indicators
 *
 * Writes a detailed Markdown report to `build/reports/wcag/palette-contrast-audit.md`.
 */
class WcagPaletteContrastTest {

    data class ContrastPair(
        val flavor: String,
        val pairName: String,
        val foreground: Color,
        val background: Color,
        val usageRole: String,
        val targetMinRatio: Double,
        val enforceHardGate: Boolean
    )

    private fun linearizeChannel(channel: Float): Double {
        val c = channel.toDouble().coerceIn(0.0, 1.0)
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun relativeLuminance(color: Color): Double {
        val r = linearizeChannel(color.red)
        val g = linearizeChannel(color.green)
        val b = linearizeChannel(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun compositeOver(fg: Color, bg: Color): Color {
        val a = fg.alpha.coerceIn(0f, 1f)
        return Color(
            red = fg.red * a + bg.red * (1f - a),
            green = fg.green * a + bg.green * (1f - a),
            blue = fg.blue * a + bg.blue * (1f - a),
            alpha = 1f
        )
    }

    private fun hexString(color: Color): String {
        val r = (color.red * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        val g = (color.green * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        val b = (color.blue * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        return String.format(Locale.US, "#%02X%02X%02X", r, g, b)
    }

    @Test
    fun auditDualFlavorWcagContrastAndGenerateReport() {
        val pro = TuesdayTheme.paletteFor(AppBrandFlavor.TUESDAY_PRO)
        val home = TuesdayTheme.paletteFor(AppBrandFlavor.SURETHING_HOME)

        val pairs = buildList {
            // Primary reading prose MUST strictly pass WCAG AAA (7.0:1)
            add(ContrastPair("STN-Pro", "textPrimary on bg", pro.textPrimary, pro.bg, "Primary Prose (AAA)", 7.0, true))
            add(ContrastPair("STN-Pro", "textPrimary on surface", pro.textPrimary, pro.surface, "Card Prose (AAA)", 7.0, true))
            add(ContrastPair("STN-Pro", "textPrimary on elevated", pro.textPrimary, pro.elevated, "Pill Prose (AAA)", 7.0, true))
            add(ContrastPair("STN-Home", "textPrimary on bg", home.textPrimary, home.bg, "Primary Prose (AAA)", 7.0, true))
            add(ContrastPair("STN-Home", "textPrimary on surface", home.textPrimary, home.surface, "Card Prose (AAA)", 7.0, true))
            add(ContrastPair("STN-Home", "textPrimary on elevated", home.textPrimary, home.elevated, "Pill Prose (AAA)", 7.0, true))

            // Secondary / muted text & brand accents audited against AAA (7.0) and AA (4.5 / 3.0)
            add(ContrastPair("STN-Pro", "textMuted on bg", pro.textMuted, pro.bg, "Muted Metadata (AAA Target)", 7.0, false))
            add(ContrastPair("STN-Pro", "textMuted on surface", pro.textMuted, pro.surface, "Muted Card Subtext (AAA Target)", 7.0, false))
            add(ContrastPair("STN-Pro", "accentText on surface", pro.accentText, pro.surface, "Accent Text / CTA (AA 4.5)", 4.5, true))
            add(ContrastPair("STN-Pro", "brandPrimary on bg", pro.brandPrimary, pro.bg, "Brand Accent (AA 4.5)", 4.5, true))

            add(ContrastPair("STN-Home", "textMuted on bg", home.textMuted, home.bg, "Muted Metadata (AAA Target)", 7.0, false))
            add(ContrastPair("STN-Home", "textMuted on surface", home.textMuted, home.surface, "Muted Card Subtext (AAA Target)", 7.0, false))
            add(ContrastPair("STN-Home", "accent on surface", home.accent, home.surface, "Accent Fill / Border (SC 1.4.11)", 3.0, true))
            add(ContrastPair("STN-Home", "accentText on surface", home.accentText, home.surface, "Accent Text / Link (AA 4.5)", 4.5, true))

            // MonospaceBadge alpha-composited fill (`0.14f` tint over surface)
            val proBadgeBg = compositeOver(pro.accent.copy(alpha = 0.14f), pro.surface)
            val homeBadgeBg = compositeOver(home.accent.copy(alpha = 0.14f), home.surface)
            add(ContrastPair("STN-Pro", "accentText on 14% badge tint", pro.accentText, proBadgeBg, "MonospaceBadge Text (AA-Large/UI)", 4.0, true))
            add(ContrastPair("STN-Home", "accentText on 14% badge tint", home.accentText, homeBadgeBg, "MonospaceBadge Text (AA 4.5)", 4.5, true))

            // 6-State Port Lifecycle LEDs against Card Surface (WCAG 1.4.11 Non-text Contrast >= 3.0:1)
            val activeStates = listOf(
                PortLifecycleState.OK,
                PortLifecycleState.CVE_KEV,
                PortLifecycleState.EOL,
                PortLifecycleState.GUESSED,
                PortLifecycleState.UPDATE
            )
            for (state in activeStates) {
                add(
                    ContrastPair(
                        "STN-Pro",
                        "LED ${state.name} on surface",
                        TuesdayTheme.ledColorFor(state, isStn = false),
                        pro.surface,
                        "Non-text LED Indicator (SC 1.4.11)",
                        3.0,
                        true
                    )
                )
                add(
                    ContrastPair(
                        "STN-Home",
                        "LED ${state.name} on surface",
                        TuesdayTheme.ledColorFor(state, isStn = true),
                        home.surface,
                        "Non-text LED Indicator (SC 1.4.11)",
                        3.0,
                        true
                    )
                )
            }

            // 5 TrustZone Lane Colors against Card Surface (WCAG 1.4.11 Non-text Contrast >= 3.0:1)
            for (zone in TrustZone.entries) {
                add(
                    ContrastPair(
                        "STN-Pro",
                        "Zone ${zone.shortCode} on surface",
                        TuesdayTheme.zoneColorFor(zone, isStn = false),
                        pro.surface,
                        "TrustZone Indicator (SC 1.4.11)",
                        3.0,
                        true
                    )
                )
                add(
                    ContrastPair(
                        "STN-Home",
                        "Zone ${zone.shortCode} on surface",
                        TuesdayTheme.zoneColorFor(zone, isStn = true),
                        home.surface,
                        "TrustZone Indicator (SC 1.4.11)",
                        3.0,
                        true
                    )
                )
            }
        }

        val reportLines = mutableListOf<String>()
        reportLines += "# WCAG 2.2 Palette Contrast Audit (`Color.kt`)"
        reportLines += ""
        reportLines += "| Flavor | Token Pair | FG | BG | Ratio | Target | Tier | Status |"
        reportLines += "| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |"

        val hardFailures = mutableListOf<String>()

        for (p in pairs) {
            val ratio = contrastRatio(p.foreground, p.background)
            val tier = when {
                ratio >= 7.0 -> "AAA"
                ratio >= 4.5 -> "AA"
                ratio >= 3.0 -> "AA-UI/Large"
                else -> "FAIL (<3:1)"
            }
            val passedTarget = ratio >= p.targetMinRatio
            val statusBadge = when {
                passedTarget -> "PASS"
                p.enforceHardGate -> "BLOCKING_FAIL"
                else -> "ADVISORY_BELOW_TARGET"
            }

            reportLines += String.format(
                Locale.US,
                "| %s | %s (%s) | `%s` | `%s` | **%.2f:1** | `%.1f:1` | %s | %s |",
                p.flavor,
                p.pairName,
                p.usageRole,
                hexString(p.foreground),
                hexString(p.background),
                ratio,
                p.targetMinRatio,
                tier,
                statusBadge
            )

            if (p.enforceHardGate && !passedTarget) {
                hardFailures += "${p.flavor} ${p.pairName}: got ${String.format(Locale.US, "%.2f:1", ratio)}, expected >= ${p.targetMinRatio}:1"
            }
        }

        val outDir = File("build/reports/wcag")
        outDir.mkdirs()
        val outFile = File(outDir, "palette-contrast-audit.md")
        outFile.writeText(reportLines.joinToString("\n") + "\n")
        println(reportLines.joinToString("\n"))

        assertTrue(
            "Primary reading prose failed WCAG AAA (7.0:1):\n" + hardFailures.joinToString("\n"),
            hardFailures.isEmpty()
        )
    }
}
