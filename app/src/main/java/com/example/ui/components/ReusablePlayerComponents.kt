package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.MediaEntity
import com.example.data.local.MediaType
import com.example.media.DetailedMediaFileInfo
import com.example.media.MediaFileActionsHelper
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
                Box(
                    modifier = Modifier
                        .size(if (isCompactHeight) 36.dp else 40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
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

