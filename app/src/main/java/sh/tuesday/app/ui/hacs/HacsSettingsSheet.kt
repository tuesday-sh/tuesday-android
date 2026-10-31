package sh.tuesday.app.ui.hacs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCode
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
import sh.tuesday.app.models.HacsConfig
import sh.tuesday.app.theme.TuesdayTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HacsSettingsSheet(
    config: HacsConfig,
    simulateWifiDisconnected: Boolean,
    onSaveConfig: (HacsConfig) -> Unit,
    onToggleSimulateWifi: (Boolean) -> Unit,
    onResetDemoData: () -> Unit,
    onDismiss: () -> Unit
) {
    var instanceName by remember { mutableStateOf(config.instanceName) }
    var baseUrl by remember { mutableStateOf(config.baseUrl) }
    var apiToken by remember { mutableStateOf(config.apiToken) }
    var geminiApiKey by remember { mutableStateOf(config.geminiApiKey) }
    var requireLocalWifi by remember { mutableStateOf(config.requireLocalWifi) }
    var isSimulatingWifi by remember { mutableStateOf(simulateWifiDisconnected) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TuesdayTheme.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TuesdayTheme.CardSurface)
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(TuesdayTheme.HacsCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "HACS",
                        tint = TuesdayTheme.HacsCyan
                    )
                }
                Column {
                    Text(
                        text = "Tuesday.sh HACS Bridge",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Component v${config.hacsComponentVersion} • Server v${config.tuesdayServerVersion}",
                        color = TuesdayTheme.HacsCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Fields
            OutlinedTextField(
                value = instanceName,
                onValueChange = { instanceName = it },
                label = { Text("INSTANCE NAME", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TuesdayTheme.HacsCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                )
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("HACS / TUESDAY SERVER URL", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TuesdayTheme.HacsCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                )
            )

            OutlinedTextField(
                value = apiToken,
                onValueChange = { apiToken = it },
                label = { Text("HACS API TOKEN", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TuesdayTheme.HacsCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                )
            )

            OutlinedTextField(
                value = geminiApiKey,
                onValueChange = { geminiApiKey = it },
                label = { Text("GEMINI API KEY (OPTIONAL OVERRIDE)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TuesdayTheme.HacsCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                )
            )

            Button(
                onClick = {
                    instanceName = "Home Assistant OS (lml HACS)"
                    baseUrl = "http://homeassistant.local:8123/api/tuesday"
                    apiToken = "tuesday_hacs_qr_${(1000..9999).random()}"
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = TuesdayTheme.HacsCyan.copy(alpha = 0.15f),
                    contentColor = TuesdayTheme.HacsCyan
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Simulate HACS Pairing QR Payload", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            // Toggles
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TuesdayTheme.CardSurface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Require Local Wi-Fi (LAN Guard)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Blocks dashboard if disconnected from LAN", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                    Switch(
                        checked = requireLocalWifi,
                        onCheckedChange = { requireLocalWifi = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = TuesdayTheme.HealthyGreen)
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Simulate Wi-Fi Disconnected", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Test the Wi-Fi warning overlay on device", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                    Switch(
                        checked = isSimulatingWifi,
                        onCheckedChange = {
                            isSimulatingWifi = it
                            onToggleSimulateWifi(it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = TuesdayTheme.WarningAmber)
                    )
                }
            }

            // Actions
            Button(
                onClick = {
                    val updated = config.copy(
                        instanceName = instanceName.trim(),
                        baseUrl = baseUrl.trim(),
                        apiToken = apiToken.trim(),
                        geminiApiKey = geminiApiKey.trim(),
                        requireLocalWifi = requireLocalWifi,
                        lastSyncedEpochMs = System.currentTimeMillis()
                    )
                    onSaveConfig(updated)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = TuesdayTheme.HealthyGreen,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save HACS Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            TextButton(
                onClick = {
                    onResetDemoData()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset Demo Fleet Data", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
