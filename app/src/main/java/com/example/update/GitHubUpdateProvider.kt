package com.example.update

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Common abstraction for an update distribution channel so new channels (e.g., F-Droid, enterprise
 * MDM, or self-hosted feeds) can be added without modifying the player, studio editor, database,
 * or other unrelated modules.
 */
interface UpdateChannelProvider {
    val channel: UpdateDistributionChannel
}

/**
 * GitHub Releases update provider for PulsePlay.
 *
 * Responsibilities:
 * - Fetches release metadata over HTTPS from the configured official GitHub repository (`owner/repo`).
 * - Compares `versionCode` and semantic `versionName` monotonically.
 * - Downloads the compatible APK asset with progress and cancellation support.
 * - Verifies SHA-256 checksum (`SHA256SUMS.txt` or release body), package identity (`applicationId`),
 *   non-downgrade `versionCode`, and signing certificate compatibility via [UpdateSourcePolicy].
 * - Launches Android's supported system package installer via `FileProvider` with user consent.
 */
class GitHubUpdateProvider(
    context: Context,
    private val appUpdateManager: AppUpdateManager = AppUpdateManager(context),
    private val sourcePolicy: UpdateSourcePolicy = UpdateSourcePolicy(context),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : UpdateChannelProvider {

    override val channel: UpdateDistributionChannel = UpdateDistributionChannel.GITHUB_RELEASES

    val state: StateFlow<UpdateCheckState> = appUpdateManager.updateState

    fun getInstalledVersionName(): String = appUpdateManager.getInstalledVersionName()

    fun getInstalledVersionCode(): Long = appUpdateManager.getInstalledVersionCode()

    suspend fun checkForUpdates(
        configuredRepositorySlug: String,
        includePrereleases: Boolean = false,
        overrideSourceEvaluation: UpdateSourceEvaluation? = null
    ): UpdateCheckState {
        val evaluation = overrideSourceEvaluation ?: sourcePolicy.evaluate()
        if (evaluation.blockGitHubApkOverwriteOfPlayInstall ||
            evaluation.detectedChannel == InstallChannel.GOOGLE_PLAY
        ) {
            return UpdateCheckState.VerificationFailed(
                releaseInfo = null,
                reason = "Blocked by UpdateSourcePolicy: PulsePlay was installed from Google Play Store. " +
                    "Use Google Play In-App Updates instead of replacing a Play-installed app with a GitHub APK."
            )
        }
        return appUpdateManager.checkForUpdates(
            configuredRepositorySlug = configuredRepositorySlug,
            includePrereleases = includePrereleases
        )
    }

    suspend fun downloadAndVerifyUpdate(
        releaseInfo: GitHubReleaseInfo,
        overrideSourceEvaluation: UpdateSourceEvaluation? = null
    ): UpdateCheckState {
        val evaluation = overrideSourceEvaluation ?: sourcePolicy.evaluate()
        if (evaluation.blockGitHubApkOverwriteOfPlayInstall ||
            evaluation.detectedChannel == InstallChannel.GOOGLE_PLAY
        ) {
            return UpdateCheckState.VerificationFailed(
                releaseInfo = releaseInfo,
                reason = "Blocked by UpdateSourcePolicy: PulsePlay was installed from Google Play Store. " +
                    "A GitHub APK update cannot replace a Google Play installation."
            )
        }
        return appUpdateManager.downloadAndVerifyUpdate(releaseInfo)
    }

    fun verifyDownloadedApk(
        apkFile: File,
        expectedSha256Hex: String?,
        overrideSourceEvaluation: UpdateSourceEvaluation? = null,
        overrideArchiveCertSha256Set: Set<String>? = null
    ): ApkVerificationResult {
        return appUpdateManager.verifyDownloadedApk(
            apkFile = apkFile,
            expectedSha256Hex = expectedSha256Hex,
            overrideSourceEvaluation = overrideSourceEvaluation,
            overrideArchiveCertSha256Set = overrideArchiveCertSha256Set
        )
    }

    fun cancelActiveDownload() {
        appUpdateManager.cancelActiveDownload()
    }

    fun dismissTemporaryStateIfMatching(expectedState: UpdateCheckState? = null) {
        appUpdateManager.dismissTemporaryStateIfMatching(expectedState)
    }

    fun triggerInstallOrPermissionFlow(
        overrideSourceEvaluation: UpdateSourceEvaluation? = null
    ): Boolean {
        return appUpdateManager.triggerInstallOrPermissionFlow(
            overrideSourceEvaluation = overrideSourceEvaluation
        )
    }
}
