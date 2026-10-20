package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.data.local.MediaEntity
import com.example.data.local.MediaType
import com.example.data.preferences.UserPreferencesState
import com.example.media.HardwareEqualizerState
import com.example.ui.components.formatDurationMillis
import com.example.viewmodel.PlaybackUiState
import com.example.viewmodel.RepeatModeState
import com.example.viewmodel.VideoAspectMode
import kotlin.math.sin

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
    onBrightnessGestureDelta: (Float) -> Unit,
    onVolumeGestureDelta: (Float) -> Unit,
    onPickSubtitleFileClick: () -> Unit,
    onToggleSubtitlesEnabled: (Boolean) -> Unit,
    onSetSubtitleFontScale: (Float) -> Unit,
    onSetSubtitleOffsetMs: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenStudio: () -> Unit,
    onSelectQueueItem: (MediaEntity) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onRetryPlayback: () -> Unit
) {
    BackHandler {
        if (playbackState.isFullscreenVideo) {
            onToggleFullscreen(false)
        } else {
            onCollapse()
        }
    }

    val currentMedia = playbackState.currentMedia
    var showSubtitleSettings by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }

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
                            text = if (currentMedia.mediaType == MediaType.VIDEO) "VIDEO CINEMA ENGINE" else "AUDIO STUDIO DECK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = currentMedia.folderName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
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

            // Playback Error Banner with Retry (Phase 6 & 7)
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
                    val aspectRatioModifier = when (playbackState.videoAspectMode) {
                        VideoAspectMode.SIXTEEN_NINE -> Modifier.aspectRatio(16f / 9f)
                        VideoAspectMode.FOUR_THREE -> Modifier.aspectRatio(4f / 3f)
                        else -> Modifier.height(if (playbackState.isFullscreenVideo) 420.dp else 240.dp)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(aspectRatioModifier)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.Black)
                            .pointerInput(Unit) {
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
                            .testTag("video_surface_container")
                    ) {
                        if (playerInstance != null) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        useController = false
                                        player = playerInstance
                                        resizeMode = when (playbackState.videoAspectMode) {
                                            VideoAspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        }
                                    }
                                },
                                update = { view ->
                                    if (view.player !== playerInstance) {
                                        view.player = playerInstance
                                    }
                                    view.resizeMode = when (playbackState.videoAspectMode) {
                                        VideoAspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        rotationZ = playbackState.videoRotationDegrees.toFloat()
                                    }
                            )
                        }

                        // Brightness overlay simulation for left-side vertical gesture
                        val darknessAlpha = (1f - playbackState.brightnessGestureLevel).coerceIn(0f, 0.75f)
                        if (darknessAlpha > 0.05f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = darknessAlpha))
                            )
                        }

                        // Live Subtitle Overlay (Phase 9: SRT / WebVTT with font scale & offset)
                        if (userPreferences.subtitlesEnabledByDefault && !playbackState.activeSubtitleText.isNullOrBlank()) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.75f),
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

                        // Gesture telemetry badges (Brightness & Volume)
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

                    // Video Controls Strip: Aspect Ratio, Rotate, Fullscreen, Subtitles
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                onClick = { showSubtitleSettings = !showSubtitleSettings },
                                label = {
                                    Text(
                                        playbackState.loadedSubtitleFormat?.name
                                            ?: "Subtitles"
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                modifier = Modifier.testTag("video_subtitles_chip")
                            )
                        }
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
                    }
                } else {
                    // Acoustic Studio Waveform Canvas for Audio Playback
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val secondaryColor = MaterialTheme.colorScheme.secondary
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .testTag("audio_visualizer_card"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val barCount = 32
                                val totalWidth = size.width
                                val totalHeight = size.height
                                val barWidth = (totalWidth / (barCount * 1.8f)).coerceAtLeast(4f)
                                val spacing = (totalWidth - (barWidth * barCount)) / (barCount - 1).coerceAtLeast(1)
                                val phase = (playbackState.currentPositionMs / 180.0)

                                for (i in 0 until barCount) {
                                    val wave = if (playbackState.isPlaying) {
                                        0.25f + 0.7f * kotlin.math.abs(sin(phase + i * 0.45)).toFloat()
                                    } else {
                                        0.2f + 0.3f * kotlin.math.abs(sin(i * 0.5)).toFloat()
                                    }
                                    val barHeight = (totalHeight * wave).coerceAtLeast(12f)
                                    val x = i * (barWidth + spacing)
                                    val y = (totalHeight - barHeight) / 2f
                                    drawRoundRect(
                                        color = if (i % 3 == 0) secondaryColor else primaryColor,
                                        topLeft = Offset(x, y),
                                        size = Size(barWidth, barHeight),
                                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Subtitle Controls Panel (Phase 9)
            if (showSubtitleSettings || playbackState.subtitleError != null) {
                item {
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
                                    text = "Subtitle Engine (SRT & WebVTT Verified)",
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
                                    "Loaded: $it (${playbackState.loadedSubtitleFormat}) • ${playbackState.loadedSubtitleCues.size} cues"
                                } ?: "No subtitle track loaded",
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
                                Text("Load Local .SRT / .VTT File (SAF)")
                            }
                        }
                    }
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
                        Text(
                            text = "${currentMedia.artist} • ${currentMedia.album}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

            // Playback Speed Selector Strip
            item {
                val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                Column {
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
                            text = "Playback Speed (${playbackState.playbackSpeed}x)",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(speeds) { spd ->
                            FilterChip(
                                selected = kotlin.math.abs(playbackState.playbackSpeed - spd) < 0.05f,
                                onClick = { onSetPlaybackSpeed(spd) },
                                label = { Text("${spd}x") },
                                modifier = Modifier.testTag("speed_chip_${spd}")
                            )
                        }
                    }
                }
            }

            // Active Playback Queue Management (Phase 6)
            item {
                Text(
                    text = "Active Playback Queue (${playbackState.queue.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
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
                                text = formatDurationMillis(item.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (playbackState.queue.size > 1) {
                            IconButton(
                                onClick = { onRemoveQueueItem(idx) },
                                modifier = Modifier.minimumInteractiveComponentSize()
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
 * Phase 10: Hardware Equalizer & Audio Effects Bottom Sheet with Device Capability Detection.
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    eqState: HardwareEqualizerState,
    onDismiss: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSelectPreset: (Int) -> Unit,
    onBandLevelChange: (Int, Int) -> Unit,
    onBassBoostChange: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("equalizer_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                        text = "Hardware Equalizer & Bass DSP",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = eqState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = eqState.isEnabled,
                    onCheckedChange = onSetEnabled,
                    modifier = Modifier.testTag("equalizer_enable_switch")
                )
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
                    "${band.centerFreqHz / 1000} kHz"
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
                        enabled = eqState.isEnabled
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
                        text = if (eqState.isBassBoostSupported) {
                            "Hardware Bass Boost"
                        } else {
                            "Bass Boost (Saved Preference; Hardware DSP Inactive)"
                        },
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
                    enabled = eqState.isEnabled
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
