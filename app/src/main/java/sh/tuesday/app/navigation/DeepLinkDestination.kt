package sh.tuesday.app.navigation

import sh.tuesday.app.models.AppBrandFlavor

sealed interface DeepLinkDestination {
    data class Tab(val tab: String) : DeepLinkDestination
    data class Device(val deviceId: String) : DeepLinkDestination
    data class AddDevice(
        val mode: String? = null,
        val synthPreset: String? = null,
        val clueQuery: String? = null,
        val cluesCsv: String? = null,
        val autoSearch: Boolean = false
    ) : DeepLinkDestination
    data object HacsSettings : DeepLinkDestination
    data object NetworkPicker : DeepLinkDestination
    data object ReferenceGuide : DeepLinkDestination
    data class SeedFleet(val count: Int) : DeepLinkDestination
    data object ToggleFlavor : DeepLinkDestination
    data class SetFlavor(val flavor: AppBrandFlavor) : DeepLinkDestination
    data object ResetDemo : DeepLinkDestination
    data class VisionDebug(val enabled: Boolean, val openWizard: Boolean = true) : DeepLinkDestination
    data class SetApiEndpoint(val url: String) : DeepLinkDestination
    data class SyncNotifications(val forceResend: Boolean = true) : DeepLinkDestination
    data object TriggerMockPatchTuesday : DeepLinkDestination
    data class TimelineAction(val scenario: String? = null, val modal: String? = null) : DeepLinkDestination
    data class UpgradePrompt(val source: String = "modal", val sku: String? = null) : DeepLinkDestination
    data class Preferences(
        val showStateFilterStrip: Boolean? = null,
        val showZoneFilterStrip: Boolean? = null,
        val openSettings: Boolean = true
    ) : DeepLinkDestination

