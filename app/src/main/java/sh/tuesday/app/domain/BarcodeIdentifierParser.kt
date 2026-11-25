package sh.tuesday.app.domain

import com.google.mlkit.vision.barcode.common.Barcode

data class ParsedBarcodeResult(
    val normalizedValue: String,
    val is2DMatrix: Boolean,
    val isMacAddress: Boolean = false
)

object BarcodeIdentifierParser {
    private val DELIMITED_MAC_REGEX = Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$")
    private val BARE_HEX_MAC_REGEX = Regex("^[0-9A-Fa-f]{12}$")

    fun is2DFormat(format: Int): Boolean =
        format == Barcode.FORMAT_QR_CODE ||
            format == Barcode.FORMAT_DATA_MATRIX ||
            format == Barcode.FORMAT_AZTEC ||
            format == Barcode.FORMAT_PDF417

    private fun normalizeMacIfApplicable(cleaned: String, format: Int): String? {
        if (DELIMITED_MAC_REGEX.matches(cleaned)) {
            return cleaned.uppercase().replace('-', ':')
        }
        // Hardware MAC stickers (Code 128 / Code 39) often omit colons (e.g., 00107F617AB0).
        // Guard against 12-digit numeric UPC-A retail barcodes by requiring at least one hex letter A-F.
        val isRetailUpc = format == Barcode.FORMAT_UPC_A || format == Barcode.FORMAT_EAN_13
        if (!isRetailUpc && BARE_HEX_MAC_REGEX.matches(cleaned) && cleaned.any { it in 'A'..'F' || it in 'a'..'f' }) {
            return cleaned.uppercase().chunked(2).joinToString(":")
        }
        return null
    }

    /**
     * Normalizes scanned 1D/2D barcode payloads:
     * - Trims whitespace and returns null on blank strings
     * - Detects colon/hyphen-delimited or bare 12-hex MAC stickers (e.g. `00107F617AB0` -> `00:10:7F:61:7A:B0`)
     * - Distinguishes 2D matrices (QR, DataMatrix, Aztec, PDF417) from 1D retail/serial barcodes
     * - Strips leading `0` from 13-digit numeric EAN-13 payloads into 12-digit UPC-A for Android/iOS parity
     */
    fun parse(rawValue: String, format: Int): ParsedBarcodeResult? {
        val cleaned = rawValue.trim()
        if (cleaned.isEmpty()) return null
        val is2D = is2DFormat(format)
        val normalizedMac = if (!is2D) normalizeMacIfApplicable(cleaned, format) else null
        if (normalizedMac != null) {
            return ParsedBarcodeResult(
                normalizedValue = normalizedMac,
                is2DMatrix = false,
                isMacAddress = true
            )
        }
        val normalized = if (!is2D && cleaned.length == 13 && cleaned.startsWith("0") && cleaned.all { it.isDigit() }) {
            cleaned.removePrefix("0")
        } else {
            cleaned
        }
        return ParsedBarcodeResult(
            normalizedValue = normalized,
            is2DMatrix = is2D,
            isMacAddress = false
        )
    }
}
