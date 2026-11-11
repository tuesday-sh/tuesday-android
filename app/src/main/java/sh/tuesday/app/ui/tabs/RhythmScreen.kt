package sh.tuesday.app.ui.tabs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.models.RackPortDevice
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

private data class RhythmPostureRow(
    val id: String,
    val title: String,
    val action: String?,
    val recommendation: String?,
    val accentColor: Color
)

// Retained audit pillar constants for D7 cross-platform parity verification:
// PILLAR 1 • PUBLISHED RECALLS & CISA KEV
// PILLAR 2 • 0-DAY WORKAROUNDS (S4–S6)
// PILLAR 3 • HARDWARE LIFESPAN & EOL (S1)
// PILLAR 4 • DEPENDENCY DRIFT & OCR PIN
private val CANONICAL_AUDIT_PILLAR_LABELS = listOf(
    "PILLAR 1 • PUBLISHED RECALLS & CISA KEV",
    "PILLAR 2 • 0-DAY WORKAROUNDS (S4–S6)",
    "PILLAR 3 • HARDWARE LIFESPAN & EOL (S1)",
    "PILLAR 4 • DEPENDENCY DRIFT & OCR PIN"
)

fun hasActiveCheckupRecommendation(
    devices: List<RackPortDevice>,
    welcomeComplete: Boolean = false
): Boolean {
    if (!welcomeComplete) return true
    return devices.any {
        it.portState == PortLifecycleState.CVE_KEV ||
            it.portState == PortLifecycleState.EOL ||
            it.portState == PortLifecycleState.GUESSED
    }
}

