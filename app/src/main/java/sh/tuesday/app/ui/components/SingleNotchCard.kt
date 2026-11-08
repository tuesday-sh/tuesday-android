package sh.tuesday.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.ui.uiAction
import sh.tuesday.app.ui.uiComponent

/**
 * Reusable Single Fused Upper-Left Notch Card container.
 *
 * Implements the canonical fused corner notch pattern:
 * - Upper-Left Corner Notch: Inset 1dp border, seamless fused geometry with an inner 8dp curve arc,
 *   housing a status indicator dot and monospace tag.
 * - Upper-Right Header Slot: Space for timestamps, auxiliary badges, or secondary metadata.
 * - Central Content Slot: Ample breathing space (42dp top padding) separating headline/body from the notch.
 *
 * Use this foundational container for compact alerts, single-item bulletins (CVE, EOL, Release),
 * or status telemetry panels.
 */
@Composable
fun SingleNotchCard(
    palette: BrandPalette,
    accentColor: Color,
    notchLabel: String,
    notchDotColor: Color = accentColor,
    onNotchClick: () -> Unit = {},
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    cardBorderColor: Color = accentColor.copy(alpha = 0.35f),
    topEndHeader: (@Composable RowScope.() -> Unit)? = null,
    contentTopPadding: Dp = 42.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .uiComponent("SingleNotchCard")
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surface)
            .border(1.dp, cardBorderColor, RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        // 1. Upper-Left Corner Notch (Fused Pill)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 1.dp, start = 1.dp)
                .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 0.dp, bottomEnd = 8.dp, bottomStart = 0.dp))
                .background(accentColor.copy(alpha = 0.12f))
                .clickable { onNotchClick() }
                .drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    val strokeColor = cardBorderColor
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
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.5.dp)
                        .clip(CircleShape)
                        .background(notchDotColor)
                )
                Text(
                    text = notchLabel,
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // 2. Upper-Right Header Slot (Timestamps, secondary tags, etc.)
        if (topEndHeader != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = topEndHeader
            )
        }

        // 3. Central Content Column (Breathing space between notch and title)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = contentTopPadding, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            content = content
        )
    }
}
