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
import androidx.media3.common.AudioAttributes
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
import com.example.data.local.JobStatus
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
import com.example.media.MediaProcessorEngine
import com.example.media.PlayerHandoffAndVideoPolicy
import com.example.media.ProcessingOutcome
import com.example.media.SampleMediaGenerator
import com.example.media.SubtitleCueItem
import com.example.media.SubtitleFormat
import com.example.media.SubtitleLoadResult
import com.example.media.SubtitleParser
import com.example.service.PulsePlaybackService
import com.example.ui.theme.AppThemeMode
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
import java.io.File

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
    FILL("Crop / Fill"),
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
    val playbackError: String? = null
)

class PulsePlayViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PulseDatabase.getInstance(application)
    private val dao = database.pulseDao()
    private val sampleGenerator = SampleMediaGenerator(application)
    val mediaRepository = MediaLibraryRepository(application, dao, sampleGenerator)
    val preferencesRepository = UserPreferencesRepository(application)
    val processorEngine = MediaProcessorEngine(application, dao)
    val equalizerController = EqualizerController()

    // Navigation & Overlay States
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

    private val _hasMediaPermission = MutableStateFlow(false)
    val hasMediaPermission: StateFlow<Boolean> = _hasMediaPermission.asStateFlow()

    private val _permissionPermanentlyDenied = MutableStateFlow(false)
    val permissionPermanentlyDenied: StateFlow<Boolean> = _permissionPermanentlyDenied.asStateFlow()

    private val _isScanningLibrary = MutableStateFlow(false)
    val isScanningLibrary: StateFlow<Boolean> = _isScanningLibrary.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

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

    // Single Managed Playback Engine (Phase 6 & 7 requirement: never create duplicate players)
    private var activePlayer: Player? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var fallbackLocalPlayer: ExoPlayer? = null
    private var progressPollingJob: Job? = null
    private var activeProcessingJob: Job? = null
    private var lastHistoryWriteTimeMs: Long = 0L

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
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
                persistCurrentPlaybackPosition(force = true)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = activePlayer ?: return
            val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
                ?: _playbackState.value.currentMedia?.durationMs ?: 0L
            _playbackState.value = _playbackState.value.copy(
                durationMs = dur,
                currentPositionMs = player.currentPosition.coerceAtLeast(0L)
            )
            equalizerController.updatePlaybackActive(
                player.isPlaying || (playbackState == Player.STATE_READY && player.mediaItemCount > 0)
            )
            queryServiceAudioSessionIdIfController(player)
        }

        @OptIn(UnstableApi::class)
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (audioSessionId > 0) {
                viewModelScope.launch {
                    val prefs = userPreferences.first()
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
            val idx = activePlayer?.currentMediaItemIndex ?: -1
            val queue = _playbackState.value.queue
            val matchedFromLibrary = mediaItem?.mediaId?.let { id ->
                allMedia.value.find { it.uriString == id }
            }
            val nextEntity = queue.getOrNull(idx) ?: matchedFromLibrary ?: _playbackState.value.currentMedia
            _playbackState.value = _playbackState.value.copy(
                currentMedia = nextEntity,
                currentQueueIndex = idx,
                playbackError = null
            )
            if (nextEntity != null) {
                autoLoadMatchingSampleSubtitles(nextEntity)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateAvailableAudioTracks(tracks)
        }

        override fun onPlayerError(error: PlaybackException) {
            val friendlyMsg = "Playback error (${error.errorCodeName}): The file may be corrupt, moved, or use an unsupported codec."
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                playbackError = friendlyMsg
            )
        }
    }

    init {
        viewModelScope.launch {
            // Phase 16: Recover any interrupted processing jobs from prior session closure
            dao.recoverInterruptedJobsOnStartup()
            // Ensure legal sample fixtures exist and scan library
            refreshLibrary()
        }
        viewModelScope.launch {
            // Restore persisted Equalizer + Bass Boost settings immediately on startup
            val initialPrefs = preferencesRepository.preferencesFlow.first()
            equalizerController.bindToAudioSession(
                audioSessionId = PulsePlaybackService.serviceAudioSessionId.value,
                enabled = initialPrefs.equalizerEnabled,
                presetIndex = initialPrefs.equalizerPresetIndex,
                bassStrength = initialPrefs.bassBoostStrength,
                savedBandLevelsMb = initialPrefs.customBandLevelsMb,
                isPlaybackActive = PulsePlaybackService.serviceIsPlaying.value
            )

            // Continuously synchronize Equalizer DSP with PulsePlaybackService audioSessionId
            PulsePlaybackService.serviceAudioSessionId.collect { serviceSessionId ->
                val prefs = preferencesRepository.preferencesFlow.first()
                val currentSession = equalizerController.state.value
                val effectiveEnabled = currentSession.isEnabled || prefs.equalizerEnabled
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

        // Immediate local ExoPlayer availability if MediaSessionService is still binding
        if (activePlayer == null) {
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

        // Safely hand off any active queue/playback state from temporary fallbackLocalPlayer to
        // the authoritative MediaController and immediately stop/release fallbackLocalPlayer
        val snapshot = PlayerHandoffAndVideoPolicy.handoffFallbackToAuthoritativePlayer(
            outgoingFallback = previousFallback,
            incomingTarget = player
        )
        if (previousFallback != null && previousFallback !== player) {
            fallbackLocalPlayer = null
        }

        activePlayer = player
        player.addListener(playerListener)

        viewModelScope.launch {
            val prefs = preferencesRepository.preferencesFlow.first()
            if (snapshot.mediaItems.isEmpty()) {
                player.setPlaybackSpeed(prefs.defaultPlaybackSpeed)
            } else {
                // Synchronize UI state if service already had an active queue or transferred from fallback
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

    fun getPlayerInstanceForVideoSurface(): Player? = activePlayer

    // --- Navigation & UI Actions ---
    fun selectPrimaryTab(tab: PrimaryTab) {
        _selectedTab.value = tab
    }

    fun selectLibrarySubTab(subTab: LibrarySubTab) {
        _selectedLibrarySubTab.value = subTab
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

    fun dismissStatusBanner() {
        _statusBannerMessage.value = null
    }

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean = false) {
        _hasMediaPermission.value = granted
        _permissionPermanentlyDenied.value = !granted && permanentlyDenied
        refreshLibrary()
    }

    fun refreshLibrary() {
        if (_isScanningLibrary.value) return
        viewModelScope.launch {
            _isScanningLibrary.value = true
            try {
                val count = mediaRepository.scanAndSyncMediaLibrary(
                    includeDeviceMediaStore = _hasMediaPermission.value
                )
                if (count > 0 && _studioTargetMedia.value == null) {
                    _studioTargetMedia.value = allMedia.value.firstOrNull()
                }
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
                _statusBannerMessage.value = "Generated & verified ${generated.size} legal studio audio/video/subtitle fixtures."
            } finally {
                _isScanningLibrary.value = false
            }
        }
    }

    fun importSafUri(uri: Uri) {
        viewModelScope.launch {
            val entity = mediaRepository.importSafMediaUri(uri)
            if (entity != null) {
                _statusBannerMessage.value = "Imported '${entity.title}' via Storage Access Framework."
                playMedia(entity, listOf(entity))
                _isNowPlayingExpanded.value = true
            } else {
                _statusBannerMessage.value = "Could not import selected file."
            }
        }
    }

    // --- Playback Controls (Phase 6 & Phase 7) ---
    fun playMedia(media: MediaEntity, queueContext: List<MediaEntity> = emptyList()) {
        viewModelScope.launch {
            val isAvailable = mediaRepository.verifyUriAvailability(media.uriString)
            if (!isAvailable) {
                _playbackState.value = _playbackState.value.copy(
                    currentMedia = media.copy(isAvailable = false),
                    isPlaying = false,
                    playbackError = "File unavailable: '${media.title}' was moved, deleted, or its URI permission was revoked."
                )
                _statusBannerMessage.value = "File unavailable: ${media.title}"
                return@launch
            }

            val resolvedQueue = if (queueContext.isNotEmpty()) queueContext else listOf(media)
            val startIndex = resolvedQueue.indexOfFirst { it.uriString == media.uriString }.coerceAtLeast(0)
            val prefs = userPreferences.first()
            val savedPos = if (prefs.resumePlaybackEnabled) {
                mediaRepository.getSavedResumePosition(media.uriString)
            } else 0L
            val initialSeekMs = if (savedPos in 1000L until (media.durationMs - 1500L)) savedPos else 0L

            val mediaItems = resolvedQueue.map { item ->
                MediaItem.Builder()
                    .setMediaId(item.uriString)
                    .setUri(Uri.parse(item.uriString))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(item.title)
                            .setArtist(item.artist)
                            .setAlbumTitle(item.album)
                            .build()
                    )
                    .build()
            }

            // Ensure PulsePlaybackService is started so background & screen-off playback survives Activity backgrounding
            val app = getApplication<Application>()
            runCatching {
                app.startService(Intent(app, PulsePlaybackService::class.java))
            }

            // Acquire authoritative activePlayer AFTER suspend points so handoff never races with playMedia
            val player = activePlayer ?: run {
                ensureFallbackLocalExoPlayer()
                activePlayer
            } ?: return@launch

            player.setMediaItems(mediaItems, startIndex, initialSeekMs)
            player.PlaybackParameters = PlaybackParameters(prefs.defaultPlaybackSpeed)
            player.prepare()
            player.playWhenReady = true
            player.play()

            // Synchronize Equalizer DSP and resolve active audioSessionId without casting MediaController to ExoPlayer
            val sessionId = resolveActiveAudioSessionId(player)
            equalizerController.bindToAudioSession(
                audioSessionId = sessionId,
                enabled = prefs.equalizerEnabled,
                presetIndex = prefs.equalizerPresetIndex,
                bassStrength = prefs.bassBoostStrength,
                savedBandLevelsMb = prefs.customBandLevelsMb,
                isPlaybackActive = true
            )
            queryServiceAudioSessionIdIfController(player)

            autoLoadMatchingSampleSubtitles(media)

            _playbackState.value = _playbackState.value.copy(
                currentMedia = media,
                queue = resolvedQueue,
                currentQueueIndex = startIndex,
                isPlaying = true,
                currentPositionMs = initialSeekMs,
                durationMs = media.durationMs,
                playbackSpeed = prefs.defaultPlaybackSpeed,
                playbackError = null
            )
            startProgressPolling()
        }
    }

    private var Player.PlaybackParameters: PlaybackParameters
        get() = playbackParameters
        set(value) {
            playbackParameters = value
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
        val player = activePlayer ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else if (_playbackState.value.queue.size > 1) {
            player.seekTo(0, 0L)
        }
    }

    fun skipToPrevious() {
        val player = activePlayer ?: return
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
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

    fun removeQueueItemAt(index: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (index !in currentQueue.indices) return
        currentQueue.removeAt(index)
        activePlayer?.removeMediaItem(index)
        _playbackState.value = _playbackState.value.copy(queue = currentQueue)
    }

    // --- Subtitles (Phase 9) ---
    private fun autoLoadMatchingSampleSubtitles(media: MediaEntity) {
        if (media.mediaType != MediaType.VIDEO) {
            _playbackState.value = _playbackState.value.copy(
                loadedSubtitleName = null,
                loadedSubtitleFormat = null,
                loadedSubtitleCues = emptyList(),
                activeSubtitleText = null,
                subtitleError = null
            )
            return
        }

        val sampleSubs = sampleGenerator.getBundledSampleSubtitleFiles()
        val srtFile = sampleSubs.firstOrNull { it.extension.equals("srt", true) }
        if (srtFile != null && srtFile.exists()) {
            loadSubtitleFromFile(srtFile)
        }
    }

    fun loadSubtitleFromFile(file: File) {
        viewModelScope.launch {
            val raw = runCatching { file.readText() }.getOrDefault("")
            when (val res = SubtitleParser.parseSubtitleContent(raw, file.name)) {
                is SubtitleLoadResult.Success -> {
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
                }
            }
        }
    }

    fun loadExternalSubtitleUri(uri: Uri) {
        viewModelScope.launch {
            val res = SubtitleParser.loadAndValidateSubtitleUri(
                getApplication(),
                uri,
                uri.lastPathSegment ?: "External Subtitle"
            )
            when (res) {
                is SubtitleLoadResult.Success -> {
                    _playbackState.value = _playbackState.value.copy(
                        loadedSubtitleName = res.sourceName,
                        loadedSubtitleFormat = res.format,
                        loadedSubtitleCues = res.cues,
                        subtitleError = null
                    )
                    updateSubtitleCueForPosition(_playbackState.value.currentPositionMs)
                    _statusBannerMessage.value = "Loaded ${res.format} subtitle (${res.cues.size} cues)."
                }
                is SubtitleLoadResult.Error -> {
                    _playbackState.value = _playbackState.value.copy(
                        subtitleError = res.reason
                    )
                    _statusBannerMessage.value = res.reason
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

    private fun startProgressPolling() {
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

                    // Phase 6 Requirement: Save playback state without continuously writing to DB at high frequency
                    val now = System.currentTimeMillis()
                    if (now - lastHistoryWriteTimeMs >= 10_000L) {
                        persistCurrentPlaybackPosition(force = false)
                        lastHistoryWriteTimeMs = now
                    }
                }
                delay(250L)
            }
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
            _statusBannerMessage.value = if (added) {
                "Added '${media.title}' to Favorites"
            } else {
                "Removed '${media.title}' from Favorites"
            }
        }
    }

    fun createNewPlaylist(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            mediaRepository.createPlaylist(name, description)
            _statusBannerMessage.value = "Created playlist '$name'"
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            mediaRepository.deletePlaylist(playlistId)
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
            _statusBannerMessage.value = "Playlist deleted"
        }
    }

    fun addTrackToPlaylist(playlistId: Long, media: MediaEntity) {
        viewModelScope.launch {
            mediaRepository.addMediaToPlaylist(playlistId, media.uriString)
            _statusBannerMessage.value = "Added '${media.title}' to playlist"
        }
    }

    // --- Equalizer Actions (Phase 10) ---
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

    // --- Media Editor & Converter Actions (Phase 11 & Phase 12) ---
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
                    _statusBannerMessage.value = outcome.summary
                }
                is ProcessingOutcome.Failure -> {
                    _statusBannerMessage.value = outcome.errorMessage
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
                    _statusBannerMessage.value = outcome.summary
                }
                is ProcessingOutcome.Failure -> {
                    _statusBannerMessage.value = outcome.errorMessage
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
                    _statusBannerMessage.value = outcome.summary
                }
                is ProcessingOutcome.Failure -> {
                    _statusBannerMessage.value = outcome.errorMessage
                }
            }
        }
    }

    fun cancelActiveProcessingJob() {
        activeProcessingJob?.cancel()
        activeProcessingJob = null
        _statusBannerMessage.value = "Active media processing job cancelled."
    }

    fun clearCompletedProcessingJobs() {
        viewModelScope.launch {
            dao.clearCompletedJobs()
        }
    }

    // --- Preferences & Privacy Actions (Phase 4 & Phase 13) ---
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

    fun setOptionalAdsConfig(enabled: Boolean, consentGranted: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOptionalAdsConfiguration(enabled, consentGranted)
        }
    }

    fun clearPlaybackHistoryOnly() {
        viewModelScope.launch {
            mediaRepository.clearPlaybackHistoryOnly()
            _statusBannerMessage.value = "Cleared local playback history. Your media files remain untouched."
        }
    }

    fun resetAllPreferences() {
        viewModelScope.launch {
            preferencesRepository.resetAllPreferences()
            val resolvedId = resolveActiveAudioSessionId(activePlayer)
            equalizerController.bindToAudioSession(
                audioSessionId = resolvedId,
                enabled = false,
                presetIndex = 0,
                bassStrength = 0,
                savedBandLevelsMb = listOf(0, 0, 0, 0, 0),
                isPlaybackActive = activePlayer?.isPlaying == true
            )
            _statusBannerMessage.value = "All application preferences reset to defaults."
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
        activeProcessingJob?.cancel()
        equalizerController.releaseEffects()
        activePlayer?.removeListener(playerListener)
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        mediaControllerFuture = null
        fallbackLocalPlayer?.release()
        fallbackLocalPlayer = null
        super.onCleared()
    }
}
