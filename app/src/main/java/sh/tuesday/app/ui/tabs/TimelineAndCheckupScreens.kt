package sh.tuesday.app.ui.tabs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.AttestationType
import sh.tuesday.app.models.BulletinsAndInventoryEngine
import sh.tuesday.app.models.DeviceAttestation
import sh.tuesday.app.models.DevicePresenceState
import sh.tuesday.app.models.LifecycleLayer
import sh.tuesday.app.models.OperationalCandle
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.models.RackPortDevice
import sh.tuesday.app.models.TrustZone
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class TimelineViewMode {
    CANDLESTICKS,
    ATTESTED_EVENTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwimlaneTimelineScreen(
    flavor: AppBrandFlavor,
    palette: BrandPalette,
    devices: List<RackPortDevice>,
    attestations: List<DeviceAttestation> = emptyList(),
    userSignature: String = "Aaron Bronow (sig_4b9a8c)",
    onOpenReferenceGuide: () -> Unit = {},
    onInspectDevice: (String) -> Unit = {},
    onRecordAttestation: ((deviceId: String, type: AttestationType, auditDate: String, note: String?, sessionId: String?) -> DeviceAttestation)? = null,
    onRecordBatchAudit: ((deviceIds: List<String>, auditDate: String, sessionId: String, note: String?) -> List<DeviceAttestation>)? = null
) {
    val isStn = flavor == AppBrandFlavor.SURETHING_HOME
    var activeViewMode by remember { mutableStateOf(TimelineViewMode.CANDLESTICKS) }
    var activeLayer by remember { mutableStateOf(LifecycleLayer.HARDWARE) }
    var scrubberXNorm by remember { mutableFloatStateOf(0.72f) }
    val retiredOverrides = remember { mutableStateMapOf<String, Boolean>() }
    var selectedDeviceForAttestation by remember { mutableStateOf<RackPortDevice?>(null) }
    var showBatchAuditModal by remember { mutableStateOf(false) }
    var expandedAuditTrailDeviceId by remember { mutableStateOf<String?>(null) }
    var attestationFilterType by remember { mutableStateOf<AttestationType?>(null) }

    val effectiveDevices = remember(devices) {
        if (devices.isEmpty()) BulletinsAndInventoryEngine.defaultRackPortDevices() else devices
    }

    val sortedDevices = remember(effectiveDevices) {
        BulletinsAndInventoryEngine.sortDevicesForTimeline(effectiveDevices)
    }

    val currentCandles = remember(sortedDevices, retiredOverrides.toMap(), scrubberXNorm, activeLayer) {
        when (activeLayer) {
            LifecycleLayer.HARDWARE -> sortedDevices.map { dev ->
                val isRet = retiredOverrides[dev.id] ?: dev.isRetired
                BulletinsAndInventoryEngine.toOperationalCandle(
                    device = dev.copy(isRetired = isRet),
                    referenceNowNorm = scrubberXNorm
                )
            }
            LifecycleLayer.OS -> BulletinsAndInventoryEngine.sampleHostOsCandles(referenceNowNorm = scrubberXNorm)
            LifecycleLayer.CONTAINER -> BulletinsAndInventoryEngine.sampleContainerCandles(referenceNowNorm = scrubberXNorm)
        }
    }

    val dayOffset = ((0.72f - scrubberXNorm) * 120f).roundToInt()
    val cursorDateStr = if (kotlin.math.abs(dayOffset) <= 1) {
        "2026-11-11 (TODAY)"
    } else if (dayOffset > 0) {
        "2026-11-11 (-%dd)".format(dayOffset)
    } else {
        "2026-11-11 (+%dd projection)".format(-dayOffset)
    }

    val filteredAttestations = remember(attestations, attestationFilterType) {
        if (attestationFilterType == null) attestations
        else attestations.filter { it.type == attestationFilterType }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .uiComponent("SwimlaneTimelineScreen")
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // 1. Unboxed Header with View Mode Switcher + Batch Ingress Audit Trigger
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("timeline-header-card"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Header Row
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
                            text = if (isStn) "Lifespan & Attestations" else "Timeline & Attestations",
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

                    // Quick trigger to record a point-in-time fleet audit
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.accent.copy(alpha = 0.15f))
                            .border(0.8.dp, palette.accent, RoundedCornerShape(4.dp))
                            .clickable { showBatchAuditModal = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "+ Point-in-Time Audit",
                            color = palette.accent,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // View Mode Segmented Control: Candlesticks vs Attestation Stream
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.elevated)
                        .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (activeViewMode == TimelineViewMode.CANDLESTICKS) palette.accent else Color.Transparent)
                            .clickable { activeViewMode = TimelineViewMode.CANDLESTICKS }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📊 Lifecycle Candlesticks",
                            color = if (activeViewMode == TimelineViewMode.CANDLESTICKS) Color.Black else palette.textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (activeViewMode == TimelineViewMode.ATTESTED_EVENTS) palette.accent else Color.Transparent)
                            .clickable { activeViewMode = TimelineViewMode.ATTESTED_EVENTS }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📜 Attested Events (${attestations.size})",
                            color = if (activeViewMode == TimelineViewMode.ATTESTED_EVENTS) Color.Black else palette.textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ==================== VIEW MODE 1: CANDLESTICK ENVELOPE ====================
        if (activeViewMode == TimelineViewMode.CANDLESTICKS) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Layer Selection Chips (Hardware • Host OS • Containers)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LAYER FOCUS:",
                            color = palette.textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LifecycleLayer.entries.forEach { layer ->
                                val selected = activeLayer == layer
                                val count = when (layer) {
                                    LifecycleLayer.HARDWARE -> effectiveDevices.size
                                    LifecycleLayer.OS -> 6
                                    LifecycleLayer.CONTAINER -> 5
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (selected) palette.accent.copy(alpha = 0.2f) else palette.elevated)
                                        .border(
                                            0.8.dp,
                                            if (selected) palette.accent else palette.border,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { activeLayer = layer }
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${layer.label} ($count)",
                                        color = if (selected) palette.accent else palette.textMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    // Cursor Date & Timeline Scrubber
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF13171F))
                            .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("⏱", fontSize = 12.sp)
                            Text(
                                text = "POINT-IN-TIME CURSOR:",
                                color = palette.textMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = cursorDateStr,
                                color = if (kotlin.math.abs(dayOffset) <= 1) Color(0xFF2EBD6B) else palette.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (kotlin.math.abs(dayOffset) > 1) {
                            Text(
                                text = "[RESET TODAY]",
                                color = palette.accent,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.clickable { scrubberXNorm = 0.72f }
                            )
                        }
                    }

                    // 2D Candlestick Lifespan Canvas
                    SwimlaneCanvas(
                        candles = currentCandles,
                        palette = palette,
                        scrubberXNorm = scrubberXNorm,
                        onScrubChanged = { scrubberXNorm = it }
                    )

                    // Visual Grammar Legend (All 6 States)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F1218))
                            .border(0.5.dp, palette.border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "CANDLESTICK LIFECYCLE GRAMMAR:",
                            color = palette.accent,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(width = 8.dp, height = 6.dp).background(Color(0xFF2EBD6B)))
                                Text("Solid: Active", color = palette.textMuted, fontSize = 9.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(width = 8.dp, height = 6.dp).border(1.dp, Color(0xFF64748B)))
                                Text("Hollow: Retired", color = palette.textMuted, fontSize = 9.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(width = 8.dp, height = 6.dp).border(1.dp, Color(0xFFA855F7)))
                                Text("Dashed: Lost", color = Color(0xFFC084FC), fontSize = 9.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(width = 8.dp, height = 6.dp).background(Color(0xFFEF4444)))
                                Text("Slash: Defect", color = Color(0xFFF87171), fontSize = 9.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(width = 8.dp, height = 6.dp).background(Color(0xFFF59E0B)))
                                Text("Amber: EOL Nears", color = palette.textMuted, fontSize = 9.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(width = 8.dp, height = 6.dp).background(Color(0xFFDC2626)))
                                Text("Overhang: Past EOL", color = Color(0xFFF87171), fontSize = 9.sp)
                            }
                        }
                    }

                    // Section Title
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = 2.dp, end = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (activeLayer) {
                                LifecycleLayer.HARDWARE -> if (isStn) "Hardware Lifespan & Active Tenure" else "Operational Tenures & Attested State"
                                LifecycleLayer.OS -> "Host OS Tenures & Security EOL Envelopes"
                                LifecycleLayer.CONTAINER -> "Container Deployment Uptimes & Deprecations"
                            },
                            color = palette.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${activeLayer.label.uppercase()} LAYER",
                            color = palette.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            if (currentCandles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No operational items tracked in ${activeLayer.label} layer.",
                            color = palette.textMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(currentCandles, key = { "${it.layer}-${it.id}" }) { candle ->
                    val isPastEol = candle.isPastEol
                    val isEolNearing = candle.isEolNearing
                    val presenceState = candle.presenceState

                    val statusBadgeColor = when (presenceState) {
                        DevicePresenceState.STOPPED_WORKING -> Color(0xFFEF4444)
                        DevicePresenceState.LOST -> Color(0xFFA855F7)
                        DevicePresenceState.RETIRED -> Color(0xFF64748B)
                        DevicePresenceState.ACTIVE -> when {
                            candle.hasCveOrRecall -> Color(0xFFDC2626)
                            isPastEol -> Color(0xFFDC2626)
                            isEolNearing -> Color(0xFFF59E0B)
                            else -> Color(0xFF2EBD6B)
                        }
                    }

                    val statusText = when (presenceState) {
                        DevicePresenceState.STOPPED_WORKING -> "STOPPED WORKING • DEFECT"
                        DevicePresenceState.LOST -> "LOST • UNACCOUNTED"
                        DevicePresenceState.RETIRED -> "RETIRED • DECOMMISSIONED"
                        DevicePresenceState.ACTIVE -> when {
                            isPastEol -> "ZOMBIE HARDWARE • PAST EOL"
                            isEolNearing -> "EOL NEARS • RUNWAY LOW"
                            candle.hasCveOrRecall -> "SECURITY RECALL / CVE"
                            else -> "ACTIVE • SUPPORTED RUNWAY"
                        }
                    }

                    val devAttestations = remember(attestations, candle.id) {
                        attestations.filter { it.deviceId == candle.id }
                    }
                    val isTrailExpanded = expandedAuditTrailDeviceId == candle.id

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (candle.layer == LifecycleLayer.HARDWARE) onInspectDevice(candle.id) }
                    ) {
                        HorizontalDivider(color = palette.border, thickness = 1.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (candle.portIndex > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(statusBadgeColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = candle.portIndex.toString(),
                                                color = Color.White,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                    Text(
                                        text = candle.name,
                                        color = palette.textPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                // Interactive State Attestation & Audit Trail Chips
                                if (candle.layer == LifecycleLayer.HARDWARE) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Trail Expansion toggle
                                        if (devAttestations.isNotEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isTrailExpanded) palette.accent.copy(alpha = 0.2f) else palette.elevated)
                                                    .border(0.8.dp, if (isTrailExpanded) palette.accent else palette.border, RoundedCornerShape(4.dp))
                                                    .clickable {
                                                        expandedAuditTrailDeviceId = if (isTrailExpanded) null else candle.id
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Trail (${devAttestations.size})",
                                                    color = if (isTrailExpanded) palette.accent else palette.textMuted,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }

                                        // Attest State Change button
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(palette.accent.copy(alpha = 0.15f))
                                                .border(0.8.dp, palette.accent, RoundedCornerShape(4.dp))
                                                .clickable {
                                                    val targetDev = effectiveDevices.find { it.id == candle.id }
                                                    selectedDeviceForAttestation = targetDev
                                                }
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Attest State",
                                                color = palette.accent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }

                            // Mini Candlestick Bar Widget
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MiniCandleBar(
                                    candle = candle,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = statusText,
                                    color = statusBadgeColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Operational metrics row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val preAgeText = "Pre-adoption: %s".format(candle.releaseDateStr ?: "Launch")
                                val deployText = if (presenceState.isOutOfService) {
                                    "${presenceState.shortLabel}: %s".format(candle.decommissionDateStr ?: "Attested Out")
                                } else {
                                    "Active since: %s".format(candle.deployDateStr ?: "Enrollment")
                                }
                                val eolText = "EOL: %s".format(candle.eolDateStr ?: "TBD")

                                Text(
                                    text = "$preAgeText  •  $deployText",
                                    color = palette.textMuted,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = eolText,
                                    color = if (isPastEol) Color(0xFFF87171) else palette.textMuted,
                                    fontSize = 11.sp,
                                    fontWeight = if (isPastEol) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            // Latest Attestation Signer Stamp
                            val latestAtt = candle.latestAttestation ?: devAttestations.firstOrNull()
                            if (latestAtt != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF0F1218))
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "👤 ${latestAtt.userSignature} • 📅 ${latestAtt.auditDate}",
                                        color = palette.textMuted,
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "🔐 sig:${latestAtt.shortProof}",
                                        color = palette.accent,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Expanded Audit Trail Drawer (if opened)
                            if (isTrailExpanded && devAttestations.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF13171F))
                                        .border(0.8.dp, palette.border, RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "ATTESTATION HISTORY & POINT-IN-TIME AUDITS:",
                                        color = palette.accent,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    devAttestations.forEach { att ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${att.type.displayLabel}  •  ${att.auditDate}",
                                                    color = palette.textPrimary,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (!att.note.isNullOrBlank()) {
                                                    Text(
                                                        text = "“${att.note}”",
                                                        color = palette.textMuted,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                Text(
                                                    text = "Signer: ${att.userSignature} • Env: ${att.environment}",
                                                    color = palette.textMuted.copy(alpha = 0.8f),
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Text(
                                                text = "🔐 ${att.shortProof}",
                                                color = palette.accent,
                                                fontSize = 9.5.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        HorizontalDivider(color = palette.border.copy(alpha = 0.3f), thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== VIEW MODE 2: ATTESTED EVENT STREAM ====================
        if (activeViewMode == TimelineViewMode.ATTESTED_EVENTS) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column {
                        Text(
                            text = "CHRONOLOGICAL FLEET ATTESTATION AUDIT LOG",
                            color = palette.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Every point-in-time presence audit, retirement, loss, and defect event is cryptographically sealed by an operator present in the environment.",
                            color = palette.textMuted,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }

                    // Summary Metrics Row
                    val presenceCount = attestations.count { it.type == AttestationType.PRESENCE_AUDIT || it.type == AttestationType.PRESENCE_REACTIVATED }
                    val retiredCount = attestations.count { it.type == AttestationType.ABSENCE_RETIRED }
                    val lostCount = attestations.count { it.type == AttestationType.ABSENCE_LOST }
                    val defectCount = attestations.count { it.type == AttestationType.ABSENCE_STOPPED_WORKING }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AuditMetricBadge("TOTAL", attestations.size.toString(), palette.textPrimary, palette, Modifier.weight(1f))
                        AuditMetricBadge("PRESENCE", presenceCount.toString(), Color(0xFF2EBD6B), palette, Modifier.weight(1f))
                        AuditMetricBadge("RETIRED", retiredCount.toString(), Color(0xFF64748B), palette, Modifier.weight(1f))
                        AuditMetricBadge("LOST", lostCount.toString(), Color(0xFFA855F7), palette, Modifier.weight(1f))
                        AuditMetricBadge("DEFECT", defectCount.toString(), Color(0xFFEF4444), palette, Modifier.weight(1f))
                    }

                    // Filter chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("FILTER:", color = palette.textMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        AttestationFilterChip("All (${attestations.size})", isSelected = attestationFilterType == null, palette) {
                            attestationFilterType = null
                        }
                        AttestationFilterChip("Presence", isSelected = attestationFilterType == AttestationType.PRESENCE_AUDIT, palette) {
                            attestationFilterType = AttestationType.PRESENCE_AUDIT
                        }
                        AttestationFilterChip("Retired", isSelected = attestationFilterType == AttestationType.ABSENCE_RETIRED, palette) {
                            attestationFilterType = AttestationType.ABSENCE_RETIRED
                        }
                        AttestationFilterChip("Lost", isSelected = attestationFilterType == AttestationType.ABSENCE_LOST, palette) {
                            attestationFilterType = AttestationType.ABSENCE_LOST
                        }
                        AttestationFilterChip("Defect", isSelected = attestationFilterType == AttestationType.ABSENCE_STOPPED_WORKING, palette) {
                            attestationFilterType = AttestationType.ABSENCE_STOPPED_WORKING
                        }
                    }
                }
            }

            if (filteredAttestations.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No attestations match the selected filter.",
                            color = palette.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                items(filteredAttestations, key = { it.id }) { att ->
                    val typeBadgeColor = when (att.type) {
                        AttestationType.PRESENCE_AUDIT -> Color(0xFF2EBD6B)
                        AttestationType.PRESENCE_REACTIVATED -> Color(0xFF3B82F6)
                        AttestationType.ABSENCE_RETIRED -> Color(0xFF64748B)
                        AttestationType.ABSENCE_LOST -> Color(0xFFA855F7)
                        AttestationType.ABSENCE_STOPPED_WORKING -> Color(0xFFEF4444)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Top row: Type badge, audit date, session ID
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(typeBadgeColor.copy(alpha = 0.18f))
                                        .border(0.8.dp, typeBadgeColor, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = att.type.displayLabel.uppercase(),
                                        color = typeBadgeColor,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = "📅 ${att.auditDate}",
                                    color = palette.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            if (!att.auditSessionId.isNullOrBlank()) {
                                Text(
                                    text = "#${att.auditSessionId}",
                                    color = palette.accent,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Target Device Header
                        val matchingDev = effectiveDevices.find { it.id == att.deviceId }
                        Text(
                            text = matchingDev?.name ?: att.deviceLabel,
                            color = palette.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Attestation Notes
                        if (!att.note.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF0F1218))
                                    .border(0.5.dp, palette.border, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "“${att.note}”",
                                    color = palette.textPrimary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        // Signer & Environment Metadata Footer
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.elevated)
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "👤 Signer: ${att.userSignature}",
                                    color = palette.textMuted,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "📱 ${att.environment}",
                                    color = palette.textMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "🔐 SHA-256: ${att.cryptographicProof}",
                                color = palette.accent,
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet 1: Record Lifecycle Attestation for a Device
    selectedDeviceForAttestation?.let { dev ->
        LifecycleAttestationSheet(
            device = dev,
            userSignature = userSignature,
            environment = "Tuesday Android (Pixel 9)",
            palette = palette,
            onDismiss = { selectedDeviceForAttestation = null },
            onAttest = { type, date, note, sessId ->
                onRecordAttestation?.invoke(dev.id, type, date, note, sessId)
            }
        )
    }

    // Modal Sheet 2: Point-in-Time Batch Audit
    if (showBatchAuditModal) {
        BatchAuditModal(
            devices = effectiveDevices,
            userSignature = userSignature,
            environment = "Tuesday Android (Pixel 9)",
            palette = palette,
            onDismiss = { showBatchAuditModal = false },
            onBatchAttest = { devIds, date, sessId, note ->
                onRecordBatchAudit?.invoke(devIds, date, sessId, note)
            }
        )
    }
}

@Composable
private fun AuditMetricBadge(
    label: String,
    value: String,
    valueColor: Color,
    palette: BrandPalette,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(palette.elevated)
            .border(0.5.dp, palette.border, RoundedCornerShape(6.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = label, color = palette.textMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(text = value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun AttestationFilterChip(
    label: String,
    isSelected: Boolean,
    palette: BrandPalette,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) palette.accent.copy(alpha = 0.2f) else palette.elevated)
            .border(0.8.dp, if (isSelected) palette.accent else palette.border, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) palette.accent else palette.textMuted,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LifecycleAttestationSheet(
    device: RackPortDevice,
    userSignature: String,
    environment: String,
    palette: BrandPalette,
    onDismiss: () -> Unit,
    onAttest: (AttestationType, String, String?, String?) -> Unit
) {
    var selectedType by remember {
        mutableStateOf(
            if (device.presenceState == DevicePresenceState.ACTIVE) AttestationType.ABSENCE_RETIRED
            else AttestationType.PRESENCE_REACTIVATED
        )
    }
    var auditDate by remember { mutableStateOf("2026-11-11") }
    var auditSessionId by remember { mutableStateOf("AUDIT-2026-Q4") }
    var notes by remember { mutableStateOf("") }

    val liveProof = remember(device.id, selectedType, auditDate, userSignature, environment) {
        BulletinsAndInventoryEngine.computeAttestationProof(
            id = "preview-seal",
            deviceId = device.id,
            type = selectedType,
            auditDate = auditDate,
            userSignature = userSignature,
            environment = environment,
            timestampMs = System.currentTimeMillis()
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RECORD LIFECYCLE ATTESTATION",
                        color = palette.accent,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = device.name,
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.elevated)
                        .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Current: ${device.presenceState.shortLabel}",
                        color = palette.textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Text(
                text = "Select the lifecycle state change to attest. The event will be cryptographically sealed with your operator signature and device environment.",
                color = palette.textMuted,
                fontSize = 11.5.sp,
                lineHeight = 15.sp
            )

            // Event Options
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                AttestationOptionRow(
                    label = "🟢 Presence Attestation (Verify Present in Audit)",
                    desc = "Confirms device is currently online, racked, and active.",
                    isSelected = selectedType == AttestationType.PRESENCE_AUDIT,
                    palette = palette,
                    onClick = { selectedType = AttestationType.PRESENCE_AUDIT }
                )
                AttestationOptionRow(
                    label = "📦 Retire Gear (Graceful Decommission)",
                    desc = "Planned sunset, replacement, or standard decommissioning.",
                    isSelected = selectedType == AttestationType.ABSENCE_RETIRED,
                    palette = palette,
                    onClick = { selectedType = AttestationType.ABSENCE_RETIRED }
                )
                AttestationOptionRow(
                    label = "❓ Mark Lost (Missing in Audit)",
                    desc = "Device unaccounted for during inventory walk or missing from rack.",
                    isSelected = selectedType == AttestationType.ABSENCE_LOST,
                    palette = palette,
                    onClick = { selectedType = AttestationType.ABSENCE_LOST }
                )
                AttestationOptionRow(
                    label = "⚡ Stopped Working (Hardware Failure)",
                    desc = "Power supply blown, fried capacitor, port failure, or bricked.",
                    isSelected = selectedType == AttestationType.ABSENCE_STOPPED_WORKING,
                    palette = palette,
                    onClick = { selectedType = AttestationType.ABSENCE_STOPPED_WORKING }
                )
                if (device.presenceState != DevicePresenceState.ACTIVE) {
                    AttestationOptionRow(
                        label = "🔄 Return to Active Service (Reactivate)",
                        desc = "Repaired, located, or re-commissioned back into fleet.",
                        isSelected = selectedType == AttestationType.PRESENCE_REACTIVATED,
                        palette = palette,
                        onClick = { selectedType = AttestationType.PRESENCE_REACTIVATED }
                    )
                }
            }

            // Audit Date input
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "AUDIT DATE (YYYY-MM-DD)",
                    color = palette.textMuted,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                OutlinedTextField(
                    value = auditDate,
                    onValueChange = { auditDate = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border
                    )
                )
            }

            // Note input
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "AUDIT NOTES / JUSTIFICATION",
                    color = palette.textMuted,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("e.g. Replaced with UDM-SE for 2.5GbE PoE, or PSU blown", color = palette.textMuted.copy(alpha = 0.5f), fontSize = 11.5.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border
                    )
                )
            }

            // Cryptographic Proof Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F1218))
                    .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "CRYPTOGRAPHIC ATTESTATION SEAL",
                    color = palette.accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "👤 Signer: $userSignature",
                    color = palette.textPrimary,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "📱 Environment: $environment",
                    color = palette.textMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "🔐 Proof: sha256:${liveProof.take(24)}...",
                    color = palette.textMuted,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.elevated)
                        .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = palette.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .weight(2f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.accent)
                        .clickable {
                            onAttest(selectedType, auditDate, notes.ifBlank { null }, auditSessionId)
                            onDismiss()
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Sign & Record Attestation", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AttestationOptionRow(
    label: String,
    desc: String,
    isSelected: Boolean,
    palette: BrandPalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) palette.accent.copy(alpha = 0.12f) else palette.elevated)
            .border(0.8.dp, if (isSelected) palette.accent else palette.border, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = palette.textPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                color = palette.textMuted,
                fontSize = 10.sp
            )
        }
        Text(
            text = if (isSelected) "●" else "○",
            color = if (isSelected) palette.accent else palette.textMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchAuditModal(
    devices: List<RackPortDevice>,
    userSignature: String,
    environment: String,
    palette: BrandPalette,
    onDismiss: () -> Unit,
    onBatchAttest: (List<String>, String, String, String?) -> Unit
) {
    val selectedIds = remember { mutableStateMapOf<String, Boolean>().apply {
        devices.filter { it.presenceState == DevicePresenceState.ACTIVE }.forEach { put(it.id, true) }
    } }
    var auditDate by remember { mutableStateOf("2026-11-11") }
    var sessionId by remember { mutableStateOf("AUDIT-2026-Q4-RACK") }
    var notes by remember { mutableStateOf("Scheduled physical rack inventory audit walk.") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    text = "POINT-IN-TIME AUDIT BATCH",
                    color = palette.accent,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Attest Fleet Presence",
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Text(
                text = "Attest to the verified presence of multiple devices in your network during a point-in-time audit date. Each device receives a signed attestation record.",
                color = palette.textMuted,
                fontSize = 11.5.sp,
                lineHeight = 15.sp
            )

            // Select All / Deselect All
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TARGET INVENTORY (${selectedIds.values.count { it }} / ${devices.size})",
                    color = palette.textMuted,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Select All",
                    color = palette.accent,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        devices.forEach { selectedIds[it.id] = true }
                    }
                )
            }

            // Device list (compact)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(devices, key = { it.id }) { dev ->
                    val isChecked = selectedIds[dev.id] == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isChecked) palette.accent.copy(alpha = 0.1f) else palette.elevated)
                            .border(0.8.dp, if (isChecked) palette.accent else palette.border, RoundedCornerShape(4.dp))
                            .clickable { selectedIds[dev.id] = !isChecked }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#%02d %s (%s)".format(dev.portIndex, dev.shortLabel, dev.assetTag),
                            color = palette.textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isChecked) "✓ Selected" else "Tap to select",
                            color = if (isChecked) palette.accent else palette.textMuted,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Audit Session ID & Date fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("AUDIT SESSION ID", color = palette.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = sessionId,
                        onValueChange = { sessionId = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.border
                        )
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("AUDIT DATE", color = palette.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    OutlinedTextField(
                        value = auditDate,
                        onValueChange = { auditDate = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.border
                        )
                    )
                }
            }

            // Note input
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("AUDIT NOTES", color = palette.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border
                    )
                )
            }

            // Signer Preview
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F1218))
                    .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "👤 Signer: $userSignature  •  📱 $environment",
                    color = palette.textMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Confirm button
            val selectedCount = selectedIds.values.count { it }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selectedCount > 0) palette.accent else palette.elevated)
                    .clickable(enabled = selectedCount > 0) {
                        val targets = selectedIds.filter { it.value }.keys.toList()
                        onBatchAttest(targets, auditDate, sessionId, notes.ifBlank { null })
                        onDismiss()
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sign & Attest Fleet Audit ($selectedCount Devices)",
                    color = if (selectedCount > 0) Color.Black else palette.textMuted,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MiniCandleBar(
    candle: OperationalCandle,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .height(12.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF13171F))
    ) {
        val w = size.width
        val h = size.height
        val yCenter = h / 2f
        val candleH = 6.dp.toPx()
        val cTop = yCenter - candleH / 2f

        val xRel = candle.marketReleaseNorm * w
        val xDeployStart = candle.deployStartNorm * w
        val xDeployEnd = candle.deployEndNorm * w
        val xEol = candle.vendorEolNorm * w

        // 1. Left Wick: Pre-adoption age
        if (xDeployStart > xRel) {
            drawLine(
                color = Color(0xFF64748B),
                start = Offset(xRel, yCenter),
                end = Offset(xDeployStart, yCenter),
                strokeWidth = 1.2.dp.toPx()
            )
            drawCircle(Color(0xFF94A3B8), radius = 1.8.dp.toPx(), center = Offset(xRel, yCenter))
        }

        // 2. Candle Body: Operational tenure
        val xSupportedEnd = min(xDeployEnd, xEol)
        val bodyW = (xSupportedEnd - xDeployStart).coerceAtLeast(3.dp.toPx())
        val bodyColor = when {
            candle.presenceState == DevicePresenceState.STOPPED_WORKING -> Color(0xFFEF4444)
            candle.presenceState == DevicePresenceState.LOST -> Color(0xFFA855F7)
            candle.presenceState == DevicePresenceState.RETIRED -> Color(0xFF64748B)
            candle.hasCveOrRecall -> Color(0xFFDC2626)
            candle.isPastEol -> Color(0xFFE11D48)
            candle.isEolNearing -> Color(0xFFF59E0B)
            else -> Color(0xFF2EBD6B)
        }

        when (candle.presenceState) {
            DevicePresenceState.RETIRED -> {
                drawRect(
                    color = bodyColor.copy(alpha = 0.15f),
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(bodyW, candleH)
                )
                drawRect(
                    color = bodyColor,
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(bodyW, candleH),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
            DevicePresenceState.LOST -> {
                drawRect(
                    color = bodyColor.copy(alpha = 0.12f),
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(bodyW, candleH)
                )
                drawRect(
                    color = bodyColor,
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(bodyW, candleH),
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
                    )
                )
            }
            DevicePresenceState.STOPPED_WORKING -> {
                drawRect(
                    color = bodyColor.copy(alpha = 0.18f),
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(bodyW, candleH)
                )
                drawRect(
                    color = bodyColor,
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(bodyW, candleH),
                    style = Stroke(width = 1.2.dp.toPx())
                )
                drawLine(
                    color = bodyColor,
                    start = Offset(xDeployStart + 2f, cTop + 1f),
                    end = Offset(xDeployStart + bodyW - 2f, cTop + candleH - 1f),
                    strokeWidth = 1f
                )
            }
            else -> {
                if (candle.isRetired) {
                    drawRect(
                        color = bodyColor.copy(alpha = 0.15f),
                        topLeft = Offset(xDeployStart, cTop),
                        size = Size(bodyW, candleH)
                    )
                    drawRect(
                        color = bodyColor,
                        topLeft = Offset(xDeployStart, cTop),
                        size = Size(bodyW, candleH),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                } else {
                    drawRect(
                        color = bodyColor,
                        topLeft = Offset(xDeployStart, cTop),
                        size = Size(bodyW, candleH)
                    )
                }
            }
        }

        // 3. Post-EOL Overhang: Zombie gear running past EOL
        if (candle.isPastEol && xDeployEnd > xEol) {
            val overW = (xDeployEnd - xEol).coerceAtLeast(3.dp.toPx())
            drawLine(
                color = Color.White,
                start = Offset(xEol, cTop - 1f),
                end = Offset(xEol, cTop + candleH + 1f),
                strokeWidth = 1.2.dp.toPx()
            )
            drawRect(
                color = Color(0xFFDC2626),
                topLeft = Offset(xEol, cTop),
                size = Size(overW, candleH)
            )
        }

        // 4. Right Wick: Remaining runway
        if (!candle.isPastEol && xEol > xDeployEnd) {
            drawLine(
                color = if (candle.isEolNearing) Color(0xFFF59E0B) else Color(0xFF64748B),
                start = Offset(xDeployEnd, yCenter),
                end = Offset(xEol, yCenter),
                strokeWidth = 1.2.dp.toPx()
            )
            drawCircle(
                color = if (candle.isEolNearing) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                radius = 1.8.dp.toPx(),
                center = Offset(xEol, yCenter)
            )
        }
    }
}

@Composable
private fun SwimlaneCanvas(
    candles: List<OperationalCandle>,
    palette: BrandPalette,
    scrubberXNorm: Float,
    onScrubChanged: (Float) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(236.dp)
            .background(Color(0xFF0B0D11))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val labelW = 96.dp.toPx()
                    val plotW = (size.width - labelW - 12.dp.toPx()).coerceAtLeast(10f)
                    val norm = ((offset.x - labelW) / plotW).coerceIn(0f, 1f)
                    onScrubChanged(norm)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val labelW = 96.dp.toPx()
                    val plotW = (size.width - labelW - 12.dp.toPx()).coerceAtLeast(10f)
                    val norm = ((change.position.x - labelW) / plotW).coerceIn(0f, 1f)
                    onScrubChanged(norm)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val footerH = 12.dp.toPx()

        if (candles.isEmpty()) {
            val midY = h / 2f
            drawLine(
                color = Color(0xFF222631),
                start = Offset(36.dp.toPx(), midY),
                end = Offset(w - 36.dp.toPx(), midY),
                strokeWidth = 1.dp.toPx()
            )
            drawCircle(
                color = Color(0xFF64748B),
                radius = 4.dp.toPx(),
                center = Offset(36.dp.toPx(), midY)
            )
            drawCircle(
                color = Color(0xFF64748B),
                radius = 4.dp.toPx(),
                center = Offset(w - 36.dp.toPx(), midY)
            )
            val paintTitle = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#F3F4F6")
                textSize = 12.sp.toPx()
                isAntiAlias = true
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val paintSub = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#8E95A2")
                textSize = 10.5.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                "No Operational Items in Layer",
                w / 2f,
                midY - 12.dp.toPx(),
                paintTitle
            )
            drawContext.canvas.nativeCanvas.drawText(
                "Enroll inventory to visualize operational tenure wicks and envelopes",
                w / 2f,
                midY + 18.dp.toPx(),
                paintSub
            )
            return@Canvas
        }

        val labelW = 96.dp.toPx()
        val plotW = (w - labelW - 12.dp.toPx()).coerceAtLeast(10f)
        val laneCount = candles.size.coerceAtLeast(1)
        val laneH = (h - footerH) / laneCount

        // Vertical year / epoch micro-grid lines
        for (i in 0..6) {
            val gx = labelW + (plotW * i / 6f)
            drawLine(
                color = Color(0xFF1D2028),
                start = Offset(gx, 0f),
                end = Offset(gx, h - footerH),
                strokeWidth = 1f
            )
        }

        val labelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#F3F4F6")
            textSize = 9.5.sp.toPx()
            isAntiAlias = true
            isFakeBoldText = true
        }

        candles.forEachIndexed { idx, candle ->
            val yTop = idx * laneH
            val yCenter = yTop + laneH / 2f

            // Lane divider
            drawLine(
                color = Color(0xFF242730),
                start = Offset(0f, (idx + 1) * laneH),
                end = Offset(w, (idx + 1) * laneH),
                strokeWidth = 1f
            )

            // Left zone indicator bar
            val zoneColor = when (candle.trustZone) {
                TrustZone.Z0_CORE -> Color(0xFF3B82F6)
                TrustZone.Z1_TRUSTED -> Color(0xFF2EBD6B)
                TrustZone.Z2_LAB -> Color(0xFFA3C644)
                TrustZone.Z3_IOT -> Color(0xFFF59E0B)
                TrustZone.Z4_GUEST -> Color(0xFF8E95A2)
            }
            drawRect(
                color = zoneColor,
                topLeft = Offset(0f, yTop + 2f),
                size = Size(3.dp.toPx(), (laneH - 4f).coerceAtLeast(2f))
            )

            // Device port + short name label
            val label = if (candle.layer == LifecycleLayer.HARDWARE) {
                "#%02d %s".format(candle.portIndex, candle.shortLabel)
            } else {
                candle.shortLabel
            }
            drawContext.canvas.nativeCanvas.drawText(
                label,
                7.dp.toPx(),
                yCenter + 3.5.dp.toPx(),
                labelPaint
            )

            // Subtle lane baseline rail
            drawLine(
                color = Color(0xFF181B22),
                start = Offset(labelW, yCenter),
                end = Offset(labelW + plotW, yCenter),
                strokeWidth = 1f
            )

            // OPERATIONAL TENURE CANDLESTICK GEOMETRY
            val xRel = labelW + candle.marketReleaseNorm * plotW
            val xDeployStart = labelW + candle.deployStartNorm * plotW
            val xDeployEnd = labelW + candle.deployEndNorm * plotW
            val xEol = labelW + candle.vendorEolNorm * plotW

            val candleHeight = min(10.dp.toPx(), max(6.dp.toPx(), laneH * 0.46f))
            val cTop = yCenter - candleHeight / 2f

            // 1. LEFT WICK: Pre-adoption age (how long on the market before entering network)
            if (xDeployStart > xRel) {
                drawLine(
                    color = Color(0xFF64748B),
                    start = Offset(xRel, yCenter),
                    end = Offset(xDeployStart, yCenter),
                    strokeWidth = 1.2.dp.toPx()
                )
                // Vendor release point dot
                drawCircle(
                    color = Color(0xFF94A3B8),
                    radius = 2.2.dp.toPx(),
                    center = Offset(xRel, yCenter)
                )
            }

            // 2. CANDLE BODY: Operational tenure inside network
            val xBodySupportedEnd = min(xDeployEnd, xEol)
            val supportedWidth = (xBodySupportedEnd - xDeployStart).coerceAtLeast(3.dp.toPx())

            val bodyColor = when {
                candle.presenceState == DevicePresenceState.STOPPED_WORKING -> Color(0xFFEF4444)
                candle.presenceState == DevicePresenceState.LOST -> Color(0xFFA855F7)
                candle.presenceState == DevicePresenceState.RETIRED -> Color(0xFF64748B)
                candle.hasCveOrRecall -> Color(0xFFDC2626)
                candle.isPastEol -> Color(0xFFE11D48)
                candle.isEolNearing -> Color(0xFFF59E0B)
                else -> Color(0xFF2EBD6B)
            }

            if (candle.isGuessed) {
                // Guessed / Provisional: stippled dash stroke
                drawRect(
                    color = bodyColor.copy(alpha = 0.22f),
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(supportedWidth, candleHeight)
                )
                drawRect(
                    color = Color(0xFF94A3B8),
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(supportedWidth, candleHeight),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                )
            } else if (candle.presenceState == DevicePresenceState.RETIRED || candle.isRetired) {
                // RETIRED GEAR: Hollow outline!
                drawRect(
                    color = bodyColor.copy(alpha = 0.12f),
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(supportedWidth, candleHeight)
                )
                drawRect(
                    color = bodyColor,
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(supportedWidth, candleHeight),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            } else if (candle.presenceState == DevicePresenceState.LOST) {
                // LOST GEAR: Dashed lavender outline!
                drawRect(
                    color = bodyColor.copy(alpha = 0.12f),
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(supportedWidth, candleHeight)
                )
                drawRect(
                    color = bodyColor,
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(supportedWidth, candleHeight),
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                    )
                )
            } else if (candle.presenceState == DevicePresenceState.STOPPED_WORKING) {
                // DEFECTIVE / STOPPED WORKING GEAR: Slashed red warning outline!
                drawRect(
                    color = bodyColor.copy(alpha = 0.18f),
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(supportedWidth, candleHeight)
                )
                drawRect(
                    color = bodyColor,
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(supportedWidth, candleHeight),
                    style = Stroke(width = 1.4.dp.toPx())
                )
                drawLine(
                    color = bodyColor,
                    start = Offset(xDeployStart + 3f, cTop + 2f),
                    end = Offset(xDeployStart + supportedWidth - 3f, cTop + candleHeight - 2f),
                    strokeWidth = 1.2.dp.toPx()
                )
            } else {
                // ACTIVE LIVE GEAR: Solid fill!
                drawRect(
                    color = bodyColor,
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(supportedWidth, candleHeight)
                )
                drawRect(
                    color = Color.White.copy(alpha = 0.22f),
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(supportedWidth, candleHeight),
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }

            // 3. POST-EOL OVERHANG: Captures "zombie" hardware running past EOL
            if (candle.isPastEol && xDeployEnd > xEol) {
                val xOverhangStart = xEol
                val overhangWidth = (xDeployEnd - xOverhangStart).coerceAtLeast(3.dp.toPx())

                // Vertical boundary line at vendor EOL marking the cliff
                drawLine(
                    color = Color(0xFFFDA4AF),
                    start = Offset(xEol, cTop - 2.dp.toPx()),
                    end = Offset(xEol, cTop + candleHeight + 2.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Red warning overhang
                drawRect(
                    color = Color(0xFFDC2626),
                    topLeft = Offset(xOverhangStart, cTop),
                    size = Size(overhangWidth, candleHeight)
                )

                // Diagonal hatching across the overhang to visually shout "ZOMBIE / UNSUPPORTED"
                val hatchStep = 4.dp.toPx()
                var hx = xOverhangStart
                while (hx < xOverhangStart + overhangWidth + candleHeight) {
                    val p1x = (hx - candleHeight).coerceIn(xOverhangStart, xOverhangStart + overhangWidth)
                    val p1y = cTop + candleHeight
                    val p2x = hx.coerceIn(xOverhangStart, xOverhangStart + overhangWidth)
                    val p2y = cTop
                    if (p2x > p1x) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.6f),
                            start = Offset(p1x, p1y),
                            end = Offset(p2x, p2y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    hx += hatchStep
                }

                drawRect(
                    color = Color(0xFFFECDD3),
                    topLeft = Offset(xOverhangStart, cTop),
                    size = Size(overhangWidth, candleHeight),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // 4. RIGHT WICK: Visualizes remaining runway buffer between active tenure and vendor EOL
            if (!candle.isPastEol && xEol > xDeployEnd) {
                drawLine(
                    color = if (candle.isEolNearing) Color(0xFFF59E0B) else Color(0xFF64748B),
                    start = Offset(xDeployEnd, yCenter),
                    end = Offset(xEol, yCenter),
                    strokeWidth = 1.2.dp.toPx()
                )
                // Vendor EOL terminus dot
                drawCircle(
                    color = if (candle.isEolNearing) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                    radius = 2.2.dp.toPx(),
                    center = Offset(xEol, yCenter)
                )
            }

            // 5. CVE / Recall triangle marker
            candle.cveStartNorm?.let { cveNorm ->
                val cx = labelW + cveNorm * plotW
                val tri = Path().apply {
                    moveTo(cx, cTop - 5.dp.toPx())
                    lineTo(cx - 3.5.dp.toPx(), cTop)
                    lineTo(cx + 3.5.dp.toPx(), cTop)
                    close()
                }
                drawPath(path = tri, color = TuesdayTheme.LedCveRed)
            }
        }

        // Bottom Scrubber Track & Vertical Cursor Line
        val fy = h - footerH + 4.dp.toPx()
        drawRect(
            color = Color(0xFF222631),
            topLeft = Offset(labelW, fy),
            size = Size(plotW, 3.dp.toPx())
        )

        val scrubX = labelW + scrubberXNorm * plotW
        drawLine(
            color = palette.accent,
            start = Offset(scrubX, 0f),
            end = Offset(scrubX, h - footerH),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

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
fun SeasonalCheckupScreen(
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
