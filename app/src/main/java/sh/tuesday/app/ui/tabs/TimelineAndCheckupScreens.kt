package sh.tuesday.app.ui.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.models.RackPortDevice
import sh.tuesday.app.models.TrustZone
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.home.RiskMeter
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

@Composable
fun SwimlaneTimelineScreen(
    flavor: AppBrandFlavor,
    palette: BrandPalette,
    devices: List<RackPortDevice>
) {
    val isStn = flavor == AppBrandFlavor.SURETHING_HOME

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .uiComponent("SwimlaneTimelineScreen")
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("timeline-header-card")
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isStn) "HOME DEVICE LIFESPAN TRACKER" else "Z0 → Z4 LIFECYCLE SWIMLANES (2024 – 2030)",
                        color = palette.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (!isStn) {
                        Text(
                            text = "CURSOR: NOW • Zoom: 1M",
                            color = palette.textMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    text = if (isStn) {
                        "See how many years of safe manufacturer updates remain on each device in your home."
                    } else {
                        "Hardware support windows, vendor EOL cutoffs, and active vs. vintage lifecycle tiers."
                    },
                    color = palette.textMuted,
                    fontSize = 11.sp
                )

                if (!isStn && devices.isNotEmpty()) {
                    SwimlaneCanvas(
                        devices = devices,
                        palette = palette
                    )
                }
            }
        }

        // Section Header for Dependency Ledger (Pro) or Lifespan Cards (Home)
        item {
            Text(
                text = if (isStn) {
                    "DEVICE SUPPORT & WARRANTY WINDOWS (${devices.size})"
                } else {
                    "PURL DEPENDENCY & ADVISORY LEDGER (${devices.size} NODES)"
                },
                color = palette.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        items(devices, key = { it.id }) { dev ->
            val progress = when (dev.portState) {
                PortLifecycleState.EOL -> 1.0f
                PortLifecycleState.CVE_KEV -> 0.68f
                PortLifecycleState.GUESSED -> 0.50f
                PortLifecycleState.UPDATE -> 0.35f
                PortLifecycleState.OK -> 0.25f
                PortLifecycleState.UNASSIGNED -> 0.0f
            }
            val barColor = TuesdayTheme.ledColorFor(dev.portState, isStn)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "#%02d %s".format(dev.portIndex, dev.name),
                                color = palette.textPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (!isStn) {
                                Text(
                                    text = dev.assetTag,
                                    color = palette.textMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        if (!isStn && dev.purls.isNotEmpty()) {
                            Text(
                                text = dev.purls.joinToString(" • "),
                                color = palette.accent,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    if (isStn) {
                        Text(
                            text = "${dev.trustZone.stnLabel} • ${dev.lifecycleTier}",
                            color = barColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        RiskMeter(portState = dev.portState, isStn = false)
                    }
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = barColor,
                    trackColor = palette.elevated
                )

                Text(
                    text = if (isStn) "${dev.s3Lifecycle} • Version ${dev.version}" else "${dev.s3Lifecycle} • ${dev.s4Advisories}",
                    color = palette.textMuted,
                    fontSize = 10.sp,
                    fontFamily = if (isStn) FontFamily.Default else FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun SwimlaneCanvas(
    devices: List<RackPortDevice>,
    palette: BrandPalette
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0B0D11))
            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
    ) {
        val w = size.width
        val h = size.height
        val footerH = 12.dp.toPx()
        val labelW = 74.dp.toPx()
        val plotW = (w - labelW - 10.dp.toPx()).coerceAtLeast(10f)
        val laneCount = devices.size.coerceAtLeast(1)
        val laneH = (h - footerH) / laneCount

        // Vertical micro-grid lines
        for (i in 0..6) {
            val gx = labelW + (plotW * i / 6f)
            drawLine(
                color = Color(0xFF1D2028),
                start = Offset(gx, 0f),
                end = Offset(gx, h - footerH),
                strokeWidth = 1f
            )
        }

        devices.forEachIndexed { idx, dev ->
            val yTop = idx * laneH
            val yCenter = yTop + laneH / 2f
            val ledColor = TuesdayTheme.ledColorFor(dev.portState, isStn = false)
            val zoneColor = when (dev.trustZone) {
                TrustZone.Z0_CORE -> Color(0xFF06B6D4)
                TrustZone.Z1_TRUSTED -> Color(0xFF0284C7)
                TrustZone.Z2_LAB -> Color(0xFF7C3AED)
                TrustZone.Z3_IOT -> Color(0xFFD97706)
                TrustZone.Z4_GUEST -> Color(0xFF64748B)
            }

            // Left zone indicator bar
            drawRect(
                color = zoneColor,
                topLeft = Offset(0f, yTop + 2f),
                size = Size(3.dp.toPx(), (laneH - 4f).coerceAtLeast(2f))
            )

            // Lane baseline
            drawLine(
                color = Color(0xFF1F232B),
                start = Offset(labelW, yCenter),
                end = Offset(labelW + plotW, yCenter),
                strokeWidth = 1.5f
            )

            // Attestation span segment
            val startFrac = ((idx * 13) % 45) / 100f + 0.12f
            val endFrac = 0.88f
            val x1 = labelW + startFrac * plotW
            val x2 = labelW + endFrac * plotW

            drawLine(
                color = ledColor,
                start = Offset(x1, yCenter),
                end = Offset(x2, yCenter),
                strokeWidth = 2.5.dp.toPx()
            )
            drawCircle(
                color = ledColor,
                radius = 3.5.dp.toPx(),
                center = Offset(x1, yCenter)
            )

            // Red CVE warning triangle for CVE/KEV devices
            if (dev.portState == PortLifecycleState.CVE_KEV) {
                val cx = labelW + 0.76f * plotW
                val tri = Path().apply {
                    moveTo(cx, yCenter - 5.dp.toPx())
                    lineTo(cx - 4.dp.toPx(), yCenter + 3.dp.toPx())
                    lineTo(cx + 4.dp.toPx(), yCenter + 3.dp.toPx())
                    close()
                }
                drawPath(path = tri, color = TuesdayTheme.LedCveRed)
            }
        }

        // Bottom Green / Amber / Red health timeline footer bar
        val fy = h - footerH + 3.dp.toPx()
        val barH = 4.dp.toPx()
        drawRect(
            color = TuesdayTheme.LedOkGreen,
            topLeft = Offset(labelW, fy),
            size = Size(plotW * 0.72f, barH)
        )
        drawRect(
            color = TuesdayTheme.LedEolAmber,
            topLeft = Offset(labelW + plotW * 0.72f, fy),
            size = Size(plotW * 0.12f, barH)
        )
        drawRect(
            color = TuesdayTheme.LedCveRed,
            topLeft = Offset(labelW + plotW * 0.84f, fy),
            size = Size(plotW * 0.16f, barH)
        )

        // Scrubber vertical line at 0.88
        val scrubX = labelW + 0.88f * plotW
        drawLine(
            color = palette.accent,
            start = Offset(scrubX, 0f),
            end = Offset(scrubX, h - footerH),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

private data class AuditPillarItem(
    val proHeader: String,
    val proSummary: String,
    val stnHeader: String,
    val stnSummary: String,
    val state: PortLifecycleState
)

@Composable
fun SeasonalCheckupScreen(
    flavor: AppBrandFlavor,
    palette: BrandPalette,
    devices: List<RackPortDevice>
) {
    val isStn = flavor == AppBrandFlavor.SURETHING_HOME
    val nonGreen = devices.filter { it.portState != PortLifecycleState.OK && it.portState != PortLifecycleState.UNASSIGNED }

    val auditPillars = listOf(
        AuditPillarItem(
            proHeader = "PILLAR 1 • PUBLISHED RECALLS & CISA KEV",
            proSummary = "1 CPSC Recall (STN-CAM-01) • 1 KEV Patch (STN-IOT-01)",
            stnHeader = "! 1 Published Safety Recall Found",
            stnSummary = "Nursery Baby Monitor has an active manufacturer recall (CPSC-26-104).",
            state = PortLifecycleState.CVE_KEV
        ),
        AuditPillarItem(
            proHeader = "PILLAR 2 • 0-DAY WORKAROUNDS (S4–S6)",
            proSummary = "STN-GW-01 on latest v4.0.20 (CVE-2026-50881)",
            stnHeader = "↻ 2 Software Updates Available",
            stnSummary = "Living Room LG OLED TV (security fix 8.3.0) and Garage Relay ESP32.",
            state = PortLifecycleState.EOL
        ),
        AuditPillarItem(
            proHeader = "PILLAR 3 • HARDWARE LIFESPAN & EOL (S1)",
            proSummary = "STN-AP-00 Netgear R7000 passed EOL (2025-12-31)",
            stnHeader = "⏳ 1 Retired Device Past Support Cutoff",
            stnSummary = "Basement Netgear R7000 ended manufacturer updates on Dec 31, 2025.",
            state = PortLifecycleState.CVE_KEV
        ),
        AuditPillarItem(
            proHeader = "PILLAR 4 • DEPENDENCY DRIFT & OCR PIN",
            proSummary = "1 Minor Update (ESPHome) • 1 Unverified Node",
            stnHeader = "🔍 1 Device Needs Model Verification",
            stnSummary = "Hallway Smart Thermostat is matched at 72% confidence — snap label to pin.",
            state = PortLifecycleState.UPDATE
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .uiComponent("SeasonalCheckupScreen")
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("r2-quarterly-tuneup-card")
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isStn) "🏡 R2/R3 FALL HOME CHECKUP • 10 MINS" else "⚡ Q4 FLEET TUNE-UP & REMEDIATION QUEUE",
                    color = palette.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${nonGreen.size} devices have pending lifecycle actions before the quarterly sign-off.",
                    color = palette.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                // 4 R2 Quarterly Audit Pillars
                auditPillars.forEach { pillar ->
                    val pColor = TuesdayTheme.ledColorFor(pillar.state, isStn)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.elevated)
                            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (isStn) pillar.stnHeader else pillar.proHeader,
                                color = pColor,
                                fontSize = if (isStn) 11.5.sp else 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = if (isStn) FontFamily.Default else FontFamily.Monospace
                            )
                            Text(
                                text = if (isStn) pillar.stnSummary else pillar.proSummary,
                                color = palette.textPrimary,
                                fontSize = 11.sp
                            )
                        }
                        if (!isStn) {
                            Spacer(modifier = Modifier.width(8.dp))
                            RiskMeter(portState = pillar.state, isStn = false)
                        }
                    }
                }
            }
        }

        // R3 Annual Gear Review Card (Active / Vintage / Legacy EOL)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("r3-annual-review-card")
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isStn) "Yearly Lifespan Check (R3)" else "R3 Annual Gear Review (Active / Vintage / Legacy)",
                        color = palette.textPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isStn) "1 Retired Model" else "1 Legacy EOL",
                        color = TuesdayTheme.WarningAmber,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = if (isStn) {
                        "11 of your 12 devices are Current & Supported. Your Basement Netgear R7000 reached manufacturer End-of-Support on Dec 31, 2025 and no longer receives security updates."
                    } else {
                        "Cross-referenced against Vendor EOL Schedules & CISA BOD 23-01: STN-AP-00 (Netgear R7000) reached End-of-Support on 2025-12-31 and no longer receives CVE patches."
                    },
                    color = palette.textMuted,
                    fontSize = 10.5.sp
                )
            }
        }

        items(nonGreen, key = { it.id }) { dev ->
            val stateColor = TuesdayTheme.ledColorFor(dev.portState, isStn)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Port #%02d • %s".format(dev.portIndex, dev.name),
                        color = palette.textPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isStn) dev.portState.stnLabel else dev.portState.proLabel,
                        color = stateColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = dev.s6Remediation,
                    color = palette.textMuted,
                    fontSize = 10.5.sp
                )
            }
        }
    }
}
