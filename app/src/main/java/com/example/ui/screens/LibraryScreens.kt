package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ads.OptionalAdManager
import com.example.data.local.MediaEntity
import com.example.data.local.PlaybackHistoryEntity
import com.example.data.local.PlaylistWithCount
import com.example.data.preferences.SortOption
import com.example.data.preferences.UserPreferencesState
import com.example.ui.components.MediaItemCard
import com.example.ui.components.formatDurationMillis
import com.example.ui.theme.FavoriteCardGradientEnd
import com.example.ui.theme.FavoriteCardGradientStart
import com.example.ui.theme.MusicCardGradientEnd
import com.example.ui.theme.MusicCardGradientStart
import com.example.ui.theme.PlaylistCardGradientEnd
import com.example.ui.theme.PlaylistCardGradientStart
import com.example.ui.theme.VideoCardGradientEnd
import com.example.ui.theme.VideoCardGradientStart
import com.example.viewmodel.LibrarySubTab

@Composable
fun SearchAndActionHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    currentSort: SortOption,
    onSortChange: (SortOption) -> Unit,
    onPickSafFileClick: () -> Unit,
    onRescanClick: () -> Unit,
    onOpenEqualizerClick: () -> Unit,
    onOpenStudioClick: () -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_input"),
            placeholder = { Text("Search songs, videos, artists, folders...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search")
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.testTag("clear_search_button")
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.testTag("sort_menu_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort library")
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        onSortChange(option)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                AssistChip(
                    onClick = onPickSafFileClick,
                    label = { Text("Open File (SAF)") },
                    leadingIcon = {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.testTag("saf_open_file_chip")
                )
            }
            item {
                AssistChip(
                    onClick = onOpenStudioClick,
                    label = { Text("Media Studio & Converter") },
                    leadingIcon = {
                        Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.testTag("open_studio_chip")
                )
            }
            item {
                AssistChip(
                    onClick = onOpenEqualizerClick,
                    label = { Text("Equalizer DSP") },
                    leadingIcon = {
                        Icon(Icons.Default.Equalizer, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.testTag("open_equalizer_chip")
                )
            }
            item {
                AssistChip(
                    onClick = onRescanClick,
                    label = { Text("Rescan") },
                    leadingIcon = {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.testTag("rescan_library_chip")
                )
            }
        }
    }
}

@Composable
fun HomeDashboardScreen(
    allMedia: List<MediaEntity>,
    audioMedia: List<MediaEntity>,
    videoMedia: List<MediaEntity>,
    playlistsCount: Int = 0,
    recentHistory: List<PlaybackHistoryEntity>,
    favoriteUris: Set<String>,
    currentlyPlayingUri: String?,
    hasMediaPermission: Boolean,
    permissionPermanentlyDenied: Boolean,
    userPreferences: UserPreferencesState,
    onRequestPermissionClick: () -> Unit,
    onPickSafFileClick: () -> Unit,
    onGenerateSamplesClick: () -> Unit,
    onNavigateToMusic: () -> Unit = {},
    onNavigateToVideos: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onPlayMedia: (MediaEntity, List<MediaEntity>) -> Unit,
    onToggleFavorite: (MediaEntity) -> Unit,
    onAddToPlaylistRequest: (MediaEntity) -> Unit,
    onOpenStudioForMedia: (MediaEntity?) -> Unit,
    onOpenEqualizer: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_dashboard_list"),
        contentPadding = PaddingValues(bottom = 120.dp, start = 16.dp, end = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Phase 14 Optional Local Sponsor Slot (Only rendered if explicitly enabled AND consented in Settings)
        if (OptionalAdManager.canShowOptionalTestBanner(
                moduleEnabledInSettings = userPreferences.optionalAdsModuleEnabled,
                explicitUserConsentGranted = userPreferences.optionalAdsConsentGranted
            )
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("optional_local_ad_slot")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TEST AD SLOT (OFFLINE LOCAL SANDBOX)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Zero network calls • Zero tracking IDs • Disabled by default",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // 2x2 Category Dashboard Cards (Inspired by PulsePlay Home Concept: Music, Videos, Playlists, Favorites)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeCategoryTile(
                        title = "Music",
                        subtitle = "${audioMedia.size} tracks",
                        icon = Icons.Default.LibraryMusic,
                        gradientColors = listOf(MusicCardGradientStart, MusicCardGradientEnd),
                        onClick = onNavigateToMusic,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_category_music")
                    )
                    HomeCategoryTile(
                        title = "Videos",
                        subtitle = "${videoMedia.size} videos",
                        icon = Icons.Default.Movie,
                        gradientColors = listOf(VideoCardGradientStart, VideoCardGradientEnd),
                        onClick = onNavigateToVideos,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_category_videos")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeCategoryTile(
                        title = "Playlists",
                        subtitle = "$playlistsCount sets",
                        icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                        gradientColors = listOf(PlaylistCardGradientStart, PlaylistCardGradientEnd),
                        onClick = onNavigateToPlaylists,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_category_playlists")
                    )
                    HomeCategoryTile(
                        title = "Favorites",
                        subtitle = "${favoriteUris.size} saved",
                        icon = Icons.Default.Favorite,
                        gradientColors = listOf(FavoriteCardGradientStart, FavoriteCardGradientEnd),
                        onClick = onNavigateToFavorites,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_category_favorites")
                    )
                }
            }
        }

        // Studio & Audio Tools Hub (Media Editor, Equalizer DSP, Audio Converter, Audio Cutter)
        item {
            Text(
                text = "Studio & Audio Tools",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenStudioForMedia(null) }
                        .testTag("quick_action_studio_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Media Editor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Trim, Rotate, Extract Audio & Convert",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenEqualizer() }
                        .testTag("quick_action_eq_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Equalizer DSP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "5-Band Biquad EQ & Bass Boost",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Recently Played / Continue Listening Section
        if (recentHistory.isNotEmpty()) {
            item {
                Text(
                    text = "Recently Played",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recentHistory.take(8), key = { it.uriString }) { hist ->
                        val matchingMedia = allMedia.find { it.uriString == hist.uriString }
                        Card(
                            modifier = Modifier
                                .width(196.dp)
                                .clickable(enabled = matchingMedia != null) {
                                    matchingMedia?.let { onPlayMedia(it, allMedia) }
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Resume @ ${formatDurationMillis(hist.lastPositionMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = hist.titleSnapshot,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Played ${hist.playCount}x",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // All Discovered & Studio Media Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Studio & Device Media (${allMedia.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = onGenerateSamplesClick,
                    modifier = Modifier.testTag("regenerate_samples_button")
                ) {
                    Text("Verify Samples")
                }
            }
        }

        if (allMedia.isEmpty()) {
            item {
                EmptyLibraryPlaceholder(
                    title = "No Media Indexed Yet",
                    subtitle = "Tap 'Verify Samples' to synthesize legal WAV/M4A/MP4 test media or open any local file via SAF.",
                    onActionClick = onGenerateSamplesClick,
                    actionLabel = "Generate Legal Studio Samples"
                )
            }
        } else {
            items(allMedia, key = { it.uriString }) { media ->
                MediaItemCard(
                    media = media,
                    isCurrentlyPlaying = currentlyPlayingUri == media.uriString,
                    isFavorite = favoriteUris.contains(media.uriString),
                    onPlayClick = { onPlayMedia(media, allMedia) },
                    onFavoriteToggle = { onToggleFavorite(media) },
                    onAddToPlaylistClick = { onAddToPlaylistRequest(media) },
                    onOpenInStudioClick = { onOpenStudioForMedia(media) }
                )
            }
        }
    }
}

@Composable
private fun HomeCategoryTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
fun MediaCatalogScreen(
    title: String,
    subtitle: String,
    mediaList: List<MediaEntity>,
    favoriteUris: Set<String>,
    currentlyPlayingUri: String?,
    emptyTitle: String,
    emptySubtitle: String,
    onPlayMedia: (MediaEntity, List<MediaEntity>) -> Unit,
    onToggleFavorite: (MediaEntity) -> Unit,
    onAddToPlaylistRequest: (MediaEntity) -> Unit,
    onOpenStudioForMedia: (MediaEntity) -> Unit,
    onPickSafFileClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("media_catalog_list"),
        contentPadding = PaddingValues(bottom = 120.dp, start = 16.dp, end = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (mediaList.isEmpty()) {
            item {
                EmptyLibraryPlaceholder(
                    title = emptyTitle,
                    subtitle = emptySubtitle,
                    onActionClick = onPickSafFileClick,
                    actionLabel = "Open Local File (SAF)"
                )
            }
        } else {
            items(mediaList, key = { it.uriString }) { item ->
                MediaItemCard(
                    media = item,
                    isCurrentlyPlaying = currentlyPlayingUri == item.uriString,
                    isFavorite = favoriteUris.contains(item.uriString),
                    onPlayClick = { onPlayMedia(item, mediaList) },
                    onFavoriteToggle = { onToggleFavorite(item) },
                    onAddToPlaylistClick = { onAddToPlaylistRequest(item) },
                    onOpenInStudioClick = { onOpenStudioForMedia(item) }
                )
            }
        }
    }
}

@Composable
fun LibraryAndFoldersScreen(
    selectedSubTab: LibrarySubTab,
    onSelectSubTab: (LibrarySubTab) -> Unit,
    allMedia: List<MediaEntity>,
    selectedFolder: String?,
    onSelectFolder: (String?) -> Unit,
    selectedAlbum: String? = null,
    onSelectAlbum: (String?) -> Unit = {},
    selectedArtist: String? = null,
    onSelectArtist: (String?) -> Unit = {},
    playlists: List<PlaylistWithCount>,
    selectedPlaylistId: Long?,
    playlistItems: List<MediaEntity>,
    onSelectPlaylist: (Long?) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    favoriteMedia: List<MediaEntity>,
    favoriteUris: Set<String>,
    historyItems: List<PlaybackHistoryEntity>,
    currentlyPlayingUri: String?,
    onPlayMedia: (MediaEntity, List<MediaEntity>) -> Unit,
    onToggleFavorite: (MediaEntity) -> Unit,
    onAddToPlaylistRequest: (MediaEntity) -> Unit,
    onOpenStudioForMedia: (MediaEntity?) -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onClearHistoryOnly: () -> Unit
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistDesc by remember { mutableStateOf("") }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Local Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier.testTag("new_playlist_name_input")
                    )
                    OutlinedTextField(
                        value = newPlaylistDesc,
                        onValueChange = { newPlaylistDesc = it },
                        label = { Text("Description (Optional)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName, newPlaylistDesc)
                            newPlaylistName = ""
                            newPlaylistDesc = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_playlist_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-navigation chips: All Songs, Albums, Artists, Folders, Playlists, Favorites, History, Studio Tools
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LibrarySubTab.entries) { tab ->
                FilterChip(
                    selected = selectedSubTab == tab,
                    onClick = {
                        onSelectSubTab(tab)
                        onSelectFolder(null)
                        onSelectAlbum(null)
                        onSelectArtist(null)
                        onSelectPlaylist(null)
                    },
                    label = { Text(tab.label) },
                    modifier = Modifier.testTag("library_subtab_${tab.name.lowercase()}")
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (selectedSubTab) {
                LibrarySubTab.ALL_SONGS -> {
                    item {
                        Text(
                            text = "All Media Tracks (${allMedia.size})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(allMedia, key = { it.uriString }) { media ->
                        MediaItemCard(
                            media = media,
                            isCurrentlyPlaying = currentlyPlayingUri == media.uriString,
                            isFavorite = favoriteUris.contains(media.uriString),
                            onPlayClick = { onPlayMedia(media, allMedia) },
                            onFavoriteToggle = { onToggleFavorite(media) },
                            onAddToPlaylistClick = { onAddToPlaylistRequest(media) },
                            onOpenInStudioClick = { onOpenStudioForMedia(media) }
                        )
                    }
                }

                LibrarySubTab.ALBUMS -> {
                    val groupedByAlbum = allMedia.groupBy { it.album.ifBlank { "Unknown Album" } }
                    if (selectedAlbum == null) {
                        item {
                            Text(
                                text = "Albums (${groupedByAlbum.size})",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(groupedByAlbum.entries.toList(), key = { it.key }) { (albumName, tracks) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectAlbum(albumName) }
                                    .testTag("album_card_$albumName"),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Album,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = albumName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${tracks.size} tracks • ${tracks.firstOrNull()?.artist ?: "Unknown Artist"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val albumTracks = groupedByAlbum[selectedAlbum].orEmpty()
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Album: $selectedAlbum",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { onSelectAlbum(null) }) {
                                    Text("All Albums")
                                }
                            }
                        }
                        items(albumTracks, key = { it.uriString }) { media ->
                            MediaItemCard(
                                media = media,
                                isCurrentlyPlaying = currentlyPlayingUri == media.uriString,
                                isFavorite = favoriteUris.contains(media.uriString),
                                onPlayClick = { onPlayMedia(media, albumTracks) },
                                onFavoriteToggle = { onToggleFavorite(media) },
                                onAddToPlaylistClick = { onAddToPlaylistRequest(media) },
                                onOpenInStudioClick = { onOpenStudioForMedia(media) }
                            )
                        }
                    }
                }

                LibrarySubTab.ARTISTS -> {
                    val groupedByArtist = allMedia.groupBy { it.artist.ifBlank { "Unknown Artist" } }
                    if (selectedArtist == null) {
                        item {
                            Text(
                                text = "Artists (${groupedByArtist.size})",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(groupedByArtist.entries.toList(), key = { it.key }) { (artistName, tracks) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectArtist(artistName) }
                                    .testTag("artist_card_$artistName"),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = artistName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${tracks.size} media items",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val artistTracks = groupedByArtist[selectedArtist].orEmpty()
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Artist: $selectedArtist",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { onSelectArtist(null) }) {
                                    Text("All Artists")
                                }
                            }
                        }
                        items(artistTracks, key = { it.uriString }) { media ->
                            MediaItemCard(
                                media = media,
                                isCurrentlyPlaying = currentlyPlayingUri == media.uriString,
                                isFavorite = favoriteUris.contains(media.uriString),
                                onPlayClick = { onPlayMedia(media, artistTracks) },
                                onFavoriteToggle = { onToggleFavorite(media) },
                                onAddToPlaylistClick = { onAddToPlaylistRequest(media) },
                                onOpenInStudioClick = { onOpenStudioForMedia(media) }
                            )
                        }
                    }
                }

                LibrarySubTab.FOLDERS -> {
                    val groupedByFolder = allMedia.groupBy { it.folderName }
                    if (selectedFolder == null) {
                        item {
                            Text(
                                text = "Local Media Folders (${groupedByFolder.size})",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(groupedByFolder.entries.toList(), key = { it.key }) { (folderName, itemsInFolder) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectFolder(folderName) }
                                    .testTag("folder_card_$folderName"),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folderName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${itemsInFolder.size} media items",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val folderItems = groupedByFolder[selectedFolder].orEmpty()
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Folder: $selectedFolder",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { onSelectFolder(null) }) {
                                    Text("All Folders")
                                }
                            }
                        }
                        items(folderItems, key = { it.uriString }) { media ->
                            MediaItemCard(
                                media = media,
                                isCurrentlyPlaying = currentlyPlayingUri == media.uriString,
                                isFavorite = favoriteUris.contains(media.uriString),
                                onPlayClick = { onPlayMedia(media, folderItems) },
                                onFavoriteToggle = { onToggleFavorite(media) },
                                onAddToPlaylistClick = { onAddToPlaylistRequest(media) },
                                onOpenInStudioClick = { onOpenStudioForMedia(media) }
                            )
                        }
                    }
                }

                LibrarySubTab.PLAYLISTS -> {
                    if (selectedPlaylistId == null) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "User Playlists (${playlists.size})",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Button(
                                    onClick = { showCreatePlaylistDialog = true },
                                    modifier = Modifier.testTag("create_playlist_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("New Playlist")
                                }
                            }
                        }

                        if (playlists.isEmpty()) {
                            item {
                                EmptyLibraryPlaceholder(
                                    title = "No Playlists Created",
                                    subtitle = "Create custom local playlists stored in Room SQLite with zero cloud sync.",
                                    onActionClick = { showCreatePlaylistDialog = true },
                                    actionLabel = "Create First Playlist"
                                )
                            }
                        } else {
                            items(playlists, key = { it.playlistId }) { pl ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectPlaylist(pl.playlistId) },
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pl.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${pl.itemCount} tracks • ${pl.description.ifBlank { "Local Playlist" }}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeletePlaylist(pl.playlistId) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete playlist"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Playlist Tracks (${playlistItems.size})",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { onSelectPlaylist(null) }) {
                                    Text("Back to Playlists")
                                }
                            }
                        }
                        items(playlistItems, key = { it.uriString }) { media ->
                            MediaItemCard(
                                media = media,
                                isCurrentlyPlaying = currentlyPlayingUri == media.uriString,
                                isFavorite = favoriteUris.contains(media.uriString),
                                onPlayClick = { onPlayMedia(media, playlistItems) },
                                onFavoriteToggle = { onToggleFavorite(media) },
                                onAddToPlaylistClick = { onAddToPlaylistRequest(media) },
                                onOpenInStudioClick = { onOpenStudioForMedia(media) }
                            )
                        }
                    }
                }

                LibrarySubTab.FAVORITES -> {
                    item {
                        Text(
                            text = "Favorite Media (${favoriteMedia.size})",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (favoriteMedia.isEmpty()) {
                        item {
                            EmptyLibraryPlaceholder(
                                title = "No Favorites Yet",
                                subtitle = "Tap the heart icon on any audio or video track to bookmark it locally.",
                                onActionClick = {},
                                actionLabel = null
                            )
                        }
                    } else {
                        items(favoriteMedia, key = { it.uriString }) { media ->
                            MediaItemCard(
                                media = media,
                                isCurrentlyPlaying = currentlyPlayingUri == media.uriString,
                                isFavorite = true,
                                onPlayClick = { onPlayMedia(media, favoriteMedia) },
                                onFavoriteToggle = { onToggleFavorite(media) },
                                onAddToPlaylistClick = { onAddToPlaylistRequest(media) },
                                onOpenInStudioClick = { onOpenStudioForMedia(media) }
                            )
                        }
                    }
                }

                LibrarySubTab.HISTORY -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Playback History (${historyItems.size})",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Clearing history removes database records only; never deletes media files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (historyItems.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = onClearHistoryOnly,
                                    modifier = Modifier.testTag("clear_history_subtab_button")
                                ) {
                                    Text("Clear History")
                                }
                            }
                        }
                    }
                    items(historyItems, key = { it.uriString }) { hist ->
                        val matching = allMedia.find { it.uriString == hist.uriString }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = matching != null) {
                                    matching?.let { onPlayMedia(it, allMedia) }
                                },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = hist.titleSnapshot,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Last position: ${formatDurationMillis(hist.lastPositionMs)} / ${formatDurationMillis(hist.durationMs)} • Played ${hist.playCount}x",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                LibrarySubTab.TOOLS -> {
                    item {
                        Text(
                            text = "PulsePlay Studio & DSP Tools",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Offline audio/video editing, format conversion, and real-time 5-band biquad DSP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        StudioToolFeatureCard(
                            title = "Audio Cutter & Trim Studio",
                            subtitle = "Sample-accurate RIFF WAVE & M4A audio trimming without modifying original files",
                            icon = Icons.Default.ContentCut,
                            badge = "EDITOR",
                            onClick = { onOpenStudioForMedia(null) }
                        )
                    }
                    item {
                        StudioToolFeatureCard(
                            title = "Audio & Video Format Converter",
                            subtitle = "Convert audio to 16-bit PCM WAV or AAC M4A, or extract audio tracks from video files",
                            icon = Icons.Default.Transform,
                            badge = "CONVERTER",
                            onClick = { onOpenStudioForMedia(null) }
                        )
                    }
                    item {
                        StudioToolFeatureCard(
                            title = "Video Trimmer & Orientation Remuxer",
                            subtitle = "Trim video ranges and write ISO MP4 orientation matrices (0° / 90° / 180° / 270°)",
                            icon = Icons.Default.Movie,
                            badge = "VIDEO LAB",
                            onClick = { onOpenStudioForMedia(null) }
                        )
                    }
                    item {
                        StudioToolFeatureCard(
                            title = "5-Band Biquad Equalizer & Bass Boost",
                            subtitle = "Real-time PCM16 cascade filter bank (60 Hz – 14 kHz) with soft-knee limiter",
                            icon = Icons.Default.GraphicEq,
                            badge = "LIVE DSP",
                            onClick = onOpenEqualizer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioToolFeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyLibraryPlaceholder(
    title: String,
    subtitle: String,
    onActionClick: () -> Unit,
    actionLabel: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AudioFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onActionClick) {
                    Text(actionLabel)
                }
            }
        }
    }
}
