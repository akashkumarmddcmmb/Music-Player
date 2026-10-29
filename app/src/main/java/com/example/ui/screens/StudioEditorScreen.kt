package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.ui.components.FastScrollbar
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.JobStatus
import com.example.data.local.MediaEntity
import com.example.data.local.MediaType
import com.example.data.local.ProcessingJobEntity
import com.example.media.ConversionProfile
import com.example.media.MediaFileActionsHelper
import com.example.ui.components.formatDurationMillis
import com.example.ui.components.formatFileSize
import java.io.File

enum class StudioMode(val label: String) {
    AUDIO_TRIM("Audio Trim"),
    VIDEO_EDIT("Video Trim & Rotate"),
    AUDIO_EXTRACT("Extract Audio"),
    FORMAT_CONVERT("Format Converter")
}

/**
 * Immutable snapshot of Studio Editor parameters for reversible Undo/Redo state management.
 */
data class EditorParameterSnapshot(
    val mode: StudioMode = StudioMode.AUDIO_TRIM,
    val trimStartFraction: Float = 0.0f,
    val trimEndFraction: Float = 0.8f,
    val audioTrimFormat: String = "wav",
    val videoRotationDegrees: Int = 90,
    val videoCropPreset: String = "16:9 Cinema",
    val selectedProfileId: String = "WAV_PCM_16BIT",
    val selectedBitrateKbps: Int = 192
)

/**
 * Bounded Undo/Redo history stack for Studio Editor parameter adjustments.
 */
class EditorUndoRedoHistory(
    initial: EditorParameterSnapshot,
    private val maxDepth: Int = 32
) {
    private val undoStack = ArrayDeque<EditorParameterSnapshot>()
    private val redoStack = ArrayDeque<EditorParameterSnapshot>()

    var current: EditorParameterSnapshot = initial
        private set

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    fun recordChange(next: EditorParameterSnapshot) {
        if (next == current) return
        if (undoStack.size >= maxDepth) {
            undoStack.removeFirst()
        }
        undoStack.addLast(current)
        redoStack.clear()
        current = next
    }

    fun undo(): EditorParameterSnapshot? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current)
        current = undoStack.removeLast()
        return current
    }

    fun redo(): EditorParameterSnapshot? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(current)
        current = redoStack.removeLast()
        return current
    }
}

