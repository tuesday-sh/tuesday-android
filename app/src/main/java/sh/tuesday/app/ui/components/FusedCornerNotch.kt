package sh.tuesday.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared 1dp-inset corner-notch geometry primitives used by [SingleNotchCard] and [NotchBulletinCard].
 * Draws seamless curved inner arcs without outer border alpha compounding.
 */
@Composable
fun BoxScope.FusedTopStartNotch(
    accentColor: Color,
    strokeColor: Color,
    label: String,
    dotColor: Color = accentColor,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .align(Alignment.TopStart)
            .padding(top = 1.dp, start = 1.dp)
            .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 0.dp, bottomEnd = 8.dp, bottomStart = 0.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
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
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun BoxScope.FusedTopEndNotch(
    accentColor: Color,
    strokeColor: Color,
    label: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable RowScope.() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .align(Alignment.TopEnd)
            .padding(top = 1.dp, end = 1.dp)
            .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 9.dp, bottomEnd = 0.dp, bottomStart = 8.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
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
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = label,
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            )
            if (trailingIcon != null) {
                trailingIcon()
            }
        }
    }
}

@Composable
fun BoxScope.FusedBottomDualNotchBar(
    primaryBackground: Color,
    primaryStrokeColor: Color,
    onPrimaryClick: () -> Unit,
    primaryModifier: Modifier = Modifier,
    primaryContent: @Composable RowScope.() -> Unit,
    secondaryBackground: Color,
    secondaryStrokeColor: Color,
    onSecondaryClick: () -> Unit,
    secondaryModifier: Modifier = Modifier,
    secondaryContent: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .padding(bottom = 1.dp, start = 1.dp, end = 1.dp)
    ) {
        Box(
            modifier = primaryModifier
                .weight(1f)
                .clip(RoundedCornerShape(bottomStart = 9.dp))
                .background(primaryBackground)
                .clickable(onClick = onPrimaryClick)
                .drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    val w = size.width
                    drawLine(
                        color = primaryStrokeColor,
                        start = Offset(0f, 0f),
                        end = Offset(w, 0f),
                        strokeWidth = strokeWidth
                    )
                    drawLine(
                        color = primaryStrokeColor,
                        start = Offset(w, 0f),
                        end = Offset(w, size.height),
                        strokeWidth = strokeWidth
                    )
                }
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = primaryContent
            )
        }

        Box(
            modifier = secondaryModifier
                .weight(1f)
                .clip(RoundedCornerShape(bottomEnd = 9.dp))
                .background(secondaryBackground)
                .clickable(onClick = onSecondaryClick)
                .drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    drawLine(
                        color = secondaryStrokeColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = strokeWidth
                    )
                }
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = secondaryContent
            )
        }
    }
}
