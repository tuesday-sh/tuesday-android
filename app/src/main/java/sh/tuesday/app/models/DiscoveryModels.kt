package sh.tuesday.app.models

import kotlinx.serialization.Serializable

@Serializable
enum class AiProviderType(val displayName: String, val badgeLabel: String) {
    ELASTIX("Elastix.ai FPGA", "LIVE ELASTIX FPGA"),
    GEMINI("GCP Gemini 3.8", "LIVE GCP GEMINI")
}

@Serializable
data class HacsConfig(
    val instanceName: String = "Home Assistant (HACS)",
    val baseUrl: String = "http://homeassistant.local:8123",
    val apiToken: String = "tuesday_hacs_live_98f2a4c1d0",
    val hacsComponentVersion: String = "0.4.2",
    val tuesdayServerVersion: String = "0.2.0-hacs",
    val requireLocalWifi: Boolean = false,
    val trustedSsid: String = "SureThing-Home-5G",
    val isConnected: Boolean = true,
    val lastSyncedEpochMs: Long = System.currentTimeMillis(),
    val aiProvider: AiProviderType = AiProviderType.ELASTIX,
    val tuesdayApiBaseUrl: String = "https://api.tuesday.sh",
    val elastixApiKey: String = "",
    val elastixModel: String = "qwen3.8-27b",
    val geminiApiKey: String = "",
    val geminiModel: String = "gemini-3.8-flash"
)

enum class DeviceClueType(val rawValue: String, val placeholder: String) {
    MODEL_NUMBER("Model", "e.g. UCG-Ultra / RT-AX86U"),
    MAC_ADDRESS("MAC Address", "e.g. 74:83:C2:9E:11:04"),
    SERIAL_NUMBER("Serial Number", "e.g. SN-2409A88192"),
    BARCODE("Barcode", "e.g. UPC 810010078142"),
    QR_CODE("QR Code", "e.g. WIFI:S:UniFi;T:WPA;...")
}

@Serializable
data class ExtractedDeviceIdentifiers(
    var barcode: String? = null,
    var serialNumber: String? = null,
    var qrCode: String? = null,
    var modelNumber: String? = null,
    var macAddress: String? = null
) {
    fun merge(other: ExtractedDeviceIdentifiers) {
        other.barcode?.takeIf { it.isNotBlank() }?.let { barcode = it }
        other.serialNumber?.takeIf { it.isNotBlank() }?.let { serialNumber = it }
        other.qrCode?.takeIf { it.isNotBlank() }?.let { qrCode = it }
        other.modelNumber?.takeIf { it.isNotBlank() }?.let { modelNumber = it }
        other.macAddress?.takeIf { it.isNotBlank() }?.let { macAddress = it }
    }

    fun nonEmptyPairs(): List<Pair<DeviceClueType, String>> {
        val list = mutableListOf<Pair<DeviceClueType, String>>()
        modelNumber?.takeIf { it.isNotBlank() }?.let { list.add(DeviceClueType.MODEL_NUMBER to it) }
        macAddress?.takeIf { it.isNotBlank() }?.let { list.add(DeviceClueType.MAC_ADDRESS to it) }
        serialNumber?.takeIf { it.isNotBlank() }?.let { list.add(DeviceClueType.SERIAL_NUMBER to it) }
        barcode?.takeIf { it.isNotBlank() }?.let { list.add(DeviceClueType.BARCODE to it) }
        qrCode?.takeIf { it.isNotBlank() }?.let { list.add(DeviceClueType.QR_CODE to it) }
        return list
    }
}

@Serializable
data class DeviceCandidateMatch(
    val manufacturer: String,
    val modelNumber: String,
    val displayName: String,
    val deviceCategory: String,
    val confidencePercent: Int,
    val reasoning: String,
    val suggestedHostname: String,
    val suggestedOsDetails: String,
    val suggestedPackageManager: String
) {
    val resolvedPackageManager: PackageManagerType
        get() = PackageManagerType.fromRaw(suggestedPackageManager)
}

@Serializable
data class GeminiDiscoveryTurnResult(
    val visualSummary: String,
    val nextNarrowingQuestion: String,
    val recommendedClueTypes: List<String>,
    val extractedIdentifiers: ExtractedDeviceIdentifiers,
    val candidates: List<DeviceCandidateMatch>,
    val usedLiveGeminiApi: Boolean = true,
    val providerUsed: String = "Elastix.ai FPGA (qwen3.8-27b)"
)
