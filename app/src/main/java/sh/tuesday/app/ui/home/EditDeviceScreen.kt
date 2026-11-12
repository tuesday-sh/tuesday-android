package sh.tuesday.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.TrustZone
import sh.tuesday.app.services.TuesdayRepository
import sh.tuesday.app.theme.TuesdayTheme
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
    val device = devices.find { it.id == deviceId }

    if (device == null) {
        LaunchedEffect(deviceId) { onNavigateBack() }
        return
    }

    var draftName by remember(device.id) { mutableStateOf(device.name) }
    var draftShortLabel by remember(device.id) { mutableStateOf(device.shortLabel) }
    var draftRoom by remember(device.id) { mutableStateOf(device.roomLocation) }
    var draftAssetTag by remember(device.id) { mutableStateOf(device.assetTag) }
    var draftZone by remember(device.id) { mutableStateOf(device.trustZone) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = palette.accent,
        unfocusedBorderColor = palette.border,
        focusedTextColor = palette.textPrimary,
        unfocusedTextColor = palette.textPrimary,
        focusedLabelColor = palette.accent,
        unfocusedLabelColor = palette.textMuted
    )

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
                        onClick = onNavigateBack,
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
            OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it },
                label = { Text("Device Name", fontSize = 11.sp) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("edit-device-name")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = draftShortLabel,
                    onValueChange = { draftShortLabel = it },
                    label = { Text("Short Label", fontSize = 11.sp) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier
                        .weight(1f)
                        .uiSlot("edit-device-short-label")
                )
                OutlinedTextField(
                    value = draftAssetTag,
                    onValueChange = { draftAssetTag = it },
                    label = { Text("Asset Tag", fontSize = 11.sp) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier
                        .weight(1f)
                        .uiSlot("edit-device-asset-tag")
                )
            }

            OutlinedTextField(
                value = draftRoom,
                onValueChange = { draftRoom = it },
                label = { Text("Room & Placement", fontSize = 11.sp) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("edit-device-room")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isStn) "NETWORK LANE" else "TRUST ZONE",
                    color = palette.textMuted,
                    fontSize = 9.5.sp,
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
                        ZoneFilterPill(
                            label = if (isStn) zone.stnLabel else "${zone.shortCode} ${zone.stnLabel}",
                            selected = draftZone == zone,
                            palette = palette,
                            onClick = { draftZone = zone }
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
                    onClick = onNavigateBack,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
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
                            assetTag = draftAssetTag
                        )
                        onNavigateBack()
                    },
                    enabled = draftName.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .uiAction("saveDeviceEdit")
                ) {
                    Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
