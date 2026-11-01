package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.data.local.MediaEntity
import com.example.media.rememberMediaArtworkBitmap
import com.example.viewmodel.PlaybackUiState
import com.example.viewmodel.RepeatModeState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Three-State Expandable Playlist & Smart Playback Queue States:
 * - [COLLAPSED] (State A): Normal Now Playing screen visible with bottom dock handle / Playlist button.
 * - [PARTIALLY_EXPANDED] (State B): Opens ~40% of screen height from the bottom while keeping Now Playing visible behind.
 * - [FULLY_EXPANDED] (State C): Expands to ~92% of screen height toward the top with sticky header & full virtualized queue.
 */
enum class PlaylistSheetState {
    COLLAPSED,
    PARTIALLY_EXPANDED,
    FULLY_EXPANDED;

    fun nextExpandedState(): PlaylistSheetState = when (this) {
        COLLAPSED -> PARTIALLY_EXPANDED
        PARTIALLY_EXPANDED -> FULLY_EXPANDED
        FULLY_EXPANDED -> COLLAPSED
    }
}

/**
 * Bottom Docked Handle Bar shown in State A ([PlaylistSheetState.COLLAPSED]) at the bottom of the
 * Now Playing screen. Lets the user either tap or drag upward to open the Expandable Playlist Sheet
 * while music continues playing uninterrupted.
 */
@Composable
fun CollapsedPlaylistDockBar(
    playbackState: PlaybackUiState,
    sheetState: PlaylistSheetState,
    onSheetStateChange: (PlaylistSheetState) -> Unit,
    onDragDeltaPx: (Float) -> Unit = {},
    onDragEndWithVelocity: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val queueSize = playbackState.queue.size
    val nextIndex = if (queueSize > 0) {
        val candidate = playbackState.currentQueueIndex + 1
        if (candidate in playbackState.queue.indices) candidate
        else if (playbackState.repeatMode == RepeatModeState.ALL) 0
        else -1
    } else -1
    val nextTrack = playbackState.queue.getOrNull(nextIndex)

    var accumulatedDragY by remember(sheetState) { mutableFloatStateOf(0f) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .pointerInput(sheetState) {
                detectVerticalDragGestures(
                    onDragStart = {
                        accumulatedDragY = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragY += dragAmount
                        onDragDeltaPx(dragAmount)
                    },
                    onDragEnd = {
                        when {
                            accumulatedDragY < -180f -> {
                                onSheetStateChange(PlaylistSheetState.FULLY_EXPANDED)
                            }
                            accumulatedDragY < -24f -> {
                                onSheetStateChange(PlaylistSheetState.PARTIALLY_EXPANDED)
                            }
                            accumulatedDragY > 24f && sheetState != PlaylistSheetState.COLLAPSED -> {
                                onSheetStateChange(PlaylistSheetState.COLLAPSED)
                            }
                        }
                        accumulatedDragY = 0f
                        onDragEndWithVelocity(0f)
                    },
                    onDragCancel = {
                        accumulatedDragY = 0f
                        onDragEndWithVelocity(0f)
                    }
                )
            }
            .clickable {
                onSheetStateChange(
                    if (sheetState == PlaylistSheetState.COLLAPSED) {
                        PlaylistSheetState.PARTIALLY_EXPANDED
                    } else {
                        PlaylistSheetState.COLLAPSED
                    }
                )
            }
            .testTag("playlist_sheet_dock_bar"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.90f),
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag Handle Pill
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                    .testTag("playlist_dock_drag_pill")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = playbackState.queueTitle.ifBlank { "Playing Queue" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "$queueSize ${if (queueSize == 1) "song" else "songs"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = when {
                                nextTrack != null -> "Up Next: ${nextTrack.title} • ${nextTrack.artist}"
                                queueSize > 0 -> "End of queue • Tap or swipe up to manage playlist"
                                else -> "Queue is empty"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = {
                        onSheetStateChange(
                            if (sheetState == PlaylistSheetState.COLLAPSED) {
                                PlaylistSheetState.PARTIALLY_EXPANDED
                            } else {
                                PlaylistSheetState.COLLAPSED
                            }
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_playlist_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandLess,
                        contentDescription = "Open Playlist Queue"
                    )
                }
            }
        }
    }
}

