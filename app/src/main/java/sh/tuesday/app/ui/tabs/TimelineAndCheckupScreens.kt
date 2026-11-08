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
import androidx.compose.material3.HorizontalDivider
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
import sh.tuesday.app.models.BulletinsAndInventoryEngine
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

@Composable
fun SwimlaneTimelineScreen(
    flavor: AppBrandFlavor,
    palette: BrandPalette,
    devices: List<RackPortDevice>,
    onOpenReferenceGuide: () -> Unit = {},
    onInspectDevice: (String) -> Unit = {}
) {
    val isStn = flavor == AppBrandFlavor.SURETHING_HOME
    var activeZoom by remember { mutableStateOf("1M") }
    var scrubberXNorm by remember { mutableFloatStateOf(0.88f) }

    val sortedDevices = remember(devices) {
        BulletinsAndInventoryEngine.sortDevicesForTimeline(devices)
    }

    val dayOffset = ((1f - scrubberXNorm) * 30f).roundToInt()
    val cursorDateStr = if (dayOffset <= 0) {
        "2026-10-29 23:00 UTC (NOW)"
    } else {
        val day = max(1, 29 - dayOffset)
        "2026-10-%02d (-%dd)".format(day, dayOffset)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .uiComponent("SwimlaneTimelineScreen")
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // 1. Unboxed Header + 2D Candlestick Lifespan Canvas
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("timeline-header-card"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header Row matching My Net / Check-Ups unboxed style
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
                            text = if (isStn) "Lifespan" else "Timeline",
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

                    if (!isStn) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("1D", "1W", "1M", "1Y").forEach { zoom ->
                                val selected = activeZoom == zoom
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(if (selected) palette.accent.copy(alpha = 0.18f) else palette.elevated)
                                        .border(
                                            1.dp,
                                            if (selected) palette.accent else palette.border,
                                            RoundedCornerShape(5.dp)
                                        )
                                        .clickable { activeZoom = zoom }
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = zoom,
                                        color = if (selected) palette.accent else palette.textMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE0F2FE))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Hardware Lifecycles",
                                color = Color(0xFF0284C7),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 2D Hardware Candlestick Lifecycle Canvas Card (interactive scrubber)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                ) {
                    // Scrubber Readout Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.elevated.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CURSOR: $cursorDateStr",
                            color = palette.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (isStn) "Drag to Scrub Versions" else "Zoom: $activeZoom",
                            color = palette.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    HorizontalDivider(color = palette.border, thickness = 1.dp)

                    SwimlaneCanvas(
                        devices = sortedDevices,
                        palette = palette,
                        scrubberXNorm = scrubberXNorm,
                        onScrubChanged = { scrubberXNorm = it }
                    )

                    HorizontalDivider(color = palette.border, thickness = 1.dp)

                    // Candlestick Legend Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.elevated.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF64748B))
                            )
                            Text("Known Release Span", color = palette.textMuted, fontSize = 11.sp)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 10.dp, height = 6.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(TuesdayTheme.LedOkGreen)
                            )
                            Text("Permissible", color = palette.textMuted, fontSize = 11.sp)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 2.dp, height = 8.dp)
                                    .background(Color.White)
                            )
                            Text("Current Ver", color = palette.textMuted, fontSize = 11.sp)
                        }
                        Text("▒ Guessed", color = palette.textMuted, fontSize = 11.sp)
                    }
                }

                // Section Header for Unboxed Hardware Lifecycles & Permissible Versions List
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, start = 2.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isStn) {
                            "Device Lifespan & Supported Versions"
                        } else {
                            "Hardware Lifecycles & Permissible Versions"
                        },
                        color = palette.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isStn) "Candlestick Drift" else "LIFECYCLE DRIFT",
                        color = if (isStn) palette.textMuted else palette.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (isStn) FontFamily.Default else FontFamily.Monospace
                    )
                }
            }
        }

        if (sortedDevices.isEmpty()) {
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
                        text = if (isStn) {
                            "Add devices to start tracking software updates and manufacturer warranty dates."
                        } else {
                            "No enrolled devices in inventory yet."
                        },
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Unboxed Device List sorted identically to the timeline canvas (Z0 -> Z4, then portIndex)
            // Dynamically updates "Current" version as the user scrubs the timeline cursor!
            items(sortedDevices, key = { it.id }) { dev ->
                val stateColor = TuesdayTheme.ledColorFor(dev.portState, isStn)
                val scrubbedVersion = BulletinsAndInventoryEngine.getEstimatedVersionAtCursor(dev, scrubberXNorm)
                val isGuessed = dev.portState == PortLifecycleState.GUESSED || dev.version == "unverified"

                if (isStn) {
                    val driftLabel = when {
                        isGuessed -> "Estimated version drift range"
                        dev.portState == PortLifecycleState.UPDATE -> "Drift detected • Newer release available"
                        dev.portState == PortLifecycleState.EOL -> "End-of-life • No further updates"
                        else -> "Up to date"
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onInspectDevice(dev.id) }
                    ) {
                        HorizontalDivider(color = palette.border, thickness = 1.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = dev.name,
                                color = palette.textPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            // Candlestick Mini Indicator Bar + Dynamic Scrubbed Version
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(0xFFF1F5F9))
                                ) {
                                    if (isGuessed) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.5f)
                                                .fillMaxHeight()
                                                .align(Alignment.Center)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color(0xFFCBD5E1))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.65f)
                                                .fillMaxHeight()
                                                .align(Alignment.Center)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(stateColor)
                                        )
                                    }
                                }
                                Text(
                                    text = "v$scrubbedVersion",
                                    color = stateColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Released: ${dev.purchaseDate ?: "Known release"}",
                                    color = palette.textMuted,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = driftLabel,
                                    color = palette.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                } else {
                    // STN-Pro Unboxed Timeline Row:
                    // Port square + Device Name + Dynamic Scrubbed "Current" version + Release + EOL
                    // (No state pills, no STN-... asset tags, no PURLs/dependency dumps)
                    val sqTextColor = if (dev.portState == PortLifecycleState.EOL || dev.portState == PortLifecycleState.OK) {
                        Color(0xFF090D0A)
                    } else {
                        Color.White
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onInspectDevice(dev.id) }
                    ) {
                        HorizontalDivider(color = palette.border, thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(stateColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dev.portIndex.toString(),
                                    color = sqTextColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = dev.name,
                                    color = palette.textPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = buildAnnotatedString {
                                        append("Current: ")
                                        withStyle(
                                            SpanStyle(
                                                color = palette.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        ) {
                                            append(scrubbedVersion)
                                        }
                                        append("  •  Release: ${dev.purchaseDate ?: "Known release"}  •  EOL: ${dev.vendorEolAt ?: "TBD"}")
                                    },
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

@Composable
private fun SwimlaneCanvas(
    devices: List<RackPortDevice>,
    palette: BrandPalette,
    scrubberXNorm: Float,
    onScrubChanged: (Float) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(228.dp)
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

        if (devices.isEmpty()) {
            // Pure Hardware Lifecycle Empty State (no rhythms or bulletins)
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
                "No Enrolled Hardware in Inventory",
                w / 2f,
                midY - 12.dp.toPx(),
                paintTitle
            )
            drawContext.canvas.nativeCanvas.drawText(
                "Add devices to see release-to-EOL candlesticks and software drift",
                w / 2f,
                midY + 18.dp.toPx(),
                paintSub
            )
            return@Canvas
        }

        val labelW = 96.dp.toPx()
        val plotW = (w - labelW - 12.dp.toPx()).coerceAtLeast(10f)
        val laneCount = devices.size.coerceAtLeast(1)
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

        devices.forEachIndexed { idx, dev ->
            val yTop = idx * laneH
            val yCenter = yTop + laneH / 2f
            val isGuessed = dev.portState == PortLifecycleState.GUESSED || dev.version == "unverified"

            // Lane divider
            drawLine(
                color = Color(0xFF242730),
                start = Offset(0f, (idx + 1) * laneH),
                end = Offset(w, (idx + 1) * laneH),
                strokeWidth = 1f
            )

            // Left zone indicator bar
            val zoneColor = when (dev.trustZone) {
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
            val label = "#%02d %s".format(dev.portIndex, dev.shortLabel)
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

            // Candlestick Hardware Lifecycle Geometry
            val candleData = dev.candles.lastOrNull()
            val relNorm = dev.candles.firstOrNull()?.released ?: 0.15f
            val eolNorm = candleData?.eol ?: 0.95f
            val oldestPerm = candleData?.attested ?: 0.40f
            val latestPerm = candleData?.ended ?: 0.85f

            val xRel = labelW + relNorm * plotW
            val xEol = labelW + eolNorm * plotW
            val xBodyStart = labelW + oldestPerm * plotW
            val xBodyEnd = labelW + latestPerm * plotW
            val bodyWidth = max(6.dp.toPx(), xBodyEnd - xBodyStart)
            val candleHeight = min(10.dp.toPx(), max(6.dp.toPx(), laneH * 0.46f))

            if (isGuessed) {
                // IoT / Provisional Guessed Range: Whisker + Stippled drift rectangle
                drawLine(
                    color = Color(0xFF64748B),
                    start = Offset(xRel, yCenter),
                    end = Offset(xEol, yCenter),
                    strokeWidth = 1.2.dp.toPx()
                )
                drawCircle(
                    color = Color(0xFF64748B),
                    radius = 2.5.dp.toPx(),
                    center = Offset(xRel, yCenter)
                )
                drawCircle(
                    color = Color(0xFF64748B),
                    radius = 2.5.dp.toPx(),
                    center = Offset(xEol, yCenter)
                )

                drawRect(
                    color = Color(0xFF64748B).copy(alpha = 0.28f),
                    topLeft = Offset(xBodyStart, yCenter - candleHeight / 2f),
                    size = Size(bodyWidth, candleHeight)
                )
                drawRect(
                    color = Color(0xFF94A3B8),
                    topLeft = Offset(xBodyStart, yCenter - candleHeight / 2f),
                    size = Size(bodyWidth, candleHeight),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                )
                drawCircle(
                    color = Color(0xFFCBD5E1),
                    radius = 2.2.dp.toPx(),
                    center = Offset(xBodyStart + bodyWidth * 0.5f, yCenter)
                )
            } else {
                // Hardware Whisker: Oldest known release date to latest known / EOL
                drawLine(
                    color = Color(0xFF475569),
                    start = Offset(xRel, yCenter),
                    end = Offset(xEol, yCenter),
                    strokeWidth = 1.2.dp.toPx()
                )
                // Release date dot
                drawCircle(
                    color = Color(0xFF64748B),
                    radius = 2.5.dp.toPx(),
                    center = Offset(xRel, yCenter)
                )
                // EOL date dot
                drawCircle(
                    color = if (dev.portState == PortLifecycleState.EOL) Color(0xFF881337) else Color(0xFF64748B),
                    radius = 2.5.dp.toPx(),
                    center = Offset(xEol, yCenter)
                )

                // Candle Body: Oldest permissible version to latest permissible version
                val candleColor = when (dev.portState) {
                    PortLifecycleState.CVE_KEV -> Color(0xFFDC2626)
                    PortLifecycleState.UPDATE -> Color(0xFF3B82F6)
                    PortLifecycleState.EOL -> Color(0xFF881337)
                    else -> Color(0xFF2EBD6B)
                }

                drawRect(
                    color = candleColor,
                    topLeft = Offset(xBodyStart, yCenter - candleHeight / 2f),
                    size = Size(bodyWidth, candleHeight)
                )
                drawRect(
                    color = Color.White.copy(alpha = 0.18f),
                    topLeft = Offset(xBodyStart, yCenter - candleHeight / 2f),
                    size = Size(bodyWidth, candleHeight),
                    style = Stroke(width = 0.8.dp.toPx())
                )

                // Current Version Marker: Distinct vertical white line
                val currentOffsetNorm = if (dev.portState == PortLifecycleState.UPDATE) 0.35f else 0.90f
                val xCurrent = xBodyStart + bodyWidth * currentOffsetNorm
                drawLine(
                    color = Color.White,
                    start = Offset(xCurrent, yCenter - candleHeight / 2f - 1.5.dp.toPx()),
                    end = Offset(xCurrent, yCenter + candleHeight / 2f + 1.5.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )

                // Vulnerability marker triangle if CVE present
                candleData?.cveStart?.let { cveNorm ->
                    val cx = labelW + cveNorm * plotW
                    val tri = Path().apply {
                        moveTo(cx, yCenter - candleHeight / 2f - 5.dp.toPx())
                        lineTo(cx - 3.5.dp.toPx(), yCenter - candleHeight / 2f)
                        lineTo(cx + 3.5.dp.toPx(), yCenter - candleHeight / 2f)
                        close()
                    }
                    drawPath(path = tri, color = TuesdayTheme.LedCveRed)
                }
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
