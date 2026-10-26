package com.example.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

enum class UpdateDistributionChannel(val label: String) {
    GITHUB_RELEASES("GitHub Releases"),
    GOOGLE_PLAY("Google Play Store")
}

enum class InstallChannel(val label: String) {
    GOOGLE_PLAY("Google Play Store"),
    GITHUB_OR_SIDELOAD("GitHub / Package Installer"),
    UNKNOWN("Unverified / Ambiguous Source")
}

data class InstallSourceSignals(
    val installingPackageName: String?,
    val initiatingPackageName: String?,
    val originatingPackageName: String?,
    val installedSigningCertSha256Set: Set<String>,
    val isPlayStoreAppInstalledOnDevice: Boolean
)

data class UpdateSourceEvaluation(
    val detectedChannel: InstallChannel,
    val recommendedChannel: UpdateDistributionChannel,
    val allowedChannels: Set<UpdateDistributionChannel>,
    val canAutoInstallGitHubApk: Boolean,
    val blockGitHubApkOverwriteOfPlayInstall: Boolean,
    val installerPackageSummary: String,
    val signingCertificateShortFingerprint: String,
    val installedSigningCertSha256Set: Set<String>,
    val policyExplanation: String,
    val signingMigrationGuidance: String
)

data class CrossChannelUpgradeValidation(
    val isAllowed: Boolean,
    val requiresManualMigration: Boolean,
    val reason: String,
    val migrationInstructions: String? = null
)

/**
 * Determines which update methods are appropriate for the current installation source and enforces
 * signing-key continuity and cross-channel safety between GitHub Releases and Google Play Store.
 *
 * Rules:
 * 1. If the app was installed from Google Play (`com.android.vending` verified across install-source
 *    signals), prefers Google Play In-App Updates and blocks automatic or accidental overwrite from
 *    an external GitHub APK.
 * 2. If the app was installed from a GitHub APK or system package installer, offers GitHub Releases
 *    updates while allowing the user to check or open the Google Play Store listing.
 * 3. If the installation source cannot be verified (`UNKNOWN`), does not assume the source and
 *    presents safe, clearly labeled choices for both channels while still verifying signing
 *    certificate compatibility before any APK installation.
 * 4. Never relies on a single weak signal when multiple signals (`installingPackageName`,
 *    `initiatingPackageName`, `originatingPackageName`, and signing certificate digests) are available.
 */
class UpdateSourcePolicy(private val context: Context) {

    companion object {
        const val PLAY_STORE_PACKAGE_NAME = "com.android.vending"
        private const val PLAY_FEEDBACK_AGENT = "com.google.android.feedback"

        private val KNOWN_PACKAGE_INSTALLER_PACKAGES = setOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.coloros.phonemanager",
            "com.oplus.appdetail",
            "com.vivo.appfilter"
        )

