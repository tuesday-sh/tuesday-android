package sh.tuesday.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import sh.tuesday.app.R
import sh.tuesday.app.models.StnHardwareCategory

@Composable
internal fun HardwareCategorySvgIcon(
    category: StnHardwareCategory,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sx = w / 24f
        val sy = h / 24f
        val stroke = Stroke(
            width = 1.8f * sx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        when (category) {
            StnHardwareCategory.C1_GATEWAY -> {
                // <rect x="2" y="7" width="20" height="10" rx="2"/>
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(2f * sx, 7f * sy),
                    size = Size(20f * sx, 10f * sy),
                    cornerRadius = CornerRadius(2f * sx, 2f * sy),
                    style = stroke
                )
                // <circle cx="6" cy="12" r="1.2"/>
                drawCircle(
                    color = tint,
                    radius = 1.3f * sx,
                    center = Offset(6f * sx, 12f * sy),
                    style = stroke
                )
                // <path d="M11 10h8v4h-8z"/>
                drawRect(
                    color = tint,
                    topLeft = Offset(11f * sx, 10f * sy),
                    size = Size(8f * sx, 4f * sy),
                    style = stroke
                )
            }
            StnHardwareCategory.C2_SWITCH -> {
                // <rect x="2" y="6" width="20" height="12" rx="2"/>
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(2f * sx, 6f * sy),
                    size = Size(20f * sx, 12f * sy),
                    cornerRadius = CornerRadius(2f * sx, 2f * sy),
                    style = stroke
                )
                // 2x3 port squares inside chassis
                val xs = listOf(5.5f, 10.5f, 15.5f)
                val ys = listOf(8.8f, 13.2f)
                ys.forEach { y ->
                    xs.forEach { x ->
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(x * sx, y * sy),
                            size = Size(2.6f * sx, 2.2f * sy),
                            cornerRadius = CornerRadius(0.5f * sx, 0.5f * sy),
                            style = Stroke(width = 1.3f * sx)
                        )
                    }
                }
            }
            StnHardwareCategory.C3_AP -> {
                // <circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="3.5"/>
                drawCircle(
                    color = tint,
                    radius = 9f * sx,
                    center = Offset(12f * sx, 12f * sy),
                    style = stroke
                )
                drawCircle(
                    color = tint,
                    radius = 3.5f * sx,
                    center = Offset(12f * sx, 12f * sy),
                    style = stroke
                )
            }
            StnHardwareCategory.C4_SERVER -> {
                // <rect x="3" y="4" width="18" height="6" rx="1.5"/>
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(3f * sx, 4f * sy),
                    size = Size(18f * sx, 6f * sy),
                    cornerRadius = CornerRadius(1.5f * sx, 1.5f * sy),
                    style = stroke
                )
                // <rect x="3" y="14" width="18" height="6" rx="1.5"/>
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(3f * sx, 14f * sy),
                    size = Size(18f * sx, 6f * sy),
                    cornerRadius = CornerRadius(1.5f * sx, 1.5f * sy),
                    style = stroke
                )
                drawCircle(color = tint, radius = 1.1f * sx, center = Offset(7f * sx, 7f * sy))
                drawCircle(color = tint, radius = 1.1f * sx, center = Offset(7f * sx, 17f * sy))
            }
            StnHardwareCategory.C5_CAMERA -> {
                // Camera body + prism + center lens
                val path = Path().apply {
                    moveTo(14.5f * sx, 4f * sy)
                    lineTo(9.5f * sx, 4f * sy)
                    lineTo(7f * sx, 7f * sy)
                    lineTo(4f * sx, 7f * sy)
                    lineTo(4f * sx, 20f * sy)
                    lineTo(20f * sx, 20f * sy)
                    lineTo(20f * sx, 7f * sy)
                    lineTo(17f * sx, 7f * sy)
                    close()
                }
                drawPath(path = path, color = tint, style = stroke)
                drawCircle(
                    color = tint,
                    radius = 3.5f * sx,
                    center = Offset(12f * sx, 13.5f * sy),
                    style = stroke
                )
            }
            StnHardwareCategory.C6_IOT -> {
                // <rect x="4" y="4" width="16" height="16" rx="3"/><path d="M9 12h6M12 9v6"/>
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(4f * sx, 4f * sy),
                    size = Size(16f * sx, 16f * sy),
                    cornerRadius = CornerRadius(3f * sx, 3f * sy),
                    style = stroke
                )
                drawLine(
                    color = tint,
                    start = Offset(9f * sx, 12f * sy),
                    end = Offset(15f * sx, 12f * sy),
                    strokeWidth = 1.8f * sx,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(12f * sx, 9f * sy),
                    end = Offset(12f * sx, 15f * sy),
                    strokeWidth = 1.8f * sx,
                    cap = StrokeCap.Round
                )
            }
            StnHardwareCategory.C7_CLIENT -> {
                // <rect x="4" y="4" width="16" height="11" rx="1.5"/><path d="M2 19h20M9 19v-4M15 19v-4"/>
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(4f * sx, 4f * sy),
                    size = Size(16f * sx, 11f * sy),
                    cornerRadius = CornerRadius(1.5f * sx, 1.5f * sy),
                    style = stroke
                )
                drawLine(
                    color = tint,
                    start = Offset(2f * sx, 19f * sy),
                    end = Offset(22f * sx, 19f * sy),
                    strokeWidth = 1.8f * sx,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(9f * sx, 15f * sy),
                    end = Offset(9f * sx, 19f * sy),
                    strokeWidth = 1.8f * sx
                )
                drawLine(
                    color = tint,
                    start = Offset(15f * sx, 15f * sy),
                    end = Offset(15f * sx, 19f * sy),
                    strokeWidth = 1.8f * sx
                )
            }
            StnHardwareCategory.C8_GUEST -> {
                // <circle cx="12" cy="7" r="4"/><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/>
                drawCircle(
                    color = tint,
                    radius = 4f * sx,
                    center = Offset(12f * sx, 7.5f * sy),
                    style = stroke
                )
                val body = Path().apply {
                    moveTo(5f * sx, 21f * sy)
                    lineTo(5f * sx, 18.5f * sy)
                    cubicTo(5f * sx, 16f * sy, 7f * sx, 15f * sy, 9.5f * sx, 15f * sy)
                    lineTo(14.5f * sx, 15f * sy)
                    cubicTo(17f * sx, 15f * sy, 19f * sx, 16f * sy, 19f * sx, 18.5f * sy)
                    lineTo(19f * sx, 21f * sy)
                }
                drawPath(path = body, color = tint, style = stroke)
            }
        }
    }
}

@Composable
internal fun TuesdayCalendarHeartIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(id = R.drawable.ic_tuesday_mark),
        contentDescription = "Tuesday.sh",
        tint = tint,
        modifier = modifier
    )
}
