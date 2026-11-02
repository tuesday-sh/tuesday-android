package sh.tuesday.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.*
import sh.tuesday.app.services.ConnectivityMonitor
import sh.tuesday.app.services.TuesdayRepository
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.bulletins.BulletinsScreen
import sh.tuesday.app.ui.components.CandleGraphCanvas
import sh.tuesday.app.ui.components.WifiWarningOverlay
import sh.tuesday.app.ui.hacs.HacsSettingsSheet
import sh.tuesday.app.ui.tabs.SeasonalCheckupScreen
import sh.tuesday.app.ui.tabs.SwimlaneTimelineScreen

enum class MainNavTab(val labelPro: String, val labelStn: String) {
    STATUS("Status", "Home"),
    BULLETINS("Bulletins", "Bulletins"),
    TIMELINE("Timeline", "Lifespan"),
    CHECKUP("Q4 Tune-Up", "Checkup"),
    PLUG_AND_TAG("Plug & Tag", "Add Device")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: TuesdayRepository,
    connectivity: ConnectivityMonitor,
    onNavigateToAddDevice: () -> Unit
) {
    val rackDevices by repository.rackDevices.collectAsState()
    val brandFlavor by repository.brandFlavor.collectAsState()
    val mutedFeedSlugs by repository.mutedFeedSlugs.collectAsState()
    val includeOkDevices by repository.includeOkDevices.collectAsState()
    val selectedZoneFilter by repository.selectedZoneFilter.collectAsState()
    val hacsConfig by repository.hacsConfig.collectAsState()
    val isWifiConnected by connectivity.isWifiConnected.collectAsState()
    val isSimulatingWifi by connectivity.simulateWifiDisconnected.collectAsState()

    var activeTab by remember { mutableStateOf(MainNavTab.STATUS) }
    var inspectedDevice by remember { mutableStateOf<RackPortDevice?>(null) }
    var showHacsSheet by remember { mutableStateOf(false) }

    val palette = TuesdayTheme.paletteFor(brandFlavor)
    val isStn = brandFlavor == AppBrandFlavor.SURETHING_HOME
    val shouldShowWifiOverlay = hacsConfig.requireLocalWifi && !isWifiConnected

    val ledSlots = remember(rackDevices, selectedZoneFilter) {
        BulletinsAndInventoryEngine.build128LedMatrix(rackDevices, selectedZoneFilter)
    }
    val actionDevices = remember(rackDevices, includeOkDevices, selectedZoneFilter) {
        BulletinsAndInventoryEngine.filterActionFirstDevices(rackDevices, includeOkDevices, selectedZoneFilter)
    }
    val bulletinsPayload = remember(brandFlavor, rackDevices, mutedFeedSlugs) {
        BulletinsAndInventoryEngine.assembleBulletins(
            flavor = brandFlavor,
            devices = rackDevices,
            mutedFeedSlugs = mutedFeedSlugs
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(palette.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = brandFlavor.title,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Surface(
                            onClick = { repository.toggleBrandFlavor() },
                            shape = RoundedCornerShape(6.dp),
                            color = palette.elevated
                        ) {
                            Text(
                                text = brandFlavor.badge,
                                color = palette.accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        onClick = { showHacsSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        color = palette.elevated,
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (hacsConfig.isConnected) TuesdayTheme.LedOkGreen else TuesdayTheme.LedCveRed)
                            )
                            Text(
                                text = "HACS",
                                color = palette.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.surface)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = palette.surface,
                tonalElevation = 0.dp
            ) {
                MainNavTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (tab == MainNavTab.PLUG_AND_TAG) {
                                onNavigateToAddDevice()
                            } else {
                                activeTab = tab
                            }
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (tab == MainNavTab.BULLETINS && bulletinsPayload.inbox.isNotEmpty()) {
                                        Badge(containerColor = TuesdayTheme.LedCveRed) {
                                            Text(
                                                text = bulletinsPayload.inbox.size.toString(),
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = when (tab) {
                                        MainNavTab.STATUS -> Icons.Default.GridView
                                        MainNavTab.BULLETINS -> Icons.Default.Notifications
                                        MainNavTab.TIMELINE -> Icons.Default.Timeline
                                        MainNavTab.CHECKUP -> Icons.Default.Checklist
                                        MainNavTab.PLUG_AND_TAG -> Icons.Default.CameraAlt
                                    },
                                    contentDescription = tab.name,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = if (isStn) tab.labelStn else tab.labelPro,
                                fontSize = 9.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = palette.accent,
                            selectedTextColor = palette.accent,
                            unselectedIconColor = palette.textMuted,
                            unselectedTextColor = palette.textMuted,
                            indicatorColor = palette.accent.copy(alpha = 0.14f)
                        )
                    )
                }
            }
        },
        containerColor = palette.bg
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (activeTab) {
                MainNavTab.STATUS -> StatusMatrixAndActionListScreen(
                    flavor = brandFlavor,
                    palette = palette,
                    ledSlots = ledSlots,
                    actionDevices = actionDevices,
                    includeOkDevices = includeOkDevices,
                    selectedZoneFilter = selectedZoneFilter,
                    onSelectZone = { repository.setZoneFilter(it) },
                    onToggleIncludeOk = { repository.toggleIncludeOkDevices() },
                    onInspectDevice = { devId ->
                        rackDevices.find { it.id == devId }?.let { inspectedDevice = it }
                    }
                )
                MainNavTab.BULLETINS -> BulletinsScreen(
                    flavor = brandFlavor,
                    palette = palette,
                    payload = bulletinsPayload,
                    onToggleMuteFeed = { repository.toggleMuteFeed(it) },
                    onConfirmModel = { repository.confirmProvisionalDeviceModel(it) },
                    onRejectModel = { repository.rejectProvisionalDeviceModel() }
                )
                MainNavTab.TIMELINE -> SwimlaneTimelineScreen(
                    flavor = brandFlavor,
                    palette = palette,
                    devices = rackDevices
                )
                MainNavTab.CHECKUP -> SeasonalCheckupScreen(
                    flavor = brandFlavor,
                    palette = palette,
                    devices = rackDevices
                )
                MainNavTab.PLUG_AND_TAG -> {}
            }

            WifiWarningOverlay(
                isVisible = shouldShowWifiOverlay,
                hacsUrl = hacsConfig.baseUrl,
                onDisableRequirement = {
                    repository.updateHacsConfig(hacsConfig.copy(requireLocalWifi = false))
                    connectivity.setSimulateDisconnected(false)
                }
            )
        }
    }

    inspectedDevice?.let { device ->
        ModalBottomSheet(
            onDismissRequest = { inspectedDevice = null },
            containerColor = palette.surface
        ) {
            DeviceInspectorBottomSheet(
                device = device,
                palette = palette,
                onApplyPatch = {
                    repository.markDeviceUpdatesApplied(device.id)
                    inspectedDevice = null
                },
                onClose = { inspectedDevice = null }
            )
        }
    }

    if (showHacsSheet) {
        HacsSettingsSheet(
            config = hacsConfig,
            simulateWifiDisconnected = isSimulatingWifi,
            onSaveConfig = { repository.updateHacsConfig(it) },
            onToggleSimulateWifi = { connectivity.setSimulateDisconnected(it) },
            onResetDemoData = {
                repository.resetDemoData()
                connectivity.setSimulateDisconnected(false)
            },
            onDismiss = { showHacsSheet = false }
        )
    }
}

