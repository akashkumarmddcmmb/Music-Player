package com.akashkumarmmb.pulseplay.ui.components

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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.akashkumarmmb.pulseplay.ui.theme.AppThemeMode
import com.akashkumarmmb.pulseplay.data.local.PlaylistWithCount
import com.akashkumarmmb.pulseplay.data.preferences.UserPreferencesState
import com.akashkumarmmb.pulseplay.data.preferences.VideoOrientationPreference
import com.akashkumarmmb.pulseplay.viewmodel.AudioTrackOption
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.akashkumarmmb.pulseplay.data.local.MediaEntity
import com.akashkumarmmb.pulseplay.data.local.MediaType
import com.akashkumarmmb.pulseplay.data.preferences.HighResVideoPlaybackMode
import com.akashkumarmmb.pulseplay.media.DetailedMediaFileInfo
import com.akashkumarmmb.pulseplay.media.MediaFileActionsHelper
import com.akashkumarmmb.pulseplay.media.Resilient4KCodecEngine
import com.akashkumarmmb.pulseplay.media.VideoCompatibilityAction
import com.akashkumarmmb.pulseplay.media.VideoStreamCapabilityEvaluation
import com.akashkumarmmb.pulseplay.media.rememberMediaArtworkBitmap
import com.akashkumarmmb.pulseplay.viewmodel.PlaybackUiState
import com.akashkumarmmb.pulseplay.viewmodel.RepeatModeState

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
    onPlayNextClick: () -> Unit = {},
    onAddToQueueClick: () -> Unit = {},
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
                            text = { Text("Play Next", maxLines = 1) },
                            leadingIcon = {
                                Icon(Icons.Default.SkipNext, contentDescription = null)
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            onClick = {
                                overflowExpanded = false
                                onPlayNextClick()
                            },
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("overflow_play_next_action")
                        )

                        DropdownMenuItem(
                            text = { Text("Add to Queue", maxLines = 1) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            onClick = {
                                overflowExpanded = false
                                onAddToQueueClick()
                            },
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("overflow_add_to_queue_action")
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
    onCopyLocation: (String) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavoriteClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
    onAddToPlaylistClick: (() -> Unit)? = null,
    onOpenInStudioClick: (() -> Unit)? = null
) {
    var copiedLocationFeedback by remember { mutableStateOf(false) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(if (isLandscape) 0.88f else 0.94f)
            .widthIn(max = if (isLandscape) 640.dp else 520.dp)
            .testTag("file_info_dialog"),
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
                    .heightIn(max = if (isLandscape) 240.dp else 460.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("file_info_scroll_column"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onToggleFavoriteClick != null || onShareClick != null || onAddToPlaylistClick != null || onOpenInStudioClick != null) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("file_info_quick_actions_row")
                    ) {
                        if (onToggleFavoriteClick != null) {
                            item {
                                FilterChip(
                                    selected = isFavorite,
                                    onClick = onToggleFavoriteClick,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(if (isFavorite) "Favorited" else "Favorite") },
                                    modifier = Modifier.testTag("file_info_favorite_chip")
                                )
                            }
                        }
                        if (onAddToPlaylistClick != null) {
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        onDismiss()
                                        onAddToPlaylistClick()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("Add to Playlist") },
                                    modifier = Modifier.testTag("file_info_add_playlist_chip")
                                )
                            }
                        }
                        if (onShareClick != null) {
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        onDismiss()
                                        onShareClick()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("Share") },
                                    modifier = Modifier.testTag("file_info_share_chip")
                                )
                            }
                        }
                        if (onOpenInStudioClick != null) {
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        onDismiss()
                                        onOpenInStudioClick()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.ContentCut,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("Media Studio") },
                                    modifier = Modifier.testTag("file_info_studio_chip")
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                }

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
                FileInfoMetadataRow(
                    label = "Sample Rate & Channels",
                    value = info.sampleRateAndChannels,
                    testTag = "file_info_row_sample_rate_channels"
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
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
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
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.92f)
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
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(if (isLandscape) 0.88f else 0.92f)
            .widthIn(max = if (isLandscape) 600.dp else 520.dp)
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
                    style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
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
                    .heightIn(max = if (isLandscape) 230.dp else 440.dp)
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
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val dialogMaxWidth = if (isLandscape) 720.dp else 520.dp
    val landscapeBodyMaxHeight = (configuration.screenHeightDp - 126).coerceIn(175, 265).dp
    val contentMaxHeight = if (isLandscape) landscapeBodyMaxHeight else 460.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.92f else 0.94f)
                .widthIn(max = dialogMaxWidth)
                .padding(vertical = if (isLandscape) 6.dp else 12.dp)
                .testTag("video_settings_panel")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isLandscape) 20.dp else 22.dp,
                        vertical = if (isLandscape) 14.dp else 20.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 14.dp)
            ) {
                // Header Row
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
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Video Settings & Playback",
                            style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Scrollable Settings Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = contentMaxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 12.dp)
                ) {
                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Left Column: Orientation, Aspect Ratio & Gesture/Screen Switches
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                VideoSettingsSectionHeader("Screen Rotation Mode")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    VideoOrientationPreference.entries.forEach { pref ->
                                        FilterChip(
                                            selected = userPreferences.videoOrientationPreference == pref,
                                            onClick = { onSelectVideoOrientation(pref) },
                                            label = {
                                                Text(
                                                    text = pref.label,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            modifier = Modifier.testTag("video_orientation_pref_${pref.name.lowercase()}")
                                        )
                                    }
                                }

                                VideoSettingsSectionHeader("Frame & Aspect Ratio")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onRotateVideo90,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.ScreenRotation, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Frame: ${playbackState.videoRotationDegrees}°",
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onCycleAspectMode,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = playbackState.videoAspectMode.label,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                VideoSettingsSwitchRow(
                                    title = "Keep Screen On During Video",
                                    checked = userPreferences.keepScreenOnDuringVideo,
                                    onCheckedChange = onToggleKeepScreenOn,
                                    testTag = "video_keep_screen_on_switch"
                                )

                                VideoSettingsSwitchRow(
                                    title = "Brightness & Volume Gestures",
                                    checked = userPreferences.videoGestureControlsEnabled,
                                    onCheckedChange = onToggleVideoGestureControls,
                                    testTag = "video_gesture_controls_switch"
                                )
                            }

                            // Right Column: Playback Speed (Full 8-speed grid), Subtitles/EQ & 4K Options
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                VideoSettingsSectionHeader("Playback Speed (${playbackState.playbackSpeed}x)")
                                val speedRow1 = listOf(0.5f, 0.75f, 1.0f, 1.25f)
                                val speedRow2 = listOf(1.5f, 2.0f, 2.5f, 3.0f)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        speedRow1.forEach { spd ->
                                            FilterChip(
                                                selected = abs(playbackState.playbackSpeed - spd) < 0.05f,
                                                onClick = { onSetPlaybackSpeed(spd) },
                                                label = {
                                                    Text(
                                                        text = "${spd}x",
                                                        fontSize = 11.sp,
                                                        maxLines = 1
                                                    )
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        speedRow2.forEach { spd ->
                                            FilterChip(
                                                selected = abs(playbackState.playbackSpeed - spd) < 0.05f,
                                                onClick = { onSetPlaybackSpeed(spd) },
                                                label = {
                                                    Text(
                                                        text = "${spd}x",
                                                        fontSize = 11.sp,
                                                        maxLines = 1
                                                    )
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                VideoSettingsSectionHeader("Subtitles & Audio EQ")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onPickSubtitleFileClick,
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Subtitles (${if (userPreferences.subtitlesEnabledByDefault) "On" else "Off"})",
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onDismiss()
                                            onOpenEqualizer()
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Equalizer, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Audio EQ",
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                VideoSettingsSectionHeader("High-Resolution Playback Mode")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(HighResVideoPlaybackMode.entries) { mode ->
                                        val avail = playbackState.playbackModeAvailabilities.find { it.mode == mode }
                                        val isEnabled = avail?.isAvailable ?: true
                                        FilterChip(
                                            selected = userPreferences.highResVideoPlaybackMode == mode,
                                            onClick = { if (isEnabled) onSelectHighResMode(mode) },
                                            enabled = isEnabled,
                                            label = { Text(mode.label, fontSize = 11.sp, maxLines = 1) },
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
                                        .testTag("open_video_compatibility_options_button"),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "4K/8K Decoder & Compatibility Options",
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else {
                        // Portrait Layout
                        VideoSettingsSectionHeader("Screen Rotation Mode")
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

                        VideoSettingsSectionHeader("Frame & Aspect Ratio")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onRotateVideo90, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.ScreenRotation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Frame: ${playbackState.videoRotationDegrees}°", maxLines = 1)
                            }
                            OutlinedButton(onClick = onCycleAspectMode, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(playbackState.videoAspectMode.label, maxLines = 1)
                            }
                        }

                        VideoSettingsSectionHeader("Playback Speed (${playbackState.playbackSpeed}x)")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)) { spd ->
                                FilterChip(
                                    selected = abs(playbackState.playbackSpeed - spd) < 0.05f,
                                    onClick = { onSetPlaybackSpeed(spd) },
                                    label = { Text("${spd}x") }
                                )
                            }
                        }

                        VideoSettingsSwitchRow(
                            title = "Keep Screen On During Video",
                            subtitle = "Prevents screen timeout while video is actively playing",
                            checked = userPreferences.keepScreenOnDuringVideo,
                            onCheckedChange = onToggleKeepScreenOn,
                            testTag = "video_keep_screen_on_switch"
                        )

                        VideoSettingsSwitchRow(
                            title = "Brightness & Volume Gestures",
                            subtitle = "Vertical swipe gestures on screen edges",
                            checked = userPreferences.videoGestureControlsEnabled,
                            onCheckedChange = onToggleVideoGestureControls,
                            testTag = "video_gesture_controls_switch"
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onPickSubtitleFileClick, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Subtitles (${if (userPreferences.subtitlesEnabledByDefault) "On" else "Off"})", maxLines = 1)
                            }
                            OutlinedButton(onClick = { onDismiss(); onOpenEqualizer() }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Equalizer, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Audio EQ", maxLines = 1)
                            }
                        }

                        VideoSettingsSectionHeader("High-Resolution Video Playback Mode")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(HighResVideoPlaybackMode.entries) { mode ->
                                val avail = playbackState.playbackModeAvailabilities.find { it.mode == mode }
                                val isEnabled = avail?.isAvailable ?: true
                                FilterChip(
                                    selected = userPreferences.highResVideoPlaybackMode == mode,
                                    onClick = { if (isEnabled) onSelectHighResMode(mode) },
                                    enabled = isEnabled,
                                    label = { Text(mode.label, maxLines = 1) },
                                    modifier = Modifier.testTag("video_dialog_high_res_${mode.name.lowercase()}")
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { onDismiss(); onOpenCompatibilityOptions() },
                            modifier = Modifier.fillMaxWidth().testTag("open_video_compatibility_options_button")
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("4K/8K Decoder & Compatibility Options", maxLines = 1)
                        }
                    }
                }

                // Footer Row: Lock Controls + Done Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            onToggleControlsLock(!playbackState.isVideoControlsLocked)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (playbackState.isVideoControlsLocked) "Unlock Video Controls" else "Lock Video Touch Controls",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("video_settings_close_button")
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoSettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun VideoSettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag).scale(0.85f)
        )
    }
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

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(if (isLandscape) 0.88f else 0.92f)
            .widthIn(max = if (isLandscape) 600.dp else 520.dp)
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
                    .heightIn(max = if (isLandscape) 230.dp else 440.dp)
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
            }
            Text(
                text = action.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Clean Video Information Dialog opened when the user selects
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
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.90f)
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
                    text = "Video Information",
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
                Text("Resolution: ${width}x${height} (${evaluation?.resolutionTier?.displayLabel ?: "Standard"})", style = MaterialTheme.typography.bodySmall)
                Text("Format: ${media.mimeType}", style = MaterialTheme.typography.bodySmall)
                Text("Estimated Bitrate: $bitrateMbps", style = MaterialTheme.typography.bodySmall)
                Text("Color Profile: ${if (evaluation?.is10BitOrHdr == true) "10-bit / HDR" else "8-bit SDR"}", style = MaterialTheme.typography.bodySmall)
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
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.90f)
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

