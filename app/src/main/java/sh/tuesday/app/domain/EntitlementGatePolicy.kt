package sh.tuesday.app.domain

import sh.tuesday.app.models.NetworkSite

object EntitlementGatePolicy {
    const val DEMO_NETWORK_ID = "net-demo"

    fun isProTier1Entitled(tier: String?): Boolean {
        val normalized = tier?.trim()?.lowercase().orEmpty()
        return normalized == "pro_tier_1" ||
            normalized == "multisite_tier_1" ||
            normalized == "fleet"
    }

    fun realNetworkCount(networks: List<NetworkSite>): Int =
        networks.count { it.id != DEMO_NETWORK_ID }

    fun canCreateAdditionalNetwork(tier: String?, networks: List<NetworkSite>): Boolean =
        isProTier1Entitled(tier) || realNetworkCount(networks) < 1

    fun requiresUpgradeForNewSite(tier: String?, networks: List<NetworkSite>): Boolean =
        !canCreateAdditionalNetwork(tier, networks)
}
