package com.example.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager as PlayCoreAppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class PlayStoreUpdateState {
    companion object {
        private val emissionCounter = java.util.concurrent.atomic.AtomicLong(1L)
        fun nextEmissionId(): Long = emissionCounter.getAndIncrement()
    }

    data object Idle : PlayStoreUpdateState()
    data object Checking : PlayStoreUpdateState()

    data class NoUpdateAvailable(
        val installedVersionName: String,
        val installedVersionCode: Long,
        val availabilityCode: Int,
        val statusMessage: String,
        val emissionId: Long = nextEmissionId()
    ) : PlayStoreUpdateState()

    data class UpdateAvailable(
        val availableVersionCode: Int,
        val installedVersionCode: Long,
        val updatePriority: Int,
        val clientVersionStalenessDays: Int?,
        val isFlexibleAllowed: Boolean,
        val isImmediateAllowed: Boolean,
        val recommendedUpdateType: Int,
        val appUpdateInfo: AppUpdateInfo?,
        val statusMessage: String
    ) : PlayStoreUpdateState()

    data class FlexibleDownloading(
        val availableVersionCode: Int,
        val bytesDownloaded: Long,
        val totalBytesToDownload: Long,
        val progressPercent: Int
    ) : PlayStoreUpdateState()

    data class DownloadedReadyToComplete(
        val availableVersionCode: Int,
        val promptMessage: String
    ) : PlayStoreUpdateState()

    data class Installing(
        val availableVersionCode: Int
    ) : PlayStoreUpdateState()

    data class Installed(
        val versionCode: Int,
        val statusMessage: String,
        val emissionId: Long = nextEmissionId()
    ) : PlayStoreUpdateState()

    data class DeveloperTriggeredUpdateInProgress(
        val availableVersionCode: Int,
        val appUpdateInfo: AppUpdateInfo?,
        val statusMessage: String
    ) : PlayStoreUpdateState()

    data class Cancelled(
        val statusMessage: String,
        val emissionId: Long = nextEmissionId()
    ) : PlayStoreUpdateState()

    data class PlayStoreUnavailableOrUnpublished(
        val errorCode: Int,
        val reason: String,
        val emissionId: Long = nextEmissionId()
    ) : PlayStoreUpdateState()

    data class UpdateFailed(
        val errorCode: Int,
        val reason: String
    ) : PlayStoreUpdateState()

    /**
     * Returns true if this state represents a temporary, non-critical status result that should
     * auto-dismiss after ~3.5 seconds, or false if it represents an ongoing download/install,
     * actionable update, or persistent error.
     */
    val isTemporaryNotificationState: Boolean
        get() = this is NoUpdateAvailable ||
            this is Installed ||
            this is Cancelled ||
            this is PlayStoreUnavailableOrUnpublished
}

/**
 * Official Google Play In-App Updates provider for PulsePlay.
 *
 * Responsibilities:
 * - Integrates the official `com.google.android.play:app-update` library via [PlayCoreAppUpdateManager].
 * - Queries real update availability (`UpdateAvailability`) and allowed update types
 *   ([AppUpdateType.FLEXIBLE] and [AppUpdateType.IMMEDIATE]).
 * - Supports background downloading via the Flexible update flow with real-time progress tracking
 *   and explicit user confirmation before calling `completeUpdate()`.
 * - Supports full-screen Immediate updates for high-priority or critical releases.
 * - Handles lifecycle resume behavior (`checkAndResumePendingUpdate`) when an immediate update was
 *   interrupted (`DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS`) or when a flexible update finished
 *   downloading while the app was in the background (`InstallStatus.DOWNLOADED`).
 * - Never fabricates a Play Store update when the app is not yet published or not installed from
 *   Google Play; instead surfaces an accurate, transparent status and provides a direct intent to
 *   open the official Google Play Store listing.
 */