private val ReferencePanelCharcoal = Color(0xFF1A1D24)
private val ReferencePanelSurface = Color(0xFF232730)
private val ReferenceActiveGreen = Color(0xFF00E676)
private val ReferenceDividerColor = Color.White.copy(alpha = 0.10f)
private val ReferenceNormalText = Color(0xFFF3F5F8)
private val ReferenceSecondaryText = Color(0xFFAAB2C0)

/**
 * Reference-style circular outline icon button with centered label below.
 * Normal state: white/light grey icon and thin circular outline.
 * Active state: bright green (#00E676) icon, border, subtle green tint, and label.
 */
@Composable
private fun MusicCircularActionItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    badgeText: String? = null,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = ReferenceActiveGreen
    val iconTint = if (isActive) activeColor else ReferenceNormalText
    val borderColor = if (isActive) activeColor else Color.White.copy(alpha = 0.24f)
    val circleBg = if (isActive) activeColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)
    val labelColor = if (isActive) activeColor else ReferenceNormalText.copy(alpha = 0.90f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(circleBg, CircleShape)
                .border(1.2.dp, borderColor, CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            if (!badgeText.isNullOrBlank()) {
                Surface(
                    color = if (isActive) activeColor else ReferencePanelSurface,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .border(0.8.dp, borderColor, RoundedCornerShape(6.dp))
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color(0xFF05180E) else ReferenceNormalText,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                        maxLines = 1
                    )
                }
            }
        }
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReferenceSectionHeading(title: String, trailingSummary: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = ReferenceSecondaryText,
            fontSize = 13.sp
        )
        if (!trailingSummary.isNullOrBlank()) {
            Text(
                text = trailingSummary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = ReferenceActiveGreen,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Unified Dark Charcoal Translucent Music Quick Settings Panel reproducing the 5-section
 * reference video specification:
 * - Section A: Top Action Icon Grid (Equalizer, Audio Output, Cast, Favorite, Bookmark, Lyrics, Share, Audio Info)
 * - Section B: Play settings (A-B Repeat, Sleep Timer, Playback Speed, Shuffle, Repeat Mode, Crossfade, Gapless, Stop After Current)
 * - Section C: Loop (Horizontal pill control group: Shuffle | Repeat One | Repeat All | Repeat Off)
 * - Section D: Audio controls (Thin Volume & Audio Balance sliders with circular thumb + bright green active track, plus Audio Output, Bass Boost & EQ shortcuts)
 * - Section E: More (Edit Player, Lyrics Help/Tutorials, File Info, Feedback, Customize Theme, Share Song, Add to Playlist, Set Ringtone, Studio)
 *
 * Responsive across Portrait (clean bottom/center overlay) and Landscape (right-hand translucent panel keeping left playback visible).
 */
@Composable
fun MusicPlaybackSettingsDialog(
    playbackState: PlaybackUiState,
    preferences: UserPreferencesState,
    onDismiss: () -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSetAbRepeatStart: () -> Unit,
    onSetAbRepeatEnd: () -> Unit,
    onClearAbRepeat: () -> Unit,
    onToggleBookmark: () -> Unit,
    onRemoveBookmark: (Long) -> Unit,
    onSeekToBookmark: (Long) -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onToggleStopAtEndOfTrack: (Boolean) -> Unit,
    onToggleShuffle: () -> Unit,
    onSelectRepeatMode: (RepeatModeState) -> Unit,
    onToggleBackgroundPlayback: (Boolean) -> Unit,
    onToggleResumePlayback: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenFileInfo: () -> Unit,
    onOpenAudioOutputSwitcher: () -> Unit,
    isFavorite: Boolean = false,
    equalizerEnabled: Boolean = false,
    bassBoostStrength: Int = 0,
    onToggleFavorite: () -> Unit = {},
    onOpenLyricsSettings: () -> Unit = {},
    onShareCurrentMedia: () -> Unit = {},
    onOpenCastSettings: () -> Unit = {},
    onCycleCrossfade: () -> Unit = {},
    onToggleGaplessPlayback: (Boolean) -> Unit = {},
    onSetVolumeLevel: (Float) -> Unit = {},
    onSetAudioBalance: (Float) -> Unit = {},
    onCycleBassBoost: () -> Unit = {},
    onOpenStudio: () -> Unit = {},
    onAddToPlaylistCurrentMedia: () -> Unit = {},
    onSetRingtoneCurrentMedia: () -> Unit = {},
    onSetThemeMode: (AppThemeMode) -> Unit = {},
    onSetDynamicColor: (Boolean) -> Unit = {},
    onSetPlayerShowWaveform: (Boolean) -> Unit = {},
    onSetPlayerShowQuickActionRow: (Boolean) -> Unit = {},
    onSetPlayerShowRemainingTime: (Boolean) -> Unit = {},
    onSetPlayerShowAbAndBookmarkStrip: (Boolean) -> Unit = {},
    onSendFeedback: (String, String) -> Unit = { _, _ -> },
    onOpenSpeedDialog: (() -> Unit)? = null
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp > configuration.screenHeightDp

    var showEditPlayerDialog by rememberSaveable { mutableStateOf(false) }
    var showLyricsHelpDialog by rememberSaveable { mutableStateOf(false) }
    var showFeedbackDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomizeThemeDialog by rememberSaveable { mutableStateOf(false) }

    if (showEditPlayerDialog) {
        EditPlayerLayoutDialog(
            preferences = preferences,
            onToggleWaveform = onSetPlayerShowWaveform,
            onToggleQuickActionRow = onSetPlayerShowQuickActionRow,
            onToggleRemainingTime = onSetPlayerShowRemainingTime,
            onToggleAbAndBookmarkStrip = onSetPlayerShowAbAndBookmarkStrip,
            onToggleBackgroundPlayback = onToggleBackgroundPlayback,
            onToggleResumePlayback = onToggleResumePlayback,
            onDismiss = { showEditPlayerDialog = false }
        )
    }

    if (showLyricsHelpDialog) {
        LyricsHelpTutorialDialog(
            onOpenLyricsLoader = {
                showLyricsHelpDialog = false
                onDismiss()
                onOpenLyricsSettings()
            },
            onDismiss = { showLyricsHelpDialog = false }
        )
    }

    if (showFeedbackDialog) {
        PlayerFeedbackDialog(
            currentTrackTitle = playbackState.currentMedia?.title,
            onSubmit = { category, message ->
                showFeedbackDialog = false
                onSendFeedback(category, message)
            },
            onDismiss = { showFeedbackDialog = false }
        )
    }

    if (showCustomizeThemeDialog) {
        CustomizeThemeDialog(
            preferences = preferences,
            onSetThemeMode = onSetThemeMode,
            onSetDynamicColor = onSetDynamicColor,
            onDismiss = { showCustomizeThemeDialog = false }
        )
    }

    val panelAlignment = if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter
    val panelMaxWidth = if (isLandscape) 440.dp else 520.dp
    val maxScrollHeight = if (isLandscape) {
        (configuration.screenHeightDp - 96).coerceIn(190, 420).dp
    } else {
        (configuration.screenHeightDp - 140).coerceIn(220, 560).dp
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = panelAlignment
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isLandscape) 0.38f else 0.52f))
                    .clickable(onClick = onDismiss)
                    .testTag("music_settings_scrim")
            )
            Surface(
                shape = if (isLandscape) {
                    RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 14.dp, bottomEnd = 14.dp)
                } else {
                    RoundedCornerShape(24.dp)
                },
                color = ReferencePanelCharcoal.copy(alpha = 0.94f),
                tonalElevation = 8.dp,
                shadowElevation = 20.dp,
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.62f else 0.96f)
                    .widthIn(max = panelMaxWidth)
                    .padding(
                        horizontal = if (isLandscape) 8.dp else 10.dp,
                        vertical = if (isLandscape) 8.dp else 12.dp
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.12f),
                        shape = if (isLandscape) {
                            RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 14.dp, bottomEnd = 14.dp)
                        } else {
                            RoundedCornerShape(24.dp)
                        }
                    )
                    .testTag("music_playback_settings_panel")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isLandscape) 16.dp else 18.dp,
                            vertical = if (isLandscape) 12.dp else 16.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Top Panel Header Bar
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
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(ReferenceActiveGreen.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = ReferenceActiveGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Player Quick Settings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ReferenceNormalText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = playbackState.currentMedia?.title ?: "Active Audio Stream",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ReferenceSecondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    onDismiss()
                                    onOpenStudio()
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("now_playing_menu_studio")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCut,
                                    contentDescription = "Edit in Media Studio",
                                    tint = ReferenceNormalText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("music_settings_header_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Music Playback Settings",
                                    tint = ReferenceNormalText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = ReferenceDividerColor)

                    // Vertically Scrollable 5-Section Body
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxScrollHeight)
                            .verticalScroll(rememberScrollState())
                            .testTag("now_playing_overflow_dropdown_menu"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // =========================================================================
                        // SECTION A – TOP ACTION ICON GRID (8 Circular Icons, 4x2)
                        // 1. Equalizer | 2. Audio Output | 3. Cast | 4. Favorite
                        // 5. Bookmark  | 6. Lyrics       | 7. Share | 8. Audio Info
                        // =========================================================================
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("music_settings_section_a_grid"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MusicCircularActionItem(
                                    icon = Icons.Default.Equalizer,
                                    label = "Equalizer",
                                    isActive = equalizerEnabled || bassBoostStrength > 0,
                                    badgeText = if (equalizerEnabled) "ON" else null,
                                    testTag = "now_playing_menu_equalizer",
                                    onClick = {
                                        onDismiss()
                                        onOpenEqualizer()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.Default.Speaker,
                                    label = "Audio Output",
                                    isActive = false,
                                    testTag = "music_settings_audio_output_button",
                                    onClick = onOpenAudioOutputSwitcher,
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.Default.Cast,
                                    label = "Cast",
                                    isActive = false,
                                    testTag = "music_settings_cast_button",
                                    onClick = onOpenCastSettings,
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    label = "Favorite",
                                    isActive = isFavorite,
                                    testTag = "now_playing_menu_favorite",
                                    onClick = onToggleFavorite,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MusicCircularActionItem(
                                    icon = if (playbackState.trackBookmarksMs.isNotEmpty()) {
                                        Icons.Default.Bookmark
                                    } else {
                                        Icons.Default.BookmarkBorder
                                    },
                                    label = "Bookmark",
                                    isActive = playbackState.trackBookmarksMs.isNotEmpty(),
                                    badgeText = playbackState.trackBookmarksMs.size.takeIf { it > 0 }?.toString(),
                                    testTag = "music_settings_add_bookmark_button",
                                    onClick = onToggleBookmark,
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.Default.Subtitles,
                                    label = "Lyrics",
                                    isActive = playbackState.loadedSubtitleFormat != null,
                                    badgeText = playbackState.loadedSubtitleFormat?.name,
                                    testTag = "now_playing_menu_lyrics",
                                    onClick = {
                                        onDismiss()
                                        onOpenLyricsSettings()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.Default.Share,
                                    label = "Share",
                                    isActive = false,
                                    testTag = "now_playing_menu_share",
                                    onClick = {
                                        onDismiss()
                                        onShareCurrentMedia()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.Default.Info,
                                    label = "Audio Info",
                                    isActive = false,
                                    testTag = "now_playing_menu_file_info",
                                    onClick = {
                                        onDismiss()
                                        onOpenFileInfo()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Saved Bookmarks Strip (when bookmarks exist on active track)
                            if (playbackState.trackBookmarksMs.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(items = playbackState.trackBookmarksMs) { bmMs ->
                                        FilterChip(
                                            selected = true,
                                            onClick = { onSeekToBookmark(bmMs) },
                                            label = {
                                                Text(
                                                    text = "🔖 ${formatDurationMillis(bmMs)}",
                                                    fontSize = 11.sp,
                                                    color = ReferenceActiveGreen
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove bookmark",
                                                    tint = ReferenceActiveGreen,
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable { onRemoveBookmark(bmMs) }
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = ReferenceActiveGreen.copy(alpha = 0.14f)
                                            ),
                                            modifier = Modifier.testTag("music_settings_bookmark_chip_$bmMs")
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = ReferenceDividerColor)

                        // =========================================================================
                        // SECTION B – PLAY SETTINGS (Heading: "Play settings", 8 Circular Icons, 4x2)
                        // 1. A-B Repeat | 2. Sleep Timer | 3. Playback Speed | 4. Shuffle
                        // 5. Repeat     | 6. Crossfade   | 7. Gapless        | 8. Stop After Current
                        // =========================================================================
                        val speedBadge = "${"%.2f".format(playbackState.playbackSpeed).trimEnd('0').trimEnd('.')}x"
                        val sleepBadge = when {
                            playbackState.sleepTimerStopAtEndOfTrack -> "EOT"
                            playbackState.sleepTimerRemainingMs > 0L -> "${(playbackState.sleepTimerRemainingMs / 60_000L).coerceAtLeast(1L)}m"
                            else -> null
                        }
                        val crossfadeBadge = if (preferences.crossfadeDurationSeconds > 0) {
                            "${preferences.crossfadeDurationSeconds}s"
                        } else {
                            "Off"
                        }

                        ReferenceSectionHeading(
                            title = "Play settings",
                            trailingSummary = "$speedBadge • ${if (preferences.gaplessPlaybackEnabled) "Gapless" else "Standard"}"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("music_settings_section_b_play_settings"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MusicCircularActionItem(
                                    icon = Icons.Default.Repeat,
                                    label = "A-B Repeat",
                                    isActive = playbackState.abRepeatStartMs != null,
                                    badgeText = when {
                                        playbackState.isAbRepeatActive -> "A-B"
                                        playbackState.abRepeatStartMs != null -> "A"
                                        else -> null
                                    },
                                    testTag = "music_settings_ab_grid_button",
                                    onClick = {
                                        when {
                                            playbackState.abRepeatStartMs == null -> onSetAbRepeatStart()
                                            playbackState.abRepeatEndMs == null -> onSetAbRepeatEnd()
                                            else -> onClearAbRepeat()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.Default.Bedtime,
                                    label = "Sleep Timer",
                                    isActive = playbackState.isSleepTimerActive,
                                    badgeText = sleepBadge,
                                    testTag = "now_playing_menu_sleep_timer",
                                    onClick = {
                                        val nextMins = when (playbackState.sleepTimerTotalMinutes) {
                                            0 -> 15
                                            15 -> 30
                                            30 -> 45
                                            45 -> 60
                                            else -> 0
                                        }
                                        if (nextMins == 0) onCancelSleepTimer() else onStartSleepTimer(nextMins)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.Default.Speed,
                                    label = "Speed",
                                    isActive = abs(playbackState.playbackSpeed - 1.0f) > 0.04f,
                                    badgeText = speedBadge,
                                    testTag = "now_playing_menu_speed",
                                    onClick = {
                                        if (onOpenSpeedDialog != null) {
                                            onDismiss()
                                            onOpenSpeedDialog()
                                        } else {
                                            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                                            val curIdx = speeds.indexOfFirst { abs(it - playbackState.playbackSpeed) < 0.04f }
                                            val nextSpd = speeds[(curIdx + 1) % speeds.size]
                                            onSelectSpeed(nextSpd)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.Default.Shuffle,
                                    label = "Shuffle",
                                    isActive = playbackState.shuffleEnabled,
                                    testTag = "music_settings_play_shuffle_button",
                                    onClick = onToggleShuffle,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MusicCircularActionItem(
                                    icon = if (playbackState.repeatMode == RepeatModeState.ONE) {
                                        Icons.Default.RepeatOne
                                    } else {
                                        Icons.Default.Repeat
                                    },
                                    label = when (playbackState.repeatMode) {
                                        RepeatModeState.ONE -> "Repeat 1"
                                        RepeatModeState.ALL -> "Repeat All"
                                        RepeatModeState.OFF -> "Repeat Off"
                                    },
                                    isActive = playbackState.repeatMode != RepeatModeState.OFF,
                                    testTag = "music_settings_play_repeat_button",
                                    onClick = {
                                        val nextMode = when (playbackState.repeatMode) {
                                            RepeatModeState.OFF -> RepeatModeState.ALL
                                            RepeatModeState.ALL -> RepeatModeState.ONE
                                            RepeatModeState.ONE -> RepeatModeState.OFF
                                        }
                                        onSelectRepeatMode(nextMode)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.Default.SyncAlt,
                                    label = "Crossfade",
                                    isActive = preferences.crossfadeDurationSeconds > 0,
                                    badgeText = crossfadeBadge,
                                    testTag = "music_settings_crossfade_button",
                                    onClick = onCycleCrossfade,
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.Default.GraphicEq,
                                    label = "Gapless",
                                    isActive = preferences.gaplessPlaybackEnabled,
                                    badgeText = if (preferences.gaplessPlaybackEnabled) "ON" else "OFF",
                                    testTag = "music_settings_gapless_button",
                                    onClick = { onToggleGaplessPlayback(!preferences.gaplessPlaybackEnabled) },
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.Default.HourglassTop,
                                    label = "Stop After",
                                    isActive = playbackState.sleepTimerStopAtEndOfTrack,
                                    badgeText = if (playbackState.sleepTimerStopAtEndOfTrack) "ON" else null,
                                    testTag = "music_settings_stop_after_current_button",
                                    onClick = {
                                        onToggleStopAtEndOfTrack(!playbackState.sleepTimerStopAtEndOfTrack)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Inline Precision Controls for A-B Repeat, Speed & Sleep Timer
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onSetAbRepeatStart,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("music_settings_ab_set_a"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = playbackState.abRepeatStartMs?.let { "A: ${formatDurationMillis(it)}" } ?: "Set Point A",
                                        fontSize = 11.sp,
                                        color = if (playbackState.abRepeatStartMs != null) ReferenceActiveGreen else ReferenceNormalText,
                                        maxLines = 1
                                    )
                                }
                                OutlinedButton(
                                    onClick = onSetAbRepeatEnd,
                                    enabled = playbackState.abRepeatStartMs != null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("music_settings_ab_set_b"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = playbackState.abRepeatEndMs?.let { "B: ${formatDurationMillis(it)}" } ?: "Set Point B",
                                        fontSize = 11.sp,
                                        color = if (playbackState.abRepeatEndMs != null) ReferenceActiveGreen else ReferenceNormalText,
                                        maxLines = 1
                                    )
                                }
                                if (playbackState.abRepeatStartMs != null || playbackState.abRepeatEndMs != null) {
                                    TextButton(
                                        onClick = onClearAbRepeat,
                                        modifier = Modifier.testTag("music_settings_ab_clear")
                                    ) {
                                        Text("Clear", fontSize = 11.sp, color = ReferenceActiveGreen, maxLines = 1)
                                    }
                                }
                            }

                            // Quick Speed Chips Row
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)) { spd ->
                                    val isSelected = abs(playbackState.playbackSpeed - spd) < 0.05f
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onSelectSpeed(spd) },
                                        label = {
                                            Text(
                                                text = "${spd}x",
                                                fontSize = 11.sp,
                                                color = if (isSelected) Color(0xFF041A0E) else ReferenceNormalText,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ReferenceActiveGreen,
                                            containerColor = ReferencePanelSurface
                                        ),
                                        modifier = Modifier.testTag("music_settings_speed_${spd}")
                                    )
                                }
                            }

                            // Quick Sleep Timer Presets Row
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(listOf(0, 10, 15, 30, 45, 60)) { mins ->
                                    val selected = if (mins == 0) {
                                        !playbackState.isSleepTimerActive
                                    } else {
                                        !playbackState.sleepTimerStopAtEndOfTrack && playbackState.sleepTimerTotalMinutes == mins
                                    }
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            if (mins == 0) onCancelSleepTimer() else onStartSleepTimer(mins)
                                        },
                                        label = {
                                            Text(
                                                text = if (mins == 0) "Timer Off" else "${mins}m",
                                                fontSize = 11.sp,
                                                color = if (selected) Color(0xFF041A0E) else ReferenceNormalText,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ReferenceActiveGreen,
                                            containerColor = ReferencePanelSurface
                                        ),
                                        modifier = Modifier.testTag("music_settings_sleep_${mins}")
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = playbackState.sleepTimerStopAtEndOfTrack,
                                        onClick = { onToggleStopAtEndOfTrack(!playbackState.sleepTimerStopAtEndOfTrack) },
                                        label = {
                                            Text(
                                                text = "End of Track",
                                                fontSize = 11.sp,
                                                color = if (playbackState.sleepTimerStopAtEndOfTrack) Color(0xFF041A0E) else ReferenceNormalText,
                                                fontWeight = if (playbackState.sleepTimerStopAtEndOfTrack) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ReferenceActiveGreen,
                                            containerColor = ReferencePanelSurface
                                        ),
                                        modifier = Modifier.testTag("music_settings_sleep_end_of_track")
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = ReferenceDividerColor)

                        // =========================================================================
                        // SECTION C – LOOP (Heading: "Loop")
                        // Horizontal pill-shaped control group with bright green active state:
                        // Shuffle | Repeat One | Repeat All | Repeat Off
                        // =========================================================================
                        ReferenceSectionHeading(title = "Loop")

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = ReferencePanelSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
                                .testTag("music_settings_section_c_loop")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LoopPillSegment(
                                    label = "Shuffle",
                                    icon = Icons.Default.Shuffle,
                                    selected = playbackState.shuffleEnabled,
                                    testTag = "music_settings_shuffle_chip",
                                    onClick = onToggleShuffle,
                                    modifier = Modifier.weight(1f)
                                )
                                LoopPillSegment(
                                    label = "Repeat 1",
                                    icon = Icons.Default.RepeatOne,
                                    selected = playbackState.repeatMode == RepeatModeState.ONE,
                                    testTag = "music_settings_repeat_one",
                                    onClick = { onSelectRepeatMode(RepeatModeState.ONE) },
                                    modifier = Modifier.weight(1f)
                                )
                                LoopPillSegment(
                                    label = "Repeat All",
                                    icon = Icons.Default.Repeat,
                                    selected = playbackState.repeatMode == RepeatModeState.ALL,
                                    testTag = "music_settings_repeat_all",
                                    onClick = { onSelectRepeatMode(RepeatModeState.ALL) },
                                    modifier = Modifier.weight(1f)
                                )
                                LoopPillSegment(
                                    label = "Repeat Off",
                                    icon = null,
                                    selected = playbackState.repeatMode == RepeatModeState.OFF,
                                    testTag = "music_settings_repeat_off",
                                    onClick = { onSelectRepeatMode(RepeatModeState.OFF) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        HorizontalDivider(color = ReferenceDividerColor)

                        // =========================================================================
                        // SECTION D – AUDIO CONTROLS (Heading: "Audio controls")
                        // Replaces video brightness slider with thin Volume Slider, Audio Balance
                        // Slider, Audio Output selection, Bass Boost shortcut, and Equalizer shortcut.
                        // =========================================================================
                        val volumePercent = (playbackState.volumeGestureLevel.coerceIn(0f, 1f) * 100f).roundToInt()
                        val balanceText = when {
                            abs(preferences.audioBalance) < 0.05f -> "Center (0.0)"
                            preferences.audioBalance < 0f -> "L ${(abs(preferences.audioBalance) * 100).roundToInt()}%"
                            else -> "R ${(preferences.audioBalance * 100).roundToInt()}%"
                        }
                        ReferenceSectionHeading(
                            title = "Audio controls",
                            trailingSummary = "Vol $volumePercent% • $balanceText"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("music_settings_section_d_audio_controls"),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 1. Thin Volume Slider with circular thumb and bright green active track
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Volume",
                                    tint = ReferenceActiveGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Slider(
                                    value = playbackState.volumeGestureLevel.coerceIn(0f, 1f),
                                    onValueChange = onSetVolumeLevel,
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = ReferenceActiveGreen,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("music_settings_volume_slider")
                                )
                                Text(
                                    text = "$volumePercent%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ReferenceNormalText,
                                    modifier = Modifier.width(36.dp),
                                    textAlign = TextAlign.End
                                )
                            }

                            // 2. Thin Stereo Audio Balance (L/R) Slider with center reset
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "L/R",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (abs(preferences.audioBalance) > 0.04f) ReferenceActiveGreen else ReferenceSecondaryText,
                                    modifier = Modifier.width(24.dp)
                                )
                                Slider(
                                    value = preferences.audioBalance.coerceIn(-1f, 1f),
                                    onValueChange = onSetAudioBalance,
                                    valueRange = -1f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = ReferenceActiveGreen,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.18f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("music_settings_balance_slider")
                                )
                                Text(
                                    text = if (abs(preferences.audioBalance) < 0.05f) "0.0" else "%+.1f".format(preferences.audioBalance),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ReferenceActiveGreen,
                                    modifier = Modifier
                                        .width(36.dp)
                                        .clickable { onSetAudioBalance(0f) }
                                        .testTag("music_settings_balance_reset"),
                                    textAlign = TextAlign.End
                                )
                            }

                            // 3. Audio Output, Bass Boost & Equalizer Shortcut Pills
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onCycleBassBoost,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("music_settings_bass_boost_button"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = if (bassBoostStrength > 0) ReferenceActiveGreen else ReferenceNormalText,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (bassBoostStrength > 0) "Bass ${bassBoostStrength / 10}%" else "Bass Boost",
                                        fontSize = 11.sp,
                                        color = if (bassBoostStrength > 0) ReferenceActiveGreen else ReferenceNormalText,
                                        maxLines = 1
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        onDismiss()
                                        onOpenEqualizer()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("music_settings_open_equalizer_button"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Equalizer,
                                        contentDescription = null,
                                        tint = if (equalizerEnabled) ReferenceActiveGreen else ReferenceNormalText,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "5-Band EQ",
                                        fontSize = 11.sp,
                                        color = if (equalizerEnabled) ReferenceActiveGreen else ReferenceNormalText,
                                        maxLines = 1
                                    )
                                }
                                OutlinedButton(
                                    onClick = onOpenAudioOutputSwitcher,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("music_settings_audio_route_shortcut"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SpeakerGroup,
                                        contentDescription = null,
                                        tint = ReferenceNormalText,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Output",
                                        fontSize = 11.sp,
                                        color = ReferenceNormalText,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = ReferenceDividerColor)

                        // =========================================================================
                        // SECTION E – MORE (Heading: "More", Circular Icon Grid)
                        // 1. Edit Player | 2. Lyrics Help / Tutorials | 3. File Info | 4. Feedback
                        // 5. Customize Theme | 6. Share Song | 7. Add to Playlist | 8. Set Ringtone
                        // =========================================================================
                        ReferenceSectionHeading(title = "More")

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("music_settings_section_e_more"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MusicCircularActionItem(
                                    icon = Icons.Default.Edit,
                                    label = "Edit Player",
                                    isActive = !preferences.playerShowWaveform ||
                                        !preferences.playerShowQuickActionRow ||
                                        !preferences.playerShowRemainingTime,
                                    testTag = "music_settings_edit_player_button",
                                    onClick = { showEditPlayerDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                                    label = "Lyrics Help",
                                    isActive = false,
                                    testTag = "music_settings_lyrics_help_button",
                                    onClick = { showLyricsHelpDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.Default.Info,
                                    label = "File Info",
                                    isActive = false,
                                    testTag = "music_settings_file_info_button",
                                    onClick = {
                                        onDismiss()
                                        onOpenFileInfo()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.Default.Feedback,
                                    label = "Feedback",
                                    isActive = false,
                                    testTag = "music_settings_feedback_button",
                                    onClick = { showFeedbackDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MusicCircularActionItem(
                                    icon = Icons.Default.Palette,
                                    label = "Theme",
                                    isActive = preferences.themeMode == AppThemeMode.DARK,
                                    badgeText = preferences.themeMode.name.take(4),
                                    testTag = "music_settings_customize_theme_button",
                                    onClick = { showCustomizeThemeDialog = true },
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.Default.Share,
                                    label = "Share Song",
                                    isActive = false,
                                    testTag = "music_settings_more_share_button",
                                    onClick = {
                                        onDismiss()
                                        onShareCurrentMedia()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                                    label = "Playlist",
                                    isActive = false,
                                    testTag = "now_playing_menu_add_to_playlist",
                                    onClick = {
                                        onDismiss()
                                        onAddToPlaylistCurrentMedia()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                MusicCircularActionItem(
                                    icon = Icons.Default.RingVolume,
                                    label = "Ringtone",
                                    isActive = false,
                                    testTag = "now_playing_menu_ringtone",
                                    onClick = {
                                        onDismiss()
                                        onSetRingtoneCurrentMedia()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Background Audio & Resume Position Switches
                            VideoSettingsSwitchRow(
                                title = "Background Audio Playback",
                                subtitle = "Keep playing music when screen is off or switching apps",
                                checked = preferences.backgroundPlaybackEnabled,
                                onCheckedChange = onToggleBackgroundPlayback,
                                testTag = "music_settings_background_playback_switch"
                            )
                            VideoSettingsSwitchRow(
                                title = "Resume Last Playback Position",
                                subtitle = "Automatically restore saved timestamp when replaying tracks",
                                checked = preferences.resumePlaybackEnabled,
                                onCheckedChange = onToggleResumePlayback,
                                testTag = "music_settings_resume_playback_switch"
                            )
                        }
                    }

                    // Footer Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ReferenceActiveGreen,
                                contentColor = Color(0xFF041A0E)
                            ),
                            modifier = Modifier.testTag("music_settings_done_button")
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoopPillSegment(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) ReferenceActiveGreen else Color.Transparent
    val contentColor = if (selected) Color(0xFF041A0E) else ReferenceNormalText

    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EditPlayerLayoutDialog(
    preferences: UserPreferencesState,
    onToggleWaveform: (Boolean) -> Unit,
    onToggleQuickActionRow: (Boolean) -> Unit,
    onToggleRemainingTime: (Boolean) -> Unit,
    onToggleAbAndBookmarkStrip: (Boolean) -> Unit,
    onToggleBackgroundPlayback: (Boolean) -> Unit,
    onToggleResumePlayback: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ReferencePanelCharcoal,
        titleContentColor = ReferenceNormalText,
        textContentColor = ReferenceNormalText,
        title = {
            Text(
                text = "Edit Player Interface",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("edit_player_layout_dialog"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoSettingsSwitchRow(
                    title = "Live Acoustic Waveform",
                    subtitle = "Show animated frequency bars beneath album artwork",
                    checked = preferences.playerShowWaveform,
                    onCheckedChange = onToggleWaveform,
                    testTag = "edit_player_waveform_switch"
                )
                VideoSettingsSwitchRow(
                    title = "Quick-Action Control Strip",
                    subtitle = "Show Speed, Equalizer, A-B Repeat & Bookmark shortcuts on player",
                    checked = preferences.playerShowQuickActionRow,
                    onCheckedChange = onToggleQuickActionRow,
                    testTag = "edit_player_quick_row_switch"
                )
                VideoSettingsSwitchRow(
                    title = "Remaining Track Duration",
                    subtitle = "Display countdown time (-MM:SS) next to total duration",
                    checked = preferences.playerShowRemainingTime,
                    onCheckedChange = onToggleRemainingTime,
                    testTag = "edit_player_remaining_time_switch"
                )
                VideoSettingsSwitchRow(
                    title = "Active A-B & Bookmark Markers",
                    subtitle = "Show saved timestamp chips on the main player screen",
                    checked = preferences.playerShowAbAndBookmarkStrip,
                    onCheckedChange = onToggleAbAndBookmarkStrip,
                    testTag = "edit_player_markers_switch"
                )
                VideoSettingsSwitchRow(
                    title = "Background Playback",
                    subtitle = "Continue playing audio when app is minimized",
                    checked = preferences.backgroundPlaybackEnabled,
                    onCheckedChange = onToggleBackgroundPlayback,
                    testTag = "edit_player_bg_switch"
                )
                VideoSettingsSwitchRow(
                    title = "Resume Track Position",
                    subtitle = "Restore last timestamp on long tracks & podcasts",
                    checked = preferences.resumePlaybackEnabled,
                    onCheckedChange = onToggleResumePlayback,
                    testTag = "edit_player_resume_switch"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ReferenceActiveGreen,
                    contentColor = Color(0xFF041A0E)
                ),
                modifier = Modifier.testTag("edit_player_done_button")
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun LyricsHelpTutorialDialog(
    onOpenLyricsLoader: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ReferencePanelCharcoal,
        titleContentColor = ReferenceNormalText,
        textContentColor = ReferenceNormalText,
        title = {
            Text(
                text = "Synchronized Lyrics & Tutorials",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("lyrics_help_tutorial_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "1. Automatic Companion Matching",
                    fontWeight = FontWeight.SemiBold,
                    color = ReferenceActiveGreen
                )
                Text(
                    text = "Place a .srt or .vtt file in the same folder with the exact same filename as your audio track (e.g., Song.mp3 and Song.srt) to auto-sync lyrics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReferenceSecondaryText
                )
                Text(
                    text = "2. Manual SAF Lyrics Import",
                    fontWeight = FontWeight.SemiBold,
                    color = ReferenceActiveGreen
                )
                Text(
                    text = "Tap 'Lyrics' in Quick Settings to load any external .SRT or .VTT file from device storage and fine-tune sync offset (-5000ms to +5000ms) or font scale.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReferenceSecondaryText
                )
                Text(
                    text = "3. A-B Repeat & Bookmarks",
                    fontWeight = FontWeight.SemiBold,
                    color = ReferenceActiveGreen
                )
                Text(
                    text = "Tap 'A-B Repeat' once at the start of a verse (Point A) and again at the end (Point B) to loop that section seamlessly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReferenceSecondaryText
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onOpenLyricsLoader,
                modifier = Modifier.testTag("lyrics_help_open_loader_button")
            ) {
                Text("Load Lyrics File", color = ReferenceActiveGreen)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ReferenceActiveGreen,
                    contentColor = Color(0xFF041A0E)
                ),
                modifier = Modifier.testTag("lyrics_help_close_button")
            ) {
                Text("Got It", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun PlayerFeedbackDialog(
    currentTrackTitle: String?,
    onSubmit: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by rememberSaveable { mutableStateOf("Audio Quality") }
    var feedbackNotes by rememberSaveable { mutableStateOf("") }
    val categories = listOf("Audio Quality", "Lyrics Sync", "Equalizer DSP", "UI & Controls")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ReferencePanelCharcoal,
        titleContentColor = ReferenceNormalText,
        textContentColor = ReferenceNormalText,
        title = {
            Text(
                text = "Send Player Feedback",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("player_feedback_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Track: ${currentTrackTitle ?: "None"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ReferenceSecondaryText
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { cat ->
                        val selected = selectedCategory == cat
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategory = cat },
                            label = {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    color = if (selected) Color(0xFF041A0E) else ReferenceNormalText
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ReferenceActiveGreen,
                                containerColor = ReferencePanelSurface
                            ),
                            modifier = Modifier.testTag("feedback_cat_${cat.lowercase().replace(" ", "_")}")
                        )
                    }
                }
                OutlinedTextField(
                    value = feedbackNotes,
                    onValueChange = { feedbackNotes = it },
                    label = { Text("Describe your experience or suggestion") },
                    singleLine = false,
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("feedback_message_input")
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ReferenceSecondaryText)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedCategory, feedbackNotes) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ReferenceActiveGreen,
                    contentColor = Color(0xFF041A0E)
                ),
                modifier = Modifier.testTag("feedback_send_button")
            ) {
                Text("Send Feedback", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun CustomizeThemeDialog(
    preferences: UserPreferencesState,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ReferencePanelCharcoal,
        titleContentColor = ReferenceNormalText,
        textContentColor = ReferenceNormalText,
        title = {
            Text(
                text = "Customize Player Theme",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("customize_theme_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Appearance Mode",
                    style = MaterialTheme.typography.labelLarge,
                    color = ReferenceActiveGreen
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        val selected = preferences.themeMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onSetThemeMode(mode) },
                            label = {
                                Text(
                                    text = mode.name,
                                    fontSize = 11.sp,
                                    color = if (selected) Color(0xFF041A0E) else ReferenceNormalText,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ReferenceActiveGreen,
                                containerColor = ReferencePanelSurface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_mode_chip_${mode.name.lowercase()}")
                        )
                    }
                }
                VideoSettingsSwitchRow(
                    title = "Material You Dynamic Wallpaper Accent",
                    subtitle = "Disable to lock signature PulsePlay Neon Green (#00E676) studio palette",
                    checked = preferences.dynamicColor,
                    onCheckedChange = onSetDynamicColor,
                    testTag = "theme_dynamic_color_switch"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ReferenceActiveGreen,
                    contentColor = Color(0xFF041A0E)
                ),
                modifier = Modifier.testTag("customize_theme_done_button")
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    )
}



