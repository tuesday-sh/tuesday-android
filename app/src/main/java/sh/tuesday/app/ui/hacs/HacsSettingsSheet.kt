package sh.tuesday.app.ui.hacs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.HacsConfig
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HacsSettingsSheet(
    config: HacsConfig,
    simulateWifiDisconnected: Boolean,
    onSaveConfig: (HacsConfig) -> Unit,
    onToggleSimulateWifi: (Boolean) -> Unit,
    onResetDemoData: () -> Unit,
    onExportBackupJson: () -> String,
    onImportBackupJson: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var importStatusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessStatus by remember { mutableStateOf(false) }

    // Launcher for exporting backup to system storage (Google Drive, Files, Downloads)
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonContent = onExportBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(jsonContent.toByteArray(Charsets.UTF_8))
                }
                importStatusMessage = "Backup exported successfully!"
                isSuccessStatus = true
                Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                importStatusMessage = "Export failed: ${e.localizedMessage}"
                isSuccessStatus = false
            }
        }
    }

    // Launcher for importing backup from system storage
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                val content = reader.readText()
                reader.close()
                inputStream?.close()

                val success = onImportBackupJson(content)
                if (success) {
                    importStatusMessage = "Inventory & settings restored!"
                    isSuccessStatus = true
                    Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show()
                } else {
                    importStatusMessage = "Invalid backup file format"
                    isSuccessStatus = false
                }
            } catch (e: Exception) {
                importStatusMessage = "Import failed: ${e.localizedMessage}"
                isSuccessStatus = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TuesdayTheme.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .uiComponent("HacsSettingsSheet")
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("hacs-bridge-header")
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
                        text = "Home Assistant Integration",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tuesday.sh Bridge",
                        color = TuesdayTheme.HacsCyan,
                        fontSize = 12.sp
                    )
                }
            }

            // Coming Soon Notice Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TuesdayTheme.CardSurface)
                    .padding(vertical = 24.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Home Assistant sync coming soon.",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Native integration for Home Assistant and HACS is currently in development.",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // Sovereign Data Backup & Migration Panel (Local Export / Import)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TuesdayTheme.CardSurface)
                    .border(1.dp, Color(0xFF2C3240), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Local Backup",
                        tint = TuesdayTheme.LedOkGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Local Network Backup & Restore",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Save your full network inventory, device aliases, port allocations, and settings off-device (Google Drive, iCloud, local storage) without creating an account.",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Export Button
                    Button(
                        onClick = {
                            val defaultFileName = "tuesday_backup_${System.currentTimeMillis() / 1000}.json"
                            exportFileLauncher.launch(defaultFileName)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .uiAction("exportBackupFile")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export JSON", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    // Import Button
                    Button(
                        onClick = {
                            importFileLauncher.launch("application/json")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .uiAction("importBackupFile")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import JSON", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Copy to Clipboard Fallback
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF14171F))
                        .clickable {
                            val jsonText = onExportBackupJson()
                            clipboardManager.setText(AnnotatedString(jsonText))
                            importStatusMessage = "Copied JSON payload to clipboard"
                            isSuccessStatus = true
                            Toast.makeText(context, "Copied backup to clipboard", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Copy Backup to Clipboard",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "COPY",
                        color = TuesdayTheme.HacsCyan,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Status Message if present
                if (importStatusMessage != null) {
                    Text(
                        text = importStatusMessage!!,
                        color = if (isSuccessStatus) TuesdayTheme.LedOkGreen else TuesdayTheme.LedCveRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Dismiss Button (maintains parity action contract)
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TuesdayTheme.HacsCyan.copy(alpha = 0.15f),
                    contentColor = TuesdayTheme.HacsCyan
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .uiAction("saveHacsConfig")
            ) {
                Text("Close", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
