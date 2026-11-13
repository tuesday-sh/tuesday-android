package sh.tuesday.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.theme.TuesdayDimens
import sh.tuesday.app.theme.TuesdayTheme

@Composable
fun WifiWarningOverlay(
    isVisible: Boolean,
    hacsUrl: String,
    onDisableRequirement: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    val palette = TuesdayTheme.colors
    val shape = RoundedCornerShape(TuesdayDimens.RadiusDialog)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(shape)
                .background(palette.surface)
                .border(1.dp, palette.border, shape)
                .padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = "Wi-Fi Disconnected",
                tint = TuesdayTheme.WarningAmber,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Local Wi-Fi Required",
                color = palette.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tuesday.sh is configured to communicate with your Home Assistant HACS integration ($hacsUrl) only while connected to your local network.",
                color = palette.textMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDisableRequirement,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TuesdayTheme.WarningAmber,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(TuesdayDimens.RadiusControl),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Bypass Local LAN Guard",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
