package sh.tuesday.app.ui.tabs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.domain.RhythmPostureEvaluator
import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.BulletinInboxView
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.models.RackPortDevice
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayDimens
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.components.HelpIconButton
import sh.tuesday.app.ui.components.SurfaceListDivider
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

fun hasActiveCheckupRecommendation(
    devices: List<RackPortDevice>,
    welcomeComplete: Boolean = false
): Boolean = RhythmPostureEvaluator.hasActiveRecommendation(devices, welcomeComplete)

@Composable
fun RhythmScreen(
    flavor: AppBrandFlavor,
    palette: BrandPalette,
    devices: List<RackPortDevice>,
    bulletins: List<BulletinInboxView> = emptyList(),
    welcomeComplete: Boolean = false,
    onOpenReferenceGuide: () -> Unit = {}
) {
    val isStn = flavor == AppBrandFlavor.SURETHING_HOME
    val context = LocalContext.current

    val rhythms = remember(flavor, devices, bulletins, welcomeComplete) {
        RhythmPostureEvaluator.evaluatePosture(
            flavor = flavor,
            devices = devices,
            bulletins = bulletins,
            welcomeComplete = welcomeComplete
        )
    }

    fun accentColorForRhythm(id: String): Color = when (id) {
        "r1" -> TuesdayTheme.ledColorFor(PortLifecycleState.EOL, isStn)
        "r2" -> palette.accentText
        "r3" -> TuesdayTheme.ledColorFor(PortLifecycleState.CVE_KEV, isStn)
        "r4" -> TuesdayTheme.overhangColor(isStn)
        else -> palette.accentText
    }

    val amberColor = TuesdayTheme.ledColorFor(PortLifecycleState.EOL, isStn)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .uiComponent("SeasonalCheckupScreen")
            .padding(horizontal = TuesdayDimens.ScreenPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .uiSlot("r2-quarterly-tuneup-card"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Check-Ups",
                            color = palette.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Box(modifier = Modifier.uiSlot("help-button")) {
                            HelpIconButton(
                                palette = palette,
                                onClick = onOpenReferenceGuide,
                                modifier = Modifier.uiAction("openReferenceGuide")
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 28.dp)
                                .uiAction("askClaudeCheckup")
                                .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                .background(amberColor.copy(alpha = 0.14f))
                                .border(1.dp, amberColor, RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                .clickable {
                                    val prompt = "I am reviewing my network check-up:\n\n" +
                                        RhythmPostureEvaluator.formatShareReport(flavor, rhythms)
                                    val url = "https://claude.ai/new?q=" + Uri.encode(prompt)
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✦ Ask Claude",
                                color = amberColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .heightIn(min = 28.dp)
                                .uiAction("shareCheckupReport")
                                .clip(RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                .background(palette.elevated)
                                .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusBadge))
                                .clickable {
                                    val reportText = RhythmPostureEvaluator.formatShareReport(flavor, rhythms)
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, reportText)
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Check-Up Report"))
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Share",
                                color = palette.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rhythms.forEach { rhythm ->
                        val hasAction = rhythm.action != null
                        val topperShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        val accentColor = if (hasAction) accentColorForRhythm(rhythm.id) else palette.textMuted
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(topperShape)
                                    .background(if (hasAction) palette.surface else palette.surface.copy(alpha = 0.6f))
                                    .border(1.dp, palette.border, topperShape),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(if (hasAction) 4.dp else 3.dp)
                                        .height(34.dp)
                                        .background(accentColor)
                                )
                                Text(
                                    text = rhythm.title,
                                    color = accentColor,
                                    fontSize = if (isStn) 12.sp else 11.sp,
                                    fontWeight = if (hasAction) FontWeight.ExtraBold else FontWeight.Bold,
                                    fontFamily = if (isStn) FontFamily.Default else FontFamily.Monospace,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }

                            if (!hasAction) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "Everything OK",
                                        color = palette.textMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                SurfaceListDivider(palette)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = rhythm.action ?: "",
                                        color = palette.textPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                SurfaceListDivider(palette)
                                if (rhythm.recommendation != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "💡 Recommendation: ${rhythm.recommendation}",
                                            color = palette.textMuted,
                                            fontSize = 12.sp
                                        )
                                    }
                                    SurfaceListDivider(palette)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .size(0.dp)
                    .uiSlot("r3-annual-review-card")
            )
        }
    }
}
