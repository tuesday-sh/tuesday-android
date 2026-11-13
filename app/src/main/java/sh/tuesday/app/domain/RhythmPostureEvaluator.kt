package sh.tuesday.app.domain

import sh.tuesday.app.models.AppBrandFlavor
import sh.tuesday.app.models.BulletinInboxView
import sh.tuesday.app.models.PortLifecycleState
import sh.tuesday.app.models.RackPortDevice

data class RhythmPostureItem(
    val id: String,
    val title: String,
    val action: String?,
    val recommendation: String?
)

object RhythmPostureEvaluator {
    fun hasActiveRecommendation(
        devices: List<RackPortDevice>,
        welcomeComplete: Boolean = false
    ): Boolean {
        if (!welcomeComplete) return true
        return devices.any {
            it.portState == PortLifecycleState.CVE_KEV ||
                it.portState == PortLifecycleState.EOL ||
                it.portState == PortLifecycleState.GUESSED
        }
    }

    fun evaluatePosture(
        flavor: AppBrandFlavor,
        devices: List<RackPortDevice>,
        bulletins: List<BulletinInboxView> = emptyList(),
        welcomeComplete: Boolean = false
    ): List<RhythmPostureItem> {
        val isStn = flavor == AppBrandFlavor.SURETHING_HOME
        val isDayZero = devices.isEmpty()

        val provisionalDevices = devices.filter { it.portState == PortLifecycleState.GUESSED }
        val cveDevices = devices.filter { it.portState == PortLifecycleState.CVE_KEV }
        val eolDevices = devices.filter { it.portState == PortLifecycleState.EOL }

        val recallBulletins = bulletins.filter { it.bulletinType.equals("RECALL", ignoreCase = true) }
        val patchBulletins = bulletins.filter {
            it.bulletinType.equals("CVE", ignoreCase = true) ||
                it.bulletinType.equals("CISA_KEV", ignoreCase = true) ||
                it.bulletinType.equals("ADVISORY", ignoreCase = true)
        }

        val r1Action = if (isDayZero || provisionalDevices.isEmpty()) null else {
            val count = provisionalDevices.size
            val labels = provisionalDevices.joinToString(", ") { it.name }
            "$count provisional device${if (count == 1) "" else "s"} to verify ($labels)"
        }
        val r1Rec = if (r1Action != null) {
            "Confirm exact hardware model on Bulletins tab or scan barcode."
        } else null

        val r2Action = when {
            !welcomeComplete -> if (isStn) {
                "Welcome Bulletin: 3 preparation steps for upcoming Patch Tuesday (2nd Tuesday of the month)."
            } else {
                "Welcome Bulletin: Complete 3-step setup before Patch Tuesday (2nd Tuesday of the month)."
            }
            patchBulletins.isNotEmpty() -> {
                val count = patchBulletins.size
                val titles = patchBulletins.joinToString(" • ") { it.title }
                "$count active Patch Tuesday / security bulletin${if (count == 1) "" else "s"}: $titles"
            }
            else -> null
        }
        val r2Rec = when {
            !welcomeComplete -> if (isStn) {
                "Review Welcome Bulletin steps: [1 Add Router, 2 Turn On Notifications, 3 Subscribe]."
            } else {
                "Check Welcome Bulletin: [1 Add Router, 2 Enable 10:00 AM PT Push, 3 Subscribe Dispatch]."
            }
            patchBulletins.isNotEmpty() -> "Review affected devices and apply vendor firmware updates on the Bulletins tab."
            else -> null
        }

        val r3Parts = buildList {
            if (recallBulletins.isNotEmpty()) {
                val count = recallBulletins.size
                add("$count Product Safety Recall${if (count == 1) "" else "s"} (${recallBulletins.joinToString(", ") { it.title }})")
            }
            if (cveDevices.isNotEmpty()) {
                val count = cveDevices.size
                add("$count CVE-affected device${if (count == 1) "" else "s"} (${cveDevices.joinToString(", ") { it.name }})")
            }
        }
        val r3Action = if (isDayZero || r3Parts.isEmpty()) null else r3Parts.joinToString(" • ")
        val r3Rec = if (r3Action != null) {
            if (isStn) {
                "Verify your Wi-Fi router admin password is not the factory default (\"admin\") and resolve active recalls."
            } else {
                "Check router admin password, disable WAN management, and resolve active recalls."
            }
        } else null

        val r4Action = if (isDayZero || eolDevices.isEmpty()) null else {
            val count = eolDevices.size
            val labels = eolDevices.joinToString(", ") { it.name }
            "$count EOL device${if (count == 1) "" else "s"} past vendor support ($labels)"
        }
        val r4Rec = if (r4Action != null) {
            if (isStn) {
                "Consider upgrading or unplugging unsupported hardware."
            } else {
                "Isolate EOL hardware to Guest VLAN or replace with supported gear."
            }
        } else null

        return listOf(
            RhythmPostureItem(
                id = "r1",
                title = if (isStn) "R1 • New Devices & Recognition" else "R1 • NEW HARDWARE INTAKE & ATTESTATION",
                action = r1Action,
                recommendation = r1Rec
            ),
            RhythmPostureItem(
                id = "r2",
                title = if (isStn) "R2 • Monthly Patch Tuesday (2nd Tuesday)" else "R2 • MONTHLY PATCH TUESDAY READINESS",
                action = r2Action,
                recommendation = r2Rec
            ),
            RhythmPostureItem(
                id = "r3",
                title = if (isStn) "R3 • Quarterly Safety Check (Jan / Apr / Jul / Oct)" else "R3 • SEASONAL SAFETY, PASSWORDS & DRIFT AUDIT",
                action = r3Action,
                recommendation = r3Rec
            ),
            RhythmPostureItem(
                id = "r4",
                title = if (isStn) "R4 • Annual Hardware Lifespan (January)" else "R4 • YEARLY HARDWARE LIFESPAN & EOL",
                action = r4Action,
                recommendation = r4Rec
            )
        )
    }

    fun formatShareReport(
        flavor: AppBrandFlavor,
        items: List<RhythmPostureItem>
    ): String {
        val isStn = flavor == AppBrandFlavor.SURETHING_HOME
        val header = if (isStn) {
            "🛡️ SureThingNet Home Safety & Software Check-Up\nCadence: 4-Rhythm Home Protection Check-Up"
        } else {
            "🛡️ Tuesday.sh (STN-Pro) Fleet Check-Up\nCadence: 4-Rhythm Continuous Operational Posture"
        }
        val body = items.mapIndexed { idx, r ->
            val status = if (r.action != null) "⚠️ Action Needed" else "✓ Everything OK"
            val summary = r.action ?: "Everything OK"
            val recLine = if (r.recommendation != null) "\n   💡 Recommendation: ${r.recommendation}" else ""
            "${idx + 1}. [${r.title}] — $status\n   Status: $summary$recLine"
        }.joinToString("\n\n")
        return "$header\n\n$body"
    }
}
