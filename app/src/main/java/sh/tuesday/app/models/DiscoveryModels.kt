package sh.tuesday.app.models

import kotlinx.serialization.Serializable

@Serializable
enum class AiProviderType(val displayName: String, val badgeLabel: String) {
    ELASTIX("Elastix.ai FPGA", "LIVE ELASTIX FPGA"),
    GEMINI("GCP Gemini 3.8", "LIVE GCP GEMINI")
}

enum class ApiEndpointPreset(val label: String, val shortBadge: String, val url: String) {
    LOCAL_TAILSCALE("Local Dev (Tailscale)", "💻 LOCAL TS", "http://100.108.40.3:3000"),
    LOCAL_LAN("Local Dev (LAN)", "🏠 LOCAL LAN", "http://192.168.9.201:3000"),
    PROD_GCP("Prod (GCP Cloud Run)", "☁️ PROD GCP", "https://tuesday-server-1076233850924.us-west1.run.app");

    companion object {
        fun fromUrl(url: String): ApiEndpointPreset? {
            val clean = url.trim().trimEnd('/')
            return entries.find { it.url.trimEnd('/').equals(clean, ignoreCase = true) }
        }
    }
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
    val tuesdayApiBaseUrl: String = "http://100.108.40.3:3000",
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
data class GoldAlignmentInfo(
    val aligned: Boolean = false,
    val matchedFeedSlug: String? = null,
    val matchedFeedName: String? = null,
    val matchedPurlPrefix: String? = null,
    val inferredPurl: String = "",
    val latestGoldRelease: String? = null,
    val inBuiltInCatalog: Boolean = false,
    val inConfirmedCatalog: Boolean = false,
    val statusLabel: String = "UNALIGNED RAW MATCH",
    val diagnosticNote: String = ""
)

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
    val suggestedPackageManager: String,
    val goldAlignment: GoldAlignmentInfo? = null
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
    val providerUsed: String = "Elastix.ai FPGA (qwen3.8-27b)",
    val latencyMs: Long = 0,
    val costUsd: Double = 0.0,
    val debugTrace: List<String> = emptyList(),
    val rawLlmResponse: String? = null,
    val debugLogText: String? = null
)