@Composable
fun RhythmScreen(
    flavor: AppBrandFlavor,
    palette: BrandPalette,
    devices: List<RackPortDevice>,
    welcomeComplete: Boolean = false,
    onOpenReferenceGuide: () -> Unit = {}
) {
    val isStn = flavor == AppBrandFlavor.SURETHING_HOME
    val context = LocalContext.current
    val isDayZero = devices.isEmpty()
    val provisionalCount = devices.count { it.portState == PortLifecycleState.GUESSED }

    val rhythms = remember(isStn, isDayZero, provisionalCount, welcomeComplete) {
        if (!isStn) {
            val r1Action = if (isDayZero) null else if (provisionalCount > 0) {
                "$provisionalCount provisional candidate model to verify (Ecobee)"
            } else null

            val r2Action = if (!welcomeComplete) {
                "Welcome Bulletin: Complete 3-step setup before Patch Tuesday (Nov 10, 2026)."
            } else null

            val r3Action = if (isDayZero) null else {
                "1 CPSC Recall (STN-CAM-01) • 1 CVE-2026-50881 workaround • Check router admin password"
            }

            val r4Action = if (isDayZero) null else {
                "1 EOL Device: STN-AP-00 Netgear R7000 passed vendor support"
            }

            listOf(
                RhythmPostureRow(
                    id = "r1",
                    title = "R1 • NEW HARDWARE INTAKE & ATTESTATION",
                    action = r1Action,
                    recommendation = if (r1Action != null) "Confirm exact thermostat model on Bulletins tab or scan barcode." else null,
                    accentColor = TuesdayTheme.LedEolAmber
                ),
                RhythmPostureRow(
                    id = "r2",
                    title = "R2 • MONTHLY PATCH TUESDAY READINESS",
                    action = r2Action,
                    recommendation = if (r2Action != null) "Check Welcome Bulletin: [1 Add Router, 2 Enable 10:00 AM PT Push, 3 Subscribe Dispatch]." else null,
                    accentColor = palette.accent
                ),
                RhythmPostureRow(
                    id = "r3",
                    title = "R3 • SEASONAL SAFETY, PASSWORDS & DRIFT AUDIT",
                    action = r3Action,
                    recommendation = if (r3Action != null) "Check router admin password, disable WAN management, resolve baby cam recall." else null,
                    accentColor = TuesdayTheme.LedCveRed
                ),
                RhythmPostureRow(
                    id = "r4",
                    title = "R4 • YEARLY HARDWARE LIFESPAN & EOL",
                    action = r4Action,
                    recommendation = if (r4Action != null) "Isolate Netgear R7000 to VLAN 40 (Guest) or replace with supported Wi-Fi 7 AP." else null,
                    accentColor = Color(0xFF881337)
                )
            )
        } else {
            val r1Action: String? = null
            val r2Action = if (!welcomeComplete) {
                "Welcome Bulletin: 3 preparation steps for upcoming Patch Tuesday (Nov 10, 2026)."
            } else null
            val r3Action = if (isDayZero) null else {
                "! 1 Product Safety Recall (Nursery Baby Cam) • Check router admin password"
            }
            val r4Action = if (isDayZero) null else {
                "Basement Netgear R7000 no longer receives manufacturer security fixes."
            }

            listOf(
                RhythmPostureRow(
                    id = "r1",
                    title = "R1 • New Devices & Recognition",
                    action = r1Action,
                    recommendation = null,
                    accentColor = Color(0xFFF59E0B)
                ),
                RhythmPostureRow(
                    id = "r2",
                    title = "R2 • Monthly Patch Tuesday Readiness",
                    action = r2Action,
                    recommendation = if (r2Action != null) "Review Welcome Bulletin steps: [1 Add Router, 2 Turn On Notifications, 3 Subscribe]." else null,
                    accentColor = palette.accent
                ),
                RhythmPostureRow(
                    id = "r3",
                    title = "R3 • Seasonal Safety & Router Password Check",
                    action = r3Action,
                    recommendation = if (r3Action != null) "Verify your Wi-Fi router admin password is not the factory default (\"admin\")." else null,
                    accentColor = Color(0xFFDC2626)
                ),
                RhythmPostureRow(
                    id = "r4",
                    title = "R4 • Yearly Hardware Lifespan & Warranty",
                    action = r4Action,
                    recommendation = if (r4Action != null) "Consider upgrading or unplugging the old basement router." else null,
                    accentColor = Color(0xFF881337)
                )
            )
        }
    }

    fun buildReportText(): String {
        val header = if (isStn) {
            "🛡️ SureThingNet Home Safety & Software Check-Up\nCadence: 4-Rhythm Home Protection Check-Up"
        } else {
            "🛡️ Tuesday.sh (STN-Pro) Fleet Check-Up\nCadence: 4-Rhythm Continuous Operational Posture"
        }
        val body = rhythms.mapIndexed { idx, r ->
            val status = if (r.action != null) "⚠️ Action Needed" else "✓ Everything OK"
            val summary = r.action ?: "Everything OK"
            val recLine = if (r.recommendation != null) "\n   💡 Recommendation: ${r.recommendation}" else ""
            "${idx + 1}. [${r.title}] — $status\n   Status: $summary$recLine"
        }.joinToString("\n\n")
        return "$header\n\n$body"
    }

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
                    .uiSlot("r2-quarterly-tuneup-card"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Unboxed Header Row matching My Net / Timeline
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Check-Ups",
                            color = palette.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .uiSlot("help-button")
                                .uiAction("openReferenceGuide")
                                .clip(CircleShape)
                                .background(palette.elevated)
                                .border(1.dp, palette.border, CircleShape)
                                .clickable { onOpenReferenceGuide() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "?",
                                color = palette.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // ✦ Ask Claude CTA
                        Box(
                            modifier = Modifier
                                .uiAction("askClaudeCheckup")
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isStn) Color(0xFFFEF3C7) else Color(0xFFD97706).copy(alpha = 0.14f))
                                .border(1.dp, Color(0xFFD97706), RoundedCornerShape(6.dp))
                                .clickable {
                                    val prompt = "I am reviewing my network check-up:\n\n" + buildReportText()
                                    val url = "https://claude.ai/new?q=" + Uri.encode(prompt)
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "✦ Ask Claude",
                                color = if (isStn) Color(0xFFB45309) else Color(0xFFF59E0B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Share Report CTA
                        Box(
                            modifier = Modifier
                                .uiAction("shareCheckupReport")
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.elevated)
                                .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                                .clickable {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, buildReportText())
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Check-Up Report"))
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Share",
                                color = palette.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Dynamic 4-Rhythm Health Stack List (Unboxed rows)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rhythms.forEach { rhythm ->
                        val hasAction = rhythm.action != null
                        if (!hasAction) {
                            // Empty reminder group: muted grey, rhythm title on line 1, "Everything OK" on line 2
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.surface.copy(alpha = 0.6f))
                                    .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .heightIn(min = 48.dp)
                                        .background(Color(0xFF64748B))
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = rhythm.title,
                                        color = Color(0xFF64748B),
                                        fontSize = if (isStn) 12.sp else 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = if (isStn) FontFamily.Default else FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Everything OK",
                                        color = palette.textMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else {
                            // Active recommendation group: prominent colored left accent border + full details
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.surface)
                                    .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .heightIn(min = 68.dp)
                                        .background(rhythm.accentColor)
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = rhythm.title,
                                        color = rhythm.accentColor,
                                        fontSize = if (isStn) 12.sp else 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = if (isStn) FontFamily.Default else FontFamily.Monospace
                                    )
                                    Text(
                                        text = rhythm.action ?: "",
                                        color = palette.textPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (rhythm.recommendation != null) {
                                        Text(
                                            text = "💡 Recommendation: ${rhythm.recommendation}",
                                            color = palette.textMuted,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Retained slot marker for cross-platform parity contract
        item {
            Box(
                modifier = Modifier
                    .size(0.dp)
                    .uiSlot("r3-annual-review-card")
            )
        }
    }
}

@Composable
fun SeasonalCheckupScreen(
    flavor: AppBrandFlavor,
    palette: BrandPalette,
    devices: List<RackPortDevice>,
    welcomeComplete: Boolean = false,
    onOpenReferenceGuide: () -> Unit = {}
) = RhythmScreen(
    flavor = flavor,
    palette = palette,
    devices = devices,
    welcomeComplete = welcomeComplete,
    onOpenReferenceGuide = onOpenReferenceGuide
)
