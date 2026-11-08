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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayTheme
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent
import sh.tuesday.app.ui.uiSlot

/**
 * Model representing an actionable step inside the [NotchBulletinCard].
 */
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
 * Reusable Fused Notch Bulletin Card component.
 *
 * Implements the 4-corner seamless fused notch pattern:
 * - Upper-Left Notch: Status pill (e.g. "BULLETIN" with pulsing LED dot)
 * - Upper-Right Notch: Contextual reference/info link (e.g. "RHYTHM ⓘ")
 * - Center Body: Headline, description, and hairline-divided action checklist
 * - Lower Full-Width Notch Bar: Primary action (Snooze or Mark Complete) + Secondary action (Ignore)
 *
 * All notch containers are inset by 1.dp against the outer card border to prevent alpha compounding.
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .uiComponent("NotchBulletinCard")
            .uiSlot("rhythm-reminder-panel")
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surface)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
    ) {
        // 1. Top-Left Corner Notch Pill ("BULLETIN")
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 1.dp, start = 1.dp)
                .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 0.dp, bottomEnd = 8.dp, bottomStart = 0.dp))
                .background(palette.accent.copy(alpha = 0.12f))
                .clickable { onLeftNotchClick() }
                .drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    val strokeColor = palette.accent.copy(alpha = 0.45f)
                    val cornerRadius = 8.dp.toPx()
                    val w = size.width
                    val h = size.height

                    val path = Path().apply {
                        moveTo(0f, h)
                        lineTo(w - cornerRadius, h)
                        arcTo(
                            rect = Rect(
                                left = w - 2 * cornerRadius,
                                top = h - 2 * cornerRadius,
                                right = w,
                                bottom = h
                            ),
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = -90f,
                            forceMoveTo = false
                        )
                        lineTo(w, 0f)
                    }
                    drawPath(path = path, color = strokeColor, style = Stroke(width = strokeWidth))
                }
                .padding(horizontal = 9.dp, vertical = 5.dp)
                .uiAction("clickLeftNotch"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(leftNotchDotColor)
                )
                Text(
                    text = leftNotchLabel,
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // 2. Top-Right Corner Notch Link ("RHYTHM ⓘ")
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 1.dp, end = 1.dp)
                .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 9.dp, bottomEnd = 0.dp, bottomStart = 8.dp))
                .background(palette.accent.copy(alpha = 0.12f))
                .clickable { onRightNotchClick() }
                .drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    val strokeColor = palette.accent.copy(alpha = 0.45f)
                    val cornerRadius = 8.dp.toPx()
                    val w = size.width
                    val h = size.height

                    val path = Path().apply {
                        moveTo(w, h)
                        lineTo(cornerRadius, h)
                        arcTo(
                            rect = Rect(
                                left = 0f,
                                top = h - 2 * cornerRadius,
                                right = 2 * cornerRadius,
                                bottom = h
                            ),
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false
                        )
                        lineTo(0f, 0f)
                    }
                    drawPath(path = path, color = strokeColor, style = Stroke(width = strokeWidth))
                }
                .padding(horizontal = 9.dp, vertical = 5.dp)
                .uiAction("clickRightNotch"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = rightNotchLabel,
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                )
                Canvas(modifier = Modifier.size(13.dp)) {
                    val strokeWidth = 1.3.dp.toPx()
                    val color = palette.accent
                    val w = size.width
                    val h = size.height
                    val r = w / 2f

                    // Outer circle
                    drawCircle(
                        color = color,
                        radius = r - strokeWidth / 2,
                        center = Offset(r, r),
                        style = Stroke(width = strokeWidth)
                    )
                    // Info 'i' dot
                    drawCircle(
                        color = color,
                        radius = 1.15.dp.toPx(),
                        center = Offset(r, h * 0.28f)
                    )
                    // Info 'i' vertical body stem
                    drawLine(
                        color = color,
                        start = Offset(r, h * 0.44f),
                        end = Offset(r, h * 0.74f),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // 3. Central Content Column (Headline + Prose + Divided Checklist)
        Column(
            modifier = Modifier
                .fillMaxWidth()
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

            // Step Checklist
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
                                color = if (step.isDone) TuesdayTheme.LedOkGreen else palette.accent,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .clickable { step.onClick() }
                                    .padding(start = 6.dp)
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

        // 4. Bottom Fused Action Notches Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 1.dp, start = 1.dp, end = 1.dp)
        ) {
            // Left CTA Notch: Primary Action (Snooze or Mark Complete)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(bottomStart = 9.dp))
                    .background(if (allStepsDone) TuesdayTheme.LedOkGreen.copy(alpha = 0.22f) else palette.accent.copy(alpha = 0.18f))
                    .clickable { onPrimaryAction() }
                    .drawBehind {
                        val strokeWidth = 1.dp.toPx()
                        val strokeColor = if (allStepsDone) TuesdayTheme.LedOkGreen.copy(alpha = 0.55f) else palette.accent.copy(alpha = 0.45f)
                        val w = size.width
                        drawLine(
                            color = strokeColor,
                            start = Offset(0f, 0f),
                            end = Offset(w, 0f),
                            strokeWidth = strokeWidth
                        )
                        drawLine(
                            color = strokeColor,
                            start = Offset(w, 0f),
                            end = Offset(w, size.height),
                            strokeWidth = strokeWidth
                        )
                    }
                    .padding(vertical = 11.dp)
                    .uiAction("primaryNotchAction"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
                            color = palette.accent,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.1).sp
                        )
                    }
                }
            }

            // Right CTA Notch: Secondary Action (Ignore / Dismiss)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(bottomEnd = 9.dp))
                    .background(palette.accent.copy(alpha = 0.05f))
                .clickable { onSecondaryAction() }
                .drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    val strokeColor = palette.accent.copy(alpha = 0.45f)
                    drawLine(
                        color = strokeColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = strokeWidth
                    )
                }
                .padding(vertical = 11.dp)
                .uiAction("secondaryNotchAction"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
            }
        }
    }
}
