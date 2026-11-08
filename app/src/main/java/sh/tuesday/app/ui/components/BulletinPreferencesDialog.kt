package sh.tuesday.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent

/**
 * Dialog popped when the user clicks 'Ignore' on the Patch Tuesday rhythm bulletin.
 * Offers two options matching the canonical Web Workbench specification:
 * 1. 🔕 Mute: Turns off monthly checklist reminders while preserving critical CVEs & recalls.
 * 2. Ignore until next month: Dismisses/snoozes the bulletin until next cycle.
 */
@Composable
fun BulletinPreferencesDialog(
    palette: BrandPalette,
    onDismissRequest: () -> Unit,
    onConfirmMute: () -> Unit,
    onConfirmIgnoreUntilNextMonth: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onDismissRequest() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .uiComponent("BulletinPreferencesDialog")
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                    .clickable(enabled = false) {}
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⚙️", fontSize = 16.sp)
                        Text(
                            text = "Bulletin Preferences",
                            color = palette.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "✕",
                        color = palette.textMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onDismissRequest() }
                            .padding(4.dp)
                    )
                }

                // Question & Description
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Do you want to mute Patch Tuesday monthly rhythm?",
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = "Muting turns off all routine updates and monthly checklist reminders for this rhythm. Critical CVEs and hardware recalls will still be delivered.",
                        color = palette.textMuted,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                }

                // Actions
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onConfirmMute,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiAction("confirmMuteBulletinRhythm")
                    ) {
                        Text(
                            text = "🔕 Mute",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onConfirmIgnoreUntilNextMonth,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = palette.textPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .uiAction("confirmIgnoreUntilNextMonth")
                    ) {
                        Text(
                            text = "Ignore until next month",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
