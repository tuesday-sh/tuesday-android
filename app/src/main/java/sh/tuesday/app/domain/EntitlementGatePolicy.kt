package sh.tuesday.app.domain

import sh.tuesday.app.models.NetworkSite

object EntitlementGatePolicy {
    const val DEMO_NETWORK_ID = "net-demo"
    const val FREE_DEVICE_LIMIT = 15
    const val PRO_DEVICE_LIMIT = 128

    fun isProTier1Entitled(tier: String?): Boolean {
        val normalized = tier?.trim()?.lowercase().orEmpty()
        return normalized == "pro_tier_1" ||
            normalized == "multisite_tier_1" ||
            normalized == "fleet"
    }

    fun maxDevicesForTier(tier: String?): Int =
        if (isProTier1Entitled(tier)) PRO_DEVICE_LIMIT else FREE_DEVICE_LIMIT

    fun canAddDevice(
        tier: String?,
        activeNetworkId: String?,
        currentDeviceCount: Int
    ): Boolean {
        if (activeNetworkId == DEMO_NETWORK_ID) return true
        return currentDeviceCount < maxDevicesForTier(tier)
    }

    fun requiresUpgradeForNewDevice(
        tier: String?,
        activeNetworkId: String?,
        currentDeviceCount: Int
    ): Boolean = !isProTier1Entitled(tier) && !canAddDevice(tier, activeNetworkId, currentDeviceCount)

    fun realNetworkCount(networks: List<NetworkSite>): Int =
        networks.count { it.id != DEMO_NETWORK_ID }

    fun canCreateAdditionalNetwork(tier: String?, networks: List<NetworkSite>): Boolean =
        isProTier1Entitled(tier) || realNetworkCount(networks) < 1

    fun requiresUpgradeForNewSite(tier: String?, networks: List<NetworkSite>): Boolean =
        !canCreateAdditionalNetwork(tier, networks)
}