    companion object {
        private fun parseQueryMap(cleanUri: String): Map<String, String> {
            val queryString = cleanUri.substringAfter('?', "")
            if (queryString.isBlank()) return emptyMap()
            return queryString.split('&')
                .mapNotNull { kv ->
                    val idx = kv.indexOf('=')
                    if (idx > 0) {
                        val decoded = runCatching {
                            java.net.URLDecoder.decode(kv.substring(idx + 1), Charsets.UTF_8.name())
                        }.getOrDefault(kv.substring(idx + 1))
                        kv.substring(0, idx).lowercase() to decoded
                    } else null
                }
                .toMap()
        }

        private fun parseBoolParam(raw: String?): Boolean? = when (raw?.trim()?.lowercase()) {
            "true", "1", "on", "yes", "enabled" -> true
            "false", "0", "off", "no", "disabled" -> false
            else -> null
        }

        private fun parsePreferencesDestination(
            subParts: List<String>,
            queryMap: Map<String, String>
        ): Preferences {
            val sub0 = subParts.getOrNull(0)?.lowercase()
            val sub1 = subParts.getOrNull(1)?.lowercase()

            val pathAllToggle = when {
                sub0 in listOf("on", "enable", "show", "true") -> true
                sub0 in listOf("off", "disable", "hide", "false") -> false
                sub0 in listOf("filters", "filter", "strips") -> parseBoolParam(sub1)
                else -> null
            }
            val pathStateToggle = if (sub0 in listOf("state", "state-filter", "state-strip")) {
                parseBoolParam(sub1) ?: true
            } else null
            val pathZoneToggle = if (sub0 in listOf("zone", "zone-filter", "zone-strip")) {
                parseBoolParam(sub1) ?: true
            } else null

            val queryAll = parseBoolParam(queryMap["filters"] ?: queryMap["all"] ?: queryMap["strips"])
            val stateVal = parseBoolParam(queryMap["state"] ?: queryMap["state_filter"] ?: queryMap["showstatefilterstrip"])
                ?: pathStateToggle
                ?: queryAll
                ?: pathAllToggle
            val zoneVal = parseBoolParam(queryMap["zone"] ?: queryMap["zone_filter"] ?: queryMap["showzonefilterstrip"])
                ?: pathZoneToggle
                ?: queryAll
                ?: pathAllToggle

            val hasDirectMutation = stateVal != null || zoneVal != null
            val openParam = parseBoolParam(queryMap["open"] ?: queryMap["tab"] ?: queryMap["settings"])
            val openSettings = openParam ?: !hasDirectMutation

            return Preferences(
                showStateFilterStrip = stateVal,
                showZoneFilterStrip = zoneVal,
                openSettings = openSettings
            )
        }

        fun fromUri(uriString: String?): DeepLinkDestination? {
            if (uriString.isNullOrBlank()) return null
            val clean = uriString.trim()
            if (!clean.startsWith("tuesday://")) return null

            val path = clean.removePrefix("tuesday://").substringBefore('?').trimStart('/')
            val parts = path.split("/").filter { it.isNotBlank() }
            if (parts.isEmpty()) return null
            val queryMap = parseQueryMap(clean)

            val head = parts[0].lowercase()
            return when (head) {
                "status", "fleet", "home", "grid" -> Tab("status")
                "bulletins", "inbox", "alerts" -> Tab("bulletins")
                "timeline", "lifespan", "swimlane" -> {
                    val sub = parts.getOrNull(1)?.lowercase()
                    when (sub) {
                        "decommissioned", "retired", "zombie", "runway", "standard" -> TimelineAction(scenario = sub, modal = parts.getOrNull(2)?.lowercase())
                        "checkin", "check-in", "status", "update-status" -> TimelineAction(scenario = null, modal = sub)
                        else -> Tab("timeline")
                    }
                }
                "checkup", "audit" -> Tab("checkup")
                "account", "settings" -> {
                    val sub = parts.getOrNull(1)?.lowercase()
                    when (sub) {
                        "dev" -> Tab("account-dev")
                        "plan", "billing", "upgrade", "subscription" -> UpgradePrompt(source = "account")
                        "preferences", "prefs" -> parsePreferencesDestination(parts.drop(2), queryMap)
                        else -> Tab("account")
                    }
                }
                "preferences", "prefs" -> parsePreferencesDestination(parts.drop(1), queryMap)
                "upgrade", "paywall", "billing", "pro" -> {
                    val sub = parts.getOrNull(1)?.lowercase()
                    val sku = when {
                        clean.contains("monthly", ignoreCase = true) || sub == "monthly" -> "sh.tuesday.pro.tier1.monthly"
                        clean.contains("annual", ignoreCase = true) || clean.contains("yearly", ignoreCase = true) || sub == "annual" -> "sh.tuesday.pro.tier1.annual"
                        else -> null
                    }
                    val source = if (sub == "account" || sub == "settings") "account" else "modal"
                    UpgradePrompt(source = source, sku = sku)
                }
                "add-device", "add_device", "intake", "wizard" -> {
                    val sub = parts.getOrNull(1)?.lowercase()
                    val modeParam = when {
                        clean.contains("mode=scan", ignoreCase = true) || sub == "scan" || sub == "barcode" -> "scan"
                        clean.contains("mode=manual", ignoreCase = true) || sub == "manual" -> "manual"
                        else -> "photo"
                    }
                    val synthPreset = when {
                        clean.contains("synth=router", ignoreCase = true) || sub == "router" -> "router"
                        clean.contains("synth=iot", ignoreCase = true) || sub == "iot" || sub == "zigbee" -> "iot-zigbee"
                        clean.contains("synth=barcode", ignoreCase = true) || clean.contains("barcode=udm-pro", ignoreCase = true) -> "barcode-udm-pro"
                        clean.contains("synth=qr", ignoreCase = true) || clean.contains("qr=ucg-ultra", ignoreCase = true) -> "qr-ucg-ultra"
                        else -> null
                    }
                    val queryString = clean.substringAfter('?', "")
                    val queryMap = queryString.split('&')
                        .mapNotNull { kv ->
                            val idx = kv.indexOf('=')
                            if (idx > 0) {
                                val decoded = runCatching {
                                    java.net.URLDecoder.decode(kv.substring(idx + 1), Charsets.UTF_8.name())
                                }.getOrDefault(kv.substring(idx + 1))
                                kv.substring(0, idx).lowercase() to decoded
                            } else null
                        }
                        .toMap()

                    AddDevice(
                        mode = modeParam,
                        synthPreset = synthPreset,
                        clueQuery = queryMap["query"] ?: queryMap["q"],
                        cluesCsv = queryMap["clues"] ?: queryMap["clue"],
                        autoSearch = queryMap["search"]?.equals("true", ignoreCase = true) == true ||
                            queryMap["autosearch"]?.equals("true", ignoreCase = true) == true
                    )
                }
                "synth", "synthesize" -> {
                    val target = parts.getOrNull(1)?.lowercase() ?: "router"
                    when (target) {
                        "barcode", "barcode-udm-pro", "udm-pro" -> AddDevice(mode = "scan", synthPreset = "barcode-udm-pro")
                        "qr", "qr-ucg-ultra", "ucg-ultra" -> AddDevice(mode = "scan", synthPreset = "qr-ucg-ultra")
                        "iot", "iot-zigbee", "zigbee" -> AddDevice(mode = "photo", synthPreset = "iot-zigbee")
                        else -> AddDevice(mode = "photo", synthPreset = "router")
                    }
                }
                "hacs", "hacs-settings" -> HacsSettings
                "network-picker", "networks", "sites", "join-site" -> NetworkPicker
                "reference-guide", "reference", "standards" -> ReferenceGuide
                "toggle-flavor" -> ToggleFlavor
                "flavor" -> {
                    when (parts.getOrNull(1)?.lowercase()) {
                        "pro", "tuesday", "stn-pro" -> SetFlavor(AppBrandFlavor.TUESDAY_PRO)
                        "home", "stn", "stn-home", "surething" -> SetFlavor(AppBrandFlavor.SURETHING_HOME)
                        else -> ToggleFlavor
                    }
                }
                "reset-demo", "reset" -> ResetDemo
                "seed", "seed-forest", "demo" -> {
                    val count = parts.getOrNull(1)?.toIntOrNull()
                        ?: parts.getOrNull(2)?.toIntOrNull()
                        ?: 12
                    SeedFleet(count)
                }
                "device", "inspect" -> {
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
                        "local", "loopback", "reverse" -> "http://127.0.0.1:3000"
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
