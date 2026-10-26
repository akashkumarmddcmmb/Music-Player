package com.example.update

import android.app.Activity
import android.content.Context
import com.example.BuildConfig
import com.example.ui.components.InAppNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Unified snapshot of PulsePlay's dual-distribution update state (GitHub Releases + Google Play Store).
 */
data class UnifiedUpdateState(
    val installedVersionName: String = BuildConfig.VERSION_NAME,
    val installedVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
    val sourceEvaluation: UpdateSourceEvaluation,
    val activeChannel: UpdateDistributionChannel,
    val gitHubState: UpdateCheckState = UpdateCheckState.Idle,
    val playStoreState: PlayStoreUpdateState = PlayStoreUpdateState.Idle,
    val lastPolicyBlockMessage: String? = null
)

/**
 * Shared Update Manager coordinating [UpdateSourcePolicy], [GitHubUpdateProvider], and
 * [PlayStoreUpdateProvider].
 *
 * Responsibilities:
 * - Exposes common update state, installed version metadata, release notes, and error handling.
 * - Uses [UpdateSourcePolicy] to select the appropriate distribution channel for the current
 *   installation source (`Google Play Store`, `GitHub / Package Installer`, or `Unverified`).
 * - Prevents a GitHub APK update from replacing a Google Play-installed application.
 * - Automatically dismisses temporary update check states (`ConfigurationRequired`, `UpToDate`,
 *   `NoUpdateAvailable`, `Cancelled`, `Installed`, `PlayStoreUnavailableOrUnpublished`) after
 *   ~3.5 seconds while keeping ongoing progress, actionable updates, and security errors visible.
 */
