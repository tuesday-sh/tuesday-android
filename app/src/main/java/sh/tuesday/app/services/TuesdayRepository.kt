package sh.tuesday.app.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import sh.tuesday.app.models.*
import java.util.UUID
import java.util.concurrent.TimeUnit

class TuesdayRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tuesday_prefs_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val clientDeviceId: String by lazy {
        val existing = prefs.getString("client_device_id", null)
        if (!existing.isNullOrBlank()) {
            existing
        } else {
            val generated = "android-" + UUID.randomUUID().toString().take(8)
            prefs.edit().putString("client_device_id", generated).apply()
            generated
        }
    }

    private val _devices = MutableStateFlow<List<MonitoredDevice>>(emptyList())
    val devices: StateFlow<List<MonitoredDevice>> = _devices.asStateFlow()

    private val _rackDevices = MutableStateFlow<List<RackPortDevice>>(BulletinsAndInventoryEngine.defaultRackPortDevices())
    val rackDevices: StateFlow<List<RackPortDevice>> = _rackDevices.asStateFlow()

    private val _brandFlavor = MutableStateFlow(AppBrandFlavor.TUESDAY_PRO)
    val brandFlavor: StateFlow<AppBrandFlavor> = _brandFlavor.asStateFlow()

    private val _mutedFeedSlugs = MutableStateFlow(setOf("lg-esphome-iot"))
    val mutedFeedSlugs: StateFlow<Set<String>> = _mutedFeedSlugs.asStateFlow()

    private val _includeOkDevices = MutableStateFlow(false)
    val includeOkDevices: StateFlow<Boolean> = _includeOkDevices.asStateFlow()

    private val _selectedZoneFilter = MutableStateFlow<TrustZone?>(null)
    val selectedZoneFilter: StateFlow<TrustZone?> = _selectedZoneFilter.asStateFlow()

    private val _selectedPortStateFilter = MutableStateFlow<PortLifecycleState?>(null)
    val selectedPortStateFilter: StateFlow<PortLifecycleState?> = _selectedPortStateFilter.asStateFlow()

    private val _userNetworks = MutableStateFlow(defaultUserNetworks())
    val userNetworks: StateFlow<List<NetworkSite>> = _userNetworks.asStateFlow()

    private val _activeNetworkId = MutableStateFlow("net-default")
    val activeNetworkId: StateFlow<String> = _activeNetworkId.asStateFlow()

    private val _activeDeviceCount = MutableStateFlow(0)
    val activeDeviceCount: StateFlow<Int> = _activeDeviceCount.asStateFlow()

    private val _isDeveloperMode = MutableStateFlow(false)
    val isDeveloperMode: StateFlow<Boolean> = _isDeveloperMode.asStateFlow()

    private val _isVisionDebugEnabled = MutableStateFlow(true)
    val isVisionDebugEnabled: StateFlow<Boolean> = _isVisionDebugEnabled.asStateFlow()

    private val _hacsConfig = MutableStateFlow(HacsConfig())
    val hacsConfig: StateFlow<HacsConfig> = _hacsConfig.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _entitlementTier = MutableStateFlow(prefs.getString("entitlement_tier", "free") ?: "free")
    val entitlementTier: StateFlow<String> = _entitlementTier.asStateFlow()

    private val _remindersEnabled = MutableStateFlow(prefs.getBoolean("monthly_reminders_enabled", false))
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()

    private val _dispatchSubscribed = MutableStateFlow(prefs.getBoolean("dispatch_subscribed", false))
    val dispatchSubscribed: StateFlow<Boolean> = _dispatchSubscribed.asStateFlow()

    private val _bulletinDismissed = MutableStateFlow(prefs.getBoolean("bulletin_dismissed", false))
    val bulletinDismissed: StateFlow<Boolean> = _bulletinDismissed.asStateFlow()

    private val _bulletinSnoozed = MutableStateFlow(prefs.getBoolean("bulletin_snoozed", false))
    val bulletinSnoozed: StateFlow<Boolean> = _bulletinSnoozed.asStateFlow()

    private val _bulletinMuted = MutableStateFlow(prefs.getBoolean("bulletin_muted", false))
    val bulletinMuted: StateFlow<Boolean> = _bulletinMuted.asStateFlow()

    init {
        loadConfig()
        loadDevices()
    }

    fun toggleBrandFlavor() {
        _brandFlavor.value = if (_brandFlavor.value == AppBrandFlavor.TUESDAY_PRO) {
            AppBrandFlavor.SURETHING_HOME
        } else {
            AppBrandFlavor.TUESDAY_PRO
        }
    }

    fun toggleIncludeOkDevices() {
        _includeOkDevices.value = !_includeOkDevices.value
    }

    fun setZoneFilter(zone: TrustZone?) {
        _selectedZoneFilter.value = zone
    }

    fun setPortStateFilter(state: PortLifecycleState?) {
        _selectedPortStateFilter.value = state
    }

    fun toggleDeveloperMode(enabled: Boolean) {
        _isDeveloperMode.value = enabled
        prefs.edit().putBoolean("is_developer_mode", enabled).apply()
    }

    fun toggleVisionDebugMode(enabled: Boolean) {
        _isVisionDebugEnabled.value = enabled
        prefs.edit().putBoolean("is_vision_debug_enabled", enabled).apply()
    }

    fun setSimulatedDeviceCount(count: Int) {
        val clamped = count.coerceIn(0, 128)
        _activeDeviceCount.value = clamped
        _selectedZoneFilter.value = null
        _selectedPortStateFilter.value = null
        _userNetworks.value = _userNetworks.value.map {
            if (it.id == _activeNetworkId.value) it.copy(devicesCount = clamped) else it
        }
    }

    fun selectNetwork(netId: String) {
        val target = _userNetworks.value.find { it.id == netId } ?: return
        _activeNetworkId.value = target.id
        _activeDeviceCount.value = target.devicesCount
        _selectedZoneFilter.value = null
        _selectedPortStateFilter.value = null
    }

    fun renameActiveNetwork(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        _userNetworks.value = _userNetworks.value.map {
            if (it.id == _activeNetworkId.value) it.copy(name = trimmed) else it
        }
    }

    fun createNetwork(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val newNet = NetworkSite(
            id = "net-${System.currentTimeMillis()}",
            name = trimmed,
            devicesCount = 1,
            role = "Site"
        )
        _userNetworks.value = _userNetworks.value + newNet
        selectNetwork(newNet.id)
    }

    fun toggleMuteFeed(slug: String) {
        val current = _mutedFeedSlugs.value.toMutableSet()
        val nowMuted = if (current.contains(slug)) {
            current.remove(slug)
            false
        } else {
            current.add(slug)
            true
        }
        _mutedFeedSlugs.value = current
        emitTelemetryEventAsync(
            eventName = "bulletin.feed_mute_toggled",
            targetEntityType = "CanonicalFeed",
            targetEntityId = slug,
            properties = mapOf("muted" to nowMuted)
        )
    }

    fun setRemindersEnabled(enabled: Boolean) {
        _remindersEnabled.value = enabled
        prefs.edit().putBoolean("monthly_reminders_enabled", enabled).apply()
        emitTelemetryEventAsync(
            eventName = "checkup.reminders_toggled",
            properties = mapOf("enabled" to enabled)
        )
    }

    fun setDispatchSubscribed(subscribed: Boolean) {
        _dispatchSubscribed.value = subscribed
        prefs.edit().putBoolean("dispatch_subscribed", subscribed).apply()
    }

    fun setBulletinDismissed(dismissed: Boolean) {
        _bulletinDismissed.value = dismissed
        prefs.edit().putBoolean("bulletin_dismissed", dismissed).apply()
        if (dismissed) {
            emitTelemetryEventAsync(
                eventName = "bulletin.marked_complete",
                targetEntityType = "CuratedBulletin",
                targetEntityId = "active-bulletin"
            )
        }
    }

    fun setBulletinSnoozed(snoozed: Boolean) {
        _bulletinSnoozed.value = snoozed
        prefs.edit().putBoolean("bulletin_snoozed", snoozed).apply()
        if (snoozed) {
            emitTelemetryEventAsync(
                eventName = "bulletin.snoozed",
                targetEntityType = "CuratedBulletin",
                targetEntityId = "active-bulletin",
                properties = mapOf("durationHours" to 24)
            )
        }
    }

    fun setBulletinMuted(muted: Boolean) {
        _bulletinMuted.value = muted
        prefs.edit().putBoolean("bulletin_muted", muted).apply()
        if (muted) {
            emitTelemetryEventAsync(
                eventName = "bulletin.ignored",
                targetEntityType = "CuratedBulletin",
                targetEntityId = "active-bulletin"
            )
        }
    }

    fun confirmProvisionalDeviceModel(chosenModel: String) {
        var targetDeviceId: String? = null
        _rackDevices.value = _rackDevices.value.map { dev ->
            if (dev.confidenceTier == DeviceConfidenceTier.PROVISIONAL_GUESS) {
                targetDeviceId = dev.id
                dev.copy(
                    confidenceTier = DeviceConfidenceTier.CONFIRMED,
                    portState = PortLifecycleState.CVE_KEV,
                    name = "Hallway Ecobee $chosenModel",
                    version = "4.8.7.210",
                    targetVersion = "4.8.7.220",
                    lifecycleTier = "Active",
                    purls = listOf("pkg:generic/ecobee/smart-thermostat@4.8.7.210"),
                    proHeadline = "Confirmed ($chosenModel): CVE-2026-88901 HomeKit Pairing Fix (4.8.7.220)",
                    stnHeadline = "Confirmed ($chosenModel): Update thermostat firmware to 4.8.7.220"
                )
            } else dev
        }
        targetDeviceId?.let { devId ->
            syncDevicePatchToServerAsync(
                deviceId = devId,
                payload = JSONObject().apply {
                    put("confirmed_candidate_model", chosenModel)
                }
            )
        }
    }

    fun rejectProvisionalDeviceModel() {
        var targetDeviceId: String? = null
        _rackDevices.value = _rackDevices.value.map { dev ->
            if (dev.confidenceTier == DeviceConfidenceTier.PROVISIONAL_GUESS) {
                targetDeviceId = dev.id
                dev.copy(
                    confidenceTier = DeviceConfidenceTier.OPAQUE_UNKNOWN,
                    excludedPurlPrefixes = dev.excludedPurlPrefixes + "pkg:generic/ecobee/*",
                    proHeadline = "Ecobee Family Excluded • Use Plug & Tag Camera to Identify Exact Model",
                    stnHeadline = "Not an Ecobee • Snap a photo of the thermostat label to identify"
                )
            } else dev
        }
        targetDeviceId?.let { devId ->
            syncDevicePatchToServerAsync(
                deviceId = devId,
                payload = JSONObject().apply {
                    put("confidence_tier", "opaque_unknown")
                    put("add_excluded_purl_prefix", "pkg:generic/ecobee/*")
                }
            )
        }
    }

    private fun loadConfig() {
        _isDeveloperMode.value = prefs.getBoolean("is_developer_mode", false)
        _isVisionDebugEnabled.value = prefs.getBoolean("is_vision_debug_enabled", true)
        val raw = prefs.getString("hacs_config", null)
        if (raw != null) {
            try {
                _hacsConfig.value = json.decodeFromString(raw)
            } catch (_: Exception) {
                _hacsConfig.value = HacsConfig()
            }
        }
    }

    private fun loadDevices() {
        val raw = prefs.getString("devices_list", null)
        if (raw != null) {
            try {
                val list = json.decodeFromString<List<MonitoredDevice>>(raw)
                _devices.value = list
                return
            } catch (_: Exception) {}
        }
        _devices.value = emptyList()
        persistDevices()
    }

    private fun persistDevices() {
        val raw = json.encodeToString(_devices.value)
        prefs.edit().putString("devices_list", raw).apply()
    }

    private fun persistConfig() {
        val raw = json.encodeToString(_hacsConfig.value)
        prefs.edit().putString("hacs_config", raw).apply()
    }

    fun updateHacsConfig(config: HacsConfig) {
        _hacsConfig.value = config
        persistConfig()
    }

    fun setTuesdayApiBaseUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        if (clean.isEmpty()) return
        _hacsConfig.value = _hacsConfig.value.copy(tuesdayApiBaseUrl = clean)
        persistConfig()
    }

    suspend fun refreshTelemetry() {
        _isSyncing.value = true
        kotlinx.coroutines.delay(250)
        val now = System.currentTimeMillis()
        _devices.value = _devices.value.map { it.copy(lastCheckInEpochMs = now) }
        _hacsConfig.value = _hacsConfig.value.copy(lastSyncedEpochMs = now)
        _isSyncing.value = false
        persistDevices()
        persistConfig()
    }

    suspend fun enrollDevice(
        hostname: String,
        osDetails: String,
        packageManager: PackageManagerType,
        agentToken: String,
        inventoryState: InventoryConfidenceState? = InventoryConfidenceState.ENROLLED,
        discoveredMetadata: DiscoveredDeviceMetadata? = null
    ) {
        _isSyncing.value = true
        kotlinx.coroutines.delay(200)
        val now = System.currentTimeMillis()
        val sampleUpdates = if (inventoryState == InventoryConfidenceState.INCOMPLETE_OR_GUESSED) {
            listOf(
                PackageUpdate(
                    name = "firmware/inventory-audit",
                    currentVersion = "unverified",
                    availableVersion = "verify-model",
                    isSecurityCritical = false,
                    advisoryNote = "Incomplete/guessed inventory record — confirm model/MAC when convenient"
                )
            )
        } else {
            listOf(
                PackageUpdate(
                    name = if (packageManager == PackageManagerType.BREW) "git" else (if (packageManager == PackageManagerType.FIRMWARE) "vendor-firmware" else "ca-certificates"),
                    currentVersion = "2026.01.1",
                    availableVersion = "2026.10.1"
                )
            )
        }

        val initialScore = if (inventoryState == InventoryConfidenceState.INCOMPLETE_OR_GUESSED) 64.0 else 84.0
        val newDevice = MonitoredDevice(
            hostname = hostname,
            osDetails = osDetails,
            packageManager = packageManager,
            lastCheckInEpochMs = now,
            pendingUpdates = sampleUpdates,
            healthScore = initialScore,
            inventoryState = inventoryState,
            discoveredMetadata = discoveredMetadata
        )

        val updated = mutableListOf(newDevice)
        updated.addAll(_devices.value)
        _devices.value = updated

        // Also add to rackDevices so it reflects in the 128-LED Matrix & Action-First list
        val nextPort = (_rackDevices.value.maxOfOrNull { it.portIndex } ?: 12) + 1
        val isGuessed = inventoryState == InventoryConfidenceState.INCOMPLETE_OR_GUESSED
        val newRackDev = RackPortDevice(
            id = newDevice.id,
            portIndex = nextPort,
            portState = if (isGuessed) PortLifecycleState.GUESSED else PortLifecycleState.OK,
            trustZone = TrustZone.Z3_IOT,
            assetTag = "STN-NEW-$nextPort",
            shortLabel = hostname.take(10),
            name = hostname,
            roomLocation = "Newly Enrolled",
            version = if (isGuessed) "unverified" else "2026.10.1",
            targetVersion = "2026.10.1",
            lifecycleTier = if (isGuessed) "Provisional Guess" else "Active",
            purls = listOf("pkg:generic/enrolled/${hostname.lowercase()}"),
            confidenceTier = if (isGuessed) DeviceConfidenceTier.PROVISIONAL_GUESS else DeviceConfidenceTier.CONFIRMED,
            candidateModels = if (isGuessed) listOf(discoveredMetadata?.modelNumber ?: "Generic IoT Node") else emptyList(),
            proHeadline = osDetails,
            stnHeadline = osDetails,
            s0Identity = "STN-NEW-$nextPort • Newly Enrolled • Z3 IoT",
            s1Hardware = discoveredMetadata?.manufacturer ?: hostname,
            s2Software = osDetails,
            s3Lifecycle = if (isGuessed) "Provisional Guess" else "Active Support",
            s4Advisories = "0 Active CVEs",
            s5Topology = "Z3 IoT",
            s6Remediation = if (isGuessed) "Confirm exact model on Bulletins tab." else "No action required."
        )
        _rackDevices.value = listOf(newRackDev) + _rackDevices.value
        _activeDeviceCount.value = (_activeDeviceCount.value + 1).coerceAtMost(128)

        syncDeviceCreateToServerAsync(newRackDev, discoveredMetadata)

        _isSyncing.value = false
        persistDevices()
    }

    fun triggerDeviceCheckIn(deviceId: String) {
        val now = System.currentTimeMillis()
        _devices.value = _devices.value.map {
            if (it.id == deviceId) it.copy(lastCheckInEpochMs = now) else it
        }
        persistDevices()
    }

    fun markDeviceUpdatesApplied(deviceId: String) {
        val now = System.currentTimeMillis()
        _devices.value = _devices.value.map {
            if (it.id == deviceId) {
                it.copy(
                    lastCheckInEpochMs = now,
                    pendingUpdates = emptyList(),
                    healthScore = 100.0,
                    status = NodeHealthStatus.HEALTHY
                )
            } else it
        }
        _rackDevices.value = _rackDevices.value.map {
            if (it.id == deviceId) {
                it.copy(
                    portState = PortLifecycleState.OK,
                    version = it.targetVersion ?: it.version,
                    proHeadline = "Patched & Verified OK • 0 Active Advisories",
                    stnHeadline = "Everything OK • Latest software installed"
                )
            } else it
        }
        syncDevicePatchToServerAsync(
            deviceId = deviceId,
            payload = JSONObject().apply {
                put("mark_remediated", true)
            }
        )
        persistDevices()
    }

    fun upgradeToProTier1(productId: String = "sh.tuesday.pro.tier1.annual") {
        _entitlementTier.value = "pro_tier_1"
        prefs.edit().putString("entitlement_tier", "pro_tier_1").apply()
        ioScope.launch {
            try {
                val baseUrl = _hacsConfig.value.tuesdayApiBaseUrl.ifBlank { GeminiDiscoveryService.DEFAULT_SERVER_BASE_URL }.trimEnd('/')
                val payload = JSONObject().apply {
                    put("platform", "android")
                    put("product_id", productId)
                    put("purchase_token", "gp_token_${UUID.randomUUID()}")
                }
                val req = Request.Builder()
                    .url("$baseUrl/api/v1/billing/verify")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Tuesday-Device-ID", clientDeviceId)
                    .addHeader("X-Tuesday-Client-Platform", "android")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (e: Exception) {
                Log.w("TuesdayRepoSync", "Billing verification queued offline: ${e.message}")
            }
        }
    }

    private fun syncDeviceCreateToServerAsync(dev: RackPortDevice, meta: DiscoveredDeviceMetadata?) {
        ioScope.launch {
            try {
                val baseUrl = _hacsConfig.value.tuesdayApiBaseUrl.ifBlank { GeminiDiscoveryService.DEFAULT_SERVER_BASE_URL }.trimEnd('/')
                val payload = JSONObject().apply {
                    put("id", dev.id)
                    put("port_index", dev.portIndex)
                    put("category_code", dev.category.code)
                    put("trust_zone", dev.trustZone.key)
                    put("port_state", dev.portState.key)
                    put("confidence_tier", dev.confidenceTier.name.lowercase())
                    put("asset_tag", dev.assetTag)
                    put("short_label", dev.shortLabel)
                    put("name", dev.name)
                    put("vendor", meta?.manufacturer ?: "Generic")
                    put("model_number", meta?.modelNumber ?: dev.shortLabel)
                    put("room_location", dev.roomLocation)
                    put("version", dev.version)
                    put("target_version", dev.targetVersion ?: dev.version)
                    put("purls", JSONArray(dev.purls))
                }
                val req = Request.Builder()
                    .url("$baseUrl/api/v1/inventory")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Tuesday-Device-ID", clientDeviceId)
                    .addHeader("X-Tuesday-Client-Platform", "android")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (e: Exception) {
                Log.w("TuesdayRepoSync", "Inventory create queued offline: ${e.message}")
            }
        }
    }

    fun emitTelemetryEventAsync(
        eventName: String,
        targetEntityType: String? = null,
        targetEntityId: String? = null,
        properties: Map<String, Any?> = emptyMap()
    ) {
        ioScope.launch {
            try {
                val baseUrl = _hacsConfig.value.tuesdayApiBaseUrl.ifBlank { GeminiDiscoveryService.DEFAULT_SERVER_BASE_URL }.trimEnd('/')
                val propsObj = JSONObject()
                for ((k, v) in properties) {
                    if (v != null) propsObj.put(k, v)
                }
                val payload = JSONObject().apply {
                    put("event_name", eventName)
                    put("client_platform", "android")
                    put("app_flavor", if (_brandFlavor.value == AppBrandFlavor.SURETHING_HOME) "surething" else "tuesday")
                    put("device_id", clientDeviceId)
                    if (!targetEntityType.isNullOrBlank()) put("target_entity_type", targetEntityType)
                    if (!targetEntityId.isNullOrBlank()) put("target_entity_id", targetEntityId)
                    put("properties", propsObj)
                }
                val req = Request.Builder()
                    .url("$baseUrl/api/v1/telemetry/events")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Tuesday-Device-ID", clientDeviceId)
                    .addHeader("X-Tuesday-Client-Platform", "android")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (e: Exception) {
                Log.w("TuesdayRepoSync", "Telemetry event queued offline: ${e.message}")
            }
        }
    }

    private fun syncDevicePatchToServerAsync(deviceId: String, payload: JSONObject) {
        ioScope.launch {
            try {
                val baseUrl = _hacsConfig.value.tuesdayApiBaseUrl.ifBlank { GeminiDiscoveryService.DEFAULT_SERVER_BASE_URL }.trimEnd('/')
                val req = Request.Builder()
                    .url("$baseUrl/api/v1/inventory/$deviceId")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Tuesday-Device-ID", clientDeviceId)
                    .addHeader("X-Tuesday-Client-Platform", "android")
                    .patch(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (e: Exception) {
                Log.w("TuesdayRepoSync", "Inventory patch queued offline: ${e.message}")
            }
        }
    }

    fun resetDemoData() {
        _devices.value = defaultSeedDevices()
        _rackDevices.value = BulletinsAndInventoryEngine.defaultRackPortDevices()
        _mutedFeedSlugs.value = setOf("lg-esphome-iot")
        _includeOkDevices.value = false
        _selectedZoneFilter.value = null
        _selectedPortStateFilter.value = null
        _activeDeviceCount.value = 0
        _bulletinDismissed.value = false
        _bulletinSnoozed.value = false
        _bulletinMuted.value = false
        _hacsConfig.value = HacsConfig()
        prefs.edit()
            .putBoolean("bulletin_dismissed", false)
            .putBoolean("bulletin_snoozed", false)
            .putBoolean("bulletin_muted", false)
            .apply()
        persistDevices()
        persistConfig()
    }

    companion object {
        fun defaultUserNetworks(): List<NetworkSite> = listOf(
            NetworkSite(id = "net-default", name = "", devicesCount = 0, role = "Default Site")
        )

        fun defaultSeedDevices(now: Long = System.currentTimeMillis()): List<MonitoredDevice> {
            return listOf(
                MonitoredDevice(
                    id = "device-ubuntu-dev",
                    hostname = "ubuntu-dev",
                    osDetails = "Ubuntu 24.04.1 LTS (Linux 6.8.0-45 x86_64)",
                    agentVersion = "0.2.1",
                    packageManager = PackageManagerType.APT,
                    lastCheckInEpochMs = now - 12 * 60 * 1000,
                    pendingUpdates = listOf(
                        PackageUpdate(
                            name = "openssl",
                            currentVersion = "3.0.13-1",
                            availableVersion = "3.0.13-4",
                            isSecurityCritical = true,
                            advisoryNote = "CVE-2026-4102 High Severity TLS Fix"
                        ),
                        PackageUpdate(
                            name = "openssh-server",
                            currentVersion = "9.6p1-3",
                            availableVersion = "9.6p1-5",
                            isSecurityCritical = true,
                            advisoryNote = "USN-6891-1 sshd auth hardening"
                        ),
                        PackageUpdate(
                            name = "docker-ce",
                            currentVersion = "27.1.1-1",
                            availableVersion = "27.3.1-1"
                        ),
                        PackageUpdate(
                            name = "jq",
                            currentVersion = "1.7-1",
                            availableVersion = "1.7.1-1"
                        )
                    ),
                    healthScore = 34.0
                ),
                MonitoredDevice(
                    id = "device-homeassistant",
                    hostname = "homeassistant.local",
                    osDetails = "Home Assistant OS 14.2 (Tuesday HACS Add-on)",
                    agentVersion = "0.4.2-hacs",
                    packageManager = PackageManagerType.HACS,
                    lastCheckInEpochMs = now - 3 * 60 * 1000,
                    pendingUpdates = listOf(
                        PackageUpdate(
                            name = "hacs/tuesday-server",
                            currentVersion = "0.4.1",
                            availableVersion = "0.4.2"
                        ),
                        PackageUpdate(
                            name = "esphome",
                            currentVersion = "2026.9.1",
                            availableVersion = "2026.10.0"
                        )
                    ),
                    healthScore = 78.0
                ),
                MonitoredDevice(
                    id = "device-lml",
                    hostname = "lml.local",
                    osDetails = "Unraid NAS 6.12.14 (Docker & APK)",
                    agentVersion = "0.2.1",
                    packageManager = PackageManagerType.DOCKER,
                    lastCheckInEpochMs = now - 38 * 60 * 1000,
                    pendingUpdates = listOf(
                        PackageUpdate(name = "couchdb", currentVersion = "3.3.3", availableVersion = "3.4.1"),
                        PackageUpdate(name = "traefik", currentVersion = "v3.1.2", availableVersion = "v3.1.6"),
                        PackageUpdate(name = "tailscale", currentVersion = "1.72.1", availableVersion = "1.76.1")
                    ),
                    healthScore = 68.0
                ),
                MonitoredDevice(
                    id = "device-aaronpro18",
                    hostname = "aaronpro18",
                    osDetails = "macOS Sequoia 15.7.9 (Darwin 24.6.0 x86_64)",
                    agentVersion = "0.2.1",
                    packageManager = PackageManagerType.BREW,
                    lastCheckInEpochMs = now - 5 * 60 * 1000,
                    pendingUpdates = emptyList(),
                    healthScore = 100.0
                )
            )
        }
    }
}
