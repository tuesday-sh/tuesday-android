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
import sh.tuesday.app.BuildConfig
import sh.tuesday.app.models.*
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class TimelineSeedScenario(val label: String, val shortBadge: String, val description: String) {
    STANDARD("Standard Fleet", "12 Active", "Realistic release-to-EOL envelopes with live operational tenures"),
    ZOMBIE_OVERHANG("Zombie Outbreak", "3 Past EOL", "Multiple devices running past vendor EOL with red hatched overhangs"),
    DECOMMISSIONED("Decommissioned Fleet", "4 Retired", "Mix of active solid candles and hollow retired outlines"),
    RUNWAY_CRITICAL("Runway Critical", "4 Amber", "Imminent support cliffs (< 12 mo remaining) with amber warning fills")
}

class TuesdayRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tuesday_prefs_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Tuesday-Android/${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                .header("X-Tuesday-App-Version", BuildConfig.VERSION_NAME)
                .header("X-Tuesday-Build", BuildConfig.VERSION_CODE.toString())
                .build()
            chain.proceed(req)
        }
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

    private val _userSignature = MutableStateFlow(
        prefs.getString("user_signature", null) ?: "Aaron Bronow (sig_4b9a8c)"
    )
    val userSignature: StateFlow<String> = _userSignature.asStateFlow()

    fun setUserSignature(signature: String) {
        _userSignature.value = signature
        prefs.edit().putString("user_signature", signature).apply()
    }

    fun getAppEnvironmentString(): String {
        return "Tuesday Android v${BuildConfig.VERSION_NAME} (Pixel 9 • ${_activeNetworkId.value})"
    }

    private val _attestations = MutableStateFlow<List<DeviceAttestation>>(initialDefaultAttestations())
    val attestations: StateFlow<List<DeviceAttestation>> = _attestations.asStateFlow()

    private fun initialDefaultAttestations(): List<DeviceAttestation> {
        val baseDevices = BulletinsAndInventoryEngine.defaultRackPortDevices()
        val env = "Tuesday Android v${BuildConfig.VERSION_NAME} (Pixel 9 • net-default)"
        val sig = _userSignature.value
        return baseDevices.map { dev ->
            val attId = "att-${dev.portIndex}-ingress"
            val auditDate = dev.purchaseDate ?: "2024-02-01"
            val timestamp = 1706788800000L + (dev.portIndex * 86400000L)
            val iso = "2024-02-%02dT12:00:00Z".format((dev.portIndex % 28) + 1)
            val proof = BulletinsAndInventoryEngine.computeAttestationProof(
                id = attId,
                deviceId = dev.id,
                type = AttestationType.PRESENCE_AUDIT,
                auditDate = auditDate,
                userSignature = sig,
                environment = env,
                timestampMs = timestamp
            )
            DeviceAttestation(
                id = attId,
                deviceId = dev.id,
                deviceLabel = dev.shortLabel,
                type = AttestationType.PRESENCE_AUDIT,
                presenceState = DevicePresenceState.ACTIVE,
                timestampMs = timestamp,
                timestampIso = iso,
                auditDate = auditDate,
                userSignature = sig,
                environment = env,
                auditSessionId = "AUDIT-2024-Q1-INGRESS",
                note = "Initial point-in-time fleet inventory audit ingress.",
                cryptographicProof = proof
            )
        }.reversed()
    }

    fun recordAttestation(
        deviceId: String,
        type: AttestationType,
        auditDate: String,
        note: String? = null,
        auditSessionId: String? = null
    ): DeviceAttestation {
        val targetDev = _rackDevices.value.find { it.id == deviceId }
        val devLabel = targetDev?.shortLabel ?: targetDev?.name ?: deviceId
        val now = System.currentTimeMillis()
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(now))
        val env = getAppEnvironmentString()
        val sig = _userSignature.value
        val attId = "att-" + java.util.UUID.randomUUID().toString().take(8)
        val proof = BulletinsAndInventoryEngine.computeAttestationProof(
            id = attId,
            deviceId = deviceId,
            type = type,
            auditDate = auditDate,
            userSignature = sig,
            environment = env,
            timestampMs = now
        )

        val newState = type.resultingState
        val attestation = DeviceAttestation(
            id = attId,
            deviceId = deviceId,
            deviceLabel = devLabel,
            type = type,
            presenceState = newState,
            timestampMs = now,
            timestampIso = iso,
            auditDate = auditDate,
            userSignature = sig,
            environment = env,
            auditSessionId = auditSessionId ?: if (type == AttestationType.PRESENCE_AUDIT) "POINT-IN-TIME-AUDIT" else "LIFECYCLE-STATE-CHANGE",
            note = note,
            cryptographicProof = proof
        )

        _attestations.value = listOf(attestation) + _attestations.value

        _rackDevices.value = _rackDevices.value.map { dev ->
            if (dev.id == deviceId) {
                dev.copy(
                    presenceState = newState,
                    isRetired = newState.isOutOfService,
                    decommissionDate = if (newState.isOutOfService) auditDate else null,
                    latestAttestation = attestation,
                    attestationCount = dev.attestationCount + 1
                )
            } else dev
        }
        return attestation
    }

    fun recordBatchAudit(
        deviceIds: List<String>,
        auditDate: String,
        auditSessionId: String,
        note: String? = null
    ): List<DeviceAttestation> {
        val created = mutableListOf<DeviceAttestation>()
        deviceIds.forEach { id ->
            val att = recordAttestation(
                deviceId = id,
                type = AttestationType.PRESENCE_AUDIT,
                auditDate = auditDate,
                note = note,
                auditSessionId = auditSessionId
            )
            created.add(att)
        }
        return created
    }

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

    fun setBrandFlavor(flavor: AppBrandFlavor) {
        _brandFlavor.value = flavor
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
        val canonicalPurls = buildCanonicalPurls(hostname, discoveredMetadata)
        val inferredCategory = inferRackCategory(discoveredMetadata)
        val newRackDev = RackPortDevice(
            id = newDevice.id,
            portIndex = nextPort,
            category = inferredCategory,
            portState = if (isGuessed) PortLifecycleState.GUESSED else PortLifecycleState.OK,
            trustZone = TrustZone.Z3_IOT,
            assetTag = "STN-NEW-$nextPort",
            shortLabel = hostname.take(10),
            name = hostname,
            roomLocation = "Newly Enrolled",
            version = if (isGuessed) "unverified" else "2026.10.1",
            targetVersion = "2026.10.1",
            lifecycleTier = if (isGuessed) "Provisional Guess" else "Active",
            purls = canonicalPurls,
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
            s6Remediation = if (isGuessed) "Confirm exact model on Bulletins tab." else "No action required.",
            presenceState = DevicePresenceState.ACTIVE,
            isRetired = false
        )

        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now))
        val isoStr = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(now))
        val attId = "att-" + java.util.UUID.randomUUID().toString().take(8)
        val proof = BulletinsAndInventoryEngine.computeAttestationProof(
            id = attId,
            deviceId = newDevice.id,
            type = AttestationType.PRESENCE_AUDIT,
            auditDate = todayStr,
            userSignature = _userSignature.value,
            environment = getAppEnvironmentString(),
            timestampMs = now
        )
        val initialAttestation = DeviceAttestation(
            id = attId,
            deviceId = newDevice.id,
            deviceLabel = hostname.take(10),
            type = AttestationType.PRESENCE_AUDIT,
            presenceState = DevicePresenceState.ACTIVE,
            timestampMs = now,
            timestampIso = isoStr,
            auditDate = todayStr,
            userSignature = _userSignature.value,
            environment = getAppEnvironmentString(),
            auditSessionId = "ENROLL-AUDIT",
            note = "Device added via Point-in-Time Inventory Audit",
            cryptographicProof = proof
        )
        _attestations.value = listOf(initialAttestation) + _attestations.value
        val finalRackDev = newRackDev.copy(
            latestAttestation = initialAttestation,
            attestationCount = 1
        )
        _rackDevices.value = listOf(finalRackDev) + _rackDevices.value
        _activeDeviceCount.value = (_activeDeviceCount.value + 1).coerceAtMost(128)

        syncDeviceCreateToServerAsync(finalRackDev, discoveredMetadata)

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

    private fun buildCanonicalPurls(hostname: String, meta: DiscoveredDeviceMetadata?): List<String> {
        val rawVendor = meta?.manufacturer?.trim().orEmpty()
        val rawModel = meta?.modelNumber?.trim().orEmpty()
        if (rawVendor.isEmpty() && rawModel.isEmpty()) {
            return listOf("pkg:generic/enrolled/${hostname.lowercase().replace(Regex("[^a-z0-9-]+"), "-")}")
        }
        val vendorSlug = when {
            rawVendor.contains("espressif", ignoreCase = true) || rawVendor.contains("ai-thinker", ignoreCase = true) -> "espressif"
            rawVendor.contains("itead", ignoreCase = true) || rawVendor.contains("sonoff", ignoreCase = true) -> "sonoff"
            rawVendor.contains("ubiquiti", ignoreCase = true) -> "ubiquiti"
            rawVendor.contains("cisco", ignoreCase = true) -> "cisco"
            rawVendor.contains("synology", ignoreCase = true) -> "synology"
            rawVendor.contains("motorola", ignoreCase = true) -> "motorola"
            else -> rawVendor.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        }.ifBlank { "generic" }

        val parts = rawModel.split(Regex("\\s*[/|,]\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
        val purls = parts.mapNotNull { part ->
            val slug = part.replace("+", "-plus").lowercase().replace(Regex("[^a-z0-9.-]+"), "-").trim('-')
            if (slug.isNotEmpty()) "pkg:generic/$vendorSlug/$slug@1.0.0" else null
        }.distinct().toMutableList()

        if (purls.isEmpty()) {
            val fallbackSlug = hostname.lowercase().replace("+", "-plus").replace(Regex("[^a-z0-9.-]+"), "-").trim('-')
            purls.add("pkg:generic/$vendorSlug/$fallbackSlug@1.0.0")
        }
        return purls
    }

    private fun inferRackCategory(meta: DiscoveredDeviceMetadata?): StnHardwareCategory {
        val combined = "${meta?.deviceCategory.orEmpty()} ${meta?.modelNumber.orEmpty()} ${meta?.manufacturer.orEmpty()}".lowercase()
        return when {
            combined.contains("router") || combined.contains("gateway") || combined.contains("modem") || combined.contains("docsis") || combined.contains("ucg") || combined.contains("uxg") || combined.contains("mb8600") -> StnHardwareCategory.C1_GATEWAY
            combined.contains("switch") || combined.contains("2960") || combined.contains("usw") -> StnHardwareCategory.C2_SWITCH
            combined.contains("wireless") || combined.contains("access point") || combined.contains("u6-") || combined.contains("u7-") -> StnHardwareCategory.C3_AP
            combined.contains("nas") || combined.contains("synology") || combined.contains("server") || combined.contains("ups") || combined.contains("power") -> StnHardwareCategory.C4_SERVER
            combined.contains("camera") || combined.contains("protect") -> StnHardwareCategory.C5_CAMERA
            combined.contains("workstation") || combined.contains("macbook") || combined.contains("laptop") -> StnHardwareCategory.C7_CLIENT
            else -> StnHardwareCategory.C6_IOT
        }
    }

    fun registerPushTokenAsync(pushToken: String = "fcm-local-$clientDeviceId") {
        ioScope.launch {
            try {
                val baseUrl = _hacsConfig.value.tuesdayApiBaseUrl.ifBlank { GeminiDiscoveryService.DEFAULT_SERVER_BASE_URL }.trimEnd('/')
                val payload = JSONObject().apply {
                    put("client_device_id", clientDeviceId)
                    put("platform", "android")
                    put("push_token", pushToken)
                    put("app_flavor", if (_brandFlavor.value == AppBrandFlavor.SURETHING_HOME) "surething" else "tuesday")
                    put("notifications_enabled", true)
                }
                val req = Request.Builder()
                    .url("$baseUrl/api/v1/notifications/register_token")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Tuesday-Device-ID", clientDeviceId)
                    .addHeader("X-Tuesday-Client-Platform", "android")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (e: Exception) {
                Log.w("TuesdayRepoPush", "Push token registration skipped: ${e.message}")
            }
        }
    }

    fun acknowledgeNotificationAsync(deliveryId: Int, actionType: String = "opened") {
        if (deliveryId <= 0) return
        ioScope.launch {
            try {
                val baseUrl = _hacsConfig.value.tuesdayApiBaseUrl.ifBlank { GeminiDiscoveryService.DEFAULT_SERVER_BASE_URL }.trimEnd('/')
                val payload = JSONObject().apply {
                    put("action_type", actionType)
                }
                val req = Request.Builder()
                    .url("$baseUrl/api/v1/notifications/$deliveryId/ack")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Tuesday-Device-ID", clientDeviceId)
                    .addHeader("X-Tuesday-Client-Platform", "android")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (e: Exception) {
                Log.w("TuesdayRepoPush", "Notification ack skipped: ${e.message}")
            }
        }
    }

    suspend fun syncPendingNotifications(
        notificationManager: TuesdayNotificationManager,
        forceResend: Boolean = false,
        includeAllSites: Boolean = true
    ): Int = withContext(Dispatchers.IO) {
        try {
            val baseUrl = _hacsConfig.value.tuesdayApiBaseUrl.ifBlank { GeminiDiscoveryService.DEFAULT_SERVER_BASE_URL }.trimEnd('/')

            // 1. Register push token
            val regPayload = JSONObject().apply {
                put("client_device_id", clientDeviceId)
                put("platform", "android")
                put("push_token", "fcm-local-$clientDeviceId")
                put("app_flavor", if (_brandFlavor.value == AppBrandFlavor.SURETHING_HOME) "surething" else "tuesday")
                put("notifications_enabled", true)
            }
            val regReq = Request.Builder()
                .url("$baseUrl/api/v1/notifications/register_token")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Tuesday-Device-ID", clientDeviceId)
                .addHeader("X-Tuesday-Client-Platform", "android")
                .post(regPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(regReq).execute().close()

            // 2. Optionally force dispatch evaluation across enrolled sites
            if (forceResend) {
                val dispPayload = JSONObject().apply {
                    put("force_resend", true)
                    put("all_sites", includeAllSites)
                }
                val dispReq = Request.Builder()
                    .url("$baseUrl/api/v1/notifications/dispatch")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Tuesday-Device-ID", clientDeviceId)
                    .addHeader("X-Tuesday-Client-Platform", "android")
                    .post(dispPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(dispReq).execute().close()
            }

            // 3. Fetch pending notifications
            val pendingUrl = "$baseUrl/api/v1/notifications/pending?all_sites=$includeAllSites"
            val pendingReq = Request.Builder()
                .url(pendingUrl)
                .addHeader("X-Tuesday-Device-ID", clientDeviceId)
                .addHeader("X-Tuesday-Client-Platform", "android")
                .get()
                .build()

            var shownCount = 0
            val syncedDevicesFromNotifs = mutableListOf<RackPortDevice>()

            httpClient.newCall(pendingReq).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext 0
                val bodyStr = resp.body?.string().orEmpty()
                val root = JSONObject(bodyStr)
                val arr = root.optJSONArray("notifications") ?: JSONArray()

                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val notif = PendingPushNotification(
                        id = item.optInt("id"),
                        bulletinExternalId = item.optString("bulletinExternalId", item.optString("bulletin_external_id")),
                        feedSlug = item.optString("feedSlug", item.optString("feed_slug")).takeIf { it.isNotBlank() },
                        bulletinType = item.optString("bulletinType", item.optString("bulletin_type", "advisory")),
                        isHighSev = if (item.has("isHighSev")) item.optBoolean("isHighSev", false) else item.optBoolean("is_high_sev", false),
                        brokeThroughMute = if (item.has("brokeThroughMute")) item.optBoolean("brokeThroughMute", false) else item.optBoolean("broke_through_mute", false),
                        matchedByGuess = if (item.has("matchedByGuess")) item.optBoolean("matchedByGuess", false) else item.optBoolean("matched_by_guess", false),
                        deliveryChannel = item.optString("deliveryChannel", item.optString("delivery_channel", TuesdayNotificationManager.CHANNEL_LIFECYCLE_DIGESTS)),
                        deliveryStatus = item.optString("deliveryStatus", item.optString("delivery_status", "pending")),
                        title = item.optString("title"),
                        body = item.optString("body"),
                        deepLinkUri = item.optString("deepLinkUri", item.optString("deep_link_uri", "tuesday://bulletins")),
                        deviceDeepLinkUri = item.optString("deviceDeepLinkUri", item.optString("device_deep_link_uri")).takeIf { it.isNotBlank() },
                        monitoredDeviceId = item.optString("monitoredDeviceId", item.optString("monitored_device_id")).takeIf { it.isNotBlank() },
                        monitoredDeviceLabel = item.optString("monitoredDeviceLabel", item.optString("monitored_device_label")).takeIf { it.isNotBlank() },
                        categoryCode = item.optString("categoryCode", item.optString("category_code")).takeIf { it.isNotBlank() }
                    )

                    if (notificationManager.showPushNotification(notif)) {
                        shownCount++
                    }

                    // Ensure the corresponding enrolled hardware is represented in _rackDevices so deep links and Bulletins tab match it
                    val devId = notif.monitoredDeviceId ?: "srv-${notif.bulletinExternalId}"
                    val hostname = notif.monitoredDeviceLabel ?: notif.title
                    val matchingBul = BulletinsAndInventoryEngine.defaultGoldBulletins()
                        .find { it.externalId.equals(notif.bulletinExternalId, ignoreCase = true) }
                    val purls = if (matchingBul != null) listOf("${matchingBul.purlBase}@1.0.0") else listOf("pkg:generic/enrolled/${hostname.lowercase()}")
                    val isGuess = notif.matchedByGuess
                    val portState = when {
                        isGuess -> PortLifecycleState.GUESSED
                        notif.bulletinType.equals("eol", ignoreCase = true) -> PortLifecycleState.EOL
                        notif.isHighSev -> PortLifecycleState.CVE_KEV
                        else -> PortLifecycleState.UPDATE
                    }
                    val cat = inferRackCategory(
                        DiscoveredDeviceMetadata(
                            deviceCategory = notif.categoryCode ?: "",
                            manufacturer = hostname,
                            modelNumber = hostname
                        )
                    )
                    syncedDevicesFromNotifs.add(
                        RackPortDevice(
                            id = devId,
                            portIndex = 20 + i,
                            category = cat,
                            portState = portState,
                            trustZone = TrustZone.Z1_TRUSTED,
                            assetTag = "STN-LAB-${20 + i}",
                            shortLabel = hostname.take(12),
                            name = hostname,
                            roomLocation = "Enrolled Lab",
                            version = if (isGuess) "unverified" else "1.0.0",
                            targetVersion = "latest",
                            lifecycleTier = if (isGuess) "Provisional Guess" else (if (portState == PortLifecycleState.EOL) "End of Life" else "Active"),
                            purls = purls,
                            confidenceTier = if (isGuess) DeviceConfidenceTier.PROVISIONAL_GUESS else DeviceConfidenceTier.CONFIRMED,
                            candidateModels = if (isGuess) listOf(hostname) else emptyList(),
                            proHeadline = notif.title,
                            stnHeadline = notif.body,
                            s0Identity = "STN-LAB-${20 + i} • Enrolled Lab • Z1 Trusted",
                            s1Hardware = hostname,
                            s2Software = "Matched Bulletin: ${notif.bulletinExternalId}",
                            s3Lifecycle = if (isGuess) "Provisional Guess" else "Active Monitoring",
                            s4Advisories = "${notif.bulletinType.uppercase()}: ${notif.bulletinExternalId}",
                            s5Topology = "Enrolled Inventory",
                            s6Remediation = notif.body
                        )
                    )
                }
            }

            if (syncedDevicesFromNotifs.isNotEmpty()) {
                val existingById = _rackDevices.value.associateBy { it.id }.toMutableMap()
                for (d in syncedDevicesFromNotifs) {
                    existingById[d.id] = d
                }
                val merged = syncedDevicesFromNotifs + _rackDevices.value.filter { dev ->
                    syncedDevicesFromNotifs.none { it.id == dev.id }
                }
                _rackDevices.value = merged
                if (_activeDeviceCount.value < syncedDevicesFromNotifs.size) {
                    _activeDeviceCount.value = (syncedDevicesFromNotifs.size + 4).coerceAtMost(128)
                }
            }

            shownCount
        } catch (e: Exception) {
            Log.w("TuesdayRepoPush", "Failed to sync pending notifications: ${e.message}")
            0
        }
    }

    fun triggerMockPatchTuesdayBulletin(notificationManager: TuesdayNotificationManager): Boolean {
        _bulletinDismissed.value = false
        _bulletinSnoozed.value = false
        _bulletinMuted.value = false
        prefs.edit()
            .putBoolean("bulletin_dismissed", false)
            .putBoolean("bulletin_snoozed", false)
            .putBoolean("bulletin_muted", false)
            .apply()

        val isStn = _brandFlavor.value == AppBrandFlavor.SURETHING_HOME
        val mockNotif = PendingPushNotification(
            id = 9901,
            bulletinExternalId = "bul-00-rhythm-patch-tuesday",
            feedSlug = "patch-tuesday-rhythm",
            bulletinType = "rhythm",
            isHighSev = true,
            brokeThroughMute = false,
            matchedByGuess = false,
            deliveryChannel = TuesdayNotificationManager.CHANNEL_CRITICAL_BULLETINS,
            deliveryStatus = "pending",
            title = if (isStn) {
                "📅 Patch Tuesday Check-In: 3 Safety & Security Fixes Ready"
            } else {
                "📅 Patch Tuesday Triage Drop: 3 High-Sev Actions & 2 Firmware Updates"
            },
            body = if (isStn) {
                "Your monthly network check-in is ready (10:00 AM PT). We found 3 priority security items and 2 routine software updates across your enrolled devices. Tap to open your Patch Tuesday checklist."
            } else {
                "Monthly patch window open (10:00 AM PT). Prioritized queue: 1 EOL switch (Cisco 2960-8TC-L), 1 Critical Override CVE (ESP8266MOD), 1 Provisional NAS CVE (Synology DS220+), and 2 routine coordinator/modem updates."
            },
            deepLinkUri = "tuesday://bulletins/bul-00-rhythm",
            deviceDeepLinkUri = null,
            monitoredDeviceId = null,
            monitoredDeviceLabel = "Monthly Rhythm • 2nd Tuesday",
            categoryCode = null
        )

        val shown = notificationManager.showPushNotification(mockNotif)
        emitTelemetryEventAsync(
            eventName = "notification.mock_patch_tuesday_triggered",
            targetEntityType = "CuratedBulletin",
            targetEntityId = mockNotif.bulletinExternalId,
            properties = mapOf("shown" to shown)
        )
        return shown
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
        selectNetwork("net-default")
        prefs.edit()
            .putBoolean("bulletin_dismissed", false)
            .putBoolean("bulletin_snoozed", false)
            .putBoolean("bulletin_muted", false)
            .apply()
        persistDevices()
        persistConfig()
    }

    fun enableDemoMode() {
        val demoNet = _userNetworks.value.find { it.id == "net-demo" }
        if (demoNet == null) {
            val created = NetworkSite(
                id = "net-demo",
                name = "Demo Network",
                devicesCount = 12,
                role = "Evaluation Homelab (Demo Mode)"
            )
            _userNetworks.value = _userNetworks.value + created
        } else {
            _userNetworks.value = _userNetworks.value.map {
                if (it.id == "net-demo") it.copy(devicesCount = 12) else it
            }
        }
        selectNetwork("net-demo")
        _activeDeviceCount.value = 12
    }

    fun seedTimelineScenario(scenario: TimelineSeedScenario) {
        val seededDevices = generateTimelineSeedScenario(scenario)
        _rackDevices.value = seededDevices
        _activeDeviceCount.value = seededDevices.size

        if (scenario == TimelineSeedScenario.DECOMMISSIONED) {
            val env = getAppEnvironmentString()
            val sig = _userSignature.value
            val decommAttestations = listOf(
                createSeedAttestation("dev-01", "UDM-Pro", AttestationType.ABSENCE_RETIRED, DevicePresenceState.RETIRED, "2025-08-15", "Replaced with UDM-SE for 2.5GbE PoE backhaul", sig, env),
                createSeedAttestation("dev-04", "USW-Lite", AttestationType.ABSENCE_RETIRED, DevicePresenceState.RETIRED, "2025-10-01", "Consolidated patch cables to 24-port switch", sig, env),
                createSeedAttestation("dev-07", "Shelly 1PM", AttestationType.ABSENCE_STOPPED_WORKING, DevicePresenceState.STOPPED_WORKING, "2025-04-20", "Internal relay burnt out after electrical storm surge; power dead", sig, env),
                createSeedAttestation("dev-11", "Hue Bridge", AttestationType.ABSENCE_LOST, DevicePresenceState.LOST, "2024-11-30", "Unaccounted during Q4 physical audit walk; missing from rack cabinet", sig, env)
            )
            _attestations.value = decommAttestations + initialDefaultAttestations()
        } else {
            _attestations.value = initialDefaultAttestations()
        }
    }

    private fun createSeedAttestation(
        deviceId: String,
        label: String,
        type: AttestationType,
        state: DevicePresenceState,
        auditDate: String,
        note: String,
        sig: String,
        env: String
    ): DeviceAttestation {
        val attId = "att-" + UUID.randomUUID().toString().take(8)
        val timestamp = 1735689600000L
        val proof = BulletinsAndInventoryEngine.computeAttestationProof(
            id = attId,
            deviceId = deviceId,
            type = type,
            auditDate = auditDate,
            userSignature = sig,
            environment = env,
            timestampMs = timestamp
        )
        return DeviceAttestation(
            id = attId,
            deviceId = deviceId,
            deviceLabel = label,
            type = type,
            presenceState = state,
            timestampMs = timestamp,
            timestampIso = "${auditDate}T14:30:00Z",
            auditDate = auditDate,
            userSignature = sig,
            environment = env,
            auditSessionId = "AUDIT-DECOMM-CYCLE",
            note = note,
            cryptographicProof = proof
        )
    }

    companion object {
        fun generateTimelineSeedScenario(
            scenario: TimelineSeedScenario,
            baseDevices: List<RackPortDevice> = BulletinsAndInventoryEngine.defaultRackPortDevices()
        ): List<RackPortDevice> = when (scenario) {
            TimelineSeedScenario.STANDARD -> baseDevices
            TimelineSeedScenario.ZOMBIE_OVERHANG -> baseDevices.map { dev ->
                when (dev.portIndex) {
                    2 -> dev.copy(
                        portState = PortLifecycleState.EOL,
                        vendorEolAt = "2025-12-31",
                        candles = listOf(DeviceLifecycleCandle("1.0.11.136", 0.08f, 0.18f, 0.96f, 0.65f, null))
                    )
                    6 -> dev.copy(
                        portState = PortLifecycleState.EOL,
                        shortLabel = "Zombie Pi",
                        vendorEolAt = "2025-06-30",
                        candles = listOf(DeviceLifecycleCandle("legacy-kernel", 0.20f, 0.35f, 0.96f, 0.58f, null))
                    )
                    10 -> dev.copy(
                        portState = PortLifecycleState.EOL,
                        vendorEolAt = "2026-03-31",
                        candles = listOf(DeviceLifecycleCandle("webos-6", 0.16f, 0.32f, 0.96f, 0.62f, 0.50f))
                    )
                    else -> dev
                }
            }
            TimelineSeedScenario.DECOMMISSIONED -> baseDevices.map { dev ->
                when (dev.portIndex) {
                    1 -> dev.copy(isRetired = true, presenceState = DevicePresenceState.RETIRED, decommissionDate = "2025-08-15")
                    4 -> dev.copy(isRetired = true, presenceState = DevicePresenceState.RETIRED, decommissionDate = "2025-10-01")
                    7 -> dev.copy(isRetired = true, presenceState = DevicePresenceState.STOPPED_WORKING, decommissionDate = "2025-04-20")
                    11 -> dev.copy(isRetired = true, presenceState = DevicePresenceState.LOST, decommissionDate = "2024-11-30")
                    else -> dev
                }
            }
            TimelineSeedScenario.RUNWAY_CRITICAL -> baseDevices.map { dev ->
                when (dev.portIndex) {
                    5 -> dev.copy(
                        vendorEolAt = "2026-12-31",
                        candles = listOf(DeviceLifecycleCandle("15.3.1", 0.35f, 0.52f, 0.90f, 0.75f, null))
                    )
                    8 -> dev.copy(
                        vendorEolAt = "2027-01-15",
                        candles = listOf(DeviceLifecycleCandle("8.3.1", 0.40f, 0.54f, 0.90f, 0.76f, null))
                    )
                    9 -> dev.copy(
                        vendorEolAt = "2026-11-30",
                        candles = listOf(DeviceLifecycleCandle("1.0.4", 0.22f, 0.52f, 0.96f, 0.74f, 0.62f))
                    )
                    10 -> dev.copy(
                        vendorEolAt = "2027-02-28",
                        candles = listOf(DeviceLifecycleCandle("8.1.0", 0.16f, 0.36f, 0.96f, 0.77f, 0.70f))
                    )
                    else -> dev
                }
            }
        }
        fun defaultUserNetworks(): List<NetworkSite> = listOf(
            NetworkSite(id = "net-default", name = "My Network", devicesCount = 0, role = "Primary Site"),
            NetworkSite(id = "net-demo", name = "Demo Network", devicesCount = 12, role = "Evaluation Homelab (Demo Mode)")
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
