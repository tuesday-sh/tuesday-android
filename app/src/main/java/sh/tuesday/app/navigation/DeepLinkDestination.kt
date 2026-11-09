package sh.tuesday.app.navigation

sealed interface DeepLinkDestination {
    data class Tab(val tab: String) : DeepLinkDestination
    data class Device(val deviceId: String) : DeepLinkDestination
    data class AddDevice(val mode: String? = null) : DeepLinkDestination
    data object HacsSettings : DeepLinkDestination
    data object NetworkPicker : DeepLinkDestination
    data object ReferenceGuide : DeepLinkDestination
    data class SeedFleet(val count: Int) : DeepLinkDestination
    data object ToggleFlavor : DeepLinkDestination
    data object ResetDemo : DeepLinkDestination
    data class VisionDebug(val enabled: Boolean, val openWizard: Boolean = true) : DeepLinkDestination
    data class SetApiEndpoint(val url: String) : DeepLinkDestination
    data class SyncNotifications(val forceResend: Boolean = true) : DeepLinkDestination
    data object TriggerMockPatchTuesday : DeepLinkDestination

    companion object {
        fun fromUri(uriString: String?): DeepLinkDestination? {
            if (uriString.isNullOrBlank()) return null
            val clean = uriString.trim()
            if (!clean.startsWith("tuesday://")) return null

            val path = clean.removePrefix("tuesday://").trimStart('/')
            val parts = path.split("/").filter { it.isNotBlank() }
            if (parts.isEmpty()) return null

            val head = parts[0].lowercase()
            return when (head) {
                "status", "fleet", "home", "grid" -> Tab("status")
                "bulletins", "inbox" -> Tab("bulletins")
                "timeline", "lifespan", "swimlane" -> Tab("timeline")
                "checkup", "audit" -> Tab("checkup")
                "account", "settings" -> {
                    val sub = parts.getOrNull(1)?.lowercase()
                    if (sub == "dev") Tab("account-dev") else Tab("account")
                }
                "add-device", "add_device", "intake", "wizard" -> {
                    val sub = parts.getOrNull(1)?.lowercase()
                    val modeParam = when {
                        clean.contains("mode=scan", ignoreCase = true) || sub == "scan" || sub == "barcode" -> "scan"
                        clean.contains("mode=manual", ignoreCase = true) || sub == "manual" -> "manual"
                        else -> "photo"
                    }
                    AddDevice(modeParam)
                }
                "hacs", "hacs-settings" -> HacsSettings
                "network-picker", "networks", "sites" -> NetworkPicker
                "reference-guide", "reference", "standards" -> ReferenceGuide
                "toggle-flavor", "flavor" -> ToggleFlavor
                "reset-demo", "reset" -> ResetDemo
                "seed", "seed-forest", "demo" -> {
                    val count = parts.getOrNull(1)?.toIntOrNull() ?: 12
                    SeedFleet(count)
                }
                "device" -> {
                    val deviceId = parts.getOrNull(1)
                    if (!deviceId.isNullOrBlank()) Device(deviceId) else null
                }
                "vision-debug", "debug-vision", "dev" -> {
                    val sub = parts.getOrNull(1)?.lowercase()
                    val isOff = clean.contains("enabled=false", ignoreCase = true) || sub == "off" || sub == "false"
                    VisionDebug(enabled = !isOff, openWizard = !clean.contains("wizard=false", ignoreCase = true))
                }
                "api-endpoint", "api-target" -> {
                    val target = parts.getOrNull(1)?.lowercase()
                    val resolvedUrl = when (target) {
                        "prod", "gcp" -> "https://api.tuesday.sh"
                        "lan" -> "http://192.168.9.201:3000"
                        else -> "http://100.108.40.3:3000"
                    }
                    SetApiEndpoint(resolvedUrl)
                }
                "sync-notifications", "notifications", "push-test" -> {
                    val sub = parts.getOrNull(1)?.lowercase()
                    val force = sub != "pending" && !clean.contains("force=false", ignoreCase = true)
                    SyncNotifications(forceResend = force)
                }
                "patch-tuesday", "mock-patch-tuesday" -> TriggerMockPatchTuesday
                else -> null
            }
        }
    }
}
