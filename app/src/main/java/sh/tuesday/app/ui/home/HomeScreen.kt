package sh.tuesday.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import kotlinx.coroutines.launch
import sh.tuesday.app.models.InventoryConfidenceState
import sh.tuesday.app.models.MonitoredDevice
import sh.tuesday.app.models.NodeHealthStatus
import sh.tuesday.app.services.ConnectivityMonitor
import sh.tuesday.app.services.TuesdayRepository
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.components.CandleGraphCanvas
import sh.tuesday.app.ui.components.PackageDiffRow
import sh.tuesday.app.ui.components.WifiWarningOverlay
import sh.tuesday.app.ui.hacs.HacsSettingsSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: TuesdayRepository,
    connectivity: ConnectivityMonitor,
    onNavigateToAddDevice: () -> Unit
) {
    val devices by repository.devices.collectAsState()
    val hacsConfig by repository.hacsConfig.collectAsState()
    val isWifiConnected by connectivity.isWifiConnected.collectAsState()
    val currentSsid by connectivity.currentSsid.collectAsState()
    val isSimulatingWifi by connectivity.simulateWifiDisconnected.collectAsState()

    var showHacsSheet by remember { mutableStateOf(false) }
    var expandedDeviceIds by remember { mutableStateOf(setOf("device-ubuntu-dev")) }
    val scope = rememberCoroutineScope()

    val shouldShowWifiOverlay = hacsConfig.requireLocalWifi && !isWifiConnected

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(TuesdayTheme.HealthyGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = "Tuesday.sh",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                actions = {
                    Surface(
                        onClick = { showHacsSheet = true },
                        shape = RoundedCornerShape(16.dp),
                        color = TuesdayTheme.ElevatedPill,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (hacsConfig.isConnected) TuesdayTheme.HacsCyan else TuesdayTheme.CriticalRed)
                            )
                            Text(
                                text = "HACS",
                                color = TuesdayTheme.HacsCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "HACS Settings",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TuesdayTheme.AppBar)
            )
        },
        containerColor = TuesdayTheme.Background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // 1. Fleet Summary Card
                item {
                    FleetSummaryCard(
                        devices = devices,
                        instanceName = hacsConfig.instanceName,
                        currentSsid = currentSsid
                    )
                }

                // 2. Section Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DEVICES (${devices.size})",
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        TextButton(onClick = onNavigateToAddDevice) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = TuesdayTheme.HealthyGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Add Device",
                                color = TuesdayTheme.HealthyGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // 3. Devices List
                if (devices.isEmpty()) {
                    item {
                        EmptyStateCard()
                    }
                } else {
                    items(devices, key = { it.id }) { device ->
                        val isExpanded = expandedDeviceIds.contains(device.id)
                        DeviceCard(
                            device = device,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedDeviceIds = if (isExpanded) {
                                    expandedDeviceIds - device.id
                                } else {
                                    expandedDeviceIds + device.id
                                }
                            },
                            onPoll = {
                                repository.triggerDeviceCheckIn(device.id)
                            },
                            onPatch = {
                                repository.markDeviceUpdatesApplied(device.id)
                            }
                        )
                    }
                }

                // 4. Footer
                item {
                    FooterSection(baseUrl = hacsConfig.baseUrl)
                }
            }

            // Wi-Fi LAN Guard Warning Overlay
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
private fun FleetSummaryCard(
    devices: List<MonitoredDevice>,
    instanceName: String,
    currentSsid: String
) {
    val criticalCount = devices.count { it.status == NodeHealthStatus.CRITICAL }
    val warningCount = devices.count { it.status == NodeHealthStatus.WARNING }
    val totalPendingUpdates = devices.sumOf { it.pendingUpdates.size }

    val stateColor = when {
        criticalCount > 0 -> TuesdayTheme.CriticalRed
        warningCount > 0 -> TuesdayTheme.WarningAmber
        else -> TuesdayTheme.HealthyGreen
    }

    val stateTitle = when {
        criticalCount > 0 -> "$criticalCount Critical Security Alert${if (criticalCount == 1) "" else "s"}"
        warningCount > 0 -> "$warningCount Device${if (warningCount == 1) "" else "s"} With Outdated Packages"
        else -> "All Devices Up to Date"
    }

    val stateSubtitle = when {
        criticalCount > 0 -> "$totalPendingUpdates total package updates pending across HACS & Tuesday devices."
        warningCount > 0 -> "$totalPendingUpdates non-critical package updates queued for Tuesday window."
        else -> "Every monitored device passed active package update policies."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TuesdayTheme.CardSurface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (criticalCount == 0 && warningCount == 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = stateColor,
                modifier = Modifier.size(28.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stateTitle,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stateSubtitle,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = null, tint = TuesdayTheme.HacsCyan, modifier = Modifier.size(13.dp))
                Text(text = instanceName, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = TuesdayTheme.ElevatedPill
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = TuesdayTheme.HealthyGreen, modifier = Modifier.size(12.dp))
                    Text(
                        text = currentSsid,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: MonitoredDevice,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onPoll: () -> Unit,
    onPatch: () -> Unit
) {
    val statusColor = when (device.status) {
        NodeHealthStatus.HEALTHY -> TuesdayTheme.HealthyGreen
        NodeHealthStatus.WARNING -> TuesdayTheme.WarningAmber
        NodeHealthStatus.CRITICAL -> TuesdayTheme.CriticalRed
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TuesdayTheme.CardSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = device.hostname,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TuesdayTheme.HacsCyan.copy(alpha = 0.14f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = device.packageManager.rawValue.uppercase(),
                            color = TuesdayTheme.HacsCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (device.inventoryState != null && device.inventoryState != InventoryConfidenceState.ENROLLED) {
                        val invColor = if (device.inventoryState == InventoryConfidenceState.VERIFIED_MATCH) TuesdayTheme.HealthyGreen else TuesdayTheme.WarningAmber
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(invColor.copy(alpha = 0.14f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = device.inventoryState.rawValue,
                                color = invColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Text(
                    text = device.osDetails,
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 12.sp
                )

                if (device.discoveredMetadata != null) {
                    val meta = device.discoveredMetadata
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        meta.modelNumber?.takeIf { it.isNotBlank() }?.let {
                            Text("Model: $it", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                        meta.macAddress?.takeIf { it.isNotBlank() }?.let {
                            Text("MAC: $it", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(statusColor.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = device.status.displayTitle.uppercase(),
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // HACS Entity and Outdated count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("HACS ENTITY ID", color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(device.haEntityId, color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("OUTDATED PACKAGES", color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (device.pendingUpdates.isEmpty()) "0 (Up to date)" else "${device.pendingUpdates.size} available",
                    color = if (device.pendingUpdates.isEmpty()) TuesdayTheme.HealthyGreen else statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // High-framerate hardware-accelerated Candle Canvas indicator
        CandleGraphCanvas(score = device.healthScore)

        // Expandable Diff & Actions
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                if (device.pendingUpdates.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TuesdayTheme.HealthyGreen, modifier = Modifier.size(14.dp))
                        Text(
                            text = "No pending ${device.packageManager.rawValue} updates reported.",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    device.pendingUpdates.forEach { pkg ->
                        PackageDiffRow(update = pkg)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Button(
                        onClick = onPoll,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TuesdayTheme.ElevatedPill,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Poll Agent", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (device.pendingUpdates.isNotEmpty()) {
                        Button(
                            onClick = onPatch,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TuesdayTheme.HealthyGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simulate Patch Run", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TuesdayTheme.CardSurface)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Dns, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(42.dp))
        Text("No Monitored Devices", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "Inventory your routers, IoT devices, Linux servers, or Home Assistant HACS nodes by photo or agent enrollment.",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun FooterSection(baseUrl: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = TuesdayTheme.HacsCyan, modifier = Modifier.size(14.dp))
            Text("Synced with $baseUrl", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        Text(
            text = "Tuesday.sh • Calm Package Telemetry for Home Assistant & Linux Fleets",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp
        )
    }
}
