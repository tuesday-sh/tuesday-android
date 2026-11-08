package sh.tuesday.app.ui.hacs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.HacsConfig
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

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
                    .padding(vertical = 36.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Home Assistant sync coming soon.",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Native integration for Home Assistant and HACS is currently in development.",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
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
