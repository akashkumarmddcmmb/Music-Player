package com.example.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class ReleaseAssetInfo(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val contentType: String
)

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val remoteVersionCode: Long?,
    val releaseTitle: String,
    val releaseNotes: String,
    val publishedAt: String,
    val htmlUrl: String,
    val isPrerelease: Boolean,
    val apkAsset: ReleaseAssetInfo,
    val checksumAsset: ReleaseAssetInfo?,
    val expectedSha256: String?
)

sealed class UpdateCheckState {
    companion object {
        private val emissionCounter = java.util.concurrent.atomic.AtomicLong(1L)
        fun nextEmissionId(): Long = emissionCounter.getAndIncrement()
    }

    data object Idle : UpdateCheckState()
    data class ConfigurationRequired(
        val message: String,
        val emissionId: Long = nextEmissionId()
    ) : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data class UpToDate(
        val installedVersionName: String,
        val installedVersionCode: Long,
        val latestRemoteTag: String? = null,
        val emissionId: Long = nextEmissionId()
    ) : UpdateCheckState()

    data class UpdateAvailable(
        val releaseInfo: GitHubReleaseInfo,
        val installedVersionName: String,
        val installedVersionCode: Long
    ) : UpdateCheckState()

    data class Downloading(
        val releaseInfo: GitHubReleaseInfo,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progressPercent: Int
    ) : UpdateCheckState()

    data class Verifying(val releaseInfo: GitHubReleaseInfo) : UpdateCheckState()

    data class ReadyToInstall(
        val releaseInfo: GitHubReleaseInfo,
        val apkFile: File,
        val verifiedSha256: String,
        val archivePackageName: String,
        val archiveVersionName: String,
        val archiveVersionCode: Long
    ) : UpdateCheckState()

    data class InstallationPending(
        val releaseInfo: GitHubReleaseInfo,
        val apkFile: File,
        val verifiedSha256: String,
        val requiresUnknownSourcesPermission: Boolean,
        val statusMessage: String
    ) : UpdateCheckState()

    data class VerificationFailed(
        val releaseInfo: GitHubReleaseInfo?,
        val reason: String
    ) : UpdateCheckState()

    data class DownloadFailed(
        val releaseInfo: GitHubReleaseInfo?,
        val reason: String
    ) : UpdateCheckState()

    data class NetworkError(val reason: String) : UpdateCheckState()

    /**
     * Returns true if this state represents a temporary, non-critical status or configuration
     * warning that should auto-dismiss after ~3.5 seconds, or false if it represents an ongoing
     * operation, actionable update, or persistent error/security failure.
     */
    val isTemporaryNotificationState: Boolean
        get() = this is ConfigurationRequired || this is UpToDate
}

data class ApkVerificationResult(
    val isValid: Boolean,
    val sha256Hex: String,
    val archivePackageName: String = "",
    val archiveVersionName: String = "",
    val archiveVersionCode: Long = 0L,
    val errorReason: String? = null
)

/**
 * Production-grade GitHub Releases in-app update manager for PulsePlay.
 *
 * Responsibilities:
 * - Queries the configured GitHub repository's Releases API over HTTPS.
 * - Does not invent a fake repository owner or name: if neither [DEFAULT_GITHUB_REPOSITORY]
 *   nor the user-configured repository slug in Settings is set, reports [UpdateCheckState.ConfigurationRequired].
 * - Compares semantic versions (`major.minor.patch`) and `versionCode` metadata cleanly.
 * - Streams APK downloads to `cacheDir/updates` with real-time progress and cancellation support.
 * - Verifies SHA-256 checksum (from `SHA256SUMS.txt` asset or release notes) and validates the
 *   downloaded APK archive's package name, version code, and signing certificate continuity
 *   before launching the system package installer via [FileProvider].
 */
