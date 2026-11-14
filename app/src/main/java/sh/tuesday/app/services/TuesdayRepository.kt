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

    private val _isVisionDebugEnabled = MutableStateFlow(false)
    val isVisionDebugEnabled: StateFlow<Boolean> = _isVisionDebugEnabled.asStateFlow()

    private val _isDiagnosticsEnabled = MutableStateFlow(false)
    val isDiagnosticsEnabled: StateFlow<Boolean> = _isDiagnosticsEnabled.asStateFlow()

    private val _userSignature = MutableStateFlow(
        prefs.getString("user_signature", null) ?: "Aaron Bronow (sig_4b9a8c)"
    )

    fun getAppEnvironmentString(): String {
        val deviceModel = android.os.Build.MODEL.takeIf { it.isNotBlank() } ?: "Android"
        return "Tuesday Android v${BuildConfig.VERSION_NAME} ($deviceModel • ${_activeNetworkId.value})"
    }

    private val _attestations = MutableStateFlow<List<DeviceAttestation>>(initialDefaultAttestations())
    val attestations: StateFlow<List<DeviceAttestation>> = _attestations.asStateFlow()

    private fun initialDefaultAttestations(): List<DeviceAttestation> {
        val baseDevices = BulletinsAndInventoryEngine.defaultRackPortDevices()
        val env = "Tuesday Android v${BuildConfig.VERSION_NAME} (Pixel 5 • net-default)"
        val sig = _userSignature.value
        return baseDevices.map { dev ->
            val attId = "att-${dev.portIndex}-ingress"
            val isDecomm = dev.presenceState != DevicePresenceState.ACTIVE || dev.isRetired
            val auditDate = if (isDecomm) {
                dev.decommissionDate ?: "2025-10-01"
            } else if (dev.portIndex in listOf(1, 3, 4, 5, 7, 8)) {
                "2026-01-15"
            } else {
                dev.purchaseDate ?: "2024-02-01"
            }
            val attType = when (dev.presenceState) {
                DevicePresenceState.RETIRED -> AttestationType.ABSENCE_RETIRED
                DevicePresenceState.STOPPED_WORKING -> AttestationType.ABSENCE_STOPPED_WORKING
                DevicePresenceState.LOST -> AttestationType.ABSENCE_LOST
                DevicePresenceState.ACTIVE -> AttestationType.PRESENCE_AUDIT
            }
            val noteText = when (dev.presenceState) {
                DevicePresenceState.RETIRED -> "Decommissioned during seasonal rack consolidation."
                DevicePresenceState.STOPPED_WORKING -> "Hardware fault detected during check-in."
                DevicePresenceState.LOST -> "Unaccounted for during seasonal inventory check-in."
                DevicePresenceState.ACTIVE -> "Enrolled during initial hardware check-in."
            }
            val timestamp = 1706788800000L + (dev.portIndex * 86400000L)
            val iso = "${auditDate}T12:00:00Z"
            val proof = BulletinsAndInventoryEngine.computeAttestationProof(
                id = attId,
                deviceId = dev.id,
                type = attType,
                auditDate = auditDate,
                userSignature = sig,
                environment = env,
                timestampMs = timestamp
            )
            DeviceAttestation(
                id = attId,
                deviceId = dev.id,
                deviceLabel = dev.shortLabel,
                type = attType,
                presenceState = dev.presenceState,
                timestampMs = timestamp,
                timestampIso = iso,
                auditDate = auditDate,
                userSignature = sig,
                environment = env,
                auditSessionId = "CHECKIN-2024-Q1-INITIAL",
                note = noteText,
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

    private val _entitlementTier = MutableStateFlow(prefs.getString("entitlement_tier", "free") ?: "free")
    val entitlementTier: StateFlow<String> = _entitlementTier.asStateFlow()

    private val _activeSubscriptionSku = MutableStateFlow(
        prefs.getString("active_subscription_sku", TuesdayBillingManager.SKU_PRO_TIER1_ANNUAL)
            ?: TuesdayBillingManager.SKU_PRO_TIER1_ANNUAL
    )
    val activeSubscriptionSku: StateFlow<String> = _activeSubscriptionSku.asStateFlow()

    fun isProTier1Entitled(tier: String = _entitlementTier.value): Boolean {
        return tier == "pro_tier_1" || tier == "multisite_tier_1" || tier == "fleet"
    }

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

    private val _showStateFilterStrip = MutableStateFlow(prefs.getBoolean("pref_show_state_filter_strip", false))
    val showStateFilterStrip: StateFlow<Boolean> = _showStateFilterStrip.asStateFlow()

    private val _showZoneFilterStrip = MutableStateFlow(prefs.getBoolean("pref_show_zone_filter_strip", false))
    val showZoneFilterStrip: StateFlow<Boolean> = _showZoneFilterStrip.asStateFlow()

    init {
        loadConfig()
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

    fun toggleDiagnosticsMode(enabled: Boolean) {
        _isDiagnosticsEnabled.value = enabled
        prefs.edit().putBoolean("is_diagnostics_enabled", enabled).apply()
    }

    fun setSimulatedDeviceCount(count: Int) {
        val clamped = count.coerceIn(0, 128)
        _rackDevices.value = BulletinsAndInventoryEngine.defaultRackPortDevices()
        _attestations.value = initialDefaultAttestations()
        _activeDeviceCount.value = clamped
        _selectedZoneFilter.value = null
        _selectedPortStateFilter.value = null
        if (clamped == 0) {
            _bulletinDismissed.value = false
            _bulletinSnoozed.value = false
            _bulletinMuted.value = false
            prefs.edit()
                .putBoolean("bulletin_dismissed", false)
                .putBoolean("bulletin_snoozed", false)
                .putBoolean("bulletin_muted", false)
                .apply()
        }
        _userNetworks.value = _userNetworks.value.map {
            if (it.id == _activeNetworkId.value) it.copy(devicesCount = clamped) else it
        }
        persistNetworks()
    }

    fun selectNetwork(netId: String) {
        val target = _userNetworks.value.find { it.id == netId } ?: return
        _activeNetworkId.value = target.id
        _activeDeviceCount.value = target.devicesCount
        _selectedZoneFilter.value = null
        _selectedPortStateFilter.value = null
        persistNetworks()
    }

    fun renameActiveNetwork(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        _userNetworks.value = _userNetworks.value.map {
            if (it.id == _activeNetworkId.value) it.copy(name = trimmed) else it
        }
        persistNetworks()
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

    fun setShowStateFilterStrip(enabled: Boolean) {
        _showStateFilterStrip.value = enabled
        prefs.edit().putBoolean("pref_show_state_filter_strip", enabled).apply()
        if (!enabled) {
            _selectedPortStateFilter.value = null
        }
    }

    fun setShowZoneFilterStrip(enabled: Boolean) {
        _showZoneFilterStrip.value = enabled
        prefs.edit().putBoolean("pref_show_zone_filter_strip", enabled).apply()
        if (!enabled) {
            _selectedZoneFilter.value = null
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
                    purls = listOf("pkg:generic/ecobee/smart-thermostat@4.8.7.210")
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
                    excludedPurlPrefixes = dev.excludedPurlPrefixes + "pkg:generic/ecobee/*"
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
        _isVisionDebugEnabled.value = prefs.getBoolean("is_vision_debug_enabled", false)
        _isDiagnosticsEnabled.value = prefs.getBoolean("is_diagnostics_enabled", false)
        val raw = prefs.getString("hacs_config", null)
        if (raw != null) {
            try {
                _hacsConfig.value = json.decodeFromString(raw)
            } catch (_: Exception) {
                _hacsConfig.value = HacsConfig()
            }
        }
        val rawNets = prefs.getString("user_networks_json", null)
        if (!rawNets.isNullOrBlank()) {
            try {
                val decoded = json.decodeFromString<List<NetworkSite>>(rawNets)
                if (decoded.isNotEmpty()) {
                    _userNetworks.value = decoded
                }
            } catch (_: Exception) {
                _userNetworks.value = defaultUserNetworks()
            }
        }
        val savedNetId = prefs.getString("active_network_id", null)
        if (!savedNetId.isNullOrBlank()) {
            val found = _userNetworks.value.find { it.id == savedNetId }
            if (found != null) {
                _activeNetworkId.value = found.id
                _activeDeviceCount.value = found.devicesCount
            }
        }
    }

    private fun persistNetworks() {
        val rawNets = json.encodeToString(_userNetworks.value)
        prefs.edit()
            .putString("user_networks_json", rawNets)
            .putString("active_network_id", _activeNetworkId.value)
            .apply()
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
        val newDeviceId = UUID.randomUUID().toString()

        // Add to rackDevices so it reflects in the 128-LED Matrix & Action-First list
        val nextPort = (_rackDevices.value.maxOfOrNull { it.portIndex } ?: 12) + 1
        val isGuessed = inventoryState == InventoryConfidenceState.INCOMPLETE_OR_GUESSED
        val canonicalPurls = buildCanonicalPurls(hostname, discoveredMetadata)
        val inferredCategory = inferRackCategory(discoveredMetadata)
        val newRackDev = RackPortDevice(
            id = newDeviceId,
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
            deviceId = newDeviceId,
            type = AttestationType.PRESENCE_AUDIT,
            auditDate = todayStr,
            userSignature = _userSignature.value,
            environment = getAppEnvironmentString(),
            timestampMs = now
        )
        val initialAttestation = DeviceAttestation(
            id = attId,
            deviceId = newDeviceId,
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
        val nextCount = (_activeDeviceCount.value + 1).coerceAtMost(128)
        _activeDeviceCount.value = nextCount
        _userNetworks.value = _userNetworks.value.map {
            if (it.id == _activeNetworkId.value) it.copy(devicesCount = nextCount) else it
        }
        persistNetworks()

        syncDeviceCreateToServerAsync(finalRackDev, discoveredMetadata)

        _isSyncing.value = false
    }

    fun markDeviceUpdatesApplied(deviceId: String) {
        _rackDevices.value = _rackDevices.value.map {
            if (it.id == deviceId) {
                it.copy(
                    portState = PortLifecycleState.OK,
                    version = it.targetVersion ?: it.version
                )
            } else it
        }
        syncDevicePatchToServerAsync(
            deviceId = deviceId,
            payload = JSONObject().apply {
                put("mark_remediated", true)
            }
        )
    }

    fun updateDeviceDetails(
        deviceId: String,
        name: String,
        shortLabel: String,
        roomLocation: String,
        trustZone: TrustZone,
        assetTag: String
    ): RackPortDevice? {
        var updated: RackPortDevice? = null
        _rackDevices.value = _rackDevices.value.map { dev ->
            if (dev.id == deviceId) {
                val next = dev.copy(
                    name = name.trim().ifBlank { dev.name },
                    shortLabel = shortLabel.trim().ifBlank { dev.shortLabel },
                    roomLocation = roomLocation.trim().ifBlank { dev.roomLocation },
                    trustZone = trustZone,
                    assetTag = assetTag.trim()
                )
                updated = next
                next
            } else dev
        }
        updated?.let { next ->
            syncDevicePatchToServerAsync(
                deviceId = deviceId,
                payload = JSONObject().apply {
                    put("name", next.name)
                    put("short_label", next.shortLabel)
                    put("room_location", next.roomLocation)
                    put("trust_zone", next.trustZone.key)
                    put("asset_tag", next.assetTag)
                }
            )
        }
        return updated
    }

    fun billingAccountIdentifier(): String = "device-$clientDeviceId"

    fun billingProfileSlug(): String = "net-default"

    fun upgradeToProTier1(
        productId: String = TuesdayBillingManager.SKU_PRO_TIER1_ANNUAL,
        purchaseToken: String? = null
    ) {
        val canonicalSku = when (productId) {
            "sh.tuesday.multisite.tier1.monthly", "sh.tuesday.pro.monthly" -> TuesdayBillingManager.SKU_PRO_TIER1_MONTHLY
            "sh.tuesday.multisite.tier1.annual", "sh.tuesday.pro.yearly" -> TuesdayBillingManager.SKU_PRO_TIER1_ANNUAL
            else -> productId
        }
        val isSandbox = purchaseToken.isNullOrBlank() ||
            purchaseToken.startsWith("gp_sandbox_") ||
            purchaseToken.startsWith("gp_token_")
        val entitlementSource = if (isSandbox) "sandbox" else "google_play"

        _entitlementTier.value = "pro_tier_1"
        _activeSubscriptionSku.value = canonicalSku
        prefs.edit()
            .putString("entitlement_tier", "pro_tier_1")
            .putString("active_subscription_sku", canonicalSku)
            .putString("entitlement_source", entitlementSource)
            .apply()

        emitTelemetryEventAsync(
            eventName = "billing.tier1_upgraded",
            targetEntityType = "NetworkSite",
            targetEntityId = _activeNetworkId.value,
            properties = mapOf(
                "productId" to canonicalSku,
                "entitlementTier" to "pro_tier_1",
                "entitlementSource" to entitlementSource
            )
        )

        if (isRobolectricJvm()) return

        ioScope.launch {
            try {
                val baseUrl = _hacsConfig.value.tuesdayApiBaseUrl.ifBlank { GeminiDiscoveryService.DEFAULT_SERVER_BASE_URL }.trimEnd('/')
                val canonicalPkg = BuildConfig.APPLICATION_ID.removeSuffix(".debug")
                val payload = JSONObject().apply {
                    put("platform", "android")
                    put("package_name", canonicalPkg)
                    put("product_id", canonicalSku)
                    put("purchase_token", purchaseToken?.takeIf { it.isNotBlank() } ?: "gp_token_${UUID.randomUUID()}")
                }
                val req = Request.Builder()
                    .url("$baseUrl/api/v1/billing/verify")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Tuesday-Device-ID", clientDeviceId)
                    .addHeader("X-Tuesday-Client-Platform", "android")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string().orEmpty()
                        if (bodyStr.isNotBlank()) {
                            val root = JSONObject(bodyStr)
                            val serverTier = root.optString("entitlementTier", "pro_tier_1")
                            if (serverTier.isNotBlank()) {
                                _entitlementTier.value = serverTier
                                prefs.edit().putString("entitlement_tier", serverTier).apply()
                            }
                        }
                    } else if (!isSandbox && resp.code in listOf(403, 409, 422)) {
                        Log.w("TuesdayRepoSync", "Server rejected Play receipt (HTTP ${resp.code}); reverting local tier to free")
                        downgradeToFreeTier()
                    }
                }
            } catch (e: Exception) {
                Log.w("TuesdayRepoSync", "Billing verification queued offline: ${e.message}")
            }
        }
    }

    fun downgradeToFreeTier() {
        _entitlementTier.value = "free"
        prefs.edit()
            .putString("entitlement_tier", "free")
            .remove("entitlement_source")
            .apply()
    }

    suspend fun reconcileWhenPlayHasNoActivePurchase() {
        val currentSource = prefs.getString("entitlement_source", null)
        if (currentSource == "sandbox" && (BuildConfig.DEBUG || _isDeveloperMode.value)) {
            return
        }
        if (isProTier1Entitled(_entitlementTier.value)) {
            val serverTier = fetchServerEntitlementStatus()
            if (!isProTier1Entitled(serverTier) && currentSource == "google_play") {
                downgradeToFreeTier()
            }
        }
    }

    suspend fun fetchServerEntitlementStatus(): String = withContext(Dispatchers.IO) {
        if (isRobolectricJvm()) return@withContext _entitlementTier.value
        try {
            val baseUrl = _hacsConfig.value.tuesdayApiBaseUrl.ifBlank { GeminiDiscoveryService.DEFAULT_SERVER_BASE_URL }.trimEnd('/')
            val req = Request.Builder()
                .url("$baseUrl/api/v1/billing/status")
                .addHeader("X-Tuesday-Device-ID", clientDeviceId)
                .addHeader("X-Tuesday-Client-Platform", "android")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    val root = JSONObject(bodyStr)
                    val serverTier = root.optString("entitlementTier", "")
                    val serverSku = root.optString("activeSubscriptionSku", "")
                    if (serverSku.isNotBlank()) {
                        _activeSubscriptionSku.value = serverSku
                        prefs.edit().putString("active_subscription_sku", serverSku).apply()
                    }
                    if (serverTier.isNotBlank()) {
                        _entitlementTier.value = serverTier
                        prefs.edit().putString("entitlement_tier", serverTier).apply()
                        return@withContext serverTier
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("TuesdayRepoSync", "Billing status check failed: ${e.message}")
        }
        _entitlementTier.value
    }

    private fun syncDeviceCreateToServerAsync(dev: RackPortDevice, meta: DiscoveredDeviceMetadata?) {
        if (isRobolectricJvm()) return
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
        if (isRobolectricJvm()) return
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
        if (isRobolectricJvm()) return
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
        includeAllSites: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        if (isRobolectricJvm()) return@withContext 0
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
                            candidateModels = if (isGuess) listOf(hostname) else emptyList()
                        )
                    )
                }
            }

            if (forceResend && syncedDevicesFromNotifs.isNotEmpty() && _activeNetworkId.value != "net-demo") {
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
        _rackDevices.value = BulletinsAndInventoryEngine.defaultRackPortDevices()
        _mutedFeedSlugs.value = setOf("lg-esphome-iot")
        _includeOkDevices.value = false
        _selectedZoneFilter.value = null
        _selectedPortStateFilter.value = null
        _activeDeviceCount.value = 0
        _bulletinDismissed.value = false
        _bulletinSnoozed.value = false
        _bulletinMuted.value = false
        _remindersEnabled.value = false
        _dispatchSubscribed.value = false
        _showStateFilterStrip.value = false
        _showZoneFilterStrip.value = false
        _isDeveloperMode.value = false
        _isVisionDebugEnabled.value = false
        _isDiagnosticsEnabled.value = false
        _entitlementTier.value = "free"
        _userNetworks.value = defaultUserNetworks()
        _hacsConfig.value = HacsConfig()
        selectNetwork("net-default")
        prefs.edit()
            .putBoolean("bulletin_dismissed", false)
            .putBoolean("bulletin_snoozed", false)
            .putBoolean("bulletin_muted", false)
            .putBoolean("monthly_reminders_enabled", false)
            .putBoolean("dispatch_subscribed", false)
            .putBoolean("pref_show_state_filter_strip", false)
            .putBoolean("pref_show_zone_filter_strip", false)
            .putBoolean("is_developer_mode", false)
            .putBoolean("is_vision_debug_enabled", false)
            .putBoolean("is_diagnostics_enabled", false)
            .putString("entitlement_tier", "free")
            .apply()
        persistConfig()
    }

    fun exportInventoryBackupJson(): String {
        val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

        val backup = NetworkInventoryBackup(
            version = "1.0.0",
            exportedAt = nowIso,
            activeNetworkId = _activeNetworkId.value,
            networks = _userNetworks.value,
            devices = _rackDevices.value,
            mutedFeedSlugs = _mutedFeedSlugs.value.toList(),
            attestations = _attestations.value
        )
        return json.encodeToString(backup)
    }

    fun importInventoryBackupJson(rawJson: String): Boolean {
        return try {
            val backup = json.decodeFromString<NetworkInventoryBackup>(rawJson)
            if (backup.devices.isNotEmpty() || backup.networks.isNotEmpty()) {
                if (backup.networks.isNotEmpty()) {
                    _userNetworks.value = backup.networks
                }
                if (backup.activeNetworkId.isNotBlank()) {
                    _activeNetworkId.value = backup.activeNetworkId
                }
                if (backup.devices.isNotEmpty()) {
                    _rackDevices.value = backup.devices
                    _activeDeviceCount.value = backup.devices.count { !it.isRetired && it.presenceState == DevicePresenceState.ACTIVE }
                }
                if (backup.mutedFeedSlugs.isNotEmpty()) {
                    _mutedFeedSlugs.value = backup.mutedFeedSlugs.toSet()
                }
                if (backup.attestations.isNotEmpty()) {
                    _attestations.value = backup.attestations
                }
                persistNetworks()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
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
        _rackDevices.value = BulletinsAndInventoryEngine.defaultRackPortDevices()
        _attestations.value = initialDefaultAttestations()
        _activeDeviceCount.value = 12
    }

    companion object {
        internal fun isRobolectricJvm(): Boolean =
            android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

        fun defaultUserNetworks(): List<NetworkSite> = listOf(
            NetworkSite(id = "net-default", name = "My Network", devicesCount = 0, role = "Primary Site")
        )
    }
}
