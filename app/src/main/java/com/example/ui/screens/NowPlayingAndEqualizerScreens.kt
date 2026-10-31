package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.ui.components.FastScrollbar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.local.MediaEntity
import com.example.data.local.MediaType
import com.example.data.preferences.HighResVideoPlaybackMode
import com.example.data.preferences.UserPreferencesState
import com.example.data.preferences.VideoOrientationPreference
import com.example.media.HardwareEqualizerState
import com.example.media.PlayerHandoffAndVideoPolicy
import com.example.media.Resilient4KCodecEngine
import com.example.media.VideoCompatibilityAction
import com.example.media.rememberMediaArtworkBitmap
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.PlaybackSpeedDialog
import com.example.ui.components.SleepTimerDialog
import com.example.ui.components.SubtitleSettingsDialog
import com.example.ui.components.VideoAudioTrackDialog
import com.example.ui.components.VideoCompatibilityOptionsDialog
import com.example.ui.components.VideoSettingsDialog
import com.example.ui.components.VideoTechnicalInfoDialog
import com.example.ui.components.formatDurationMillis
import com.example.ui.components.formatFileSize
import com.example.viewmodel.AudioTrackOption
import com.example.viewmodel.PlaybackUiState
import com.example.viewmodel.RepeatModeState
import com.example.viewmodel.VideoAspectMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun NowPlayingOverlayScreen(
    playbackState: PlaybackUiState,
    userPreferences: UserPreferencesState,
    isFavorite: Boolean,
    playerInstance: Player?,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onCycleAspectMode: () -> Unit,
    onRotateVideo90: () -> Unit,
    onToggleFullscreen: (Boolean) -> Unit,
    onToggleControlsLock: (Boolean) -> Unit = {},
    onSelectVideoOrientation: (VideoOrientationPreference) -> Unit = {},
    onToggleKeepScreenOn: (Boolean) -> Unit = {},
    onToggleVideoGestureControls: (Boolean) -> Unit = {},
    onBrightnessGestureDelta: (Float) -> Unit,
    onVolumeGestureDelta: (Float) -> Unit,
    onPickSubtitleFileClick: () -> Unit,
    onToggleSubtitlesEnabled: (Boolean) -> Unit,
    onSetSubtitleFontScale: (Float) -> Unit,
    onSetSubtitleOffsetMs: (Long) -> Unit,
    onSetSleepTimerMinutes: (Int) -> Unit = {},
    onSetSleepTimerEndOfTrack: (Boolean) -> Unit = {},
    onToggleFavorite: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenStudio: () -> Unit,
    onSelectQueueItem: (MediaEntity) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onSaveQueueAsPlaylist: (String) -> Unit = {},
    onSelectAudioTrack: (AudioTrackOption) -> Unit = {},
    onVideoSurfaceAttached: (Boolean) -> Unit = {},
    onShareCurrentMedia: () -> Unit = {},
    onSetRingtoneCurrentMedia: () -> Unit = {},
    onAddToPlaylistCurrentMedia: () -> Unit = {},
    onShowFileInfoCurrentMedia: () -> Unit = {},
    onEnterPipClick: () -> Unit = {},
    onSelectHighResVideoPlaybackMode: (HighResVideoPlaybackMode) -> Unit = {},
    onOpenVideoCompatibilityDialog: () -> Unit = {},
    onDismissVideoCompatibilityDialog: () -> Unit = {},
    onSelectVideoCompatibilityAction: (VideoCompatibilityAction) -> Unit = {},
    onDismissVideoInfoDetailsDialog: () -> Unit = {},
    onRetryPlayback: () -> Unit
) {
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findNowPlayingHostActivity() }
    val currentMedia = playbackState.currentMedia
    var showSubtitleSettings by rememberSaveable { mutableStateOf(false) }
    var showVideoSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showSleepTimerPanel by rememberSaveable { mutableStateOf(false) }
    var showSpeedPanel by rememberSaveable { mutableStateOf(false) }
    var fullscreenControlsVisible by rememberSaveable { mutableStateOf(true) }
    var controlsInteractionTick by rememberSaveable { mutableIntStateOf(0) }
    var lastObservedMediaUri by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(currentMedia?.uriString) {
        val uri = currentMedia?.uriString
        if (uri != null && uri != lastObservedMediaUri) {
            lastObservedMediaUri = uri
            fullscreenControlsVisible = true
            controlsInteractionTick++
        }
    }

    BackHandler {
        when {
            showSubtitleSettings -> showSubtitleSettings = false
            showVideoSettingsSheet -> showVideoSettingsSheet = false
            showSleepTimerPanel -> showSleepTimerPanel = false
            showSpeedPanel -> showSpeedPanel = false
            playbackState.isVideoControlsLocked -> {
                onToggleControlsLock(false)
                fullscreenControlsVisible = true
                controlsInteractionTick++
            }
            playbackState.isFullscreenVideo -> {
                onToggleFullscreen(false)
                if (userPreferences.videoOrientationPreference == VideoOrientationPreference.LANDSCAPE) {
                    onSelectVideoOrientation(VideoOrientationPreference.AUTO)
                }
            }
            else -> onCollapse()
        }
    }

    val shouldKeepVideoAwake = PlayerHandoffAndVideoPolicy.shouldKeepVideoScreenAwake(
        isActivityResumed = true,
        isNowPlayingOverlayVisible = true,
        mediaType = currentMedia?.mediaType,
        isPlaying = playbackState.isPlaying,
        hasPlaybackError = playbackState.playbackError != null,
        keepScreenOnPreferenceEnabled = userPreferences.keepScreenOnDuringVideo
    )

    if (showSpeedPanel) {
        PlaybackSpeedDialog(
            playbackState = playbackState,
            onSetPlaybackSpeed = onSetPlaybackSpeed,
            onDismiss = { showSpeedPanel = false }
        )
    }

    if (showSleepTimerPanel) {
        SleepTimerDialog(
            playbackState = playbackState,
            onSetSleepTimerMinutes = onSetSleepTimerMinutes,
            onSetSleepTimerEndOfTrack = onSetSleepTimerEndOfTrack,
            onDismiss = { showSleepTimerPanel = false }
        )
    }

    if (showSubtitleSettings) {
        SubtitleSettingsDialog(
            playbackState = playbackState,
            userPreferences = userPreferences,
            onToggleSubtitlesEnabled = onToggleSubtitlesEnabled,
            onSetSubtitleFontScale = onSetSubtitleFontScale,
            onSetSubtitleOffsetMs = onSetSubtitleOffsetMs,
            onPickSubtitleFileClick = onPickSubtitleFileClick,
            onSeekToCue = onSeekTo,
            onDismiss = { showSubtitleSettings = false }
        )
    }

    if (currentMedia?.mediaType == MediaType.VIDEO && showVideoSettingsSheet) {
        VideoSettingsDialog(
            playbackState = playbackState,
            userPreferences = userPreferences,
            onSetPlaybackSpeed = onSetPlaybackSpeed,
            onSelectVideoOrientation = onSelectVideoOrientation,
            onRotateVideo90 = onRotateVideo90,
            onCycleAspectMode = onCycleAspectMode,
            onToggleKeepScreenOn = onToggleKeepScreenOn,
            onToggleVideoGestureControls = onToggleVideoGestureControls,
            onToggleControlsLock = onToggleControlsLock,
            onToggleSubtitlesEnabled = onToggleSubtitlesEnabled,
            onPickSubtitleFileClick = onPickSubtitleFileClick,
            onOpenEqualizer = onOpenEqualizer,
            onSelectHighResMode = onSelectHighResVideoPlaybackMode,
            onOpenCompatibilityOptions = onOpenVideoCompatibilityDialog,
            onDismiss = { showVideoSettingsSheet = false }
        )
    }

    if (currentMedia?.mediaType == MediaType.VIDEO && playbackState.showVideoCompatibilityDialog) {
        VideoCompatibilityOptionsDialog(
            media = currentMedia,
            playbackState = playbackState,
            evaluation = playbackState.videoCapabilityEvaluation,
            onSelectAction = onSelectVideoCompatibilityAction,
            onDismiss = onDismissVideoCompatibilityDialog
        )
    }

    if (currentMedia?.mediaType == MediaType.VIDEO && playbackState.showVideoInfoDetailsDialog) {
        VideoTechnicalInfoDialog(
            media = currentMedia,
            playbackState = playbackState,
            evaluation = playbackState.videoCapabilityEvaluation,
            onDismiss = onDismissVideoInfoDetailsDialog
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("now_playing_full_overlay"),
        color = MaterialTheme.colorScheme.background
    ) {
        if (currentMedia == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No active media selected", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onCollapse) { Text("Return to Library") }
                }
            }
            return@Surface
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscapeViewport = maxWidth > maxHeight
            val isImmersiveVideoCanvas = currentMedia.mediaType == MediaType.VIDEO

            val shouldHideSystemBars = PlayerHandoffAndVideoPolicy.shouldHideSystemBarsForVideo(
                isActivityResumed = true,
                isNowPlayingOverlayVisible = true,
                isStudioSheetOpen = false,
                mediaType = currentMedia.mediaType,
                isFullscreenVideo = playbackState.isFullscreenVideo,
                isLandscapeViewport = isLandscapeViewport,
                orientationPreference = userPreferences.videoOrientationPreference
            )

            DisposableEffect(
                hostActivity,
                shouldHideSystemBars,
                isImmersiveVideoCanvas,
                isLandscapeViewport,
                playbackState.isFullscreenVideo,
                userPreferences.videoOrientationPreference
            ) {
                PlayerHandoffAndVideoPolicy.applyImmersiveSystemBars(
                    window = hostActivity?.window,
                    hideSystemBars = shouldHideSystemBars
                )
                onDispose {
                    if (shouldHideSystemBars) {
                        PlayerHandoffAndVideoPolicy.applyImmersiveSystemBars(
                            window = hostActivity?.window,
                            hideSystemBars = false
                        )
                    }
                }
            }

            if (isImmersiveVideoCanvas) {
                // Unified Cinema Video Player (Portrait & Landscape): Full black canvas, 5-button transport
                // with -10s / +10s & Neon Green Play/Pause, Neon Green seekbar, and 1-tap bottom pills.
                ImmersiveVideoCinemaLayout(
                    currentMedia = currentMedia,
                    playbackState = playbackState,
                    userPreferences = userPreferences,
                    isFavorite = isFavorite,
                    isLandscapeViewport = isLandscapeViewport,
                    isSystemBarsHidden = shouldHideSystemBars,
                    playerInstance = playerInstance,
                    shouldKeepVideoAwake = shouldKeepVideoAwake,
                    controlsVisible = fullscreenControlsVisible,
                    controlsInteractionTick = controlsInteractionTick,
                    showVideoSettings = showVideoSettingsSheet,
                    showSubtitleSettings = showSubtitleSettings,
                    onToggleControlsVisible = {
                        fullscreenControlsVisible = !fullscreenControlsVisible
                        controlsInteractionTick++
                    },
                    onSetControlsVisible = { visible ->
                        fullscreenControlsVisible = visible
                        if (visible) {
                            controlsInteractionTick++
                        }
                    },
                    onUserInteraction = {
                        controlsInteractionTick++
                    },
                    onToggleVideoSettings = {
                        controlsInteractionTick++
                        showSubtitleSettings = false
                        showVideoSettingsSheet = !showVideoSettingsSheet
                    },
                    onToggleSubtitleSettings = {
                        controlsInteractionTick++
                        showVideoSettingsSheet = false
                        showSubtitleSettings = !showSubtitleSettings
                    },
                    onCollapse = {
                        if (playbackState.isFullscreenVideo) {
                            onToggleFullscreen(false)
                            if (userPreferences.videoOrientationPreference == VideoOrientationPreference.LANDSCAPE) {
                                onSelectVideoOrientation(VideoOrientationPreference.AUTO)
                            }
                        } else {
                            onCollapse()
                        }
                    },
                    onPlayPause = onPlayPause,
                    onSeekTo = onSeekTo,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onCycleRepeat = onCycleRepeat,
                    onSetPlaybackSpeed = onSetPlaybackSpeed,
                    onCycleAspectMode = onCycleAspectMode,
                    onRotateVideo90 = onRotateVideo90,
                    onToggleFullscreen = onToggleFullscreen,
                    onToggleControlsLock = onToggleControlsLock,
                    onSelectVideoOrientation = onSelectVideoOrientation,
                    onToggleKeepScreenOn = onToggleKeepScreenOn,
                    onToggleVideoGestureControls = onToggleVideoGestureControls,
                    onBrightnessGestureDelta = onBrightnessGestureDelta,
                    onVolumeGestureDelta = onVolumeGestureDelta,
                    onPickSubtitleFileClick = onPickSubtitleFileClick,
                    onToggleSubtitlesEnabled = onToggleSubtitlesEnabled,
                    onSetSubtitleFontScale = onSetSubtitleFontScale,
                    onSetSubtitleOffsetMs = onSetSubtitleOffsetMs,
                    onToggleFavorite = onToggleFavorite,
                    onOpenEqualizer = onOpenEqualizer,
                    onOpenStudio = onOpenStudio,
                    onSelectAudioTrack = onSelectAudioTrack,
                    onVideoSurfaceAttached = onVideoSurfaceAttached,
                    onEnterPipClick = onEnterPipClick,
                    onOpenVideoCompatibilityDialog = onOpenVideoCompatibilityDialog,
                    onRetryPlayback = onRetryPlayback
                )
            } else if (isLandscapeViewport) {
                LandscapeAudioNowPlayingDeck(
                    currentMedia = currentMedia,
                    playbackState = playbackState,
                    userPreferences = userPreferences,
                    isFavorite = isFavorite,
                    showSubtitleSettings = showSubtitleSettings,
                    showSleepTimerPanel = showSleepTimerPanel,
                    showSpeedPanel = showSpeedPanel,
                    onToggleSubtitleSettings = { showSubtitleSettings = !showSubtitleSettings },
                    onToggleSleepTimerPanel = { showSleepTimerPanel = !showSleepTimerPanel },
                    onToggleSpeedPanel = { showSpeedPanel = !showSpeedPanel },
                    onCollapse = onCollapse,
                    onPlayPause = onPlayPause,
                    onSeekTo = onSeekTo,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onSetPlaybackSpeed = onSetPlaybackSpeed,
                    onPickSubtitleFileClick = onPickSubtitleFileClick,
                    onToggleSubtitlesEnabled = onToggleSubtitlesEnabled,
                    onSetSubtitleFontScale = onSetSubtitleFontScale,
                    onSetSubtitleOffsetMs = onSetSubtitleOffsetMs,
                    onSetSleepTimerMinutes = onSetSleepTimerMinutes,
                    onSetSleepTimerEndOfTrack = onSetSleepTimerEndOfTrack,
                    onToggleFavorite = onToggleFavorite,
                    onOpenEqualizer = onOpenEqualizer,
                    onOpenStudio = onOpenStudio,
                    onShareCurrentMedia = onShareCurrentMedia,
                    onSetRingtoneCurrentMedia = onSetRingtoneCurrentMedia,
                    onAddToPlaylistCurrentMedia = onAddToPlaylistCurrentMedia,
                    onShowFileInfoCurrentMedia = onShowFileInfoCurrentMedia,
                    onSelectQueueItem = onSelectQueueItem,
                    onRemoveQueueItem = onRemoveQueueItem,
                    onMoveQueueItem = onMoveQueueItem,
                    onSaveQueueAsPlaylist = onSaveQueueAsPlaylist,
                    onRetryPlayback = onRetryPlayback
                )
            } else {
                // Standard Portrait Studio Deck (Music & Portrait Video)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    contentPadding = PaddingValues(bottom = 48.dp, start = 16.dp, end = 16.dp, top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Top Bar: Collapse button on left, NOW PLAYING title in center, 3-dot More Options menu on right corner
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onCollapse,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("collapse_now_playing_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Collapse player"
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (currentMedia.mediaType == MediaType.VIDEO) "VIDEO CINEMA ENGINE" else "NOW PLAYING",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentMedia.folderName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            NowPlayingOverflowMenuButton(
                                currentMedia = currentMedia,
                                playbackState = playbackState,
                                isFavorite = isFavorite,
                                showSpeedPanel = showSpeedPanel,
                                showSleepTimerPanel = showSleepTimerPanel,
                                showSubtitleSettings = showSubtitleSettings,
                                onToggleSpeedPanel = { showSpeedPanel = !showSpeedPanel },
                                onOpenEqualizer = onOpenEqualizer,
                                onToggleSleepTimerPanel = { showSleepTimerPanel = !showSleepTimerPanel },
                                onToggleSubtitleSettings = { showSubtitleSettings = !showSubtitleSettings },
                                onOpenStudio = onOpenStudio,
                                onToggleFavorite = onToggleFavorite,
                                onShareCurrentMedia = onShareCurrentMedia,
                                onSetRingtoneCurrentMedia = onSetRingtoneCurrentMedia,
                                onAddToPlaylistCurrentMedia = onAddToPlaylistCurrentMedia,
                                onShowFileInfoCurrentMedia = onShowFileInfoCurrentMedia
                            )
                        }
                    }

                    // Playback Error Banner with Retry
                    if (playbackState.playbackError != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("playback_error_card")
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = playbackState.playbackError,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = onRetryPlayback,
                                        modifier = Modifier.testTag("retry_playback_button")
                                    ) {
                                        Text("Retry Playback")
                                    }
                                }
                            }
                        }
                    }

                    // Video Surface or Acoustic Studio Visualizer
                    item {
                        if (currentMedia.mediaType == MediaType.VIDEO) {
                            PortraitVideoSurfaceSection(
                                playbackState = playbackState,
                                userPreferences = userPreferences,
                                playerInstance = playerInstance,
                                shouldKeepVideoAwake = shouldKeepVideoAwake,
                                showSubtitleSettings = showSubtitleSettings,
                                showVideoSettingsSheet = showVideoSettingsSheet,
                                onToggleSubtitleSettings = { showSubtitleSettings = !showSubtitleSettings },
                                onToggleVideoSettings = { showVideoSettingsSheet = !showVideoSettingsSheet },
                                onCycleAspectMode = onCycleAspectMode,
                                onRotateVideo90 = onRotateVideo90,
                                onToggleFullscreen = onToggleFullscreen,
                                onToggleControlsLock = onToggleControlsLock,
                                onSelectVideoOrientation = onSelectVideoOrientation,
                                onBrightnessGestureDelta = onBrightnessGestureDelta,
                                onVolumeGestureDelta = onVolumeGestureDelta
                            )
                        } else {
                            StudioCircularAudioArtworkAndWaveform(
                                currentMedia = currentMedia,
                                playbackState = playbackState,
                                subtitlesEnabled = userPreferences.subtitlesEnabledByDefault
                            )
                        }
                    }

                    // Track Metadata & Favorite Toggle
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentMedia.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val artistText = currentMedia.artist.takeIf {
                                    it.isNotBlank() &&
                                        !it.startsWith("PulsePlay", ignoreCase = true) &&
                                        it != "<unknown>" &&
                                        it != "Unknown Artist" &&
                                        it != "Local Storage"
                                }
                                if (artistText != null) {
                                    Text(
                                        text = artistText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = onToggleFavorite,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("now_playing_favorite_button")
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Toggle Favorite",
                                    tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Seek Bar & Timestamps
                    item {
                        val duration = playbackState.durationMs.coerceAtLeast(1L)
                        val pos = playbackState.currentPositionMs.coerceIn(0L, duration)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = pos.toFloat(),
                                onValueChange = {
                                    if (!playbackState.isVideoControlsLocked) {
                                        onSeekTo(it.toLong())
                                    }
                                },
                                enabled = !playbackState.isVideoControlsLocked,
                                valueRange = 0f..duration.toFloat(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("now_playing_seek_slider")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatDurationMillis(pos),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = formatDurationMillis(duration),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Primary Transport Controls: Shuffle, Previous, Play/Pause, Next, Repeat
                    item {
                        if (playbackState.isVideoControlsLocked) {
                            OutlinedButton(
                                onClick = { onToggleControlsLock(false) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("video_unlock_button")
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Controls Locked • Tap to Unlock")
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onToggleShuffle,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("transport_shuffle_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = if (playbackState.shuffleEnabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }

                                IconButton(
                                    onClick = onPrevious,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .testTag("transport_previous_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Previous",
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                FilledIconButton(
                                    onClick = onPlayPause,
                                    modifier = Modifier
                                        .size(68.dp)
                                        .testTag("transport_play_pause_button"),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                IconButton(
                                    onClick = onNext,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .testTag("transport_next_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next",
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                IconButton(
                                    onClick = onCycleRepeat,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("transport_repeat_button")
                                ) {
                                    Icon(
                                        imageVector = when (playbackState.repeatMode) {
                                            RepeatModeState.ONE -> Icons.Default.RepeatOne
                                            else -> Icons.Default.Repeat
                                        },
                                        contentDescription = "Repeat mode: ${playbackState.repeatMode}",
                                        tint = if (playbackState.repeatMode != RepeatModeState.OFF) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Active Playback Queue Management (Reorder Up/Down, Remove, Save as Playlist)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Up Next • Active Queue (${playbackState.queue.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (playbackState.queue.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        onSaveQueueAsPlaylist("Queue Mix (${playbackState.queue.size} tracks)")
                                    },
                                    modifier = Modifier.testTag("save_queue_as_playlist_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save as Playlist", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }
                            }
                        }
                    }

                    itemsIndexed(
                        playbackState.queue,
                        key = { idx, item -> "${idx}_${item.uriString}" }
                    ) { idx, item ->
                        val isCurrent = idx == playbackState.currentQueueIndex
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectQueueItem(item) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.artist} • ${formatDurationMillis(item.durationMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (playbackState.queue.size > 1) {
                                    IconButton(
                                        onClick = { onMoveQueueItem(idx, idx - 1) },
                                        enabled = idx > 0,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("queue_move_up_$idx")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move track up"
                                        )
                                    }
                                    IconButton(
                                        onClick = { onMoveQueueItem(idx, idx + 1) },
                                        enabled = idx < playbackState.queue.lastIndex,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("queue_move_down_$idx")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move track down"
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveQueueItem(idx) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("queue_remove_$idx")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remove from queue"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PortraitVideoSurfaceSection(
    playbackState: PlaybackUiState,
    userPreferences: UserPreferencesState,
    playerInstance: Player?,
    shouldKeepVideoAwake: Boolean,
    showSubtitleSettings: Boolean,
    showVideoSettingsSheet: Boolean,
    onToggleSubtitleSettings: () -> Unit,
    onToggleVideoSettings: () -> Unit,
    onCycleAspectMode: () -> Unit,
    onRotateVideo90: () -> Unit,
    onToggleFullscreen: (Boolean) -> Unit,
    onToggleControlsLock: (Boolean) -> Unit,
    onSelectVideoOrientation: (VideoOrientationPreference) -> Unit,
    onBrightnessGestureDelta: (Float) -> Unit,
    onVolumeGestureDelta: (Float) -> Unit
) {
    var containerWidthPx by remember { mutableIntStateOf(640) }
    var containerHeightPx by remember { mutableIntStateOf(360) }

    val frameTransform = remember(playbackState.videoRotationDegrees, containerWidthPx, containerHeightPx) {
        PlayerHandoffAndVideoPolicy.computeVideoFrameTransform(
            rotationDegrees = playbackState.videoRotationDegrees,
            containerWidthPx = containerWidthPx,
            containerHeightPx = containerHeightPx
        )
    }

    val aspectRatioModifier = when (playbackState.videoAspectMode) {
        VideoAspectMode.SIXTEEN_NINE -> Modifier.aspectRatio(16f / 9f)
        VideoAspectMode.FOUR_THREE -> Modifier.aspectRatio(4f / 3f)
        else -> Modifier.aspectRatio(16f / 9f)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(aspectRatioModifier)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black)
                .onSizeChanged { sz ->
                    containerWidthPx = sz.width.coerceAtLeast(1)
                    containerHeightPx = sz.height.coerceAtLeast(1)
                }
                .pointerInput(userPreferences.videoGestureControlsEnabled, playbackState.isVideoControlsLocked) {
                    if (userPreferences.videoGestureControlsEnabled && !playbackState.isVideoControlsLocked) {
                        detectVerticalDragGestures { change, dragAmount ->
                            val widthPx = size.width.coerceAtLeast(1)
                            val delta = -dragAmount / 500f
                            if (change.position.x < widthPx / 2f) {
                                onBrightnessGestureDelta(delta)
                            } else {
                                onVolumeGestureDelta(delta)
                            }
                        }
                    }
                }
                .testTag("video_surface_container")
        ) {
            if (playerInstance != null) {
                val requiresTextureView = frameTransform.rotationDegrees != 0
                key(requiresTextureView, playerInstance, playbackState.playerGeneration) {
                    AndroidView(
                        factory = { ctx ->
                            val pv = if (requiresTextureView) {
                                runCatching {
                                    LayoutInflater.from(ctx).inflate(R.layout.pulse_texture_player_view, null) as PlayerView
                                }.getOrElse { PlayerView(ctx) }
                            } else {
                                // Requirement 7: Prefer SurfaceView for ordinary video playback
                                PlayerView(ctx)
                            }
                            pv.apply {
                                useController = false
                                isClickable = false
                                isFocusable = false
                                isFocusableInTouchMode = false
                                setOnTouchListener { _, _ -> false }
                                player = playerInstance
                                keepScreenOn = shouldKeepVideoAwake
                                resizeMode = when (playbackState.videoAspectMode) {
                                    VideoAspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    VideoAspectMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            }
                        },
                        update = { view ->
                            view.useController = false
                            view.isClickable = false
                            view.isFocusable = false
                            if (view.player !== playerInstance) {
                                view.player = playerInstance
                            }
                            view.keepScreenOn = shouldKeepVideoAwake
                            view.resizeMode = when (playbackState.videoAspectMode) {
                                VideoAspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                VideoAspectMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        onReset = { view ->
                            if (view.player === playerInstance) {
                                view.player = null
                            }
                        },
                        onRelease = { view ->
                            if (view.player === playerInstance) {
                                view.player = null
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = frameTransform.rotationDegrees.toFloat()
                                scaleX = frameTransform.scaleX
                                scaleY = frameTransform.scaleY
                            }
                    )
                }
            }

            // Brightness overlay for left-side vertical gesture
            val darknessAlpha = (1f - playbackState.brightnessGestureLevel).coerceIn(0f, 0.75f)
            if (darknessAlpha > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = darknessAlpha))
                )
            }

            // Live Subtitle Overlay (never rotated with video frame!)
            if (userPreferences.subtitlesEnabledByDefault && !playbackState.activeSubtitleText.isNullOrBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp)
                        .testTag("active_subtitle_overlay")
                ) {
                    Text(
                        text = playbackState.activeSubtitleText,
                        color = Color.White,
                        fontSize = (15f * userPreferences.subtitleFontScale).sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Gesture telemetry & Screen-On status badges
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${(playbackState.brightnessGestureLevel * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${(playbackState.volumeGestureLevel * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Video Controls Strip: Aspect Ratio, Frame Rotate, Orientation Mode, Fullscreen, Subtitles, Lock, Settings
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                AssistChip(
                    onClick = { onToggleFullscreen(!playbackState.isFullscreenVideo) },
                    label = { Text(if (playbackState.isFullscreenVideo) "Exit Fullscreen" else "Fullscreen") },
                    leadingIcon = {
                        Icon(
                            if (playbackState.isFullscreenVideo) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("video_fullscreen_chip")
                )
            }
            item {
                AssistChip(
                    onClick = {
                        val nextPref = when (userPreferences.videoOrientationPreference) {
                            VideoOrientationPreference.AUTO -> VideoOrientationPreference.LANDSCAPE
                            VideoOrientationPreference.LANDSCAPE -> VideoOrientationPreference.PORTRAIT
                            VideoOrientationPreference.PORTRAIT -> VideoOrientationPreference.AUTO
                        }
                        onSelectVideoOrientation(nextPref)
                    },
                    label = { Text(userPreferences.videoOrientationPreference.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = when (userPreferences.videoOrientationPreference) {
                                VideoOrientationPreference.LANDSCAPE -> Icons.Default.ScreenLockLandscape
                                VideoOrientationPreference.PORTRAIT -> Icons.Default.ScreenLockPortrait
                                VideoOrientationPreference.AUTO -> Icons.Default.ScreenRotation
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("video_orientation_mode_chip")
                )
            }
            item {
                AssistChip(
                    onClick = onRotateVideo90,
                    label = { Text("Rotate (${playbackState.videoRotationDegrees}°)") },
                    leadingIcon = {
                        Icon(Icons.Default.ScreenRotation, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.testTag("video_rotate_chip")
                )
            }
            item {
                AssistChip(
                    onClick = onCycleAspectMode,
                    label = { Text(playbackState.videoAspectMode.label) },
                    leadingIcon = {
                        Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.testTag("video_aspect_ratio_chip")
                )
            }
            item {
                AssistChip(
                    onClick = onToggleSubtitleSettings,
                    label = {
                        Text(playbackState.loadedSubtitleFormat?.name ?: "Subtitles")
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.testTag("video_subtitles_chip")
                )
            }
            item {
                AssistChip(
                    onClick = { onToggleControlsLock(!playbackState.isVideoControlsLocked) },
                    label = { Text(if (playbackState.isVideoControlsLocked) "Locked" else "Lock") },
                    leadingIcon = {
                        Icon(
                            if (playbackState.isVideoControlsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("video_lock_chip")
                )
            }
            item {
                AssistChip(
                    onClick = onToggleVideoSettings,
                    label = { Text("Video Settings") },
                    leadingIcon = {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.testTag("video_settings_chip")
                )
            }
        }
    }
}

private val CinemaNeonGreen = Color(0xFF00E676)
private val CinemaPillBackground = Color(0xFF1A1D24).copy(alpha = 0.86f)
private val CinemaPillBorder = Color.White.copy(alpha = 0.14f)

private tailrec fun Context.findNowPlayingHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findNowPlayingHostActivity()
    else -> null
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmersiveVideoCinemaLayout(
    currentMedia: MediaEntity,
    playbackState: PlaybackUiState,
    userPreferences: UserPreferencesState,
    isFavorite: Boolean,
    isLandscapeViewport: Boolean,
    isSystemBarsHidden: Boolean = false,
    playerInstance: Player?,
    shouldKeepVideoAwake: Boolean,
    controlsVisible: Boolean,
    controlsInteractionTick: Int = 0,
    showVideoSettings: Boolean,
    showSubtitleSettings: Boolean,
    onToggleControlsVisible: () -> Unit,
    onSetControlsVisible: (Boolean) -> Unit,
    onUserInteraction: () -> Unit = {},
    onToggleVideoSettings: () -> Unit,
    onToggleSubtitleSettings: () -> Unit,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onCycleAspectMode: () -> Unit,
    onRotateVideo90: () -> Unit,
    onToggleFullscreen: (Boolean) -> Unit,
    onToggleControlsLock: (Boolean) -> Unit,
    onSelectVideoOrientation: (VideoOrientationPreference) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onToggleVideoGestureControls: (Boolean) -> Unit,
    onBrightnessGestureDelta: (Float) -> Unit,
    onVolumeGestureDelta: (Float) -> Unit,
    onPickSubtitleFileClick: () -> Unit,
    onToggleSubtitlesEnabled: (Boolean) -> Unit,
    onSetSubtitleFontScale: (Float) -> Unit,
    onSetSubtitleOffsetMs: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenStudio: () -> Unit,
    onSelectAudioTrack: (AudioTrackOption) -> Unit = {},
    onVideoSurfaceAttached: (Boolean) -> Unit = {},
    onEnterPipClick: () -> Unit = {},
    onOpenVideoCompatibilityDialog: () -> Unit = {},
    onRetryPlayback: () -> Unit
) {
    var containerWidthPx by remember { mutableIntStateOf(1280) }
    var containerHeightPx by remember { mutableIntStateOf(720) }
    var activeAudioTrackLabel by rememberSaveable { mutableStateOf("Original HD Audio") }
    var showAudioTrackSheet by rememberSaveable { mutableStateOf(false) }
    var gestureFeedbackBanner by remember { mutableStateOf<String?>(null) }
    var localInteractionTick by rememberSaveable { mutableIntStateOf(0) }

    val notifyUserInteraction: () -> Unit = {
        localInteractionTick++
        onUserInteraction()
    }

    // Keep controls visible for a fresh inactivity window if device rotates while controls are visible
    LaunchedEffect(isLandscapeViewport) {
        if (controlsVisible) {
            localInteractionTick++
        }
    }

    // Single, lifecycle-safe Auto-hide HUD timer (3.8s) while video is actively playing and no sheet is open
    LaunchedEffect(
        controlsVisible,
        controlsInteractionTick,
        localInteractionTick,
        playbackState.isPlaying,
        showVideoSettings,
        showSubtitleSettings,
        showAudioTrackSheet
    ) {
        if (controlsVisible &&
            playbackState.isPlaying &&
            !showVideoSettings &&
            !showSubtitleSettings &&
            !showAudioTrackSheet
        ) {
            delay(3800L)
            onSetControlsVisible(false)
        }
    }

    // Auto-dismiss gesture feedback badge
    LaunchedEffect(gestureFeedbackBanner) {
        if (gestureFeedbackBanner != null) {
            delay(1100L)
            gestureFeedbackBanner = null
        }
    }

    val frameTransform = remember(playbackState.videoRotationDegrees, containerWidthPx, containerHeightPx) {
        PlayerHandoffAndVideoPolicy.computeVideoFrameTransform(
            rotationDegrees = playbackState.videoRotationDegrees,
            containerWidthPx = containerWidthPx,
            containerHeightPx = containerHeightPx
        )
    }

    val isCurrentlyLandscapeMode = isLandscapeViewport ||
        userPreferences.videoOrientationPreference == VideoOrientationPreference.LANDSCAPE ||
        playbackState.isFullscreenVideo

    val toggleLandscapePortraitQuick: () -> Unit = {
        notifyUserInteraction()
        if (playbackState.isFullscreenVideo) {
            onToggleFullscreen(false)
            if (userPreferences.videoOrientationPreference == VideoOrientationPreference.LANDSCAPE) {
                onSelectVideoOrientation(VideoOrientationPreference.AUTO)
            }
        } else if (isCurrentlyLandscapeMode) {
            onSelectVideoOrientation(VideoOrientationPreference.PORTRAIT)
            onToggleFullscreen(false)
        } else {
            if (userPreferences.videoOrientationPreference == VideoOrientationPreference.PORTRAIT) {
                onSelectVideoOrientation(VideoOrientationPreference.AUTO)
            }
            onToggleFullscreen(true)
        }
    }

    val seekRelativeMs: (Long) -> Unit = { deltaMs ->
        notifyUserInteraction()
        val duration = playbackState.durationMs.coerceAtLeast(1L)
        val target = (playbackState.currentPositionMs + deltaMs).coerceIn(0L, duration)
        onSeekTo(target)
        gestureFeedbackBanner = if (deltaMs > 0) "+10s Forward" else "-10s Rewind"
    }

    val cycleSpeedQuick: () -> Unit = {
        notifyUserInteraction()
        val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.5f, 0.75f)
        val currentIdx = speeds.indexOfFirst { abs(it - playbackState.playbackSpeed) < 0.04f }
        val nextSpeed = speeds[(currentIdx + 1) % speeds.size]
        onSetPlaybackSpeed(nextSpeed)
        gestureFeedbackBanner = "Speed ${nextSpeed}x"
    }

    val effectiveWidth = currentMedia.width.takeIf { it > 0 } ?: playbackState.decodedVideoWidth
    val effectiveHeight = currentMedia.height.takeIf { it > 0 } ?: playbackState.decodedVideoHeight
    val resolutionTag = remember(effectiveWidth, effectiveHeight, playbackState.isDownscaledPlayback) {
        Resilient4KCodecEngine.formatVerifiedResolutionBadge(
            width = effectiveWidth,
            height = effectiveHeight,
            isDownscaled = playbackState.isDownscaledPlayback
        )
    }
    val subtitleMetaText = remember(currentMedia.sizeBytes, resolutionTag) {
        val formattedSize = if (currentMedia.sizeBytes > 0L) {
            formatFileSize(currentMedia.sizeBytes)
        } else {
            ""
        }
        when {
            !resolutionTag.isNullOrBlank() && formattedSize.isNotBlank() -> "$resolutionTag • $formattedSize"
            !resolutionTag.isNullOrBlank() -> resolutionTag
            formattedSize.isNotBlank() -> formattedSize
            else -> "Video Stream"
        }
    }

    // Latest references for pointerInput(Unit) so 200ms currentPositionMs ticks NEVER cancel tap gestures
    val latestControlsVisible by rememberUpdatedState(controlsVisible)
    val latestIsLocked by rememberUpdatedState(playbackState.isVideoControlsLocked)
    val latestGesturesEnabled by rememberUpdatedState(userPreferences.videoGestureControlsEnabled)
    val latestShowVideoSettings by rememberUpdatedState(showVideoSettings)
    val latestShowSubtitleSettings by rememberUpdatedState(showSubtitleSettings)
    val latestSetControlsVisible by rememberUpdatedState(onSetControlsVisible)
    val latestToggleVideoSettings by rememberUpdatedState(onToggleVideoSettings)
    val latestToggleSubtitleSettings by rememberUpdatedState(onToggleSubtitleSettings)
    val latestNotifyInteraction by rememberUpdatedState(notifyUserInteraction)
    val latestSeekRelativeMs by rememberUpdatedState(seekRelativeMs)
    val latestOnPlayPause by rememberUpdatedState(onPlayPause)
    val latestOnBrightnessDelta by rememberUpdatedState(onBrightnessGestureDelta)
    val latestOnVolumeDelta by rememberUpdatedState(onVolumeGestureDelta)
    val latestBrightnessLevel by rememberUpdatedState(playbackState.brightnessGestureLevel)
    val latestVolumeLevel by rememberUpdatedState(playbackState.volumeGestureLevel)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { sz ->
                containerWidthPx = sz.width.coerceAtLeast(1)
                containerHeightPx = sz.height.coerceAtLeast(1)
            }
    ) {
        // 1. BOTTOM LAYER: Video Surface Container centered on pure black cinema canvas
        //    PlayerView explicitly has useController=false and touch/click disabled so it never swallows taps.
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val videoModifier = when (playbackState.videoAspectMode) {
                VideoAspectMode.SIXTEEN_NINE -> Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                VideoAspectMode.FOUR_THREE -> Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                else -> Modifier.fillMaxSize()
            }

            if (playerInstance != null) {
                val requiresTextureView = frameTransform.rotationDegrees != 0
                key(requiresTextureView, playerInstance, playbackState.playerGeneration) {
                    AndroidView(
                        factory = { ctx ->
                            val pv = if (requiresTextureView) {
                                runCatching {
                                    LayoutInflater.from(ctx).inflate(R.layout.pulse_texture_player_view, null) as PlayerView
                                }.getOrElse { PlayerView(ctx) }
                            } else {
                                // Requirement 7: Prefer SurfaceView for ordinary video playback
                                PlayerView(ctx)
                            }
                            pv.apply {
                                useController = false
                                isClickable = false
                                isFocusable = false
                                isFocusableInTouchMode = false
                                setOnTouchListener { _, _ -> false }
                                player = playerInstance
                                keepScreenOn = shouldKeepVideoAwake
                                resizeMode = when (playbackState.videoAspectMode) {
                                    VideoAspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    VideoAspectMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            }
                            onVideoSurfaceAttached(true)
                            pv
                        },
                        update = { view ->
                            view.useController = false
                            view.isClickable = false
                            view.isFocusable = false
                            if (view.player !== playerInstance) {
                                view.player = playerInstance
                            }
                            onVideoSurfaceAttached(true)
                            view.keepScreenOn = shouldKeepVideoAwake
                            view.resizeMode = when (playbackState.videoAspectMode) {
                                VideoAspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                VideoAspectMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        onReset = { view ->
                            if (view.player === playerInstance) {
                                view.player = null
                            }
                            onVideoSurfaceAttached(false)
                        },
                        onRelease = { view ->
                            if (view.player === playerInstance) {
                                view.player = null
                            }
                            onVideoSurfaceAttached(false)
                        },
                        modifier = videoModifier
                            .graphicsLayer {
                                rotationZ = frameTransform.rotationDegrees.toFloat()
                                scaleX = frameTransform.scaleX
                                scaleY = frameTransform.scaleY
                            }
                    )
                }
            }
        }

        // 2. MIDDLE VISUAL OVERLAYS: Brightness darkening & Subtitles
        val darknessAlpha = (1f - playbackState.brightnessGestureLevel).coerceIn(0f, 0.75f)
        if (darknessAlpha > 0.05f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = darknessAlpha))
            )
        }

        // Subtitle overlay (always upright and centered near bottom)
        if (userPreferences.subtitlesEnabledByDefault && !playbackState.activeSubtitleText.isNullOrBlank()) {
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (controlsVisible) 136.dp else 28.dp, start = 24.dp, end = 24.dp)
                    .testTag("active_subtitle_overlay")
            ) {
                Text(
                    text = playbackState.activeSubtitleText,
                    color = Color.White,
                    fontSize = (16f * userPreferences.subtitleFontScale).sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        // Transient gesture feedback badge (Speed / Seek / Brightness / Volume / Aspect)
        AnimatedVisibility(
            visible = gestureFeedbackBanner != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 78.dp)
        ) {
            Surface(
                color = CinemaPillBackground,
                shape = RoundedCornerShape(50),
                modifier = Modifier.border(1.dp, CinemaNeonGreen.copy(alpha = 0.6f), RoundedCornerShape(50))
            ) {
                Text(
                    text = gestureFeedbackBanner.orEmpty(),
                    color = CinemaNeonGreen,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // 3. TOP INTERACTIVE LAYER (ABOVE PlayerView):
        //    Handles single-tap to show/hide controls immediately (never cancelled by currentPositionMs updates),
        //    rapid double-tap seek/play-pause, vertical brightness/volume swipes, and hosts all HUD controls.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var lastTapUptimeMs = 0L
                    var lastTapPosition = Offset.Zero
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                        var consumedByChild = down.isConsumed
                        var isVerticalDrag = false
                        var lastY = down.position.y
                        var totalDy = 0f
                        var upPosition = down.position
                        val touchSlop = viewConfiguration.touchSlop

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (event.changes.any { it.isConsumed }) {
                                consumedByChild = true
                            }
                            val pointerChange = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                            if (pointerChange != null) {
                                upPosition = pointerChange.position
                                val stepDy = pointerChange.position.y - lastY
                                lastY = pointerChange.position.y
                                totalDy += stepDy

                                if (!consumedByChild && !isVerticalDrag && abs(totalDy) > touchSlop) {
                                    isVerticalDrag = true
                                }
                                if (!consumedByChild && isVerticalDrag && latestGesturesEnabled && !latestIsLocked) {
                                    pointerChange.consume()
                                    latestNotifyInteraction()
                                    val widthPx = size.width.coerceAtLeast(1)
                                    val delta = -stepDy / 500f
                                    if (pointerChange.position.x < widthPx / 2f) {
                                        latestOnBrightnessDelta(delta)
                                        val pct = ((latestBrightnessLevel + delta).coerceIn(0.1f, 1f) * 100).roundToInt()
                                        gestureFeedbackBanner = "Brightness $pct%"
                                    } else {
                                        latestOnVolumeDelta(delta)
                                        val pct = ((latestVolumeLevel + delta).coerceIn(0f, 1f) * 100).roundToInt()
                                        gestureFeedbackBanner = "Volume $pct%"
                                    }
                                }
                            }
                            if (event.changes.none { it.pressed }) {
                                break
                            }
                        }

                        if (!consumedByChild && !isVerticalDrag) {
                            val now = SystemClock.uptimeMillis()
                            val isRapidSecondTap = latestControlsVisible &&
                                !latestIsLocked &&
                                latestGesturesEnabled &&
                                (now - lastTapUptimeMs) in 35L..260L &&
                                (upPosition - lastTapPosition).getDistance() < 140f

                            if (isRapidSecondTap) {
                                lastTapUptimeMs = 0L
                                latestNotifyInteraction()
                                val widthPx = size.width.coerceAtLeast(1)
                                when {
                                    upPosition.x < widthPx * 0.35f -> latestSeekRelativeMs(-10_000L)
                                    upPosition.x > widthPx * 0.65f -> latestSeekRelativeMs(10_000L)
                                    else -> latestOnPlayPause()
                                }
                            } else {
                                lastTapUptimeMs = now
                                lastTapPosition = upPosition
                                latestNotifyInteraction()
                                if (!latestControlsVisible) {
                                    latestSetControlsVisible(true)
                                } else if (showAudioTrackSheet) {
                                    showAudioTrackSheet = false
                                } else if (latestShowVideoSettings) {
                                    latestToggleVideoSettings()
                                } else if (latestShowSubtitleSettings) {
                                    latestToggleSubtitleSettings()
                                } else {
                                    latestSetControlsVisible(false)
                                }
                            }
                        }
                    }
                }
                .testTag("video_surface_container")
        ) {
            // If Controls are Locked, only show minimal unlock pill when controlsVisible is true
            if (playbackState.isVideoControlsLocked) {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(animationSpec = tween(150)),
                    exit = fadeOut(animationSpec = tween(150)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(top = 24.dp)
                ) {
                    Button(
                        onClick = {
                            notifyUserInteraction()
                            onToggleControlsLock(false)
                            onSetControlsVisible(true)
                        },
                        modifier = Modifier.testTag("video_unlock_button")
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = "Unlock video controls")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock Video Controls")
                    }
                }
                return@Box
            }

            // Fullscreen / Portrait Cinema Translucent HUD Overlay
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150)),
                modifier = Modifier.fillMaxSize()
            ) {
                val hudInsetsModifier = if (isSystemBarsHidden) {
                    Modifier.windowInsetsPadding(WindowInsets.displayCutout)
                } else {
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.80f),
                                    Color.Black.copy(alpha = 0.18f),
                                    Color.Black.copy(alpha = 0.18f),
                                    Color.Black.copy(alpha = 0.88f)
                                )
                            )
                        )
                        .then(hudInsetsModifier)
                        .padding(
                            horizontal = if (isLandscapeViewport) 20.dp else 14.dp,
                            vertical = if (isLandscapeViewport) 8.dp else 14.dp
                        )
                        .testTag("video_controls_hud_overlay")
                ) {
                    val isCompactHudHeight = maxHeight < 400.dp
                    val centerPlayButtonSize = if (isCompactHudHeight) 56.dp else 68.dp
                    val centerSeekButtonSize = if (isCompactHudHeight) 48.dp else 54.dp

                    // 1. TOP BAR: Back Arrow, Video Title + "FHD 1080p • 39.7 MB" subtitle, Lock, CC, Equalizer, 1-Tap Fullscreen/Rotate
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    notifyUserInteraction()
                                    onCollapse()
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("collapse_now_playing_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = currentMedia.title,
                                    style = if (isCompactHudHeight) {
                                        MaterialTheme.typography.titleSmall
                                    } else {
                                        MaterialTheme.typography.titleMedium
                                    },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = subtitleMetaText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.76f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .testTag("video_resolution_meta_label")
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Lock Controls Icon
                            IconButton(
                                onClick = {
                                    notifyUserInteraction()
                                    onToggleControlsLock(true)
                                },
                                modifier = Modifier.testTag("video_lock_chip")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = "Lock Controls",
                                    tint = Color.White
                                )
                            }
                            // Subtitles / CC Icon
                            IconButton(
                                onClick = {
                                    notifyUserInteraction()
                                    onToggleSubtitleSettings()
                                },
                                modifier = Modifier.testTag("video_subtitles_chip")
                            ) {
                                Icon(
                                    imageVector = if (userPreferences.subtitlesEnabledByDefault) {
                                        Icons.Default.ClosedCaption
                                    } else {
                                        Icons.Default.SubtitlesOff
                                    },
                                    contentDescription = "Subtitles",
                                    tint = if (playbackState.loadedSubtitleFormat != null) CinemaNeonGreen else Color.White
                                )
                            }
                            // Equalizer / Audio Icon
                            IconButton(
                                onClick = {
                                    notifyUserInteraction()
                                    onOpenEqualizer()
                                },
                                modifier = Modifier.testTag("now_playing_eq_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Equalizer,
                                    contentDescription = "Equalizer",
                                    tint = Color.White
                                )
                            }
                            // Picture-in-Picture (PiP) Icon
                            IconButton(
                                onClick = {
                                    notifyUserInteraction()
                                    onEnterPipClick()
                                },
                                modifier = Modifier.testTag("video_pip_chip")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "Picture-in-Picture",
                                    tint = Color.White
                                )
                            }
                            // 1-Tap Fullscreen / Landscape-Portrait Toggle Icon
                            IconButton(
                                onClick = toggleLandscapePortraitQuick,
                                modifier = Modifier.testTag("video_fullscreen_chip")
                            ) {
                                Icon(
                                    imageVector = if (isCurrentlyLandscapeMode) {
                                        Icons.Default.FullscreenExit
                                    } else {
                                        Icons.Default.Fullscreen
                                    },
                                    contentDescription = if (isCurrentlyLandscapeMode) {
                                        "Exit Fullscreen"
                                    } else {
                                        "Fullscreen"
                                    },
                                    tint = if (isCurrentlyLandscapeMode) CinemaNeonGreen else Color.White
                                )
                            }
                        }
                    }

                    // 2. CENTER 5-BUTTON TRANSPORT OVERLAY: Previous | -10s Replay | Neon Green Play/Pause | +10s Forward | Next
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(if (isLandscapeViewport) 20.dp else 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous Video
                        IconButton(
                            onClick = {
                                notifyUserInteraction()
                                onPrevious()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                .testTag("transport_previous_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Rewind 10 Seconds (↺10)
                        IconButton(
                            onClick = { seekRelativeMs(-10_000L) },
                            modifier = Modifier
                                .size(centerSeekButtonSize)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                                .testTag("transport_rewind_10_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Rewind 10 seconds",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Vibrant Neon Green Play / Pause Circle (responsive 56dp-68dp)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(centerPlayButtonSize)
                                .clip(CircleShape)
                                .background(CinemaNeonGreen, CircleShape)
                                .clickable {
                                    notifyUserInteraction()
                                    onPlayPause()
                                }
                                .testTag("transport_play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = Color(0xFF06150D),
                                modifier = Modifier.size(if (isCompactHudHeight) 30.dp else 36.dp)
                            )
                        }

                        // Fast-Forward 10 Seconds (↻10)
                        IconButton(
                            onClick = { seekRelativeMs(10_000L) },
                            modifier = Modifier
                                .size(centerSeekButtonSize)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                                .testTag("transport_forward_10_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Forward 10 seconds",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Next Video
                        IconButton(
                            onClick = {
                                notifyUserInteraction()
                                onNext()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                .testTag("transport_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // 3. BOTTOM BAR: Timestamps -> Neon Green Seekbar -> Dark Translucent Pill Action Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        if (playbackState.playbackError != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = playbackState.playbackError,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = {
                                        notifyUserInteraction()
                                        onOpenVideoCompatibilityDialog()
                                    },
                                    modifier = Modifier.testTag("error_open_compatibility_options_button")
                                ) {
                                    Text("Options", color = CinemaNeonGreen)
                                }
                                Button(
                                    onClick = {
                                        notifyUserInteraction()
                                        onRetryPlayback()
                                    },
                                    modifier = Modifier.testTag("retry_playback_button")
                                ) {
                                    Text("Retry")
                                }
                            }
                        }

                        val duration = playbackState.durationMs.coerceAtLeast(1L)
                        val pos = playbackState.currentPositionMs.coerceIn(0L, duration)

                        // Timestamps directly above SeekBar (00:08 on left, 06:31 on right)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDurationMillis(pos),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.testTag("video_current_time_text")
                            )
                            Text(
                                text = formatDurationMillis(duration),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.testTag("video_total_time_text")
                            )
                        }

                        // Neon Green SeekBar with vertical green thumb bar
                        Slider(
                            value = pos.toFloat(),
                            onValueChange = {
                                notifyUserInteraction()
                                onSeekTo(it.toLong())
                            },
                            valueRange = 0f..duration.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = CinemaNeonGreen,
                                activeTrackColor = CinemaNeonGreen,
                                inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                            ),
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .height(20.dp)
                                        .background(CinemaNeonGreen, RoundedCornerShape(3.dp))
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .testTag("now_playing_seek_slider")
                        )

                        Spacer(modifier = Modifier.height(if (isCompactHudHeight) 2.dp else 8.dp))

                        // Bottom Dark Translucent Pill Action Bar (matches video recording pills)
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pill 1: Playback Speed (1.0x -> 1.25x -> 1.5x -> 2.0x -> 0.5x -> 0.75x)
                            item {
                                CinemaActionPill(
                                    label = "${playbackState.playbackSpeed}x",
                                    isHighlighted = abs(playbackState.playbackSpeed - 1.0f) > 0.04f,
                                    onClick = cycleSpeedQuick,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("video_speed_pill")
                                )
                            }

                            // Pill 2: Aspect Ratio (Fit Screen -> Fill (Zoom) -> Original -> 16:9 -> 4:3)
                            item {
                                CinemaActionPill(
                                    label = playbackState.videoAspectMode.label,
                                    isHighlighted = playbackState.videoAspectMode != VideoAspectMode.FIT,
                                    onClick = {
                                        notifyUserInteraction()
                                        onCycleAspectMode()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AspectRatio,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("video_aspect_ratio_chip")
                                )
                            }

                            // Pill 3: Audio Track / Sound Mode (♪ Original HD Audio)
                            item {
                                CinemaActionPill(
                                    label = activeAudioTrackLabel,
                                    isHighlighted = showAudioTrackSheet,
                                    onClick = {
                                        notifyUserInteraction()
                                        showAudioTrackSheet = !showAudioTrackSheet
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = CinemaNeonGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("video_audio_track_pill")
                                )
                            }

                            // Pill 4: 1-Tap Orientation Toggle (Portrait / Landscape)
                            item {
                                CinemaActionPill(
                                    label = if (isCurrentlyLandscapeMode) "Landscape" else "Portrait",
                                    isHighlighted = isCurrentlyLandscapeMode,
                                    onClick = toggleLandscapePortraitQuick,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isCurrentlyLandscapeMode) {
                                                Icons.Default.ScreenLockLandscape
                                            } else {
                                                Icons.Default.ScreenLockPortrait
                                            },
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("video_orientation_mode_chip")
                                )
                            }

                            // Pill 5: Repeat Mode Toggle (Repeat Off / All / One)
                            item {
                                val repeatLabel = when (playbackState.repeatMode) {
                                    RepeatModeState.OFF -> "Repeat"
                                    RepeatModeState.ALL -> "Repeat All"
                                    RepeatModeState.ONE -> "Repeat One"
                                }
                                CinemaActionPill(
                                    label = repeatLabel,
                                    isHighlighted = playbackState.repeatMode != RepeatModeState.OFF,
                                    onClick = {
                                        notifyUserInteraction()
                                        onCycleRepeat()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (playbackState.repeatMode == RepeatModeState.ONE) {
                                                Icons.Default.RepeatOne
                                            } else {
                                                Icons.Default.Repeat
                                            },
                                            contentDescription = null,
                                            tint = if (playbackState.repeatMode != RepeatModeState.OFF) CinemaNeonGreen else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("video_repeat_pill")
                                )
                            }

                            // Pill 6: Frame Rotate 90°
                            item {
                                CinemaActionPill(
                                    label = "Rotate (${playbackState.videoRotationDegrees}°)",
                                    isHighlighted = playbackState.videoRotationDegrees != 0,
                                    onClick = {
                                        notifyUserInteraction()
                                        onRotateVideo90()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.ScreenRotation,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("video_rotate_chip")
                                )
                            }

                            // Pill 7: Favorite toggle
                            item {
                                CinemaActionPill(
                                    label = if (isFavorite) "Saved" else "Favorite",
                                    isHighlighted = isFavorite,
                                    onClick = {
                                        notifyUserInteraction()
                                        onToggleFavorite()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = null,
                                            tint = if (isFavorite) CinemaNeonGreen else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }

                            // Pill 8: Media Studio / Cut
                            item {
                                CinemaActionPill(
                                    label = "Studio",
                                    isHighlighted = false,
                                    onClick = {
                                        notifyUserInteraction()
                                        onOpenStudio()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.ContentCut,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("now_playing_studio_button")
                                )
                            }

                            // Pill 9: Video Settings Drawer
                            item {
                                CinemaActionPill(
                                    label = "Settings",
                                    isHighlighted = showVideoSettings,
                                    onClick = {
                                        notifyUserInteraction()
                                        onToggleVideoSettings()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("now_playing_video_settings_button")
                                )
                            }
                        }
                    }

                    // Quick Audio Track & Sound Mode selector popup when "♪ Original HD Audio" pill is tapped
                    if (showAudioTrackSheet) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF14181F).copy(alpha = 0.96f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .widthIn(max = 360.dp)
                                .fillMaxWidth(0.92f)
                                .heightIn(max = (maxHeight - 24.dp).coerceAtLeast(180.dp))
                                .border(1.dp, CinemaPillBorder, RoundedCornerShape(16.dp))
                                .testTag("video_audio_track_sheet_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Audio Track & Sound Profile",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            showAudioTrackSheet = false
                                            onOpenEqualizer()
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("5-Band EQ", color = CinemaNeonGreen, style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(
                                        onClick = { showAudioTrackSheet = false },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close Audio Track Sheet",
                                            tint = Color.White
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                if (playbackState.availableAudioTracks.isNotEmpty()) {
                                    Text(
                                        text = "Container Audio Streams",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CinemaNeonGreen
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    playbackState.availableAudioTracks.forEach { trackOption ->
                                        Surface(
                                            color = if (trackOption.isSelected) CinemaNeonGreen.copy(alpha = 0.18f) else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onSelectAudioTrack(trackOption)
                                                    activeAudioTrackLabel = trackOption.label
                                                    showAudioTrackSheet = false
                                                    gestureFeedbackBanner = trackOption.label
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${trackOption.label} (${trackOption.language})",
                                                    color = if (trackOption.isSelected) CinemaNeonGreen else Color.White,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (trackOption.isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (trackOption.isSelected) {
                                                    Text(
                                                        text = "ACTIVE",
                                                        color = CinemaNeonGreen,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                val profiles = listOf(
                                    "Original HD Audio",
                                    "Cinema Surround Boost",
                                    "Clear Dialogue / Vocal",
                                    "Deep Bass Theater"
                                )
                                profiles.forEach { profile ->
                                    val selected = activeAudioTrackLabel == profile
                                    Surface(
                                        color = if (selected) CinemaNeonGreen.copy(alpha = 0.18f) else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                activeAudioTrackLabel = profile
                                                showAudioTrackSheet = false
                                                gestureFeedbackBanner = profile
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = profile,
                                                color = if (selected) CinemaNeonGreen else Color.White,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (selected) {
                                                Text(
                                                    text = "ACTIVE",
                                                    color = CinemaNeonGreen,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating Video Settings Drawer in Cinema Mode
                    if (showVideoSettings) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .widthIn(max = 380.dp)
                                .fillMaxWidth(if (isLandscapeViewport) 0.58f else 0.94f)
                                .fillMaxHeight(0.88f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            VideoPlayerSettingsCard(
                                playbackState = playbackState,
                                userPreferences = userPreferences,
                                onSetPlaybackSpeed = onSetPlaybackSpeed,
                                onSelectVideoOrientation = onSelectVideoOrientation,
                                onRotateVideo90 = onRotateVideo90,
                                onCycleAspectMode = onCycleAspectMode,
                                onToggleKeepScreenOn = onToggleKeepScreenOn,
                                onToggleVideoGestureControls = onToggleVideoGestureControls,
                                onToggleControlsLock = onToggleControlsLock,
                                onToggleSubtitlesEnabled = onToggleSubtitlesEnabled,
                                onPickSubtitleFileClick = onPickSubtitleFileClick,
                                onOpenEqualizer = onOpenEqualizer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CinemaActionPill(
    label: String,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val borderColor = if (isHighlighted) CinemaNeonGreen.copy(alpha = 0.75f) else CinemaPillBorder
    val bgColor = if (isHighlighted) Color(0xFF0E291C).copy(alpha = 0.88f) else CinemaPillBackground
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leadingIcon != null) {
                leadingIcon()
            }
            Text(
                text = label,
                color = if (isHighlighted) CinemaNeonGreen else Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/**
 * Original PulsePlay Circular Studio Artwork, Live Acoustic Waveform & Synchronized Lyrics centerpiece.
 */
@Composable
private fun StudioCircularAudioArtworkAndWaveform(
    currentMedia: MediaEntity,
    playbackState: PlaybackUiState,
    subtitlesEnabled: Boolean = true,
    compactMode: Boolean = false
) {
    val centerCircleSize = if (compactMode) 136.dp else 200.dp
    val iconSize = if (compactMode) 36.dp else 50.dp
    val mintGreenAccent = Color(0xFF00E676)
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val artworkBitmap by rememberMediaArtworkBitmap(
        media = currentMedia,
        artworkData = playbackState.currentArtworkData,
        artworkUri = playbackState.currentArtworkUri,
        targetSizePx = if (compactMode) 384 else 640
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audio_visualizer_card"),
        shape = RoundedCornerShape(if (compactMode) 22.dp else 28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = if (compactMode) 14.dp else 22.dp,
                    horizontal = if (compactMode) 14.dp else 20.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val resolvedCover = artworkBitmap
            if (resolvedCover != null) {
                // Real Album Artwork: Clean circular crop with subtle static mint-green border & glow
                Box(
                    modifier = Modifier
                        .size(centerCircleSize)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    mintGreenAccent.copy(alpha = 0.22f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    Color.Black
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            color = mintGreenAccent.copy(alpha = 0.45f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = resolvedCover,
                        contentDescription = "Album artwork for ${currentMedia.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .testTag("now_playing_album_artwork_image")
                    )
                }
            } else {
                // Fallback: Simple audio icon with subtle static mint-green decorative ring & glow (no duplicate rotating arc)
                Box(
                    modifier = Modifier
                        .size(centerCircleSize)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    mintGreenAccent.copy(alpha = 0.16f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            color = mintGreenAccent.copy(alpha = 0.45f),
                            shape = CircleShape
                        )
                        .testTag("now_playing_default_artwork_placeholder"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                            contentDescription = "Default audio icon",
                            tint = mintGreenAccent,
                            modifier = Modifier.size(iconSize)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentMedia.mimeType.substringAfterLast('/').uppercase().takeIf { it.isNotBlank() } ?: "AUDIO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = mintGreenAccent.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (compactMode) 10.dp else 18.dp))

            // Horizontal Studio Waveform Bar
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compactMode) 36.dp else 48.dp)
            ) {
                val barCount = 36
                val totalWidth = size.width
                val totalHeight = size.height
                val barWidth = (totalWidth / (barCount * 1.75f)).coerceAtLeast(3.5f)
                val spacing = (totalWidth - (barWidth * barCount)) / (barCount - 1).coerceAtLeast(1)
                val phase = (playbackState.currentPositionMs / 160.0)
                val progressIndex = if (playbackState.durationMs > 0L) {
                    ((playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()) * barCount).toInt()
                } else 0

                for (i in 0 until barCount) {
                    val wave = if (playbackState.isPlaying) {
                        0.22f + 0.72f * abs(sin(phase + i * 0.42) * cos(phase * 0.5 + i * 0.18)).toFloat()
                    } else {
                        0.18f + 0.32f * abs(sin(i * 0.48)).toFloat()
                    }
                    val barHeight = (totalHeight * wave).coerceAtLeast(8f)
                    val x = i * (barWidth + spacing)
                    val y = (totalHeight - barHeight) / 2f
                    val barColor = when {
                        i <= progressIndex -> secondaryColor
                        i % 3 == 0 -> primaryColor.copy(alpha = 0.65f)
                        else -> primaryColor.copy(alpha = 0.35f)
                    }
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }

            // Synchronized Lyrics Banner (SRT & WebVTT) for Music Playback
            if (subtitlesEnabled && !playbackState.activeSubtitleText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_lyrics_banner")
                ) {
                    Text(
                        text = playbackState.activeSubtitleText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Three-Dot Overflow Menu Button in the top-right corner of the Music Player (Now Playing) screen.
 * Consolidates all secondary options (Playback Speed, Equalizer, Sleep Timer, Lyrics/Subtitles,
 * Media Studio Audio Cutter, Share, Set as Ringtone, Add to Playlist, and File Information)
 * inside a clean dropdown menu so the main Now Playing screen stays uncluttered.
 */
@Composable
private fun NowPlayingOverflowMenuButton(
    currentMedia: MediaEntity,
    playbackState: PlaybackUiState,
    isFavorite: Boolean,
    showSpeedPanel: Boolean,
    showSleepTimerPanel: Boolean,
    showSubtitleSettings: Boolean,
    onToggleSpeedPanel: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onToggleSleepTimerPanel: () -> Unit,
    onToggleSubtitleSettings: () -> Unit,
    onOpenStudio: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShareCurrentMedia: () -> Unit,
    onSetRingtoneCurrentMedia: () -> Unit,
    onAddToPlaylistCurrentMedia: () -> Unit,
    onShowFileInfoCurrentMedia: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val speedFormatted = "${"%.2f".format(playbackState.playbackSpeed).trimEnd('0').trimEnd('.')}x"
    val sleepTimerLabel = when {
        playbackState.sleepTimerStopAtEndOfTrack -> "Sleep Timer (End of Track)"
        playbackState.sleepTimerRemainingMs > 0L ->
            "Sleep Timer (${formatDurationMillis(playbackState.sleepTimerRemainingMs)})"
        else -> "Sleep Timer"
    }
    val lyricsLabel = playbackState.loadedSubtitleFormat?.name?.let { "Lyrics & Subtitles ($it)" }
        ?: "Lyrics & Subtitles (SRT/VTT)"

    Box {
        IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("now_playing_more_options_button")
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More player options",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.testTag("now_playing_overflow_dropdown_menu")
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (showSpeedPanel) "Hide Playback Speed ($speedFormatted)" else "Playback Speed ($speedFormatted)",
                        maxLines = 1
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                onClick = {
                    menuExpanded = false
                    onToggleSpeedPanel()
                },
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .testTag("now_playing_menu_speed")
            )

            DropdownMenuItem(
                text = { Text("Equalizer & Bass Boost", maxLines = 1) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                onClick = {
                    menuExpanded = false
                    onOpenEqualizer()
                },
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .testTag("now_playing_menu_equalizer")
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = if (showSleepTimerPanel) "Hide $sleepTimerLabel" else sleepTimerLabel,
                        maxLines = 1
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = if (playbackState.isSleepTimerActive) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                onClick = {
                    menuExpanded = false
                    onToggleSleepTimerPanel()
                },
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .testTag("now_playing_menu_sleep_timer")
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = if (showSubtitleSettings) "Hide $lyricsLabel" else lyricsLabel,
                        maxLines = 1
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                onClick = {
                    menuExpanded = false
                    onToggleSubtitleSettings()
                },
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .testTag("now_playing_menu_lyrics")
            )

            DropdownMenuItem(
                text = { Text("Edit in Media Studio", maxLines = 1) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                onClick = {
                    menuExpanded = false
                    onOpenStudio()
                },
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .testTag("now_playing_menu_studio")
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DropdownMenuItem(
                text = { Text("Share", maxLines = 1) },
                leadingIcon = {
                    Icon(Icons.Default.Share, contentDescription = null)
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                onClick = {
                    menuExpanded = false
                    onShareCurrentMedia()
                },
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .testTag("now_playing_menu_share")
            )

            if (currentMedia.mediaType == MediaType.AUDIO) {
                DropdownMenuItem(
                    text = { Text("Set as Ringtone", maxLines = 1) },
                    leadingIcon = {
                        Icon(Icons.Default.RingVolume, contentDescription = null)
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    onClick = {
                        menuExpanded = false
                        onSetRingtoneCurrentMedia()
                    },
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .testTag("now_playing_menu_ringtone")
                )
            }

            DropdownMenuItem(
                text = { Text("Add to Playlist", maxLines = 1) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                onClick = {
                    menuExpanded = false
                    onAddToPlaylistCurrentMedia()
                },
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .testTag("now_playing_menu_add_to_playlist")
            )

            DropdownMenuItem(
                text = {
                    Text(
                        if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                        maxLines = 1
                    )
                },
                leadingIcon = {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null
                    )
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                onClick = {
                    menuExpanded = false
                    onToggleFavorite()
                },
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .testTag("now_playing_menu_favorite")
            )

            DropdownMenuItem(
                text = { Text("File Information", maxLines = 1) },
                leadingIcon = {
                    Icon(Icons.Default.Info, contentDescription = null)
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                onClick = {
                    menuExpanded = false
                    onShowFileInfoCurrentMedia()
                },
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .testTag("now_playing_menu_file_info")
            )
        }
    }
}

/**
 * Playback Speed Control Card (opened from the Now Playing 3-dot menu).
 */
@Composable
private fun PlaybackSpeedControlCard(
    playbackState: PlaybackUiState,
    onSetPlaybackSpeed: (Float) -> Unit,
    onClose: (() -> Unit)? = null
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("playback_speed_panel"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Playback Speed (${"%.2f".format(playbackState.playbackSpeed).trimEnd('0').trimEnd('.')}x)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Playback Speed panel"
                        )
                    }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(speeds) { spd ->
                    FilterChip(
                        selected = abs(playbackState.playbackSpeed - spd) < 0.05f,
                        onClick = { onSetPlaybackSpeed(spd) },
                        label = { Text("${spd}x", maxLines = 1) },
                        modifier = Modifier.testTag("speed_chip_${spd}")
                    )
                }
            }
            Slider(
                value = playbackState.playbackSpeed.coerceIn(0.5f, 3.0f),
                onValueChange = { raw ->
                    val snapped = (raw * 20f).roundToInt() / 20f
                    onSetPlaybackSpeed(snapped.coerceIn(0.5f, 3.0f))
                },
                valueRange = 0.5f..3.0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("now_playing_speed_slider")
            )
        }
    }
}

/**
 * Sleep Timer (Automatic Stop) Control Card.
 */
@Composable
private fun SleepTimerControlCard(
    playbackState: PlaybackUiState,
    onSetSleepTimerMinutes: (Int) -> Unit,
    onSetSleepTimerEndOfTrack: (Boolean) -> Unit,
    onClose: (() -> Unit)? = null
) {
    val presetMinutes = listOf(0, 5, 10, 15, 30, 45, 60)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sleep_timer_panel"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Sleep Timer (Automatic Stop)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val statusLabel = when {
                    playbackState.sleepTimerStopAtEndOfTrack -> "Stops at End of Track"
                    playbackState.sleepTimerRemainingMs > 0L ->
                        "Remaining: ${formatDurationMillis(playbackState.sleepTimerRemainingMs)}"
                    else -> "Off"
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (playbackState.isSleepTimerActive) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.testTag("sleep_timer_status_text")
                )
                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Sleep Timer panel"
                        )
                    }
                }
            }

            Text(
                text = "Automatically pauses music or video playback when the countdown completes or the current track ends.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presetMinutes) { mins ->
                    val isSelected = if (mins == 0) {
                        !playbackState.isSleepTimerActive
                    } else {
                        !playbackState.sleepTimerStopAtEndOfTrack && playbackState.sleepTimerTotalMinutes == mins
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSetSleepTimerMinutes(mins) },
                        label = { Text(if (mins == 0) "Off" else "$mins min") },
                        modifier = Modifier.testTag("sleep_timer_chip_$mins")
                    )
                }
                item {
                    FilterChip(
                        selected = playbackState.sleepTimerStopAtEndOfTrack,
                        onClick = {
                            onSetSleepTimerEndOfTrack(!playbackState.sleepTimerStopAtEndOfTrack)
                        },
                        label = { Text("End of Track") },
                        modifier = Modifier.testTag("sleep_timer_chip_end_of_track")
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerSettingsCard(
    playbackState: PlaybackUiState,
    userPreferences: UserPreferencesState,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSelectVideoOrientation: (VideoOrientationPreference) -> Unit,
    onRotateVideo90: () -> Unit,
    onCycleAspectMode: () -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onToggleVideoGestureControls: (Boolean) -> Unit,
    onToggleControlsLock: (Boolean) -> Unit,
    onToggleSubtitlesEnabled: (Boolean) -> Unit,
    onPickSubtitleFileClick: () -> Unit,
    onOpenEqualizer: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("video_settings_panel"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Video Settings & Orientation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // 1. Screen Rotation Mode (Auto / Landscape / Portrait)
            Text(
                text = "Screen Rotation Mode",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VideoOrientationPreference.entries) { pref ->
                    FilterChip(
                        selected = userPreferences.videoOrientationPreference == pref,
                        onClick = { onSelectVideoOrientation(pref) },
                        label = { Text(pref.label) },
                        modifier = Modifier.testTag("video_orientation_pref_${pref.name.lowercase()}")
                    )
                }
            }

            // 2. Frame Orientation & Aspect Ratio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRotateVideo90,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Frame: ${playbackState.videoRotationDegrees}°")
                }
                OutlinedButton(
                    onClick = onCycleAspectMode,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(playbackState.videoAspectMode.label)
                }
            }

            // 3. Playback Speed (0.5x – 3.0x)
            Text(
                text = "Playback Speed (${playbackState.playbackSpeed}x • 0.5x–3.0x)",
                style = MaterialTheme.typography.labelMedium
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)) { spd ->
                    FilterChip(
                        selected = abs(playbackState.playbackSpeed - spd) < 0.05f,
                        onClick = { onSetPlaybackSpeed(spd) },
                        label = { Text("${spd}x") }
                    )
                }
            }

            // 4. Keep Screen On During Video toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keep Screen On During Video", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Prevents screen timeout while video is actively playing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = userPreferences.keepScreenOnDuringVideo,
                    onCheckedChange = onToggleKeepScreenOn,
                    modifier = Modifier.testTag("video_keep_screen_on_switch")
                )
            }

            // 5. Gesture Controls toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Brightness & Volume Swipe Gestures", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = userPreferences.videoGestureControlsEnabled,
                    onCheckedChange = onToggleVideoGestureControls,
                    modifier = Modifier.testTag("video_gesture_controls_switch")
                )
            }

            // 6. Subtitles & Audio Track DSP shortcuts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPickSubtitleFileClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Subtitles (${if (userPreferences.subtitlesEnabledByDefault) "On" else "Off"})")
                }
                OutlinedButton(
                    onClick = onOpenEqualizer,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Equalizer, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Audio Track EQ")
                }
            }

            // 7. Lock Controls button
            OutlinedButton(
                onClick = { onToggleControlsLock(!playbackState.isVideoControlsLocked) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Lock Video Touch Controls")
            }
        }
    }
}

@Composable
private fun SubtitleSettingsCard(
    playbackState: PlaybackUiState,
    userPreferences: UserPreferencesState,
    onToggleSubtitlesEnabled: (Boolean) -> Unit,
    onSetSubtitleFontScale: (Float) -> Unit,
    onSetSubtitleOffsetMs: (Long) -> Unit,
    onPickSubtitleFileClick: () -> Unit,
    onSeekToCue: (Long) -> Unit = {},
    onClose: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("subtitle_settings_panel"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lyrics & Subtitles (SRT & WebVTT)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = userPreferences.subtitlesEnabledByDefault,
                    onCheckedChange = onToggleSubtitlesEnabled,
                    modifier = Modifier.testTag("subtitle_enable_switch")
                )
                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Lyrics & Subtitles panel"
                        )
                    }
                }
            }

            Text(
                text = playbackState.loadedSubtitleName?.let {
                    "Loaded: $it (${playbackState.loadedSubtitleFormat}) • ${playbackState.loadedSubtitleCues.size} synchronized cues"
                } ?: "No SRT/WebVTT lyrics or subtitle track loaded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (playbackState.subtitleError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = playbackState.subtitleError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Synchronized Lyrics / Cue List (tap any line to jump to its timestamp)
            if (playbackState.loadedSubtitleCues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Synchronized Lyrics / Cues (Tap line to seek):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    playbackState.loadedSubtitleCues.forEachIndexed { idx, cue ->
                        val isActiveCue = playbackState.activeSubtitleText == cue.text
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isActiveCue) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable { onSeekToCue(cue.startMs) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("lyrics_cue_item_$idx"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDurationMillis(cue.startMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.width(48.dp)
                            )
                            Text(
                                text = cue.text,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isActiveCue) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActiveCue) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Font Scale: ${(userPreferences.subtitleFontScale * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium
            )
            Slider(
                value = userPreferences.subtitleFontScale,
                onValueChange = onSetSubtitleFontScale,
                valueRange = 0.7f..1.8f
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Sync Offset: ${userPreferences.subtitleSyncOffsetMs} ms",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSetSubtitleOffsetMs(userPreferences.subtitleSyncOffsetMs - 250L) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) { Text("-250ms", maxLines = 1) }
                    OutlinedButton(
                        onClick = { onSetSubtitleOffsetMs(0L) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) { Text("0ms", maxLines = 1) }
                    OutlinedButton(
                        onClick = { onSetSubtitleOffsetMs(userPreferences.subtitleSyncOffsetMs + 250L) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("subtitle_offset_plus_250_button"),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                    ) { Text("+250ms", maxLines = 1) }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onPickSubtitleFileClick,
                modifier = Modifier.testTag("load_external_subtitle_button")
            ) {
                Icon(Icons.Default.ClosedCaption, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Load Local .SRT / .VTT Lyrics or Subtitles (SAF)")
            }
        }
    }
}

/**
 * Multiband Biquad Equalizer, Bass Boost, Preamp Sound Enhancer & Channel Balance DSP Bottom Sheet.
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    eqState: HardwareEqualizerState,
    audioBalance: Float = 0.0f,
    onDismiss: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSelectPreset: (Int) -> Unit,
    onBandLevelChange: (Int, Int) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onAudioBalanceChange: (Float) -> Unit = {},
    onPreampGainChange: (Float) -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("equalizer_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Multiband Equalizer & Bass DSP",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = eqState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (eqState.isEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.testTag("eq_status_message")
                    )
                    Text(
                        text = "Engine: ${eqState.processingMode.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = eqState.isEnabled,
                    onCheckedChange = onSetEnabled,
                    modifier = Modifier.testTag("equalizer_enable_switch")
                )
            }

            // Visual 5-Band Spectrum Response Curve Card (inspired by PulsePlay Equalizer concept)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                )
            ) {
                val primary = MaterialTheme.colorScheme.primary
                val secondary = MaterialTheme.colorScheme.secondary
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val bands = eqState.bands
                        if (bands.isEmpty()) return@Canvas
                        val midY = size.height / 2f
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(0f, midY),
                            end = Offset(size.width, midY),
                            strokeWidth = 2f
                        )
                        val stepX = if (bands.size > 1) size.width / (bands.size - 1) else size.width
                        var prevPoint: Offset? = null
                        bands.forEachIndexed { idx, b ->
                            val norm = (b.currentLevelMb.toFloat() / b.maxLevelMb.coerceAtLeast(100).toFloat())
                                .coerceIn(-1f, 1f)
                            val x = idx * stepX
                            val y = midY - (norm * (size.height * 0.4f))
                            val pt = Offset(x, y)
                            prevPoint?.let { prev ->
                                drawLine(
                                    brush = Brush.horizontalGradient(listOf(primary, secondary)),
                                    start = prev,
                                    end = pt,
                                    strokeWidth = 6f,
                                    cap = StrokeCap.Round
                                )
                            }
                            drawCircle(
                                color = if (eqState.isEnabled) secondary else Color.Gray,
                                radius = 9f,
                                center = pt
                            )
                            prevPoint = pt
                        }
                    }
                }
            }

            // Presets Row
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(eqState.presets) { idx, presetName ->
                    FilterChip(
                        selected = eqState.selectedPresetIndex == idx,
                        onClick = { onSelectPreset(idx) },
                        label = { Text(presetName) },
                        modifier = Modifier.testTag("eq_preset_chip_$idx")
                    )
                }
            }

            // Frequency Band Sliders
            eqState.bands.forEach { band ->
                val freqLabel = if (band.centerFreqHz >= 1000) {
                    "%.1f kHz".format(band.centerFreqHz / 1000f).replace(".0 ", " ")
                } else {
                    "${band.centerFreqHz} Hz"
                }
                val dbValue = band.currentLevelMb / 100f
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = freqLabel, style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "%+.1f dB".format(dbValue),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = band.currentLevelMb.toFloat(),
                        onValueChange = { onBandLevelChange(band.bandIndex, it.toInt()) },
                        valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
                        enabled = eqState.isEnabled,
                        modifier = Modifier.testTag("eq_band_slider_${band.bandIndex}")
                    )
                }
            }

            // Bass Boost Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Bass Boost (85 Hz Low-Shelf DSP)",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "${eqState.bassBoostStrength / 10}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Slider(
                    value = eqState.bassBoostStrength.toFloat(),
                    onValueChange = { onBassBoostChange(it.toInt()) },
                    valueRange = 0f..1000f,
                    enabled = eqState.isEnabled,
                    modifier = Modifier.testTag("eq_bass_boost_slider")
                )
            }

            // Preamp / Sound Enhancer Gain (-6.0 dB to +6.0 dB)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sound Enhancer Preamp Gain",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "%+.1f dB".format(eqState.preampGainDb),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Slider(
                    value = eqState.preampGainDb,
                    onValueChange = onPreampGainChange,
                    valueRange = -6.0f..6.0f,
                    modifier = Modifier.testTag("eq_preamp_slider")
                )
            }

            // Left / Right Stereo Balance Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Stereo Balance (L / R)",
                        style = MaterialTheme.typography.labelMedium
                    )
                    val balanceText = when {
                        abs(audioBalance) < 0.05f -> "Center"
                        audioBalance < 0f -> "L ${(abs(audioBalance) * 100).toInt()}%"
                        else -> "R ${(audioBalance * 100).toInt()}%"
                    }
                    Text(
                        text = balanceText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = audioBalance,
                    onValueChange = onAudioBalanceChange,
                    valueRange = -1.0f..1.0f,
                    modifier = Modifier.testTag("eq_balance_slider")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Responsive Two-Column Landscape Audio Now Playing Layout:
 * - Left pane: Top navigation / collapse bar + compact studio visualizer + track title/artist/favorite.
 * - Right pane: Scrollable controls (Seekbar, 5-button Transport, Quick Feature Chips, Speed, and Active Queue).
 */
@Composable
private fun LandscapeAudioNowPlayingDeck(
    currentMedia: MediaEntity,
    playbackState: PlaybackUiState,
    userPreferences: UserPreferencesState,
    isFavorite: Boolean,
    showSubtitleSettings: Boolean,
    showSleepTimerPanel: Boolean,
    showSpeedPanel: Boolean,
    onToggleSubtitleSettings: () -> Unit,
    onToggleSleepTimerPanel: () -> Unit,
    onToggleSpeedPanel: () -> Unit,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onPickSubtitleFileClick: () -> Unit,
    onToggleSubtitlesEnabled: (Boolean) -> Unit,
    onSetSubtitleFontScale: (Float) -> Unit,
    onSetSubtitleOffsetMs: (Long) -> Unit,
    onSetSleepTimerMinutes: (Int) -> Unit,
    onSetSleepTimerEndOfTrack: (Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenStudio: () -> Unit,
    onShareCurrentMedia: () -> Unit,
    onSetRingtoneCurrentMedia: () -> Unit,
    onAddToPlaylistCurrentMedia: () -> Unit,
    onShowFileInfoCurrentMedia: () -> Unit,
    onSelectQueueItem: (MediaEntity) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onSaveQueueAsPlaylist: (String) -> Unit,
    onRetryPlayback: () -> Unit
) {
    if (showSpeedPanel) {
        PlaybackSpeedDialog(
            playbackState = playbackState,
            onSetPlaybackSpeed = onSetPlaybackSpeed,
            onDismiss = onToggleSpeedPanel
        )
    }

    if (showSleepTimerPanel) {
        SleepTimerDialog(
            playbackState = playbackState,
            onSetSleepTimerMinutes = onSetSleepTimerMinutes,
            onSetSleepTimerEndOfTrack = onSetSleepTimerEndOfTrack,
            onDismiss = onToggleSleepTimerPanel
        )
    }

    if (showSubtitleSettings) {
        SubtitleSettingsDialog(
            playbackState = playbackState,
            userPreferences = userPreferences,
            onToggleSubtitlesEnabled = onToggleSubtitlesEnabled,
            onSetSubtitleFontScale = onSetSubtitleFontScale,
            onSetSubtitleOffsetMs = onSetSubtitleOffsetMs,
            onPickSubtitleFileClick = onPickSubtitleFileClick,
            onSeekToCue = onSeekTo,
            onDismiss = onToggleSubtitleSettings
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("landscape_audio_now_playing_split"),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: Header + Compact Visualizer + Track Metadata
        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("collapse_now_playing_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse player"
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = currentMedia.folderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_favorite_button")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            StudioCircularAudioArtworkAndWaveform(
                currentMedia = currentMedia,
                playbackState = playbackState,
                subtitlesEnabled = userPreferences.subtitlesEnabledByDefault,
                compactMode = true
            )

            Text(
                text = currentMedia.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val artistText = currentMedia.artist.takeIf {
                it.isNotBlank() &&
                    !it.startsWith("PulsePlay", ignoreCase = true) &&
                    it != "<unknown>" &&
                    it != "Unknown Artist" &&
                    it != "Local Storage"
            }
            if (artistText != null) {
                Text(
                    text = artistText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Right Column: Top-Right 3-Dot Menu, Seekbar, Transport Controls, Optional Opened Panels, and Active Queue
        LazyColumn(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 28.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Utility Row: Single 3-Dot More Options Menu in the top-right corner
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NowPlayingOverflowMenuButton(
                        currentMedia = currentMedia,
                        playbackState = playbackState,
                        isFavorite = isFavorite,
                        showSpeedPanel = showSpeedPanel,
                        showSleepTimerPanel = showSleepTimerPanel,
                        showSubtitleSettings = showSubtitleSettings,
                        onToggleSpeedPanel = onToggleSpeedPanel,
                        onOpenEqualizer = onOpenEqualizer,
                        onToggleSleepTimerPanel = onToggleSleepTimerPanel,
                        onToggleSubtitleSettings = onToggleSubtitleSettings,
                        onOpenStudio = onOpenStudio,
                        onToggleFavorite = onToggleFavorite,
                        onShareCurrentMedia = onShareCurrentMedia,
                        onSetRingtoneCurrentMedia = onSetRingtoneCurrentMedia,
                        onAddToPlaylistCurrentMedia = onAddToPlaylistCurrentMedia,
                        onShowFileInfoCurrentMedia = onShowFileInfoCurrentMedia
                    )
                }
            }

            if (playbackState.playbackError != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("playback_error_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = playbackState.playbackError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = onRetryPlayback,
                                modifier = Modifier.testTag("retry_playback_button")
                            ) {
                                Text("Retry Playback")
                            }
                        }
                    }
                }
            }

            // Seek Bar & Timestamps
            item {
                val duration = playbackState.durationMs.coerceAtLeast(1L)
                val pos = playbackState.currentPositionMs.coerceIn(0L, duration)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = pos.toFloat(),
                        onValueChange = { onSeekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("now_playing_seek_slider")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDurationMillis(pos),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formatDurationMillis(duration),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Primary Transport Controls: Shuffle, Previous, Play/Pause, Next, Repeat
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("transport_shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playbackState.shuffleEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .size(50.dp)
                            .testTag("transport_previous_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    FilledIconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(60.dp)
                            .testTag("transport_play_pause_button"),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .size(50.dp)
                            .testTag("transport_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    IconButton(
                        onClick = onCycleRepeat,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("transport_repeat_button")
                    ) {
                        Icon(
                            imageVector = when (playbackState.repeatMode) {
                                RepeatModeState.ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Repeat mode: ${playbackState.repeatMode}",
                            tint = if (playbackState.repeatMode != RepeatModeState.OFF) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }



            // Active Queue Management
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Up Next • Active Queue (${playbackState.queue.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (playbackState.queue.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                onSaveQueueAsPlaylist("Queue Mix (${playbackState.queue.size} tracks)")
                            },
                            modifier = Modifier.testTag("save_queue_as_playlist_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save as Playlist", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }

            itemsIndexed(
                playbackState.queue,
                key = { idx, item -> "${idx}_${item.uriString}" }
            ) { idx, item ->
                val isCurrent = idx == playbackState.currentQueueIndex
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectQueueItem(item) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.artist} • ${formatDurationMillis(item.durationMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (playbackState.queue.size > 1) {
                            IconButton(
                                onClick = { onMoveQueueItem(idx, idx - 1) },
                                enabled = idx > 0,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("queue_move_up_$idx")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Move track up"
                                )
                            }
                            IconButton(
                                onClick = { onMoveQueueItem(idx, idx + 1) },
                                enabled = idx < playbackState.queue.lastIndex,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("queue_move_down_$idx")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Move track down"
                                )
                            }
                            IconButton(
                                onClick = { onRemoveQueueItem(idx) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("queue_remove_$idx")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Remove from queue"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Right-Hand Now Playing Side Panel for Landscape Library / Browse mode.
 * Displays full playback controls, seekbar, speed/EQ chips, and Up Next queue alongside the main library content.
 */
@Composable
fun NowPlayingSidePanel(
    playbackState: PlaybackUiState,
    userPreferences: UserPreferencesState,
    isFavorite: Boolean,
    onExpandFullPlayer: () -> Unit,
    onCollapseSidePanel: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenStudio: () -> Unit,
    onSelectQueueItem: (MediaEntity) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMedia = playbackState.currentMedia
    var showSidePanelSpeedDialog by rememberSaveable { mutableStateOf(false) }

    if (showSidePanelSpeedDialog) {
        PlaybackSpeedDialog(
            playbackState = playbackState,
            onSetPlaybackSpeed = onSetPlaybackSpeed,
            onDismiss = { showSidePanelSpeedDialog = false }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .testTag("landscape_now_playing_panel"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
    ) {
        if (currentMedia == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Now Playing Deck",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select any song or video from the library to control playback, queue, and DSP here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onOpenEqualizer,
                        modifier = Modifier.testTag("side_panel_idle_eq_button")
                    ) {
                        Icon(Icons.Default.Equalizer, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Equalizer", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onCollapseSidePanel,
                        modifier = Modifier.testTag("side_panel_collapse_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Now Playing panel", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hide", maxLines = 1)
                    }
                }
            }
        } else {
            val sidePanelListState = rememberLazyListState()

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = sidePanelListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp)
                        .testTag("side_panel_scroll_list"),
                contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header Row: Title badge + Expand Full Player + Collapse Panel
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (currentMedia.mediaType == MediaType.VIDEO) Icons.Default.Movie else Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "NOW PLAYING",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var sideMenuExpanded by remember { mutableStateOf(false) }
                            val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.5f, 0.75f)
                            val speedLabel = "${"%.2f".format(playbackState.playbackSpeed).trimEnd('0').trimEnd('.')}x"

                            IconButton(
                                onClick = onExpandFullPlayer,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("side_panel_expand_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInFull,
                                    contentDescription = "Expand full Now Playing screen",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = onCollapseSidePanel,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("side_panel_collapse_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Hide Now Playing side panel",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { sideMenuExpanded = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("side_panel_more_options_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More player options",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = sideMenuExpanded,
                                    onDismissRequest = { sideMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Playback Speed ($speedLabel)", maxLines = 1) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                        },
                                        onClick = {
                                            sideMenuExpanded = false
                                            showSidePanelSpeedDialog = true
                                        },
                                        modifier = Modifier.testTag("side_panel_speed_chip")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sleep Timer", maxLines = 1) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                        },
                                        onClick = {
                                            sideMenuExpanded = false
                                            onExpandFullPlayer()
                                        },
                                        modifier = Modifier.testTag("side_panel_sleep_timer_chip")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Lyrics & Subtitles", maxLines = 1) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Subtitles, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        },
                                        onClick = {
                                            sideMenuExpanded = false
                                            onExpandFullPlayer()
                                        },
                                        modifier = Modifier.testTag("side_panel_lyrics_chip")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Equalizer & Bass Boost", maxLines = 1) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Equalizer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        },
                                        onClick = {
                                            sideMenuExpanded = false
                                            onOpenEqualizer()
                                        },
                                        modifier = Modifier.testTag("side_panel_eq_chip")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Edit in Media Studio", maxLines = 1) },
                                        leadingIcon = {
                                            Icon(Icons.Default.ContentCut, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                        },
                                        onClick = {
                                            sideMenuExpanded = false
                                            onOpenStudio()
                                        },
                                        modifier = Modifier.testTag("side_panel_studio_chip")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Open Full Player", maxLines = 1) },
                                        leadingIcon = {
                                            Icon(Icons.Default.OpenInFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        },
                                        onClick = {
                                            sideMenuExpanded = false
                                            onExpandFullPlayer()
                                        },
                                        modifier = Modifier.testTag("side_panel_full_player_chip")
                                    )
                                }
                            }
                        }
                    }
                }

                // Compact Track / Video Preview Card + Favorite Toggle (fits in short landscape heights so transport controls are immediately visible)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onExpandFullPlayer)
                            .testTag(
                                if (currentMedia.mediaType == MediaType.VIDEO) {
                                    "side_panel_video_preview_card"
                                } else {
                                    "side_panel_audio_preview_card"
                                }
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val sidePanelArtwork by rememberMediaArtworkBitmap(
                                media = currentMedia,
                                artworkData = playbackState.currentArtworkData,
                                artworkUri = playbackState.currentArtworkUri,
                                targetSizePx = 160
                            )
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (currentMedia.mediaType == MediaType.VIDEO) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val resolvedSideThumb = sidePanelArtwork
                                if (resolvedSideThumb != null && currentMedia.mediaType == MediaType.AUDIO) {
                                    Image(
                                        bitmap = resolvedSideThumb,
                                        contentDescription = "Album artwork for ${currentMedia.title}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .testTag("side_panel_artwork_image")
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (currentMedia.mediaType == MediaType.VIDEO) {
                                            Icons.Default.Movie
                                        } else if (playbackState.isPlaying) {
                                            Icons.Default.GraphicEq
                                        } else {
                                            Icons.Default.MusicNote
                                        },
                                        contentDescription = null,
                                        tint = if (currentMedia.mediaType == MediaType.VIDEO) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentMedia.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        append(currentMedia.artist.ifBlank { currentMedia.folderName })
                                        if (currentMedia.mediaType == MediaType.VIDEO && currentMedia.width > 0 && currentMedia.height > 0) {
                                            append(" • ${currentMedia.width}x${currentMedia.height}")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = onToggleFavorite,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("side_panel_favorite_button")
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Toggle Favorite",
                                    tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Seek Slider & Timestamps
                item {
                    val duration = playbackState.durationMs.coerceAtLeast(1L)
                    val pos = playbackState.currentPositionMs.coerceIn(0L, duration)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = pos.toFloat(),
                            onValueChange = { onSeekTo(it.toLong()) },
                            valueRange = 0f..duration.toFloat(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("side_panel_seek_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDurationMillis(pos),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = formatDurationMillis(duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 5-Button Transport Controls (all with 48dp minimum interactive size)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onToggleShuffle,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("side_panel_shuffle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playbackState.shuffleEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        IconButton(
                            onClick = onPrevious,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("side_panel_previous_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous"
                            )
                        }

                        FilledIconButton(
                            onClick = onPlayPause,
                            modifier = Modifier
                                .size(52.dp)
                                .testTag("side_panel_play_pause_button"),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = onNext,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("side_panel_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next"
                            )
                        }

                        IconButton(
                            onClick = onCycleRepeat,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("side_panel_repeat_button")
                        ) {
                            Icon(
                                imageVector = when (playbackState.repeatMode) {
                                    RepeatModeState.ONE -> Icons.Default.RepeatOne
                                    else -> Icons.Default.Repeat
                                },
                                contentDescription = "Repeat mode: ${playbackState.repeatMode}",
                                tint = if (playbackState.repeatMode != RepeatModeState.OFF) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }


                // Up Next Queue in Side Panel
                if (playbackState.queue.isNotEmpty()) {
                    item {
                        Text(
                            text = "Up Next (${playbackState.queue.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    itemsIndexed(
                        playbackState.queue,
                        key = { idx, item -> "side_${idx}_${item.uriString}" }
                    ) { idx, item ->
                        val isCurrent = idx == playbackState.currentQueueIndex
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectQueueItem(item) }
                                .testTag("side_panel_queue_item_$idx"),
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatDurationMillis(item.durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                if (playbackState.queue.size > 1) {
                                    IconButton(
                                        onClick = { onRemoveQueueItem(idx) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("side_panel_queue_remove_$idx")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remove from queue",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FastScrollbar(
                listState = sidePanelListState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp)
            )
        }
    }
}
}

