package sh.tuesday.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayDimens
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

data class BulletinChecklistStep(
    val id: String,
    val num: String,
    val title: String,
    val description: String,
    val linkText: String,
    val isDone: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Reusable 4-corner Fused Notch Bulletin Card backed by [FusedTopStartNotch], [FusedTopEndNotch],
 * and [FusedBottomDualNotchBar].
 */
@Composable
fun NotchBulletinCard(
    palette: BrandPalette,
    title: String,
    description: String,
    leftNotchLabel: String = "BULLETIN",
    leftNotchDotColor: Color = TuesdayTheme.LedCveRed,
    onLeftNotchClick: () -> Unit = {},
    rightNotchLabel: String = "RHYTHM",
    onRightNotchClick: () -> Unit = {},
    steps: List<BulletinChecklistStep> = emptyList(),
    allStepsDone: Boolean = false,
    primaryActionLabel: String = "Snooze 24h",
    completeActionLabel: String = "Mark Complete",
    secondaryActionLabel: String = "Ignore",
    onPrimaryAction: () -> Unit = {},
    onSecondaryAction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val borderColor = if (allStepsDone) TuesdayTheme.LedOkGreen.copy(alpha = 0.45f) else palette.accent.copy(alpha = 0.45f)
    val accentStroke = palette.accent.copy(alpha = 0.45f)
    val shape = RoundedCornerShape(TuesdayDimens.RadiusCard)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .uiComponent("NotchBulletinCard")
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, borderColor, shape)
    ) {
        FusedTopStartNotch(
            accentColor = palette.accent,
            strokeColor = accentStroke,
            label = leftNotchLabel,
            dotColor = leftNotchDotColor,
            onClick = onLeftNotchClick,
            modifier = Modifier.uiAction("clickLeftNotch")
        )

        FusedTopEndNotch(
            accentColor = palette.accent,
            strokeColor = accentStroke,
            label = rightNotchLabel,
            onClick = onRightNotchClick,
            modifier = Modifier.uiAction("clickRightNotch"),
            trailingIcon = {
                Canvas(modifier = Modifier.size(13.dp)) {
                    val strokeWidth = 1.3.dp.toPx()
                    val color = palette.accent
                    val w = size.width
                    val h = size.height
                    val r = w / 2f
                    drawCircle(
                        color = color,
                        radius = r - strokeWidth / 2,
                        center = Offset(r, r),
                        style = Stroke(width = strokeWidth)
                    )
                    drawCircle(
                        color = color,
                        radius = 1.15.dp.toPx(),
                        center = Offset(r, h * 0.28f)
                    )
                    drawLine(
                        color = color,
                        start = Offset(r, h * 0.44f),
                        end = Offset(r, h * 0.74f),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .uiSlot("rhythm-reminder-panel")
                .padding(start = 14.dp, end = 14.dp, top = 44.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp
            )
            Text(
                text = description,
                color = palette.textMuted,
                fontSize = 13.sp,
                lineHeight = 17.5.sp,
                fontWeight = FontWeight.Normal
            )

            steps.forEachIndexed { index, step ->
                HorizontalDivider(
                    color = palette.border.copy(alpha = 0.5f),
                    thickness = 0.6.dp,
                    modifier = if (index == 0) Modifier.padding(top = 2.dp) else Modifier
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (step.isDone) TuesdayTheme.LedOkGreen else palette.elevated)
                            .border(
                                1.dp,
                                if (step.isDone) TuesdayTheme.LedOkGreen else palette.border,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (step.isDone) "✓" else step.num,
                            color = if (step.isDone) Color.White else palette.textPrimary,
                            fontSize = if (step.isDone) 12.sp else 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = step.title,
                                color = palette.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = step.linkText,
                                color = if (step.isDone) TuesdayTheme.LedOkGreen else palette.accentText,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .heightIn(min = 24.dp)
                                    .clickable { step.onClick() }
                                    .padding(start = 6.dp, top = 3.dp, bottom = 3.dp)
                            )
                        }
                        Text(
                            text = step.description,
                            color = palette.textMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        FusedBottomDualNotchBar(
            primaryBackground = if (allStepsDone) TuesdayTheme.LedOkGreen.copy(alpha = 0.22f) else palette.accent.copy(alpha = 0.18f),
            primaryStrokeColor = if (allStepsDone) TuesdayTheme.LedOkGreen.copy(alpha = 0.55f) else accentStroke,
            onPrimaryClick = onPrimaryAction,
            primaryModifier = Modifier.uiAction("primaryNotchAction"),
            primaryContent = {
                if (allStepsDone) {
                    Text(
                        text = "✓",
                        color = TuesdayTheme.LedOkGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = completeActionLabel,
                        color = TuesdayTheme.LedOkGreen,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.1).sp
                    )
                } else {
                    Canvas(modifier = Modifier.size(14.dp)) {
                        val strokeWidth = 1.4.dp.toPx()
                        val color = palette.accent
                        val w = size.width
                        val h = size.height
                        val r = w / 2f - strokeWidth / 2
                        drawCircle(
                            color = color,
                            radius = r,
                            center = Offset(w / 2f, h / 2f),
                            style = Stroke(width = strokeWidth)
                        )
                        drawLine(
                            color = color,
                            start = Offset(w / 2f, h / 2f),
                            end = Offset(w / 2f, h * 0.28f),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = color,
                            start = Offset(w / 2f, h / 2f),
                            end = Offset(w * 0.72f, h / 2f),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                    Text(
                        text = primaryActionLabel,
                        color = palette.accentText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.1).sp
                    )
                }
            },
            secondaryBackground = palette.accent.copy(alpha = 0.05f),
            secondaryStrokeColor = accentStroke,
            onSecondaryClick = onSecondaryAction,
            secondaryModifier = Modifier.uiAction("secondaryNotchAction"),
            secondaryContent = {
                Canvas(modifier = Modifier.size(11.dp)) {
                    val strokeWidth = 1.4.dp.toPx()
                    val color = palette.textMuted
                    val w = size.width
                    val h = size.height
                    drawLine(
                        color = color,
                        start = Offset(1.dp.toPx(), 1.dp.toPx()),
                        end = Offset(w - 1.dp.toPx(), h - 1.dp.toPx()),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = color,
                        start = Offset(w - 1.dp.toPx(), 1.dp.toPx()),
                        end = Offset(1.dp.toPx(), h - 1.dp.toPx()),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
                Text(
                    text = secondaryActionLabel,
                    color = palette.textMuted,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.1).sp
                )
            }
        )
    }
}
