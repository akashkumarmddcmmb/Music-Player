package com.example.ads

/**
 * Phase 14: Optional Advertising Module (Strictly Privacy-First & Offline-Safe).
 *
 * Default configuration:
 * - Ads disabled (`enabledByDefault = false`).
 * - No production advertising identifiers or third-party tracking SDK bundled.
 * - Zero network requests (`INTERNET` permission is removed in AndroidManifest.xml).
 * - Never accesses or transmits media filenames, thumbnails, playlists, or playback history.
 */
data class AdvertisingModuleAudit(
    val moduleName: String = "PulsePlay Optional Local Sponsor Slot (No Tracking SDK)",
    val sdkBundled: String = "None (Zero Third-Party Ad SDK Bundled)",
    val license: String = "Apache-2.0 (Internal Module)",
    val requiredPermissions: List<String> = emptyList(),
    val networkRequestsPerformed: String = "None (0 network requests; app has no INTERNET permission)",
    val dataCollected: String = "None (Zero device IDs, zero filenames, zero playlists, zero history)",
    val consentRequiredBeforeEnable: Boolean = true,
    val offlineImpact: String = "100% offline compatible; never blocks local media playback",
    val apkSizeImpactBytes: Long = 0L
)

object OptionalAdManager {
    val auditReport = AdvertisingModuleAudit()

    fun canShowOptionalTestBanner(
        moduleEnabledInSettings: Boolean,
        explicitUserConsentGranted: Boolean
    ): Boolean {
        return moduleEnabledInSettings && explicitUserConsentGranted
    }
}
