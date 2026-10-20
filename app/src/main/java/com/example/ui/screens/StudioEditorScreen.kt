package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.JobStatus
import com.example.data.local.MediaEntity
import com.example.data.local.MediaType
import com.example.data.local.ProcessingJobEntity
import com.example.media.ConversionProfile
import com.example.ui.components.formatDurationMillis

enum class StudioMode(val label: String) {
    AUDIO_TRIM("Audio Trim"),
    VIDEO_EDIT("Video Trim & Rotate"),
    AUDIO_EXTRACT("Extract Audio"),
    FORMAT_CONVERT("Format Converter")
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

    var selectedMode by remember {
        mutableStateOf(
            if (initialTargetMedia?.mediaType == MediaType.VIDEO) {
                StudioMode.VIDEO_EDIT
            } else {
                StudioMode.AUDIO_TRIM
            }
        )
    }

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
    var selectedProfileId by remember {
        mutableStateOf(conversionProfiles.firstOrNull()?.id ?: "WAV_PCM_16BIT")
    }
    var selectedBitrateKbps by remember { mutableIntStateOf(192) }

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

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("studio_editor_overlay"),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp, start = 16.dp, end = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PulsePlay Processing Studio",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Real MediaExtractor • MediaCodec • MediaMuxer • PCM WAVE Pipeline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

            // Studio Mode Selector
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StudioMode.entries) { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("studio_mode_${mode.name.lowercase()}")
                        )
                    }
                }
            }

            // Source Media Selector Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "1. Source Media Selection",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedSource?.title ?: "No Media Available",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = selectedSource?.let {
                                        "${it.mimeType} • Duration: ${formatDurationMillis(it.durationMs)}"
                                    } ?: "Generate samples on Home first",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                OutlinedButton(
                                    onClick = { sourcePickerExpanded = true },
                                    modifier = Modifier.testTag("change_studio_source_button")
                                ) {
                                    Text("Switch Track")
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
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (selectedMode) {
                                StudioMode.AUDIO_TRIM -> {
                                    Text(
                                        text = "2. Audio Trim Range & Export Format",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
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
                                        valueRange = 0f..1f,
                                        modifier = Modifier.testTag("audio_trim_range_slider")
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = audioTrimFormat == "wav",
                                            onClick = { audioTrimFormat = "wav" },
                                            label = { Text("Export PCM .WAV (Lossless)") }
                                        )
                                        FilterChip(
                                            selected = audioTrimFormat == "m4a",
                                            onClick = { audioTrimFormat = "m4a" },
                                            label = { Text("Export AAC .M4A") }
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(
                                            onClick = { onPreviewSegment(src, startMs) },
                                            modifier = Modifier.testTag("preview_trim_segment_button")
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Preview Segment")
                                        }
                                        Button(
                                            onClick = {
                                                onRunAudioTrim(src, startMs, endMs, audioTrimFormat)
                                            },
                                            modifier = Modifier.testTag("execute_audio_trim_button")
                                        ) {
                                            Icon(Icons.Default.ContentCut, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Trim & Export Audio")
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
                                        text = "Trim Window: ${formatDurationMillis(startMs)} — ${formatDurationMillis(endMs)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    RangeSlider(
                                        value = trimStartFraction..trimEndFraction,
                                        onValueChange = { range ->
                                            trimStartFraction = range.start.coerceIn(0f, 0.9f)
                                            trimEndFraction = range.endInclusive.coerceIn(trimStartFraction + 0.08f, 1f)
                                        },
                                        valueRange = 0f..1f
                                    )
                                    Text(
                                        text = "Orientation Matrix (MediaMuxer Hint):",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(listOf(0, 90, 180, 270)) { deg ->
                                            FilterChip(
                                                selected = videoRotationDegrees == deg,
                                                onClick = { videoRotationDegrees = deg },
                                                label = { Text("Rotate ${deg}°") }
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
                                                onClick = { videoCropPreset = crop },
                                                label = { Text(crop) }
                                            )
                                        }
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
                                        modifier = Modifier.testTag("execute_video_edit_button")
                                    ) {
                                        Icon(Icons.Default.Movie, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Export Edited MP4 Video")
                                    }
                                }

                                StudioMode.AUDIO_EXTRACT -> {
                                    Text(
                                        text = "2. Extract Audio Track from Video",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Remuxes AAC without re-encoding when compatible, or decodes to 16-bit PCM WAV while preserving the original video untouched.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val audioProfiles = conversionProfiles.filter { it.videoCodecMime == null }
                                    audioProfiles.forEach { prof ->
                                        FilterChip(
                                            selected = selectedProfileId == prof.id,
                                            onClick = { selectedProfileId = prof.id },
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
                                        text = "Real transcoding/remuxing via Android MediaCodec & MediaMuxer. Never renames file extensions.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    conversionProfiles.forEach { prof ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedProfileId = prof.id }
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
                                                onClick = { selectedBitrateKbps = kbps },
                                                label = { Text("$kbps kbps") }
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
                            OutlinedButton(
                                onClick = { onPlayExportedUri(job.outputUri) },
                                modifier = Modifier.testTag("play_exported_job_button_${job.jobId}")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play Verified Export")
                            }
                        }
                    }
                }
            }
        }
    }
}
