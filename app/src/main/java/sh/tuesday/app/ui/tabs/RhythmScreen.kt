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
import sh.tuesday.app.models.BulletinInboxView
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.ui.components.SurfaceListDivider
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
    bulletins: List<BulletinInboxView> = emptyList(),
    welcomeComplete: Boolean = false,
    onOpenReferenceGuide: () -> Unit = {}
) {
    val isStn = flavor == AppBrandFlavor.SURETHING_HOME
    val context = LocalContext.current
    val isDayZero = devices.isEmpty()

    val rhythms = remember(isStn, isDayZero, devices, bulletins, welcomeComplete) {
        val provisionalDevices = devices.filter { it.portState == PortLifecycleState.GUESSED }
        val cveDevices = devices.filter { it.portState == PortLifecycleState.CVE_KEV }
        val eolDevices = devices.filter { it.portState == PortLifecycleState.EOL }

        val recallBulletins = bulletins.filter { it.bulletinType.equals("RECALL", ignoreCase = true) }
        val patchBulletins = bulletins.filter {
            it.bulletinType.equals("CVE", ignoreCase = true) ||
                it.bulletinType.equals("CISA_KEV", ignoreCase = true) ||
                it.bulletinType.equals("ADVISORY", ignoreCase = true)
        }

        val r1Action = if (isDayZero || provisionalDevices.isEmpty()) null else {
            val count = provisionalDevices.size
            val labels = provisionalDevices.joinToString(", ") { it.name }
            "$count provisional device${if (count == 1) "" else "s"} to verify ($labels)"
        }
        val r1Rec = if (r1Action != null) {
            "Confirm exact hardware model on Bulletins tab or scan barcode."
        } else null

        val r2Action = when {
            !welcomeComplete -> if (isStn) {
                "Welcome Bulletin: 3 preparation steps for upcoming Patch Tuesday (2nd Tuesday of the month)."
            } else {
                "Welcome Bulletin: Complete 3-step setup before Patch Tuesday (2nd Tuesday of the month)."
            }
            patchBulletins.isNotEmpty() -> {
                val count = patchBulletins.size
                val titles = patchBulletins.joinToString(" • ") { it.title }
                "$count active Patch Tuesday / security bulletin${if (count == 1) "" else "s"}: $titles"
            }
            else -> null
        }
        val r2Rec = when {
            !welcomeComplete -> if (isStn) {
                "Review Welcome Bulletin steps: [1 Add Router, 2 Turn On Notifications, 3 Subscribe]."
            } else {
                "Check Welcome Bulletin: [1 Add Router, 2 Enable 10:00 AM PT Push, 3 Subscribe Dispatch]."
            }
            patchBulletins.isNotEmpty() -> "Review affected devices and apply vendor firmware updates on the Bulletins tab."
            else -> null
        }

        val r3Parts = buildList {
            if (recallBulletins.isNotEmpty()) {
                val count = recallBulletins.size
                add("$count Product Safety Recall${if (count == 1) "" else "s"} (${recallBulletins.joinToString(", ") { it.title }})")
            }
            if (cveDevices.isNotEmpty()) {
                val count = cveDevices.size
                add("$count CVE-affected device${if (count == 1) "" else "s"} (${cveDevices.joinToString(", ") { it.name }})")
            }
        }
        val r3Action = if (isDayZero || r3Parts.isEmpty()) null else r3Parts.joinToString(" • ")
        val r3Rec = if (r3Action != null) {
            if (isStn) {
                "Verify your Wi-Fi router admin password is not the factory default (\"admin\") and resolve active recalls."
            } else {
                "Check router admin password, disable WAN management, and resolve active recalls."
            }
        } else null

        val r4Action = if (isDayZero || eolDevices.isEmpty()) null else {
            val count = eolDevices.size
            val labels = eolDevices.joinToString(", ") { it.name }
            "$count EOL device${if (count == 1) "" else "s"} past vendor support ($labels)"
        }
        val r4Rec = if (r4Action != null) {
            if (isStn) {
                "Consider upgrading or unplugging unsupported hardware."
            } else {
                "Isolate EOL hardware to Guest VLAN or replace with supported gear."
            }
        } else null

        listOf(
            RhythmPostureRow(
                id = "r1",
                title = if (isStn) "R1 • New Devices & Recognition" else "R1 • NEW HARDWARE INTAKE & ATTESTATION",
                action = r1Action,
                recommendation = r1Rec,
                accentColor = if (isStn) Color(0xFFF59E0B) else TuesdayTheme.LedEolAmber
            ),
            RhythmPostureRow(
                id = "r2",
                title = if (isStn) "R2 • Monthly Patch Tuesday (2nd Tuesday)" else "R2 • MONTHLY PATCH TUESDAY READINESS",
                action = r2Action,
                recommendation = r2Rec,
                accentColor = palette.accent
            ),
            RhythmPostureRow(
                id = "r3",
                title = if (isStn) "R3 • Quarterly Safety Check (Jan / Apr / Jul / Oct)" else "R3 • SEASONAL SAFETY, PASSWORDS & DRIFT AUDIT",
                action = r3Action,
                recommendation = r3Rec,
                accentColor = if (isStn) Color(0xFFDC2626) else TuesdayTheme.LedCveRed
            ),
            RhythmPostureRow(
                id = "r4",
                title = if (isStn) "R4 • Annual Hardware Lifespan (January)" else "R4 • YEARLY HARDWARE LIFESPAN & EOL",
                action = r4Action,
                recommendation = r4Rec,
                accentColor = Color(0xFF881337)
            )
        )
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

                // Dynamic 4-Rhythm Health Stack List (Card toppers + unboxed list view items)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rhythms.forEach { rhythm ->
                        val hasAction = rhythm.action != null
                        val topperShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        val accentColor = if (hasAction) rhythm.accentColor else Color(0xFF64748B)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Fixed heading bar (card topper with top rounded corners, cut off bottom)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(topperShape)
                                    .background(if (hasAction) palette.surface else palette.surface.copy(alpha = 0.6f))
                                    .border(1.dp, palette.border, topperShape),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(if (hasAction) 4.dp else 3.dp)
                                        .height(34.dp)
                                        .background(accentColor)
                                )
                                Text(
                                    text = rhythm.title,
                                    color = accentColor,
                                    fontSize = if (isStn) 12.sp else 11.sp,
                                    fontWeight = if (hasAction) FontWeight.ExtraBold else FontWeight.Bold,
                                    fontFamily = if (isStn) FontFamily.Default else FontFamily.Monospace,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }

                            // List view items below the topper
                            if (!hasAction) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "Everything OK",
                                        color = palette.textMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                SurfaceListDivider(palette)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = rhythm.action ?: "",
                                        color = palette.textPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                SurfaceListDivider(palette)
                                if (rhythm.recommendation != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "💡 Recommendation: ${rhythm.recommendation}",
                                            color = palette.textMuted,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    SurfaceListDivider(palette)
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