class PlayStoreUpdateProvider(
    private val context: Context,
    private val playCoreManagerProvider: () -> PlayCoreAppUpdateManager = {
        AppUpdateManagerFactory.create(context.applicationContext)
    }
) {
    companion object {
        const val HIGH_PRIORITY_IMMEDIATE_THRESHOLD = 4
        const val STALENESS_DAYS_IMMEDIATE_THRESHOLD = 14
        const val PLAY_UPDATE_REQUEST_CODE = 9412

        private val DIRECT_EXECUTOR = Executor { command -> command.run() }

        fun describeInstallErrorCode(errorCode: Int): String {
            return when (errorCode) {
                InstallErrorCode.ERROR_API_NOT_AVAILABLE ->
                    "Google Play In-App Updates API is not available on this device or emulator."
                InstallErrorCode.ERROR_APP_NOT_OWNED ->
                    "PulsePlay is not installed from Google Play on this account (or has not been published to Google Play yet). Play In-App Updates require an official Google Play installation."
                InstallErrorCode.ERROR_DOWNLOAD_NOT_PRESENT ->
                    "The Google Play update download is not present on the device."
                InstallErrorCode.ERROR_INSTALL_NOT_ALLOWED ->
                    "Google Play update installation is not allowed right now (for example, low battery or storage)."
                InstallErrorCode.ERROR_INSTALL_UNAVAILABLE ->
                    "Google Play update is unavailable for this device or Play account."
                InstallErrorCode.ERROR_INTERNAL_ERROR ->
                    "An internal error occurred inside the Google Play Store app."
                InstallErrorCode.ERROR_INVALID_REQUEST ->
                    "Invalid Google Play update request parameters."
                InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND ->
                    "The official Google Play Store app (com.android.vending) is not installed on this device."
                else ->
                    "Google Play update check returned error code $errorCode."
            }
        }
    }

    private val playCoreManager: PlayCoreAppUpdateManager by lazy { playCoreManagerProvider() }

    private val _state = MutableStateFlow<PlayStoreUpdateState>(PlayStoreUpdateState.Idle)
    val state: StateFlow<PlayStoreUpdateState> = _state.asStateFlow()

    @Volatile
    private var lastKnownAvailableVersionCode: Int = BuildConfig.VERSION_CODE

    @Volatile
    private var isListenerRegistered: Boolean = false

    private val installStateListener = InstallStateUpdatedListener { installState ->
        handleInstallStateUpdate(installState)
    }

    fun registerListenerIfNeeded() {
        if (!isListenerRegistered) {
            runCatching {
                playCoreManager.registerListener(installStateListener)
                isListenerRegistered = true
            }
        }
    }

    fun unregisterListener() {
        if (isListenerRegistered) {
            runCatching {
                playCoreManager.unregisterListener(installStateListener)
            }
            isListenerRegistered = false
        }
    }

    /**
     * Resets [_state] back to [PlayStoreUpdateState.Idle] only if the current state is still the
     * same temporary notification state ([expectedState]). Never dismisses ongoing downloads,
     * installation progress, actionable updates, or persistent errors.
     */
    fun dismissTemporaryStateIfMatching(expectedState: PlayStoreUpdateState? = null) {
        val current = _state.value
        if (!current.isTemporaryNotificationState) return
        if (expectedState == null || current == expectedState) {
            _state.value = PlayStoreUpdateState.Idle
        }
    }

    internal fun handleInstallStateUpdate(installState: InstallState) {
        when (installState.installStatus()) {
            InstallStatus.PENDING -> {
                _state.value = PlayStoreUpdateState.FlexibleDownloading(
                    availableVersionCode = lastKnownAvailableVersionCode,
                    bytesDownloaded = 0L,
                    totalBytesToDownload = installState.totalBytesToDownload().coerceAtLeast(0L),
                    progressPercent = 0
                )
            }

            InstallStatus.DOWNLOADING -> {
                val downloaded = installState.bytesDownloaded().coerceAtLeast(0L)
                val total = installState.totalBytesToDownload().coerceAtLeast(downloaded)
                val percent = if (total > 0L) {
                    ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                _state.value = PlayStoreUpdateState.FlexibleDownloading(
                    availableVersionCode = lastKnownAvailableVersionCode,
                    bytesDownloaded = downloaded,
                    totalBytesToDownload = total,
                    progressPercent = percent
                )
            }

            InstallStatus.DOWNLOADED -> {
                _state.value = PlayStoreUpdateState.DownloadedReadyToComplete(
                    availableVersionCode = lastKnownAvailableVersionCode,
                    promptMessage = "Google Play update (versionCode $lastKnownAvailableVersionCode) has finished downloading in the background. Tap 'Complete Update & Restart' to install."
                )
            }

            InstallStatus.INSTALLING -> {
                _state.value = PlayStoreUpdateState.Installing(
                    availableVersionCode = lastKnownAvailableVersionCode
                )
            }

            InstallStatus.INSTALLED -> {
                _state.value = PlayStoreUpdateState.Installed(
                    versionCode = lastKnownAvailableVersionCode,
                    statusMessage = "Google Play update installed successfully."
                )
                unregisterListener()
            }

            InstallStatus.CANCELED -> {
                _state.value = PlayStoreUpdateState.Cancelled(
                    statusMessage = "Google Play update was cancelled by the user."
                )
            }

            InstallStatus.FAILED -> {
                val errCode = installState.installErrorCode()
                _state.value = PlayStoreUpdateState.UpdateFailed(
                    errorCode = errCode,
                    reason = describeInstallErrorCode(errCode)
                )
            }

            else -> Unit
        }
    }

    /**
     * Queries the official Google Play In-App Updates API for update availability and allowed flows.
     */
    suspend fun checkForPlayStoreUpdate(): PlayStoreUpdateState {
        _state.value = PlayStoreUpdateState.Checking
        registerListenerIfNeeded()

        return try {
            val info = awaitPlayTask(playCoreManager.appUpdateInfo)
            val installedCode = BuildConfig.VERSION_CODE.toLong()
            val installedName = BuildConfig.VERSION_NAME

            // If a flexible update was already downloaded in the background, prompt completion first
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                val availCode = info.availableVersionCode().takeIf { it > 0 } ?: BuildConfig.VERSION_CODE
                lastKnownAvailableVersionCode = availCode
                val downloadedState = PlayStoreUpdateState.DownloadedReadyToComplete(
                    availableVersionCode = availCode,
                    promptMessage = "A downloaded Google Play update (versionCode $availCode) is ready to install. Confirm to complete the update."
                )
                _state.value = downloadedState
                return downloadedState
            }

            when (val availability = info.updateAvailability()) {
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                    val availCode = info.availableVersionCode()
                    lastKnownAvailableVersionCode = availCode
                    val inProgress = PlayStoreUpdateState.DeveloperTriggeredUpdateInProgress(
                        availableVersionCode = availCode,
                        appUpdateInfo = info,
                        statusMessage = "An immediate Google Play update (versionCode $availCode) is in progress and ready to resume."
                    )
                    _state.value = inProgress
                    inProgress
                }

                UpdateAvailability.UPDATE_AVAILABLE -> {
                    val availCode = info.availableVersionCode()
                    lastKnownAvailableVersionCode = availCode

                    // Enforce monotonic versionCode check even for store metadata
                    if (!UpdateSourcePolicy.validateMonotonicVersionCode(installedCode, availCode.toLong())) {
                        val noUpdate = PlayStoreUpdateState.NoUpdateAvailable(
                            installedVersionName = installedName,
                            installedVersionCode = installedCode,
                            availabilityCode = availability,
                            statusMessage = "Installed build v$installedName (versionCode $installedCode) is already at or ahead of Google Play versionCode $availCode."
                        )
                        _state.value = noUpdate
                        return noUpdate
                    }

                    val flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                    val immediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                    val priority = info.updatePriority()
                    val staleness = info.clientVersionStalenessDays()

                    val shouldPreferImmediate = immediateAllowed && (
                        priority >= HIGH_PRIORITY_IMMEDIATE_THRESHOLD ||
                            ((staleness ?: 0) >= STALENESS_DAYS_IMMEDIATE_THRESHOLD && !flexibleAllowed) ||
                            !flexibleAllowed
                        )
                    val recommendedType = if (shouldPreferImmediate) {
                        AppUpdateType.IMMEDIATE
                    } else {
                        AppUpdateType.FLEXIBLE
                    }

                    val modeSummary = buildList {
                        if (flexibleAllowed) add("Flexible (background download)")
                        if (immediateAllowed) add("Immediate (full-screen)")
                    }.ifEmpty { listOf("Store Listing") }.joinToString(" & ")

                    val availableState = PlayStoreUpdateState.UpdateAvailable(
                        availableVersionCode = availCode,
                        installedVersionCode = installedCode,
                        updatePriority = priority,
                        clientVersionStalenessDays = staleness,
                        isFlexibleAllowed = flexibleAllowed,
                        isImmediateAllowed = immediateAllowed,
                        recommendedUpdateType = recommendedType,
                        appUpdateInfo = info,
                        statusMessage = "Google Play update available: versionCode $availCode (priority $priority). Supported flows: $modeSummary."
                    )
                    _state.value = availableState
                    availableState
                }

                UpdateAvailability.UPDATE_NOT_AVAILABLE,
                UpdateAvailability.UNKNOWN -> {
                    val noUpdate = PlayStoreUpdateState.NoUpdateAvailable(
                        installedVersionName = installedName,
                        installedVersionCode = installedCode,
                        availabilityCode = availability,
                        statusMessage = "No Google Play update is currently available for PulsePlay v$installedName (versionCode $installedCode). If PulsePlay has not yet been published to your Google Play track, updates will appear here once published."
                    )
                    _state.value = noUpdate
                    noUpdate
                }

                else -> {
                    val noUpdate = PlayStoreUpdateState.NoUpdateAvailable(
                        installedVersionName = installedName,
                        installedVersionCode = installedCode,
                        availabilityCode = availability,
                        statusMessage = "Google Play reported status code $availability (no update available)."
                    )
                    _state.value = noUpdate
                    noUpdate
                }
            }
        } catch (installEx: InstallException) {
            val code = installEx.errorCode
            val unavailable = PlayStoreUpdateState.PlayStoreUnavailableOrUnpublished(
                errorCode = code,
                reason = describeInstallErrorCode(code)
            )
            _state.value = unavailable
            unavailable
        } catch (t: Throwable) {
            val unavailable = PlayStoreUpdateState.PlayStoreUnavailableOrUnpublished(
                errorCode = InstallErrorCode.ERROR_INTERNAL_ERROR,
                reason = t.message?.takeIf { it.isNotBlank() }
                    ?: "Google Play Store In-App Updates service could not be reached on this device."
            )
            _state.value = unavailable
            unavailable
        }
    }

    /**
     * Starts a Flexible Google Play update flow (`AppUpdateType.FLEXIBLE`) so the update downloads
     * in the background while the user continues using PulsePlay.
     */
    @Suppress("DEPRECATION")
    fun startFlexibleUpdateFlow(activity: Activity, appUpdateInfo: AppUpdateInfo? = null): Boolean {
        val resolvedInfo = appUpdateInfo
            ?: (state.value as? PlayStoreUpdateState.UpdateAvailable)?.appUpdateInfo
            ?: return false

        if (!resolvedInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
            _state.value = PlayStoreUpdateState.UpdateFailed(
                errorCode = InstallErrorCode.ERROR_INSTALL_NOT_ALLOWED,
                reason = "Flexible background update is not allowed for this Google Play release."
            )
            return false
        }

        registerListenerIfNeeded()
        lastKnownAvailableVersionCode = resolvedInfo.availableVersionCode()

        return runCatching {
            val options = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
            playCoreManager.startUpdateFlowForResult(
                resolvedInfo,
                activity,
                options,
                PLAY_UPDATE_REQUEST_CODE
            )
        }.getOrElse { err ->
            _state.value = PlayStoreUpdateState.UpdateFailed(
                errorCode = InstallErrorCode.ERROR_INTERNAL_ERROR,
                reason = "Failed to start Google Play Flexible update flow: ${err.message}"
            )
            false
        }
    }

    /**
     * Starts an Immediate Google Play update flow (`AppUpdateType.IMMEDIATE`) for high-priority
     * or critical updates.
     */
    @Suppress("DEPRECATION")
    fun startImmediateUpdateFlow(activity: Activity, appUpdateInfo: AppUpdateInfo? = null): Boolean {
        val resolvedInfo = appUpdateInfo
            ?: (state.value as? PlayStoreUpdateState.UpdateAvailable)?.appUpdateInfo
            ?: (state.value as? PlayStoreUpdateState.DeveloperTriggeredUpdateInProgress)?.appUpdateInfo
            ?: return false

        if (!resolvedInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            _state.value = PlayStoreUpdateState.UpdateFailed(
                errorCode = InstallErrorCode.ERROR_INSTALL_NOT_ALLOWED,
                reason = "Immediate update flow is not allowed for this Google Play release."
            )
            return false
        }

        registerListenerIfNeeded()
        lastKnownAvailableVersionCode = resolvedInfo.availableVersionCode()

        return runCatching {
            val options = AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
            playCoreManager.startUpdateFlowForResult(
                resolvedInfo,
                activity,
                options,
                PLAY_UPDATE_REQUEST_CODE
            )
        }.getOrElse { err ->
            _state.value = PlayStoreUpdateState.UpdateFailed(
                errorCode = InstallErrorCode.ERROR_INTERNAL_ERROR,
                reason = "Failed to start Google Play Immediate update flow: ${err.message}"
            )
            false
        }
    }

    /**
     * Completes a downloaded Flexible Google Play update once confirmed by the user.
     */
    suspend fun completeFlexibleUpdate(): Boolean {
        return try {
            _state.value = PlayStoreUpdateState.Installing(
                availableVersionCode = lastKnownAvailableVersionCode
            )
            awaitPlayTask(playCoreManager.completeUpdate())
            true
        } catch (t: Throwable) {
            val code = (t as? InstallException)?.errorCode ?: InstallErrorCode.ERROR_INTERNAL_ERROR
            _state.value = PlayStoreUpdateState.UpdateFailed(
                errorCode = code,
                reason = "Failed to complete Google Play update installation: ${t.message ?: describeInstallErrorCode(code)}"
            )
            false
        }
    }

    /**
     * Inspects Play Store update state when the Activity resumes (`onResume`) to:
     * 1. Resume an interrupted `DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS` immediate update if [activity] is provided.
     * 2. Surface `DownloadedReadyToComplete` if a flexible update finished downloading in the background.
     */
    suspend fun onActivityResumed(activity: Activity? = null): PlayStoreUpdateState {
        return try {
            val info = awaitPlayTask(playCoreManager.appUpdateInfo)
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                val availCode = info.availableVersionCode().takeIf { it > 0 } ?: lastKnownAvailableVersionCode
                lastKnownAvailableVersionCode = availCode
                val ready = PlayStoreUpdateState.DownloadedReadyToComplete(
                    availableVersionCode = availCode,
                    promptMessage = "A Google Play update (versionCode $availCode) finished downloading in the background and is ready to install."
                )
                _state.value = ready
                return ready
            }

            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                val availCode = info.availableVersionCode()
                lastKnownAvailableVersionCode = availCode
                val inProgress = PlayStoreUpdateState.DeveloperTriggeredUpdateInProgress(
                    availableVersionCode = availCode,
                    appUpdateInfo = info,
                    statusMessage = "Resuming interrupted Google Play immediate update (versionCode $availCode)..."
                )
                _state.value = inProgress
                if (activity != null && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    startImmediateUpdateFlow(activity, info)
                }
                return inProgress
            }

            _state.value
        } catch (_: Throwable) {
            _state.value
        }
    }

    fun onUpdateFlowActivityResult(resultCode: Int) {
        when (resultCode) {
            Activity.RESULT_OK -> Unit
            Activity.RESULT_CANCELED -> {
                _state.value = PlayStoreUpdateState.Cancelled(
                    statusMessage = "Google Play update flow was cancelled by the user."
                )
            }
            else -> {
                _state.value = PlayStoreUpdateState.UpdateFailed(
                    errorCode = resultCode,
                    reason = "Google Play update flow ended with result code $resultCode."
                )
            }
        }
    }

    fun buildPlayStoreMarketIntent(packageName: String = context.packageName): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName")
        ).apply {
            setPackage(UpdateSourcePolicy.PLAY_STORE_PACKAGE_NAME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildPlayStoreWebIntent(packageName: String = context.packageName): Intent {
        return Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Opens the official Google Play Store listing for PulsePlay, falling back to the HTTPS Play Store
     * URL if the Play Store app is not installed on the device.
     */
    fun openOfficialPlayStoreListing(packageName: String = context.packageName): Boolean {
        return runCatching {
            context.startActivity(buildPlayStoreMarketIntent(packageName))
            true
        }.recoverCatching {
            context.startActivity(buildPlayStoreWebIntent(packageName))
            true
        }.getOrDefault(false)
    }

    private suspend fun <T> awaitPlayTask(task: Task<T>): T {
        if (task.isComplete) {
            if (task.isSuccessful) {
                @Suppress("UNCHECKED_CAST")
                return task.result as T
            } else {
                throw task.exception ?: IllegalStateException("Google Play Task failed without exception.")
            }
        }
        return suspendCancellableCoroutine { cont ->
            task.addOnCompleteListener(DIRECT_EXECUTOR) { completed ->
                if (!cont.isActive) return@addOnCompleteListener
                if (completed.isSuccessful) {
                    @Suppress("UNCHECKED_CAST")
                    cont.resume(completed.result as T)
                } else {
                    cont.resumeWithException(
                        completed.exception ?: IllegalStateException("Google Play Task failed.")
                    )
                }
            }
        }
    }
}
