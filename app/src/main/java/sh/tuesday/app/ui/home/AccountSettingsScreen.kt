package sh.tuesday.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.services.GoldenCorpusRecorder
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme

@Composable
fun AccountSettingsScreen(
    flavor: AppBrandFlavor,
    palette: BrandPalette,
    networkLocationName: String,
    activeDeviceCount: Int,
    isDeveloperMode: Boolean,
    onSaveNetworkName: (String) -> Unit,
    onToggleDeveloperMode: (Boolean) -> Unit,
    onSetSimulatedDeviceCount: (Int) -> Unit,
    onOpenIntakeFlow: () -> Unit,
    onSwitchToFleetTab: () -> Unit
) {
    val isStn = flavor == AppBrandFlavor.SURETHING_HOME
    var draftName by remember(networkLocationName) { mutableStateOf(networkLocationName) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var savedCorpusCount by remember { mutableStateOf(GoldenCorpusRecorder.getSavedSessionCount(context)) }
    var purgeFeedback by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isStn) "Account & Home Settings" else "Account & Network Settings",
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(TuesdayTheme.LedOkGreen.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isStn) "Protected" else "STN-Pro",
                        color = TuesdayTheme.LedOkGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Network / Location Name Field
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isStn) "HOME / NETWORK NAME" else "NETWORK / LOCATION NAME",
                    color = palette.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.border
                        )
                    )
                    Button(
                        onClick = { onSaveNetworkName(draftName) },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Text(
                    text = if (isStn) {
                        "Used on your home health dashboard and family safety notifications."
                    } else {
                        "Displayed in the console header and synced to Quarterly Audit exports."
                    },
                    color = palette.textMuted,
                    fontSize = 10.5.sp
                )
            }

            // Profile Summary Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.bg)
                    .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AccountSummaryRow(
                    label = if (isStn) "Home Location" else "Profile Tier",
                    value = if (isStn) networkLocationName else "$networkLocationName • STN-Pro",
                    valueColor = palette.textPrimary,
                    palette = palette
                )
                AccountSummaryRow(
                    label = if (isStn) "Protected Devices" else "Tracked Ports",
                    value = if (isStn) "$activeDeviceCount Protected" else "$activeDeviceCount Active (128 Cap)",
                    valueColor = TuesdayTheme.LedOkGreen,
                    palette = palette
                )
                AccountSummaryRow(
                    label = if (isStn) "Protection Status" else "Cloud Sync",
                    value = if (isStn) "Active 24/7 Monitoring" else "Connected • Zero-Knowledge",
                    valueColor = TuesdayTheme.LedOkGreen,
                    palette = palette
                )
            }

            // Developer Mode Toggle & In-App Inventory State Tester
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.accent.copy(alpha = 0.08f))
                    .border(1.dp, palette.accent.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "🛠️ Developer Mode",
                                color = palette.textPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(palette.accent.copy(alpha = 0.16f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (isStn) "LAB" else "INTERNAL",
                                    color = palette.accent,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Text(
                            text = if (isStn) {
                                "Test different household device scales and prototype states."
                            } else {
                                "Enables in-app state controls & diagnostic testing tools."
                            },
                            color = palette.textMuted,
                            fontSize = 10.5.sp
                        )
                    }
                    Switch(
                        checked = isDeveloperMode,
                        onCheckedChange = onToggleDeveloperMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = palette.accent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = palette.border
                        )
                    )
                }

                if (isDeveloperMode) {
                    HorizontalDivider(color = palette.accent.copy(alpha = 0.2f))
                    Text(
                        text = if (isStn) "SIMULATE HOUSEHOLD SIZE:" else "QUICK STATE SIMULATION:",
                        color = palette.textMuted,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DevStateSimButton(
                            label = "🔄 Reset (Day 0)",
                            isSelected = activeDeviceCount == 0,
                            palette = palette,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onSetSimulatedDeviceCount(0)
                                onSwitchToFleetTab()
                            }
                        )
                        DevStateSimButton(
                            label = if (isStn) "🌐 1 Router (Seed)" else "🌐 1 Node (Seed)",
                            isSelected = activeDeviceCount == 1,
                            palette = palette,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onSetSimulatedDeviceCount(1)
                                onSwitchToFleetTab()
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DevStateSimButton(
                            label = if (isStn) "🌱 7 Devices (Small)" else "🌱 7 Nodes (Small)",
                            isSelected = activeDeviceCount == 7,
                            palette = palette,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onSetSimulatedDeviceCount(7)
                                onSwitchToFleetTab()
                            }
                        )
                        DevStateSimButton(
                            label = if (isStn) "🌲 12 Devices (Full)" else "🌲 12 Nodes (Fleet)",
                            isSelected = activeDeviceCount == 12,
                            palette = palette,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onSetSimulatedDeviceCount(12)
                                onSwitchToFleetTab()
                            }
                        )
                    }

                    if (!isStn) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.accent.copy(alpha = 0.18f))
                                .border(1.dp, palette.accent, RoundedCornerShape(6.dp))
                                .clickable {
                                    onSetSimulatedDeviceCount(12)
                                    onSwitchToFleetTab()
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📦 Load Full Pro View Mock",
                                color = palette.accent,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Offline Golden Corpus Purge Control
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(TuesdayTheme.LedCveRed.copy(alpha = 0.14f))
                            .border(1.dp, TuesdayTheme.LedCveRed.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .clickable {
                                scope.launch {
                                    val removed = GoldenCorpusRecorder.purgeAllCaptures(context)
                                    savedCorpusCount = 0
                                    purgeFeedback = "Purged $removed capture session(s) & gallery exports"
                                }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = purgeFeedback ?: "🗑️ Purge Golden Corpus Captures ($savedCorpusCount Saved)",
                            color = TuesdayTheme.LedCveRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Open Camera Intake Flow Button
            OutlinedButton(
                onClick = onOpenIntakeFlow,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isStn) "+ Name New Device with Camera" else "+ Open Intake / Camera Clues Flow",
                    color = palette.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AccountSummaryRow(
    label: String,
    value: String,
    valueColor: Color,
    palette: BrandPalette
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = palette.textMuted, fontSize = 11.sp)
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun DevStateSimButton(
    label: String,
    isSelected: Boolean,
    palette: BrandPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) palette.accent.copy(alpha = 0.2f) else palette.surface)
            .border(1.dp, if (isSelected) palette.accent else palette.border, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) palette.accent else palette.textPrimary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