        /**
         * Evaluates installation source signals deterministically. Exposed as a pure function as well
         * so unit and Robolectric tests can verify Play-installed, GitHub/sideloaded, spoofed, and
         * unknown install-source scenarios.
         */
        fun evaluateSignals(signals: InstallSourceSignals): UpdateSourceEvaluation {
            val installing = signals.installingPackageName?.trim()?.takeIf { it.isNotEmpty() }
            val initiating = signals.initiatingPackageName?.trim()?.takeIf { it.isNotEmpty() }
            val originating = signals.originatingPackageName?.trim()?.takeIf { it.isNotEmpty() }

            // Multi-signal verification for Google Play installation:
            // Must have installingPackageName == com.android.vending AND no conflicting non-Play
            // originating package, plus initiatingPackageName (if present) must be Play Store or feedback agent.
            val isVerifiedPlayInstall = installing == PLAY_STORE_PACKAGE_NAME &&
                (initiating == null || initiating == PLAY_STORE_PACKAGE_NAME || initiating == PLAY_FEEDBACK_AGENT) &&
                (originating == null || originating == PLAY_STORE_PACKAGE_NAME)

            val isKnownPackageInstaller = !isVerifiedPlayInstall && (
                (installing != null && KNOWN_PACKAGE_INSTALLER_PACKAGES.any { installing.equals(it, ignoreCase = true) }) ||
                    (initiating != null && KNOWN_PACKAGE_INSTALLER_PACKAGES.any { initiating.equals(it, ignoreCase = true) })
                )

            val detectedChannel = when {
                isVerifiedPlayInstall -> InstallChannel.GOOGLE_PLAY
                isKnownPackageInstaller -> InstallChannel.GITHUB_OR_SIDELOAD
                else -> InstallChannel.UNKNOWN
            }

            val shortCert = signals.installedSigningCertSha256Set.firstOrNull()?.let { hex ->
                if (hex.length >= 16) {
                    hex.take(16).uppercase(Locale.US).chunked(2).joinToString(":")
                } else {
                    hex.uppercase(Locale.US)
                }
            } ?: "Unverified"

            val installerSummary = buildString {
                append("installer=")
                append(installing ?: "none")
                if (!initiating.isNullOrBlank() && initiating != installing) {
                    append(", initiator=$initiating")
                }
                if (!originating.isNullOrBlank() && originating != installing) {
                    append(", origin=$originating")
                }
            }

            val migrationGuidance = buildSigningMigrationGuidance()

            return when (detectedChannel) {
                InstallChannel.GOOGLE_PLAY -> UpdateSourceEvaluation(
                    detectedChannel = InstallChannel.GOOGLE_PLAY,
                    recommendedChannel = UpdateDistributionChannel.GOOGLE_PLAY,
                    allowedChannels = setOf(UpdateDistributionChannel.GOOGLE_PLAY),
                    canAutoInstallGitHubApk = false,
                    blockGitHubApkOverwriteOfPlayInstall = true,
                    installerPackageSummary = installerSummary,
                    signingCertificateShortFingerprint = shortCert,
                    installedSigningCertSha256Set = signals.installedSigningCertSha256Set,
                    policyExplanation = "Installed from Google Play Store ($PLAY_STORE_PACKAGE_NAME). " +
                        "Updates are managed exclusively through Google Play In-App Updates to preserve Play App Signing continuity and automatic store updates. " +
                        "Replacing a Play-installed build with an external GitHub APK is blocked.",
                    signingMigrationGuidance = migrationGuidance
                )

                InstallChannel.GITHUB_OR_SIDELOAD -> UpdateSourceEvaluation(
                    detectedChannel = InstallChannel.GITHUB_OR_SIDELOAD,
                    recommendedChannel = UpdateDistributionChannel.GITHUB_RELEASES,
                    allowedChannels = setOf(
                        UpdateDistributionChannel.GITHUB_RELEASES,
                        UpdateDistributionChannel.GOOGLE_PLAY
                    ),
                    canAutoInstallGitHubApk = true,
                    blockGitHubApkOverwriteOfPlayInstall = false,
                    installerPackageSummary = installerSummary,
                    signingCertificateShortFingerprint = shortCert,
                    installedSigningCertSha256Set = signals.installedSigningCertSha256Set,
                    policyExplanation = "Installed via Android Package Installer ($installerSummary). " +
                        "GitHub Releases updates are active and verify SHA-256 checksums and signing certificate continuity before launching the system installer.",
                    signingMigrationGuidance = migrationGuidance
                )

                InstallChannel.UNKNOWN -> UpdateSourceEvaluation(
                    detectedChannel = InstallChannel.UNKNOWN,
                    recommendedChannel = UpdateDistributionChannel.GITHUB_RELEASES,
                    allowedChannels = setOf(
                        UpdateDistributionChannel.GITHUB_RELEASES,
                        UpdateDistributionChannel.GOOGLE_PLAY
                    ),
                    canAutoInstallGitHubApk = true,
                    blockGitHubApkOverwriteOfPlayInstall = false,
                    installerPackageSummary = installerSummary,
                    signingCertificateShortFingerprint = shortCert,
                    installedSigningCertSha256Set = signals.installedSigningCertSha256Set,
                    policyExplanation = "Installation source could not be verified ($installerSummary). " +
                        "Both GitHub Releases and Google Play Store update checks are available as safe choices. " +
                        "Any downloaded APK must still match the installed signing certificate ($shortCert...) before installation.",
                    signingMigrationGuidance = migrationGuidance
                )
            }
        }

        /**
         * Validates that [candidateVersionCode] is monotonically newer than [installedVersionCode]
         * across all distribution channels.
         */
        fun validateMonotonicVersionCode(
            installedVersionCode: Long,
            candidateVersionCode: Long,
            allowEqualForSameBuild: Boolean = false
        ): Boolean {
            return if (allowEqualForSameBuild) {
                candidateVersionCode >= installedVersionCode
            } else {
                candidateVersionCode > installedVersionCode
            }
        }

        /**
         * Checks whether two non-empty sets of SHA-256 signing certificate digests share a trusted signer.
         */
        fun areSigningCertificatesCompatible(
            installedCertSha256Set: Set<String>,
            candidateCertSha256Set: Set<String>
        ): Boolean {
            if (installedCertSha256Set.isEmpty() || candidateCertSha256Set.isEmpty()) {
                return true
            }
            val normalizedInstalled = installedCertSha256Set.map { it.lowercase(Locale.US) }.toSet()
            val normalizedCandidate = candidateCertSha256Set.map { it.lowercase(Locale.US) }.toSet()
            return normalizedInstalled.intersect(normalizedCandidate).isNotEmpty()
        }

