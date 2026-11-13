package sh.tuesday.app.domain

import sh.tuesday.app.models.DiscoveredDeviceMetadata
import sh.tuesday.app.models.ExtractedDeviceIdentifiers
import sh.tuesday.app.models.InventoryConfidenceState
import sh.tuesday.app.services.GoldClueItem
import sh.tuesday.app.services.GoldClueKind

data class ResolvedManualEnrollment(
    val hostname: String,
    val osSummary: String,
    val metadata: DiscoveredDeviceMetadata,
    val inventoryState: InventoryConfidenceState
)

object ManualEnrollmentResolver {
    /**
     * Resolves manual hardware enrollment metadata from selected Gold Catalog clues,
     * scanned/OCR identifiers, and optional user-typed hostname.
     * Returns `null` if neither a hostname nor a Gold Model clue is present.
     */
    fun resolve(
        manualHostname: String,
        selectedClues: List<GoldClueItem>,
        accumulatedIdentifiers: ExtractedDeviceIdentifiers
    ): ResolvedManualEnrollment? {
        val matchedModelClue = selectedClues.lastOrNull { it.kind == GoldClueKind.MODEL }
        val matchedBrandClue = selectedClues.lastOrNull { !it.vendor.isNullOrBlank() }
        val matchedTypeClue = selectedClues.lastOrNull { it.kind == GoldClueKind.DEVICE_TYPE }

        val resolvedName = manualHostname.trim().ifBlank {
            matchedModelClue?.chipLabel.orEmpty()
        }
        if (resolvedName.isBlank()) {
            return null
        }

        val vendor = matchedModelClue?.vendor
            ?: matchedBrandClue?.vendor
            ?: "Manual"
        val model = matchedModelClue?.model
            ?: accumulatedIdentifiers.modelNumber?.takeIf { it.isNotBlank() }
            ?: matchedTypeClue?.title
            ?: resolvedName
        val mac = selectedClues.lastOrNull { !it.macAddress.isNullOrBlank() }?.macAddress
            ?: accumulatedIdentifiers.macAddress?.takeIf { it.isNotBlank() }
        val serial = accumulatedIdentifiers.serialNumber?.takeIf { it.isNotBlank() }
        val barcode = selectedClues.lastOrNull { !it.upc.isNullOrBlank() }?.upc
            ?: accumulatedIdentifiers.barcode?.takeIf { it.isNotBlank() }

        val metadata = DiscoveredDeviceMetadata(
            manufacturer = vendor,
            modelNumber = model,
            macAddress = mac,
            serialNumber = serial,
            barcodeOrQr = barcode
        )

        val osSummary = buildList {
            if (matchedModelClue != null) add("Gold Verified") else add("Manual Entry")
            matchedTypeClue?.title?.let { add(it) }
            if (vendor != "Manual") add(vendor)
            if (!matchedModelClue?.model.isNullOrBlank()) add(matchedModelClue!!.model!!)
        }.joinToString(" • ")

        val inventoryState = if (matchedModelClue != null || !accumulatedIdentifiers.modelNumber.isNullOrBlank()) {
            InventoryConfidenceState.ENROLLED
        } else {
            InventoryConfidenceState.INCOMPLETE_OR_GUESSED
        }

        return ResolvedManualEnrollment(
            hostname = resolvedName,
            osSummary = osSummary,
            metadata = metadata,
            inventoryState = inventoryState
        )
    }
}
