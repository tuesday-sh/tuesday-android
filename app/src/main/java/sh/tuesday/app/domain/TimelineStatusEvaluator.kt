package sh.tuesday.app.domain

import sh.tuesday.app.models.DeviceAttestation
import sh.tuesday.app.models.DevicePresenceState
import sh.tuesday.app.models.OperationalCandle
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.models.RackPortDevice
import sh.tuesday.app.models.StnHardwareCategory
import kotlin.math.abs
import kotlin.math.roundToInt

data class CandlestickStatusSummary(
    val statusText: String,
    val needsStatusCheck: Boolean
)

object TimelineStatusEvaluator {
    const val DEFAULT_REFERENCE_DATE = "2026-11-11"
    const val DEFAULT_REFERENCE_YEAR = "2026"

    fun isAnnualCheckInDue(
        latestAuditDate: String?,
        referenceYearPrefix: String = DEFAULT_REFERENCE_YEAR
    ): Boolean = latestAuditDate?.startsWith(referenceYearPrefix) != true

    fun evaluateCandleStatus(
        candle: OperationalCandle,
        attestations: List<DeviceAttestation>,
        referenceYearPrefix: String = DEFAULT_REFERENCE_YEAR
    ): CandlestickStatusSummary {
        val statusText = when (candle.presenceState) {
            DevicePresenceState.STOPPED_WORKING -> "STOPPED WORKING • DEFECT"
            DevicePresenceState.LOST -> "LOST • UNACCOUNTED"
            DevicePresenceState.RETIRED -> "RETIRED • DECOMMISSIONED"
            DevicePresenceState.ACTIVE -> when {
                candle.isPastEol -> "ZOMBIE HARDWARE • PAST EOL"
                candle.isEolNearing -> "EOL NEARS • RUNWAY LOW"
                candle.hasCveOrRecall -> "SECURITY RECALL / CVE"
                else -> "ACTIVE • SUPPORTED RUNWAY"
            }
        }

        val latestAtt = candle.latestAttestation ?: attestations.firstOrNull { it.deviceId == candle.id }
        val checkInDue = isAnnualCheckInDue(latestAtt?.auditDate, referenceYearPrefix)
        val needsStatusCheck = checkInDue ||
            candle.isPastEol ||
            candle.isEolNearing ||
            candle.hasCveOrRecall ||
            candle.presenceState == DevicePresenceState.LOST ||
            candle.presenceState == DevicePresenceState.STOPPED_WORKING

        return CandlestickStatusSummary(
            statusText = statusText,
            needsStatusCheck = needsStatusCheck
        )
    }

    fun formatScrubberCursorDate(
        scrubberXNorm: Float,
        anchorNorm: Float = 0.72f,
        baseDateStr: String = DEFAULT_REFERENCE_DATE
    ): String {
        val dayOffset = ((anchorNorm - scrubberXNorm) * 120f).roundToInt()
        return when {
            abs(dayOffset) <= 1 -> "$baseDateStr (TODAY)"
            dayOffset > 0 -> "$baseDateStr (-%dd)".format(dayOffset)
            else -> "$baseDateStr (+%dd projection)".format(-dayOffset)
        }
    }

    fun worstCategoryState(
        category: StnHardwareCategory,
        devices: List<RackPortDevice>
    ): PortLifecycleState? =
        devices
            .asSequence()
            .filter { it.category == category }
            .minByOrNull { it.portState.severityPriority }
            ?.portState

    fun clampPopoverArrowFraction(
        anchorXInStripPx: Float,
        stripWidthPx: Float
    ): Float = if (stripWidthPx > 0f) {
        (anchorXInStripPx / stripWidthPx).coerceIn(0.08f, 0.92f)
    } else {
        0.5f
    }
}
