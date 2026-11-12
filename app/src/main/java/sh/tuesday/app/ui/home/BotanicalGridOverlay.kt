package sh.tuesday.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.InventoryScaleTier
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.ui.uiSlot

@Composable
internal fun BotanicalGridOverlay(
    scaleTier: InventoryScaleTier,
    activeCount: Int,
    isStn: Boolean,
    palette: BrandPalette
) {
    val fgColor = palette.textMuted

    when (scaleTier) {
        InventoryScaleTier.DAY_ZERO -> {
            // PNW Misty Mountain Horizon with centered status pill
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Mountain silhouette resting along bottom
                PixelArtMountainHorizon(
                    color = fgColor.copy(alpha = if (isStn) 0.35f else 0.38f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.BottomCenter)
                )

                // Status pill vertically & horizontally centered in the 128-LED grid using canonical BrandPalette tokens
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .uiSlot("inventory-empty-pill")
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.elevated)
                        .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Inventory Empty",
                        color = palette.textMuted,
                        fontSize = 9.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(
                                includeFontPadding = false
                            )
                        )
                    )
                }
            }
        }
        InventoryScaleTier.PEA -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PixelArtSprout(
                        color = fgColor.copy(alpha = 0.85f),
                        size = 32.dp
                    )
                }
            }
        }
        InventoryScaleTier.GARDEN -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom
                ) {
                    PixelArtPineTree(color = fgColor.copy(alpha = 0.75f), width = 24.dp, height = 34.dp)
                    PixelArtPineTree(color = fgColor.copy(alpha = 0.75f), width = 30.dp, height = 42.dp)
                    PixelArtPineTree(color = fgColor.copy(alpha = 0.75f), width = 20.dp, height = 28.dp)
                }
            }
        }
        InventoryScaleTier.FOREST -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.Bottom
                ) {
                    repeat(9) { idx ->
                        val isTall = idx % 2 == 0
                        PixelArtPineTree(
                            color = fgColor.copy(alpha = 0.45f),
                            width = if (isTall) 22.dp else 18.dp,
                            height = if (isTall) 32.dp else 26.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PixelArtSprout(color: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val px = this.size.width / 16f
        val py = this.size.height / 16f
        fun rect(x: Int, y: Int, w: Int, h: Int) {
            drawRect(color = color, topLeft = Offset(x * px, y * py), size = Size(w * px, h * py))
        }
        // Left leaf
        rect(2, 3, 3, 1); rect(1, 4, 5, 1); rect(1, 5, 5, 1); rect(2, 6, 4, 1); rect(4, 7, 2, 1)
        // Right leaf
        rect(10, 1, 3, 1); rect(9, 2, 5, 1); rect(9, 3, 6, 1); rect(9, 4, 5, 1); rect(8, 5, 4, 1); rect(8, 6, 2, 1)
        // Stem & Soil mound
        rect(6, 7, 3, 1); rect(7, 8, 2, 4); rect(6, 12, 3, 1); rect(5, 13, 5, 1); rect(3, 14, 9, 1); rect(4, 15, 7, 1)
    }
}

@Composable
internal fun PixelArtMountainHorizon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cols = 64
        val colW = w / cols.toFloat()

        // Secondary background ridge (fainter)
        val bgCol = color.copy(alpha = color.alpha * 0.45f)
        val pathBg = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.55f)
            lineTo(w * 0.22f, h * 0.35f)
            lineTo(w * 0.38f, h * 0.52f)
            lineTo(w * 0.72f, h * 0.22f) // Prominent Cascade peak
            lineTo(w * 0.88f, h * 0.42f)
            lineTo(w, h * 0.38f)
            lineTo(w, h)
            close()
        }
        drawPath(path = pathBg, color = bgCol)

        // Stepped pixel ridge line
        for (i in 0 until cols) {
            val fracX = i.toFloat() / cols.toFloat()
            // Mount Rainier silhouette formula: sharp crest at 0.68, shoulder at 0.30
            val elev = when {
                fracX in 0.15f..0.45f -> {
                    val p = 1f - kotlin.math.abs(fracX - 0.30f) / 0.15f
                    p * 0.55f
                }
                fracX in 0.52f..0.84f -> {
                    val p = 1f - kotlin.math.abs(fracX - 0.68f) / 0.16f
                    p * 0.82f
                }
                else -> 0.15f
            }
            val stepY = h - elev * h
            drawRect(
                color = color,
                topLeft = Offset(i * colW, stepY),
                size = Size(colW + 0.5f, h - stepY)
            )
        }
    }
}

@Composable
internal fun PixelArtPineTree(color: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val px = this.size.width / 16f
        val py = this.size.height / 22f
        fun rect(x: Int, y: Int, w: Int, h: Int) {
            drawRect(color = color, topLeft = Offset(x * px, y * py), size = Size(w * px, h * py))
        }
        rect(7, 0, 2, 2)
        rect(6, 2, 4, 2)
        rect(5, 4, 6, 2)
        rect(6, 6, 4, 2)
        rect(4, 8, 8, 2)
        rect(3, 10, 10, 2)
        rect(5, 12, 6, 2)
        rect(2, 14, 12, 2)
        rect(1, 16, 14, 2)
        rect(7, 18, 2, 4)
    }
}
