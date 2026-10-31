package sh.tuesday.app

import org.junit.Assert.*
import org.junit.Test
import sh.tuesday.app.models.*
import sh.tuesday.app.services.GeminiDiscoveryService

class MonitoredDeviceTest {

    @Test
    fun testHealthyDeviceEvaluation() {
        val now = 1700000000000L
        val device = MonitoredDevice(
            hostname = "aaronpro18",
            osDetails = "macOS 15.7.9",
            packageManager = PackageManagerType.BREW,
            lastCheckInEpochMs = now - 10 * 60 * 1000,
            pendingUpdates = emptyList(),
            healthScore = 100.0,
            status = NodeHealthStatus.HEALTHY
        )

        assertEquals(NodeHealthStatus.HEALTHY, device.status)
        assertEquals(100.0, device.healthScore, 0.01)
        assertEquals("sensor.tuesday_aaronpro18", device.haEntityId)
    }

    @Test
    fun testWarningDeviceEvaluationWithOutdatedPackages() {
        val now = 1700000000000L
        val updates = listOf(
            PackageUpdate(name = "esphome", currentVersion = "2026.9.1", availableVersion = "2026.10.0")
        )
        val score = MonitoredDevice.calculateHealthScore(updates, now - 5 * 60 * 1000, now)
        val status = MonitoredDevice.evaluateStatus(score, updates, now - 5 * 60 * 1000, now)

        assertEquals(NodeHealthStatus.WARNING, status)
        assertEquals(92.0, score, 0.01)
    }

    @Test
    fun testCriticalDeviceEvaluationWithSecurityCriticalUpdate() {
        val now = 1700000000000L
        val updates = listOf(
            PackageUpdate(
                name = "openssl",
                currentVersion = "3.0.13-1",
                availableVersion = "3.0.13-4",
                isSecurityCritical = true
            )
        )
        val score = MonitoredDevice.calculateHealthScore(updates, now, now)
        val status = MonitoredDevice.evaluateStatus(score, updates, now, now)

        assertEquals(NodeHealthStatus.CRITICAL, status)
        assertEquals(70.0, score, 0.01)
    }

    @Test
    fun testProgressiveGeminiDiscoveryHeuristicNarrowing() {
        val initialIdentifiers = ExtractedDeviceIdentifiers()
        val initialResult = GeminiDiscoveryService.fallbackHeuristic(
            accumulatedIdentifiers = initialIdentifiers,
            userClueNotes = emptyList(),
            imageCount = 1,
            usedLiveApi = false
        )

        assertFalse(initialResult.candidates.isEmpty())
        assertTrue(initialResult.nextNarrowingQuestion.contains("Barcode") || initialResult.nextNarrowingQuestion.contains("MAC"))

        // Add Model & MAC clue
        initialIdentifiers.modelNumber = "UCG-Ultra"
        initialIdentifiers.macAddress = "74:83:C2:9E:11:04"

        val refinedResult = GeminiDiscoveryService.fallbackHeuristic(
            accumulatedIdentifiers = initialIdentifiers,
            userClueNotes = listOf("Model=UCG-Ultra"),
            imageCount = 1,
            usedLiveApi = false
        )

        val topMatch = refinedResult.candidates.first()
        assertTrue(topMatch.confidencePercent >= 90)
        assertEquals("UCG-Ultra", topMatch.modelNumber)
    }
}
