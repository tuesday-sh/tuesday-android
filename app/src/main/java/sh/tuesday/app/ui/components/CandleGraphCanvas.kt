package sh.tuesday.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.theme.TuesdayTheme
import kotlin.math.roundToInt

/**
 * High-framerate hardware-accelerated Candle Health Indicator rendered directly on Jetpack Compose Canvas.
 * Glides smoothly between 0% and 100% using spring physics for 60/120Hz displays.
 */
@Composable
fun CandleGraphCanvas(
    score: Double,
    modifier: Modifier = Modifier
) {
    val clampedScore = score.coerceIn(0.0, 100.0)
    val targetFraction = (clampedScore / 100.0).toFloat()

    val animatedFraction = remember { Animatable(targetFraction) }

    LaunchedEffect(targetFraction) {
        animatedFraction.animateTo(
            targetValue = targetFraction,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val scoreColor = when {
        score < 40.0 -> TuesdayTheme.CriticalRed
        score < 75.0 -> TuesdayTheme.WarningAmber
        else -> TuesdayTheme.HealthyGreen
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Package Health Score",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${clampedScore.roundToInt()}%",
                color = scoreColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
        ) {
            val trackHeight = 8.dp.toPx()
            val candleWidth = 6.dp.toPx()
            val candleHeight = 16.dp.toPx()
            val cornerRadius = CornerRadius(trackHeight / 2, trackHeight / 2)

            val trackTop = (size.height - trackHeight) / 2f
            val candleTop = (size.height - candleHeight) / 2f

            // 1. Draw smooth gradient track
            val trackBrush = Brush.horizontalGradient(
                colors = listOf(
                    TuesdayTheme.CriticalRed,
                    TuesdayTheme.WarningAmber,
                    TuesdayTheme.HealthyGreen
                )
            )

            drawRoundRect(
                brush = trackBrush,
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeight),
                cornerRadius = cornerRadius
            )

            // 2. Compute candle indicator position with sub-pixel precision
            val availableWidth = size.width - candleWidth
            val candleX = (availableWidth * animatedFraction.value).coerceIn(0f, availableWidth)

            // 3. Draw ambient glow behind candle
            drawRoundRect(
                color = scoreColor.copy(alpha = 0.35f),
                topLeft = Offset(candleX - 2.dp.toPx(), candleTop - 2.dp.toPx()),
                size = Size(candleWidth + 4.dp.toPx(), candleHeight + 4.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // 4. Draw crisp white candle indicator
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(candleX, candleTop),
                size = Size(candleWidth, candleHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}
