package sh.tuesday.app.ui.hacs

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Group
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.domain.EntitlementGatePolicy
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayDimens
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.components.MonospaceBadge
import sh.tuesday.app.ui.components.TuesdayBottomSheet
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HacsSettingsSheet(
    entitlementTier: String = "free",
    activeNetworkName: String = "My Network",
    palette: BrandPalette = TuesdayTheme.colors,
    onOpenUpgradeModal: () -> Unit = {},
    onOpenNetworkPicker: () -> Unit = {},
    onExportBackupJson: () -> String,
    onImportBackupJson: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var importStatusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessStatus by remember { mutableStateOf(false) }
    var familyInviteFeedback by remember { mutableStateOf<String?>(null) }
    val isProTier1 = EntitlementGatePolicy.isProTier1Entitled(entitlementTier)
    val okGreen = TuesdayTheme.ledColorFor(PortLifecycleState.OK, palette.isStn)
    val cardShape = RoundedCornerShape(TuesdayDimens.RadiusModalCard)

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

    TuesdayBottomSheet(
        palette = palette,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .uiComponent("HacsSettingsSheet")
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("hacs-bridge-header")
                    .clip(cardShape)
                    .background(palette.elevated)
                    .border(1.dp, palette.border, cardShape)
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(palette.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "HACS",
                        tint = palette.accent
                    )
                }
                Column {
                    Text(
                        text = "Home Assistant Integration",
                        color = palette.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tuesday.sh Bridge",
                        color = palette.accentText,
                        fontSize = 12.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(palette.elevated)
                    .border(1.dp, palette.border, cardShape)
                    .padding(vertical = 24.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Home Assistant sync coming soon.",
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Native integration for Home Assistant and HACS is currently in development.",
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(palette.elevated)
                    .border(1.dp, palette.border, cardShape)
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
                        tint = okGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Local Network Backup & Restore",
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Save your full network inventory, device aliases, port allocations, and settings off-device (Google Drive, iCloud, local storage) without creating an account.",
                    color = palette.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val defaultFileName = "tuesday_backup_${System.currentTimeMillis() / 1000}.json"
                            exportFileLauncher.launch(defaultFileName)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = palette.surface,
                            contentColor = palette.textPrimary
                        ),
                        shape = RoundedCornerShape(TuesdayDimens.RadiusControl),
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
                        Text("Export JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            importFileLauncher.launch("application/json")
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = palette.surface,
                            contentColor = palette.textPrimary
                        ),
                        shape = RoundedCornerShape(TuesdayDimens.RadiusControl),
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
                        Text("Import JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TuesdayDimens.HitTargetCompact)
                        .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                        .background(palette.bg)
                        .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusBadge))
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
                        color = palette.textPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "COPY",
                        color = palette.accentText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (importStatusMessage != null) {
                    Text(
                        text = importStatusMessage!!,
                        color = if (isSuccessStatus) okGreen else TuesdayTheme.ledColorFor(PortLifecycleState.CVE_KEV, palette.isStn),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("family-site-management-panel")
                    .clip(cardShape)
                    .background(palette.elevated)
                    .border(
                        width = 1.dp,
                        color = if (isProTier1) okGreen.copy(alpha = 0.45f) else palette.accent.copy(alpha = 0.45f),
                        shape = cardShape
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Family Site Management",
                            tint = if (isProTier1) okGreen else palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Family Site Management",
                            color = palette.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    MonospaceBadge(
                        text = if (isProTier1) "PRO TIER 1 • ACTIVE" else "PRO TIER 1",
                        color = if (isProTier1) okGreen else palette.accentText
                    )
                }

                Text(
                    text = if (isProTier1) {
                        "Included with your Pro Tier 1 entitlement. Manage multiple household or family locations, delegate co-admin access, and share pairing invites for '${activeNetworkName.ifBlank { "My Network" }}'."
                    } else {
                        "Part of Tuesday.sh Pro Tier 1. Remotely monitor parents' or family members' home networks, manage unlimited sites, and share delegated pairing invites."
                    },
                    color = palette.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                if (isProTier1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val inviteToken = "tuesday://join-site?site=${Uri.encode(activeNetworkName.ifBlank { "My Network" })}&code=FAM-${(1000..9999).random()}"
                                clipboardManager.setText(AnnotatedString(inviteToken))
                                familyInviteFeedback = "✓ Copied Family Pairing Invite link to clipboard"
                                Toast.makeText(context, "Copied family invite link", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = palette.surface,
                                contentColor = palette.textPrimary
                            ),
                            shape = RoundedCornerShape(TuesdayDimens.RadiusControl),
                            modifier = Modifier
                                .weight(1f)
                                .uiAction("copyFamilySiteInvite")
                        ) {
                            Text("Copy Family Invite", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                onDismiss()
                                onOpenNetworkPicker()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(TuesdayDimens.RadiusControl),
                            modifier = Modifier
                                .weight(1f)
                                .uiAction("manageFamilySites")
                        ) {
                            Text("Manage Sites", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (familyInviteFeedback != null) {
                        Text(
                            text = familyInviteFeedback!!,
                            color = okGreen,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenUpgradeModal()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(TuesdayDimens.RadiusControl),
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiAction("openFamilyTier1Upgrade")
                    ) {
                        Text("⚡ Unlock with Pro Tier 1", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.accent.copy(alpha = 0.15f),
                    contentColor = palette.accentText
                ),
                shape = RoundedCornerShape(TuesdayDimens.RadiusControl),
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
