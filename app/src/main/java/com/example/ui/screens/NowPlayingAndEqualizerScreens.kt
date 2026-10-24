package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.LayoutInflater
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.local.MediaEntity
import com.example.data.local.MediaType
import com.example.data.preferences.UserPreferencesState
import com.example.data.preferences.VideoOrientationPreference
import com.example.media.HardwareEqualizerState
import com.example.media.PlayerHandoffAndVideoPolicy
import com.example.media.Resilient4KCodecEngine
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
    onRetryPlayback: () -> Unit
) {
    val currentMedia = playbackState.currentMedia
    var showSubtitleSettings by remember { mutableStateOf(false) }
    var showVideoSettingsSheet by remember { mutableStateOf(false) }
    var showSleepTimerPanel by remember { mutableStateOf(false) }
    var fullscreenControlsVisible by remember { mutableStateOf(true) }

    BackHandler {
        when {
            showSubtitleSettings -> showSubtitleSettings = false
            showVideoSettingsSheet -> showVideoSettingsSheet = false
            showSleepTimerPanel -> showSleepTimerPanel = false
            playbackState.isVideoControlsLocked -> onToggleControlsLock(false)
            playbackState.isFullscreenVideo -> onToggleFullscreen(false)
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

            if (isImmersiveVideoCanvas) {
                // Unified Cinema Video Player (Portrait & Landscape): Full black canvas, 5-button transport
                // with -10s / +10s & Neon Green Play/Pause, Neon Green seekbar, and 1-tap bottom pills.
                ImmersiveVideoCinemaLayout(
                    currentMedia = currentMedia,
                    playbackState = playbackState,
                    userPreferences = userPreferences,
                    isFavorite = isFavorite,
                    isLandscapeViewport = isLandscapeViewport,
                    playerInstance = playerInstance,
                    shouldKeepVideoAwake = shouldKeepVideoAwake,
                    controlsVisible = fullscreenControlsVisible,
                    showVideoSettings = showVideoSettingsSheet,
                    showSubtitleSettings = showSubtitleSettings,
                    onToggleControlsVisible = { fullscreenControlsVisible = !fullscreenControlsVisible },
                    onSetControlsVisible = { fullscreenControlsVisible = it },
                    onToggleVideoSettings = {
                        showSubtitleSettings = false
                        showVideoSettingsSheet = !showVideoSettingsSheet
                    },
                    onToggleSubtitleSettings = {
                        showVideoSettingsSheet = false
                        showSubtitleSettings = !showSubtitleSettings
                    },
                    onCollapse = {
                        if (playbackState.isFullscreenVideo || isLandscapeViewport) {
                            onToggleFullscreen(false)
                            onSelectVideoOrientation(VideoOrientationPreference.PORTRAIT)
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
                    onRetryPlayback = onRetryPlayback
                )
            } else {
                // Standard Portrait Studio Deck (Music & Portrait Video)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 48.dp, start = 16.dp, end = 16.dp, top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Top Bar
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
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (currentMedia.mediaType == MediaType.VIDEO) "VIDEO CINEMA ENGINE" else "NOW PLAYING",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentMedia.folderName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                if (currentMedia.mediaType == MediaType.VIDEO) {
                                    IconButton(
                                        onClick = { showVideoSettingsSheet = !showVideoSettingsSheet },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("now_playing_video_settings_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Video Settings",
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showSleepTimerPanel = !showSleepTimerPanel },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("now_playing_sleep_timer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bedtime,
                                        contentDescription = "Sleep Timer",
                                        tint = if (playbackState.isSleepTimerActive) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                IconButton(
                                    onClick = onOpenEqualizer,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("now_playing_eq_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Equalizer,
                                        contentDescription = "Open Equalizer",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                IconButton(
                                    onClick = onOpenStudio,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("now_playing_studio_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCut,
                                        contentDescription = "Open Media Editor",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
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

                    // Key Features Quick Action Chips Row (Lyrics SRT/VTT, Sleep Timer, Equalizer, Studio Editor)
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("now_playing_feature_chips_row")
                        ) {
                            item {
                                FilterChip(
                                    selected = showSubtitleSettings || !playbackState.activeSubtitleText.isNullOrBlank(),
                                    onClick = { showSubtitleSettings = !showSubtitleSettings },
                                    label = {
                                        val fmt = playbackState.loadedSubtitleFormat?.name ?: "SRT/VTT"
                                        Text("Lyrics ($fmt)")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Subtitles,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("now_playing_lyrics_chip")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = showSleepTimerPanel || playbackState.isSleepTimerActive,
                                    onClick = { showSleepTimerPanel = !showSleepTimerPanel },
                                    label = {
                                        val timerLabel = when {
                                            playbackState.sleepTimerStopAtEndOfTrack -> "Sleep: End of Track"
                                            playbackState.sleepTimerRemainingMs > 0L ->
                                                "Sleep: ${formatDurationMillis(playbackState.sleepTimerRemainingMs)}"
                                            else -> "Sleep Timer"
                                        }
                                        Text(timerLabel)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Bedtime,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("now_playing_sleep_timer_chip")
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = onOpenEqualizer,
                                    label = { Text("Equalizer & Bass") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Equalizer,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("now_playing_eq_chip")
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = onOpenStudio,
                                    label = { Text("Audio/Video Edit") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.ContentCut,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("now_playing_editor_chip")
                                )
                            }
                        }
                    }

                    // Sleep Timer (Automatic Stop) Panel
                    if (showSleepTimerPanel || playbackState.isSleepTimerActive) {
                        item {
                            SleepTimerControlCard(
                                playbackState = playbackState,
                                onSetSleepTimerMinutes = onSetSleepTimerMinutes,
                                onSetSleepTimerEndOfTrack = onSetSleepTimerEndOfTrack
                            )
                        }
                    }

                    // Video Settings Panel (Speed, Subtitles, Audio Track, Screen Rotation, Keep Screen On, Gestures)
                    if (currentMedia.mediaType == MediaType.VIDEO && showVideoSettingsSheet) {
                        item {
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

                    // Synchronized Lyrics & Subtitle Controls Panel (SRT / WebVTT for Music & Video)
                    if (showSubtitleSettings || playbackState.subtitleError != null) {
                        item {
                            SubtitleSettingsCard(
                                playbackState = playbackState,
                                userPreferences = userPreferences,
                                onToggleSubtitlesEnabled = onToggleSubtitlesEnabled,
                                onSetSubtitleFontScale = onSetSubtitleFontScale,
                                onSetSubtitleOffsetMs = onSetSubtitleOffsetMs,
                                onPickSubtitleFileClick = onPickSubtitleFileClick,
                                onSeekToCue = onSeekTo
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

                    // Playback Speed Selector Strip (0.5x – 3.0x) + Fine Speed Slider
                    item {
                        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)
                        Column {
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
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                Text(
                                    text = "Range: 0.5x – 3.0x",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(speeds) { spd ->
                                    FilterChip(
                                        selected = abs(playbackState.playbackSpeed - spd) < 0.05f,
                                        onClick = { onSetPlaybackSpeed(spd) },
                                        label = { Text("${spd}x") },
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
                                fontWeight = FontWeight.Bold
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
                                    Text("Save as Playlist", style = MaterialTheme.typography.labelMedium)
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

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmersiveVideoCinemaLayout(
    currentMedia: MediaEntity,
    playbackState: PlaybackUiState,
    userPreferences: UserPreferencesState,
    isFavorite: Boolean,
    isLandscapeViewport: Boolean,
    playerInstance: Player?,
    shouldKeepVideoAwake: Boolean,
    controlsVisible: Boolean,
    showVideoSettings: Boolean,
    showSubtitleSettings: Boolean,
    onToggleControlsVisible: () -> Unit,
    onSetControlsVisible: (Boolean) -> Unit,
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
    onRetryPlayback: () -> Unit
) {
    var containerWidthPx by remember { mutableIntStateOf(1280) }
    var containerHeightPx by remember { mutableIntStateOf(720) }
    var activeAudioTrackLabel by remember { mutableStateOf("Original HD Audio") }
    var showAudioTrackSheet by remember { mutableStateOf(false) }
    var gestureFeedbackBanner by remember { mutableStateOf<String?>(null) }

    // Auto-hide HUD overlay after 3.8s while video is actively playing and no settings sheet is open
    LaunchedEffect(
        controlsVisible,
        playbackState.isPlaying,
        showVideoSettings,
        showSubtitleSettings,
        showAudioTrackSheet,
        playbackState.isVideoControlsLocked
    ) {
        if (controlsVisible &&
            playbackState.isPlaying &&
            !showVideoSettings &&
            !showSubtitleSettings &&
            !showAudioTrackSheet &&
            !playbackState.isVideoControlsLocked
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
        if (isCurrentlyLandscapeMode) {
            onSelectVideoOrientation(VideoOrientationPreference.PORTRAIT)
            onToggleFullscreen(false)
        } else {
            onSelectVideoOrientation(VideoOrientationPreference.LANDSCAPE)
            onToggleFullscreen(true)
        }
    }

    val seekRelativeMs: (Long) -> Unit = { deltaMs ->
        val duration = playbackState.durationMs.coerceAtLeast(1L)
        val target = (playbackState.currentPositionMs + deltaMs).coerceIn(0L, duration)
        onSeekTo(target)
        gestureFeedbackBanner = if (deltaMs > 0) "+10s Forward" else "-10s Rewind"
    }

    val cycleSpeedQuick: () -> Unit = {
        val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.5f, 0.75f)
        val currentIdx = speeds.indexOfFirst { abs(it - playbackState.playbackSpeed) < 0.04f }
        val nextSpeed = speeds[(currentIdx + 1) % speeds.size]
        onSetPlaybackSpeed(nextSpeed)
        gestureFeedbackBanner = "Speed ${nextSpeed}x"
    }

    val effectiveWidth = currentMedia.width.takeIf { it > 0 } ?: playbackState.decodedVideoWidth
    val effectiveHeight = currentMedia.height.takeIf { it > 0 } ?: playbackState.decodedVideoHeight
    val resolutionTag = remember(effectiveWidth, effectiveHeight) {
        Resilient4KCodecEngine.formatVerifiedResolutionBadge(effectiveWidth, effectiveHeight)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { sz ->
                containerWidthPx = sz.width.coerceAtLeast(1)
                containerHeightPx = sz.height.coerceAtLeast(1)
            }
            .pointerInput(
                userPreferences.videoGestureControlsEnabled,
                playbackState.isVideoControlsLocked,
                playbackState.currentPositionMs,
                playbackState.durationMs
            ) {
                detectTapGestures(
                    onTap = { onToggleControlsVisible() },
                    onDoubleTap = { tapOffset ->
                        if (!playbackState.isVideoControlsLocked) {
                            val widthPx = size.width.coerceAtLeast(1)
                            when {
                                tapOffset.x < widthPx * 0.35f -> seekRelativeMs(-10_000L)
                                tapOffset.x > widthPx * 0.65f -> seekRelativeMs(10_000L)
                                else -> onPlayPause()
                            }
                        }
                    }
                )
            }
            .pointerInput(userPreferences.videoGestureControlsEnabled, playbackState.isVideoControlsLocked) {
                if (userPreferences.videoGestureControlsEnabled && !playbackState.isVideoControlsLocked) {
                    detectVerticalDragGestures { change, dragAmount ->
                        val widthPx = size.width.coerceAtLeast(1)
                        val delta = -dragAmount / 500f
                        if (change.position.x < widthPx / 2f) {
                            onBrightnessGestureDelta(delta)
                            val pct = ((playbackState.brightnessGestureLevel + delta).coerceIn(0.1f, 1f) * 100).roundToInt()
                            gestureFeedbackBanner = "Brightness $pct%"
                        } else {
                            onVolumeGestureDelta(delta)
                            val pct = ((playbackState.volumeGestureLevel + delta).coerceIn(0f, 1f) * 100).roundToInt()
                            gestureFeedbackBanner = "Volume $pct%"
                        }
                    }
                }
            }
            .testTag("video_surface_container")
    ) {
        // Video Surface Container centered on pure black cinema canvas
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

        // Brightness gesture overlay
        val darknessAlpha = (1f - playbackState.brightnessGestureLevel).coerceIn(0f, 0.75f)
        if (darknessAlpha > 0.05f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = darknessAlpha))
            )
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

        // If Controls are Locked, only show minimal unlock pill
        if (playbackState.isVideoControlsLocked) {
            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            ) {
                Button(
                    onClick = { onToggleControlsLock(false) },
                    modifier = Modifier.testTag("video_unlock_button")
                ) {
                    Icon(Icons.Default.LockOpen, contentDescription = "Unlock video controls")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock Video Controls")
                }
            }
            return@Box
        }

        // Fullscreen / Portrait Cinema Translucent HUD Overlay (matches reference screen recording)
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
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
                    .padding(
                        horizontal = if (isLandscapeViewport) 22.dp else 14.dp,
                        vertical = if (isLandscapeViewport) 14.dp else 18.dp
                    )
            ) {
                // 1. TOP BAR: Back Arrow, Video Title + "FHD 1080p • 39.7 MB" subtitle, Lock, CC, Equalizer, 1-Tap Rotate
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
                            onClick = onCollapse,
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
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subtitleMetaText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.76f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("video_resolution_meta_label")
                            )
                            Text(
                                text = playbackState.decoderStatusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (playbackState.isDecoderVerified) {
                                    CinemaNeonGreen.copy(alpha = 0.9f)
                                } else {
                                    Color.White.copy(alpha = 0.6f)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("video_decoder_status_badge")
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Lock Controls Icon
                        IconButton(
                            onClick = { onToggleControlsLock(true) },
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
                            onClick = onToggleSubtitleSettings,
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
                            onClick = onOpenEqualizer,
                            modifier = Modifier.testTag("now_playing_eq_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Equalizer,
                                contentDescription = "Equalizer",
                                tint = Color.White
                            )
                        }
                        // 1-Tap Landscape/Portrait Screen Rotation Icon (top-right phone rotation icon from video)
                        IconButton(
                            onClick = toggleLandscapePortraitQuick,
                            modifier = Modifier.testTag("video_fullscreen_chip")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = "Toggle Landscape / Portrait",
                                tint = if (isCurrentlyLandscapeMode) CinemaNeonGreen else Color.White
                            )
                        }
                    }
                }

                // 2. CENTER 5-BUTTON TRANSPORT OVERLAY: Previous | -10s Replay | Neon Green Play/Pause | +10s Forward | Next
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(if (isLandscapeViewport) 24.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Video
                    IconButton(
                        onClick = onPrevious,
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
                            .size(54.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                            .testTag("transport_rewind_10_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Vibrant Neon Green Play / Pause Circle (68.dp)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(CinemaNeonGreen, CircleShape)
                            .clickable(onClick = onPlayPause)
                            .testTag("transport_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = Color(0xFF06150D),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Fast-Forward 10 Seconds (↻10)
                    IconButton(
                        onClick = { seekRelativeMs(10_000L) },
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                            .testTag("transport_forward_10_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Next Video
                    IconButton(
                        onClick = onNext,
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
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = playbackState.playbackError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = onRetryPlayback,
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
                            color = Color.White
                        )
                        Text(
                            text = formatDurationMillis(duration),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    // Neon Green SeekBar with vertical green thumb bar
                    Slider(
                        value = pos.toFloat(),
                        onValueChange = { onSeekTo(it.toLong()) },
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
                            .height(28.dp)
                            .testTag("now_playing_seek_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

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
                                onClick = { showAudioTrackSheet = !showAudioTrackSheet },
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
                                onClick = onCycleRepeat,
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
                                onClick = onRotateVideo90,
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
                                onClick = onToggleFavorite,
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
                                onClick = onOpenStudio,
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
                                onClick = onToggleVideoSettings,
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
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 112.dp)
                            .width(320.dp)
                            .border(1.dp, CinemaPillBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Audio Track & Sound Profile",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
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

                // Floating Video Settings / Subtitle Drawer in Cinema Mode
                if (showVideoSettings || showSubtitleSettings) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(340.dp)
                            .fillMaxHeight(0.82f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (showVideoSettings) {
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
                        } else if (showSubtitleSettings) {
                            SubtitleSettingsCard(
                                playbackState = playbackState,
                                userPreferences = userPreferences,
                                onToggleSubtitlesEnabled = onToggleSubtitlesEnabled,
                                onSetSubtitleFontScale = onSetSubtitleFontScale,
                                onSetSubtitleOffsetMs = onSetSubtitleOffsetMs,
                                onPickSubtitleFileClick = onPickSubtitleFileClick
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
    subtitlesEnabled: Boolean = true
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audio_visualizer_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Circular Neon Acoustic Ring + Studio Disc
            Box(
                modifier = Modifier.size(188.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 6.dp.toPx()
                    val radius = (size.minDimension - strokeWidth * 2) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val progress = if (playbackState.durationMs > 0L) {
                        (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    // Outer studio ring
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.22f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeWidth)
                    )

                    // Active playback arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(primaryColor, secondaryColor, tertiaryColor, primaryColor)
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceAtLeast(0.04f),
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.38f),
                                    secondaryColor.copy(alpha = 0.18f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .border(1.dp, secondaryColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = secondaryColor,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentMedia.mimeType.substringAfterLast('/').uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Horizontal Studio Waveform Bar
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
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
 * Sleep Timer (Automatic Stop) Control Card.
 */
@Composable
private fun SleepTimerControlCard(
    playbackState: PlaybackUiState,
    onSetSleepTimerMinutes: (Int) -> Unit,
    onSetSleepTimerEndOfTrack: (Boolean) -> Unit
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Sleep Timer (Automatic Stop)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
    onSeekToCue: (Long) -> Unit = {}
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
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = userPreferences.subtitlesEnabledByDefault,
                    onCheckedChange = onToggleSubtitlesEnabled,
                    modifier = Modifier.testTag("subtitle_enable_switch")
                )
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync Offset: ${userPreferences.subtitleSyncOffsetMs} ms",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { onSetSubtitleOffsetMs(userPreferences.subtitleSyncOffsetMs - 250L) }
                    ) { Text("-250ms") }
                    OutlinedButton(
                        onClick = { onSetSubtitleOffsetMs(0L) }
                    ) { Text("0ms") }
                    OutlinedButton(
                        onClick = { onSetSubtitleOffsetMs(userPreferences.subtitleSyncOffsetMs + 250L) }
                    ) { Text("+250ms") }
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