@Composable
private fun StatusMatrixAndActionListScreen(
    flavor: AppBrandFlavor,
    palette: BrandPalette,
    ledSlots: List<LedSlot>,
    actionDevices: List<RackPortDevice>,
    includeOkDevices: Boolean,
    selectedZoneFilter: TrustZone?,
    onSelectZone: (TrustZone?) -> Unit,
    onToggleIncludeOk: () -> Unit,
    onInspectDevice: (String) -> Unit
) {
    val isStn = flavor == AppBrandFlavor.SURETHING_HOME
    val okCount = ledSlots.count { it.state == PortLifecycleState.OK }
    val cveCount = ledSlots.count { it.state == PortLifecycleState.CVE_KEV }
    val eolCount = ledSlots.count { it.state == PortLifecycleState.EOL }
    val guessedCount = ledSlots.count { it.state == PortLifecycleState.GUESSED }
    val updateCount = ledSlots.count { it.state == PortLifecycleState.UPDATE }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // 1. 128-LED At-a-Glance Status Matrix Card (16 columns x 8 rows)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isStn) "HOME DEVICE STATUS MATRIX (${ledSlots.size} NODES)" else "AT-A-GLANCE STATUS MATRIX (${ledSlots.size} LEDS • Z0→Z4)",
                        color = palette.textPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "$okCount OK • ${ledSlots.size - okCount} Action",
                        color = palette.accent,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // 5-State Color Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LedLegendItem("OK ($okCount)", TuesdayTheme.LedOkGreen, palette)
                    LedLegendItem("CVE ($cveCount)", TuesdayTheme.LedCveRed, palette)
                    LedLegendItem("EOL ($eolCount)", TuesdayTheme.LedEolAmber, palette)
                    LedLegendItem("Guess ($guessedCount)", TuesdayTheme.LedGuessedGrey, palette)
                    LedLegendItem("Upd ($updateCount)", TuesdayTheme.LedUpdateBlue, palette)
                }

                // 16 columns x N rows compact LED grid (zero text inside squares)
                val rows = ledSlots.chunked(16)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isStn) Color(0xFF0F172A) else Color(0xFF070A0F))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    rows.forEach { rowSlots ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            rowSlots.forEach { slot ->
                                val ledColor = TuesdayTheme.ledColorFor(slot.state)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(ledColor.copy(alpha = if (slot.state == PortLifecycleState.OK) 0.78f else 1f))
                                        .clickable(enabled = slot.deviceId != null) {
                                            slot.deviceId?.let { onInspectDevice(it) }
                                        }
                                )
                            }
                            // Pad incomplete row if zone filtered
                            repeat((16 - rowSlots.size).coerceAtLeast(0)) {
                                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }

                // Zone Filter Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    ZoneFilterPill(
                        label = "All (128)",
                        selected = selectedZoneFilter == null,
                        palette = palette,
                        onClick = { onSelectZone(null) }
                    )
                    listOf(TrustZone.Z0_CORE, TrustZone.Z1_TRUSTED, TrustZone.Z2_LAB, TrustZone.Z3_IOT).forEach { zone ->
                        ZoneFilterPill(
                            label = zone.shortCode,
                            selected = selectedZoneFilter == zone,
                            palette = palette,
                            onClick = { onSelectZone(zone) }
                        )
                    }
                }
            }
        }

        // 2. Action-First Device List Header + Show OK Toggle
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (includeOkDevices) {
                        "ALL NAMED DEVICES (${actionDevices.size})"
                    } else {
                        "NEEDS ATTENTION (${actionDevices.size} NON-GREEN)"
                    },
                    color = palette.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.elevated)
                        .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                        .clickable { onToggleIncludeOk() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (includeOkDevices) "Hide OK Devices" else "Show OK Devices (+119)",
                        color = palette.accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 3. Action-First Device Rows
        items(actionDevices, key = { it.id }) { dev ->
            val stateColor = TuesdayTheme.ledColorFor(dev.portState)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                    .clickable { onInspectDevice(dev.id) }
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left severity stripe + Port Badge
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(stateColor.copy(alpha = 0.16f))
                        .border(1.dp, stateColor, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#%02d".format(dev.portIndex),
                        color = stateColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dev.name,
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${dev.trustZone.shortCode} • ${dev.version}",
                            color = palette.textMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = if (isStn) dev.stnHeadline else dev.proHeadline,
                        color = stateColor,
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LedLegendItem(label: String, color: Color, palette: BrandPalette) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            text = label,
            color = palette.textMuted,
            fontSize = 8.5.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ZoneFilterPill(
    label: String,
    selected: Boolean,
    palette: BrandPalette,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
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

@Composable
private fun DeviceInspectorBottomSheet(
    device: RackPortDevice,
    palette: BrandPalette,
    onApplyPatch: () -> Unit,
    onClose: () -> Unit
) {
    val stateColor = TuesdayTheme.ledColorFor(device.portState)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "PORT #%02d • %s".format(device.portIndex, device.assetTag),
                    color = stateColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = device.name,
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = onClose) {
                Text("Close", color = palette.accent)
            }
        }

        // OHLC Version Candlesticks & Telemetry Health
        CandleGraphCanvas(
            score = when (device.portState) {
                PortLifecycleState.OK -> 98.0
                PortLifecycleState.UPDATE -> 82.0
                PortLifecycleState.GUESSED -> 64.0
                PortLifecycleState.EOL -> 42.0
                PortLifecycleState.CVE_KEV -> 28.0
            }
        )

        InspectorSectionRow("S0 Identity", device.s0Identity, palette)
        InspectorSectionRow("S1 Hardware", device.s1Hardware, palette)
        InspectorSectionRow("S2 Software (PURL)", device.s2Software, palette)
        InspectorSectionRow("S3 Lifecycle", device.s3Lifecycle, palette)
        InspectorSectionRow("S4 Advisories", device.s4Advisories, palette)
        InspectorSectionRow("S5 Topology", device.s5Topology, palette)
        InspectorSectionRow("S6 Remediation", device.s6Remediation, palette)

        if (device.portState != PortLifecycleState.OK) {
            Button(
                onClick = onApplyPatch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TuesdayTheme.LedOkGreen,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 14.dp)
            ) {
                Text("Mark Remediation Complete (Set Port OK)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun InspectorSectionRow(label: String, value: String, palette: BrandPalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(palette.elevated)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label.uppercase(),
            color = palette.accent,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = palette.textPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
