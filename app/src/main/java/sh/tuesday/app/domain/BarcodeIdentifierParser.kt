package sh.tuesday.app.domain

import com.google.mlkit.vision.barcode.common.Barcode

data class ParsedBarcodeResult(
    val normalizedValue: String,
    val is2DMatrix: Boolean
)

object BarcodeIdentifierParser {
    fun is2DFormat(format: Int): Boolean =
        format == Barcode.FORMAT_QR_CODE ||
            format == Barcode.FORMAT_DATA_MATRIX ||
            format == Barcode.FORMAT_AZTEC ||
            format == Barcode.FORMAT_PDF417

    /**
     * Normalizes scanned 1D/2D barcode payloads:
     * - Trims whitespace and returns null on blank strings
     * - Distinguishes 2D matrices (QR, DataMatrix, Aztec, PDF417) from 1D retail/serial barcodes
     * - Strips leading `0` from 13-digit numeric EAN-13 payloads into 12-digit UPC-A for Android/iOS parity
     */
    fun parse(rawValue: String, format: Int): ParsedBarcodeResult? {
        val cleaned = rawValue.trim()
        if (cleaned.isEmpty()) return null
        val is2D = is2DFormat(format)
        val normalized = if (!is2D && cleaned.length == 13 && cleaned.startsWith("0") && cleaned.all { it.isDigit() }) {
            cleaned.removePrefix("0")
        } else {
            cleaned
        }
        return ParsedBarcodeResult(
            normalizedValue = normalized,
            is2DMatrix = is2D
        )
    }
}