class AppUpdateManager(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        /**
         * Authoritative default GitHub repository slug (`owner/repo`).
         * Populated at build time from the `GITHUB_REPOSITORY` environment variable / Gradle config,
         * or left empty so the user can supply their exact `owner/repo` in Settings -> App Updates.
         */
        val DEFAULT_GITHUB_REPOSITORY: String = BuildConfig.GITHUB_REPOSITORY.trim()

        private const val GITHUB_API_BASE = "https://api.github.com/repos"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val UPDATES_CACHE_DIR = "updates"

        private val REPO_SLUG_REGEX = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
        private val SHA256_HEX_REGEX = Regex("\\b([a-fA-F0-9]{64})\\b")
        private val VERSION_CODE_PATTERN = Regex(
            "(?:versionCode|version_code|build_number)\\s*[:=]\\s*(\\d+)",
            RegexOption.IGNORE_CASE
        )

        fun isValidRepositorySlug(slug: String): Boolean {
            val trimmed = slug.trim().removePrefix("https://github.com/").removeSuffix(".git").trim('/')
            return REPO_SLUG_REGEX.matches(trimmed)
        }

        fun normalizeRepositorySlug(slug: String): String {
            return slug.trim()
                .removePrefix("https://github.com/")
                .removePrefix("http://github.com/")
                .removeSuffix(".git")
                .trim('/')
        }

        fun normalizeVersionString(rawTagOrVersion: String): String {
            return rawTagOrVersion.trim()
                .removePrefix("v")
                .removePrefix("V")
                .removePrefix("release-")
                .trim()
        }

        /**
         * Compares two semantic version strings (e.g., "1.2.0" vs "1.0.0", "1.0.1-rc1" vs "1.0.1").
         * Returns > 0 if [remoteVersion] is newer than [installedVersion], 0 if equal, < 0 if older.
         */
        fun compareSemanticVersions(remoteVersion: String, installedVersion: String): Int {
            val remoteClean = normalizeVersionString(remoteVersion)
            val installedClean = normalizeVersionString(installedVersion)

            val remoteMainAndPre = remoteClean.split("-", limit = 2)
            val installedMainAndPre = installedClean.split("-", limit = 2)

            val remoteParts = remoteMainAndPre[0].split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val installedParts = installedMainAndPre[0].split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

            val maxLen = maxOf(remoteParts.size, installedParts.size, 3)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = installedParts.getOrElse(i) { 0 }
                if (r != l) return r.compareTo(l)
            }

            val remotePre = remoteMainAndPre.getOrNull(1)
            val installedPre = installedMainAndPre.getOrNull(1)
            return when {
                remotePre == null && installedPre == null -> 0
                remotePre == null && installedPre != null -> 1 // Stable is newer than pre-release of same version
                remotePre != null && installedPre == null -> -1 // Pre-release is older than stable of same version
                else -> (remotePre ?: "").compareTo(installedPre ?: "")
            }
        }

        /**
         * Determines whether a GitHub release is newer than the currently installed build.
         * Checks both semantic version (`versionName`) and optional `remoteVersionCode`.
         */
        fun isRemoteReleaseNewer(
            remoteVersionName: String,
            remoteVersionCode: Long?,
            installedVersionName: String,
            installedVersionCode: Long
        ): Boolean {
            val semVerComparison = compareSemanticVersions(remoteVersionName, installedVersionName)
            if (semVerComparison > 0) return true
            if (semVerComparison < 0) return false
            if (remoteVersionCode != null && remoteVersionCode > installedVersionCode) {
                return true
            }
            return false
        }

        /**
         * Parses a GitHub Release JSON object into [GitHubReleaseInfo].
         * Returns null if the release is a draft or has no downloadable `.apk` asset.
         */
        fun parseGitHubReleaseJson(jsonObject: JSONObject): GitHubReleaseInfo? {
            val isDraft = jsonObject.optBoolean("draft", false)
            if (isDraft) return null

            val tagName = jsonObject.optString("tag_name", "").trim()
            if (tagName.isEmpty()) return null

            val releaseName = jsonObject.optString("name", "").trim().ifEmpty { tagName }
            val body = jsonObject.optString("body", "").trim()
            val publishedAt = jsonObject.optString("published_at", "")
            val htmlUrl = jsonObject.optString("html_url", "")
            val isPrerelease = jsonObject.optBoolean("prerelease", false)

            val assetsArray = jsonObject.optJSONArray("assets") ?: JSONArray()
            val assets = mutableListOf<ReleaseAssetInfo>()
            for (i in 0 until assetsArray.length()) {
                val assetObj = assetsArray.optJSONObject(i) ?: continue
                val name = assetObj.optString("name", "").trim()
                val downloadUrl = assetObj.optString("browser_download_url", "").trim()
                val sizeBytes = assetObj.optLong("size", -1L)
                val contentType = assetObj.optString("content_type", "")
                if (name.isNotEmpty() && downloadUrl.startsWith("https://")) {
                    assets.add(
                        ReleaseAssetInfo(
                            name = name,
                            downloadUrl = downloadUrl,
                            sizeBytes = sizeBytes,
                            contentType = contentType
                        )
                    )
                }
            }

            // Prioritize signed release APK over debug APK if multiple APKs exist
            val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
            val selectedApk = apkAssets.firstOrNull {
                it.name.contains("release", ignoreCase = true) && !it.name.contains("unsigned", ignoreCase = true)
            } ?: apkAssets.firstOrNull {
                !it.name.contains("unsigned", ignoreCase = true)
            } ?: apkAssets.firstOrNull() ?: return null

            val checksumAsset = assets.firstOrNull {
                it.name.equals("SHA256SUMS.txt", ignoreCase = true) ||
                    it.name.equals("SHA256SUMS", ignoreCase = true) ||
                    it.name.equals("${selectedApk.name}.sha256", ignoreCase = true) ||
                    it.name.endsWith(".sha256", ignoreCase = true)
            }

            val inlineSha256 = parseSha256FromText(body, selectedApk.name)
            val remoteVersionCode = VERSION_CODE_PATTERN.find(body)?.groupValues?.getOrNull(1)?.toLongOrNull()

            return GitHubReleaseInfo(
                tagName = tagName,
                versionName = normalizeVersionString(tagName),
                remoteVersionCode = remoteVersionCode,
                releaseTitle = releaseName,
                releaseNotes = body.ifEmpty { "No release notes provided for $tagName." },
                publishedAt = publishedAt,
                htmlUrl = htmlUrl,
                isPrerelease = isPrerelease,
                apkAsset = selectedApk,
                checksumAsset = checksumAsset,
                expectedSha256 = inlineSha256
            )
        }

        /**
         * Extracts the expected SHA-256 hex digest for [apkFileName] from `SHA256SUMS.txt` content
         * or release notes text.
         */
        fun parseSha256FromText(text: String, apkFileName: String): String? {
            if (text.isBlank()) return null
            val lines = text.lines()
            // First look for a line explicitly mentioning the target APK filename
            for (line in lines) {
                if (line.contains(apkFileName, ignoreCase = true)) {
                    val match = SHA256_HEX_REGEX.find(line)
                    if (match != null) {
                        return match.groupValues[1].lowercase(Locale.US)
                    }
                }
            }
            // If only a single SHA-256 digest is present in a dedicated .sha256 file, accept it
            val allMatches = SHA256_HEX_REGEX.findAll(text).map { it.groupValues[1].lowercase(Locale.US) }.toList()
            if (allMatches.size == 1) {
                return allMatches.first()
            }
            return null
        }

        /**
         * Computes the lowercase SHA-256 hex digest of [file].
         */
        fun computeFileSha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            BufferedInputStream(FileInputStream(file)).use { input ->
                val buffer = ByteArray(16 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private val _updateState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    @Volatile
    private var activeDownloadJob: Job? = null

    fun getInstalledVersionName(): String = BuildConfig.VERSION_NAME

    fun getInstalledVersionCode(): Long = BuildConfig.VERSION_CODE.toLong()

    /**
     * Resets [_updateState] back to [UpdateCheckState.Idle] only if the current state is still
     * the same temporary notification state ([expectedState]). Never dismisses ongoing downloads,
     * verification, actionable updates, or persistent errors.
     */
    fun dismissTemporaryStateIfMatching(expectedState: UpdateCheckState? = null) {
        val current = _updateState.value
        if (!current.isTemporaryNotificationState) return
        if (expectedState == null || current == expectedState) {
            _updateState.value = UpdateCheckState.Idle
        }
    }

    /**
     * Checks GitHub Releases for a newer version of PulsePlay.
     *
     * @param configuredRepositorySlug User-configured `owner/repo` slug, or falls back to [DEFAULT_GITHUB_REPOSITORY].
     * @param includePrereleases Whether pre-release builds should be considered if no newer stable release exists.
     */
    suspend fun checkForUpdates(
        configuredRepositorySlug: String,
        includePrereleases: Boolean = false
    ): UpdateCheckState = withContext(ioDispatcher) {
        val rawRepo = configuredRepositorySlug.trim()
        val normalizedRepo = normalizeRepositorySlug(rawRepo)

        if (normalizedRepo.isEmpty() || !isValidRepositorySlug(normalizedRepo)) {
            val state = UpdateCheckState.ConfigurationRequired(
                "GitHub repository is not configured yet. Enter your repository in 'owner/repo' format to enable GitHub Release update checks."
            )
            _updateState.value = state
            return@withContext state
        }

        _updateState.value = UpdateCheckState.Checking

        try {
            val releaseInfo = fetchLatestEligibleRelease(normalizedRepo, includePrereleases)
            val installedName = getInstalledVersionName()
            val installedCode = getInstalledVersionCode()

            if (releaseInfo == null) {
                val upToDate = UpdateCheckState.UpToDate(
                    installedVersionName = installedName,
                    installedVersionCode = installedCode,
                    latestRemoteTag = null
                )
                _updateState.value = upToDate
                return@withContext upToDate
            }

            val isNewer = isRemoteReleaseNewer(
                remoteVersionName = releaseInfo.versionName,
                remoteVersionCode = releaseInfo.remoteVersionCode,
                installedVersionName = installedName,
                installedVersionCode = installedCode
            )

            val nextState = if (isNewer) {
                UpdateCheckState.UpdateAvailable(
                    releaseInfo = releaseInfo,
                    installedVersionName = installedName,
                    installedVersionCode = installedCode
                )
            } else {
                UpdateCheckState.UpToDate(
                    installedVersionName = installedName,
                    installedVersionCode = installedCode,
                    latestRemoteTag = releaseInfo.tagName
                )
            }
            _updateState.value = nextState
            nextState
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            val errState = UpdateCheckState.NetworkError(
                e.message?.takeIf { it.isNotBlank() }
                    ?: "Unable to reach GitHub Releases API. Check your internet connection or repository name."
            )
            _updateState.value = errState
            errState
        }
    }

    /**
     * Downloads the APK for [releaseInfo], verifies its SHA-256 digest and Android package archive
     * integrity, and transitions to [UpdateCheckState.ReadyToInstall].
     */
    suspend fun downloadAndVerifyUpdate(releaseInfo: GitHubReleaseInfo): UpdateCheckState = withContext(ioDispatcher) {
        activeDownloadJob = currentCoroutineContext()[Job]
        val updatesDir = File(context.cacheDir, UPDATES_CACHE_DIR).apply { mkdirs() }
        // Clean up any stale update files before downloading
        updatesDir.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }

        val sanitizedFileName = releaseInfo.apkAsset.name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val tempFile = File(updatesDir, "$sanitizedFileName.part")
        val finalApkFile = File(updatesDir, sanitizedFileName)

        try {
            _updateState.value = UpdateCheckState.Downloading(
                releaseInfo = releaseInfo,
                downloadedBytes = 0L,
                totalBytes = releaseInfo.apkAsset.sizeBytes.coerceAtLeast(0L),
                progressPercent = 0
            )

            // Resolve expected SHA-256 from checksum asset if not already embedded in release body
            val expectedSha256 = releaseInfo.expectedSha256 ?: releaseInfo.checksumAsset?.let { asset ->
                runCatching {
                    val checksumText = fetchTextUrl(asset.downloadUrl)
                    parseSha256FromText(checksumText, releaseInfo.apkAsset.name)
                }.getOrNull()
            }
            val resolvedRelease = releaseInfo.copy(expectedSha256 = expectedSha256)

            val connection = (URL(resolvedRelease.apkAsset.downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "PulsePlay-Android/${BuildConfig.VERSION_NAME}")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                connection.disconnect()
                val failed = UpdateCheckState.DownloadFailed(
                    releaseInfo = resolvedRelease,
                    reason = "GitHub asset download failed with HTTP $responseCode."
                )
                _updateState.value = failed
                return@withContext failed
            }

            val contentLength = connection.contentLengthLong.takeIf { it > 0L }
                ?: resolvedRelease.apkAsset.sizeBytes.takeIf { it > 0L }
                ?: -1L

            var downloadedBytes = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val percent = if (contentLength > 0L) {
                            ((downloadedBytes * 100L) / contentLength).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        _updateState.value = UpdateCheckState.Downloading(
                            releaseInfo = resolvedRelease,
                            downloadedBytes = downloadedBytes,
                            totalBytes = contentLength.coerceAtLeast(downloadedBytes),
                            progressPercent = percent
                        )
                    }
                    output.flush()
                }
            }
            connection.disconnect()

            if (contentLength > 0L && downloadedBytes != contentLength) {
                tempFile.delete()
                val failed = UpdateCheckState.DownloadFailed(
                    releaseInfo = resolvedRelease,
                    reason = "Incomplete APK download ($downloadedBytes of $contentLength bytes received)."
                )
                _updateState.value = failed
                return@withContext failed
            }

            if (finalApkFile.exists()) {
                finalApkFile.delete()
            }
            if (!tempFile.renameTo(finalApkFile)) {
                tempFile.copyTo(finalApkFile, overwrite = true)
                tempFile.delete()
            }

            _updateState.value = UpdateCheckState.Verifying(resolvedRelease)

            val verification = verifyDownloadedApk(
                apkFile = finalApkFile,
                expectedSha256Hex = resolvedRelease.expectedSha256
            )
            if (!verification.isValid) {
                finalApkFile.delete()
                val failedState = UpdateCheckState.VerificationFailed(
                    releaseInfo = resolvedRelease,
                    reason = verification.errorReason ?: "APK integrity verification failed."
                )
                _updateState.value = failedState
                return@withContext failedState
            }

            val readyState = UpdateCheckState.ReadyToInstall(
                releaseInfo = resolvedRelease,
                apkFile = finalApkFile,
                verifiedSha256 = verification.sha256Hex,
                archivePackageName = verification.archivePackageName,
                archiveVersionName = verification.archiveVersionName,
                archiveVersionCode = verification.archiveVersionCode
            )
            _updateState.value = readyState
            readyState
        } catch (ce: CancellationException) {
            tempFile.delete()
            finalApkFile.delete()
            _updateState.value = UpdateCheckState.UpdateAvailable(
                releaseInfo = releaseInfo,
                installedVersionName = getInstalledVersionName(),
                installedVersionCode = getInstalledVersionCode()
            )
            throw ce
        } catch (e: Exception) {
            tempFile.delete()
            finalApkFile.delete()
            val failed = UpdateCheckState.DownloadFailed(
                releaseInfo = releaseInfo,
                reason = e.message?.takeIf { it.isNotBlank() } ?: "APK download interrupted."
            )
            _updateState.value = failed
            failed
        } finally {
            activeDownloadJob = null
        }
    }

    fun cancelActiveDownload() {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
    }

    private val sourcePolicy = UpdateSourcePolicy(context)

    /**
     * Verifies a downloaded APK file against:
     * 1. UpdateSourcePolicy cross-channel protection (blocks replacing a Play-installed app with a GitHub APK)
     * 2. Non-empty file size
     * 3. Expected SHA-256 checksum (if provided by the release)
     * 4. Valid Android APK archive structure (`PackageManager.getPackageArchiveInfo`)
     * 5. Exact `packageName` match with the installed application (`context.packageName`)
     * 6. Non-downgrade `versionCode` check
     * 7. Signing certificate continuity check when signatures are available
     */
    fun verifyDownloadedApk(
        apkFile: File,
        expectedSha256Hex: String?,
        overrideSourceEvaluation: UpdateSourceEvaluation? = null,
        overrideArchiveCertSha256Set: Set<String>? = null
    ): ApkVerificationResult {
        val evaluation = overrideSourceEvaluation ?: sourcePolicy.evaluate()
        if (evaluation.blockGitHubApkOverwriteOfPlayInstall ||
            evaluation.detectedChannel == InstallChannel.GOOGLE_PLAY
        ) {
            return ApkVerificationResult(
                isValid = false,
                sha256Hex = "",
                errorReason = "Blocked by UpdateSourcePolicy: PulsePlay was installed from Google Play Store. " +
                    "A GitHub APK update cannot replace a Google Play installation."
            )
        }

        if (!apkFile.exists() || apkFile.length() <= 64L) {
            return ApkVerificationResult(
                isValid = false,
                sha256Hex = "",
                errorReason = "Downloaded APK file is empty or missing."
            )
        }

        val actualSha256 = runCatching { computeFileSha256Hex(apkFile) }.getOrElse {
            return ApkVerificationResult(
                isValid = false,
                sha256Hex = "",
                errorReason = "Failed to compute SHA-256 digest: ${it.message}"
            )
        }

        if (!expectedSha256Hex.isNullOrBlank()) {
            val normalizedExpected = expectedSha256Hex.trim().lowercase(Locale.US)
            if (actualSha256.lowercase(Locale.US) != normalizedExpected) {
                return ApkVerificationResult(
                    isValid = false,
                    sha256Hex = actualSha256,
                    errorReason = "SHA-256 checksum mismatch! Expected $normalizedExpected but got $actualSha256."
                )
            }
        }

        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val archiveInfo: PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            ) ?: pm.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(0L)
            )
        } else {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
                ?: pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
        }

        if (archiveInfo == null) {
            return ApkVerificationResult(
                isValid = false,
                sha256Hex = actualSha256,
                errorReason = "Downloaded file is not a valid Android APK package archive."
            )
        }

        val archivePkg = archiveInfo.packageName.orEmpty()
        if (archivePkg != context.packageName) {
            return ApkVerificationResult(
                isValid = false,
                sha256Hex = actualSha256,
                archivePackageName = archivePkg,
                errorReason = "APK applicationId mismatch: archive is '$archivePkg', expected '${context.packageName}'."
            )
        }

        @Suppress("DEPRECATION")
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode
        } else {
            archiveInfo.versionCode.toLong()
        }
        val archiveVersionName = archiveInfo.versionName ?: "unknown"

        val installedVersionCode = getInstalledVersionCode()
        if (archiveVersionCode < installedVersionCode) {
            return ApkVerificationResult(
                isValid = false,
                sha256Hex = actualSha256,
                archivePackageName = archivePkg,
                archiveVersionName = archiveVersionName,
                archiveVersionCode = archiveVersionCode,
                errorReason = "Downgrade prevented: APK versionCode ($archiveVersionCode) is older than installed ($installedVersionCode)."
            )
        }

        // Verify signing certificate continuity when both installed and archive certificates are readable
        val installedCertDigests = evaluation.installedSigningCertSha256Set.ifEmpty {
            getPackageSigningCertSha256Set(pm, context.packageName)
        }
        val archiveCertDigests = overrideArchiveCertSha256Set ?: extractSigningCertSha256Set(archiveInfo)
        val crossChannelValidation = UpdateSourcePolicy.validateGitHubApkInstallAttempt(
            evaluation = evaluation.copy(installedSigningCertSha256Set = installedCertDigests),
            installedVersionCode = installedVersionCode,
            candidateVersionCode = archiveVersionCode,
            candidateSigningCertSha256Set = archiveCertDigests
        )
        if (!crossChannelValidation.isAllowed) {
            return ApkVerificationResult(
                isValid = false,
                sha256Hex = actualSha256,
                archivePackageName = archivePkg,
                archiveVersionName = archiveVersionName,
                archiveVersionCode = archiveVersionCode,
                errorReason = "Signing certificate mismatch! ${crossChannelValidation.reason}"
            )
        }

        return ApkVerificationResult(
            isValid = true,
            sha256Hex = actualSha256,
            archivePackageName = archivePkg,
            archiveVersionName = archiveVersionName,
            archiveVersionCode = archiveVersionCode,
            errorReason = null
        )
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun buildUnknownSourcesSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun buildInstallApkIntent(apkFile: File): Intent {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Initiates the Android system package installer for a verified [ReadyToInstall] or [InstallationPending] APK.
     * If `canRequestPackageInstalls()` is false on Android 8.0+, transitions to [UpdateCheckState.InstallationPending]
     * and opens the system Unknown Sources permission screen for PulsePlay.
     */
    fun triggerInstallOrPermissionFlow(
        overrideSourceEvaluation: UpdateSourceEvaluation? = null
    ): Boolean {
        val evaluation = overrideSourceEvaluation ?: sourcePolicy.evaluate()
        val current = _updateState.value
        val (releaseInfo, apkFile, verifiedSha256) = when (current) {
            is UpdateCheckState.ReadyToInstall -> Triple(current.releaseInfo, current.apkFile, current.verifiedSha256)
            is UpdateCheckState.InstallationPending -> Triple(current.releaseInfo, current.apkFile, current.verifiedSha256)
            else -> return false
        }

        if (evaluation.blockGitHubApkOverwriteOfPlayInstall ||
            evaluation.detectedChannel == InstallChannel.GOOGLE_PLAY
        ) {
            _updateState.value = UpdateCheckState.VerificationFailed(
                releaseInfo = releaseInfo,
                reason = "Blocked by UpdateSourcePolicy: PulsePlay was installed from Google Play Store. " +
                    "A GitHub APK update cannot replace a Google Play installation."
            )
            return false
        }

        if (!apkFile.exists()) {
            _updateState.value = UpdateCheckState.VerificationFailed(
                releaseInfo = releaseInfo,
                reason = "Downloaded APK file is no longer in cache. Please download the update again."
            )
            return false
        }

        if (!canRequestPackageInstalls()) {
            _updateState.value = UpdateCheckState.InstallationPending(
                releaseInfo = releaseInfo,
                apkFile = apkFile,
                verifiedSha256 = verifiedSha256,
                requiresUnknownSourcesPermission = true,
                statusMessage = "Grant 'Install unknown apps' permission for PulsePlay, then tap 'Install Verified Update'."
            )
            runCatching {
                context.startActivity(buildUnknownSourcesSettingsIntent())
            }
            return false
        }

        return runCatching {
            context.startActivity(buildInstallApkIntent(apkFile))
            _updateState.value = UpdateCheckState.InstallationPending(
                releaseInfo = releaseInfo,
                apkFile = apkFile,
                verifiedSha256 = verifiedSha256,
                requiresUnknownSourcesPermission = false,
                statusMessage = "Android Package Installer launched. Confirm installation in the system prompt."
            )
            true
        }.getOrElse { err ->
            _updateState.value = UpdateCheckState.VerificationFailed(
                releaseInfo = releaseInfo,
                reason = "Failed to launch Android Package Installer: ${err.message}"
            )
            false
        }
    }

    private fun fetchLatestEligibleRelease(
        normalizedRepo: String,
        includePrereleases: Boolean
    ): GitHubReleaseInfo? {
        if (!includePrereleases) {
            val latestUrl = "$GITHUB_API_BASE/$normalizedRepo/releases/latest"
            val latestResponse = fetchJsonWithStatus(latestUrl)
            if (latestResponse.first in 200..299 && latestResponse.second.isNotBlank()) {
                val parsed = parseGitHubReleaseJson(JSONObject(latestResponse.second))
                if (parsed != null) return parsed
            } else if (latestResponse.first != 404) {
                throw IllegalStateException("GitHub API returned HTTP ${latestResponse.first} for $normalizedRepo")
            }
        }

        val listUrl = "$GITHUB_API_BASE/$normalizedRepo/releases?per_page=10"
        val (status, body) = fetchJsonWithStatus(listUrl)
        if (status == 404) {
            throw IllegalStateException("Repository '$normalizedRepo' or its GitHub Releases were not found (HTTP 404).")
        }
        if (status !in 200..299) {
            throw IllegalStateException("GitHub API returned HTTP $status for $normalizedRepo.")
        }

        val array = JSONArray(body)
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val parsed = parseGitHubReleaseJson(obj) ?: continue
            if (!includePrereleases && parsed.isPrerelease) continue
            return parsed
        }
        return null
    }

    private fun fetchJsonWithStatus(urlString: String): Pair<Int, String> {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "PulsePlay-Android/${BuildConfig.VERSION_NAME}")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to text
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchTextUrl(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PulsePlay-Android/${BuildConfig.VERSION_NAME}")
        }
        return try {
            if (connection.responseCode !in 200..299) return ""
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun getPackageSigningCertSha256Set(pm: PackageManager, packageName: String): Set<String> {
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

    private fun extractSigningCertSha256Set(pkgInfo: PackageInfo): Set<String> {
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
                    digests.add(sha)
                }
            }
            @Suppress("DEPRECATION")
            pkgInfo.signatures?.forEach { sig ->
                val sha = MessageDigest.getInstance("SHA-256")
                    .digest(sig.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                digests.add(sha)
            }
        }
        return digests
    }
}