@Composable
fun StudioEditorOverlayScreen(
    allMedia: List<MediaEntity>,
    initialTargetMedia: MediaEntity?,
    conversionProfiles: List<ConversionProfile>,
    processingJobs: List<ProcessingJobEntity>,
    onClose: () -> Unit,
    onPreviewSegment: (MediaEntity, Long) -> Unit,
    onRunAudioTrim: (MediaEntity, Long, Long, String) -> Unit,
    onRunVideoTrimRotate: (MediaEntity, Long, Long, Int, String) -> Unit,
    onRunConversionOrExtraction: (MediaEntity, String, Int, Boolean) -> Unit,
    onCancelActiveJob: () -> Unit,
    onClearCompletedJobs: () -> Unit,
    onPlayExportedUri: (String) -> Unit
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current

    val initialMode = if (initialTargetMedia?.mediaType == MediaType.VIDEO) {
        StudioMode.VIDEO_EDIT
    } else {
        StudioMode.AUDIO_TRIM
    }
    val defaultProfileId = conversionProfiles.firstOrNull()?.id ?: "WAV_PCM_16BIT"

    var selectedMode by remember { mutableStateOf(initialMode) }
    var selectedSource by remember(initialTargetMedia, allMedia) {
        mutableStateOf(initialTargetMedia ?: allMedia.firstOrNull())
    }
    var sourcePickerExpanded by remember { mutableStateOf(false) }

    val sourceDurationMs = (selectedSource?.durationMs ?: 8_000L).coerceAtLeast(1000L)
    var trimStartFraction by remember(selectedSource) { mutableFloatStateOf(0.0f) }
    var trimEndFraction by remember(selectedSource) { mutableFloatStateOf(0.8f) }

    var audioTrimFormat by remember { mutableStateOf("wav") }
    var videoRotationDegrees by remember { mutableIntStateOf(90) }
    var videoCropPreset by remember { mutableStateOf("16:9 Cinema") }
    var selectedProfileId by remember { mutableStateOf(defaultProfileId) }
    var selectedBitrateKbps by remember { mutableIntStateOf(192) }

    val history = remember(selectedSource) {
        EditorUndoRedoHistory(
            EditorParameterSnapshot(
                mode = selectedMode,
                trimStartFraction = 0.0f,
                trimEndFraction = 0.8f,
                audioTrimFormat = audioTrimFormat,
                videoRotationDegrees = videoRotationDegrees,
                videoCropPreset = videoCropPreset,
                selectedProfileId = selectedProfileId,
                selectedBitrateKbps = selectedBitrateKbps
            )
        )
    }
    var historyVersion by remember { mutableIntStateOf(0) }

    fun currentSnapshot() = EditorParameterSnapshot(
        mode = selectedMode,
        trimStartFraction = trimStartFraction,
        trimEndFraction = trimEndFraction,
        audioTrimFormat = audioTrimFormat,
        videoRotationDegrees = videoRotationDegrees,
        videoCropPreset = videoCropPreset,
        selectedProfileId = selectedProfileId,
        selectedBitrateKbps = selectedBitrateKbps
    )

    fun applySnapshot(snap: EditorParameterSnapshot) {
        selectedMode = snap.mode
        trimStartFraction = snap.trimStartFraction
        trimEndFraction = snap.trimEndFraction
        audioTrimFormat = snap.audioTrimFormat
        videoRotationDegrees = snap.videoRotationDegrees
        videoCropPreset = snap.videoCropPreset
        selectedProfileId = snap.selectedProfileId
        selectedBitrateKbps = snap.selectedBitrateKbps
        historyVersion++
    }

    fun commitParameterChange(update: () -> Unit) {
        update()
        history.recordChange(currentSnapshot())
        historyVersion++
    }

    var inspectedExportJob by remember { mutableStateOf<ProcessingJobEntity?>(null) }

    LaunchedEffect(selectedMode, allMedia) {
        if (selectedMode == StudioMode.VIDEO_EDIT || selectedMode == StudioMode.AUDIO_EXTRACT) {
            val firstVideo = allMedia.firstOrNull { it.mediaType == MediaType.VIDEO }
            if (selectedSource?.mediaType != MediaType.VIDEO && firstVideo != null) {
                selectedSource = firstVideo
            }
        }
    }

    val startMs = (trimStartFraction * sourceDurationMs).toLong()
    val endMs = (trimEndFraction * sourceDurationMs).toLong().coerceAtLeast(startMs + 500L)

    inspectedExportJob?.let { job ->
        val outUri = job.outputUri.orEmpty()
        val parsedFile = runCatching {
            val uri = Uri.parse(outUri)
            if (uri.scheme == "file" && uri.path != null) File(uri.path!!) else null
        }.getOrNull()
        val sizeText = parsedFile?.takeIf { it.exists() }?.let {
            formatFileSize(it.length())
        } ?: if (job.outputSizeBytes > 0L) formatFileSize(job.outputSizeBytes) else "Verified Container"

        AlertDialog(
            onDismissRequest = { inspectedExportJob = null },
            title = { Text("Exported Media Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Operation: ${job.operationType.name} (${job.outputFormat})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Source Track: ${job.sourceTitle}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Trim Window: ${formatDurationMillis(job.startMs)} — ${formatDurationMillis(job.endMs)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "File Size: $sizeText",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Output URI: ${parsedFile?.absolutePath ?: outUri}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Pipeline Summary: ${job.statusMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(onClick = { inspectedExportJob = null }) {
                    Text("Close")
                }
            }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("studio_editor_overlay"),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompactLandscape = maxHeight < 480.dp
            val studioListState = rememberLazyListState()
            LazyColumn(
                state = studioListState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("studio_editor_list"),
                contentPadding = PaddingValues(
                    bottom = if (isCompactLandscape) 32.dp else 48.dp,
                    start = 16.dp,
                    end = 16.dp,
                    top = if (isCompactLandscape) 4.dp else 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(if (isCompactLandscape) 6.dp else 14.dp)
            ) {
                // Top Header with Undo / Redo / Close
                item {
                    val canUndo = remember(historyVersion, selectedSource) { history.canUndo }
                    val canRedo = remember(historyVersion, selectedSource) { history.canRedo }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PulsePlay Processing Studio",
                                style = if (isCompactLandscape) {
                                    MaterialTheme.typography.titleMedium
                                } else {
                                    MaterialTheme.typography.headlineMedium
                                },
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!isCompactLandscape) {
                                Text(
                                    text = "Real MediaExtractor • MediaCodec • IsoMp4Muxer • PCM WAVE Pipeline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    history.undo()?.let { applySnapshot(it) }
                                },
                                enabled = canUndo,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("studio_undo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo Parameter Change"
                                )
                            }
                            IconButton(
                                onClick = {
                                    history.redo()?.let { applySnapshot(it) }
                                },
                                enabled = canRedo,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("studio_redo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Redo Parameter Change"
                                )
                            }
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("close_studio_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close Studio")
                            }
                        }
                    }
                }

                // Studio Mode Selector
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(StudioMode.entries) { mode ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = {
                                    commitParameterChange { selectedMode = mode }
                                },
                                label = { Text(mode.label, maxLines = 1) },
                                modifier = Modifier.testTag("studio_mode_${mode.name.lowercase()}")
                            )
                        }
                    }
                }

                // Source Media Selector Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(if (isCompactLandscape) 12.dp else 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(if (isCompactLandscape) 8.dp else 14.dp)) {
                            if (!isCompactLandscape) {
                                Text(
                                    text = "1. Source Media Selection",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedSource?.title ?: "No Media Available",
                                        style = if (isCompactLandscape) {
                                            MaterialTheme.typography.titleSmall
                                        } else {
                                            MaterialTheme.typography.titleMedium
                                        },
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = selectedSource?.let {
                                            "${it.mimeType} • Duration: ${formatDurationMillis(it.durationMs)}"
                                        } ?: "Generate samples on Home first",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row {
                                    OutlinedButton(
                                        onClick = { sourcePickerExpanded = true },
                                        contentPadding = if (isCompactLandscape) {
                                            PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        } else {
                                            PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                        },
                                        modifier = Modifier.testTag("change_studio_source_button")
                                    ) {
                                        Text("Switch Track", maxLines = 1)
                                    }
                                    DropdownMenu(
                                        expanded = sourcePickerExpanded,
                                        onDismissRequest = { sourcePickerExpanded = false }
                                    ) {
                                        allMedia.forEach { item ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text("${item.title} (${item.mediaType.name})")
                                                },
                                                onClick = {
                                                    selectedSource = item
                                                    sourcePickerExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Operation Configuration Card
                item {
                    val src = selectedSource
                    if (src != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (isCompactLandscape) 14.dp else 18.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(if (isCompactLandscape) 10.dp else 16.dp),
                                verticalArrangement = Arrangement.spacedBy(if (isCompactLandscape) 6.dp else 12.dp)
                            ) {
                                when (selectedMode) {
                                    StudioMode.AUDIO_TRIM -> {
                                        Text(
                                            text = "2. Audio Trim Range & Export Format",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (audioTrimFormat == "wav") {
                                                "Pipeline Mode: Lossless 16-bit PCM WAVE Slicing / Decode (Sample-Accurate Timestamps)"
                                            } else {
                                                "Pipeline Mode: Full Codec Re-encode (MediaCodec AAC-LC + ISO-BMFF M4A Muxer)"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = "Selected Range: ${formatDurationMillis(startMs)} — ${formatDurationMillis(endMs)} (Duration: ${formatDurationMillis(endMs - startMs)})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        RangeSlider(
                                            value = trimStartFraction..trimEndFraction,
                                            onValueChange = { range ->
                                                trimStartFraction = range.start.coerceIn(0f, 0.9f)
                                                trimEndFraction = range.endInclusive.coerceIn(trimStartFraction + 0.08f, 1f)
                                            },
                                            onValueChangeFinished = {
                                                history.recordChange(currentSnapshot())
                                                historyVersion++
                                            },
                                            valueRange = 0f..1f,
                                            modifier = Modifier.testTag("audio_trim_range_slider")
                                        )
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            item {
                                                FilterChip(
                                                    selected = audioTrimFormat == "wav",
                                                    onClick = {
                                                        commitParameterChange { audioTrimFormat = "wav" }
                                                    },
                                                    label = { Text("Export PCM .WAV (Lossless)", maxLines = 1) },
                                                    modifier = Modifier.testTag("audio_trim_format_wav")
                                                )
                                            }
                                            item {
                                                FilterChip(
                                                    selected = audioTrimFormat == "m4a",
                                                    onClick = {
                                                        commitParameterChange { audioTrimFormat = "m4a" }
                                                    },
                                                    label = { Text("Export AAC .M4A (Transcode)", maxLines = 1) },
                                                    modifier = Modifier.testTag("audio_trim_format_m4a")
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { onPreviewSegment(src, startMs) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("preview_trim_segment_button")
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Preview Segment", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Button(
                                                onClick = {
                                                    onRunAudioTrim(src, startMs, endMs, audioTrimFormat)
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("execute_audio_trim_button")
                                            ) {
                                                Icon(Icons.Default.ContentCut, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Trim & Export Audio", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }

                                    StudioMode.VIDEO_EDIT -> {
                                        Text(
                                            text = "2. Video Trim, Orientation & Aspect Metadata",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Pipeline Mode: Lossless Keyframe-Aligned Stream Remuxing (SEEK_TO_CLOSEST_SYNC + ISO-BMFF tkhd Matrix • Zero Quality Loss)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = "Trim Window: ${formatDurationMillis(startMs)} — ${formatDurationMillis(endMs)} (Duration: ${formatDurationMillis(endMs - startMs)})",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        RangeSlider(
                                            value = trimStartFraction..trimEndFraction,
                                            onValueChange = { range ->
                                                trimStartFraction = range.start.coerceIn(0f, 0.9f)
                                                trimEndFraction = range.endInclusive.coerceIn(trimStartFraction + 0.08f, 1f)
                                            },
                                            onValueChangeFinished = {
                                                history.recordChange(currentSnapshot())
                                                historyVersion++
                                            },
                                            valueRange = 0f..1f,
                                            modifier = Modifier.testTag("video_trim_range_slider")
                                        )
                                        Text(
                                            text = "Orientation Matrix (ISO-BMFF tkhd Rotation Hint):",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(listOf(0, 90, 180, 270)) { deg ->
                                                FilterChip(
                                                    selected = videoRotationDegrees == deg,
                                                    onClick = {
                                                        commitParameterChange { videoRotationDegrees = deg }
                                                    },
                                                    label = { Text("Rotate ${deg}°") },
                                                    modifier = Modifier.testTag("video_rotate_chip_$deg")
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Crop / Framing Preset:",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(listOf("ORIGINAL", "16:9 Cinema", "1:1 Square", "4:3 Classic")) { crop ->
                                                FilterChip(
                                                    selected = videoCropPreset == crop,
                                                    onClick = {
                                                        commitParameterChange { videoCropPreset = crop }
                                                    },
                                                    label = { Text(crop) }
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { onPreviewSegment(src, startMs) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("preview_video_trim_segment_button")
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Preview Trim", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Button(
                                                onClick = {
                                                    onRunVideoTrimRotate(
                                                        src,
                                                        startMs,
                                                        endMs,
                                                        videoRotationDegrees,
                                                        videoCropPreset
                                                    )
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("execute_video_edit_button")
                                            ) {
                                                Icon(Icons.Default.Movie, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Export Edited MP4", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }

                                    StudioMode.AUDIO_EXTRACT -> {
                                        Text(
                                            text = "2. Extract Audio Track from Video",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Pipeline Mode: Lossless AAC Demux/Remux into M4A when source audio is AAC, or full MediaCodec decode to 16-bit PCM WAV while keeping the original video untouched.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val audioProfiles = conversionProfiles.filter { it.videoCodecMime == null }
                                        audioProfiles.forEach { prof ->
                                            FilterChip(
                                                selected = selectedProfileId == prof.id,
                                                onClick = {
                                                    commitParameterChange { selectedProfileId = prof.id }
                                                },
                                                label = { Text(prof.title) }
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                onRunConversionOrExtraction(
                                                    src,
                                                    selectedProfileId,
                                                    selectedBitrateKbps,
                                                    true
                                                )
                                            },
                                            modifier = Modifier.testTag("execute_audio_extract_button")
                                        ) {
                                            Icon(Icons.Default.AudioFile, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Extract Audio Track")
                                        }
                                    }

                                    StudioMode.FORMAT_CONVERT -> {
                                        Text(
                                            text = "2. Capability-Verified Format Conversion Registry",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Real transcoding/remuxing via Android MediaCodec & IsoMp4Muxer. Never renames file extensions.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        conversionProfiles.forEach { prof ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        commitParameterChange { selectedProfileId = prof.id }
                                                    }
                                                    .testTag("conversion_profile_card_${prof.id}"),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (selectedProfileId == prof.id) {
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                    }
                                                )
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = prof.title,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = if (prof.isHardwareSupportedOnDevice) "SUPPORTED" else "UNSUPPORTED",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (prof.isHardwareSupportedOnDevice) {
                                                                MaterialTheme.colorScheme.secondary
                                                            } else {
                                                                MaterialTheme.colorScheme.error
                                                            }
                                                        )
                                                    }
                                                    Text(
                                                        text = "Container: ${prof.outputContainer} • Codec: ${prof.audioCodecMime}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = prof.compatibilityNotes,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = "Target Encoder Bitrate (kbps):",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(listOf(96, 128, 192, 256)) { kbps ->
                                                FilterChip(
                                                    selected = selectedBitrateKbps == kbps,
                                                    onClick = {
                                                        commitParameterChange { selectedBitrateKbps = kbps }
                                                    },
                                                    label = { Text("$kbps kbps") },
                                                    modifier = Modifier.testTag("studio_bitrate_chip_$kbps")
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                onRunConversionOrExtraction(
                                                    src,
                                                    selectedProfileId,
                                                    selectedBitrateKbps,
                                                    false
                                                )
                                            },
                                            modifier = Modifier.testTag("execute_conversion_button")
                                        ) {
                                            Icon(Icons.Default.SwapHoriz, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Start Verified Conversion")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Processing Jobs Queue & Recovery History (Phase 11 & Phase 16)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Processing Jobs & Exports (${processingJobs.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (processingJobs.any { it.status == JobStatus.RUNNING }) {
                            OutlinedButton(
                                onClick = onCancelActiveJob,
                                modifier = Modifier.testTag("cancel_processing_job_button")
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cancel Active")
                            }
                        } else if (processingJobs.isNotEmpty()) {
                            TextButton(onClick = onClearCompletedJobs) {
                                Text("Clear Log")
                            }
                        }
                    }
                }

                items(processingJobs, key = { it.jobId }) { job ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("processing_job_card_${job.jobId}"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (job.status) {
                                            JobStatus.COMPLETED -> Icons.Default.CheckCircle
                                            JobStatus.FAILED, JobStatus.CANCELLED -> Icons.Default.ErrorOutline
                                            else -> Icons.Default.ContentCut
                                        },
                                        contentDescription = null,
                                        tint = when (job.status) {
                                            JobStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                                            JobStatus.FAILED -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${job.operationType.name} • ${job.outputFormat}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = job.status.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Source: ${job.sourceTitle}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = job.statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (job.status == JobStatus.RUNNING) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { (job.progressPercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (job.status == JobStatus.COMPLETED && job.outputUri != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onPlayExportedUri(job.outputUri) },
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .testTag("play_exported_job_button_${job.jobId}")
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Play Export", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val matchedEntity = allMedia.find { it.uriString == job.outputUri }
                                            if (matchedEntity != null) {
                                                MediaFileActionsHelper.shareMediaFile(context, matchedEntity)
                                            } else {
                                                val fallbackEntity = MediaEntity(
                                                    uriString = job.outputUri,
                                                    mediaStoreId = null,
                                                    title = "${job.sourceTitle}_${job.outputFormat}",
                                                    artist = "PulsePlay Studio Export",
                                                    album = "Studio Exports",
                                                    folderName = "Studio Exports",
                                                    durationMs = (job.endMs - job.startMs).coerceAtLeast(1000L),
                                                    sizeBytes = 0L,
                                                    mimeType = if (job.outputFormat.equals("MP4", true)) "video/mp4" else "audio/${job.outputFormat.lowercase()}",
                                                    mediaType = if (job.outputFormat.equals("MP4", true)) MediaType.VIDEO else MediaType.AUDIO,
                                                    width = 0,
                                                    height = 0,
                                                    dateAddedEpochSec = job.createdAtMs / 1000L,
                                                    isAvailable = true,
                                                    isGeneratedSample = false
                                                )
                                                MediaFileActionsHelper.shareMediaFile(context, fallbackEntity)
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("share_exported_job_button_${job.jobId}")
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Share Export")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Share", maxLines = 1)
                                    }
                                    IconButton(
                                        onClick = { inspectedExportJob = job },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("info_exported_job_button_${job.jobId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Exported File Details"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FastScrollbar(
                listState = studioListState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(
                    top = if (isCompactLandscape) 4.dp else 12.dp,
                    bottom = if (isCompactLandscape) 32.dp else 48.dp
                )
            )
        }
    }
}