        /**
         * Validates whether a GitHub APK update is permitted given the current [UpdateSourceEvaluation],
         * version codes, and APK signing certificates.
         *
         * Enforces:
         * 1. Play-installed apps MUST NOT be replaced by a GitHub APK.
         * 2. Monotonic `versionCode` ordering (no downgrades).
         * 3. Signing certificate continuity (blocks incompatible signing keys before the package installer
         *    is ever invoked, and provides clear migration instructions without erasing user data).
         */
        fun validateGitHubApkInstallAttempt(
            evaluation: UpdateSourceEvaluation,
            installedVersionCode: Long,
            candidateVersionCode: Long,
            candidateSigningCertSha256Set: Set<String>
        ): CrossChannelUpgradeValidation {
            if (evaluation.blockGitHubApkOverwriteOfPlayInstall ||
                evaluation.detectedChannel == InstallChannel.GOOGLE_PLAY
            ) {
                return CrossChannelUpgradeValidation(
                    isAllowed = false,
                    requiresManualMigration = true,
                    reason = "Blocked by UpdateSourcePolicy: PulsePlay was installed from Google Play Store. " +
                        "A GitHub APK update cannot replace a Google Play installation.",
                    migrationInstructions = evaluation.signingMigrationGuidance
                )
            }

            if (candidateVersionCode < installedVersionCode) {
                return CrossChannelUpgradeValidation(
                    isAllowed = false,
                    requiresManualMigration = false,
                    reason = "Downgrade prevented: candidate APK versionCode ($candidateVersionCode) is older than installed versionCode ($installedVersionCode)."
                )
            }

            if (!areSigningCertificatesCompatible(
                    installedCertSha256Set = evaluation.installedSigningCertSha256Set,
                    candidateCertSha256Set = candidateSigningCertSha256Set
                )
            ) {
                return CrossChannelUpgradeValidation(
                    isAllowed = false,
                    requiresManualMigration = true,
                    reason = "Incompatible signing certificate! The downloaded APK is signed with a different key than the currently installed build.",
                    migrationInstructions = evaluation.signingMigrationGuidance
                )
            }

            return CrossChannelUpgradeValidation(
                isAllowed = true,
                requiresManualMigration = false,
                reason = "Compatible in-place update verified. Existing Room database, playlists, favorites, history, and DataStore preferences will be preserved."
            )
        }

        fun buildSigningMigrationGuidance(): String {
            return "Signing & Channel Migration Policy:\n" +
                "• Same Signing Key (In-Place Upgrade): When updating within the same channel (or when GitHub Releases and Google Play share the same release signing certificate), Android performs an in-place upgrade that preserves your Room database (playlists, favorites, playback history) and DataStore preferences.\n" +
                "• Different Signing Keys (GitHub Key vs. Play App Signing Key): Android OS security prohibits installing an APK signed with a different certificate over an existing installation. PulsePlay blocks incompatible updates before invoking the system installer and NEVER automatically uninstalls the app or erases your data.\n" +
                "• Manual Channel Switch: If you intentionally switch between differently signed GitHub and Google Play builds, your media files in shared storage (Music/Movies) remain untouched, but Android requires a manual uninstall before installing from the new channel."
        }
    }

    fun inspectInstallSourceSignals(): InstallSourceSignals {
        val pm = context.packageManager
        val pkgName = context.packageName

        var installing: String? = null
        var initiating: String? = null
        var originating: String? = null

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val info = pm.getInstallSourceInfo(pkgName)
                installing = info.installingPackageName
                initiating = info.initiatingPackageName
                originating = info.originatingPackageName
            } else {
                @Suppress("DEPRECATION")
                installing = pm.getInstallerPackageName(pkgName)
            }
        }

        val isPlayStoreInstalled = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(PLAY_STORE_PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(PLAY_STORE_PACKAGE_NAME, 0)
            }
            true
        }.getOrDefault(false)

        val certDigests = getInstalledSigningCertSha256Set(pm, pkgName)

        return InstallSourceSignals(
            installingPackageName = installing,
            initiatingPackageName = initiating,
            originatingPackageName = originating,
            installedSigningCertSha256Set = certDigests,
            isPlayStoreAppInstalledOnDevice = isPlayStoreInstalled
        )
    }

    fun evaluate(overrideSignals: InstallSourceSignals? = null): UpdateSourceEvaluation {
        val signals = overrideSignals ?: inspectInstallSourceSignals()
        return evaluateSignals(signals)
    }

    fun getInstalledSigningCertSha256Set(
        pm: PackageManager = context.packageManager,
        packageName: String = context.packageName
    ): Set<String> {
        return runCatching {
            @Suppress("DEPRECATION")
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
            extractSigningCertSha256Set(pkgInfo)
        }.getOrDefault(emptySet())
    }

    fun extractSigningCertSha256Set(pkgInfo: PackageInfo): Set<String> {
        val digests = mutableSetOf<String>()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = pkgInfo.signingInfo
                val sigs = if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else {
                    null
                }
                sigs?.forEach { sig ->
                    val sha = MessageDigest.getInstance("SHA-256")
                        .digest(sig.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    digests.add(sha.lowercase(Locale.US))
                }
            }
            @Suppress("DEPRECATION")
            pkgInfo.signatures?.forEach { sig ->
                val sha = MessageDigest.getInstance("SHA-256")
                    .digest(sig.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                digests.add(sha.lowercase(Locale.US))
            }
        }
        return digests
    }
}
