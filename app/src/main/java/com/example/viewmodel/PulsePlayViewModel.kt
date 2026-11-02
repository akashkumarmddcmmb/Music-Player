package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.example.data.local.MediaEntity
import com.example.data.local.MediaType
import com.example.data.local.PlaybackHistoryEntity
import com.example.data.local.PlaylistWithCount
import com.example.data.local.ProcessingJobEntity
import com.example.data.local.PulseDatabase
import com.example.data.preferences.HighResVideoPlaybackMode
import com.example.data.preferences.SortOption
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.UserPreferencesState
import com.example.data.preferences.VideoOrientationPreference
import com.example.data.repository.MediaLibraryRepository
import com.example.media.AdaptiveVideoEngineStrategy
import com.example.media.ConversionCapabilityRegistry
import com.example.media.ConversionProfile
import com.example.media.DeviceCapabilityDetector
import com.example.media.EqualizerController
import com.example.media.HardwareEqualizerState
import com.example.media.PersistedDeviceCapabilities
import com.example.media.MediaArtworkLoader
import com.example.media.MediaFileActionsHelper
import com.example.media.MediaProcessorEngine
import com.example.media.PlaybackModeAvailabilityReport
import com.example.media.PlayerHandoffAndVideoPolicy
import com.example.media.ProcessingOutcome
import com.example.media.Resilient4KCodecEngine
import com.example.media.SampleMediaGenerator
import com.example.media.SubtitleCueItem
import com.example.media.SubtitleFormat
import com.example.media.SubtitleLoadResult
import com.example.media.SubtitleParser
import com.example.media.VideoCompatibilityAction
import com.example.media.VideoStreamCapabilityEvaluation
import com.example.service.PulsePlaybackService
import com.example.ui.components.InAppNotification
import com.example.ui.components.InAppNotificationManager
import com.example.ui.components.InAppNotificationType
import com.example.ui.theme.AppThemeMode
import com.example.update.AppUpdateManager
import com.example.update.GitHubReleaseInfo
import com.example.update.PlayStoreUpdateState
import com.example.update.UpdateCheckState
import com.example.update.UpdateDistributionChannel
import com.example.update.UpdateManager
import com.example.update.UpdateSourceEvaluation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Arrays

enum class PrimaryTab {
    HOME,
    VIDEOS,
    MUSIC,
    LIBRARY,
    SETTINGS
}

enum class LibrarySubTab(val label: String) {
    ALL_SONGS("All Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    FOLDERS("Folders"),
    PLAYLISTS("Playlists"),
    FAVORITES("Favorites"),
    HISTORY("History"),
    TOOLS("Studio Tools")
}

enum class RepeatModeState {
    OFF,
    ONE,
    ALL
}

enum class VideoAspectMode(val label: String) {
    FIT("Fit Screen"),
    FILL("Fill (Zoom)"),
    ORIGINAL("Original"),
    SIXTEEN_NINE("16:9 Widescreen"),
    FOUR_THREE("4:3 Classic")
}

data class AudioTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val isSelected: Boolean
)

data class PlaybackUiState(
    val currentMedia: MediaEntity? = null,
    val queue: List<MediaEntity> = emptyList(),
    val originalQueue: List<MediaEntity> = emptyList(),
    val queueTitle: String = "Playing Queue",
    val currentQueueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeState = RepeatModeState.OFF,
    val videoAspectMode: VideoAspectMode = VideoAspectMode.FIT,
    val videoRotationDegrees: Int = 0,
    val isFullscreenVideo: Boolean = false,
    val isVideoControlsLocked: Boolean = false,
    val brightnessGestureLevel: Float = 0.75f, // 0.1..1.0
    val volumeGestureLevel: Float = 0.80f,     // 0.0..1.0
    val loadedSubtitleName: String? = null,
    val loadedSubtitleFormat: SubtitleFormat? = null,
    val loadedSubtitleCues: List<SubtitleCueItem> = emptyList(),
    val activeSubtitleText: String? = null,
    val subtitleError: String? = null,
    val availableAudioTracks: List<AudioTrackOption> = emptyList(),
    val sleepTimerTotalMinutes: Int = 0,
    val sleepTimerRemainingMs: Long = 0L,
    val sleepTimerStopAtEndOfTrack: Boolean = false,
    val verifiedDecoderName: String? = null,
    val isDecoderVerified: Boolean = false,
    val isDecoderHardwareAccelerated: Boolean = true,
    val isSoftwareFallbackDecoder: Boolean = false,
    val droppedVideoFrames: Int = 0,
    val decoderStatusLabel: String = "Decoder Unverified",
    val decodedVideoWidth: Int = 0,
    val decodedVideoHeight: Int = 0,
    val hasRenderedFirstFrame: Boolean = false,
    val hasVideoSurface: Boolean = false,
    val controllerConnected: Boolean = false,
    val playerGeneration: Int = 0,
    val decoderRecoveryAttempts: Int = 0,
    val playbackError: String? = null,
    val currentArtworkData: ByteArray? = null,
    val currentArtworkUri: String? = null,
    val currentArtworkSource: String = "none",
    val videoCapabilityEvaluation: VideoStreamCapabilityEvaluation? = null,
    val showVideoCompatibilityDialog: Boolean = false,
    val showVideoInfoDetailsDialog: Boolean = false,
    val activeVideoEngineStrategy: AdaptiveVideoEngineStrategy = AdaptiveVideoEngineStrategy.NORMAL_HARDWARE_PATH,
    val isTemporaryHighResEngineActive: Boolean = false,
    val isDownscaledPlayback: Boolean = false,
    val avSyncHealthy: Boolean = true,
    val showPlaybackSpeedDialog: Boolean = false,
    val showVideoSettingsDialog: Boolean = false,
    val showSubtitleSettingsDialog: Boolean = false,
    val showMusicPlaybackSettingsDialog: Boolean = false,
    val abRepeatStartMs: Long? = null,
    val abRepeatEndMs: Long? = null,
    val trackBookmarksMs: List<Long> = emptyList(),
    val playbackModeAvailabilities: List<PlaybackModeAvailabilityReport> = emptyList()
) {
    val isSleepTimerActive: Boolean
        get() = sleepTimerRemainingMs > 0L || sleepTimerStopAtEndOfTrack

    val isAbRepeatActive: Boolean
        get() = abRepeatStartMs != null && abRepeatEndMs != null && abRepeatEndMs > abRepeatStartMs

    val currentArtworkDataHash: Int
        get() = currentArtworkData?.let { Arrays.hashCode(it) } ?: 0
}

class PulsePlayViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PulseDatabase.getInstance(application)
    private val dao = database.pulseDao()
    private val sampleGenerator = SampleMediaGenerator(application)
    val mediaRepository = MediaLibraryRepository(application, dao, sampleGenerator)
    val preferencesRepository = UserPreferencesRepository(application)
    val processorEngine = MediaProcessorEngine(application, dao)
    val equalizerController = EqualizerController()
    val appUpdateManager = AppUpdateManager(application)
    val updateManager = UpdateManager(
        context = application,
        appUpdateManager = appUpdateManager,
        externalScope = viewModelScope
    )
    val updateState: StateFlow<UpdateCheckState> = updateManager.gitHubState
    val playStoreUpdateState: StateFlow<PlayStoreUpdateState> = updateManager.playStoreState
    val updateSourceEvaluation: StateFlow<UpdateSourceEvaluation> = updateManager.sourceEvaluation
    val activeUpdateChannel: StateFlow<UpdateDistributionChannel> = updateManager.activeChannel
    val updatePolicyBlockMessage: StateFlow<String?> = updateManager.lastPolicyBlockMessage

    // Navigation & Overlay States
    private val tabBackStack = ArrayDeque<PrimaryTab>()
    private val librarySubTabBackStack = ArrayDeque<LibrarySubTab>()

    private val _selectedTab = MutableStateFlow(PrimaryTab.HOME)
    val selectedTab: StateFlow<PrimaryTab> = _selectedTab.asStateFlow()

    private val _selectedLibrarySubTab = MutableStateFlow(LibrarySubTab.FOLDERS)
    val selectedLibrarySubTab: StateFlow<LibrarySubTab> = _selectedLibrarySubTab.asStateFlow()

    private val _selectedFolderFilter = MutableStateFlow<String?>(null)
    val selectedFolderFilter: StateFlow<String?> = _selectedFolderFilter.asStateFlow()

    private val _selectedAlbumFilter = MutableStateFlow<String?>(null)
    val selectedAlbumFilter: StateFlow<String?> = _selectedAlbumFilter.asStateFlow()

    private val _selectedArtistFilter = MutableStateFlow<String?>(null)
    val selectedArtistFilter: StateFlow<String?> = _selectedArtistFilter.asStateFlow()

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    private val _isNowPlayingExpanded = MutableStateFlow(false)
    val isNowPlayingExpanded: StateFlow<Boolean> = _isNowPlayingExpanded.asStateFlow()

    private val _isStudioSheetOpen = MutableStateFlow(false)
    val isStudioSheetOpen: StateFlow<Boolean> = _isStudioSheetOpen.asStateFlow()

    private val _studioTargetMedia = MutableStateFlow<MediaEntity?>(null)
    val studioTargetMedia: StateFlow<MediaEntity?> = _studioTargetMedia.asStateFlow()

    private val _isEqualizerSheetOpen = MutableStateFlow(false)
    val isEqualizerSheetOpen: StateFlow<Boolean> = _isEqualizerSheetOpen.asStateFlow()

    // Search & Permission States
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _hasMediaPermission = MutableStateFlow(mediaRepository.hasDeviceMediaAccess())
    val hasMediaPermission: StateFlow<Boolean> = _hasMediaPermission.asStateFlow()

    private val _permissionPermanentlyDenied = MutableStateFlow(false)
    val permissionPermanentlyDenied: StateFlow<Boolean> = _permissionPermanentlyDenied.asStateFlow()

    private val _isScanningLibrary = MutableStateFlow(false)
    val isScanningLibrary: StateFlow<Boolean> = _isScanningLibrary.asStateFlow()
    private var pendingRescanAfterCurrent: Boolean = false

    // Device Video Playback Capabilities
    private val _deviceCapabilities = MutableStateFlow(
        DeviceCapabilityDetector.getCachedCapabilities(application)
    )
    val deviceCapabilities: StateFlow<PersistedDeviceCapabilities> = _deviceCapabilities.asStateFlow()

    private val _isScanningCapabilities = MutableStateFlow(false)
    val isScanningCapabilities: StateFlow<Boolean> = _isScanningCapabilities.asStateFlow()

    val notificationManager = InAppNotificationManager(viewModelScope)
    val statusBannerNotification: StateFlow<InAppNotification?> = notificationManager.currentNotification
    val statusBannerMessage: StateFlow<String?> = notificationManager.currentMessage

    // Preferences & Equalizer State
    private val _userPreferences = MutableStateFlow(UserPreferencesState())
    val userPreferences: StateFlow<UserPreferencesState> = _userPreferences.asStateFlow()
    private val pendingPreferenceWrites = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile
    private var hasExplicitEqualizerMutation = false

    val equalizerState: StateFlow<HardwareEqualizerState> = equalizerController.state

    // Filtered & Sorted Media Flows
    val allMedia: StateFlow<List<MediaEntity>> = combine(
        mediaRepository.allMediaFlow,
        _searchQuery,
        userPreferences
    ) { items, query, prefs ->
        filterAndSortMedia(items, query, prefs.defaultSortOption)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioMedia: StateFlow<List<MediaEntity>> = combine(
        mediaRepository.audioMediaFlow,
        _searchQuery,
        userPreferences
    ) { items, query, prefs ->
        filterAndSortMedia(items, query, prefs.defaultSortOption)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoMedia: StateFlow<List<MediaEntity>> = combine(
        mediaRepository.videoMediaFlow,
        _searchQuery,
        userPreferences
    ) { items, query, prefs ->
        filterAndSortMedia(items, query, prefs.defaultSortOption)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteMedia: StateFlow<List<MediaEntity>> = mediaRepository.favoritesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteUriSet: StateFlow<Set<String>> = mediaRepository.favoriteEntitiesFlow
        .combine(_searchQuery) { favs, _ -> favs.map { it.uriString }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val playlists: StateFlow<List<PlaylistWithCount>> = mediaRepository.playlistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackHistory: StateFlow<List<PlaybackHistoryEntity>> = mediaRepository.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val processingJobs: StateFlow<List<ProcessingJobEntity>> = dao.observeProcessingJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversionProfiles: List<ConversionProfile> =
        ConversionCapabilityRegistry.getSupportedProfiles()

    // Single Managed Playback Engine (never create duplicate players)
    private var activePlayer: Player? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var fallbackLocalPlayer: ExoPlayer? = null
    private var progressPollingJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var activeProcessingJob: Job? = null
    private var activeUpdateJob: Job? = null
    private var artworkResolutionJob: Job? = null
    private var subtitleErrorAutoDismissJob: Job? = null
    private var lastHistoryWriteTimeMs: Long = 0L
    private val bookmarksByUri = mutableMapOf<String, List<Long>>()
    private val decoderRecoveryAttemptsByUri = object : LinkedHashMap<String, Int>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean = size > 64
    }

    private val _videoSurfacePlayerFlow = MutableStateFlow<Player?>(null)
    val videoSurfacePlayerFlow: StateFlow<Player?> = _videoSurfacePlayerFlow.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val currentUri = _playbackState.value.currentMedia?.uriString
                ?: activePlayer?.currentMediaItem?.mediaId
                ?: ""
            if (isPlaying && currentUri.isNotBlank()) {
                decoderRecoveryAttemptsByUri[currentUri] = 0
                Resilient4KCodecEngine.forceTryAlternativeDecoderOnNextInit = false
            }
            _playbackState.value = _playbackState.value.copy(
                isPlaying = isPlaying,
                isBuffering = if (isPlaying) false else _playbackState.value.isBuffering,
                decoderRecoveryAttempts = if (isPlaying) 0 else _playbackState.value.decoderRecoveryAttempts,
                playbackError = if (isPlaying) null else _playbackState.value.playbackError
            )
            val player = activePlayer
            equalizerController.updatePlaybackActive(
                isPlaying || (player != null && player.playbackState == Player.STATE_READY && player.mediaItemCount > 0)
            )
            if (isPlaying) {
                if (player != null) {
                    queryServiceAudioSessionIdIfController(player)
                }
                startProgressPolling()
            } else {
                stopProgressPolling()
                persistCurrentPlaybackPosition(force = true)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = activePlayer ?: return
            val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                ?: _playbackState.value.currentMedia?.durationMs ?: 0L
            val currentUri = _playbackState.value.currentMedia?.uriString
                ?: player.currentMediaItem?.mediaId
                ?: ""
            if (playbackState == Player.STATE_READY && currentUri.isNotBlank()) {
                decoderRecoveryAttemptsByUri[currentUri] = 0
                Resilient4KCodecEngine.forceTryAlternativeDecoderOnNextInit = false
            }
            val nowPlaying = player.isPlaying || (playbackState == Player.STATE_READY && player.playWhenReady)
            _playbackState.value = _playbackState.value.copy(
                durationMs = dur,
                currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                isPlaying = nowPlaying,
                isBuffering = playbackState == Player.STATE_BUFFERING,
                decoderRecoveryAttempts = if (playbackState == Player.STATE_READY) 0 else _playbackState.value.decoderRecoveryAttempts,
                playbackError = if (playbackState == Player.STATE_READY) null else _playbackState.value.playbackError
            )
            equalizerController.updatePlaybackActive(
                player.isPlaying || (playbackState == Player.STATE_READY && player.mediaItemCount > 0)
            )
            queryServiceAudioSessionIdIfController(player)
            if (playbackState == Player.STATE_READY) {
                onPlayerMediaMetadataUpdated(player.mediaMetadata)
            }

            if (playbackState == Player.STATE_ENDED) {
                if (_playbackState.value.currentMedia?.mediaType == MediaType.VIDEO) {
                    clearPlayerVideoSizeConstraintAndReleaseTempSession("Video playback completed (STATE_ENDED)")
                }
                if (_playbackState.value.sleepTimerStopAtEndOfTrack) {
                    triggerSleepTimerAutoStop("Sleep Timer: Stopped automatically at end of track.")
                }
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            onPlayerMediaMetadataUpdated(mediaMetadata)
        }

        override fun onRenderedFirstFrame() {
            val currentUri = _playbackState.value.currentMedia?.uriString
                ?: activePlayer?.currentMediaItem?.mediaId
                ?: ""
            if (currentUri.isNotBlank()) {
                decoderRecoveryAttemptsByUri[currentUri] = 0
            }
            Resilient4KCodecEngine.forceTryAlternativeDecoderOnNextInit = false
            _playbackState.value = _playbackState.value.copy(
                hasRenderedFirstFrame = true,
                isBuffering = false,
                decoderRecoveryAttempts = 0,
                playbackError = null
            )
        }

        @OptIn(UnstableApi::class)
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId > 0) {
                viewModelScope.launch {
                    val prefs = userPreferences.first()
                    equalizerController.setStereoBalance(prefs.audioBalance)
                    equalizerController.setPreampGainDb(prefs.preampGainDb)
                    equalizerController.bindToAudioSession(
                        audioSessionId = audioSessionId,
                        enabled = prefs.equalizerEnabled,
                        presetIndex = prefs.equalizerPresetIndex,
                        bassStrength = prefs.bassBoostStrength,
                        savedBandLevelsMb = prefs.customBandLevelsMb,
                        isPlaybackActive = activePlayer?.isPlaying == true || (activePlayer?.mediaItemCount ?: 0) > 0
                    )
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // If Sleep Timer "Stop at End of Track" is armed and track finished automatically, stop now
            if (_playbackState.value.sleepTimerStopAtEndOfTrack &&
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            ) {
                triggerSleepTimerAutoStop("Sleep Timer: Stopped automatically at end of track.")
                return
            }

            // Release any temporary high-res decoder state from the outgoing item before transitioning
            clearPlayerVideoSizeConstraintAndReleaseTempSession("Media item transition")

            val idx = activePlayer?.currentMediaItemIndex ?: -1
            val queue = _playbackState.value.queue
            val mediaId = mediaItem?.mediaId
            val matchedFromQueue = if (!mediaId.isNullOrBlank()) {
                queue.find { it.uriString == mediaId }
            } else null
            val matchedFromLibrary = if (!mediaId.isNullOrBlank() && matchedFromQueue == null) {
                allMedia.value.find { it.uriString == mediaId }
            } else null
            val nextEntity = matchedFromQueue ?: matchedFromLibrary ?: queue.getOrNull(idx) ?: _playbackState.value.currentMedia
            val actualQueueIdx = if (nextEntity != null) {
                val qPos = queue.indexOfFirst { it.uriString == nextEntity.uriString }
                if (qPos >= 0) qPos else idx
            } else idx

            val app = getApplication<Application>()
            val nextVideoEval = if (nextEntity?.mediaType == MediaType.VIDEO) {
                val eval = Resilient4KCodecEngine.evaluateVideoStreamCapability(
                    context = app,
                    media = nextEntity,
                    selectedMode = userPreferences.value.highResVideoPlaybackMode
                )
                Resilient4KCodecEngine.resetTelemetryForNewTrack(
                    mimeType = nextEntity.mimeType,
                    width = nextEntity.width,
                    height = nextEntity.height,
                    frameRate = 30f,
                    bitrateBps = eval.bitrateBps,
                    isHdrOr10Bit = eval.is10BitOrHdr,
                    trackUri = nextEntity.uriString
                )
                eval
            } else {
                null
            }

            _playbackState.value = _playbackState.value.copy(
                currentMedia = nextEntity,
                currentQueueIndex = actualQueueIdx,
                hasRenderedFirstFrame = false,
                playbackError = null,
                videoCapabilityEvaluation = nextVideoEval,
                showVideoCompatibilityDialog = false,
                showVideoInfoDetailsDialog = false,
                abRepeatStartMs = null,
                abRepeatEndMs = null,
                trackBookmarksMs = if (nextEntity != null) bookmarksByUri[nextEntity.uriString].orEmpty() else emptyList(),
                activeVideoEngineStrategy = nextVideoEval?.recommendedStrategy
                    ?: AdaptiveVideoEngineStrategy.NORMAL_HARDWARE_PATH,
                isTemporaryHighResEngineActive = nextVideoEval?.resolutionTier?.isHighRes4kOrAbove == true,
                isDownscaledPlayback = false
            )
            // Synchronize artwork for the transitioned track using the item's own metadata (never stale previous track metadata)
            syncAndResolveArtworkForTrack(
                media = nextEntity,
                playerMediaMetadata = mediaItem?.mediaMetadata
            )
            resetOrLoadCompanionSubtitleForMedia(nextEntity)
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateAvailableAudioTracks(tracks)
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlayerError(error)
        }
    }

    /**
     * Updates [_playbackState] when ExoPlayer or [PulsePlaybackService] emits updated [MediaMetadata]
     * (such as embedded ID3 `APIC` or MP4 `covr` `artworkData` / `artworkUri`) for the active track.
     */
    fun onPlayerMediaMetadataUpdated(mediaMetadata: MediaMetadata) {
        val current = _playbackState.value.currentMedia ?: return
        val activeMediaId = activePlayer?.currentMediaItem?.mediaId
        if (activeMediaId != null && activeMediaId != current.uriString) {
            return
        }
        val metaTitle = mediaMetadata.title?.toString()?.trim()
        if (!metaTitle.isNullOrBlank() &&
            !metaTitle.equals(current.title.trim(), ignoreCase = true)
        ) {
            return
        }
        val bytes = mediaMetadata.artworkData?.takeIf { it.isNotEmpty() }
        val uri = mediaMetadata.artworkUri?.toString()?.takeIf { it.isNotBlank() }
        if (bytes != null || uri != null) {
            MediaArtworkLoader.cacheLiveMediaMetadataArtwork(current.uriString, mediaMetadata)
            val state = _playbackState.value
            if (state.currentMedia?.uriString == current.uriString) {
                val nextSource = when {
                    bytes != null -> "MediaMetadata.artworkData"
                    uri != null -> "MediaMetadata.artworkUri"
                    else -> state.currentArtworkSource
                }
                if (!(bytes contentEquals state.currentArtworkData) ||
                    uri != state.currentArtworkUri ||
                    nextSource != state.currentArtworkSource
                ) {
                    _playbackState.value = state.copy(
                        currentArtworkData = bytes,
                        currentArtworkUri = uri,
                        currentArtworkSource = nextSource
                    )
                }
            }
        }
    }

    /**
     * Synchronously resets or applies cached artwork for [media] so stale artwork from the
     * previous track never remains visible on Next/Previous/Queue changes, then asynchronously
     * resolves embedded/MediaStore/MediaMetadata artwork on [Dispatchers.IO].
     */
    private fun syncAndResolveArtworkForTrack(
        media: MediaEntity?,
        playerMediaMetadata: MediaMetadata? = null
    ) {
        artworkResolutionJob?.cancel()
        if (media == null) {
            _playbackState.value = _playbackState.value.copy(
                currentArtworkData = null,
                currentArtworkUri = null,
                currentArtworkSource = "none"
            )
            return
        }

        val liveBytes = playerMediaMetadata?.artworkData?.takeIf { it.isNotEmpty() }
        val liveUri = playerMediaMetadata?.artworkUri?.toString()?.takeIf { it.isNotBlank() }
        val cached = MediaArtworkLoader.peekCachedArtworkMetadata(media.uriString)

        val immediateBytes = liveBytes ?: cached?.artworkData
        val immediateUri = liveUri ?: cached?.artworkUri?.toString()
        val immediateSource = when {
            liveBytes != null -> "MediaMetadata.artworkData"
            liveUri != null -> "MediaMetadata.artworkUri"
            cached != null && cached.hasArtwork -> cached.sourceLabel
            else -> "none"
        }

        _playbackState.value = _playbackState.value.copy(
            currentArtworkData = immediateBytes,
            currentArtworkUri = immediateUri,
            currentArtworkSource = immediateSource
        )

        if (liveBytes != null || liveUri != null) {
            MediaArtworkLoader.cacheLiveMediaMetadataArtwork(media.uriString, playerMediaMetadata)
        }

        val app = getApplication<Application>()
        artworkResolutionJob = viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                MediaArtworkLoader.resolveArtworkMetadata(
                    context = app,
                    media = media,
                    playerMetadata = playerMediaMetadata
                )
            }
            if (_playbackState.value.currentMedia?.uriString == media.uriString) {
                val state = _playbackState.value
                val bestBytes = resolved.artworkData
                val bestUri = resolved.artworkUri?.toString()
                val bestSource = if (resolved.hasArtwork) resolved.sourceLabel else "none"
                if (!(bestBytes contentEquals state.currentArtworkData) ||
                    bestUri != state.currentArtworkUri ||
                    bestSource != state.currentArtworkSource
                ) {
                    _playbackState.value = state.copy(
                        currentArtworkData = bestBytes,
                        currentArtworkUri = bestUri,
                        currentArtworkSource = bestSource
                    )
                }
            }
        }
    }

    /**
     * Handles [PlaybackException] with bounded decoder recovery for recoverable codec/surface errors,
     * never prematurely marking `isPlaying = true` before the player actually resumes (`STATE_READY` / `onIsPlayingChanged`).
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun handlePlayerError(error: PlaybackException) {
        val isDecoderFailure =
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED

        val currentMedia = _playbackState.value.currentMedia
        val currentUri = currentMedia?.uriString
            ?: activePlayer?.currentMediaItem?.mediaId
            ?: ""
        val attemptsSoFar = decoderRecoveryAttemptsByUri[currentUri] ?: 0
        val player = activePlayer
        val savedIndex = (player?.currentMediaItemIndex?.takeIf { it >= 0 }
            ?: _playbackState.value.currentQueueIndex).coerceAtLeast(0)
        val savedPos = (player?.currentPosition?.takeIf { it > 0L }
            ?: _playbackState.value.currentPositionMs).coerceAtLeast(0L)

        // Immediately reflect stopped/error state before any recovery completes
        stopProgressPolling()

        if (isDecoderFailure &&
            currentUri.isNotBlank() &&
            attemptsSoFar < Resilient4KCodecEngine.MAX_DECODER_RECOVERY_RETRIES
        ) {
            val nextAttempt = attemptsSoFar + 1
            decoderRecoveryAttemptsByUri[currentUri] = nextAttempt
            Resilient4KCodecEngine.forceTryAlternativeDecoderOnNextInit = true

            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                isBuffering = true,
                currentMedia = currentMedia,
                currentQueueIndex = savedIndex,
                currentPositionMs = savedPos,
                decoderRecoveryAttempts = nextAttempt,
                decoderStatusLabel = "Recovering Decoder ($nextAttempt/${Resilient4KCodecEngine.MAX_DECODER_RECOVERY_RETRIES})",
                playbackError = null
            )

            if (player != null) {
                viewModelScope.launch {
                    delay(120L)
                    runCatching {
                        player.stop()
                        player.seekTo(savedIndex, savedPos)
                        player.prepare()
                        player.playWhenReady = true
                        player.play()
                        // Note: Do NOT set isPlaying = true here!
                        // isPlaying will only become true when onPlaybackStateChanged(STATE_READY)
                        // or onIsPlayingChanged(true) confirms recovery succeeded.
                    }.onFailure {
                        Resilient4KCodecEngine.forceTryAlternativeDecoderOnNextInit = false
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying = false,
                            isBuffering = false,
                            playbackError = buildDetailedPlaybackDiagnosticError(error, nextAttempt)
                        )
                    }
                }
            }
            return
        }

        // Recovery not applicable (non-decoder error) or bounded retries exhausted
        Resilient4KCodecEngine.forceTryAlternativeDecoderOnNextInit = false
        val diagnosticMsg = buildDetailedPlaybackDiagnosticError(error, attemptsSoFar)
        val shouldOfferCompatibilityPanel = isDecoderFailure && currentMedia?.mediaType == MediaType.VIDEO
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            isBuffering = false,
            currentMedia = currentMedia,
            currentQueueIndex = savedIndex,
            currentPositionMs = savedPos,
            isDecoderVerified = false,
            decoderRecoveryAttempts = attemptsSoFar,
            decoderStatusLabel = if (isDecoderFailure) "Decoder Init Failed" else "Playback Error",
            playbackError = diagnosticMsg,
            showVideoCompatibilityDialog = shouldOfferCompatibilityPanel || _playbackState.value.showVideoCompatibilityDialog
        )
    }

    private fun buildDetailedPlaybackDiagnosticError(
        error: PlaybackException,
        recoveryAttempts: Int
    ): String {
        return "Unable to play this video on your device. Try another video format or resolution."
    }

    fun onVideoSurfaceAttached(attached: Boolean) {
        if (_playbackState.value.hasVideoSurface != attached) {
            _playbackState.value = _playbackState.value.copy(hasVideoSurface = attached)
        }
    }

    fun onRenderedFirstFrame() {
        playerListener.onRenderedFirstFrame()
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val detected = DeviceCapabilityDetector.detectAndCacheDeviceCapabilities(getApplication())
            _deviceCapabilities.value = detected
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                processorEngine.cleanupOrphanedTempFiles()
            }
            dao.recoverInterruptedJobsOnStartup()
            refreshLibrary()
        }
        viewModelScope.launch {
            PulsePlaybackService.serviceExoPlayerFlow.collect { serviceExo ->
                val resolvedSurfacePlayer = serviceExo ?: activePlayer
                _videoSurfacePlayerFlow.value = resolvedSurfacePlayer
                _playbackState.value = _playbackState.value.copy(
                    controllerConnected = serviceExo != null || activePlayer is MediaController,
                    playerGeneration = _playbackState.value.playerGeneration + 1
                )
            }
        }
        val initialAvailabilities = Resilient4KCodecEngine.evaluateAllPlaybackModesAvailability(getApplication())
        _playbackState.value = _playbackState.value.copy(
            playbackModeAvailabilities = initialAvailabilities
        )
        viewModelScope.launch {
            val initialPrefs = preferencesRepository.preferencesFlow.first()
            initialPrefs.selectedUpdateChannel?.let { savedChannel ->
                updateManager.selectChannel(savedChannel)
            }
            Resilient4KCodecEngine.updateConfiguredPlaybackMode(
                mode = initialPrefs.highResVideoPlaybackMode,
                legacyAllowSoftwareFallback = initialPrefs.allowExperimental4kSoftwareFallback,
                hardwareDecoderPreference = initialPrefs.hardwareDecoderPreferenceEnabled
            )
            if (!hasExplicitEqualizerMutation) {
                equalizerController.setStereoBalance(initialPrefs.audioBalance)
                equalizerController.setPreampGainDb(initialPrefs.preampGainDb)
                equalizerController.bindToAudioSession(
                    audioSessionId = PulsePlaybackService.serviceAudioSessionId.value,
                    enabled = initialPrefs.equalizerEnabled,
                    presetIndex = initialPrefs.equalizerPresetIndex,
                    bassStrength = initialPrefs.bassBoostStrength,
                    savedBandLevelsMb = initialPrefs.customBandLevelsMb,
                    isPlaybackActive = PulsePlaybackService.serviceIsPlaying.value
                )
            }

            PulsePlaybackService.serviceAudioSessionId.collect { serviceSessionId ->
                val prefs = preferencesRepository.preferencesFlow.first()
                Resilient4KCodecEngine.updateConfiguredPlaybackMode(
                    mode = prefs.highResVideoPlaybackMode,
                    legacyAllowSoftwareFallback = prefs.allowExperimental4kSoftwareFallback,
                    hardwareDecoderPreference = prefs.hardwareDecoderPreferenceEnabled
                )
                if (!hasExplicitEqualizerMutation) {
                    equalizerController.setStereoBalance(prefs.audioBalance)
                    equalizerController.setPreampGainDb(prefs.preampGainDb)
                }
                val currentSession = equalizerController.state.value
                val effectiveEnabled = if (hasExplicitEqualizerMutation) currentSession.isEnabled else prefs.equalizerEnabled
                val effectivePreset = if (hasExplicitEqualizerMutation) currentSession.selectedPresetIndex else prefs.equalizerPresetIndex
                val effectiveBass = if (hasExplicitEqualizerMutation) currentSession.bassBoostStrength else prefs.bassBoostStrength
                val effectiveBands = if (hasExplicitEqualizerMutation || currentSession.bands.any { it.currentLevelMb != 0 }) {
                    currentSession.bands.map { it.currentLevelMb }
                } else {
                    prefs.customBandLevelsMb
                }
                val resolvedId = if (serviceSessionId > 0) {
                    serviceSessionId
                } else {
                    resolveActiveAudioSessionId(activePlayer)
                }
                equalizerController.bindToAudioSession(
                    audioSessionId = resolvedId,
                    enabled = effectiveEnabled,
                    presetIndex = effectivePreset,
                    bassStrength = effectiveBass,
                    savedBandLevelsMb = effectiveBands,
                    isPlaybackActive = activePlayer?.isPlaying == true || PulsePlaybackService.serviceIsPlaying.value
                )
            }
        }
        viewModelScope.launch {
            Resilient4KCodecEngine.latestTelemetry.collect { telemetry ->
                val currentMediaUri = _playbackState.value.currentMedia?.uriString
                if (telemetry.trackUri.isNotBlank() &&
                    !currentMediaUri.isNullOrBlank() &&
                    telemetry.trackUri != currentMediaUri
                ) {
                    // Ignore stale telemetry from an older video session
                    return@collect
                }
                _playbackState.value = _playbackState.value.copy(
                    verifiedDecoderName = telemetry.selectedDecoderName,
                    isDecoderVerified = telemetry.isDecoderVerified,
                    isDecoderHardwareAccelerated = telemetry.isHardwareAccelerated,
                    isSoftwareFallbackDecoder = telemetry.isSoftwareFallbackActive,
                    droppedVideoFrames = telemetry.droppedVideoFrames,
                    decoderStatusLabel = telemetry.diagnosticSummary,
                    decodedVideoWidth = telemetry.sourceWidth,
                    decodedVideoHeight = telemetry.sourceHeight,
                    activeVideoEngineStrategy = telemetry.activeEngineStrategy,
                    isTemporaryHighResEngineActive = telemetry.isTemporaryHighResSessionActive,
                    isDownscaledPlayback = telemetry.isDownscaledOrLowerResActive,
                    avSyncHealthy = telemetry.avSyncHealthy
                )
            }
        }
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                if (pendingPreferenceWrites.get() == 0) {
                    _userPreferences.value = prefs
                }
            }
        }
        viewModelScope.launch {
            PulsePlaybackService.serviceMediaMetadata.collect { serviceMeta ->
                onPlayerMediaMetadataUpdated(serviceMeta)
            }
        }
        initializeSingleManagedPlayer()
    }

    @OptIn(UnstableApi::class)
    private fun resolveActiveAudioSessionId(player: Player?): Int {
        val serviceId = PulsePlaybackService.serviceAudioSessionId.value
        if (serviceId > 0) return serviceId
        if (player is ExoPlayer) {
            return player.audioSessionId.coerceAtLeast(0)
        }
        return equalizerController.state.value.activeAudioSessionId.coerceAtLeast(0)
    }

    private fun queryServiceAudioSessionIdIfController(player: Player) {
        if (player !is MediaController) return
        val app = getApplication<Application>()
        runCatching {
            val cmd = SessionCommand(PulsePlaybackService.ACTION_GET_AUDIO_SESSION_ID, Bundle.EMPTY)
            val future = player.sendCustomCommand(cmd, Bundle.EMPTY)
            future.addListener(
                {
                    runCatching {
                        val result = future.get()
                        val sessionId = result.extras.getInt(PulsePlaybackService.EXTRA_AUDIO_SESSION_ID, 0)
                        if (sessionId > 0 && equalizerController.state.value.activeAudioSessionId != sessionId) {
                            viewModelScope.launch {
                                val prefs = preferencesRepository.preferencesFlow.first()
                                equalizerController.setStereoBalance(prefs.audioBalance)
                                equalizerController.bindToAudioSession(
                                    audioSessionId = sessionId,
                                    enabled = prefs.equalizerEnabled,
                                    presetIndex = prefs.equalizerPresetIndex,
                                    bassStrength = prefs.bassBoostStrength,
                                    savedBandLevelsMb = prefs.customBandLevelsMb,
                                    isPlaybackActive = player.isPlaying || player.mediaItemCount > 0
                                )
                            }
                        }
                    }
                },
                ContextCompat.getMainExecutor(app)
            )
        }
    }

    @OptIn(UnstableApi::class)
    private fun initializeSingleManagedPlayer() {
        if (android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
            ensureFallbackLocalExoPlayer()
            return
        }
        val app = getApplication<Application>()
        runCatching {
            val sessionToken = SessionToken(
                app,
                ComponentName(app, PulsePlaybackService::class.java)
            )
            val controllerFuture = MediaController.Builder(app, sessionToken).buildAsync()
            mediaControllerFuture = controllerFuture
            controllerFuture.addListener(
                {
                    runCatching {
                        val controller = controllerFuture.get()
                        val sessionId = PulsePlaybackService.serviceAudioSessionId.value
                        attachManagedPlayer(controller, audioSessionId = sessionId)
                        queryServiceAudioSessionIdIfController(controller)
                    }.onFailure {
                        ensureFallbackLocalExoPlayer()
                    }
                },
                ContextCompat.getMainExecutor(app)
            )
        }.onFailure {
            ensureFallbackLocalExoPlayer()
        }
    }

    @OptIn(UnstableApi::class)
    private fun ensureFallbackLocalExoPlayer() {
        if (activePlayer != null) return
        val app = getApplication<Application>()
        val exo = PulsePlaybackService.buildExoPlayerWithEqualizerDsp(
            context = app,
            audioProcessor = equalizerController.audioProcessor
        )
        fallbackLocalPlayer = exo
        attachManagedPlayer(exo, audioSessionId = exo.audioSessionId)
    }

    @OptIn(UnstableApi::class)
    @Synchronized
    private fun attachManagedPlayer(player: Player, audioSessionId: Int) {
        val previousFallback = fallbackLocalPlayer
        activePlayer?.removeListener(playerListener)

        val snapshot = PlayerHandoffAndVideoPolicy.handoffFallbackToAuthoritativePlayer(
            outgoingFallback = previousFallback,
            incomingTarget = player
        )
        if (previousFallback != null && previousFallback !== player) {
            fallbackLocalPlayer = null
        }

        activePlayer = player
        player.addListener(playerListener)
        _videoSurfacePlayerFlow.value = PulsePlaybackService.getAuthoritativeExoPlayerOrNull() ?: player
        _playbackState.value = _playbackState.value.copy(
            controllerConnected = player is MediaController || PulsePlaybackService.getAuthoritativeExoPlayerOrNull() != null,
            playerGeneration = _playbackState.value.playerGeneration + 1
        )

        viewModelScope.launch {
            val prefs = preferencesRepository.preferencesFlow.first()
            equalizerController.setStereoBalance(prefs.audioBalance)
            equalizerController.setPreampGainDb(prefs.preampGainDb)
            if (snapshot.mediaItems.isEmpty()) {
                if (player.mediaItemCount > 0) {
                    val currentAll = allMedia.value
                    val liveItems = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
                    val reconstructedQueue = liveItems.mapNotNull { item ->
                        currentAll.find { it.uriString == item.mediaId }
                    }
                    val currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
                    val activeEntity = reconstructedQueue.getOrNull(currentIndex)
                        ?: player.currentMediaItem?.mediaId?.let { id -> currentAll.find { it.uriString == id } }
                        ?: _playbackState.value.currentMedia
                    if (activeEntity != null) {
                        val repeatState = when (player.repeatMode) {
                            Player.REPEAT_MODE_ONE -> RepeatModeState.ONE
                            Player.REPEAT_MODE_ALL -> RepeatModeState.ALL
                            else -> RepeatModeState.OFF
                        }
                        _playbackState.value = _playbackState.value.copy(
                            currentMedia = activeEntity,
                            queue = if (reconstructedQueue.isNotEmpty()) reconstructedQueue else _playbackState.value.queue,
                            currentQueueIndex = currentIndex,
                            isPlaying = player.isPlaying || player.playWhenReady,
                            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: activeEntity.durationMs,
                            playbackSpeed = player.playbackParameters.speed,
                            shuffleEnabled = player.shuffleModeEnabled,
                            repeatMode = repeatState
                        )
                        syncAndResolveArtworkForTrack(activeEntity, player.mediaMetadata)
                        if (player.isPlaying || player.playWhenReady) {
                            startProgressPolling()
                        }
                    }
                } else {
                    player.setPlaybackSpeed(prefs.defaultPlaybackSpeed)
                }
            } else {
                val currentAll = allMedia.value
                val reconstructedQueue = snapshot.mediaItems.mapNotNull { item ->
                    currentAll.find { it.uriString == item.mediaId }
                }
                val activeEntity = reconstructedQueue.getOrNull(snapshot.currentMediaItemIndex)
                    ?: _playbackState.value.currentMedia
                if (activeEntity != null) {
                    val repeatState = when (snapshot.repeatMode) {
                        Player.REPEAT_MODE_ONE -> RepeatModeState.ONE
                        Player.REPEAT_MODE_ALL -> RepeatModeState.ALL
                        else -> RepeatModeState.OFF
                    }
                    _playbackState.value = _playbackState.value.copy(
                        currentMedia = activeEntity,
                        queue = if (reconstructedQueue.isNotEmpty()) reconstructedQueue else _playbackState.value.queue,
                        currentQueueIndex = snapshot.currentMediaItemIndex,
                        isPlaying = snapshot.isPlaying || snapshot.playWhenReady,
                        currentPositionMs = snapshot.currentPositionMs,
                        durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: activeEntity.durationMs,
                        playbackSpeed = snapshot.playbackSpeed,
                        shuffleEnabled = snapshot.shuffleModeEnabled,
                        repeatMode = repeatState
                    )
                    syncAndResolveArtworkForTrack(activeEntity, player.mediaMetadata)
                    if (snapshot.isPlaying || snapshot.playWhenReady) {
                        startProgressPolling()
                    }
                }
            }

            val resolvedSessionId = if (audioSessionId > 0) {
                audioSessionId
            } else {
                resolveActiveAudioSessionId(player)
            }
            equalizerController.bindToAudioSession(
                audioSessionId = resolvedSessionId,
                enabled = prefs.equalizerEnabled,
                presetIndex = prefs.equalizerPresetIndex,
                bassStrength = prefs.bassBoostStrength,
                savedBandLevelsMb = prefs.customBandLevelsMb,
                isPlaybackActive = player.isPlaying || player.playWhenReady || player.mediaItemCount > 0
            )
            queryServiceAudioSessionIdIfController(player)
        }
    }

    fun getPlayerInstanceForVideoSurface(): Player? {
        val authoritativeServicePlayer = PulsePlaybackService.getAuthoritativeExoPlayerOrNull()
        if (authoritativeServicePlayer != null) {
            if (_videoSurfacePlayerFlow.value !== authoritativeServicePlayer) {
                _videoSurfacePlayerFlow.value = authoritativeServicePlayer
            }
            return authoritativeServicePlayer
        }
        if (activePlayer == null) {
            ensureFallbackLocalExoPlayer()
        }
        if (_videoSurfacePlayerFlow.value !== activePlayer) {
            _videoSurfacePlayerFlow.value = activePlayer
        }
        return activePlayer
    }

    // --- Navigation & UI Actions ---
    fun selectPrimaryTab(tab: PrimaryTab) {
        val current = _selectedTab.value
        if (current == tab) return
        if (tab == PrimaryTab.HOME) {
            tabBackStack.clear()
        } else {
            tabBackStack.remove(tab)
            tabBackStack.addLast(current)
        }
        _selectedTab.value = tab
    }

    fun popPrimaryTab(): Boolean {
        if (tabBackStack.isNotEmpty()) {
            val prev = tabBackStack.removeLast()
            _selectedTab.value = prev
            return true
        }
        if (_selectedTab.value != PrimaryTab.HOME) {
            _selectedTab.value = PrimaryTab.HOME
            return true
        }
        return false
    }

    fun canPopPrimaryTab(): Boolean {
        return tabBackStack.isNotEmpty() || _selectedTab.value != PrimaryTab.HOME
    }

    fun selectLibrarySubTab(subTab: LibrarySubTab) {
        val current = _selectedLibrarySubTab.value
        if (current == subTab) return
        if (subTab == LibrarySubTab.FOLDERS) {
            librarySubTabBackStack.clear()
        } else {
            librarySubTabBackStack.remove(subTab)
            librarySubTabBackStack.addLast(current)
        }
        _selectedLibrarySubTab.value = subTab
    }

    fun popLibrarySubTab(): Boolean {
        if (librarySubTabBackStack.isNotEmpty()) {
            val prev = librarySubTabBackStack.removeLast()
            _selectedLibrarySubTab.value = prev
            return true
        }
        if (_selectedLibrarySubTab.value != LibrarySubTab.FOLDERS) {
            _selectedLibrarySubTab.value = LibrarySubTab.FOLDERS
            return true
        }
        return false
    }

    fun canPopLibrarySubTab(): Boolean {
        return librarySubTabBackStack.isNotEmpty() || _selectedLibrarySubTab.value != LibrarySubTab.FOLDERS
    }

    fun selectFolderFilter(folderName: String?) {
        _selectedFolderFilter.value = folderName
    }

    fun selectAlbumFilter(albumName: String?) {
        _selectedAlbumFilter.value = albumName
    }

    fun selectArtistFilter(artistName: String?) {
        _selectedArtistFilter.value = artistName
    }

    fun selectPlaylistDetail(playlistId: Long?) {
        _selectedPlaylistId.value = playlistId
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _isNowPlayingExpanded.value = expanded
        if (expanded && activePlayer?.isPlaying == true && isAppInForeground) {
            startProgressPolling()
        } else if (!expanded) {
            val current = _playbackState.value
            if (current.isFullscreenVideo) {
                _playbackState.value = current.copy(isFullscreenVideo = false)
            }
            if (current.currentMedia?.mediaType == MediaType.VIDEO &&
                (!current.isPlaying || current.playbackError != null)
            ) {
                clearPlayerVideoSizeConstraintAndReleaseTempSession("Closed Now Playing video screen")
            }
        }
    }

    /**
     * Releases any temporary high-resolution / compatibility decoder state and clears any
     * temporary lower-resolution track selection constraints so subsequent playback returns
     * to the standard hardware path.
     */
    fun clearPlayerVideoSizeConstraintAndReleaseTempSession(reason: String) {
        Resilient4KCodecEngine.releaseTemporaryHighResSession(reason)
        activePlayer?.let { player ->
            runCatching {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearVideoSizeConstraints()
                    .build()
            }
        }
        _playbackState.value = _playbackState.value.copy(
            activeVideoEngineStrategy = AdaptiveVideoEngineStrategy.NORMAL_HARDWARE_PATH,
            isTemporaryHighResEngineActive = false,
            isDownscaledPlayback = false,
            showVideoCompatibilityDialog = false
        )
    }

    /**
     * Stops active video playback (e.g., on user Back/Stop from an errored or high-res video)
     * and immediately releases temporary high-resolution decoder state.
     */
    fun stopVideoPlaybackAndCleanup(reason: String = "User stopped video playback") {
        stopProgressPolling()
        activePlayer?.let { player ->
            runCatching {
                player.playWhenReady = false
                player.stop()
            }
        }
        clearPlayerVideoSizeConstraintAndReleaseTempSession(reason)
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            isBuffering = false
        )
    }

    fun openStudioForMedia(media: MediaEntity?) {
        val target = media ?: _playbackState.value.currentMedia ?: allMedia.value.firstOrNull()
        _studioTargetMedia.value = target
        _isStudioSheetOpen.value = true
    }

    fun closeStudioSheet() {
        _isStudioSheetOpen.value = false
    }

    fun setEqualizerSheetOpen(open: Boolean) {
        _isEqualizerSheetOpen.value = open
        if (open) {
            _playbackState.value = _playbackState.value.copy(
                showPlaybackSpeedDialog = false,
                showVideoSettingsDialog = false,
                showSubtitleSettingsDialog = false,
                showMusicPlaybackSettingsDialog = false,
                showVideoCompatibilityDialog = false,
                showVideoInfoDetailsDialog = false
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun postStatusBannerMessage(
        message: String,
        type: InAppNotificationType = InAppNotificationManager.classifyMessage(message),
        durationMs: Long = notificationManager.defaultAutoDismissDurationMs
    ) {
        notificationManager.show(
            message = message,
            type = type,
            durationMs = durationMs
        )
    }

    fun dismissStatusBanner() {
        notificationManager.dismiss()
    }

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean = false) {
        val effectiveGranted = granted || mediaRepository.hasDeviceMediaAccess()
        _hasMediaPermission.value = effectiveGranted
        _permissionPermanentlyDenied.value = !effectiveGranted && permanentlyDenied
        refreshLibrary()
    }

    fun refreshLibrary(showStatusFeedback: Boolean = false) {
        if (_isScanningLibrary.value) {
            pendingRescanAfterCurrent = true
            return
        }
        viewModelScope.launch {
            _isScanningLibrary.value = true
            try {
                do {
                    pendingRescanAfterCurrent = false
                    val hasAccess = _hasMediaPermission.value || mediaRepository.hasDeviceMediaAccess()
                    if (hasAccess && !_hasMediaPermission.value) {
                        _hasMediaPermission.value = true
                    }
                    val count = mediaRepository.scanAndSyncMediaLibrary(
                        includeDeviceMediaStore = hasAccess
                    )
                    if (count > 0 && _studioTargetMedia.value == null) {
                        _studioTargetMedia.value = allMedia.value.firstOrNull()
                    }
                    if (showStatusFeedback) {
                        postStatusBannerMessage(
                            "Scanned $count audio & video files across all folders.",
                            InAppNotificationType.TEMPORARY_SUCCESS
                        )
                    }
                } while (pendingRescanAfterCurrent)
            } finally {
                _isScanningLibrary.value = false
            }
        }
    }

    fun generateBuiltInSamplesExplicitly() {
        viewModelScope.launch {
            _isScanningLibrary.value = true
            try {
                val generated = sampleGenerator.ensureLegalSampleMediaGenerated()
                dao.upsertMediaItems(generated)
                postStatusBannerMessage(
                    "Generated & verified ${generated.size} legal studio audio/video/subtitle fixtures.",
                    InAppNotificationType.TEMPORARY_SUCCESS
                )
            } finally {
                _isScanningLibrary.value = false
            }
        }
    }

    fun importSafUri(uri: Uri) {
        viewModelScope.launch {
            when (val result = mediaRepository.importSafMediaUriDetailed(uri)) {
                is MediaLibraryRepository.SafImportResult.Success -> {
                    val entity = result.entity
                    val typeLabel = if (entity.mediaType == MediaType.VIDEO) "video" else "audio"
                    postStatusBannerMessage(
                        "Imported $typeLabel '${entity.title}' (${entity.mimeType}).",
                        InAppNotificationType.TEMPORARY_SUCCESS
                    )
                    playMedia(entity, listOf(entity))
                    _isNowPlayingExpanded.value = true
                }
                is MediaLibraryRepository.SafImportResult.UnsupportedFormat -> {
                    postStatusBannerMessage(result.reason, InAppNotificationType.PERSISTENT_ERROR)
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        isBuffering = false,
                        playbackError = result.reason
                    )
                }
                is MediaLibraryRepository.SafImportResult.CorruptedOrEmpty -> {
                    postStatusBannerMessage(result.reason, InAppNotificationType.PERSISTENT_ERROR)
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        isBuffering = false,
                        playbackError = result.reason
                    )
                }
                is MediaLibraryRepository.SafImportResult.AccessDeniedOrNotFound -> {
                    postStatusBannerMessage(result.reason, InAppNotificationType.PERSISTENT_ERROR)
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        isBuffering = false,
                        playbackError = result.reason
                    )
                }
            }
        }
    }

    // --- Playback Controls ---
    fun playMedia(
        media: MediaEntity,
        queueContext: List<MediaEntity> = emptyList(),
        queueTitle: String? = null
    ) {
        viewModelScope.launch {
            if (media.isGeneratedSample || media.uriString.contains("Pulse_Cinema_Test_Reel")) {
                runCatching { sampleGenerator.ensureLegalSampleMediaGenerated() }
            }
            val parsedUri = Uri.parse(media.uriString)
            if (parsedUri.scheme == "file") {
                val localFile = parsedUri.path?.let { File(it) }
                if (localFile != null && localFile.exists() && localFile.length() in 0L..31L) {
                    val corruptMsg = "Corrupted or empty media file: '${media.title}' (${localFile.length()} bytes)."
                    _playbackState.value = _playbackState.value.copy(
                        currentMedia = media,
                        isPlaying = false,
                        isBuffering = false,
                        hasRenderedFirstFrame = false,
                        playbackError = corruptMsg
                    )
                    postStatusBannerMessage(corruptMsg, InAppNotificationType.PERSISTENT_ERROR)
                    return@launch
                }
            }

            val isAvailable = mediaRepository.verifyUriAvailability(media.uriString)
            if (!isAvailable) {
                _playbackState.value = _playbackState.value.copy(
                    currentMedia = media.copy(isAvailable = false),
                    isPlaying = false,
                    isBuffering = false,
                    hasRenderedFirstFrame = false,
                    playbackError = "File unavailable: '${media.title}' was moved, deleted, or its URI permission was revoked."
                )
                postStatusBannerMessage("File unavailable: ${media.title}", InAppNotificationType.PERSISTENT_ERROR)
                return@launch
            }

            val baseOriginalQueue = if (queueContext.isNotEmpty()) queueContext else listOf(media)
            val currentState = _playbackState.value
            val resolvedQueue = if (currentState.shuffleEnabled && baseOriginalQueue.size > 1) {
                val others = baseOriginalQueue.filter { it.uriString != media.uriString }.shuffled()
                buildList {
                    add(media)
                    addAll(others)
                }
            } else {
                baseOriginalQueue
            }
            val startIndex = resolvedQueue.indexOfFirst { it.uriString == media.uriString }.coerceAtLeast(0)
            val resolvedQueueTitle = MediaFileActionsHelper.computeQueueSourceTitle(
                context = getApplication(),
                queue = resolvedQueue,
                explicitQueueTitle = queueTitle
            )
            val prefs = userPreferences.first()
            val savedPos = if (prefs.resumePlaybackEnabled) {
                mediaRepository.getSavedResumePosition(media.uriString)
            } else 0L
            val initialSeekMs = if (savedPos in 1000L until (media.durationMs - 1500L)) savedPos else 0L

            val app = getApplication<Application>()
            val activeResolvedArtwork = withContext(Dispatchers.IO) {
                MediaArtworkLoader.resolveArtworkMetadata(app, media, null)
            }

            val mediaItems = resolvedQueue.map { item ->
                buildMediaItemForEntity(
                    item = item,
                    overrideArtwork = if (item.uriString == media.uriString) activeResolvedArtwork else null
                )
            }

            runCatching {
                app.startService(Intent(app, PulsePlaybackService::class.java))
            }

            val player = activePlayer ?: run {
                ensureFallbackLocalExoPlayer()
                activePlayer
            } ?: return@launch

            _videoSurfacePlayerFlow.value = PulsePlaybackService.getAuthoritativeExoPlayerOrNull() ?: player

            val currentActiveUri = player.currentMediaItem?.mediaId
            val isSameActiveItem = currentActiveUri == media.uriString &&
                player.playbackState != Player.STATE_IDLE &&
                _playbackState.value.playbackError == null

            // Requirement 8: Never release the decoder that is still actively rendering the current video!
            if (isSameActiveItem) {
                if (!player.isPlaying) {
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekTo(0L)
                    }
                    player.playWhenReady = true
                    player.play()
                }
                _playbackState.value = _playbackState.value.copy(
                    currentMedia = media,
                    queue = resolvedQueue,
                    originalQueue = baseOriginalQueue,
                    queueTitle = resolvedQueueTitle,
                    currentQueueIndex = startIndex,
                    isPlaying = true,
                    playbackError = null
                )
                syncAndResolveArtworkForTrack(media, player.mediaMetadata)
                startProgressPolling()
                return@launch
            }

            // Reset per-URI recovery counter when user explicitly initiates playback of a track
            decoderRecoveryAttemptsByUri[media.uriString] = 0
            Resilient4KCodecEngine.updateConfiguredPlaybackMode(
                mode = prefs.highResVideoPlaybackMode,
                legacyAllowSoftwareFallback = prefs.allowExperimental4kSoftwareFallback
            )

            // Always release any previous track's temporary high-res or downscaled constraint first
            clearPlayerVideoSizeConstraintAndReleaseTempSession("Loading new media item: ${media.title}")

            val videoCapabilityEval: VideoStreamCapabilityEvaluation? = if (media.mediaType == MediaType.VIDEO) {
                val eval = Resilient4KCodecEngine.evaluateVideoStreamCapability(
                    context = app,
                    media = media,
                    selectedMode = prefs.highResVideoPlaybackMode
                )
                Resilient4KCodecEngine.resetTelemetryForNewTrack(
                    mimeType = media.mimeType,
                    width = media.width,
                    height = media.height,
                    frameRate = 30f,
                    bitrateBps = eval.bitrateBps,
                    isHdrOr10Bit = eval.is10BitOrHdr,
                    trackUri = media.uriString
                )
                eval
            } else {
                null
            }

            // Release obsolete codec instances from the previous track safely before loading the new track
            if (currentActiveUri != null || _playbackState.value.playbackError != null) {
                runCatching {
                    player.stop()
                    player.clearMediaItems()
                }
            }

            player.setMediaItems(mediaItems, startIndex, initialSeekMs)
            player.playbackParameters = PlaybackParameters(prefs.defaultPlaybackSpeed)
            if (player is ExoPlayer) {
                player.pauseAtEndOfMediaItems = !prefs.gaplessPlaybackEnabled
            }
            player.volume = _playbackState.value.volumeGestureLevel.coerceIn(0f, 1f)
            player.prepare()
            player.playWhenReady = true
            player.play()

            val sessionId = resolveActiveAudioSessionId(player)
            equalizerController.setStereoBalance(prefs.audioBalance)
            equalizerController.setPreampGainDb(prefs.preampGainDb)
            equalizerController.bindToAudioSession(
                audioSessionId = sessionId,
                enabled = prefs.equalizerEnabled,
                presetIndex = prefs.equalizerPresetIndex,
                bassStrength = prefs.bassBoostStrength,
                savedBandLevelsMb = prefs.customBandLevelsMb,
                isPlaybackActive = true
            )
            queryServiceAudioSessionIdIfController(player)

            val latestTelemetry = Resilient4KCodecEngine.latestTelemetry.value
            _playbackState.value = _playbackState.value.copy(
                currentMedia = media,
                queue = resolvedQueue,
                originalQueue = baseOriginalQueue,
                queueTitle = resolvedQueueTitle,
                currentQueueIndex = startIndex,
                isPlaying = true,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                hasRenderedFirstFrame = false,
                decoderRecoveryAttempts = 0,
                currentPositionMs = initialSeekMs,
                durationMs = media.durationMs,
                playbackSpeed = prefs.defaultPlaybackSpeed,
                playerGeneration = _playbackState.value.playerGeneration + 1,
                playbackError = null,
                currentArtworkData = activeResolvedArtwork.artworkData,
                currentArtworkUri = activeResolvedArtwork.artworkUri?.toString(),
                currentArtworkSource = activeResolvedArtwork.sourceLabel,
                videoCapabilityEvaluation = videoCapabilityEval,
                showVideoCompatibilityDialog = false,
                showVideoInfoDetailsDialog = false,
                showPlaybackSpeedDialog = false,
                showVideoSettingsDialog = false,
                showSubtitleSettingsDialog = false,
                showMusicPlaybackSettingsDialog = false,
                abRepeatStartMs = null,
                abRepeatEndMs = null,
                trackBookmarksMs = bookmarksByUri[media.uriString].orEmpty(),
                activeVideoEngineStrategy = videoCapabilityEval?.recommendedStrategy
                    ?: latestTelemetry.activeEngineStrategy,
                isTemporaryHighResEngineActive = videoCapabilityEval?.resolutionTier?.isHighRes4kOrAbove == true ||
                    latestTelemetry.isTemporaryHighResSessionActive,
                isDownscaledPlayback = false,
                isFullscreenVideo = if (media.mediaType == MediaType.AUDIO) false else _playbackState.value.isFullscreenVideo
            )
            resetOrLoadCompanionSubtitleForMedia(media)
            syncAndResolveArtworkForTrack(media, player.mediaMetadata)
            startProgressPolling()
        }
    }

    fun togglePlayPause() {
        val player = activePlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0L)
            }
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = activePlayer ?: return
        val clamped = positionMs.coerceIn(0L, _playbackState.value.durationMs.coerceAtLeast(1L))
        player.seekTo(clamped)
        updateSubtitleCueForPosition(clamped)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = clamped)
    }

    fun skipToNext() {
        val player = activePlayer
        val queue = _playbackState.value.queue
        if (player != null && player.mediaItemCount > 0) {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            } else if (queue.size > 1) {
                player.seekTo(0, 0L)
            }
            val activeId = player.currentMediaItem?.mediaId
            val matchedFromQueue = if (!activeId.isNullOrBlank()) queue.find { it.uriString == activeId } else null
            val nextIdx = if (matchedFromQueue != null) {
                queue.indexOfFirst { it.uriString == matchedFromQueue.uriString }
            } else {
                player.currentMediaItemIndex.takeIf { it in queue.indices }
                    ?: ((_playbackState.value.currentQueueIndex + 1) % queue.size.coerceAtLeast(1))
            }
            val nextEntity = matchedFromQueue ?: queue.getOrNull(nextIdx)
            if (nextEntity != null) {
                _playbackState.value = _playbackState.value.copy(
                    currentMedia = nextEntity,
                    currentQueueIndex = nextIdx,
                    currentPositionMs = 0L,
                    durationMs = nextEntity.durationMs,
                    hasRenderedFirstFrame = false
                )
                resetOrLoadCompanionSubtitleForMedia(nextEntity)
                syncAndResolveArtworkForTrack(nextEntity, player.currentMediaItem?.mediaMetadata)
            }
        } else if (queue.size > 1) {
            val nextIdx = (_playbackState.value.currentQueueIndex + 1) % queue.size
            val nextEntity = queue[nextIdx]
            _playbackState.value = _playbackState.value.copy(
                currentMedia = nextEntity,
                currentQueueIndex = nextIdx,
                currentPositionMs = 0L,
                durationMs = nextEntity.durationMs,
                hasRenderedFirstFrame = false
            )
            resetOrLoadCompanionSubtitleForMedia(nextEntity)
            syncAndResolveArtworkForTrack(nextEntity, null)
        }
    }

    fun skipToPrevious() {
        val player = activePlayer
        val queue = _playbackState.value.queue
        if (player != null && player.mediaItemCount > 0) {
            if (player.currentPosition > 3000L) {
                player.seekTo(0L)
                _playbackState.value = _playbackState.value.copy(currentPositionMs = 0L)
            } else if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
                val activeId = player.currentMediaItem?.mediaId
                val matchedFromQueue = if (!activeId.isNullOrBlank()) queue.find { it.uriString == activeId } else null
                val prevIdx = if (matchedFromQueue != null) {
                    queue.indexOfFirst { it.uriString == matchedFromQueue.uriString }
                } else {
                    player.currentMediaItemIndex.takeIf { it in queue.indices }
                        ?: (_playbackState.value.currentQueueIndex - 1).coerceAtLeast(0)
                }
                val prevEntity = matchedFromQueue ?: queue.getOrNull(prevIdx)
                if (prevEntity != null) {
                    _playbackState.value = _playbackState.value.copy(
                        currentMedia = prevEntity,
                        currentQueueIndex = prevIdx,
                        currentPositionMs = 0L,
                        durationMs = prevEntity.durationMs,
                        hasRenderedFirstFrame = false
                    )
                    resetOrLoadCompanionSubtitleForMedia(prevEntity)
                    syncAndResolveArtworkForTrack(prevEntity, player.currentMediaItem?.mediaMetadata)
                }
            }
        } else if (queue.size > 1 && _playbackState.value.currentQueueIndex > 0) {
            val prevIdx = _playbackState.value.currentQueueIndex - 1
            val prevEntity = queue[prevIdx]
            _playbackState.value = _playbackState.value.copy(
                currentMedia = prevEntity,
                currentQueueIndex = prevIdx,
                currentPositionMs = 0L,
                durationMs = prevEntity.durationMs,
                hasRenderedFirstFrame = false
            )
            resetOrLoadCompanionSubtitleForMedia(prevEntity)
            syncAndResolveArtworkForTrack(prevEntity, null)
        }
    }

    fun toggleShuffle() {
        val state = _playbackState.value
        val next = !state.shuffleEnabled
        val player = activePlayer

        if (state.queue.size <= 1) {
            player?.shuffleModeEnabled = next
            val baseOrig = if (state.originalQueue.isNotEmpty()) state.originalQueue else state.queue
            _playbackState.value = state.copy(
                shuffleEnabled = next,
                originalQueue = baseOrig
            )
            return
        }

        if (next) {
            // Enable Shuffle: preserve originalQueue and place active song at index 0 followed by shuffled remaining tracks
            val baseOriginal = if (state.originalQueue.isNotEmpty()) state.originalQueue else state.queue
            val currentIdx = state.currentQueueIndex.coerceIn(0, state.queue.lastIndex)
            val currentTrack = state.queue[currentIdx]
            val remaining = state.queue.filterIndexed { idx, _ -> idx != currentIdx }.shuffled()
            // Ensure deterministic change when > 2 items and random shuffle happened to match original order
            val adjustedRemaining = if (remaining.size > 1 && remaining == state.queue.filterIndexed { idx, _ -> idx != currentIdx }) {
                remaining.reversed()
            } else {
                remaining
            }
            val shuffledQueue = buildList(state.queue.size) {
                add(currentTrack)
                addAll(adjustedRemaining)
            }

            if (player != null) {
                player.shuffleModeEnabled = false
                runCatching {
                    if (player.mediaItemCount == state.queue.size) {
                        if (currentIdx != 0) {
                            player.moveMediaItem(currentIdx, 0)
                        }
                        if (adjustedRemaining.isNotEmpty()) {
                            player.replaceMediaItems(
                                1,
                                player.mediaItemCount,
                                adjustedRemaining.map { buildMediaItemForEntity(it) }
                            )
                        }
                    }
                }
            }

            _playbackState.value = state.copy(
                shuffleEnabled = true,
                queue = shuffledQueue,
                originalQueue = baseOriginal,
                currentQueueIndex = 0
            )
        } else {
            // Disable Shuffle: restore originalQueue while keeping the currently playing song uninterrupted
            val restoredQueue = if (state.originalQueue.isNotEmpty()) state.originalQueue else state.queue
            val activeTrack = state.currentMedia ?: state.queue.getOrNull(state.currentQueueIndex)
            val restoredIdx = if (activeTrack != null) {
                restoredQueue.indexOfFirst { it.uriString == activeTrack.uriString }.takeIf { it >= 0 } ?: 0
            } else {
                0
            }

            if (player != null) {
                player.shuffleModeEnabled = false
                runCatching {
                    if (player.mediaItemCount > 0 && restoredQueue.isNotEmpty()) {
                        val playerActiveIdx = player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1)
                        if (playerActiveIdx > 0) {
                            player.removeMediaItems(0, playerActiveIdx)
                        }
                        if (player.mediaItemCount > 1) {
                            player.removeMediaItems(1, player.mediaItemCount)
                        }
                        if (restoredIdx > 0) {
                            player.addMediaItems(
                                0,
                                restoredQueue.subList(0, restoredIdx).map { buildMediaItemForEntity(it) }
                            )
                        }
                        if (restoredIdx + 1 < restoredQueue.size) {
                            player.addMediaItems(
                                restoredIdx + 1,
                                restoredQueue.subList(restoredIdx + 1, restoredQueue.size).map { buildMediaItemForEntity(it) }
                            )
                        }
                    }
                }
            }

            _playbackState.value = state.copy(
                shuffleEnabled = false,
                queue = restoredQueue,
                originalQueue = restoredQueue,
                currentQueueIndex = restoredIdx
            )
        }
    }

    fun cycleRepeatMode() {
        val player = activePlayer ?: return
        val nextMode = when (_playbackState.value.repeatMode) {
            RepeatModeState.OFF -> RepeatModeState.ALL
            RepeatModeState.ALL -> RepeatModeState.ONE
            RepeatModeState.ONE -> RepeatModeState.OFF
        }
        player.repeatMode = when (nextMode) {
            RepeatModeState.OFF -> Player.REPEAT_MODE_OFF
            RepeatModeState.ONE -> Player.REPEAT_MODE_ONE
            RepeatModeState.ALL -> Player.REPEAT_MODE_ALL
        }
        _playbackState.value = _playbackState.value.copy(repeatMode = nextMode)
    }

    fun setRepeatModeState(mode: RepeatModeState) {
        activePlayer?.repeatMode = when (mode) {
            RepeatModeState.OFF -> Player.REPEAT_MODE_OFF
            RepeatModeState.ONE -> Player.REPEAT_MODE_ONE
            RepeatModeState.ALL -> Player.REPEAT_MODE_ALL
        }
        _playbackState.value = _playbackState.value.copy(repeatMode = mode)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3.0f)
        activePlayer?.setPlaybackSpeed(clamped)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = clamped)
    }

    // --- Sleep Timer (Automatic Stop) ---
    fun setSleepTimerMinutes(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _playbackState.value = _playbackState.value.copy(
                sleepTimerTotalMinutes = 0,
                sleepTimerRemainingMs = 0L,
                sleepTimerStopAtEndOfTrack = false
            )
            postStatusBannerMessage("Sleep Timer turned off.", InAppNotificationType.TEMPORARY_INFO)
            return
        }

        val totalMs = minutes * 60_000L
        _playbackState.value = _playbackState.value.copy(
            sleepTimerTotalMinutes = minutes,
            sleepTimerRemainingMs = totalMs,
            sleepTimerStopAtEndOfTrack = false
        )
        postStatusBannerMessage(
            "Sleep Timer set for $minutes min (automatic stop).",
            InAppNotificationType.TEMPORARY_INFO
        )

        sleepTimerJob = viewModelScope.launch {
            while (isActive && _playbackState.value.sleepTimerRemainingMs > 0L) {
                delay(1000L)
                advanceSleepTimerByMs(1000L)
            }
        }
    }

    fun setSleepTimerEndOfTrack(enabled: Boolean) {
        sleepTimerJob?.cancel()
        _playbackState.value = _playbackState.value.copy(
            sleepTimerTotalMinutes = 0,
            sleepTimerRemainingMs = 0L,
            sleepTimerStopAtEndOfTrack = enabled
        )
        postStatusBannerMessage(
            if (enabled) {
                "Sleep Timer: Will automatically stop at the end of the current track."
            } else {
                "Sleep Timer turned off."
            },
            InAppNotificationType.TEMPORARY_INFO
        )
    }

    fun cancelSleepTimer() {
        setSleepTimerMinutes(0)
    }

    /**
     * Advances the Sleep Timer countdown by [elapsedMs] and automatically pauses playback when it reaches 0.
     */
    fun advanceSleepTimerByMs(elapsedMs: Long) {
        val current = _playbackState.value
        if (current.sleepTimerRemainingMs <= 0L) return
        val nextRemaining = (current.sleepTimerRemainingMs - elapsedMs).coerceAtLeast(0L)
        if (nextRemaining == 0L) {
            triggerSleepTimerAutoStop("Sleep Timer finished • Playback paused automatically.")
        } else {
            _playbackState.value = current.copy(sleepTimerRemainingMs = nextRemaining)
        }
    }

    private fun triggerSleepTimerAutoStop(message: String) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        activePlayer?.playWhenReady = false
        activePlayer?.pause()
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            sleepTimerTotalMinutes = 0,
            sleepTimerRemainingMs = 0L,
            sleepTimerStopAtEndOfTrack = false
        )
        postStatusBannerMessage(message, InAppNotificationType.TEMPORARY_INFO)
    }

    fun cycleVideoAspectMode() {
        val modes = VideoAspectMode.entries
        val currentIdx = modes.indexOf(_playbackState.value.videoAspectMode)
        val next = modes[(currentIdx + 1) % modes.size]
        _playbackState.value = _playbackState.value.copy(videoAspectMode = next)
    }

    fun rotateVideoView90Degrees() {
        val nextRot = (_playbackState.value.videoRotationDegrees + 90) % 360
        _playbackState.value = _playbackState.value.copy(videoRotationDegrees = nextRot)
    }

    fun setVideoRotationDegrees(degrees: Int) {
        val normalized = ((degrees % 360) + 360) % 360
        _playbackState.value = _playbackState.value.copy(videoRotationDegrees = normalized)
    }

    fun setFullscreenVideo(fullscreen: Boolean) {
        _playbackState.value = _playbackState.value.copy(isFullscreenVideo = fullscreen)
    }

    fun setVideoControlsLocked(locked: Boolean) {
        _playbackState.value = _playbackState.value.copy(isVideoControlsLocked = locked)
    }

    fun adjustBrightnessGesture(delta: Float) {
        val next = (_playbackState.value.brightnessGestureLevel + delta).coerceIn(0.1f, 1.0f)
        _playbackState.value = _playbackState.value.copy(brightnessGestureLevel = next)
    }

    fun adjustVolumeGesture(delta: Float) {
        val next = (_playbackState.value.volumeGestureLevel + delta).coerceIn(0.0f, 1.0f)
        setVolumeLevel(next)
    }

    fun setVolumeLevel(level: Float) {
        val clamped = level.coerceIn(0.0f, 1.0f)
        activePlayer?.volume = clamped
        _playbackState.value = _playbackState.value.copy(volumeGestureLevel = clamped)
        runCatching {
            val audioManager = getApplication<Application>()
                .getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
            if (audioManager != null) {
                val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val targetVol = (clamped * maxVol).toInt().coerceIn(0, maxVol)
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVol, 0)
            }
        }
    }

    fun retryCurrentPlayback() {
        val current = _playbackState.value.currentMedia ?: return
        playMedia(current, _playbackState.value.queue)
    }

    fun openVideoCompatibilityDialog() {
        val current = _playbackState.value.currentMedia ?: return
        if (current.mediaType != MediaType.VIDEO) return
        val eval = _playbackState.value.videoCapabilityEvaluation
            ?: Resilient4KCodecEngine.evaluateVideoStreamCapability(
                context = getApplication(),
                media = current,
                selectedMode = userPreferences.value.highResVideoPlaybackMode
            )
        _isEqualizerSheetOpen.value = false
        _playbackState.value = _playbackState.value.copy(
            videoCapabilityEvaluation = eval,
            showVideoCompatibilityDialog = true,
            showVideoInfoDetailsDialog = false,
            showPlaybackSpeedDialog = false,
            showVideoSettingsDialog = false,
            showSubtitleSettingsDialog = false,
            showMusicPlaybackSettingsDialog = false
        )
    }

    fun setPlaybackSpeedDialogVisible(visible: Boolean) {
        if (visible) _isEqualizerSheetOpen.value = false
        _playbackState.value = _playbackState.value.copy(
            showPlaybackSpeedDialog = visible,
            showVideoCompatibilityDialog = if (visible) false else _playbackState.value.showVideoCompatibilityDialog,
            showVideoInfoDetailsDialog = if (visible) false else _playbackState.value.showVideoInfoDetailsDialog,
            showVideoSettingsDialog = if (visible) false else _playbackState.value.showVideoSettingsDialog,
            showSubtitleSettingsDialog = if (visible) false else _playbackState.value.showSubtitleSettingsDialog,
            showMusicPlaybackSettingsDialog = if (visible) false else _playbackState.value.showMusicPlaybackSettingsDialog
        )
    }

    fun setVideoSettingsDialogVisible(visible: Boolean) {
        if (visible) _isEqualizerSheetOpen.value = false
        _playbackState.value = _playbackState.value.copy(
            showVideoSettingsDialog = visible,
            showPlaybackSpeedDialog = if (visible) false else _playbackState.value.showPlaybackSpeedDialog,
            showVideoCompatibilityDialog = if (visible) false else _playbackState.value.showVideoCompatibilityDialog,
            showVideoInfoDetailsDialog = if (visible) false else _playbackState.value.showVideoInfoDetailsDialog,
            showSubtitleSettingsDialog = if (visible) false else _playbackState.value.showSubtitleSettingsDialog,
            showMusicPlaybackSettingsDialog = if (visible) false else _playbackState.value.showMusicPlaybackSettingsDialog
        )
    }

    fun setSubtitleSettingsDialogVisible(visible: Boolean) {
        if (visible) _isEqualizerSheetOpen.value = false
        _playbackState.value = _playbackState.value.copy(
            showSubtitleSettingsDialog = visible,
            showPlaybackSpeedDialog = if (visible) false else _playbackState.value.showPlaybackSpeedDialog,
            showVideoCompatibilityDialog = if (visible) false else _playbackState.value.showVideoCompatibilityDialog,
            showVideoInfoDetailsDialog = if (visible) false else _playbackState.value.showVideoInfoDetailsDialog,
            showVideoSettingsDialog = if (visible) false else _playbackState.value.showVideoSettingsDialog,
            showMusicPlaybackSettingsDialog = if (visible) false else _playbackState.value.showMusicPlaybackSettingsDialog
        )
    }

    fun setMusicPlaybackSettingsDialogVisible(visible: Boolean) {
        if (visible) _isEqualizerSheetOpen.value = false
        _playbackState.value = _playbackState.value.copy(
            showMusicPlaybackSettingsDialog = visible,
            showPlaybackSpeedDialog = if (visible) false else _playbackState.value.showPlaybackSpeedDialog,
            showVideoCompatibilityDialog = if (visible) false else _playbackState.value.showVideoCompatibilityDialog,
            showVideoInfoDetailsDialog = if (visible) false else _playbackState.value.showVideoInfoDetailsDialog,
            showVideoSettingsDialog = if (visible) false else _playbackState.value.showVideoSettingsDialog,
            showSubtitleSettingsDialog = if (visible) false else _playbackState.value.showSubtitleSettingsDialog
        )
    }

    fun setAbRepeatPointA(positionMs: Long = _playbackState.value.currentPositionMs) {
        val state = _playbackState.value
        val dur = (state.durationMs.takeIf { it > 0L } ?: state.currentMedia?.durationMs?.takeIf { it > 0L } ?: 600_000L).coerceAtLeast(1000L)
        val clampedA = positionMs.coerceIn(0L, (dur - 500L).coerceAtLeast(0L))
        val updatedB = state.abRepeatEndMs?.takeIf { it > clampedA + 500L }
        _playbackState.value = state.copy(
            abRepeatStartMs = clampedA,
            abRepeatEndMs = updatedB
        )
        postStatusBannerMessage(
            "A-B Repeat Point A set at ${com.example.ui.components.formatDurationMillis(clampedA)}",
            InAppNotificationType.TEMPORARY_INFO
        )
    }

    fun setAbRepeatPointB(positionMs: Long = _playbackState.value.currentPositionMs) {
        val state = _playbackState.value
        val startA = state.abRepeatStartMs ?: 0L
        val dur = (state.durationMs.takeIf { it > 0L } ?: state.currentMedia?.durationMs?.takeIf { it > 0L } ?: 600_000L).coerceAtLeast(1000L)
        val clampedB = positionMs.coerceIn((startA + 500L).coerceAtMost(dur), dur)
        if (clampedB <= startA) {
            postStatusBannerMessage(
                "Point B must be after Point A (${com.example.ui.components.formatDurationMillis(startA)}).",
                InAppNotificationType.TEMPORARY_WARNING
            )
            return
        }
        _playbackState.value = state.copy(
            abRepeatStartMs = startA,
            abRepeatEndMs = clampedB
        )
        postStatusBannerMessage(
            "A-B Repeat active: ${com.example.ui.components.formatDurationMillis(startA)} – ${com.example.ui.components.formatDurationMillis(clampedB)}",
            InAppNotificationType.TEMPORARY_SUCCESS
        )
    }

    fun clearAbRepeat() {
        val state = _playbackState.value
        if (state.abRepeatStartMs == null && state.abRepeatEndMs == null) return
        _playbackState.value = state.copy(
            abRepeatStartMs = null,
            abRepeatEndMs = null
        )
        postStatusBannerMessage(
            "A-B Repeat loop cleared.",
            InAppNotificationType.TEMPORARY_INFO
        )
    }

    fun addOrToggleBookmarkAtCurrentPosition() {
        val state = _playbackState.value
        val media = state.currentMedia ?: return
        val pos = state.currentPositionMs.coerceAtLeast(0L)
        val existing = state.trackBookmarksMs
        val nearMatch = existing.firstOrNull { kotlin.math.abs(it - pos) < 1500L }
        val updated = if (nearMatch != null) {
            existing.filterNot { it == nearMatch }
        } else {
            (existing + pos).distinct().sorted()
        }
        bookmarksByUri[media.uriString] = updated
        _playbackState.value = state.copy(trackBookmarksMs = updated)
        postStatusBannerMessage(
            if (nearMatch != null) {
                "Removed bookmark at ${com.example.ui.components.formatDurationMillis(nearMatch)}"
            } else {
                "Bookmarked ${com.example.ui.components.formatDurationMillis(pos)} in '${media.title}'"
            },
            InAppNotificationType.TEMPORARY_SUCCESS
        )
    }

    fun removeTrackBookmark(bookmarkMs: Long) {
        val state = _playbackState.value
        val media = state.currentMedia ?: return
        val updated = state.trackBookmarksMs.filterNot { it == bookmarkMs }
        bookmarksByUri[media.uriString] = updated
        _playbackState.value = state.copy(trackBookmarksMs = updated)
    }

    fun playFromHistoryItem(historyItem: PlaybackHistoryEntity) {
        val matched = allMedia.value.firstOrNull { it.uriString == historyItem.uriString }
        if (matched != null) {
            val contextQueue = if (matched.mediaType == MediaType.AUDIO) {
                audioMedia.value.ifEmpty { listOf(matched) }
            } else {
                videoMedia.value.ifEmpty { listOf(matched) }
            }
            playMedia(matched, contextQueue)
        } else {
            postStatusBannerMessage(
                "Track '${historyItem.titleSnapshot}' is no longer in the library.",
                InAppNotificationType.TEMPORARY_WARNING
            )
        }
    }

    fun dismissVideoCompatibilityDialog() {
        _playbackState.value = _playbackState.value.copy(
            showVideoCompatibilityDialog = false
        )
    }

    fun dismissVideoInfoDetailsDialog() {
        _playbackState.value = _playbackState.value.copy(
            showVideoInfoDetailsDialog = false
        )
    }

    /**
     * Applies one of the 6 In-Player Compatibility Options Panel actions ONLY for the active video:
     * - [VideoCompatibilityAction.AUTO_PLAYBACK]
     * - [VideoCompatibilityAction.TRY_COMPATIBLE_DECODER]
     * - [VideoCompatibilityAction.SOFTWARE_FALLBACK]
     * - [VideoCompatibilityAction.LOWER_RESOLUTION_PLAYBACK]
     * - [VideoCompatibilityAction.VIDEO_INFORMATION]
     * - [VideoCompatibilityAction.CANCEL]
     */
    fun applyVideoCompatibilityAction(action: VideoCompatibilityAction) {
        val currentMedia = _playbackState.value.currentMedia ?: return
        val uri = currentMedia.uriString
        val eval = _playbackState.value.videoCapabilityEvaluation
            ?: Resilient4KCodecEngine.evaluateVideoStreamCapability(
                context = getApplication(),
                media = currentMedia,
                selectedMode = userPreferences.value.highResVideoPlaybackMode
            )

        when (action) {
            VideoCompatibilityAction.VIDEO_INFORMATION -> {
                _playbackState.value = _playbackState.value.copy(
                    videoCapabilityEvaluation = eval,
                    showVideoCompatibilityDialog = false,
                    showVideoInfoDetailsDialog = true
                )
                return
            }

            VideoCompatibilityAction.CANCEL -> {
                _playbackState.value = _playbackState.value.copy(
                    showVideoCompatibilityDialog = false
                )
                if (_playbackState.value.playbackError != null ||
                    eval.recommendedStrategy == AdaptiveVideoEngineStrategy.UNSUPPORTED_REQUIRE_USER_CHOICE
                ) {
                    stopVideoPlaybackAndCleanup("User cancelled high-res compatibility panel")
                }
                return
            }

            VideoCompatibilityAction.SOFTWARE_FALLBACK -> {
                if (!eval.isSoftwareFallbackSafe) {
                    postStatusBannerMessage(
                        eval.softwareUnavailableReason
                            ?: "Software fallback is unsafe for this resolution/device.",
                        InAppNotificationType.PERSISTENT_ERROR
                    )
                    return
                }
                Resilient4KCodecEngine.applyTemporaryCompatibilityActionForTrack(
                    trackUri = uri,
                    action = VideoCompatibilityAction.SOFTWARE_FALLBACK,
                    mimeType = currentMedia.mimeType,
                    width = currentMedia.width,
                    height = currentMedia.height
                )
                _playbackState.value = _playbackState.value.copy(
                    showVideoCompatibilityDialog = false,
                    activeVideoEngineStrategy = AdaptiveVideoEngineStrategy.GUARDED_SOFTWARE_FALLBACK,
                    isTemporaryHighResEngineActive = true,
                    isDownscaledPlayback = false,
                    playbackError = null
                )
                restartCurrentVideoWithUpdatedDecoderConfig(clearVideoSizeConstraints = true)
            }

            VideoCompatibilityAction.TRY_COMPATIBLE_DECODER -> {
                Resilient4KCodecEngine.applyTemporaryCompatibilityActionForTrack(
                    trackUri = uri,
                    action = VideoCompatibilityAction.TRY_COMPATIBLE_DECODER,
                    mimeType = currentMedia.mimeType,
                    width = currentMedia.width,
                    height = currentMedia.height
                )
                _playbackState.value = _playbackState.value.copy(
                    showVideoCompatibilityDialog = false,
                    activeVideoEngineStrategy = AdaptiveVideoEngineStrategy.ALTERNATE_COMPATIBLE_HARDWARE,
                    isTemporaryHighResEngineActive = true,
                    isDownscaledPlayback = false,
                    playbackError = null
                )
                restartCurrentVideoWithUpdatedDecoderConfig(clearVideoSizeConstraints = true)
            }

            VideoCompatibilityAction.LOWER_RESOLUTION_PLAYBACK -> {
                Resilient4KCodecEngine.applyTemporaryCompatibilityActionForTrack(
                    trackUri = uri,
                    action = VideoCompatibilityAction.LOWER_RESOLUTION_PLAYBACK,
                    mimeType = currentMedia.mimeType,
                    width = currentMedia.width,
                    height = currentMedia.height
                )
                activePlayer?.let { player ->
                    runCatching {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setMaxVideoSize(1920, 1080)
                            .build()
                    }
                }
                _playbackState.value = _playbackState.value.copy(
                    showVideoCompatibilityDialog = false,
                    activeVideoEngineStrategy = AdaptiveVideoEngineStrategy.LOWER_RESOLUTION_DOWNSCALED,
                    isTemporaryHighResEngineActive = true,
                    isDownscaledPlayback = true,
                    playbackError = null
                )
                postStatusBannerMessage(
                    "Using supported lower-resolution compatibility path (Downscaled ≤1080p) for this video only.",
                    InAppNotificationType.TEMPORARY_INFO
                )
                restartCurrentVideoWithUpdatedDecoderConfig(clearVideoSizeConstraints = false)
            }

            VideoCompatibilityAction.AUTO_PLAYBACK -> {
                Resilient4KCodecEngine.applyTemporaryCompatibilityActionForTrack(
                    trackUri = uri,
                    action = VideoCompatibilityAction.AUTO_PLAYBACK,
                    mimeType = currentMedia.mimeType,
                    width = currentMedia.width,
                    height = currentMedia.height
                )
                _playbackState.value = _playbackState.value.copy(
                    showVideoCompatibilityDialog = false,
                    activeVideoEngineStrategy = eval.recommendedStrategy,
                    isTemporaryHighResEngineActive = eval.resolutionTier.isHighRes4kOrAbove,
                    isDownscaledPlayback = false,
                    playbackError = null
                )
                restartCurrentVideoWithUpdatedDecoderConfig(clearVideoSizeConstraints = true)
            }
        }
    }

    private fun restartCurrentVideoWithUpdatedDecoderConfig(clearVideoSizeConstraints: Boolean) {
        val player = activePlayer ?: return
        val currentMedia = _playbackState.value.currentMedia ?: return
        decoderRecoveryAttemptsByUri[currentMedia.uriString] = 0
        if (clearVideoSizeConstraints) {
            runCatching {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearVideoSizeConstraints()
                    .build()
            }
        }
        val savedIdx = (player.currentMediaItemIndex.takeIf { it >= 0 }
            ?: _playbackState.value.currentQueueIndex).coerceAtLeast(0)
        val savedPos = (player.currentPosition.takeIf { it > 0L }
            ?: _playbackState.value.currentPositionMs).coerceAtLeast(0L)
        runCatching {
            player.stop()
            player.seekTo(savedIdx, savedPos)
            player.prepare()
            player.playWhenReady = true
            player.play()
        }
    }

    private fun buildMediaItemForEntity(
        item: MediaEntity,
        overrideArtwork: com.example.media.ResolvedMediaArtwork? = null
    ): MediaItem {
        val cachedArt = overrideArtwork ?: MediaArtworkLoader.peekCachedArtworkMetadata(item.uriString)
        val metaBuilder = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist)
            .setAlbumTitle(item.album)
        if (cachedArt?.artworkData != null && cachedArt.artworkData.isNotEmpty()) {
            metaBuilder.maybeSetArtworkData(
                cachedArt.artworkData,
                MediaMetadata.PICTURE_TYPE_FRONT_COVER
            )
        }
        val resolvedArtUri = cachedArt?.artworkUri
        if (resolvedArtUri != null) {
            metaBuilder.setArtworkUri(resolvedArtUri)
        }
        return MediaItem.Builder()
            .setMediaId(item.uriString)
            .setUri(Uri.parse(item.uriString))
            .setMediaMetadata(metaBuilder.build())
            .build()
    }

    // --- Queue & Playlist Management ---
    fun setQueue(items: List<MediaEntity>, initialIndex: Int = 0, queueTitle: String = "Playing Queue") {
        val safeIndex = if (items.isNotEmpty()) initialIndex.coerceIn(0, items.size - 1) else -1
        val current = _playbackState.value
        val activeMedia = if (safeIndex >= 0) items.getOrNull(safeIndex) else null
        if (activeMedia != null) {
            decoderRecoveryAttemptsByUri[activeMedia.uriString] = 0
            val parsedUri = Uri.parse(activeMedia.uriString)
            if (parsedUri.scheme == "file") {
                val localFile = parsedUri.path?.let { File(it) }
                if (localFile != null && localFile.exists() && localFile.length() in 0L..31L) {
                    val corruptMsg = "Corrupted or empty media file: '${activeMedia.title}' (${localFile.length()} bytes)."
                    _playbackState.value = current.copy(
                        queue = items,
                        originalQueue = items,
                        queueTitle = queueTitle,
                        currentQueueIndex = safeIndex,
                        currentMedia = activeMedia,
                        isPlaying = false,
                        isBuffering = false,
                        hasRenderedFirstFrame = false,
                        decoderRecoveryAttempts = 0,
                        playbackError = corruptMsg
                    )
                    postStatusBannerMessage(corruptMsg, InAppNotificationType.PERSISTENT_ERROR)
                    return
                }
            }
        }
        val resolvedQueueTitle = MediaFileActionsHelper.computeQueueSourceTitle(
            context = getApplication(),
            queue = items,
            explicitQueueTitle = queueTitle.takeIf { it != "Playing Queue" }
        )
        _playbackState.value = current.copy(
            queue = items,
            originalQueue = items,
            queueTitle = resolvedQueueTitle,
            currentQueueIndex = safeIndex,
            currentMedia = activeMedia,
            hasRenderedFirstFrame = false,
            decoderRecoveryAttempts = 0,
            playbackError = null
        )
        if (activeMedia?.uriString != current.currentMedia?.uriString) {
            resetOrLoadCompanionSubtitleForMedia(activeMedia)
        }
        syncAndResolveArtworkForTrack(activeMedia, null)
    }

    /**
     * Immediately plays the song at [index] inside the active playback queue without rebuilding
     * the queue when ExoPlayer already has the queue items loaded.
     */
    fun selectQueueItemAt(index: Int, fallbackMedia: MediaEntity? = null) {
        val state = _playbackState.value
        val queue = state.queue
        val targetMedia = queue.getOrNull(index) ?: fallbackMedia ?: return

        if (index in queue.indices && targetMedia.isAvailable) {
            val player = activePlayer
            if (player != null) {
                runCatching {
                    if (player.mediaItemCount != queue.size) {
                        val mediaItems = queue.map { buildMediaItemForEntity(it) }
                        player.setMediaItems(mediaItems, index, 0L)
                        player.prepare()
                    } else if (index != state.currentQueueIndex ||
                        player.currentMediaItem?.mediaId != targetMedia.uriString ||
                        player.playbackState == Player.STATE_ENDED
                    ) {
                        player.seekTo(index, 0L)
                        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                            player.prepare()
                        }
                    }
                    player.playWhenReady = true
                    player.play()
                }
            }
            _playbackState.value = state.copy(
                currentMedia = targetMedia,
                currentQueueIndex = index,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = targetMedia.durationMs,
                hasRenderedFirstFrame = false,
                playbackError = null
            )
            resetOrLoadCompanionSubtitleForMedia(targetMedia)
            syncAndResolveArtworkForTrack(targetMedia, player?.currentMediaItem?.mediaMetadata)
            startProgressPolling()
        } else {
            playMedia(targetMedia, queue, state.queueTitle)
        }
    }

    /**
     * Inserts [media] immediately after the currently playing song so it plays next automatically.
     * If no song is currently loaded, starts playing [media] immediately.
     */
    fun playNext(media: MediaEntity) {
        val state = _playbackState.value
        if (state.queue.isEmpty() || state.currentMedia == null) {
            playMedia(media, listOf(media))
            postStatusBannerMessage(
                "Playing '${media.title}'",
                InAppNotificationType.TEMPORARY_INFO
            )
            return
        }

        val currentQueue = state.queue.toMutableList()
        val currentIdx = state.currentQueueIndex.coerceIn(0, currentQueue.lastIndex)
        val existingIdx = currentQueue.indexOfFirst { it.uriString == media.uriString }

        if (existingIdx == currentIdx) {
            postStatusBannerMessage(
                "'${media.title}' is already playing.",
                InAppNotificationType.TEMPORARY_INFO
            )
            return
        }

        if (existingIdx >= 0) {
            val targetIdx = if (existingIdx < currentIdx) {
                currentIdx
            } else {
                (currentIdx + 1).coerceAtMost(currentQueue.lastIndex)
            }
            moveQueueItem(existingIdx, targetIdx)
            postStatusBannerMessage(
                "Playing next: '${media.title}'",
                InAppNotificationType.TEMPORARY_SUCCESS
            )
            return
        }

        val insertIdx = (currentIdx + 1).coerceIn(0, currentQueue.size)
        currentQueue.add(insertIdx, media)
        val baseOriginal = (if (state.originalQueue.isNotEmpty()) state.originalQueue else state.queue).toMutableList()
        baseOriginal.add(insertIdx.coerceIn(0, baseOriginal.size), media)

        activePlayer?.let { player ->
            runCatching {
                val safePlayerInsertIdx = insertIdx.coerceIn(0, player.mediaItemCount)
                player.addMediaItem(safePlayerInsertIdx, buildMediaItemForEntity(media))
            }
        }

        val resolvedQueueTitle = MediaFileActionsHelper.computeQueueSourceTitle(
            context = getApplication(),
            queue = currentQueue,
            explicitQueueTitle = state.queueTitle
        )
        _playbackState.value = state.copy(
            queue = currentQueue,
            originalQueue = baseOriginal,
            queueTitle = resolvedQueueTitle
        )
        postStatusBannerMessage(
            "Playing next: '${media.title}'",
            InAppNotificationType.TEMPORARY_SUCCESS
        )
    }

    /**
     * Appends [media] to the end of the active playback queue without interrupting current playback.
     * If the queue is empty, starts playing [media] immediately.
     */
    fun addToQueue(media: MediaEntity) {
        val state = _playbackState.value
        if (state.queue.isEmpty() || state.currentMedia == null) {
            playMedia(media, listOf(media))
            postStatusBannerMessage(
                "Playing '${media.title}'",
                InAppNotificationType.TEMPORARY_INFO
            )
            return
        }

        val currentQueue = state.queue.toMutableList()
        currentQueue.add(media)
        val baseOriginal = (if (state.originalQueue.isNotEmpty()) state.originalQueue else state.queue).toMutableList()
        baseOriginal.add(media)

        activePlayer?.let { player ->
            runCatching {
                player.addMediaItem(buildMediaItemForEntity(media))
            }
        }

        val resolvedQueueTitle = MediaFileActionsHelper.computeQueueSourceTitle(
            context = getApplication(),
            queue = currentQueue,
            explicitQueueTitle = state.queueTitle
        )
        _playbackState.value = state.copy(
            queue = currentQueue,
            originalQueue = baseOriginal,
            queueTitle = resolvedQueueTitle
        )
        postStatusBannerMessage(
            "Added '${media.title}' to queue (${currentQueue.size} songs)",
            InAppNotificationType.TEMPORARY_SUCCESS
        )
    }

    /**
     * Removes the song at [index] from the queue without interrupting the currently playing song
     * unless the currently playing song itself is removed.
     */
    fun removeQueueItemAt(index: Int) {
        val state = _playbackState.value
        val currentQueue = state.queue.toMutableList()
        if (index !in currentQueue.indices) return
        val removedItem = currentQueue.removeAt(index)

        val updatedOriginal = (if (state.originalQueue.isNotEmpty()) state.originalQueue else state.queue)
            .toMutableList()
            .apply {
                val origIdx = indexOfFirst { it.uriString == removedItem.uriString }
                if (origIdx >= 0) removeAt(origIdx)
            }

        if (currentQueue.isEmpty()) {
            stopProgressPolling()
            activePlayer?.let { player ->
                runCatching {
                    player.playWhenReady = false
                    player.stop()
                    player.clearMediaItems()
                }
            }
            _playbackState.value = state.copy(
                currentMedia = null,
                queue = emptyList(),
                originalQueue = emptyList(),
                currentQueueIndex = -1,
                isPlaying = false,
                isBuffering = false,
                currentPositionMs = 0L,
                durationMs = 0L
            )
            return
        }

        activePlayer?.let { player ->
            if (index in 0 until player.mediaItemCount) {
                player.removeMediaItem(index)
            }
        }

        val resolvedQueueTitle = MediaFileActionsHelper.computeQueueSourceTitle(
            context = getApplication(),
            queue = currentQueue,
            explicitQueueTitle = state.queueTitle
        )

        when {
            index < state.currentQueueIndex -> {
                // Removed a track before the currently playing track -> shift index back by 1, keep playing uninterrupted
                val newCurrentIdx = (state.currentQueueIndex - 1).coerceAtLeast(0)
                _playbackState.value = state.copy(
                    queue = currentQueue,
                    originalQueue = updatedOriginal,
                    queueTitle = resolvedQueueTitle,
                    currentQueueIndex = newCurrentIdx
                )
            }
            index > state.currentQueueIndex -> {
                // Removed a track after the currently playing track -> current index & playback unchanged
                _playbackState.value = state.copy(
                    queue = currentQueue,
                    originalQueue = updatedOriginal,
                    queueTitle = resolvedQueueTitle,
                    currentQueueIndex = state.currentQueueIndex
                )
            }
            else -> {
                // Removed the currently playing track -> transition cleanly to the next available track in queue
                val nextIdx = index.coerceIn(0, currentQueue.lastIndex)
                val nextMedia = currentQueue[nextIdx]
                _playbackState.value = state.copy(
                    currentMedia = nextMedia,
                    queue = currentQueue,
                    originalQueue = updatedOriginal,
                    queueTitle = resolvedQueueTitle,
                    currentQueueIndex = nextIdx,
                    currentPositionMs = 0L,
                    durationMs = nextMedia.durationMs,
                    hasRenderedFirstFrame = false
                )
                resetOrLoadCompanionSubtitleForMedia(nextMedia)
                syncAndResolveArtworkForTrack(nextMedia, activePlayer?.currentMediaItem?.mediaMetadata)
            }
        }
    }

    /**
     * Moves a song from [fromIndex] to [toIndex] (via Up/Down buttons or Drag-and-Drop) while keeping
     * the currently playing track's index and playback completely synchronized.
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val state = _playbackState.value
        val currentQueue = state.queue.toMutableList()
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices || fromIndex == toIndex) return
        val moved = currentQueue.removeAt(fromIndex)
        currentQueue.add(toIndex, moved)
        activePlayer?.let { player ->
            if (fromIndex in 0 until player.mediaItemCount && toIndex in 0 until player.mediaItemCount) {
                player.moveMediaItem(fromIndex, toIndex)
            }
        }
        val newCurrentIdx = when (state.currentQueueIndex) {
            fromIndex -> toIndex
            in (minOf(fromIndex, toIndex)..maxOf(fromIndex, toIndex)) -> {
                if (fromIndex < toIndex) state.currentQueueIndex - 1
                else state.currentQueueIndex + 1
            }
            else -> state.currentQueueIndex
        }
        val updatedOriginal = if (!state.shuffleEnabled) currentQueue else state.originalQueue
        _playbackState.value = state.copy(
            queue = currentQueue,
            originalQueue = updatedOriginal,
            currentQueueIndex = newCurrentIdx
        )
    }

    /**
     * Clears the entire playback queue and stops playback cleanly.
     */
    fun clearQueue() {
        stopProgressPolling()
        activePlayer?.let { player ->
            runCatching {
                player.playWhenReady = false
                player.stop()
                player.clearMediaItems()
            }
        }
        _playbackState.value = _playbackState.value.copy(
            currentMedia = null,
            queue = emptyList(),
            originalQueue = emptyList(),
            currentQueueIndex = -1,
            isPlaying = false,
            isBuffering = false,
            currentPositionMs = 0L,
            durationMs = 0L,
            playbackError = null
        )
        postStatusBannerMessage(
            "Cleared playback queue.",
            InAppNotificationType.TEMPORARY_INFO
        )
    }

    fun saveCurrentQueueAsPlaylist(playlistName: String) {
        val queueItems = _playbackState.value.queue
        if (queueItems.isEmpty() || playlistName.isBlank()) return
        viewModelScope.launch {
            val plId = mediaRepository.createPlaylist(
                name = playlistName.trim(),
                description = "Saved from active queue (${queueItems.size} tracks)"
            )
            queueItems.forEach { item ->
                mediaRepository.addMediaToPlaylist(plId, item.uriString)
            }
            postStatusBannerMessage(
                "Saved queue (${queueItems.size} tracks) to playlist '${playlistName.trim()}'",
                InAppNotificationType.TEMPORARY_SUCCESS
            )
        }
    }

    // --- Synchronized Lyrics & Subtitles (SRT & WebVTT for Both Audio & Video) ---
    private fun scheduleTemporarySubtitleErrorDismiss(errorMsg: String) {
        subtitleErrorAutoDismissJob?.cancel()
        subtitleErrorAutoDismissJob = viewModelScope.launch {
            delay(notificationManager.defaultAutoDismissDurationMs)
            if (_playbackState.value.subtitleError == errorMsg) {
                _playbackState.value = _playbackState.value.copy(subtitleError = null)
            }
        }
    }

    private fun resetOrLoadCompanionSubtitleForMedia(media: MediaEntity?) {
        subtitleErrorAutoDismissJob?.cancel()
        // Always clear any subtitle cues from the previous track so stale or sample text never persists
        _playbackState.value = _playbackState.value.copy(
            loadedSubtitleName = null,
            loadedSubtitleFormat = null,
            loadedSubtitleCues = emptyList(),
            activeSubtitleText = null,
            subtitleError = null
        )
        if (media == null || media.isGeneratedSample) return
        val parsedUri = Uri.parse(media.uriString)
        if (parsedUri.scheme != "file") return
        val mediaFile = parsedUri.path?.let { File(it) } ?: return
        val parentDir = mediaFile.parentFile ?: return
        val baseName = mediaFile.nameWithoutExtension
        if (baseName.isBlank() || baseName.equals("Pulse_Cinema_Test_Reel", ignoreCase = true)) return

        val companionFile = listOf(
            File(parentDir, "$baseName.srt"),
            File(parentDir, "$baseName.vtt")
        ).firstOrNull { candidate ->
            if (!candidate.exists() || !candidate.isFile) return@firstOrNull false
            val text = runCatching { candidate.readText() }.getOrDefault("")
            !text.contains("Adjust font scale or sync offset in real time") &&
                !text.contains("PulsePlay WebVTT Subtitle Stream Active") &&
                !text.contains("Welcome to PulsePlay Original Cinema Engine")
        }
        if (companionFile != null) {
            loadSubtitleFromFile(companionFile)
        }
    }

    fun loadSubtitleFromFile(file: File) {
        viewModelScope.launch {
            val raw = runCatching { file.readText() }.getOrDefault("")
            when (val res = SubtitleParser.parseSubtitleContent(raw, file.name)) {
                is SubtitleLoadResult.Success -> {
                    subtitleErrorAutoDismissJob?.cancel()
                    _playbackState.value = _playbackState.value.copy(
                        loadedSubtitleName = res.sourceName,
                        loadedSubtitleFormat = res.format,
                        loadedSubtitleCues = res.cues,
                        subtitleError = null
                    )
                    updateSubtitleCueForPosition(_playbackState.value.currentPositionMs)
                }
                is SubtitleLoadResult.Error -> {
                    _playbackState.value = _playbackState.value.copy(
                        subtitleError = res.reason
                    )
                    scheduleTemporarySubtitleErrorDismiss(res.reason)
                }
            }
        }
    }

    fun loadExternalSubtitleUri(uri: Uri) {
        viewModelScope.launch {
            val res = SubtitleParser.loadAndValidateSubtitleUri(
                getApplication(),
                uri,
                uri.lastPathSegment ?: "External Lyrics/Subtitle"
            )
            when (res) {
                is SubtitleLoadResult.Success -> {
                    subtitleErrorAutoDismissJob?.cancel()
                    _playbackState.value = _playbackState.value.copy(
                        loadedSubtitleName = res.sourceName,
                        loadedSubtitleFormat = res.format,
                        loadedSubtitleCues = res.cues,
                        subtitleError = null
                    )
                    updateSubtitleCueForPosition(_playbackState.value.currentPositionMs)
                    postStatusBannerMessage(
                        "Loaded ${res.format} lyrics/subtitles (${res.cues.size} cues).",
                        InAppNotificationType.TEMPORARY_SUCCESS
                    )
                }
                is SubtitleLoadResult.Error -> {
                    _playbackState.value = _playbackState.value.copy(
                        subtitleError = res.reason
                    )
                    scheduleTemporarySubtitleErrorDismiss(res.reason)
                    postStatusBannerMessage(res.reason, InAppNotificationType.TEMPORARY_WARNING)
                }
            }
        }
    }

    private fun updateSubtitleCueForPosition(positionMs: Long) {
        val state = _playbackState.value
        val prefs = userPreferences.value
        if (!prefs.subtitlesEnabledByDefault || state.loadedSubtitleCues.isEmpty()) {
            if (state.activeSubtitleText != null) {
                _playbackState.value = state.copy(activeSubtitleText = null)
            }
            return
        }
        val text = SubtitleParser.findActiveCueText(
            cues = state.loadedSubtitleCues,
            playbackPositionMs = positionMs,
            syncOffsetMs = prefs.subtitleSyncOffsetMs
        )
        if (text != state.activeSubtitleText) {
            _playbackState.value = state.copy(activeSubtitleText = text)
        }
    }

    private fun updateAvailableAudioTracks(tracks: Tracks) {
        val list = mutableListOf<AudioTrackOption>()
        tracks.groups.forEachIndexed { groupIdx, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (t in 0 until group.length) {
                    val fmt = group.getTrackFormat(t)
                    val label = fmt.label ?: "Audio Stream #${t + 1}"
                    val lang = fmt.language ?: "und"
                    list.add(
                        AudioTrackOption(
                            groupIndex = groupIdx,
                            trackIndex = t,
                            label = "$label (${fmt.sampleMimeType ?: "audio"})",
                            language = lang,
                            isSelected = group.isTrackSelected(t)
                        )
                    )
                }
            }
        }
        _playbackState.value = _playbackState.value.copy(availableAudioTracks = list)
    }

    fun selectAudioTrack(option: AudioTrackOption) {
        val player = activePlayer ?: return
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        if (option.trackIndex !in 0 until group.length) return
        val override = TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .build()
    }

    private var isAppInForeground: Boolean = true
 
    fun onAppForegrounded() {
        isAppInForeground = true
        val nowHasAccess = mediaRepository.hasDeviceMediaAccess()
        if (nowHasAccess && !_hasMediaPermission.value) {
            _hasMediaPermission.value = true
            _permissionPermanentlyDenied.value = false
            refreshLibrary()
        }
        if (activePlayer?.isPlaying == true) {
            startProgressPolling()
        }
    }

    fun onAppBackgrounded(isEnteringPictureInPicture: Boolean = false) {
        if (isEnteringPictureInPicture) {
            return
        }
        isAppInForeground = false
        stopProgressPolling()
        persistCurrentPlaybackPosition(force = true)
        if (!userPreferences.value.backgroundPlaybackEnabled && activePlayer?.isPlaying == true) {
            activePlayer?.pause()
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
        }
    }

    private fun stopProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = null
    }

    private fun startProgressPolling() {
        if (!isAppInForeground) {
            // When app is in background, do not run high-frequency UI polling
            return
        }
        progressPollingJob?.cancel()
        progressPollingJob = viewModelScope.launch {
            while (isActive) {
                val player = activePlayer
                if (player != null && player.isPlaying) {
                    val rawPos = player.currentPosition.coerceAtLeast(0L)
                    val stateSnap = _playbackState.value
                    val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                        ?: stateSnap.durationMs
                    val pos = if (stateSnap.isAbRepeatActive &&
                        stateSnap.abRepeatEndMs != null &&
                        stateSnap.abRepeatStartMs != null &&
                        rawPos >= stateSnap.abRepeatEndMs
                    ) {
                        val loopStart = stateSnap.abRepeatStartMs.coerceAtLeast(0L)
                        player.seekTo(loopStart)
                        loopStart
                    } else {
                        rawPos
                    }
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur
                    )
                    updateSubtitleCueForPosition(pos)

                    // Apply smooth Crossfade envelope when Crossfade > 0s on audio tracks
                    val crossfadeSec = userPreferences.value.crossfadeDurationSeconds
                    val baseVol = _playbackState.value.volumeGestureLevel.coerceIn(0f, 1f)
                    if (crossfadeSec > 0 &&
                        stateSnap.currentMedia?.mediaType == MediaType.AUDIO &&
                        dur > (crossfadeSec * 2000L)
                    ) {
                        val fadeWindowMs = crossfadeSec * 1000L
                        val remainingTrackMs = (dur - pos).coerceAtLeast(0L)
                        val envelope = when {
                            pos in 0L..fadeWindowMs -> (pos.toFloat() / fadeWindowMs.toFloat()).coerceIn(0.20f, 1f)
                            remainingTrackMs in 0L..fadeWindowMs -> (remainingTrackMs.toFloat() / fadeWindowMs.toFloat()).coerceIn(0.15f, 1f)
                            else -> 1f
                        }
                        player.volume = (baseVol * envelope).coerceIn(0f, 1f)
                    } else if (player.volume != baseVol) {
                        player.volume = baseVol
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastHistoryWriteTimeMs >= 10_000L) {
                        persistCurrentPlaybackPosition(force = false)
                        lastHistoryWriteTimeMs = now
                        if (_playbackState.value.currentMedia?.mediaType == MediaType.VIDEO) {
                            Resilient4KCodecEngine.recordLongPlaybackHealthCheck(
                                context = getApplication(),
                                droppedFramesDelta = 0,
                                avSyncDriftMs = 0L
                            )
                        }
                    }
                } else {
                    break
                }
                // Throttle: 350ms for smooth seekbar/subtitles when expanded; 750ms for mini-player
                val intervalMs = if (_isNowPlayingExpanded.value) 350L else 750L
                delay(intervalMs)
            }
            progressPollingJob = null
        }
    }

    private fun persistCurrentPlaybackPosition(force: Boolean) {
        val current = _playbackState.value.currentMedia ?: return
        val pos = _playbackState.value.currentPositionMs
        val dur = _playbackState.value.durationMs
        if (!force && pos < 1000L) return
        viewModelScope.launch {
            val prefs = userPreferences.first()
            if (prefs.recordPlaybackHistory) {
                mediaRepository.recordPlaybackProgress(current, pos, dur)
            }
        }
    }

    // --- Favorites & Playlists ---
    fun toggleFavorite(media: MediaEntity) {
        viewModelScope.launch {
            val added = mediaRepository.toggleFavorite(media.uriString)
            postStatusBannerMessage(
                if (added) {
                    "Added '${media.title}' to Favorites"
                } else {
                    "Removed '${media.title}' from Favorites"
                },
                InAppNotificationType.TEMPORARY_SUCCESS
            )
        }
    }

    fun createNewPlaylist(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            mediaRepository.createPlaylist(name, description)
            postStatusBannerMessage("Created playlist '$name'", InAppNotificationType.TEMPORARY_SUCCESS)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            mediaRepository.deletePlaylist(playlistId)
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
            postStatusBannerMessage("Playlist deleted", InAppNotificationType.TEMPORARY_INFO)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, media: MediaEntity) {
        viewModelScope.launch {
            mediaRepository.addMediaToPlaylist(playlistId, media.uriString)
            postStatusBannerMessage("Added '${media.title}' to playlist", InAppNotificationType.TEMPORARY_SUCCESS)
        }
    }

    // --- Equalizer & Audio DSP Actions ---
    fun setEqualizerEnabled(enabled: Boolean) {
        hasExplicitEqualizerMutation = true
        equalizerController.setEnabled(enabled)
        saveEqualizerToPreferences()
    }

    fun selectEqualizerPreset(presetIndex: Int) {
        hasExplicitEqualizerMutation = true
        equalizerController.applyPreset(presetIndex)
        saveEqualizerToPreferences()
    }

    fun updateEqualizerBand(bandIndex: Int, levelMb: Int) {
        hasExplicitEqualizerMutation = true
        equalizerController.setBandLevel(bandIndex, levelMb)
        saveEqualizerToPreferences()
    }

    fun updateBassBoost(strength: Int) {
        hasExplicitEqualizerMutation = true
        equalizerController.setBassBoost(strength)
        saveEqualizerToPreferences()
    }

    fun setPreampGainDb(gainDb: Float) {
        hasExplicitEqualizerMutation = true
        equalizerController.setPreampGainDb(gainDb)
        launchPreferenceWrite {
            preferencesRepository.setPreampGainDb(gainDb)
        }
    }

    fun cycleBassBoostQuick() {
        hasExplicitEqualizerMutation = true
        val steps = listOf(0, 250, 500, 750, 1000)
        val current = equalizerController.state.value.bassBoostStrength
        val next = steps.firstOrNull { it > current } ?: 0
        if (next > 0 && !equalizerController.state.value.isEnabled) {
            equalizerController.setEnabled(true)
        }
        equalizerController.setBassBoost(next)
        saveEqualizerToPreferences()
        postStatusBannerMessage(
            if (next == 0) "Bass Boost: Off" else "Bass Boost: ${next / 10}%",
            InAppNotificationType.TEMPORARY_INFO
        )
    }

    private fun saveEqualizerToPreferences() {
        val eqState = equalizerController.state.value
        _userPreferences.value = _userPreferences.value.copy(
            equalizerEnabled = eqState.isEnabled,
            equalizerPresetIndex = eqState.selectedPresetIndex,
            bassBoostStrength = eqState.bassBoostStrength,
            customBandLevelsMb = eqState.bands.map { it.currentLevelMb }
        )
        launchPreferenceWrite {
            preferencesRepository.setEqualizerState(
                enabled = eqState.isEnabled,
                presetIndex = eqState.selectedPresetIndex,
                bassBoostStrength = eqState.bassBoostStrength,
                bandLevelsMb = eqState.bands.map { it.currentLevelMb }
            )
        }
    }

    private fun launchPreferenceWrite(block: suspend () -> Unit) {
        pendingPreferenceWrites.incrementAndGet()
        viewModelScope.launch {
            try {
                block()
            } finally {
                pendingPreferenceWrites.decrementAndGet()
            }
        }
    }

    // --- Media Editor & Converter Actions ---
    fun runAudioTrimJob(
        source: MediaEntity,
        startMs: Long,
        endMs: Long,
        outputExt: String
    ) {
        activeProcessingJob?.cancel()
        activeProcessingJob = viewModelScope.launch {
            val outcome = processorEngine.executeAudioTrim(source, startMs, endMs, outputExt)
            when (outcome) {
                is ProcessingOutcome.Success -> {
                    postStatusBannerMessage(outcome.summary, InAppNotificationType.TEMPORARY_SUCCESS)
                }
                is ProcessingOutcome.Failure -> {
                    postStatusBannerMessage(outcome.errorMessage, InAppNotificationType.PERSISTENT_ERROR)
                }
            }
        }
    }

    fun runVideoTrimAndRotateJob(
        source: MediaEntity,
        startMs: Long,
        endMs: Long,
        rotationDegrees: Int,
        cropPreset: String
    ) {
        activeProcessingJob?.cancel()
        activeProcessingJob = viewModelScope.launch {
            val outcome = processorEngine.executeVideoTrimAndRotate(
                source,
                startMs,
                endMs,
                rotationDegrees,
                cropPreset
            )
            when (outcome) {
                is ProcessingOutcome.Success -> {
                    postStatusBannerMessage(outcome.summary, InAppNotificationType.TEMPORARY_SUCCESS)
                }
                is ProcessingOutcome.Failure -> {
                    postStatusBannerMessage(outcome.errorMessage, InAppNotificationType.PERSISTENT_ERROR)
                }
            }
        }
    }

    fun runConversionOrExtractionJob(
        source: MediaEntity,
        profileId: String,
        bitrateKbps: Int,
        isExtractionFromVideo: Boolean
    ) {
        activeProcessingJob?.cancel()
        activeProcessingJob = viewModelScope.launch {
            val outcome = processorEngine.executeAudioExtractionOrConversion(
                sourceMedia = source,
                profileId = profileId,
                targetBitrateKbps = bitrateKbps,
                isExtractionFromVideo = isExtractionFromVideo
            )
            when (outcome) {
                is ProcessingOutcome.Success -> {
                    postStatusBannerMessage(outcome.summary, InAppNotificationType.TEMPORARY_SUCCESS)
                }
                is ProcessingOutcome.Failure -> {
                    postStatusBannerMessage(outcome.errorMessage, InAppNotificationType.PERSISTENT_ERROR)
                }
            }
        }
    }

    fun cancelActiveProcessingJob() {
        activeProcessingJob?.cancel()
        activeProcessingJob = null
        postStatusBannerMessage(
            "Active media processing job cancelled.",
            InAppNotificationType.TEMPORARY_INFO
        )
    }

    fun clearCompletedProcessingJobs() {
        viewModelScope.launch {
            dao.clearCompletedJobs()
        }
    }

    // --- Preferences & Privacy Actions ---
    fun setThemeMode(mode: AppThemeMode) {
        _userPreferences.value = _userPreferences.value.copy(themeMode = mode)
        launchPreferenceWrite { preferencesRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        _userPreferences.value = _userPreferences.value.copy(dynamicColor = enabled)
        launchPreferenceWrite { preferencesRepository.setDynamicColor(enabled) }
    }

    fun setDefaultPlaybackSpeedPref(speed: Float) {
        _userPreferences.value = _userPreferences.value.copy(defaultPlaybackSpeed = speed)
        launchPreferenceWrite {
            preferencesRepository.setDefaultPlaybackSpeed(speed)
            setPlaybackSpeed(speed)
        }
    }

    fun setResumePlaybackEnabled(enabled: Boolean) {
        _userPreferences.value = _userPreferences.value.copy(resumePlaybackEnabled = enabled)
        launchPreferenceWrite { preferencesRepository.setResumePlaybackEnabled(enabled) }
    }

    fun setRecordPlaybackHistory(enabled: Boolean) {
        _userPreferences.value = _userPreferences.value.copy(recordPlaybackHistory = enabled)
        launchPreferenceWrite { preferencesRepository.setRecordPlaybackHistory(enabled) }
    }

    fun setKeepScreenOnDuringVideo(enabled: Boolean) {
        _userPreferences.value = _userPreferences.value.copy(keepScreenOnDuringVideo = enabled)
        launchPreferenceWrite { preferencesRepository.setKeepScreenOnDuringVideo(enabled) }
    }

    fun setBackgroundPlaybackEnabled(enabled: Boolean) {
        _userPreferences.value = _userPreferences.value.copy(backgroundPlaybackEnabled = enabled)
        launchPreferenceWrite { preferencesRepository.setBackgroundPlaybackEnabled(enabled) }
    }

    fun setVideoGestureControlsEnabled(enabled: Boolean) {
        _userPreferences.value = _userPreferences.value.copy(videoGestureControlsEnabled = enabled)
        launchPreferenceWrite { preferencesRepository.setVideoGestureControlsEnabled(enabled) }
    }

    fun setVideoOrientationPreference(pref: VideoOrientationPreference) {
        _userPreferences.value = _userPreferences.value.copy(videoOrientationPreference = pref)
        launchPreferenceWrite { preferencesRepository.setVideoOrientationPreference(pref) }
    }

    fun cycleVideoOrientationPreference() {
        val current = userPreferences.value.videoOrientationPreference
        val entries = VideoOrientationPreference.entries
        val next = entries[(entries.indexOf(current) + 1) % entries.size]
        _userPreferences.value = _userPreferences.value.copy(videoOrientationPreference = next)
        launchPreferenceWrite {
            preferencesRepository.setVideoOrientationPreference(next)
        }
    }

    fun setAudioBalance(balance: Float) {
        hasExplicitEqualizerMutation = true
        val clamped = balance.coerceIn(-1.0f, 1.0f)
        _userPreferences.value = _userPreferences.value.copy(audioBalance = clamped)
        equalizerController.setStereoBalance(clamped)
        launchPreferenceWrite { preferencesRepository.setAudioBalance(clamped) }
    }

    fun setCrossfadeDurationSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        _userPreferences.value = _userPreferences.value.copy(crossfadeDurationSeconds = clamped)
        launchPreferenceWrite {
            preferencesRepository.setCrossfadeDurationSeconds(clamped)
        }
        postStatusBannerMessage(
            if (clamped == 0) "Crossfade: Off" else "Crossfade set to ${clamped}s",
            InAppNotificationType.TEMPORARY_INFO
        )
    }

    fun cycleCrossfadeDuration() {
        val steps = listOf(0, 2, 4, 6, 8, 12)
        val current = userPreferences.value.crossfadeDurationSeconds
        val next = steps[(steps.indexOf(current).takeIf { it >= 0 }?.plus(1) ?: 1) % steps.size]
        setCrossfadeDurationSeconds(next)
    }

    fun setGaplessPlaybackEnabled(enabled: Boolean) {
        _userPreferences.value = _userPreferences.value.copy(gaplessPlaybackEnabled = enabled)
        (activePlayer as? ExoPlayer)?.pauseAtEndOfMediaItems = !enabled
        launchPreferenceWrite {
            preferencesRepository.setGaplessPlaybackEnabled(enabled)
        }
        postStatusBannerMessage(
            if (enabled) "Gapless Playback enabled" else "Gapless Playback disabled",
            InAppNotificationType.TEMPORARY_INFO
        )
    }

    fun setPlayerShowWaveform(enabled: Boolean) {
        _userPreferences.value = _userPreferences.value.copy(playerShowWaveform = enabled)
        launchPreferenceWrite { preferencesRepository.setPlayerShowWaveform(enabled) }
    }

    fun setPlayerShowQuickActionRow(enabled: Boolean) {
        _userPreferences.value = _userPreferences.value.copy(playerShowQuickActionRow = enabled)
        launchPreferenceWrite { preferencesRepository.setPlayerShowQuickActionRow(enabled) }
    }

    fun setPlayerShowRemainingTime(enabled: Boolean) {
        _userPreferences.value = _userPreferences.value.copy(playerShowRemainingTime = enabled)
        launchPreferenceWrite { preferencesRepository.setPlayerShowRemainingTime(enabled) }
    }

    fun setPlayerShowAbAndBookmarkStrip(enabled: Boolean) {
        _userPreferences.value = _userPreferences.value.copy(playerShowAbAndBookmarkStrip = enabled)
        launchPreferenceWrite { preferencesRepository.setPlayerShowAbAndBookmarkStrip(enabled) }
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSubtitlesEnabled(enabled)
            updateSubtitleCueForPosition(_playbackState.value.currentPositionMs)
        }
    }

    fun setSubtitleFontScale(scale: Float) {
        viewModelScope.launch { preferencesRepository.setSubtitleFontScale(scale) }
    }

    fun setSubtitleSyncOffsetMs(offsetMs: Long) {
        viewModelScope.launch {
            preferencesRepository.setSubtitleSyncOffsetMs(offsetMs)
            updateSubtitleCueForPosition(_playbackState.value.currentPositionMs)
        }
    }

    fun setDefaultSortOption(option: SortOption) {
        viewModelScope.launch { preferencesRepository.setDefaultSortOption(option) }
    }

    fun setHardwareDecoderPreference(enabled: Boolean) {
        val prefs = userPreferences.value
        Resilient4KCodecEngine.updateConfiguredPlaybackMode(
            mode = prefs.highResVideoPlaybackMode,
            legacyAllowSoftwareFallback = prefs.allowExperimental4kSoftwareFallback,
            hardwareDecoderPreference = enabled
        )
        viewModelScope.launch {
            preferencesRepository.setHardwareDecoderPreference(enabled)
        }
    }

    fun setAllowExperimental4kSoftwareFallback(enabled: Boolean) {
        val prefs = userPreferences.value
        Resilient4KCodecEngine.updateConfiguredPlaybackMode(
            mode = prefs.highResVideoPlaybackMode,
            legacyAllowSoftwareFallback = enabled,
            hardwareDecoderPreference = prefs.hardwareDecoderPreferenceEnabled
        )
        viewModelScope.launch {
            preferencesRepository.setAllowExperimental4kSoftwareFallback(enabled)
        }
    }

    fun setHighResVideoPlaybackMode(mode: HighResVideoPlaybackMode) {
        val app = getApplication<Application>()
        val prefs = userPreferences.value
        Resilient4KCodecEngine.updateConfiguredPlaybackMode(
            mode = mode,
            legacyAllowSoftwareFallback = prefs.allowExperimental4kSoftwareFallback,
            hardwareDecoderPreference = prefs.hardwareDecoderPreferenceEnabled
        )
        viewModelScope.launch {
            preferencesRepository.setHighResVideoPlaybackMode(mode)
        }
        val currentMedia = _playbackState.value.currentMedia
        val updatedEval = if (currentMedia?.mediaType == MediaType.VIDEO) {
            Resilient4KCodecEngine.evaluateVideoStreamCapability(
                context = app,
                media = currentMedia,
                selectedMode = mode
            )
        } else {
            _playbackState.value.videoCapabilityEvaluation
        }
        _playbackState.value = _playbackState.value.copy(
            videoCapabilityEvaluation = updatedEval
        )
        postStatusBannerMessage(
            "Video playback mode: ${mode.label}",
            InAppNotificationType.TEMPORARY_INFO
        )
    }

    fun recheckDeviceCapabilities() {
        if (_isScanningCapabilities.value) return
        _isScanningCapabilities.value = true
        viewModelScope.launch {
            val updated = withContext(Dispatchers.Default) {
                DeviceCapabilityDetector.forceRedetectCapabilities(getApplication())
            }
            _deviceCapabilities.value = updated
            _isScanningCapabilities.value = false
            postStatusBannerMessage(
                "Device video playback capabilities rechecked.",
                InAppNotificationType.TEMPORARY_SUCCESS
            )
        }
    }

    fun setOptionalAdsConfig(enabled: Boolean, consentGranted: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOptionalAdsConfiguration(enabled, consentGranted)
        }
    }

    fun clearPlaybackHistoryOnly() {
        viewModelScope.launch {
            mediaRepository.clearPlaybackHistoryOnly()
            postStatusBannerMessage(
                "Cleared local playback history. Your media files remain untouched.",
                InAppNotificationType.TEMPORARY_SUCCESS
            )
        }
    }

    fun resetAllPreferences() {
        viewModelScope.launch {
            preferencesRepository.resetAllPreferences()
            val resolvedId = resolveActiveAudioSessionId(activePlayer)
            equalizerController.setStereoBalance(0.0f)
            equalizerController.setPreampGainDb(0.0f)
            equalizerController.bindToAudioSession(
                audioSessionId = resolvedId,
                enabled = false,
                presetIndex = 0,
                bassStrength = 0,
                savedBandLevelsMb = listOf(0, 0, 0, 0, 0),
                isPlaybackActive = activePlayer?.isPlaying == true
            )
            postStatusBannerMessage(
                "All application preferences reset to defaults.",
                InAppNotificationType.TEMPORARY_SUCCESS
            )
        }
    }

    // --- GitHub Release Auto-Updater Actions ---
    fun setGitHubRepositorySlug(slug: String) {
        viewModelScope.launch {
            preferencesRepository.setGitHubRepositorySlug(slug.trim())
            postStatusBannerMessage(
                if (slug.isBlank()) {
                    "Cleared custom GitHub repository slug."
                } else {
                    "Saved GitHub repository: ${AppUpdateManager.normalizeRepositorySlug(slug)}"
                },
                InAppNotificationType.TEMPORARY_SUCCESS
            )
        }
    }

    fun setIncludePrereleaseUpdates(include: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setIncludePrereleaseUpdates(include)
        }
    }

    fun selectUpdateChannel(channel: UpdateDistributionChannel) {
        updateManager.selectChannel(channel)
        // Switching providers must not trigger the other provider's update flow or leave stale status messages visible
        dismissStatusBanner()
        viewModelScope.launch {
            preferencesRepository.setSelectedUpdateChannel(channel)
        }
    }

    fun checkForAppUpdates() {
        activeUpdateJob?.cancel()
        activeUpdateJob = viewModelScope.launch {
            val prefs = preferencesRepository.preferencesFlow.first()
            val result = updateManager.checkGitHubUpdates(
                configuredRepositorySlug = prefs.githubRepositorySlug,
                includePrereleases = prefs.includePrereleaseUpdates
            )
            when (result) {
                is UpdateCheckState.UpdateAvailable -> {
                    postStatusBannerMessage(
                        "Update available: ${result.releaseInfo.tagName} (${result.releaseInfo.apkAsset.name})",
                        InAppNotificationType.TEMPORARY_INFO
                    )
                }
                is UpdateCheckState.UpToDate -> {
                    postStatusBannerMessage(
                        "PulsePlay v${result.installedVersionName} is up to date.",
                        InAppNotificationType.TEMPORARY_SUCCESS
                    )
                }
                is UpdateCheckState.ConfigurationRequired -> {
                    postStatusBannerMessage(
                        result.message,
                        InAppNotificationType.TEMPORARY_WARNING
                    )
                }
                is UpdateCheckState.VerificationFailed -> {
                    postStatusBannerMessage(
                        result.reason,
                        InAppNotificationType.PERSISTENT_ERROR
                    )
                }
                is UpdateCheckState.NetworkError -> {
                    postStatusBannerMessage(
                        result.reason,
                        InAppNotificationType.PERSISTENT_ERROR
                    )
                }
                else -> {}
            }
        }
    }

    fun downloadAvailableUpdate(releaseInfo: GitHubReleaseInfo) {
        activeUpdateJob?.cancel()
        activeUpdateJob = viewModelScope.launch {
            val result = updateManager.downloadAndVerifyGitHubUpdate(releaseInfo)
            when (result) {
                is UpdateCheckState.ReadyToInstall -> {
                    postStatusBannerMessage(
                        "Verified ${result.releaseInfo.apkAsset.name} (SHA-256 OK). Ready to install.",
                        InAppNotificationType.TEMPORARY_SUCCESS
                    )
                }
                is UpdateCheckState.VerificationFailed -> {
                    postStatusBannerMessage(result.reason, InAppNotificationType.PERSISTENT_ERROR)
                }
                is UpdateCheckState.DownloadFailed -> {
                    postStatusBannerMessage(result.reason, InAppNotificationType.PERSISTENT_ERROR)
                }
                else -> {}
            }
        }
    }

    fun cancelUpdateDownload() {
        updateManager.cancelGitHubDownload()
        activeUpdateJob?.cancel()
        activeUpdateJob = null
        postStatusBannerMessage("Update download cancelled.", InAppNotificationType.TEMPORARY_INFO)
    }

    fun installVerifiedUpdate() {
        val launched = updateManager.installVerifiedGitHubUpdate()
        if (!launched) {
            val st = updateManager.gitHubState.value
            if (st is UpdateCheckState.InstallationPending && st.requiresUnknownSourcesPermission) {
                postStatusBannerMessage(st.statusMessage, InAppNotificationType.PERSISTENT_ERROR)
            } else if (st is UpdateCheckState.VerificationFailed) {
                postStatusBannerMessage(st.reason, InAppNotificationType.PERSISTENT_ERROR)
            }
        }
    }

    fun checkForPlayStoreUpdates() {
        activeUpdateJob?.cancel()
        activeUpdateJob = viewModelScope.launch {
            val result = updateManager.checkPlayStoreUpdates()
            when (result) {
                is PlayStoreUpdateState.UpdateAvailable -> {
                    postStatusBannerMessage(result.statusMessage, InAppNotificationType.TEMPORARY_INFO)
                }
                is PlayStoreUpdateState.NoUpdateAvailable -> {
                    postStatusBannerMessage(result.statusMessage, InAppNotificationType.TEMPORARY_SUCCESS)
                }
                is PlayStoreUpdateState.DownloadedReadyToComplete -> {
                    postStatusBannerMessage(result.promptMessage, InAppNotificationType.TEMPORARY_INFO)
                }
                is PlayStoreUpdateState.DeveloperTriggeredUpdateInProgress -> {
                    postStatusBannerMessage(result.statusMessage, InAppNotificationType.TEMPORARY_INFO)
                }
                is PlayStoreUpdateState.PlayStoreUnavailableOrUnpublished -> {
                    postStatusBannerMessage(result.reason, InAppNotificationType.TEMPORARY_WARNING)
                }
                is PlayStoreUpdateState.UpdateFailed -> {
                    postStatusBannerMessage(result.reason, InAppNotificationType.PERSISTENT_ERROR)
                }
                else -> {}
            }
        }
    }

    fun startPlayFlexibleUpdate(activity: android.app.Activity?) {
        if (activity == null) return
        updateManager.startPlayFlexibleUpdate(activity)
    }

    fun startPlayImmediateUpdate(activity: android.app.Activity?) {
        if (activity == null) return
        updateManager.startPlayImmediateUpdate(activity)
    }

    fun completePlayFlexibleUpdate() {
        viewModelScope.launch {
            val completed = updateManager.completePlayFlexibleUpdate()
            if (completed) {
                postStatusBannerMessage(
                    "Completing Google Play update installation...",
                    InAppNotificationType.TEMPORARY_INFO
                )
            }
        }
    }

    fun openOfficialPlayStoreListing() {
        val opened = updateManager.openOfficialPlayStoreListing()
        if (!opened) {
            postStatusBannerMessage(
                "Unable to open Google Play Store listing on this device.",
                InAppNotificationType.TEMPORARY_WARNING
            )
        }
    }

    fun checkAndResumePlayUpdateIfNeeded(activity: android.app.Activity?) {
        viewModelScope.launch {
            updateManager.onActivityResumed(activity)
        }
    }

    private fun filterAndSortMedia(
        items: List<MediaEntity>,
        query: String,
        sortOption: SortOption
    ): List<MediaEntity> {
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            items
        } else {
            items.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.artist.contains(trimmed, ignoreCase = true) ||
                    it.album.contains(trimmed, ignoreCase = true) ||
                    it.folderName.contains(trimmed, ignoreCase = true)
            }
        }
        return when (sortOption) {
            SortOption.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOption.DATE_DESC -> filtered.sortedByDescending { it.dateAddedEpochSec }
            SortOption.DURATION_DESC -> filtered.sortedByDescending { it.durationMs }
            SortOption.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
        }
    }

    override fun onCleared() {
        progressPollingJob?.cancel()
        sleepTimerJob?.cancel()
        activeProcessingJob?.cancel()
        activeUpdateJob?.cancel()
        artworkResolutionJob?.cancel()
        subtitleErrorAutoDismissJob?.cancel()
        notificationManager.cancelPendingJobs()
        updateManager.cancelPendingDismissJobs()
        equalizerController.releaseEffects()
        activePlayer?.removeListener(playerListener)
        activePlayer = null
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        mediaControllerFuture = null
        fallbackLocalPlayer?.release()
        fallbackLocalPlayer = null
        super.onCleared()
    }
}
