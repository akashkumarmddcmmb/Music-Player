package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.Color
import com.example.data.local.PlaylistWithCount
import com.example.data.preferences.UserPreferencesState
import com.example.data.preferences.VideoOrientationPreference
import com.example.viewmodel.AudioTrackOption
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.MediaEntity
import com.example.data.local.MediaType
import com.example.data.preferences.HighResVideoPlaybackMode
import com.example.media.DetailedMediaFileInfo
import com.example.media.MediaFileActionsHelper
import com.example.media.Resilient4KCodecEngine
import com.example.media.VideoCompatibilityAction
import com.example.media.VideoStreamCapabilityEvaluation
import com.example.media.rememberMediaArtworkBitmap
import com.example.viewmodel.PlaybackUiState

fun formatDurationMillis(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) "%.1f MB".format(mb) else "%.0f KB".format(kb)
}

@Composable
fun MediaItemCard(
    media: MediaEntity,
    isCurrentlyPlaying: Boolean,
    isFavorite: Boolean,
    onPlayClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onOpenInStudioClick: () -> Unit,
    modifier: Modifier = Modifier,
    onShareClick: () -> Unit = {},
    onSetRingtoneClick: () -> Unit = {},
    onFileInfoClick: () -> Unit = {}
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    val safeTagSuffix = remember(media.title) {
        media.title.take(12).replace(" ", "_")
    }
    val isSupportedMedia = remember(media) {
        MediaFileActionsHelper.isSupportedMediaFile(media)
    }
    val canSetAsRingtone = remember(media) {
        MediaFileActionsHelper.isSupportedAudioForRingtone(media)
    }
    val canEditInStudio = remember(media) {
        MediaFileActionsHelper.isSupportedForStudioEditing(media)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("media_item_card_$safeTagSuffix")
            .clickable(onClick = onPlayClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyPlaying) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Original Studio Media Badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (media.mediaType == MediaType.VIDEO) {
                                listOf(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            } else {
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        !media.isAvailable -> Icons.Default.WarningAmber
                        isCurrentlyPlaying -> Icons.Default.GraphicEq
                        media.mediaType == MediaType.VIDEO -> Icons.Default.Movie
                        else -> Icons.Default.AudioFile
                    },
                    contentDescription = if (media.mediaType == MediaType.VIDEO) "Video item" else "Audio item",
                    tint = if (media.mediaType == MediaType.VIDEO) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            val cleanTitle = remember(media.title) {
                media.title.replace(Regex("\\s*\\(\\d+x\\d+[^)]*\\)"), "").trim()
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleanTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDurationMillis(media.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = formatFileSize(media.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (media.width > 0 && media.height > 0) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Text(
                            text = "${media.width}x${media.height}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Direct Favorite Action Button (48dp touch target)
            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("media_item_favorite_$safeTagSuffix")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove ${cleanTitle} from favorites" else "Add ${cleanTitle} to favorites",
                    tint = if (isFavorite) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            // File-type-aware Three-Dot Overflow Menu
            Box {
                IconButton(
                    onClick = { overflowExpanded = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("media_item_overflow_$safeTagSuffix")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More actions for $cleanTitle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false },
                    modifier = Modifier.testTag("media_item_dropdown_menu_$safeTagSuffix")
                ) {
                    if (isSupportedMedia) {
                        DropdownMenuItem(
                            text = { Text("Play Now", maxLines = 1) },
                            leadingIcon = {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            onClick = {
                                overflowExpanded = false
                                onPlayClick()
                            },
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("overflow_play_action")
                        )

                        DropdownMenuItem(
                            text = { Text("Share", maxLines = 1) },
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null)
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            onClick = {
                                overflowExpanded = false
                                onShareClick()
                            },
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("overflow_share_action")
                        )
                    }

                    if (canSetAsRingtone) {
                        DropdownMenuItem(
                            text = { Text("Set as Ringtone", maxLines = 1) },
                            leadingIcon = {
                                Icon(Icons.Default.RingVolume, contentDescription = null)
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            onClick = {
                                overflowExpanded = false
                                onSetRingtoneClick()
                            },
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("overflow_ringtone_action")
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("File Information", maxLines = 1) },
                        leadingIcon = {
                            Icon(Icons.Default.Info, contentDescription = null)
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        onClick = {
                            overflowExpanded = false
                            onFileInfoClick()
                        },
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("overflow_file_info_action")
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
                            overflowExpanded = false
                            onFavoriteToggle()
                        },
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("overflow_favorite_action")
                    )

                    if (isSupportedMedia) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", maxLines = 1) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            onClick = {
                                overflowExpanded = false
                                onAddToPlaylistClick()
                            },
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("overflow_playlist_action")
                        )
                    }

                    if (canEditInStudio) {
                        DropdownMenuItem(
                            text = { Text("Edit in Media Studio", maxLines = 1) },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCut, contentDescription = null)
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            onClick = {
                                overflowExpanded = false
                                onOpenInStudioClick()
                            },
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("overflow_studio_action")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaFileInformationDialog(
    info: DetailedMediaFileInfo,
    onDismiss: () -> Unit,
    onCopyLocation: (String) -> Unit
) {
    var copiedLocationFeedback by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("file_info_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "File Information",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("file_info_scroll_column"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FileInfoMetadataRow(
                    label = "File Name",
                    value = info.fullFileName,
                    testTag = "file_info_row_name"
                )
                FileInfoMetadataRow(
                    label = "Extension",
                    value = info.fileExtension,
                    testTag = "file_info_row_extension"
                )
                FileInfoMetadataRow(
                    label = "File Type / MIME",
                    value = "${info.fileTypeLabel} (${info.mimeType})",
                    testTag = "file_info_row_mime"
                )
                FileInfoMetadataRow(
                    label = "File Size",
                    value = info.formattedFileSize,
                    testTag = "file_info_row_size"
                )
                FileInfoMetadataRow(
                    label = "Duration",
                    value = info.formattedDuration,
                    testTag = "file_info_row_duration"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                if (info.mediaType == MediaType.VIDEO) {
                    FileInfoMetadataRow(
                        label = "Resolution",
                        value = info.resolution,
                        testTag = "file_info_row_resolution"
                    )
                    FileInfoMetadataRow(
                        label = "Frame Rate",
                        value = info.frameRate,
                        testTag = "file_info_row_framerate"
                    )
                    FileInfoMetadataRow(
                        label = "Video Codec",
                        value = info.videoCodec,
                        testTag = "file_info_row_video_codec"
                    )
                }

                FileInfoMetadataRow(
                    label = "Audio Codec",
                    value = info.audioCodec,
                    testTag = "file_info_row_audio_codec"
                )
                FileInfoMetadataRow(
                    label = "Bitrate",
                    value = info.bitrate,
                    testTag = "file_info_row_bitrate"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                FileInfoMetadataRow(
                    label = "Artist",
                    value = info.artist,
                    testTag = "file_info_row_artist"
                )
                FileInfoMetadataRow(
                    label = "Album",
                    value = info.album,
                    testTag = "file_info_row_album"
                )
                FileInfoMetadataRow(
                    label = "Embedded Metadata",
                    value = info.embeddedMetadata,
                    testTag = "file_info_row_embedded"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                FileInfoMetadataRow(
                    label = "Folder",
                    value = info.folderName,
                    testTag = "file_info_row_folder"
                )
                FileInfoMetadataRow(
                    label = "Date Added",
                    value = info.dateAddedFormatted,
                    testTag = "file_info_row_date_added"
                )
                FileInfoMetadataRow(
                    label = "Last Modified",
                    value = info.lastModifiedFormatted,
                    testTag = "file_info_row_last_modified"
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("file_info_row_location")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "File Location / URI",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(
                                onClick = {
                                    onCopyLocation(info.locationOrUri)
                                    copiedLocationFeedback = true
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .heightIn(min = 34.dp)
                                    .testTag("file_info_copy_location_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy file location",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (copiedLocationFeedback) "Copied!" else "Copy Path",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }
                        Text(
                            text = info.locationOrUri,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("file_info_close_button")
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun FileInfoMetadataRow(
    label: String,
    value: String,
    testTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun RingtonePermissionDialog(
    media: MediaEntity,
    onOpenSettings: () -> Unit,
    onContinueSetRingtone: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("ringtone_permission_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RingVolume,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Ringtone Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Android requires the 'Modify system settings' permission to set '${media.title}' as your system ringtone.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "1. Tap 'Open System Settings' and enable the toggle for PulsePlay.\n2. Return to PulsePlay or tap 'Continue & Set Ringtone' below to apply your ringtone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("ringtone_open_settings_button")
                ) {
                    Text("Open System Settings", maxLines = 1)
                }
                Button(
                    onClick = onContinueSetRingtone,
                    modifier = Modifier.testTag("ringtone_continue_button")
                ) {
                    Text("Continue & Set Ringtone", maxLines = 1)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("ringtone_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Centered, prominent Sleep Timer Dialog styled consistently with RingtonePermissionDialog.
 * Contains active countdown, preset duration chips, End of Track toggle, remaining time badge,
 * and accessible action buttons.
 */
@Composable
fun SleepTimerDialog(
    playbackState: PlaybackUiState,
    onSetSleepTimerMinutes: (Int) -> Unit,
    onSetSleepTimerEndOfTrack: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val presetMinutes = listOf(0, 5, 10, 15, 30, 45, 60)
    val statusLabel = when {
        playbackState.sleepTimerStopAtEndOfTrack -> "Stops at End of Track"
        playbackState.sleepTimerRemainingMs > 0L -> "Remaining: ${formatDurationMillis(playbackState.sleepTimerRemainingMs)}"
        else -> "Off"
    }
    val isTimerActive = playbackState.isSleepTimerActive

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = 520.dp)
            .testTag("sleep_timer_panel"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = if (isTimerActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Sleep Timer (Automatic Stop)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sleep_timer_dialog")
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTimerActive) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Status",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isTimerActive) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.testTag("sleep_timer_status_text")
                            )
                        }
                        if (isTimerActive && playbackState.sleepTimerRemainingMs > 0L) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Automatically pauses music or video playback when the countdown finishes or the current track ends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Set Timer Duration",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
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
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isTimerActive) {
                    OutlinedButton(
                        onClick = { onSetSleepTimerMinutes(0) },
                        modifier = Modifier.testTag("sleep_timer_cancel_button")
                    ) {
                        Text("Turn Off")
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("sleep_timer_done_button")
                ) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("sleep_timer_close_button")
            ) {
                Text("Close")
            }
        }
    )
}

/**
 * Centered, prominent Playback Speed Dialog styled consistently with RingtonePermissionDialog.
 * Contains quick preset chips, a fine-grain slider, and a clear speed readout.
 */
@Composable
fun PlaybackSpeedDialog(
    playbackState: PlaybackUiState,
    onSetPlaybackSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)
    val speedFormatted = "%.2f".format(playbackState.playbackSpeed).trimEnd('0').trimEnd('.')

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = 500.dp)
            .testTag("playback_speed_panel"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Playback Speed (${speedFormatted}x)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playback_speed_dialog")
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Adjust playback speed in real time without altering audio pitch.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Speed Presets",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(speeds) { spd ->
                        FilterChip(
                            selected = abs(playbackState.playbackSpeed - spd) < 0.05f,
                            onClick = { onSetPlaybackSpeed(spd) },
                            label = { Text("${spd}x", maxLines = 1) },
                            modifier = Modifier.testTag("speed_chip_$spd")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Fine Tuning",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${speedFormatted}x",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
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
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (abs(playbackState.playbackSpeed - 1.0f) >= 0.05f) {
                    OutlinedButton(
                        onClick = { onSetPlaybackSpeed(1.0f) },
                        modifier = Modifier.testTag("speed_reset_button")
                    ) {
                        Text("Reset (1.0x)")
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("speed_done_button")
                ) {
                    Text("Done")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("speed_close_button")
            ) {
                Text("Close")
            }
        }
    )
}

/**
 * Centered, prominent Subtitle and Lyrics Dialog styled consistently with RingtonePermissionDialog.
 * Contains display toggle, format details, cue list with tap-to-seek, font scale slider,
 * timing offset adjustments, and local file picker.
 */
@Composable
fun SubtitleSettingsDialog(
    playbackState: PlaybackUiState,
    userPreferences: UserPreferencesState,
    onToggleSubtitlesEnabled: (Boolean) -> Unit,
    onSetSubtitleFontScale: (Float) -> Unit,
    onSetSubtitleOffsetMs: (Long) -> Unit,
    onPickSubtitleFileClick: () -> Unit,
    onSeekToCue: (Long) -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = 520.dp)
            .testTag("subtitle_settings_panel"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Lyrics & Subtitles",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Display Subtitles / Lyrics",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Synchronized display over playback",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = userPreferences.subtitlesEnabledByDefault,
                        onCheckedChange = onToggleSubtitlesEnabled,
                        modifier = Modifier.testTag("subtitle_enable_switch")
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = playbackState.loadedSubtitleName?.let {
                                "Loaded: $it (${playbackState.loadedSubtitleFormat?.name ?: "Unknown"}) • ${playbackState.loadedSubtitleCues.size} synchronized cues"
                            } ?: "No SRT/WebVTT lyrics or subtitle track loaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (playbackState.subtitleError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = playbackState.subtitleError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (playbackState.loadedSubtitleCues.isNotEmpty()) {
                    Text(
                        text = "Synchronized Lyrics / Cues (Tap line to seek):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("lyrics_cue_item_$idx"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatDurationMillis(cue.startMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.width(52.dp)
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

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Sync Timing Offset: ${userPreferences.subtitleSyncOffsetMs}ms",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subtitle_sync_offset_row"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSetSubtitleOffsetMs(userPreferences.subtitleSyncOffsetMs - 250L) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) { Text("-250ms", maxLines = 1) }
                        OutlinedButton(
                            onClick = { onSetSubtitleOffsetMs(0L) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) { Text("0ms", maxLines = 1) }
                        OutlinedButton(
                            onClick = { onSetSubtitleOffsetMs(userPreferences.subtitleSyncOffsetMs + 250L) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("subtitle_offset_plus_250_button"),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) { Text("+250ms", maxLines = 1) }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Subtitle Font Scale",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${"%.2f".format(userPreferences.subtitleFontScale)}x",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Slider(
                        value = userPreferences.subtitleFontScale.coerceIn(0.75f, 1.75f),
                        onValueChange = onSetSubtitleFontScale,
                        valueRange = 0.75f..1.75f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subtitle_font_scale_slider")
                    )
                }

                OutlinedButton(
                    onClick = onPickSubtitleFileClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("load_external_subtitle_button")
                        .testTag("subtitle_load_file_button")
                ) {
                    Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Load .SRT / .VTT File (SAF)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("subtitle_close_button")
            ) {
                Text("Done")
            }
        }
    )
}

/**
 * Centered, prominent Video Settings Dialog styled consistently with RingtonePermissionDialog.
 * Contains screen rotation mode chips, frame rotation, aspect ratio cycling, playback speed,
 * keep awake switch, gesture controls switch, and lock controls button.
 */
@Composable
fun VideoSettingsDialog(
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
    onOpenEqualizer: () -> Unit,
    onSelectHighResMode: (HighResVideoPlaybackMode) -> Unit = {},
    onOpenCompatibilityOptions: () -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = 520.dp)
            .testTag("video_settings_panel"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Video Settings & Playback",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Screen Rotation Mode",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
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

                Text(
                    text = "Frame & Aspect Ratio",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
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

                Text(
                    text = "Playback Speed (${playbackState.playbackSpeed}x)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Brightness & Volume Gestures", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Vertical swipe gestures on left/right screen edges",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = userPreferences.videoGestureControlsEnabled,
                        onCheckedChange = onToggleVideoGestureControls,
                        modifier = Modifier.testTag("video_gesture_controls_switch")
                    )
                }

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
                        onClick = {
                            onDismiss()
                            onOpenEqualizer()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Equalizer, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Audio EQ")
                    }
                }

                Text(
                    text = "High-Resolution Video Playback Mode",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(HighResVideoPlaybackMode.entries) { mode ->
                        val avail = playbackState.playbackModeAvailabilities.find { it.mode == mode }
                        val isEnabled = avail?.isAvailable ?: true
                        FilterChip(
                            selected = userPreferences.highResVideoPlaybackMode == mode,
                            onClick = {
                                if (isEnabled) onSelectHighResMode(mode)
                            },
                            enabled = isEnabled,
                            label = { Text(mode.label, maxLines = 1) },
                            modifier = Modifier.testTag("video_dialog_high_res_${mode.name.lowercase()}")
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenCompatibilityOptions()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_video_compatibility_options_button")
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("4K/8K Decoder & Compatibility Options")
                }

                OutlinedButton(
                    onClick = {
                        onToggleControlsLock(!playbackState.isVideoControlsLocked)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (playbackState.isVideoControlsLocked) "Unlock Video Controls" else "Lock Video Touch Controls")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("video_settings_close_button")
            ) {
                Text("Done")
            }
        }
    )
}

/**
 * In-Player Compatibility Options Panel / Dialog for 4K, 8K, or unsupported-codec videos.
 * Provides clear, honest hardware/software capability status and the 6 required user actions:
 * - Auto playback
 * - Compatible decoder आज़माएँ
 * - Software fallback (if available & safe on this device)
 * - Supported lower-resolution playback (if available; honestly labeled Downscaled)
 * - Video information
 * - Cancel
 */
@Composable
fun VideoCompatibilityOptionsDialog(
    media: MediaEntity,
    playbackState: PlaybackUiState,
    evaluation: VideoStreamCapabilityEvaluation?,
    onSelectAction: (VideoCompatibilityAction) -> Unit,
    onDismiss: () -> Unit
) {
    val width = evaluation?.width ?: media.width.takeIf { it > 0 } ?: playbackState.decodedVideoWidth
    val height = evaluation?.height ?: media.height.takeIf { it > 0 } ?: playbackState.decodedVideoHeight
    val badge = Resilient4KCodecEngine.formatVerifiedResolutionBadge(
        width = width,
        height = height,
        isDownscaled = playbackState.isDownscaledPlayback
    ) ?: "${width}x${height}"
    val isSoftwareSafe = evaluation?.isSoftwareFallbackSafe ?: false
    val isLowerResAvailable = evaluation?.isLowerResolutionOptionAvailable ?: true

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = 520.dp)
            .testTag("video_compatibility_options_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "High-Resolution Compatibility Options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${media.title} • $badge",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = evaluation?.honestSummaryMessage
                                ?: playbackState.decoderStatusLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!evaluation?.warningMessage.isNullOrBlank()) {
                            Text(
                                text = evaluation?.warningMessage.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("video_compatibility_warning_text")
                            )
                        }
                        if (!playbackState.playbackError.isNullOrBlank()) {
                            Text(
                                text = playbackState.playbackError,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = "Note: Compatibility changes apply ONLY to this video and automatically reset to the standard hardware path when finished.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 1. Auto playback
                CompatibilityOptionButton(
                    action = VideoCompatibilityAction.AUTO_PLAYBACK,
                    enabled = true,
                    unavailableReason = null,
                    onClick = { onSelectAction(VideoCompatibilityAction.AUTO_PLAYBACK) }
                )

                // 2. Compatible decoder आज़माएँ
                CompatibilityOptionButton(
                    action = VideoCompatibilityAction.TRY_COMPATIBLE_DECODER,
                    enabled = true,
                    unavailableReason = null,
                    onClick = { onSelectAction(VideoCompatibilityAction.TRY_COMPATIBLE_DECODER) }
                )

                // 3. Software fallback (if available & safe on device)
                CompatibilityOptionButton(
                    action = VideoCompatibilityAction.SOFTWARE_FALLBACK,
                    enabled = isSoftwareSafe,
                    unavailableReason = evaluation?.softwareUnavailableReason,
                    onClick = { onSelectAction(VideoCompatibilityAction.SOFTWARE_FALLBACK) }
                )

                // 4. Supported lower-resolution playback (if available)
                CompatibilityOptionButton(
                    action = VideoCompatibilityAction.LOWER_RESOLUTION_PLAYBACK,
                    enabled = isLowerResAvailable,
                    unavailableReason = if (!isLowerResAvailable) "Not applicable for standard resolution" else null,
                    onClick = { onSelectAction(VideoCompatibilityAction.LOWER_RESOLUTION_PLAYBACK) }
                )

                // 5. Video information
                CompatibilityOptionButton(
                    action = VideoCompatibilityAction.VIDEO_INFORMATION,
                    enabled = true,
                    unavailableReason = null,
                    onClick = { onSelectAction(VideoCompatibilityAction.VIDEO_INFORMATION) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSelectAction(VideoCompatibilityAction.CANCEL) },
                modifier = Modifier.testTag("compatibility_action_cancel")
            ) {
                Text(VideoCompatibilityAction.CANCEL.label)
            }
        }
    )
}

@Composable
private fun CompatibilityOptionButton(
    action: VideoCompatibilityAction,
    enabled: Boolean,
    unavailableReason: String?,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("compatibility_action_${action.name.lowercase()}")
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                )
                if (!enabled) {
                    Text(
                        text = "Unavailable",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(
                text = action.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!enabled && !unavailableReason.isNullOrBlank()) {
                Text(
                    text = unavailableReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Detailed Video Stream & Hardware Capability Information Dialog opened when the user selects
 * "Video information" from the compatibility panel.
 */
@Composable
fun VideoTechnicalInfoDialog(
    media: MediaEntity,
    playbackState: PlaybackUiState,
    evaluation: VideoStreamCapabilityEvaluation?,
    onDismiss: () -> Unit
) {
    val width = evaluation?.width ?: media.width.takeIf { it > 0 } ?: playbackState.decodedVideoWidth
    val height = evaluation?.height ?: media.height.takeIf { it > 0 } ?: playbackState.decodedVideoHeight
    val bitrateMbps = evaluation?.bitrateBps?.takeIf { it > 0L }?.let {
        "%.1f Mbps".format(it / 1_000_000.0)
    } ?: "Auto / Container Bitrate"

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = 500.dp)
            .testTag("video_technical_info_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Video Stream & Decoder Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Title: ${media.title}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Source Resolution: ${width}x${height} (${evaluation?.resolutionTier?.label ?: "Standard"})", style = MaterialTheme.typography.bodySmall)
                Text("Codec MIME: ${media.mimeType}", style = MaterialTheme.typography.bodySmall)
                Text("Estimated Bitrate: $bitrateMbps", style = MaterialTheme.typography.bodySmall)
                Text("Color Profile: ${if (evaluation?.is10BitOrHdr == true) "10-bit / HDR" else "8-bit SDR"}", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Active Engine Path: ${playbackState.activeVideoEngineStrategy.label}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text("Verified Decoder: ${playbackState.verifiedDecoderName ?: evaluation?.primaryDecoderName ?: "Pending / Uninitialized"}", style = MaterialTheme.typography.bodySmall)
                Text("Hardware Supported at Native Res: ${if (evaluation?.isHardwareSupported == true) "Yes" else "No"}", style = MaterialTheme.typography.bodySmall)
                if (!evaluation?.hardwareUnsupportedReason.isNullOrBlank()) {
                    Text("Hardware Limit: ${evaluation?.hardwareUnsupportedReason}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Text("Software Fallback Safe: ${if (evaluation?.isSoftwareFallbackSafe == true) "Yes" else "No"}", style = MaterialTheme.typography.bodySmall)
                if (!evaluation?.softwareUnavailableReason.isNullOrBlank()) {
                    Text("Software Limit: ${evaluation?.softwareUnavailableReason}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Downscaled Compatibility Active: ${if (playbackState.isDownscaledPlayback) "Yes (≤1080p)" else "No (Native)"}", style = MaterialTheme.typography.bodySmall)
                Text("Dropped Video Frames: ${playbackState.droppedVideoFrames}", style = MaterialTheme.typography.bodySmall)
                Text("Temporary High-Res Session Active: ${if (playbackState.isTemporaryHighResEngineActive) "Yes (auto-releases on stop/end)" else "No (Standard path)"}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("video_technical_info_close_button")
            ) {
                Text("Close")
            }
        }
    )
}

/**
 * Centered, prominent Video Audio Track Dialog styled consistently with RingtonePermissionDialog.
 * Contains available container audio streams with clear selection indicator and 5-band EQ shortcut.
 */
@Composable
fun VideoAudioTrackDialog(
    playbackState: PlaybackUiState,
    onSelectAudioTrack: (AudioTrackOption) -> Unit,
    onOpenEqualizer: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = 480.dp)
            .testTag("video_audio_track_sheet_card"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Audio Track & Sound Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (playbackState.availableAudioTracks.isNotEmpty()) {
                    Text(
                        text = "Available Audio Streams",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    playbackState.availableAudioTracks.forEach { trackOption ->
                        Surface(
                            color = if (trackOption.isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectAudioTrack(trackOption)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = trackOption.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (trackOption.isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (trackOption.language.isNotBlank()) {
                                        Text(
                                            text = "Language: ${trackOption.language}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (trackOption.isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Single audio stream active (Embedded Stereo)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenEqualizer()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open 5-Band Equalizer & Bass Boost")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("audio_track_close_button")
            ) {
                Text("Done")
            }
        }
    )
}

/**
 * Centered, prominent Add to Playlist Dialog styled consistently with RingtonePermissionDialog.
 * Displays playlist list with track counts, instant playlist creation option, and accessible buttons.
 */
@Composable
fun AddToPlaylistDialog(
    media: MediaEntity,
    playlists: List<PlaylistWithCount>,
    onSelectPlaylist: (PlaylistWithCount) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(max = 480.dp)
            .testTag("add_to_playlist_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Add to Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Add '${media.title}' to a playlist:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (playlists.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No playlists yet. Create a new playlist instantly:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = {
                                    onCreatePlaylist("My Studio Favorites")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Create 'My Studio Favorites'")
                            }
                        }
                    }
                } else {
                    playlists.forEach { pl ->
                        OutlinedButton(
                            onClick = { onSelectPlaylist(pl) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = pl.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${pl.itemCount} tracks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("add_to_playlist_close_button")
            ) {
                Text("Close")
            }
        }
    )
}

/**
 * Phase 3 & Landscape Requirement: Persistent mini-player when playback is active.
 * Placed inside the center content column so it never overlaps navigation controls or system bars.
 */
@Composable
fun PersistentMiniPlayerBar(
    playbackState: PlaybackUiState,
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPreviousClick: (() -> Unit)? = null,
    showSidePanelToggle: Boolean = false,
    isSidePanelOpen: Boolean = false,
    onToggleSidePanelClick: (() -> Unit)? = null,
    isCompactHeight: Boolean = false
) {
    val current = playbackState.currentMedia ?: return
    val progress = if (playbackState.durationMs > 0L) {
        (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = if (isCompactHeight) 2.dp else 4.dp)
            .testTag("persistent_mini_player")
            .clickable(onClick = onExpandClick)
            .semantics { contentDescription = "Mini player: ${current.title}. Tap to expand player." },
        shape = RoundedCornerShape(if (isCompactHeight) 14.dp else 18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = if (isCompactHeight) 3.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val miniArtwork by rememberMediaArtworkBitmap(
                    media = current,
                    artworkData = playbackState.currentArtworkData,
                    artworkUri = playbackState.currentArtworkUri,
                    targetSizePx = 144
                )
                val thumbSize = if (isCompactHeight) 36.dp else 40.dp
                Box(
                    modifier = Modifier
                        .size(thumbSize)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    val resolvedMiniThumb = miniArtwork
                    if (resolvedMiniThumb != null && current.mediaType == MediaType.AUDIO) {
                        Image(
                            bitmap = resolvedMiniThumb,
                            contentDescription = "Album artwork for ${current.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp))
                                .testTag("mini_player_artwork_image")
                        )
                    } else {
                        Icon(
                            imageVector = if (current.mediaType == MediaType.VIDEO) {
                                Icons.Default.Movie
                            } else {
                                Icons.Default.GraphicEq
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${current.artist} • ${formatDurationMillis(playbackState.currentPositionMs)} / ${formatDurationMillis(playbackState.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onPreviousClick != null) {
                    IconButton(
                        onClick = onPreviousClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("mini_player_previous_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous track",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mini_player_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mini_player_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next track",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (showSidePanelToggle && onToggleSidePanelClick != null) {
                    IconButton(
                        onClick = onToggleSidePanelClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("mini_player_toggle_side_panel_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ViewSidebar,
                            contentDescription = if (isSidePanelOpen) "Hide Now Playing side panel" else "Show Now Playing side panel",
                            tint = if (isSidePanelOpen) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

