package sh.tuesday.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.tuesday.app.models.DevicePresenceState
import sh.tuesday.app.models.OperationalCandle
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.services.SubscriptionPlanOffer
import sh.tuesday.app.theme.BrandPalette
import sh.tuesday.app.theme.TuesdayDimens
import sh.tuesday.app.theme.TuesdayTheme
import kotlin.math.min

/**
 * Shared subscription plan selector cards used in [sh.tuesday.app.ui.home.Tier1UpgradeModalDialog]
 * and [sh.tuesday.app.ui.home.AccountSettingsScreen].
 */
@Composable
fun PlanOfferSelector(
    plans: List<SubscriptionPlanOffer>,
    selectedProductId: String,
    onSelectPlan: (String) -> Unit,
    palette: BrandPalette,
    modifier: Modifier = Modifier,
    cardModifier: (SubscriptionPlanOffer) -> Modifier = { Modifier }
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        plans.forEach { offer ->
            val isSelected = selectedProductId == offer.productId
            val shape = RoundedCornerShape(TuesdayDimens.RadiusCard)
            Column(
                modifier = cardModifier(offer)
                    .weight(1f)
                    .clip(shape)
                    .background(if (isSelected) palette.accent.copy(alpha = 0.14f) else palette.elevated)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) palette.accent else palette.border,
                        shape = shape
                    )
                    .clickable { onSelectPlan(offer.productId) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = offer.title,
                        color = if (isSelected) palette.accentText else palette.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (offer.badge != null) {
                        Text(
                            text = offer.badge,
                            color = TuesdayTheme.ledColorFor(PortLifecycleState.OK, palette.isStn),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    text = "${offer.formattedPrice} ${offer.periodLabel}",
                    color = palette.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * Palette-aware condensed candlestick bar supporting both `TUESDAY_PRO` dark and `SURETHING_HOME` light flavors.
 */
@Composable
fun CondensedMiniCandleBar(
    candle: OperationalCandle,
    palette: BrandPalette,
    modifier: Modifier = Modifier,
    showTodayMarker: Boolean = false
) {
    val isStn = palette.isStn
    val okColor = TuesdayTheme.ledColorFor(PortLifecycleState.OK, isStn)
    val cveColor = TuesdayTheme.ledColorFor(PortLifecycleState.CVE_KEV, isStn)
    val eolColor = TuesdayTheme.ledColorFor(PortLifecycleState.EOL, isStn)
    val overhangColor = TuesdayTheme.overhangColor(isStn)

    Canvas(
        modifier = modifier
            .height(14.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(palette.chassisRecessed)
            .border(1.dp, palette.border, RoundedCornerShape(3.dp))
    ) {
        val w = size.width
        val h = size.height
        val yCenter = h / 2f
        val candleH = 6.dp.toPx()
        val cTop = yCenter - candleH / 2f

        val xRel = candle.marketReleaseNorm * w
        val xDeployStart = candle.deployStartNorm * w
        val xDeployEnd = candle.deployEndNorm * w
        val xEol = candle.vendorEolNorm * w

        if (xDeployStart > xRel) {
            drawLine(
                color = palette.textMuted,
                start = Offset(xRel, yCenter),
                end = Offset(xDeployStart, yCenter),
                strokeWidth = 1.2.dp.toPx()
            )
            drawCircle(palette.textMuted, radius = 1.8.dp.toPx(), center = Offset(xRel, yCenter))
        }

        val xSupportedEnd = min(xDeployEnd, xEol)
        val bodyW = (xSupportedEnd - xDeployStart).coerceAtLeast(3.dp.toPx())
        val bodyColor = when {
            candle.presenceState != DevicePresenceState.ACTIVE ->
                TuesdayTheme.presenceColorFor(candle.presenceState, isStn)
            candle.hasCveOrRecall -> cveColor
            candle.isPastEol -> overhangColor
            candle.isEolNearing -> eolColor
            else -> okColor
        }

        when (candle.presenceState) {
            DevicePresenceState.RETIRED -> {
                drawRect(
                    color = bodyColor.copy(alpha = 0.15f),
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(bodyW, candleH)
                )
                drawRect(
                    color = bodyColor,
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(bodyW, candleH),
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
            DevicePresenceState.LOST -> {
                drawRect(
                    color = bodyColor.copy(alpha = 0.12f),
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(bodyW, candleH)
                )
                drawRect(
                    color = bodyColor,
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(bodyW, candleH),
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f), 0f)
                    )
                )
            }
            DevicePresenceState.STOPPED_WORKING -> {
                drawRect(
                    color = bodyColor.copy(alpha = 0.18f),
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(bodyW, candleH)
                )
                drawRect(
                    color = bodyColor,
                    topLeft = Offset(xDeployStart, cTop),
                    size = Size(bodyW, candleH),
                    style = Stroke(width = 1.2.dp.toPx())
                )
                drawLine(
                    color = bodyColor,
                    start = Offset(xDeployStart + 2f, cTop + 1f),
                    end = Offset(xDeployStart + bodyW - 2f, cTop + candleH - 1f),
                    strokeWidth = 1f
                )
            }
            else -> {
                if (candle.isRetired) {
                    drawRect(
                        color = bodyColor.copy(alpha = 0.15f),
                        topLeft = Offset(xDeployStart, cTop),
                        size = Size(bodyW, candleH)
                    )
                    drawRect(
                        color = bodyColor,
                        topLeft = Offset(xDeployStart, cTop),
                        size = Size(bodyW, candleH),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                } else {
                    drawRect(
                        color = bodyColor,
                        topLeft = Offset(xDeployStart, cTop),
                        size = Size(bodyW, candleH)
                    )
                }
            }
        }

        if (candle.isPastEol && xDeployEnd > xEol) {
            val overW = (xDeployEnd - xEol).coerceAtLeast(3.dp.toPx())
            drawLine(
                color = palette.textPrimary,
                start = Offset(xEol, cTop - 1f),
                end = Offset(xEol, cTop + candleH + 1f),
                strokeWidth = 1.2.dp.toPx()
            )
            drawRect(
                color = cveColor,
                topLeft = Offset(xEol, cTop),
                size = Size(overW, candleH)
            )
        }

        if (!candle.isPastEol && xEol > xDeployEnd) {
            val runwayColor = if (candle.isEolNearing) eolColor else palette.textMuted
            drawLine(
                color = runwayColor,
                start = Offset(xDeployEnd, yCenter),
                end = Offset(xEol, yCenter),
                strokeWidth = 1.2.dp.toPx()
            )
            drawCircle(
                color = runwayColor,
                radius = 1.8.dp.toPx(),
                center = Offset(xEol, yCenter)
            )
        }

        if (showTodayMarker && !candle.isRetired) {
            drawLine(
                color = palette.textPrimary,
                start = Offset(xDeployEnd, 0f),
                end = Offset(xDeployEnd, h),
                strokeWidth = 1.5.dp.toPx()
            )
            drawCircle(
                color = palette.textPrimary,
                radius = 2.5.dp.toPx(),
                center = Offset(xDeployEnd, 1.dp.toPx())
            )
        }
    }
}

/**
 * Shared condensed candlestick lifecycle card used in [sh.tuesday.app.ui.home.DeviceInspectorSheet]
 * and [sh.tuesday.app.ui.tabs.TimelineScreen].
 */
@Composable
fun CondensedCandlestickCard(
    candle: OperationalCandle,
    palette: BrandPalette,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TuesdayDimens.RadiusControl))
            .background(palette.bg)
            .border(1.dp, palette.border, RoundedCornerShape(TuesdayDimens.RadiusControl))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Timeline",
                    color = palette.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(palette.textPrimary)
                    )
                    Text(
                        text = "Today",
                        color = palette.textPrimary.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        CondensedMiniCandleBar(
            candle = candle,
            palette = palette,
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
            showTodayMarker = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Released ${candle.releaseDateStr ?: "Launch"} • Active ${candle.deployDateStr ?: "Install"}",
                color = palette.textMuted,
                fontSize = 11.sp
            )
            Text(
                text = "EOL: ${candle.eolDateStr ?: "TBD"}",
                color = if (candle.isPastEol) TuesdayTheme.ledColorFor(PortLifecycleState.CVE_KEV, palette.isStn) else palette.textMuted,
                fontSize = 11.sp,
                fontWeight = if (candle.isPastEol) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
