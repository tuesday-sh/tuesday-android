package sh.tuesday.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import sh.tuesday.app.theme.BrandPalette

/**
 * Value-moment Notification Pre-Permission Primer shown right after the user enrolls
 * their first device (never on cold Day-0 launch).
 */
@Composable
fun NotificationPrimerDialog(
    palette: BrandPalette,
    appName: String,
    deviceName: String?,
    onEnableSelected: (criticalEnabled: Boolean, patchTuesdayEnabled: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var criticalEnabled by remember { mutableStateOf(true) }
    var patchTuesdayEnabled by remember { mutableStateOf(true) }
    val previewDevice = deviceName?.takeIf { it.isNotBlank() } ?: "UniFi Dream Machine Pro"

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(20.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (!deviceName.isNullOrBlank()) {
                    "Filter advisories for $deviceName?"
                } else {
                    "Stay ahead of CVEs & Patch Tuesday"
                },
                color = palette.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 22.sp
            )

            Text(
                text = "$appName tracks manufacturer and security researcher notices from global feeds and filters only what applies to your network inventory.",
                color = palette.textMuted,
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            )

            // Realistic Notification Preview Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.elevated)
                    .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$appName • PREVIEW",
                        color = palette.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "now",
                        color = palette.textMuted,
                        fontSize = 10.sp
                    )
                }
                Text(
                    text = "Critical firmware update for $previewDevice",
                    color = palette.textPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "CVE-2026-4102 patched in latest firmware. Tap to review advisory.",
                    color = palette.textMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }

            // 2 Rhythm Toggles
            PrimerToggleRow(
                palette = palette,
                title = "Critical CVEs & safety recalls",
                subtitle = "Immediate alerts for vulnerabilities matching your gear",
                checked = criticalEnabled,
                onCheckedChange = { criticalEnabled = it }
            )
            PrimerToggleRow(
                palette = palette,
                title = "Monthly Patch Tuesday rhythm",
                subtitle = "2nd Tuesday 5-minute check-up & backup reminder",
                checked = patchTuesdayEnabled,
                onCheckedChange = { patchTuesdayEnabled = it }
            )

            Text(
                text = "Only alerts you choose. Never marketing spam.",
                color = palette.textMuted,
                fontSize = 11.sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(palette.accent)
                    .clickable { onEnableSelected(criticalEnabled, patchTuesdayEnabled) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Enable Selected Alerts",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                    .clickable { onDismiss() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Not now",
                    color = palette.textMuted,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PrimerToggleRow(
    palette: BrandPalette,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.elevated)
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = palette.textMuted,
                fontSize = 11.sp
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = palette.accent)
        )
    }
}
