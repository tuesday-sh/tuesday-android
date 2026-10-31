package sh.tuesday.app.services

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.tuesday.app.models.*

class TuesdayRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tuesday_prefs_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _devices = MutableStateFlow<List<MonitoredDevice>>(emptyList())
    val devices: StateFlow<List<MonitoredDevice>> = _devices.asStateFlow()

    private val _hacsConfig = MutableStateFlow(HacsConfig())
    val hacsConfig: StateFlow<HacsConfig> = _hacsConfig.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        loadConfig()
        loadDevices()
    }

    private fun loadConfig() {
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
                if (list.isNotEmpty()) {
                    _devices.value = list
                    return
                }
            } catch (_: Exception) {}
        }
        _devices.value = defaultSeedDevices()
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
        persistDevices()
    }

    fun resetDemoData() {
        _devices.value = defaultSeedDevices()
        _hacsConfig.value = HacsConfig()
        persistDevices()
        persistConfig()
    }

    companion object {
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
