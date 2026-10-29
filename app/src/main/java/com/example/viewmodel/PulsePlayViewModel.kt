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
import com.example.data.preferences.SortOption
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.UserPreferencesState
import com.example.data.preferences.VideoOrientationPreference
import com.example.data.repository.MediaLibraryRepository
import com.example.media.ConversionCapabilityRegistry
import com.example.media.ConversionProfile
import com.example.media.EqualizerController
import com.example.media.HardwareEqualizerState
import com.example.media.MediaArtworkLoader
import com.example.media.MediaProcessorEngine
import com.example.media.PlayerHandoffAndVideoPolicy
import com.example.media.ProcessingOutcome
import com.example.media.Resilient4KCodecEngine
import com.example.media.SampleMediaGenerator
import com.example.media.SubtitleCueItem
import com.example.media.SubtitleFormat
import com.example.media.SubtitleLoadResult
import com.example.media.SubtitleParser
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
    val currentArtworkSource: String = "none"
) {
    val isSleepTimerActive: Boolean
        get() = sleepTimerRemainingMs > 0L || sleepTimerStopAtEndOfTrack

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

    val notificationManager = InAppNotificationManager(viewModelScope)
    val statusBannerNotification: StateFlow<InAppNotification?> = notificationManager.currentNotification
    val statusBannerMessage: StateFlow<String?> = notificationManager.currentMessage

    // Preferences & Equalizer State
    val userPreferences: StateFlow<UserPreferencesState> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesState())

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

            if (playbackState == Player.STATE_ENDED && _playbackState.value.sleepTimerStopAtEndOfTrack) {
                triggerSleepTimerAutoStop("Sleep Timer: Stopped automatically at end of track.")
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
            _playbackState.value = _playbackState.value.copy(
                currentMedia = nextEntity,
                currentQueueIndex = actualQueueIdx,
                hasRenderedFirstFrame = false,
                playbackError = null
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
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            isBuffering = false,
            currentMedia = currentMedia,
            currentQueueIndex = savedIndex,
            currentPositionMs = savedPos,
            isDecoderVerified = false,
            decoderRecoveryAttempts = attemptsSoFar,
            decoderStatusLabel = if (isDecoderFailure) "Decoder Init Failed" else "Playback Error",
            playbackError = diagnosticMsg
        )
    }

    private fun buildDetailedPlaybackDiagnosticError(
        error: PlaybackException,
        recoveryAttempts: Int
    ): String {
        val state = _playbackState.value
        val media = state.currentMedia
        val telemetry = Resilient4KCodecEngine.latestTelemetry.value
        val scheme = media?.uriString?.let { runCatching { Uri.parse(it).scheme }.getOrNull() } ?: "unknown"
        val mime = media?.mimeType ?: telemetry.sourceMimeType.ifBlank { "unknown" }
        val w = state.decodedVideoWidth.takeIf { it > 0 } ?: media?.width ?: 0
        val h = state.decodedVideoHeight.takeIf { it > 0 } ?: media?.height ?: 0
        val dimText = if (w > 0 && h > 0) "${w}x${h}" else "unknown-dim"
        val fpsText = if (telemetry.sourceFrameRate > 0f) "${telemetry.sourceFrameRate.toInt()}fps" else "unknown-fps"
        val decoderName = telemetry.selectedDecoderName ?: state.verifiedDecoderName ?: "none"
        val hwSw = when {
            telemetry.selectedDecoderName == null && state.verifiedDecoderName == null -> "uninitialized"
            telemetry.isSoftwareFallbackActive -> "SW-fallback"
            telemetry.isHardwareAccelerated -> "HW"
            else -> "SW"
        }
        val surfaceInfo = "surface=${if (state.hasVideoSurface) "attached" else "detached"}, firstFrame=${state.hasRenderedFirstFrame}"
        val telemetryFailure = telemetry.failureReason?.takeIf { it.isNotBlank() }

        return buildString {
            append("Playback error (${error.errorCodeName}): ")
            if (telemetryFailure != null) {
                append(telemetryFailure).append(" ")
            } else {
                append("The file may be corrupt, moved, or use an unsupported codec. ")
            }
            append("[URI=$scheme, MIME=$mime, res=$dimText@$fpsText, decoder=$decoderName ($hwSw), $surfaceInfo, retries=$recoveryAttempts]")
        }
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
        viewModelScope.launch {
            val initialPrefs = preferencesRepository.preferencesFlow.first()
            initialPrefs.selectedUpdateChannel?.let { savedChannel ->
                updateManager.selectChannel(savedChannel)
            }
            Resilient4KCodecEngine.allowExperimental4kSoftwareFallback =
                initialPrefs.allowExperimental4kSoftwareFallback
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

            PulsePlaybackService.serviceAudioSessionId.collect { serviceSessionId ->
                val prefs = preferencesRepository.preferencesFlow.first()
                Resilient4KCodecEngine.allowExperimental4kSoftwareFallback =
                    prefs.allowExperimental4kSoftwareFallback
                equalizerController.setStereoBalance(prefs.audioBalance)
                equalizerController.setPreampGainDb(prefs.preampGainDb)
                val currentSession = equalizerController.state.value
                val effectiveEnabled = prefs.equalizerEnabled
                val effectiveBands = if (currentSession.bands.any { it.currentLevelMb != 0 }) {
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
                    presetIndex = prefs.equalizerPresetIndex,
                    bassStrength = prefs.bassBoostStrength,
                    savedBandLevelsMb = effectiveBands,
                    isPlaybackActive = activePlayer?.isPlaying == true || PulsePlaybackService.serviceIsPlaying.value
                )
            }
        }
        viewModelScope.launch {
            Resilient4KCodecEngine.latestTelemetry.collect { telemetry ->
                _playbackState.value = _playbackState.value.copy(
                    verifiedDecoderName = telemetry.selectedDecoderName,
                    isDecoderVerified = telemetry.isDecoderVerified,
                    isDecoderHardwareAccelerated = telemetry.isHardwareAccelerated,
                    isSoftwareFallbackDecoder = telemetry.isSoftwareFallbackActive,
                    droppedVideoFrames = telemetry.droppedVideoFrames,
                    decoderStatusLabel = telemetry.diagnosticSummary,
                    decodedVideoWidth = telemetry.sourceWidth,
                    decodedVideoHeight = telemetry.sourceHeight
                )
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
        }
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
    fun playMedia(media: MediaEntity, queueContext: List<MediaEntity> = emptyList()) {
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

            val resolvedQueue = if (queueContext.isNotEmpty()) queueContext else listOf(media)
            val startIndex = resolvedQueue.indexOfFirst { it.uriString == media.uriString }.coerceAtLeast(0)
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
                val cachedArt = if (item.uriString == media.uriString) {
                    activeResolvedArtwork
                } else {
                    MediaArtworkLoader.peekCachedArtworkMetadata(item.uriString)
                }
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
                MediaItem.Builder()
                    .setMediaId(item.uriString)
                    .setUri(Uri.parse(item.uriString))
                    .setMediaMetadata(metaBuilder.build())
                    .build()
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
            Resilient4KCodecEngine.allowExperimental4kSoftwareFallback =
                prefs.allowExperimental4kSoftwareFallback
            if (media.mediaType == MediaType.VIDEO) {
                Resilient4KCodecEngine.resetTelemetryForNewTrack(
                    mimeType = media.mimeType,
                    width = media.width,
                    height = media.height
                )
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

            _playbackState.value = _playbackState.value.copy(
                currentMedia = media,
                queue = resolvedQueue,
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
                currentArtworkSource = activeResolvedArtwork.sourceLabel
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
        val player = activePlayer ?: return
        val next = !_playbackState.value.shuffleEnabled
        player.shuffleModeEnabled = next
        _playbackState.value = _playbackState.value.copy(shuffleEnabled = next)
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
        activePlayer?.volume = next
        _playbackState.value = _playbackState.value.copy(volumeGestureLevel = next)
    }

    fun retryCurrentPlayback() {
        val current = _playbackState.value.currentMedia ?: return
        playMedia(current, _playbackState.value.queue)
    }

    // --- Queue & Playlist Management ---
    fun setQueue(items: List<MediaEntity>, initialIndex: Int = 0) {
        val safeIndex = if (items.isNotEmpty()) initialIndex.coerceIn(0, items.size - 1) else 0
        val current = _playbackState.value
        val activeMedia = items.getOrNull(safeIndex) ?: current.currentMedia
        if (activeMedia != null) {
            decoderRecoveryAttemptsByUri[activeMedia.uriString] = 0
            val parsedUri = Uri.parse(activeMedia.uriString)
            if (parsedUri.scheme == "file") {
                val localFile = parsedUri.path?.let { File(it) }
                if (localFile != null && localFile.exists() && localFile.length() in 0L..31L) {
                    val corruptMsg = "Corrupted or empty media file: '${activeMedia.title}' (${localFile.length()} bytes)."
                    _playbackState.value = current.copy(
                        queue = items,
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
        _playbackState.value = current.copy(
            queue = items,
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

    fun removeQueueItemAt(index: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (index !in currentQueue.indices) return
        currentQueue.removeAt(index)
        activePlayer?.removeMediaItem(index)
        val adjustedCurrentIdx = activePlayer?.currentMediaItemIndex
            ?: _playbackState.value.currentQueueIndex.coerceAtMost((currentQueue.size - 1).coerceAtLeast(0))
        _playbackState.value = _playbackState.value.copy(
            queue = currentQueue,
            currentQueueIndex = adjustedCurrentIdx
        )
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (fromIndex !in currentQueue.indices || toIndex !in currentQueue.indices || fromIndex == toIndex) return
        val moved = currentQueue.removeAt(fromIndex)
        currentQueue.add(toIndex, moved)
        activePlayer?.moveMediaItem(fromIndex, toIndex)
        val newCurrentIdx = when (_playbackState.value.currentQueueIndex) {
            fromIndex -> toIndex
            in (minOf(fromIndex, toIndex)..maxOf(fromIndex, toIndex)) -> {
                if (fromIndex < toIndex) _playbackState.value.currentQueueIndex - 1
                else _playbackState.value.currentQueueIndex + 1
            }
            else -> _playbackState.value.currentQueueIndex
        }
        _playbackState.value = _playbackState.value.copy(
            queue = currentQueue,
            currentQueueIndex = newCurrentIdx
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
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                        ?: _playbackState.value.durationMs
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur
                    )
                    updateSubtitleCueForPosition(pos)

                    val now = System.currentTimeMillis()
                    if (now - lastHistoryWriteTimeMs >= 10_000L) {
                        persistCurrentPlaybackPosition(force = false)
                        lastHistoryWriteTimeMs = now
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
        equalizerController.setEnabled(enabled)
        saveEqualizerToPreferences()
    }

    fun selectEqualizerPreset(presetIndex: Int) {
        equalizerController.applyPreset(presetIndex)
        saveEqualizerToPreferences()
    }

    fun updateEqualizerBand(bandIndex: Int, levelMb: Int) {
        equalizerController.setBandLevel(bandIndex, levelMb)
        saveEqualizerToPreferences()
    }

    fun updateBassBoost(strength: Int) {
        equalizerController.setBassBoost(strength)
        saveEqualizerToPreferences()
    }

    fun setPreampGainDb(gainDb: Float) {
        equalizerController.setPreampGainDb(gainDb)
        viewModelScope.launch {
            preferencesRepository.setPreampGainDb(gainDb)
        }
    }

    private fun saveEqualizerToPreferences() {
        val eqState = equalizerController.state.value
        viewModelScope.launch {
            preferencesRepository.setEqualizerState(
                enabled = eqState.isEnabled,
                presetIndex = eqState.selectedPresetIndex,
                bassBoostStrength = eqState.bassBoostStrength,
                bandLevelsMb = eqState.bands.map { it.currentLevelMb }
            )
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
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColor(enabled) }
    }

    fun setDefaultPlaybackSpeedPref(speed: Float) {
        viewModelScope.launch {
            preferencesRepository.setDefaultPlaybackSpeed(speed)
            setPlaybackSpeed(speed)
        }
    }

    fun setResumePlaybackEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setResumePlaybackEnabled(enabled) }
    }

    fun setRecordPlaybackHistory(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setRecordPlaybackHistory(enabled) }
    }

    fun setKeepScreenOnDuringVideo(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setKeepScreenOnDuringVideo(enabled) }
    }

    fun setBackgroundPlaybackEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setBackgroundPlaybackEnabled(enabled) }
    }

    fun setVideoGestureControlsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVideoGestureControlsEnabled(enabled) }
    }

    fun setVideoOrientationPreference(pref: VideoOrientationPreference) {
        viewModelScope.launch { preferencesRepository.setVideoOrientationPreference(pref) }
    }

    fun cycleVideoOrientationPreference() {
        viewModelScope.launch {
            val current = userPreferences.value.videoOrientationPreference
            val entries = VideoOrientationPreference.entries
            val next = entries[(entries.indexOf(current) + 1) % entries.size]
            preferencesRepository.setVideoOrientationPreference(next)
        }
    }

    fun setAudioBalance(balance: Float) {
        equalizerController.setStereoBalance(balance)
        viewModelScope.launch { preferencesRepository.setAudioBalance(balance) }
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

    fun setAllowExperimental4kSoftwareFallback(enabled: Boolean) {
        Resilient4KCodecEngine.allowExperimental4kSoftwareFallback = enabled
        viewModelScope.launch {
            preferencesRepository.setAllowExperimental4kSoftwareFallback(enabled)
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
