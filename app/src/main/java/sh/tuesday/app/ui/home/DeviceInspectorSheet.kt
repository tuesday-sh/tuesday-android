package sh.tuesday.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.BulletinsAndInventoryEngine
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.models.RackPortDevice
import sh.tuesday.app.models.TrustZone
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.tabs.MiniCandleBar
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

@Composable
internal fun DeviceInspectorBottomSheet(
    device: RackPortDevice,
    palette: BrandPalette,
    isStn: Boolean,
    onApplyPatch: () -> Unit,
    onEditDevice: () -> Unit = {},
    onNavigateToBulletins: (String?) -> Unit = {},
    onClose: () -> Unit
) {
    val stateColor = TuesdayTheme.ledColorFor(device.portState, isStn)
    val noticeCount = remember(device.id, device.purls) {
        BulletinsAndInventoryEngine.countBulletinsForDevice(device)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiComponent("DeviceInspectorSheet", "device=${device.id};state=${device.portState.key}")
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .uiSlot("inspector-header"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = device.name,
                color = palette.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TextButton(
                    onClick = onEditDevice,
                    modifier = Modifier.uiAction("editDevice")
                ) {
                    Text("Edit", color = palette.accent)
                }
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.uiAction("closeDeviceInspector")
                ) {
                    Text(if (isStn) "Done" else "Close", color = palette.accent)
                }
            }
        }

        // Clickable banner/button near the top of the drawer navigating to the Bulletins tab
        if (noticeCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(stateColor.copy(alpha = 0.12f))
                    .border(1.dp, stateColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { onNavigateToBulletins(BulletinsAndInventoryEngine.findBulletinsForDevice(device).firstOrNull()?.externalId) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📢",
                        fontSize = 13.sp
                    )
                    Column {
                        Text(
                            text = if (noticeCount == 1) "1 Advisory Bulletin for this device" else "$noticeCount Advisory Bulletins for this device",
                            color = palette.textPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap to view in Bulletins feed",
                            color = stateColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = "View →",
                    color = stateColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Timeline Candlestick Graph
        val operationalCandle = remember(device.id, device.version, device.presenceState) {
            BulletinsAndInventoryEngine.toOperationalCandle(device)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(palette.bg)
                .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                .padding(12.dp)
                .uiSlot("candlestick-chart"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Timeline",
                    color = palette.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Color.White)
                    )
                    Text(
                        text = "Today",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            MiniCandleBar(
                candle = operationalCandle,
                modifier = Modifier.fillMaxWidth().height(16.dp),
                showTodayMarker = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Released ${operationalCandle.releaseDateStr ?: "Launch"} • Active ${operationalCandle.deployDateStr ?: "Install"}",
                    color = palette.textMuted,
                    fontSize = 10.sp
                )
                Text(
                    text = "EOL: ${operationalCandle.eolDateStr ?: "TBD"}",
                    color = if (operationalCandle.isPastEol) TuesdayTheme.CriticalRed else palette.textMuted,
                    fontSize = 10.sp,
                    fontWeight = if (operationalCandle.isPastEol) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        // Condensed Device Information Panel (matching Timeline panel styling)
        val catLabel = if (isStn) device.category.stnLabel else device.category.proLabel
        val zoneLabel = if (isStn) device.trustZone.stnLabel else "${device.trustZone.displayLabel} (${device.trustZone.shortCode})"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(palette.bg)
                .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                .padding(12.dp)
                .uiSlot("device-details-stack"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InspectorKeyValueRow("CATEGORY", catLabel, palette)
            InspectorKeyValueRow("ROOM & PLACEMENT", device.roomLocation, palette)
            InspectorKeyValueRow("NETWORK LANE / ZONE", zoneLabel, palette)
            if (device.purls.isNotEmpty()) {
                InspectorKeyValueRow("CANONICAL PURL", device.purls.first(), palette)
            }
        }

        // Active Bulletins Stack (when multiple notices pile up on this device)
        val activeBulletins = remember(device.id, device.purls, device.portState) {
            BulletinsAndInventoryEngine.findBulletinsForDevice(device)
        }
        if (activeBulletins.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("device-active-bulletins-stack"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "ACTIVE NOTICES (${activeBulletins.size})",
                    color = palette.textMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
                activeBulletins.forEach { b ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                            .clickable { onNavigateToBulletins(b.externalId) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val bTypeColor = when (b.bulletinType.lowercase()) {
                                    "recall", "cisa_kev", "cve" -> TuesdayTheme.LedCveRed
                                    "eol" -> TuesdayTheme.LedEolAmber
                                    else -> TuesdayTheme.LedUpdateBlue
                                }
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(bTypeColor)
                                )
                                Text(
                                    text = b.bulletinType.uppercase(),
                                    color = bTypeColor,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = if (isStn) b.stnTitle else b.proTitle,
                                color = palette.textPrimary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "→",
                            color = palette.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }

        if (device.portState != PortLifecycleState.OK && device.portState != PortLifecycleState.UNASSIGNED) {
            Button(
                onClick = onApplyPatch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TuesdayTheme.LedOkGreen,
                    contentColor = if (isStn) Color.White else Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("remediation-cta")
                    .uiAction("markRemediationComplete")
                    .padding(top = 4.dp, bottom = 14.dp)
            ) {
                Text(
                    text = if (isStn) "✓ Mark Remediation Complete (Set Device OK)" else "✓ Mark Remediation Complete (Set Port OK)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
internal fun EditDevicePanel(
    device: RackPortDevice,
    palette: BrandPalette,
    isStn: Boolean,
    onCancel: () -> Unit,
    onSave: (name: String, shortLabel: String, roomLocation: String, trustZone: TrustZone, assetTag: String) -> Unit
) {
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiComponent("EditDevicePanel", "device=${device.id}")
            .padding(bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                fontSize = 9.sp,
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
                    val selected = draftZone == zone
                    ZoneFilterPill(
                        label = if (isStn) zone.stnLabel else "${zone.shortCode} ${zone.stnLabel}",
                        selected = selected,
                        palette = palette,
                        onClick = { draftZone = zone }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .uiAction("cancelDeviceEdit")
            ) {
                Text("Cancel", color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = { onSave(draftName, draftShortLabel, draftRoom, draftZone, draftAssetTag) },
                enabled = draftName.isNotBlank(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .uiAction("saveDeviceEdit")
            ) {
                Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun InspectorKeyValueRow(label: String, value: String, palette: BrandPalette) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = palette.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
        Text(
            text = value,
            color = palette.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
