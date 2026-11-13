package sh.tuesday.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayDimens
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent

/**
 * Dialog popped when the user clicks 'Ignore' on the Patch Tuesday rhythm bulletin.
 * Standardized on [TuesdayModalCard] with floating top-right `>= 36.dp` close button.
 */
@Composable
fun BulletinPreferencesDialog(
    palette: BrandPalette,
    onDismissRequest: () -> Unit,
    onConfirmMute: () -> Unit,
    onConfirmIgnoreUntilNextMonth: () -> Unit
) {
    TuesdayModalCard(
        palette = palette,
        title = "Bulletin Preferences",
        onDismissRequest = onDismissRequest,
        modifier = Modifier.uiComponent("BulletinPreferencesDialog"),
        leadingContent = {
            Text("⚙️", fontSize = 16.sp)
        }
    ) {
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
                shape = RoundedCornerShape(TuesdayDimens.RadiusCard),
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
                shape = RoundedCornerShape(TuesdayDimens.RadiusCard),
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
