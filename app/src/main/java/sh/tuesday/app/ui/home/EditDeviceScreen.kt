package sh.tuesday.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.models.TrustZone
import sh.tuesday.app.services.TuesdayRepository
import sh.tuesday.app.theme.TuesdayDimens
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.components.SelectableFilterChip
import sh.tuesday.app.ui.components.TuesdayTextField
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDeviceScreen(
    deviceId: String,
    repository: TuesdayRepository,
    onNavigateBack: () -> Unit
) {
    val devices by repository.rackDevices.collectAsState()
    val brandFlavor by repository.brandFlavor.collectAsState()
    val palette = TuesdayTheme.paletteFor(brandFlavor)
    val isStn = brandFlavor == AppBrandFlavor.SURETHING_HOME
    val liveDevice = devices.find { it.id == deviceId }
    var cachedDevice by remember(deviceId) { mutableStateOf(liveDevice) }
    if (liveDevice != null) {
        cachedDevice = liveDevice
    }
    var hasNavigatedBack by remember(deviceId) { mutableStateOf(false) }
    val navigateBackOnce: () -> Unit = {
        if (!hasNavigatedBack) {
            hasNavigatedBack = true
            onNavigateBack()
        }
    }
    val device = cachedDevice
    if (device == null) {
        LaunchedEffect(deviceId) { navigateBackOnce() }
        return
    }

    var draftName by remember(device.id) { mutableStateOf(device.name) }
    var draftShortLabel by remember(device.id) { mutableStateOf(device.shortLabel) }
    var draftRoom by remember(device.id) { mutableStateOf(device.roomLocation) }
    var draftZone by remember(device.id) { mutableStateOf(device.trustZone) }
    var showDeleteConfirmDialog by remember(device.id) { mutableStateOf(false) }
    val dangerColor = TuesdayTheme.ledColorFor(PortLifecycleState.CVE_KEV, isStn)

    Scaffold(
        containerColor = palette.bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Edit Device",
                        color = palette.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navigateBackOnce,
                        modifier = Modifier.uiAction("cancelDeviceEdit")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = palette.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .uiComponent("EditDeviceScreen", "device=${device.id}"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TuesdayTextField(
                value = draftName,
                onValueChange = { draftName = it },
                label = "Device Name",
                palette = palette,
                modifier = Modifier.uiSlot("edit-device-name")
            )

            TuesdayTextField(
                value = draftShortLabel,
                onValueChange = { draftShortLabel = it },
                label = "Short Label",
                palette = palette,
                modifier = Modifier.uiSlot("edit-device-short-label")
            )

            TuesdayTextField(
                value = draftRoom,
                onValueChange = { draftRoom = it },
                label = "Room & Placement",
                palette = palette,
                modifier = Modifier.uiSlot("edit-device-room")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isStn) "NETWORK LANE" else "TRUST ZONE",
                    color = palette.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TrustZone.entries.forEach { zone ->
                        val zoneColor = TuesdayTheme.zoneColorFor(zone, isStn)
                        SelectableFilterChip(
                            label = if (isStn) zone.stnLabel else "${zone.shortCode} ${zone.stnLabel}",
                            selected = draftZone == zone,
                            onClick = { draftZone = zone },
                            palette = palette,
                            accentColor = zoneColor,
                            leadingDotColor = zoneColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = navigateBackOnce,
                    shape = RoundedCornerShape(TuesdayDimens.RadiusControl),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = TuesdayDimens.HitTargetStandard)
                        .uiAction("cancelDeviceEdit")
                ) {
                    Text("Cancel", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = {
                        repository.updateDeviceDetails(
                            deviceId = device.id,
                            name = draftName,
                            shortLabel = draftShortLabel,
                            roomLocation = draftRoom,
                            trustZone = draftZone,
                            assetTag = device.assetTag
                        )
                        navigateBackOnce()
                    },
                    enabled = draftName.isNotBlank(),
                    shape = RoundedCornerShape(TuesdayDimens.RadiusControl),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = TuesdayDimens.HitTargetStandard)
                        .uiAction("saveDeviceEdit")
                ) {
                    Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = palette.border)

            OutlinedButton(
                onClick = { showDeleteConfirmDialog = true },
                border = BorderStroke(1.dp, dangerColor.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(TuesdayDimens.RadiusControl),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TuesdayDimens.HitTargetStandard)
                    .uiAction("requestDeleteDevice")
            ) {
                Text("Delete Device", color = dangerColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = palette.surface,
            title = {
                Text(
                    text = "Delete ${device.name}?",
                    color = palette.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This removes the device from your local inventory. If this hardware was decommissioned after active service, consider marking it Retired in Check-In instead.",
                    color = palette.textMuted,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = false },
                    modifier = Modifier.uiAction("cancelDeleteDevice")
                ) {
                    Text("Cancel", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        repository.deleteDevice(device.id)
                        navigateBackOnce()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = dangerColor),
                    modifier = Modifier.uiAction("confirmDeleteDevice")
                ) {
                    Text("Delete Device", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
