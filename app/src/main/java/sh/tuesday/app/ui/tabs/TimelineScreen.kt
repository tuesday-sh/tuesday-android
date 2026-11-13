package sh.tuesday.app.ui.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.domain.TimelineStatusEvaluator
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.AttestationType
import sh.tuesday.app.models.BulletinsAndInventoryEngine
import sh.tuesday.app.models.DeviceAttestation
import sh.tuesday.app.models.DevicePresenceState
import sh.tuesday.app.models.OperationalCandle
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.models.RackPortDevice
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayDimens
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.components.CondensedCandlestickCard
import sh.tuesday.app.ui.components.CondensedMiniCandleBar
import sh.tuesday.app.ui.components.HelpIconButton
import sh.tuesday.app.ui.components.MonospaceBadge
import sh.tuesday.app.ui.components.SelectableFilterChip
import sh.tuesday.app.ui.components.TuesdayBottomSheet
import sh.tuesday.app.ui.components.TuesdayTextField
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    flavor: AppBrandFlavor,
    palette: BrandPalette,
    devices: List<RackPortDevice>,
    attestations: List<DeviceAttestation> = emptyList(),
    initialModal: String? = null,
    onConsumeInitialModal: () -> Unit = {},
    onOpenReferenceGuide: () -> Unit = {},
    onInspectDevice: (String) -> Unit = {},
    onRecordAttestation: ((deviceId: String, type: AttestationType, auditDate: String, note: String?, sessionId: String?) -> DeviceAttestation)? = null,
    onRecordBatchAudit: ((deviceIds: List<String>, auditDate: String, sessionId: String, note: String?) -> List<DeviceAttestation>)? = null
) {
    val isStn = flavor == AppBrandFlavor.SURETHING_HOME
    var scrubberXNorm by remember { mutableFloatStateOf(0.72f) }
    var zoomWidthDp by remember { mutableStateOf<Int?>(null) }
    val hScroll = rememberScrollState()

    LaunchedEffect(zoomWidthDp, hScroll.maxValue) {
        scrubberXNorm = 0.72f
        if (zoomWidthDp != null && hScroll.maxValue > 0) {
            val targetScroll = (0.72f * hScroll.maxValue).toInt().coerceIn(0, hScroll.maxValue)
            hScroll.animateScrollTo(targetScroll)
        }
    }
    val retiredOverrides = remember { mutableStateMapOf<String, Boolean>() }
    var selectedDeviceForAttestation by remember { mutableStateOf<RackPortDevice?>(null) }
    var showBatchAuditModal by remember { mutableStateOf(false) }

    val effectiveDevices = devices

    LaunchedEffect(initialModal, effectiveDevices) {
        when (initialModal) {
            "checkin", "check-in" -> {
                selectedDeviceForAttestation = null
                showBatchAuditModal = true
                onConsumeInitialModal()
            }
            "status", "update-status" -> {
                showBatchAuditModal = false
                selectedDeviceForAttestation = effectiveDevices.firstOrNull()
                onConsumeInitialModal()
            }
            "none", "close" -> {
                showBatchAuditModal = false
                selectedDeviceForAttestation = null
                onConsumeInitialModal()
            }
        }
    }

    val sortedDevices = remember(effectiveDevices) {
        BulletinsAndInventoryEngine.sortDevicesForTimeline(effectiveDevices)
    }

    val currentCandles = remember(sortedDevices, retiredOverrides.toMap(), scrubberXNorm) {
        sortedDevices.map { dev ->
            val isRet = retiredOverrides[dev.id] ?: dev.isRetired
            BulletinsAndInventoryEngine.toOperationalCandle(
                device = dev.copy(isRetired = isRet),
                referenceNowNorm = scrubberXNorm
            )
        }
    }

    val cursorDateStr = TimelineStatusEvaluator.formatScrubberCursorDate(scrubberXNorm)
    val okColor = TuesdayTheme.ledColorFor(PortLifecycleState.OK, isStn)
    val cveColor = TuesdayTheme.ledColorFor(PortLifecycleState.CVE_KEV, isStn)
    val eolColor = TuesdayTheme.ledColorFor(PortLifecycleState.EOL, isStn)
    val retiredColor = TuesdayTheme.presenceColorFor(DevicePresenceState.RETIRED, isStn)
    val lostColor = TuesdayTheme.presenceColorFor(DevicePresenceState.LOST, isStn)
    val defectColor = TuesdayTheme.presenceColorFor(DevicePresenceState.STOPPED_WORKING, isStn)
    val overhangColor = TuesdayTheme.overhangColor(isStn)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .uiComponent("SwimlaneTimelineScreen")
    ) {
        // Top 1/3: Edge-to-edge 2D Candlestick Lifespan Canvas with pinned left device-name column
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.34f)
                .background(palette.chassisRecessed)
        ) {
            if (currentCandles.isEmpty()) {
                SwimlaneCanvas(
                    candles = emptyList(),
                    palette = palette,
                    scrubberXNorm = scrubberXNorm,
                    onScrubChanged = { scrubberXNorm = it },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val vScroll = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vScroll)
                ) {
                    SwimlaneLabelsColumn(
                        candles = currentCandles,
                        palette = palette
                    )
                    val scrollModifier = if (zoomWidthDp != null) {
                        Modifier.weight(1f).horizontalScroll(hScroll)
                    } else {
                        Modifier.weight(1f)
                    }
                    Box(modifier = scrollModifier) {
                        SwimlaneCanvas(
                            candles = currentCandles,
                            palette = palette,
                            scrubberXNorm = scrubberXNorm,
                            onScrubChanged = { scrubberXNorm = it },
                            drawLabels = false,
                            modifier = if (zoomWidthDp == null) Modifier.fillMaxWidth() else Modifier.width(zoomWidthDp!!.dp)
                        )
                    }
                }
            }
        }

        // Bottom 2/3: Vertically scrollable content
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.66f)
                .padding(horizontal = TuesdayDimens.ScreenPaddingHorizontal),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .uiSlot("timeline-header-card"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val earliestRelease = currentCandles.mapNotNull { it.releaseDateStr }.minOrNull()
                    val latestEol = currentCandles.mapNotNull { it.eolDateStr }.maxOrNull()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                "All time" to null,
                                "1yr" to 560,
                                "6mo" to 840
                            ).forEach { (label, wDp) ->
                                SelectableFilterChip(
                                    label = label,
                                    selected = zoomWidthDp == wDp,
                                    onClick = { zoomWidthDp = wDp },
                                    palette = palette
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (zoomWidthDp == null && (earliestRelease != null || latestEol != null)) {
                                Text(
                                    text = "${earliestRelease ?: "—"} → ${latestEol ?: "—"}",
                                    color = palette.textMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Text(
                                    text = cursorDateStr,
                                    color = palette.textMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Box(modifier = Modifier.uiSlot("help-button")) {
                                HelpIconButton(
                                    palette = palette,
                                    onClick = onOpenReferenceGuide,
                                    modifier = Modifier.uiAction("openReferenceGuide"),
                                    contentDescriptionText = "Timeline Reference Guide"
                                )
                            }
                        }
                    }

                    // Visual Grammar Legend (All 6 States)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                            .background(palette.elevated)
                            .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusBadge))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Legend:",
                            color = palette.accentText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(width = 8.dp, height = 6.dp).background(okColor))
                                Text("Solid: Active", color = palette.textMuted, fontSize = 11.sp)
                            }
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(width = 8.dp, height = 6.dp).border(1.dp, retiredColor))
                                Text("Hollow: Retired", color = palette.textMuted, fontSize = 11.sp)
                            }
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(width = 8.dp, height = 6.dp).border(1.dp, lostColor))
                                Text("Dashed: Lost", color = lostColor, fontSize = 11.sp)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(width = 8.dp, height = 6.dp).background(defectColor))
                                Text("Slash: Defect", color = defectColor, fontSize = 11.sp)
                            }
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(width = 8.dp, height = 6.dp).background(eolColor))
                                Text("Amber: EOL Nears", color = palette.textMuted, fontSize = 11.sp)
                            }
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(width = 8.dp, height = 6.dp).background(overhangColor))
                                Text("Hatched: Past EOL", color = cveColor, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            if (currentCandles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(TuesdayDimens.RadiusCard))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusCard))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hardware devices tracked in your inventory yet.",
                            color = palette.textMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(currentCandles, key = { "${it.layer}-${it.id}" }) { candle ->
                    val currentYearPrefix = remember { java.time.LocalDate.now().year.toString() }
                    val statusSummary = remember(candle, attestations, currentYearPrefix) {
                        TimelineStatusEvaluator.evaluateCandleStatus(
                            candle = candle,
                            attestations = attestations,
                            referenceYearPrefix = currentYearPrefix
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(TuesdayDimens.RadiusCard))
                                .background(palette.surface)
                                .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusCard))
                                .clickable { onInspectDevice(candle.id) }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = candle.name,
                                    color = palette.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                if (statusSummary.needsStatusCheck) {
                                    Box(
                                        modifier = Modifier
                                            .heightIn(min = 28.dp)
                                            .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                            .background(palette.accent.copy(alpha = 0.15f))
                                            .border(1.dp, palette.accent, RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                            .clickable {
                                                selectedDeviceForAttestation = effectiveDevices.find { it.id == candle.id }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Update Status",
                                            color = palette.accentText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            CondensedCandlestickCard(
                                candle = candle,
                                palette = palette,
                                showHeader = false
                            )
                        }
                        if (candle.hasCveOrRecall || candle.isPastEol || candle.isEolNearing) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (candle.hasCveOrRecall) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(cveColor)
                                    )
                                }
                                if (candle.isPastEol) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(cveColor)
                                    )
                                } else if (candle.isEolNearing) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(eolColor)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedDeviceForAttestation?.let { dev ->
        LifecycleAttestationSheet(
            device = dev,
            palette = palette,
            onDismiss = { selectedDeviceForAttestation = null },
            onAttest = { type, date, note, sessId ->
                onRecordAttestation?.invoke(dev.id, type, date, note, sessId)
            }
        )
    }

    if (showBatchAuditModal) {
        BatchAuditModal(
            devices = effectiveDevices,
            palette = palette,
            onDismiss = { showBatchAuditModal = false },
            onBatchAttest = { devIds, date, sessId, note ->
                onRecordBatchAudit?.invoke(devIds, date, sessId, note)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LifecycleAttestationSheet(
    device: RackPortDevice,
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
    val auditSessionId = "CHECKIN-2026-Q4"
    var notes by remember { mutableStateOf("") }

    TuesdayBottomSheet(
        palette = palette,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "RHYTHM CHECK-IN & LIFECYCLE",
                        color = palette.accentText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = device.name,
                        color = palette.textPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                MonospaceBadge(
                    text = "Current: ${device.presenceState.shortLabel}",
                    color = palette.textMuted,
                    fillAlpha = 0.12f
                )
            }

            Text(
                text = "Update this device's operational status. Your check-in updates the hardware lifecycle candlestick and keeps your seasonal rhythm posture accurate.",
                color = palette.textMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                AttestationOptionRow(
                    label = "🟢 Confirm Active (Check-In)",
                    desc = "Device is powered on, connected, and in active service.",
                    isSelected = selectedType == AttestationType.PRESENCE_AUDIT,
                    palette = palette,
                    onClick = { selectedType = AttestationType.PRESENCE_AUDIT }
                )
                AttestationOptionRow(
                    label = "📦 Retire Gear (Decommissioned)",
                    desc = "Planned sunset, replacement, or removed from active service.",
                    isSelected = selectedType == AttestationType.ABSENCE_RETIRED,
                    palette = palette,
                    onClick = { selectedType = AttestationType.ABSENCE_RETIRED }
                )
                AttestationOptionRow(
                    label = "❓ Mark Lost (Missing)",
                    desc = "Device unaccounted for during inventory check-in.",
                    isSelected = selectedType == AttestationType.ABSENCE_LOST,
                    palette = palette,
                    onClick = { selectedType = AttestationType.ABSENCE_LOST }
                )
                AttestationOptionRow(
                    label = "⚡ Stopped Working (Hardware Fault)",
                    desc = "Power supply failed, hardware defect, port failure, or bricked.",
                    isSelected = selectedType == AttestationType.ABSENCE_STOPPED_WORKING,
                    palette = palette,
                    onClick = { selectedType = AttestationType.ABSENCE_STOPPED_WORKING }
                )
                if (device.presenceState != DevicePresenceState.ACTIVE) {
                    AttestationOptionRow(
                        label = "🔄 Return to Active Service (Reactivate)",
                        desc = "Repaired, located, or returned to active service.",
                        isSelected = selectedType == AttestationType.PRESENCE_REACTIVATED,
                        palette = palette,
                        onClick = { selectedType = AttestationType.PRESENCE_REACTIVATED }
                    )
                }
            }

            TuesdayTextField(
                value = auditDate,
                onValueChange = { auditDate = it },
                label = "Status Date (YYYY-MM-DD)",
                palette = palette
            )

            TuesdayTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Check-In Notes (Optional)",
                placeholder = "e.g. Replaced with UDM-SE for 2.5GbE PoE, or PSU failed",
                palette = palette,
                singleLine = false
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = TuesdayDimens.HitTargetStandard)
                        .clip(RoundedCornerShape(TuesdayDimens.RadiusControl))
                        .background(palette.elevated)
                        .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusControl))
                        .clickable { onDismiss() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = palette.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .weight(2f)
                        .heightIn(min = TuesdayDimens.HitTargetStandard)
                        .clip(RoundedCornerShape(TuesdayDimens.RadiusControl))
                        .background(palette.accent)
                        .clickable {
                            onAttest(selectedType, auditDate, notes.ifBlank { null }, auditSessionId)
                            onDismiss()
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Save Lifecycle Status", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
            .background(if (isSelected) palette.accent.copy(alpha = 0.12f) else palette.elevated)
            .border(1.dp, if (isSelected) palette.accent else palette.border, RoundedCornerShape(TuesdayDimens.RadiusBadge))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                color = palette.textMuted,
                fontSize = 11.sp
            )
        }
        Text(
            text = if (isSelected) "●" else "○",
            color = if (isSelected) palette.accentText else palette.textMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchAuditModal(
    devices: List<RackPortDevice>,
    palette: BrandPalette,
    onDismiss: () -> Unit,
    onBatchAttest: (List<String>, String, String, String?) -> Unit
) {
    val selectedIds = remember {
        mutableStateMapOf<String, Boolean>().apply {
            devices.filter { it.presenceState == DevicePresenceState.ACTIVE }.forEach { put(it.id, true) }
        }
    }
    var auditDate by remember { mutableStateOf("2026-11-11") }
    val sessionId = "CHECKIN-2026-Q4-WALK"
    var notes by remember { mutableStateOf("Seasonal inventory check-in.") }

    TuesdayBottomSheet(
        palette = palette,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    text = "OPERATIONAL RHYTHM • CHECK-IN",
                    color = palette.accentText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Inventory Check-In",
                    color = palette.textPrimary,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Text(
                text = "Confirm which hardware devices are currently powered on and active in your network. Unchecked devices can be updated individually if retired or replaced.",
                color = palette.textMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE HARDWARE (${selectedIds.values.count { it }} / ${devices.size})",
                    color = palette.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .heightIn(min = 28.dp)
                        .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                        .clickable { devices.forEach { selectedIds[it.id] = true } }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select All",
                        color = palette.accentText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(devices, key = { it.id }) { dev ->
                    val isChecked = selectedIds[dev.id] == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = TuesdayDimens.HitTargetCompact)
                            .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                            .background(if (isChecked) palette.accent.copy(alpha = 0.1f) else palette.elevated)
                            .border(1.dp, if (isChecked) palette.accent else palette.border, RoundedCornerShape(TuesdayDimens.RadiusBadge))
                            .clickable { selectedIds[dev.id] = !isChecked }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${dev.category.code} • ${dev.shortLabel}",
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isChecked) "✓ Active" else "Tap to include",
                            color = if (isChecked) palette.accentText else palette.textMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            TuesdayTextField(
                value = auditDate,
                onValueChange = { auditDate = it },
                label = "Check-In Date (YYYY-MM-DD)",
                palette = palette
            )

            TuesdayTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Check-In Notes (Optional)",
                palette = palette,
                singleLine = false
            )

            val selectedCount = selectedIds.values.count { it }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TuesdayDimens.HitTargetStandard)
                    .clip(RoundedCornerShape(TuesdayDimens.RadiusControl))
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
                    text = "Complete Check-In ($selectedCount Devices)",
                    color = if (selectedCount > 0) Color.White else palette.textMuted,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MiniCandleBar(
    candle: OperationalCandle,
    modifier: Modifier = Modifier,
    showTodayMarker: Boolean = false
) {
    CondensedMiniCandleBar(
        candle = candle,
        palette = TuesdayTheme.colors,
        modifier = modifier,
        showTodayMarker = showTodayMarker
    )
}

@Composable
private fun SwimlaneLabelsColumn(
    candles: List<OperationalCandle>,
    palette: BrandPalette
) {
    val isStn = palette.isStn
    val canvasHeight = (candles.size * 20 + 22).coerceAtLeast(180).dp
    Canvas(
        modifier = Modifier
            .width(96.dp)
            .height(canvasHeight)
            .background(palette.chassisRecessed)
    ) {
        val footerH = 12.dp.toPx()
        val laneCount = candles.size.coerceAtLeast(1)
        val laneH = (size.height - footerH) / laneCount
        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = palette.textPrimary.toArgb()
            textSize = 10.sp.toPx()
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        candles.forEachIndexed { idx, candle ->
            val yTop = idx * laneH
            val yCenter = yTop + laneH / 2f
            val dotColor = when {
                candle.presenceState.isOutOfService -> TuesdayTheme.presenceColorFor(candle.presenceState, isStn)
                candle.hasCveOrRecall -> TuesdayTheme.ledColorFor(PortLifecycleState.CVE_KEV, isStn)
                candle.isPastEol -> TuesdayTheme.overhangColor(isStn)
                candle.isEolNearing -> TuesdayTheme.ledColorFor(PortLifecycleState.EOL, isStn)
                else -> TuesdayTheme.ledColorFor(PortLifecycleState.OK, isStn)
            }
            drawRect(
                color = dotColor,
                topLeft = Offset(0f, yTop + 2f),
                size = Size(3.dp.toPx(), (laneH - 4f).coerceAtLeast(2f))
            )
            drawContext.canvas.nativeCanvas.drawText(
                candle.shortLabel,
                7.dp.toPx(),
                yCenter + 3.5.dp.toPx(),
                labelPaint
            )
        }
    }
}

@Composable
private fun SwimlaneCanvas(
    candles: List<OperationalCandle>,
    palette: BrandPalette,
    scrubberXNorm: Float,
    onScrubChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
    drawLabels: Boolean = true
) {
    val isStn = palette.isStn
    val okColor = TuesdayTheme.ledColorFor(PortLifecycleState.OK, isStn)
    val cveColor = TuesdayTheme.ledColorFor(PortLifecycleState.CVE_KEV, isStn)
    val eolColor = TuesdayTheme.ledColorFor(PortLifecycleState.EOL, isStn)
    val overhangColor = TuesdayTheme.overhangColor(isStn)

    val canvasHeight = if (candles.isEmpty()) {
        104.dp
    } else {
        (candles.size * 20 + 22).coerceAtLeast(180).dp
    }
    Canvas(
        modifier = modifier
            .height(canvasHeight)
            .background(palette.chassisRecessed)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val labelW = if (drawLabels) 96.dp.toPx() else 0f
                    val plotW = (size.width - labelW - 12.dp.toPx()).coerceAtLeast(10f)
                    val norm = ((offset.x - labelW) / plotW).coerceIn(0f, 1f)
                    onScrubChanged(norm)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val labelW = if (drawLabels) 96.dp.toPx() else 0f
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
                color = palette.border,
                start = Offset(36.dp.toPx(), midY),
                end = Offset(w - 36.dp.toPx(), midY),
                strokeWidth = 1.dp.toPx()
            )
            drawCircle(
                color = palette.textMuted,
                radius = 4.dp.toPx(),
                center = Offset(36.dp.toPx(), midY)
            )
            drawCircle(
                color = palette.textMuted,
                radius = 4.dp.toPx(),
                center = Offset(w - 36.dp.toPx(), midY)
            )
            val paintTitle = android.graphics.Paint().apply {
                color = palette.textPrimary.toArgb()
                textSize = 12.sp.toPx()
                isAntiAlias = true
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val paintSub = android.graphics.Paint().apply {
                color = palette.textMuted.toArgb()
                textSize = 11.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                "No Hardware Devices in Inventory",
                w / 2f,
                midY - 12.dp.toPx(),
                paintTitle
            )
            drawContext.canvas.nativeCanvas.drawText(
                "Add a device or seed inventory to visualize hardware lifecycles",
                w / 2f,
                midY + 18.dp.toPx(),
                paintSub
            )
            return@Canvas
        }

        val labelW = if (drawLabels) 96.dp.toPx() else 0f
        val plotW = (w - labelW - 12.dp.toPx()).coerceAtLeast(10f)
        val laneCount = candles.size.coerceAtLeast(1)
        val laneH = (h - footerH) / laneCount

        for (i in 0..6) {
            val gx = labelW + (plotW * i / 6f)
            drawLine(
                color = palette.border.copy(alpha = 0.6f),
                start = Offset(gx, 0f),
                end = Offset(gx, h - footerH),
                strokeWidth = 1f
            )
        }

        val labelPaint = android.graphics.Paint().apply {
            color = palette.textPrimary.toArgb()
            textSize = 10.sp.toPx()
            isAntiAlias = true
            isFakeBoldText = true
        }

        candles.forEachIndexed { idx, candle ->
            val yTop = idx * laneH
            val yCenter = yTop + laneH / 2f

            drawLine(
                color = palette.border,
                start = Offset(0f, (idx + 1) * laneH),
                end = Offset(w, (idx + 1) * laneH),
                strokeWidth = 1f
            )

            val zoneColor = TuesdayTheme.zoneColorFor(candle.trustZone, isStn)
            if (drawLabels) {
                drawRect(
                    color = zoneColor,
                    topLeft = Offset(0f, yTop + 2f),
                    size = Size(3.dp.toPx(), (laneH - 4f).coerceAtLeast(2f))
                )
                drawContext.canvas.nativeCanvas.drawText(
                    candle.shortLabel,
                    7.dp.toPx(),
                    yCenter + 3.5.dp.toPx(),
                    labelPaint
                )
            }

            drawLine(
                color = palette.border.copy(alpha = 0.45f),
                start = Offset(labelW, yCenter),
                end = Offset(labelW + plotW, yCenter),
                strokeWidth = 1f
            )

            val xRel = labelW + candle.marketReleaseNorm * plotW
            val xDeployStart = labelW + candle.deployStartNorm * plotW
            val xDeployEnd = labelW + candle.deployEndNorm * plotW
            val xEol = labelW + candle.vendorEolNorm * plotW

            val candleHeight = min(10.dp.toPx(), max(6.dp.toPx(), laneH * 0.46f))
            val cTop = yCenter - candleHeight / 2f

            if (xDeployStart > xRel) {
                drawLine(
                    color = palette.textMuted,
                    start = Offset(xRel, yCenter),
                    end = Offset(xDeployStart, yCenter),
                    strokeWidth = 1.2.dp.toPx()
                )
                drawCircle(
                    color = palette.textMuted,
                    radius = 2.2.dp.toPx(),
                    center = Offset(xRel, yCenter)
                )
            }

            val xBodySupportedEnd = min(xDeployEnd, xEol)
            val supportedWidth = (xBodySupportedEnd - xDeployStart).coerceAtLeast(3.dp.toPx())

            val bodyColor = when {
                candle.presenceState != DevicePresenceState.ACTIVE ->
                    TuesdayTheme.presenceColorFor(candle.presenceState, isStn)
                candle.hasCveOrRecall -> cveColor
                candle.isPastEol -> overhangColor
                candle.isEolNearing -> eolColor
                else -> okColor
            }

            if (candle.isGuessed) {
                drawRect(
                    color = bodyColor.copy(alpha = 0.22f),
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(supportedWidth, candleHeight)
                )
                drawRect(
                    color = palette.textMuted,
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(supportedWidth, candleHeight),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                )
            } else if (candle.presenceState == DevicePresenceState.RETIRED || candle.isRetired) {
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
                drawRect(
                    color = bodyColor,
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(supportedWidth, candleHeight)
                )
                drawRect(
                    color = palette.textPrimary.copy(alpha = 0.20f),
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(supportedWidth, candleHeight),
                    style = Stroke(width = 0.8.dp.toPx())
                )
            }

            if (candle.isPastEol && xDeployEnd > xEol) {
                val xOverhangStart = xEol
                val overhangWidth = (xDeployEnd - xOverhangStart).coerceAtLeast(3.dp.toPx())

                drawLine(
                    color = palette.textPrimary,
                    start = Offset(xEol, cTop - 2.dp.toPx()),
                    end = Offset(xEol, cTop + candleHeight + 2.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )

                drawRect(
                    color = cveColor,
                    topLeft = Offset(xOverhangStart, cTop),
                    size = Size(overhangWidth, candleHeight)
                )

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
                    color = cveColor.copy(alpha = 0.5f),
                    topLeft = Offset(xOverhangStart, cTop),
                    size = Size(overhangWidth, candleHeight),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            if (!candle.isPastEol && xEol > xDeployEnd) {
                val runwayColor = if (candle.isEolNearing) eolColor else palette.textMuted
                drawLine(
                    color = runwayColor,
                    start = Offset(xDeployEnd, yCenter),
                    end = Offset(xEol, yCenter),
                    strokeWidth = 1.2.dp.toPx()
                )
                drawCircle(
                    color = runwayColor,
                    radius = 2.2.dp.toPx(),
                    center = Offset(xEol, yCenter)
                )
            }

            candle.cveStartNorm?.let { cveNorm ->
                val cx = labelW + cveNorm * plotW
                val tri = Path().apply {
                    moveTo(cx, cTop - 5.dp.toPx())
                    lineTo(cx - 3.5.dp.toPx(), cTop)
                    lineTo(cx + 3.5.dp.toPx(), cTop)
                    close()
                }
                drawPath(path = tri, color = cveColor)
            }
        }

        val fy = h - footerH + 4.dp.toPx()
        drawRect(
            color = palette.borderStrong,
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