class UpdateManager(
    context: Context,
    val sourcePolicy: UpdateSourcePolicy = UpdateSourcePolicy(context),
    val appUpdateManager: AppUpdateManager = AppUpdateManager(context),
    val gitHubProvider: GitHubUpdateProvider = GitHubUpdateProvider(
        context = context,
        appUpdateManager = appUpdateManager,
        sourcePolicy = sourcePolicy
    ),
    val playStoreProvider: PlayStoreUpdateProvider = PlayStoreUpdateProvider(context),
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    val temporaryStateAutoDismissMs: Long = InAppNotificationManager.DEFAULT_AUTO_DISMISS_DURATION_MS
) {
    private val _sourceEvaluation = MutableStateFlow(sourcePolicy.evaluate())
    val sourceEvaluation: StateFlow<UpdateSourceEvaluation> = _sourceEvaluation.asStateFlow()

    private val _activeChannel = MutableStateFlow(_sourceEvaluation.value.recommendedChannel)
    val activeChannel: StateFlow<UpdateDistributionChannel> = _activeChannel.asStateFlow()

    private val _lastPolicyBlockMessage = MutableStateFlow<String?>(null)
    val lastPolicyBlockMessage: StateFlow<String?> = _lastPolicyBlockMessage.asStateFlow()

    val gitHubState: StateFlow<UpdateCheckState> = gitHubProvider.state
    val playStoreState: StateFlow<PlayStoreUpdateState> = playStoreProvider.state

    private var gitHubTempDismissJob: Job? = null
    private var playStoreTempDismissJob: Job? = null

    init {
        externalScope.launch {
            gitHubState.collect { state ->
                gitHubTempDismissJob?.cancel()
                gitHubTempDismissJob = null
                if (state.isTemporaryNotificationState && temporaryStateAutoDismissMs > 0L) {
                    gitHubTempDismissJob = externalScope.launch {
                        delay(temporaryStateAutoDismissMs)
                        gitHubProvider.dismissTemporaryStateIfMatching(state)
                    }
                }
            }
        }
        externalScope.launch {
            playStoreState.collect { state ->
                playStoreTempDismissJob?.cancel()
                playStoreTempDismissJob = null
                if (state.isTemporaryNotificationState && temporaryStateAutoDismissMs > 0L) {
                    playStoreTempDismissJob = externalScope.launch {
                        delay(temporaryStateAutoDismissMs)
                        playStoreProvider.dismissTemporaryStateIfMatching(state)
                    }
                }
            }
        }
    }

    val unifiedState: StateFlow<UnifiedUpdateState> = combine(
        _sourceEvaluation,
        _activeChannel,
        gitHubState,
        playStoreState,
        _lastPolicyBlockMessage
    ) { eval, channel, ghState, playState, blockMsg ->
        UnifiedUpdateState(
            installedVersionName = BuildConfig.VERSION_NAME,
            installedVersionCode = BuildConfig.VERSION_CODE.toLong(),
            sourceEvaluation = eval,
            activeChannel = channel,
            gitHubState = ghState,
            playStoreState = playState,
            lastPolicyBlockMessage = blockMsg
        )
    }.stateIn(
        scope = externalScope,
        started = SharingStarted.Eagerly,
        initialValue = UnifiedUpdateState(
            sourceEvaluation = _sourceEvaluation.value,
            activeChannel = _activeChannel.value
        )
    )

    private var userSelectedChannelExplicitly: Boolean = false

    /**
     * Re-evaluates the installation source (or applies [overrideSignals] for testing) and updates
     * the recommended channel accordingly unless the user has explicitly selected a channel.
     */
    fun refreshInstallSourceEvaluation(overrideSignals: InstallSourceSignals? = null): UpdateSourceEvaluation {
        val eval = sourcePolicy.evaluate(overrideSignals)
        _sourceEvaluation.value = eval
        if (overrideSignals != null) {
            userSelectedChannelExplicitly = false
            _activeChannel.value = eval.recommendedChannel
        } else if (!userSelectedChannelExplicitly) {
            _activeChannel.value = eval.recommendedChannel
        }
        return eval
    }

    /**
     * Switches the active update provider panel in Settings (`GITHUB_RELEASES` or `GOOGLE_PLAY`).
     * Always updates [activeChannel] as the single source of truth for conditional UI rendering and
     * clears stale status/policy messages from the unselected provider.
     *
     * Returns `false` (and sets [lastPolicyBlockMessage] for the GitHub panel) if the app was
     * installed from Google Play and the user selects GitHub Releases, since [UpdateSourcePolicy]
     * prohibits overwriting a Play-installed app with an external GitHub APK.
     */
    fun selectChannel(channel: UpdateDistributionChannel): Boolean {
        userSelectedChannelExplicitly = true
        _activeChannel.value = channel
        val eval = _sourceEvaluation.value
        if (channel == UpdateDistributionChannel.GITHUB_RELEASES &&
            (eval.blockGitHubApkOverwriteOfPlayInstall || eval.detectedChannel == InstallChannel.GOOGLE_PLAY)
        ) {
            _lastPolicyBlockMessage.value =
                "Blocked by UpdateSourcePolicy: PulsePlay was installed from Google Play Store and cannot be overwritten by an external GitHub APK."
            return false
        }
        _lastPolicyBlockMessage.value = null
        return true
    }

    fun clearPolicyBlockMessage() {
        _lastPolicyBlockMessage.value = null
    }

    fun cancelPendingDismissJobs() {
        gitHubTempDismissJob?.cancel()
        gitHubTempDismissJob = null
        playStoreTempDismissJob?.cancel()
        playStoreTempDismissJob = null
    }

    /**
     * Checks for updates on the currently active channel (GitHub Releases or Google Play Store).
     */
    suspend fun checkForUpdatesOnActiveChannel(
        configuredRepositorySlug: String,
        includePrereleases: Boolean = false
    ) {
        val eval = _sourceEvaluation.value
        if (eval.detectedChannel == InstallChannel.GOOGLE_PLAY ||
            _activeChannel.value == UpdateDistributionChannel.GOOGLE_PLAY
        ) {
            checkPlayStoreUpdates()
        } else {
            checkGitHubUpdates(
                configuredRepositorySlug = configuredRepositorySlug,
                includePrereleases = includePrereleases
            )
        }
    }

    suspend fun checkGitHubUpdates(
        configuredRepositorySlug: String,
        includePrereleases: Boolean = false
    ): UpdateCheckState {
        gitHubTempDismissJob?.cancel()
        val eval = _sourceEvaluation.value
        if (eval.blockGitHubApkOverwriteOfPlayInstall || eval.detectedChannel == InstallChannel.GOOGLE_PLAY) {
            val msg = "Blocked by UpdateSourcePolicy: PulsePlay was installed from Google Play Store. " +
                "A GitHub APK update cannot replace a Google Play installation."
            _lastPolicyBlockMessage.value = msg
            return UpdateCheckState.VerificationFailed(releaseInfo = null, reason = msg)
        }
        _lastPolicyBlockMessage.value = null
        return gitHubProvider.checkForUpdates(
            configuredRepositorySlug = configuredRepositorySlug,
            includePrereleases = includePrereleases,
            overrideSourceEvaluation = eval
        )
    }

    suspend fun checkPlayStoreUpdates(): PlayStoreUpdateState {
        playStoreTempDismissJob?.cancel()
        _lastPolicyBlockMessage.value = null
        return playStoreProvider.checkForPlayStoreUpdate()
    }

    suspend fun downloadAndVerifyGitHubUpdate(releaseInfo: GitHubReleaseInfo): UpdateCheckState {
        gitHubTempDismissJob?.cancel()
        val eval = _sourceEvaluation.value
        if (eval.blockGitHubApkOverwriteOfPlayInstall || eval.detectedChannel == InstallChannel.GOOGLE_PLAY) {
            val msg = "Blocked by UpdateSourcePolicy: PulsePlay was installed from Google Play Store. " +
                "A GitHub APK update cannot replace a Google Play installation."
            _lastPolicyBlockMessage.value = msg
            return UpdateCheckState.VerificationFailed(releaseInfo = releaseInfo, reason = msg)
        }
        return gitHubProvider.downloadAndVerifyUpdate(
            releaseInfo = releaseInfo,
            overrideSourceEvaluation = eval
        )
    }

    fun cancelGitHubDownload() {
        gitHubProvider.cancelActiveDownload()
    }

    fun installVerifiedGitHubUpdate(): Boolean {
        val eval = _sourceEvaluation.value
        if (eval.blockGitHubApkOverwriteOfPlayInstall || eval.detectedChannel == InstallChannel.GOOGLE_PLAY) {
            _lastPolicyBlockMessage.value =
                "Blocked by UpdateSourcePolicy: PulsePlay was installed from Google Play Store. A GitHub APK cannot replace a Play-installed build."
            return false
        }
        return gitHubProvider.triggerInstallOrPermissionFlow(overrideSourceEvaluation = eval)
    }

    fun startPlayFlexibleUpdate(activity: Activity): Boolean {
        return playStoreProvider.startFlexibleUpdateFlow(activity)
    }

    fun startPlayImmediateUpdate(activity: Activity): Boolean {
        return playStoreProvider.startImmediateUpdateFlow(activity)
    }

    suspend fun completePlayFlexibleUpdate(): Boolean {
        return playStoreProvider.completeFlexibleUpdate()
    }

    suspend fun onActivityResumed(activity: Activity? = null): PlayStoreUpdateState {
        return playStoreProvider.onActivityResumed(activity)
    }

    fun openOfficialPlayStoreListing(): Boolean {
        return playStoreProvider.openOfficialPlayStoreListing()
    }
}
