package sh.tuesday.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import sh.tuesday.app.models.*
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

@Composable
internal fun InventoryCard(
    scaleTier: InventoryScaleTier,
    visibleDevices: List<RackPortDevice>,
    isStn: Boolean,
    palette: BrandPalette,
    networks: List<NetworkSite>,
    activeNetworkId: String,
    selectedZoneFilter: TrustZone?,
    selectedPortStateFilter: PortLifecycleState?,
    showZoneFilterStrip: Boolean,
    onSelectNetwork: (String) -> Unit,
    onSelectZone: (TrustZone?) -> Unit,
    onSelectPortState: (PortLifecycleState?) -> Unit,
    onOpenReferenceGuide: () -> Unit,
    onNavigateToAddDevice: () -> Unit,
    onAddCategoryDevice: (StnHardwareCategory) -> Unit,
    onInspectDevice: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiComponent("InventoryCard", "tier=${scaleTier.id};active=${visibleDevices.size}")
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header: "Inventory" + "+ Add Device" CTA + "?" Reference Guide button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .uiSlot("inventory-header"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            sh.tuesday.app.ui.components.SectionHeading(
                text = "Inventory",
                palette = palette,
                modifier = Modifier.uiSlot("inventory-title")
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Primary CTA: "+ Add Device" button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.accent)
                        .clickable { onNavigateToAddDevice() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .uiAction("openAddDevice")
                ) {
                    Row(
                        modifier = Modifier.uiSlot("add-device-cta"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "+ Add Device",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                // Secondary CTA 1: "?" Reference Guide button (36dp x 36dp circle)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.elevated)
                        .border(1.dp, palette.border, CircleShape)
                        .clickable { onOpenReferenceGuide() }
                        .semantics {
                            contentDescription = "Inventory Reference Guide"
                        }
                        .uiAction("openReferenceGuide")
                ) {
                    Text(
                        text = "?",
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.uiSlot("help-button")
                    )
                }
            }
        }

        // Unified ubnt-chassis container with HorizontalPager swipe between networks
        val pagerNetworks = remember(networks) {
            if (networks.isNotEmpty()) networks else listOf(NetworkSite("net-default", "My Network", 0, "Primary"))
        }
        val currentNetworkIndex = remember(pagerNetworks, activeNetworkId) {
            pagerNetworks.indexOfFirst { it.id == activeNetworkId }.coerceAtLeast(0)
        }
        val pagerState = rememberPagerState(
            initialPage = currentNetworkIndex,
            pageCount = { pagerNetworks.size }
        )

        LaunchedEffect(pagerState.settledPage) {
            val targetNet = pagerNetworks.getOrNull(pagerState.settledPage)
            if (targetNet != null && targetNet.id != activeNetworkId) {
                onSelectNetwork(targetNet.id)
            }
        }

        LaunchedEffect(currentNetworkIndex) {
            if (pagerState.currentPage != currentNetworkIndex) {
                pagerState.animateScrollToPage(currentNetworkIndex)
            }
        }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
        ) { page ->
            val net = pagerNetworks[page]
            val isCurrent = net.id == activeNetworkId
            val pageDevices = if (isCurrent) {
                visibleDevices
            } else if (net.id == "net-demo") {
                BulletinsAndInventoryEngine.defaultRackPortDevices()
            } else {
                emptyList()
            }
            val pageScaleTier = InventoryScaleTier.forCount(pageDevices.size)
            val pageSlots = BulletinsAndInventoryEngine.build128LedMatrix(pageDevices, selectedZoneFilter)

            InventoryChassisCard(
                network = net,
                devices = pageDevices,
                ledSlots = pageSlots,
                scaleTier = pageScaleTier,
                isStn = isStn,
                palette = palette,
                selectedPortStateFilter = selectedPortStateFilter,
                isCurrentPage = isCurrent,
                onSelectPortState = onSelectPortState,
                onInspectDevice = onInspectDevice,
                onAddCategoryDevice = onAddCategoryDevice
            )
        }

        // Trust Zone Filter Pills (shown when >= 1 device & preference allow)
        if (visibleDevices.isNotEmpty() && showZoneFilterStrip) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("zone-filter-strip")
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ZoneFilterPill(
                    label = "All Zones",
                    selected = selectedZoneFilter == null,
                    palette = palette,
                    onClick = { onSelectZone(null) }
                )
                TrustZone.entries.forEach { zone ->
                    ZoneFilterPill(
                        label = if (isStn) zone.stnLabel else "${zone.shortCode} ${zone.stnLabel}",
                        selected = selectedZoneFilter == zone,
                        palette = palette,
                        onClick = { onSelectZone(zone) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun LedLegendStripTab(
    label: String,
    color: Color,
    selected: Boolean,
    dimmed: Boolean,
    palette: BrandPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .uiAction("toggleLegendStateFilter")
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (selected) 4.5.dp else 3.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (dimmed) color.copy(alpha = 0.25f) else color)
        )
        Text(
            text = label,
            color = when {
                selected -> palette.textPrimary
                dimmed -> palette.textMuted.copy(alpha = 0.45f)
                else -> palette.textMuted
            },
            fontSize = 8.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun InventoryChassisCard(
    network: NetworkSite,
    devices: List<RackPortDevice>,
    ledSlots: List<LedSlot>,
    scaleTier: InventoryScaleTier,
    isStn: Boolean,
    palette: BrandPalette,
    selectedPortStateFilter: PortLifecycleState? = null,
    isCurrentPage: Boolean = true,
    onSelectPortState: (PortLifecycleState?) -> Unit = {},
    onInspectDevice: (String) -> Unit,
    onAddCategoryDevice: (StnHardwareCategory) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isStn) Color(0xFFF1F5F9) else Color(0xFF0B0D11))
            .border(1.dp, palette.border, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 8-Port Hardware Category Faceplate Strip (C1..C8)
        // Show faceplate strip across all tiers (including Day 0 empty state) for consistent hardware silhouette
        CategoryFaceplateStrip(
            devices = devices,
            isStn = isStn,
            palette = palette,
            onInspectDevice = onInspectDevice,
            onAddCategoryDevice = onAddCategoryDevice
        )

        // 16 columns x 8 rows compact LED grid with bottom-snapped PNW monochrome pixel art overlay
        val rows = ledSlots.chunked(16)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .uiComponent("InventoryLedGrid")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("led-grid-128"),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                rows.forEach { rowSlots ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        rowSlots.forEach { slot ->
                            val ledColor = TuesdayTheme.ledColorFor(slot.state, isStn)
                            val ledAlpha = if (selectedPortStateFilter != null) {
                                when {
                                    slot.state == selectedPortStateFilter -> 1f
                                    slot.state == PortLifecycleState.UNASSIGNED -> 0.30f
                                    else -> 0.18f
                                }
                            } else {
                                when (slot.state) {
                                    PortLifecycleState.UNASSIGNED -> 0.55f
                                    PortLifecycleState.OK -> 0.85f
                                    else -> 1f
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .uiAction("inspectDevice")
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(ledColor.copy(alpha = ledAlpha))
                                    .clickable(enabled = slot.deviceId != null) {
                                        slot.deviceId?.let { onInspectDevice(it) }
                                    }
                            )
                        }
                        repeat((16 - rowSlots.size).coerceAtLeast(0)) {
                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }

            // Bottom-snapped monochrome pixel art overlay (Mountain Horizon / Sprout / 3 Pines / 9-Tree Horizon)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .uiComponent("BotanicalGridOverlay")
                    .uiSlot("botanical-overlay")
            ) {
                BotanicalGridOverlay(
                    scaleTier = scaleTier,
                    activeCount = devices.size,
                    isStn = isStn,
                    palette = palette
                )
            }
        }

        // 5-State LED Color Strip Tabs equally spaced along the bottom of the inventory grid container (dimmed/disabled when empty)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (isCurrentPage) it.uiSlot("legend-row") else it }
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Top
        ) {
            listOf(
                PortLifecycleState.OK to "OK",
                PortLifecycleState.CVE_KEV to (if (isStn) "Alert" else "CVE/KEV"),
                PortLifecycleState.EOL to "EOL",
                PortLifecycleState.GUESSED to "Guessed",
                PortLifecycleState.UPDATE to "Update"
            ).forEach { (state, label) ->
                val isSelected = devices.isNotEmpty() && selectedPortStateFilter == state
                val isDimmed = devices.isEmpty() || (selectedPortStateFilter != null && !isSelected)
                LedLegendStripTab(
                    label = label,
                    color = TuesdayTheme.ledColorFor(state, isStn),
                    selected = isSelected,
                    dimmed = isDimmed,
                    palette = palette,
                    onClick = {
                        if (devices.isNotEmpty()) {
                            onSelectPortState(if (isSelected) null else state)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun CategoryFaceplateStrip(
    devices: List<RackPortDevice>,
    isStn: Boolean,
    palette: BrandPalette,
    onInspectDevice: (String) -> Unit,
    onAddCategoryDevice: (StnHardwareCategory) -> Unit
) {
    var activeContextCategory by remember { mutableStateOf<StnHardwareCategory?>(null) }
    var selectedAnchorXInStripPx by remember { mutableFloatStateOf(0f) }
    var stripWidthPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .uiComponent("CategoryFaceplateStrip")
            .onGloballyPositioned { coords ->
                stripWidthPx = coords.size.width.toFloat()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .uiSlot("c1-c8-strip"),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            StnHardwareCategory.entries.forEach { category ->
                val matchingDevs = devices.filter { it.category == category }
                val count = matchingDevs.size
                val hasDevices = count > 0

                // Worst severity in category (lowest severityPriority number = worst severity)
                val worstState = matchingDevs
                    .minByOrNull { it.portState.severityPriority }
                    ?.portState

                val stateColor = if (worstState != null) {
                    TuesdayTheme.ledColorFor(worstState, isStn)
                } else {
                    if (isStn) Color(0xFF94A3B8) else Color(0xFF4B5563)
                }

                val isCategorySelected = activeContextCategory == category
                val emptyBorder = if (isStn) Color(0xFFCBD5E1) else Color(0xFF2D323E)
                val iconBoxBg = if (isCategorySelected) {
                    palette.accent.copy(alpha = 0.22f)
                } else if (isStn) {
                    Color(0xFFF8FAFC)
                } else {
                    Color(0xFF20232B)
                }
                val iconBorderColor = if (isCategorySelected) {
                    palette.accent
                } else if (hasDevices) {
                    stateColor.copy(alpha = 0.53f)
                } else {
                    emptyBorder
                }
                val tickColor = if (hasDevices) stateColor.copy(alpha = 0.53f) else emptyBorder
                val tileBgColor = if (hasDevices) stateColor else if (isStn) Color(0xFFE2E8F0) else Color(0xFF20232A)
                val tileTextColor = when {
                    !hasDevices -> Color(0xFF6B7280)
                    !isStn && (worstState == PortLifecycleState.OK || worstState == PortLifecycleState.EOL) -> Color(0xFF090D0A)
                    else -> Color.White
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .uiAction("inspectCategoryDevice")
                        .onGloballyPositioned { coords ->
                            if (activeContextCategory == category) {
                                selectedAnchorXInStripPx = coords.positionInParent().x + coords.size.width / 2f
                            }
                        }
                        .clickable {
                            if (activeContextCategory == category) {
                                activeContextCategory = null
                            } else {
                                activeContextCategory = category
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Top 1:1 Square Hardware Silhouette Box (.ubnt-diagram-icon)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .uiSlot("category-col", "cat=${category.code};count=$count")
                            .clip(RoundedCornerShape(6.dp))
                            .background(iconBoxBg)
                            .border(1.dp, iconBorderColor, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        HardwareCategorySvgIcon(
                            category = category,
                            tint = stateColor,
                            modifier = Modifier.fillMaxSize(0.56f)
                        )
                    }

                    // 2. Middle Vertical Connector Tick (.ubnt-diagram-tick: 1dp x 6dp)
                    Box(
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .width(1.dp)
                            .height(6.dp)
                            .background(tickColor)
                    )

                    // 3. Bottom 1:1 Square Port Count Tile (.ubnt-diagram-port-tile)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(tileBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = count.toString(),
                            color = tileTextColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Context Panel Callout Popup
        if (activeContextCategory != null) {
            val cat = activeContextCategory!!
            val matchingDevs = devices.filter { it.category == cat }
            val density = LocalDensity.current

            Popup(
                alignment = Alignment.TopStart,
                offset = with(density) {
                    // Position below the strip (faceplate height is ~86dp)
                    IntOffset(0, 92.dp.roundToPx())
                },
                onDismissRequest = { activeContextCategory = null },
                properties = PopupProperties(focusable = true)
            ) {
                CategoryPopover(
                    category = cat,
                    devices = matchingDevs,
                    isStn = isStn,
                    palette = palette,
                    anchorXInStripPx = selectedAnchorXInStripPx,
                    stripWidthPx = stripWidthPx,
                    onInspectDevice = { devId ->
                        activeContextCategory = null
                        onInspectDevice(devId)
                    },
                    onAddDevice = {
                        activeContextCategory = null
                        onAddCategoryDevice(cat)
                    },
                    onClose = { activeContextCategory = null }
                )
            }
        }
    }
}

@Composable
internal fun CategoryPopover(
    category: StnHardwareCategory,
    devices: List<RackPortDevice>,
    isStn: Boolean,
    palette: BrandPalette,
    anchorXInStripPx: Float,
    stripWidthPx: Float,
    onInspectDevice: (String) -> Unit,
    onAddDevice: () -> Unit,
    onClose: () -> Unit
) {
    val pointerXFraction = if (stripWidthPx > 0f) {
        (anchorXInStripPx / stripWidthPx).coerceIn(0.08f, 0.92f)
    } else 0.5f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .uiComponent("CategoryPopover", "category=${category.code}")
    ) {
        // Upward pointing arrow triangle pointing to the tapped diagram button
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val arrowCenterX = size.width * pointerXFraction
            val arrowWidth = 14.dp.toPx()
            val arrowHeight = 8.dp.toPx()
            val path = Path().apply {
                moveTo(arrowCenterX - arrowWidth / 2f, arrowHeight)
                lineTo(arrowCenterX, 0f)
                lineTo(arrowCenterX + arrowWidth / 2f, arrowHeight)
                close()
            }
            drawPath(
                path = path,
                color = if (isStn) Color(0xFFE2E8F0) else Color(0xFF282C37)
            )
            val fillPath = Path().apply {
                moveTo(arrowCenterX - arrowWidth / 2f + 1f, arrowHeight)
                lineTo(arrowCenterX, 1f)
                lineTo(arrowCenterX + arrowWidth / 2f - 1f, arrowHeight)
                close()
            }
            drawPath(
                path = fillPath,
                color = if (isStn) Color(0xFFFFFFFF) else Color(0xFF14171F)
            )
        }

        // Panel Card Chassis
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = if (isStn) Color(0xFFCBD5E1) else Color(0xFF2E3442),
                    shape = RoundedCornerShape(12.dp)
                ),
            color = if (isStn) Color(0xFFFFFFFF) else Color(0xFF14171F),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header: Category Icon + Title + Device Count Badge + Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .uiSlot("category-popover-header"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(palette.accent.copy(alpha = 0.15f))
                                .border(1.dp, palette.accent.copy(alpha = 0.4f), RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            HardwareCategorySvgIcon(
                                category = category,
                                tint = palette.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isStn) category.stnLabel else category.proLabel,
                                    color = palette.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.uiSlot("category-popover-title")
                                )
                                Text(
                                    text = "${category.code} · ${category.osiLayer}",
                                    color = palette.textMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Text(
                                text = if (devices.isEmpty()) "0 devices in inventory"
                                else "${devices.size} ${if (category == StnHardwareCategory.C8_GUEST) "user(s)" else "device(s)"} enrolled",
                                color = palette.textMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Close (X) button (36dp x 36dp interactive circle)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onClose() }
                            .semantics {
                                contentDescription = "Close Category Popover"
                            }
                            .uiAction("closeCategoryPopover")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = palette.textMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .uiSlot("category-popover-close")
                        )
                    }
                }

                HorizontalDivider(
                    color = if (isStn) Color(0xFFE2E8F0) else Color(0xFF222631),
                    thickness = 1.dp
                )

                // Squircle Gallery of Devices in Category + Add Category Device CTA
                if (devices.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No devices enrolled in this category.",
                            color = palette.textMuted,
                            fontSize = 11.sp
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.accent)
                                .clickable { onAddDevice() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .uiAction("addCategoryDevice")
                        ) {
                            Text(
                                text = "+ Add ${category.shortName} Device",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.uiSlot("category-popover-add-cta", "category=${category.code}")
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiSlot("category-popover-gallery")
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        devices.forEach { dev ->
                            val devStateColor = TuesdayTheme.ledColorFor(dev.portState, isStn)
                            Column(
                                modifier = Modifier
                                    .width(62.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onInspectDevice(dev.id) }
                                    .padding(vertical = 4.dp)
                                    .uiAction("inspectPopoverDevice"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isStn) Color(0xFFF1F5F9) else Color(0xFF1E222B))
                                        .border(
                                            width = 1.5.dp,
                                            color = devStateColor.copy(alpha = 0.8f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    HardwareCategorySvgIcon(
                                        category = dev.category,
                                        tint = devStateColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Text(
                                    text = dev.shortLabel,
                                    color = palette.textPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = dev.trustZone.shortCode,
                                    color = palette.textMuted,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Always provide an inline "+ Add <Category>" tile at the end of the squircle gallery
                        Column(
                            modifier = Modifier
                                .width(62.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onAddDevice() }
                                .padding(vertical = 4.dp)
                                .uiAction("addCategoryDevice"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.accent.copy(alpha = 0.12f))
                                    .border(
                                        width = 1.5.dp,
                                        color = palette.accent.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+",
                                    color = palette.accent,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.uiSlot("category-popover-add-cta", "category=${category.code}")
                                )
                            }
                            Text(
                                text = "Add ${category.shortName}",
                                color = palette.accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = category.code,
                                color = palette.textMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun StateFilterChip(
    label: String,
    dotColor: Color?,
    selected: Boolean,
    palette: BrandPalette,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .uiAction("filterPortState")
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) palette.accent.copy(alpha = 0.18f) else palette.surface)
            .border(1.dp, if (selected) palette.accent else palette.border, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        Text(
            text = label,
            color = if (selected) palette.accent else palette.textPrimary,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
internal fun ZoneFilterPill(
    label: String,
    selected: Boolean,
    palette: BrandPalette,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .uiAction("filterZone")
            .clip(RoundedCornerShape(5.dp))
            .background(if (selected) palette.accent.copy(alpha = 0.18f) else palette.elevated)
            .border(1.dp, if (selected) palette.accent else palette.border, RoundedCornerShape(5.dp))
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = if (selected) palette.accent else palette.textMuted,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