/**
 * Three-State Expandable Bottom-Sheet Playlist & Smart Playback Queue Overlay.
 *
 * - State A ([PlaylistSheetState.COLLAPSED]): Hidden sheet body (dock bar is shown on Now Playing screen).
 * - State B ([PlaylistSheetState.PARTIALLY_EXPANDED]): ~40% screen height from bottom; Now Playing remains
 *   visible behind a subtle dim scrim.
 * - State C ([PlaylistSheetState.FULLY_EXPANDED]): ~92% screen height toward top; includes compact mini-player
 *   bar, sticky playlist header, and virtualized LazyColumn supporting 1,000–10,000+ songs.
 */
@Composable
fun ExpandablePlaylistBottomSheetOverlay(
    playbackState: PlaybackUiState,
    sheetState: PlaylistSheetState,
    listState: LazyListState,
    onSheetStateChange: (PlaylistSheetState) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSelectQueueItemAt: (Int, MediaEntity) -> Unit,
    onRemoveQueueItemAt: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onPlayNextItem: (MediaEntity) -> Unit,
    onClearQueue: () -> Unit,
    onSaveQueueAsPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var showClearConfirmationDialog by rememberSaveable { mutableStateOf(false) }

    if (showClearConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmationDialog = false },
            title = {
                Text(
                    text = "Clear Playback Queue?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will remove all ${playbackState.queue.size} songs from the current playback queue and stop playback. Your saved playlists and device files will not be deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmationDialog = false
                        onClearQueue()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_clear_queue_button")
                ) {
                    Text("Clear Queue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmationDialog = false },
                    modifier = Modifier.testTag("cancel_clear_queue_button")
                ) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("confirm_clear_queue_dialog")
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerHeightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }
        val isLandscape = maxWidth > maxHeight

        // Target height fractions for the 3 states:
        // State A (COLLAPSED): 0f
        // State B (PARTIALLY_EXPANDED): 0.42f in Portrait (0.52f in compact Landscape) -> within 30-45% spec
        // State C (FULLY_EXPANDED): 0.92f
        val partialFraction = if (isLandscape) 0.50f else 0.42f
        val fullFraction = 0.92f

        val targetFraction = when (sheetState) {
            PlaylistSheetState.COLLAPSED -> 0f
            PlaylistSheetState.PARTIALLY_EXPANDED -> partialFraction
            PlaylistSheetState.FULLY_EXPANDED -> fullFraction
        }

        // Interactive drag offset (in fraction of screen height, positive = taller sheet)
        var dragFractionDelta by remember(sheetState) { mutableFloatStateOf(0f) }

        val animatedFraction by animateFloatAsState(
            targetValue = (targetFraction + dragFractionDelta).coerceIn(0f, fullFraction),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "playlistSheetHeightFraction"
        )

        val settleSheetFromFraction: (Float, Float) -> Unit = { currentFraction, velocityY ->
            dragFractionDelta = 0f
            val nextState = when {
                velocityY < -650f -> {
                    if (sheetState == PlaylistSheetState.COLLAPSED) PlaylistSheetState.PARTIALLY_EXPANDED
                    else PlaylistSheetState.FULLY_EXPANDED
                }
                velocityY > 650f -> {
                    if (sheetState == PlaylistSheetState.FULLY_EXPANDED) PlaylistSheetState.PARTIALLY_EXPANDED
                    else PlaylistSheetState.COLLAPSED
                }
                currentFraction < partialFraction * 0.55f -> PlaylistSheetState.COLLAPSED
                currentFraction < (partialFraction + fullFraction) * 0.5f -> PlaylistSheetState.PARTIALLY_EXPANDED
                else -> PlaylistSheetState.FULLY_EXPANDED
            }
            onSheetStateChange(nextState)
        }

        // NestedScrollConnection so scrolling inside LazyColumn never conflicts with sheet dragging:
        // - In PARTIALLY_EXPANDED: pulling up expands the sheet toward FULLY_EXPANDED first;
        //   pulling down when LazyColumn is at top collapses toward COLLAPSED.
        // - In FULLY_EXPANDED: LazyColumn scrolls freely through thousands of songs;
        //   pulling down when at the very top of LazyColumn collapses toward PARTIALLY_EXPANDED.
        val nestedScrollConnection = remember(sheetState, containerHeightPx, partialFraction, fullFraction) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    val dy = available.y
                    val currentFraction = (targetFraction + dragFractionDelta).coerceIn(0f, fullFraction)

                    // Pulling upward (dy < 0) while sheet is below FULLY_EXPANDED -> expand sheet smoothly
                    if (dy < 0f && currentFraction < fullFraction - 0.01f) {
                        val deltaFraction = (-dy) / containerHeightPx
                        dragFractionDelta = (dragFractionDelta + deltaFraction)
                            .coerceIn(-targetFraction, fullFraction - targetFraction)
                        return Offset(0f, dy)
                    }

                    // Pulling downward (dy > 0) while dragFractionDelta > 0 -> shrink back before scrolling list
                    if (dy > 0f && dragFractionDelta > 0f) {
                        val deltaFraction = (-dy) / containerHeightPx
                        dragFractionDelta = (dragFractionDelta + deltaFraction)
                            .coerceIn(-targetFraction, fullFraction - targetFraction)
                        return Offset(0f, dy)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    val unconsumedDy = available.y
                    val isAtListTop = listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset == 0
                    if (unconsumedDy > 0f && isAtListTop) {
                        val deltaFraction = (-unconsumedDy) / containerHeightPx
                        dragFractionDelta = (dragFractionDelta + deltaFraction)
                            .coerceIn(-targetFraction, fullFraction - targetFraction)
                        return Offset(0f, unconsumedDy)
                    }
                    return Offset.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (dragFractionDelta != 0f) {
                        val currentFraction = (targetFraction + dragFractionDelta).coerceIn(0f, fullFraction)
                        settleSheetFromFraction(currentFraction, available.y)
                    }
                    return Velocity.Zero
                }
            }
        }

        // Dim Backdrop Scrim behind the sheet (keeps Now Playing visible behind State B)
        val scrimAlpha = ((animatedFraction / fullFraction) * 0.68f).coerceIn(0f, 0.68f)
        AnimatedVisibility(
            visible = sheetState != PlaylistSheetState.COLLAPSED || animatedFraction > 0.02f,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onSheetStateChange(PlaylistSheetState.COLLAPSED)
                    }
                    .testTag("playlist_sheet_backdrop_scrim")
            )
        }

        // Expandable Bottom Sheet Panel
        if (sheetState != PlaylistSheetState.COLLAPSED || animatedFraction > 0.01f) {
            val effectiveLayoutFraction = animatedFraction.coerceAtLeast(partialFraction)
            val sheetHeightDp = with(density) { (containerHeightPx * effectiveLayoutFraction).toDp() }
            val slideDownOffsetPx = if (animatedFraction < partialFraction) {
                ((partialFraction - animatedFraction) * containerHeightPx).roundToInt()
            } else {
                0
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, slideDownOffsetPx) }
                    .widthIn(max = if (isLandscape) 720.dp else 640.dp)
                    .fillMaxWidth()
                    .height(sheetHeightDp)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .nestedScroll(nestedScrollConnection)
                    .testTag("expandable_playlist_sheet"),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 12.dp,
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                ) {
                    // Draggable Sticky Playlist Header
                    ExpandablePlaylistHeader(
                        playbackState = playbackState,
                        sheetState = sheetState,
                        onDragDeltaPx = { dy ->
                            val deltaFraction = (-dy) / containerHeightPx
                            dragFractionDelta = (dragFractionDelta + deltaFraction)
                                .coerceIn(-targetFraction, fullFraction - targetFraction)
                        },
                        onDragEnd = {
                            val currentFraction = (targetFraction + dragFractionDelta).coerceIn(0f, fullFraction)
                            settleSheetFromFraction(currentFraction, 0f)
                        },
                        onToggleExpandState = {
                            onSheetStateChange(
                                if (sheetState == PlaylistSheetState.PARTIALLY_EXPANDED) {
                                    PlaylistSheetState.FULLY_EXPANDED
                                } else {
                                    PlaylistSheetState.PARTIALLY_EXPANDED
                                }
                            )
                        },
                        onCollapseSheet = {
                            onSheetStateChange(PlaylistSheetState.COLLAPSED)
                        },
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        onScrollToCurrentSong = {
                            val targetIdx = playbackState.currentQueueIndex
                            if (targetIdx in playbackState.queue.indices) {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(targetIdx)
                                }
                            }
                        },
                        onSaveQueueAsPlaylist = {
                            onSaveQueueAsPlaylist(
                                "${playbackState.queueTitle.ifBlank { "Queue Mix" }} (${playbackState.queue.size} tracks)"
                            )
                        },
                        onRequestClearQueue = {
                            if (playbackState.queue.isNotEmpty()) {
                                showClearConfirmationDialog = true
                            }
                        }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    // Virtualized Queue Song List (supports 1,000–10,000+ tracks smoothly)
                    if (playbackState.queue.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .testTag("playlist_sheet_empty_state"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "Playback Queue is Empty",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Select any song from your library or use 'Play Next' / 'Add to Queue' to build your queue.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        var draggedItemIndex by remember { mutableIntStateOf(-1) }
                        var dragAccumulatedPx by remember { mutableFloatStateOf(0f) }
                        val rowHeightEstimatePx = with(density) { 68.dp.toPx().coerceAtLeast(1f) }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("expandable_playlist_lazy_column"),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 24.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(
                                    items = playbackState.queue,
                                    key = { idx, item -> "${idx}_${item.uriString}" },
                                    contentType = { _, _ -> "queue_song_row" }
                                ) { idx, item ->
                                    val isCurrent = idx == playbackState.currentQueueIndex
                                    val isBeingDragged = draggedItemIndex == idx

                                    PlaylistQueueSongRow(
                                        index = idx,
                                        totalCount = playbackState.queue.size,
                                        media = item,
                                        isCurrentPlaying = isCurrent,
                                        isPlayingAudio = playbackState.isPlaying,
                                        isBeingDragged = isBeingDragged,
                                        dragOffsetPx = if (isBeingDragged) dragAccumulatedPx else 0f,
                                        onPlayClick = { onSelectQueueItemAt(idx, item) },
                                        onRemoveClick = { onRemoveQueueItemAt(idx) },
                                        onMoveUpClick = {
                                            if (idx > 0) onMoveQueueItem(idx, idx - 1)
                                        },
                                        onMoveDownClick = {
                                            if (idx < playbackState.queue.lastIndex) onMoveQueueItem(idx, idx + 1)
                                        },
                                        onMoveToTopClick = {
                                            if (idx > 0) onMoveQueueItem(idx, 0)
                                        },
                                        onMoveToBottomClick = {
                                            if (idx < playbackState.queue.lastIndex) {
                                                onMoveQueueItem(idx, playbackState.queue.lastIndex)
                                            }
                                        },
                                        onPlayNextClick = { onPlayNextItem(item) },
                                        onStartDragReorder = {
                                            draggedItemIndex = idx
                                            dragAccumulatedPx = 0f
                                        },
                                        onDragReorderDelta = { deltaY ->
                                            if (draggedItemIndex < 0) {
                                                draggedItemIndex = idx
                                            }
                                            dragAccumulatedPx += deltaY
                                            val steps = (dragAccumulatedPx / rowHeightEstimatePx).toInt()
                                            if (steps != 0) {
                                                val from = draggedItemIndex
                                                val to = (from + steps).coerceIn(0, playbackState.queue.lastIndex)
                                                if (from in playbackState.queue.indices && to != from) {
                                                    onMoveQueueItem(from, to)
                                                    draggedItemIndex = to
                                                    dragAccumulatedPx -= steps * rowHeightEstimatePx
                                                }
                                            }
                                        },
                                        onEndDragReorder = {
                                            draggedItemIndex = -1
                                            dragAccumulatedPx = 0f
                                        }
                                    )
                                }
                            }

                            FastScrollbar(
                                listState = listState,
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sticky Header for the Expandable Playlist Sheet.
 * Remains accessible while scrolling, supports dragging from the handle or header surface,
 * and exposes Shuffle, Repeat, Scroll-to-Current, Save as Playlist, and Clear Queue actions.
 */
@Composable
private fun ExpandablePlaylistHeader(
    playbackState: PlaybackUiState,
    sheetState: PlaylistSheetState,
    onDragDeltaPx: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onToggleExpandState: () -> Unit,
    onCollapseSheet: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onScrollToCurrentSong: () -> Unit,
    onSaveQueueAsPlaylist: () -> Unit,
    onRequestClearQueue: () -> Unit
) {
    var optionsMenuExpanded by remember { mutableStateOf(false) }
    val currentMedia = playbackState.currentMedia
    val queueCount = playbackState.queue.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(sheetState) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        onDragDeltaPx(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("playlist_sheet_header")
    ) {
        // Center Collapse/Expand Drag Handle Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                    .testTag("playlist_sheet_drag_handle")
            )
        }

        // In State C (FULLY_EXPANDED), keep a sleek connected Now Playing mini-bar at the top of the sheet
        if (sheetState == PlaylistSheetState.FULLY_EXPANDED && currentMedia != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .testTag("playlist_sheet_mini_transport")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentMedia.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentMedia.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("playlist_sheet_mini_play_pause")
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play"
                            )
                        }
                        IconButton(
                            onClick = onNext,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("playlist_sheet_mini_next")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next track"
                            )
                        }
                    }
                }
            }
        }

        // Main Header Row: Title + Song Count + Shuffle + Repeat + Expand/Collapse + Options Menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playbackState.queueTitle.ifBlank { "Playing Queue" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("playlist_sheet_title")
                )
                val positionLabel = if (playbackState.currentQueueIndex in playbackState.queue.indices) {
                    "Track ${playbackState.currentQueueIndex + 1} of $queueCount"
                } else {
                    "$queueCount ${if (queueCount == 1) "song" else "songs"}"
                }
                val shuffleBadge = if (playbackState.shuffleEnabled) " • Shuffled" else ""
                Text(
                    text = "$positionLabel • $queueCount total$shuffleBadge",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("playlist_sheet_count")
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Shuffle Control
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("playlist_sheet_shuffle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = if (playbackState.shuffleEnabled) "Disable Shuffle" else "Enable Shuffle",
                        tint = if (playbackState.shuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                // Repeat Mode Control
                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("playlist_sheet_repeat_button")
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

                // Expand / Partial Toggle
                IconButton(
                    onClick = onToggleExpandState,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("playlist_sheet_expand_toggle_button")
                ) {
                    Icon(
                        imageVector = if (sheetState == PlaylistSheetState.FULLY_EXPANDED) {
                            Icons.Default.ExpandMore
                        } else {
                            Icons.Default.ExpandLess
                        },
                        contentDescription = if (sheetState == PlaylistSheetState.FULLY_EXPANDED) {
                            "Partially collapse playlist"
                        } else {
                            "Fully expand playlist"
                        }
                    )
                }

                // Queue Options Menu (Save as Playlist, Scroll to Current, Clear Queue, Close)
                Box {
                    IconButton(
                        onClick = { optionsMenuExpanded = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("playlist_sheet_options_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Queue options menu"
                        )
                    }

                    DropdownMenu(
                        expanded = optionsMenuExpanded,
                        onDismissRequest = { optionsMenuExpanded = false },
                        modifier = Modifier.testTag("playlist_sheet_options_menu")
                    ) {
                        DropdownMenuItem(
                            text = { Text("Jump to Current Song", maxLines = 1) },
                            leadingIcon = {
                                Icon(Icons.Default.MyLocation, contentDescription = null)
                            },
                            onClick = {
                                optionsMenuExpanded = false
                                onScrollToCurrentSong()
                            },
                            modifier = Modifier.testTag("queue_scroll_to_current_button")
                        )

                        DropdownMenuItem(
                            text = { Text("Save Queue as Playlist", maxLines = 1) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                            },
                            onClick = {
                                optionsMenuExpanded = false
                                onSaveQueueAsPlaylist()
                            },
                            modifier = Modifier.testTag("save_queue_as_playlist_button")
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Clear Queue",
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ClearAll,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                optionsMenuExpanded = false
                                onRequestClearQueue()
                            },
                            modifier = Modifier.testTag("queue_clear_button")
                        )

                        DropdownMenuItem(
                            text = { Text("Collapse Queue Panel", maxLines = 1) },
                            leadingIcon = {
                                Icon(Icons.Default.Close, contentDescription = null)
                            },
                            onClick = {
                                optionsMenuExpanded = false
                                onCollapseSheet()
                            },
                            modifier = Modifier.testTag("queue_collapse_menu_item")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual Song Row inside the Expandable Playlist & Smart Queue.
 *
 * Displays:
 * - 1-based queue position or active playing equalizer indicator
 * - Asynchronous cached album artwork thumbnail (safe for 10,000+ songs)
 * - Song title, artist, optional album name, and duration
 * - Drag handle for vertical drag-and-drop reordering
 * - Up/Down & More Options menu + Remove-from-queue button with 48.dp touch target spacing
 */
@Composable
fun PlaylistQueueSongRow(
    index: Int,
    totalCount: Int,
    media: MediaEntity,
    isCurrentPlaying: Boolean,
    isPlayingAudio: Boolean,
    isBeingDragged: Boolean = false,
    dragOffsetPx: Float = 0f,
    onPlayClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onMoveUpClick: () -> Unit,
    onMoveDownClick: () -> Unit,
    onMoveToTopClick: () -> Unit = {},
    onMoveToBottomClick: () -> Unit = {},
    onPlayNextClick: () -> Unit = {},
    onStartDragReorder: () -> Unit = {},
    onDragReorderDelta: (Float) -> Unit = {},
    onEndDragReorder: () -> Unit = {}
) {
    var rowMenuExpanded by remember { mutableStateOf(false) }

    // Asynchronous thumbnail loading with LRU memory caching (112px thumbnail prevents OOM on 10,000+ songs)
    val artworkBitmap by rememberMediaArtworkBitmap(
        media = media,
        artworkData = null,
        artworkUri = null,
        targetSizePx = 112
    )

    val containerColor = when {
        isBeingDragged -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.90f)
        isCurrentPlaying -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
            .then(
                if (isCurrentPlaying) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(14.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onPlayClick)
            .testTag("queue_song_row_$index"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isBeingDragged) 8.dp else if (isCurrentPlaying) 3.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag-and-drop reorder handle + Track Number / Active Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .pointerInput(index, totalCount) {
                        detectVerticalDragGestures(
                            onDragStart = { onStartDragReorder() },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                onDragReorderDelta(dragAmount)
                            },
                            onDragEnd = { onEndDragReorder() },
                            onDragCancel = { onEndDragReorder() }
                        )
                    }
                    .padding(end = 6.dp)
                    .testTag("queue_drag_handle_$index")
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder track ${index + 1}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier.width(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCurrentPlaying) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Currently playing track",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .testTag("queue_current_indicator_$index")
                        )
                    } else {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }

            // Album Artwork Thumbnail (or Fallback Icon)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                val thumb = artworkBitmap
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = "Artwork for ${media.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (!media.isAvailable) {
                            Icons.Default.WarningAmber
                        } else {
                            Icons.Default.MusicNote
                        },
                        contentDescription = null,
                        tint = if (!media.isAvailable) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Song Title, Artist, Optional Album, and Duration
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = media.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.Medium,
                        color = if (!media.isAvailable) {
                            MaterialTheme.colorScheme.error
                        } else if (isCurrentPlaying) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCurrentPlaying) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = if (isPlayingAudio) "PLAYING" else "PAUSED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                val albumPart = media.album.takeIf {
                    it.isNotBlank() &&
                        !it.equals("Unknown Album", ignoreCase = true) &&
                        !it.equals("Audio", ignoreCase = true) &&
                        !it.equals(media.folderName, ignoreCase = true)
                }?.let { " • $it" }.orEmpty()

                val statusPrefix = if (!media.isAvailable) "Unavailable • " else ""
                Text(
                    text = "$statusPrefix${media.artist}$albumPart • ${formatDurationMillis(media.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (!media.isAvailable) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Direct Remove-from-Queue Button + More-Options Menu with safe touch targets
            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("queue_remove_$index")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove ${media.title} from queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Row More-Options Menu
            Box {
                IconButton(
                    onClick = { rowMenuExpanded = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("queue_item_menu_$index")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options for ${media.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = rowMenuExpanded,
                    onDismissRequest = { rowMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Immediately", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = {
                            rowMenuExpanded = false
                            onPlayClick()
                        }
                    )
                    if (!isCurrentPlaying) {
                        DropdownMenuItem(
                            text = { Text("Play Next", maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.SkipNext, contentDescription = null) },
                            onClick = {
                                rowMenuExpanded = false
                                onPlayNextClick()
                            }
                        )
                    }
                    if (index > 0) {
                        DropdownMenuItem(
                            text = { Text("Move Up", maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) },
                            onClick = {
                                rowMenuExpanded = false
                                onMoveUpClick()
                            },
                            modifier = Modifier.testTag("queue_move_up_$index")
                        )
                        DropdownMenuItem(
                            text = { Text("Move to Top", maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.VerticalAlignTop, contentDescription = null) },
                            onClick = {
                                rowMenuExpanded = false
                                onMoveToTopClick()
                            }
                        )
                    }
                    if (index < totalCount - 1) {
                        DropdownMenuItem(
                            text = { Text("Move Down", maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                            onClick = {
                                rowMenuExpanded = false
                                onMoveDownClick()
                            },
                            modifier = Modifier.testTag("queue_move_down_$index")
                        )
                        DropdownMenuItem(
                            text = { Text("Move to Bottom", maxLines = 1) },
                            leadingIcon = { Icon(Icons.Default.VerticalAlignBottom, contentDescription = null) },
                            onClick = {
                                rowMenuExpanded = false
                                onMoveToBottomClick()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Remove from Queue",
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            rowMenuExpanded = false
                            onRemoveClick()
                        }
                    )
                }
            }
        }
    }
}
