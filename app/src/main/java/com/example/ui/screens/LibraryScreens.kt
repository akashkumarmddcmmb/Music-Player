package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
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
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.data.local.MediaType
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
    onOpenStudioClick: () -> Unit,
    showActionChips: Boolean = false,
    isCompactHeight: Boolean = false,
    showNowPlayingPanelToggle: Boolean = false,
    isNowPlayingPanelOpen: Boolean = false,
    onToggleNowPlayingPanel: () -> Unit = {}
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var userExpandedActionChips by rememberSaveable { mutableStateOf(false) }
    val effectiveShowActionChips = showActionChips || userExpandedActionChips

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 16.dp,
                vertical = if (isCompactHeight) 4.dp else 8.dp
            )
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_input"),
            placeholder = {
                Text(
                    text = "Search songs, videos, artists, folders...",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search")
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_search_button")
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                    if (!showActionChips) {
                        IconButton(
                            onClick = { userExpandedActionChips = !userExpandedActionChips },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("header_quick_actions_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = if (userExpandedActionChips) "Hide quick tools" else "Show quick tools",
                                tint = if (userExpandedActionChips) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sort_menu_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort library (${currentSort.label})")
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (option == currentSort) "✓ ${option.label}" else option.label,
                                            fontWeight = if (option == currentSort) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    },
                                    onClick = {
                                        onSortChange(option)
                                        sortMenuExpanded = false
                                    },
                                    modifier = Modifier.testTag("sort_option_${option.name.lowercase()}")
                                )
                            }
                        }
                    }
                    if (showNowPlayingPanelToggle) {
                        IconButton(
                            onClick = onToggleNowPlayingPanel,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("header_toggle_now_playing_panel_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ViewSidebar,
                                contentDescription = if (isNowPlayingPanelOpen) "Hide Now Playing panel" else "Show Now Playing panel",
                                tint = if (isNowPlayingPanelOpen) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        if (effectiveShowActionChips) {
            Spacer(modifier = Modifier.height(if (isCompactHeight) 4.dp else 8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    AssistChip(
                        onClick = onPickSafFileClick,
                        label = { Text("Open File (SAF)", maxLines = 1) },
                        leadingIcon = {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.testTag("saf_open_file_chip")
                    )
                }
                item {
                    AssistChip(
                        onClick = onOpenStudioClick,
                        label = { Text("Media Studio & Converter", maxLines = 1) },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.testTag("open_studio_chip")
                    )
                }
                item {
                    AssistChip(
                        onClick = onOpenEqualizerClick,
                        label = { Text("Equalizer DSP", maxLines = 1) },
                        leadingIcon = {
                            Icon(Icons.Default.Equalizer, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.testTag("open_equalizer_chip")
                    )
                }
                item {
                    AssistChip(
                        onClick = onRescanClick,
                        label = { Text("Rescan", maxLines = 1) },
                        leadingIcon = {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.testTag("rescan_library_chip")
                    )
                }
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
    onNavigateToFolder: (String?) -> Unit = {},
    onPlayMedia: (MediaEntity, List<MediaEntity>) -> Unit,
    onToggleFavorite: (MediaEntity) -> Unit,
    onAddToPlaylistRequest: (MediaEntity) -> Unit,
    onOpenStudioForMedia: (MediaEntity?) -> Unit,
    onOpenEqualizer: () -> Unit,
    onShareMedia: (MediaEntity) -> Unit = {},
    onSetRingtoneRequest: (MediaEntity) -> Unit = {},
    onShowFileInfoRequest: (MediaEntity) -> Unit = {}
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

        if (!hasMediaPermission) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_permission_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Scan All Phone Audio & Video Folders",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Allow storage access so all songs, videos, and folders on your device appear in Categories and Library.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = onRequestPermissionClick,
                            modifier = Modifier.testTag("home_grant_permission_button")
                        ) {
                            Text("Allow & Scan")
                        }
                    }
                }
            }
        }

        // Adaptive Category Dashboard Cards (4-column row in wide landscape, 2x2 grid in portrait/compact)
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val useFourColumnRow = maxWidth >= 560.dp
                if (useFourColumnRow) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_categories_row_wide"),
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
                } else {
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
                    text = "Media Library (${allMedia.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onGenerateSamplesClick,
                    modifier = Modifier.testTag("regenerate_samples_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh", maxLines = 1)
                }
            }
        }

        if (allMedia.isEmpty()) {
            item {
                EmptyLibraryPlaceholder(
                    title = "No Media Indexed Yet",
                    subtitle = "Tap 'Scan & Refresh' to scan all audio and video folders on your phone or open a file via SAF.",
                    onActionClick = onGenerateSamplesClick,
                    actionLabel = "Scan & Refresh Media"
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
                    onOpenInStudioClick = { onOpenStudioForMedia(media) },
                    onShareClick = { onShareMedia(media) },
                    onSetRingtoneClick = { onSetRingtoneRequest(media) },
                    onFileInfoClick = { onShowFileInfoRequest(media) }
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
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private enum class CatalogViewMode {
    ALL,
    FOLDERS,
    FAVORITES
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
    onPickSafFileClick: () -> Unit,
    hasMediaPermission: Boolean = true,
    onRequestPermissionClick: () -> Unit = {},
    onRescanClick: () -> Unit = {},
    searchQuery: String = "",
    onClearSearch: () -> Unit = {},
    onShareMedia: (MediaEntity) -> Unit = {},
    onSetRingtoneRequest: (MediaEntity) -> Unit = {},
    onShowFileInfoRequest: (MediaEntity) -> Unit = {}
) {
    var viewMode by rememberSaveable { mutableStateOf(CatalogViewMode.ALL) }
    var selectedFolderInCatalog by rememberSaveable { mutableStateOf<String?>(null) }

    val favCount = remember(mediaList, favoriteUris) {
        mediaList.count { favoriteUris.contains(it.uriString) }
    }
    val groupedFolders = remember(mediaList) {
        mediaList.groupBy { it.folderName.ifBlank { "Local Storage" } }
    }
    val displayedMedia = remember(mediaList, favoriteUris, viewMode, selectedFolderInCatalog) {
        when (viewMode) {
            CatalogViewMode.FAVORITES -> mediaList.filter { favoriteUris.contains(it.uriString) }
            CatalogViewMode.FOLDERS -> {
                if (selectedFolderInCatalog != null) {
                    groupedFolders[selectedFolderInCatalog].orEmpty()
                } else {
                    mediaList
                }
            }
            CatalogViewMode.ALL -> mediaList
        }
    }

    val hasSearch = searchQuery.isNotEmpty()
    val hasFolderDetail = viewMode == CatalogViewMode.FOLDERS && selectedFolderInCatalog != null
    val isNonDefaultMode = viewMode != CatalogViewMode.ALL
    BackHandler(enabled = hasSearch || hasFolderDetail || isNonDefaultMode) {
        when {
            hasSearch -> onClearSearch()
            hasFolderDetail -> selectedFolderInCatalog = null
            isNonDefaultMode -> viewMode = CatalogViewMode.ALL
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("media_catalog_list"),
        contentPadding = PaddingValues(bottom = 120.dp, start = 16.dp, end = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                TextButton(
                    onClick = onRescanClick,
                    modifier = Modifier.testTag("catalog_rescan_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan")
                }
            }
        }

        if (!hasMediaPermission) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
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
                                text = "Grant Storage Permission",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Scan all device folders to show every audio & video file here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = onRequestPermissionClick) {
                            Text("Allow")
                        }
                    }
                }
            }
        }

        if (mediaList.isNotEmpty()) {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = viewMode == CatalogViewMode.ALL,
                            onClick = {
                                viewMode = CatalogViewMode.ALL
                                selectedFolderInCatalog = null
                            },
                            label = { Text("All (${mediaList.size})") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = viewMode == CatalogViewMode.FOLDERS,
                            onClick = {
                                viewMode = CatalogViewMode.FOLDERS
                                selectedFolderInCatalog = null
                            },
                            label = { Text("Folders (${groupedFolders.size})") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    item {
                        FilterChip(
                            selected = viewMode == CatalogViewMode.FAVORITES,
                            onClick = {
                                viewMode = CatalogViewMode.FAVORITES
                                selectedFolderInCatalog = null
                            },
                            label = { Text("Favorites ($favCount)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        if (viewMode == CatalogViewMode.FOLDERS && selectedFolderInCatalog == null) {
            if (groupedFolders.isEmpty()) {
                item {
                    EmptyLibraryPlaceholder(
                        title = emptyTitle,
                        subtitle = emptySubtitle,
                        onActionClick = onPickSafFileClick,
                        actionLabel = "Open Local File (SAF)"
                    )
                }
            } else {
                items(groupedFolders.entries.toList(), key = { it.key }) { (folderName, itemsInFolder) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFolderInCatalog = folderName }
                            .testTag("catalog_folder_card_$folderName"),
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
                                    text = "${itemsInFolder.size} files",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else {
            if (viewMode == CatalogViewMode.FOLDERS && selectedFolderInCatalog != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Folder: $selectedFolderInCatalog (${displayedMedia.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { selectedFolderInCatalog = null }) {
                            Text("All Folders", maxLines = 1)
                        }
                    }
                }
            }

            if (displayedMedia.isEmpty()) {
                item {
                    EmptyLibraryPlaceholder(
                        title = if (viewMode == CatalogViewMode.FAVORITES) "No Favorites Found" else emptyTitle,
                        subtitle = if (viewMode == CatalogViewMode.FAVORITES) "Tap the heart icon on any file to bookmark it as favorite." else emptySubtitle,
                        onActionClick = if (viewMode == CatalogViewMode.FAVORITES) { { viewMode = CatalogViewMode.ALL } } else onPickSafFileClick,
                        actionLabel = if (viewMode == CatalogViewMode.FAVORITES) "View All Media" else "Open Local File (SAF)"
                    )
                }
            } else {
                items(displayedMedia, key = { it.uriString }) { item ->
                    MediaItemCard(
                        media = item,
                        isCurrentlyPlaying = currentlyPlayingUri == item.uriString,
                        isFavorite = favoriteUris.contains(item.uriString),
                        onPlayClick = { onPlayMedia(item, displayedMedia) },
                        onFavoriteToggle = { onToggleFavorite(item) },
                        onAddToPlaylistClick = { onAddToPlaylistRequest(item) },
                        onOpenInStudioClick = { onOpenStudioForMedia(item) },
                        onShareClick = { onShareMedia(item) },
                        onSetRingtoneClick = { onSetRingtoneRequest(item) },
                        onFileInfoClick = { onShowFileInfoRequest(item) }
                    )
                }
            }
        }
    }
}

enum class FavoriteTypeFilter {
    ALL,
    AUDIO,
    VIDEO
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
    onClearHistoryOnly: () -> Unit,
    hasMediaPermission: Boolean = true,
    onRequestPermissionClick: () -> Unit = {},
    onRescanClick: () -> Unit = {},
    canPopSubTab: Boolean = false,
    onPopSubTab: () -> Unit = {},
    searchQuery: String = "",
    onClearSearch: () -> Unit = {},
    onShareMedia: (MediaEntity) -> Unit = {},
    onSetRingtoneRequest: (MediaEntity) -> Unit = {},
    onShowFileInfoRequest: (MediaEntity) -> Unit = {}
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistDesc by remember { mutableStateOf("") }
    var selectedFavoriteFilter by rememberSaveable { mutableStateOf(FavoriteTypeFilter.ALL) }
    var selectedFolderMediaFilter by rememberSaveable { mutableStateOf(FavoriteTypeFilter.ALL) }
    val favoriteAudio = remember(favoriteMedia) {
        favoriteMedia.filter { it.mediaType == MediaType.AUDIO }
    }
    val favoriteVideos = remember(favoriteMedia) {
        favoriteMedia.filter { it.mediaType == MediaType.VIDEO }
    }

    val hasPlaylistDetail = selectedPlaylistId != null
    val hasAlbumDetail = selectedAlbum != null
    val hasArtistDetail = selectedArtist != null
    val hasFolderDetail = selectedFolder != null
    val hasFolderMediaFilter = selectedSubTab == LibrarySubTab.FOLDERS && selectedFolderMediaFilter != FavoriteTypeFilter.ALL
    val hasFavoriteFilter = selectedSubTab == LibrarySubTab.FAVORITES && selectedFavoriteFilter != FavoriteTypeFilter.ALL
    val hasSearch = searchQuery.isNotEmpty()

    BackHandler(
        enabled = showCreatePlaylistDialog ||
            hasSearch ||
            hasPlaylistDetail ||
            hasAlbumDetail ||
            hasArtistDetail ||
            hasFolderDetail ||
            hasFolderMediaFilter ||
            hasFavoriteFilter ||
            canPopSubTab
    ) {
        when {
            showCreatePlaylistDialog -> showCreatePlaylistDialog = false
            hasSearch -> onClearSearch()
            hasPlaylistDetail -> onSelectPlaylist(null)
            hasAlbumDetail -> onSelectAlbum(null)
            hasArtistDetail -> onSelectArtist(null)
            hasFolderDetail -> onSelectFolder(null)
            hasFolderMediaFilter -> selectedFolderMediaFilter = FavoriteTypeFilter.ALL
            hasFavoriteFilter -> selectedFavoriteFilter = FavoriteTypeFilter.ALL
            canPopSubTab -> onPopSubTab()
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Local Playlist") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_playlist_name_input")
                    )
                    OutlinedTextField(
                        value = newPlaylistDesc,
                        onValueChange = { newPlaylistDesc = it },
                        label = { Text("Description (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
                            onOpenInStudioClick = { onOpenStudioForMedia(media) },
                            onShareClick = { onShareMedia(media) },
                            onSetRingtoneClick = { onSetRingtoneRequest(media) },
                            onFileInfoClick = { onShowFileInfoRequest(media) }
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
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onSelectAlbum(null) }) {
                                    Text("All Albums", maxLines = 1)
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
                                onOpenInStudioClick = { onOpenStudioForMedia(media) },
                                onShareClick = { onShareMedia(media) },
                                onSetRingtoneClick = { onSetRingtoneRequest(media) },
                                onFileInfoClick = { onShowFileInfoRequest(media) }
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
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onSelectArtist(null) }) {
                                    Text("All Artists", maxLines = 1)
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
                                onOpenInStudioClick = { onOpenStudioForMedia(media) },
                                onShareClick = { onShareMedia(media) },
                                onSetRingtoneClick = { onSetRingtoneRequest(media) },
                                onFileInfoClick = { onShowFileInfoRequest(media) }
                            )
                        }
                    }
                }

                LibrarySubTab.FOLDERS -> {
                    val filteredForFolders = when (selectedFolderMediaFilter) {
                        FavoriteTypeFilter.ALL -> allMedia
                        FavoriteTypeFilter.AUDIO -> allMedia.filter { it.mediaType == MediaType.AUDIO }
                        FavoriteTypeFilter.VIDEO -> allMedia.filter { it.mediaType == MediaType.VIDEO }
                    }
                    val groupedByFolder = filteredForFolders.groupBy { it.folderName.ifBlank { "Local Storage" } }
                    val allFoldersCount = allMedia.groupBy { it.folderName.ifBlank { "Local Storage" } }.size
                    val audioFoldersCount = allMedia.filter { it.mediaType == MediaType.AUDIO }
                        .groupBy { it.folderName.ifBlank { "Local Storage" } }.size
                    val videoFoldersCount = allMedia.filter { it.mediaType == MediaType.VIDEO }
                        .groupBy { it.folderName.ifBlank { "Local Storage" } }.size

                    if (!hasMediaPermission) {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
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
                                            text = "Allow Storage Access for All Folders",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Grant permission to scan all audio and video folders on your device.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(onClick = onRequestPermissionClick) {
                                        Text("Allow")
                                    }
                                }
                            }
                        }
                    }

                    if (selectedFolder == null) {
                        item {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Local Media Folders (${groupedByFolder.size})",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = onRescanClick,
                                        modifier = Modifier.testTag("folders_rescan_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Scan", maxLines = 1)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        FilterChip(
                                            selected = selectedFolderMediaFilter == FavoriteTypeFilter.ALL,
                                            onClick = { selectedFolderMediaFilter = FavoriteTypeFilter.ALL },
                                            label = { Text("All ($allFoldersCount)", maxLines = 1) }
                                        )
                                    }
                                    item {
                                        FilterChip(
                                            selected = selectedFolderMediaFilter == FavoriteTypeFilter.AUDIO,
                                            onClick = { selectedFolderMediaFilter = FavoriteTypeFilter.AUDIO },
                                            label = { Text("Audio ($audioFoldersCount)", maxLines = 1) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.LibraryMusic,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                    item {
                                        FilterChip(
                                            selected = selectedFolderMediaFilter == FavoriteTypeFilter.VIDEO,
                                            onClick = { selectedFolderMediaFilter = FavoriteTypeFilter.VIDEO },
                                            label = { Text("Videos ($videoFoldersCount)", maxLines = 1) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Movie,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        items(groupedByFolder.entries.toList(), key = { it.key }) { (folderName, itemsInFolder) ->
                            val audioCount = itemsInFolder.count { it.mediaType == MediaType.AUDIO }
                            val videoCount = itemsInFolder.count { it.mediaType == MediaType.VIDEO }
                            val breakdownText = buildList {
                                if (audioCount > 0) add("$audioCount audio")
                                if (videoCount > 0) add("$videoCount video")
                                add("${itemsInFolder.size} media items")
                            }.joinToString(" • ")

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
                                            text = breakdownText,
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
                                    text = "Folder: $selectedFolder (${folderItems.size})",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onSelectFolder(null) }) {
                                    Text("All Folders", maxLines = 1)
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
                                onOpenInStudioClick = { onOpenStudioForMedia(media) },
                                onShareClick = { onShareMedia(media) },
                                onSetRingtoneClick = { onSetRingtoneRequest(media) },
                                onFileInfoClick = { onShowFileInfoRequest(media) }
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
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { showCreatePlaylistDialog = true },
                                    modifier = Modifier.testTag("create_playlist_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("New Playlist", maxLines = 1)
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
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { onSelectPlaylist(null) }) {
                                    Text("Back to Playlists", maxLines = 1)
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
                                onOpenInStudioClick = { onOpenStudioForMedia(media) },
                                onShareClick = { onShareMedia(media) },
                                onSetRingtoneClick = { onSetRingtoneRequest(media) },
                                onFileInfoClick = { onShowFileInfoRequest(media) }
                            )
                        }
                    }
                }

                LibrarySubTab.FAVORITES -> {
                    item {
                        Column {
                            Text(
                                text = "Favorite Media (${favoriteMedia.size})",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedFavoriteFilter == FavoriteTypeFilter.ALL,
                                        onClick = { selectedFavoriteFilter = FavoriteTypeFilter.ALL },
                                        label = { Text("All (${favoriteMedia.size})", maxLines = 1) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("favorite_filter_all")
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = selectedFavoriteFilter == FavoriteTypeFilter.AUDIO,
                                        onClick = { selectedFavoriteFilter = FavoriteTypeFilter.AUDIO },
                                        label = { Text("Audio (${favoriteAudio.size})", maxLines = 1) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.LibraryMusic,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("favorite_filter_audio")
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = selectedFavoriteFilter == FavoriteTypeFilter.VIDEO,
                                        onClick = { selectedFavoriteFilter = FavoriteTypeFilter.VIDEO },
                                        label = { Text("Videos (${favoriteVideos.size})", maxLines = 1) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Movie,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("favorite_filter_video")
                                    )
                                }
                            }
                        }
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
                        when (selectedFavoriteFilter) {
                            FavoriteTypeFilter.ALL -> {
                                if (favoriteAudio.isNotEmpty()) {
                                    item {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LibraryMusic,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Favorite Audio Songs (${favoriteAudio.size})",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    items(favoriteAudio, key = { it.uriString }) { media ->
                                        MediaItemCard(
                                            media = media,
                                            isCurrentlyPlaying = currentlyPlayingUri == media.uriString,
                                            isFavorite = true,
                                            onPlayClick = { onPlayMedia(media, favoriteAudio) },
                                            onFavoriteToggle = { onToggleFavorite(media) },
                                            onAddToPlaylistClick = { onAddToPlaylistRequest(media) },
                                            onOpenInStudioClick = { onOpenStudioForMedia(media) },
                                            onShareClick = { onShareMedia(media) },
                                            onSetRingtoneClick = { onSetRingtoneRequest(media) },
                                            onFileInfoClick = { onShowFileInfoRequest(media) }
                                        )
                                    }
                                }

                                if (favoriteVideos.isNotEmpty()) {
                                    item {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Movie,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Favorite Videos (${favoriteVideos.size})",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    items(favoriteVideos, key = { it.uriString }) { media ->
                                        MediaItemCard(
                                            media = media,
                                            isCurrentlyPlaying = currentlyPlayingUri == media.uriString,
                                            isFavorite = true,
                                            onPlayClick = { onPlayMedia(media, favoriteVideos) },
                                            onFavoriteToggle = { onToggleFavorite(media) },
                                            onAddToPlaylistClick = { onAddToPlaylistRequest(media) },
                                            onOpenInStudioClick = { onOpenStudioForMedia(media) },
                                            onShareClick = { onShareMedia(media) },
                                            onSetRingtoneClick = { onSetRingtoneRequest(media) },
                                            onFileInfoClick = { onShowFileInfoRequest(media) }
                                        )
                                    }
                                }
                            }

                            FavoriteTypeFilter.AUDIO -> {
                                if (favoriteAudio.isEmpty()) {
                                    item {
                                        EmptyLibraryPlaceholder(
                                            title = "No Favorite Audio Songs",
                                            subtitle = "Tap the heart icon on any music or audio track to add it here.",
                                            onActionClick = {},
                                            actionLabel = null
                                        )
                                    }
                                } else {
                                    items(favoriteAudio, key = { it.uriString }) { media ->
                                        MediaItemCard(
                                            media = media,
                                            isCurrentlyPlaying = currentlyPlayingUri == media.uriString,
                                            isFavorite = true,
                                            onPlayClick = { onPlayMedia(media, favoriteAudio) },
                                            onFavoriteToggle = { onToggleFavorite(media) },
                                            onAddToPlaylistClick = { onAddToPlaylistRequest(media) },
                                            onOpenInStudioClick = { onOpenStudioForMedia(media) },
                                            onShareClick = { onShareMedia(media) },
                                            onSetRingtoneClick = { onSetRingtoneRequest(media) },
                                            onFileInfoClick = { onShowFileInfoRequest(media) }
                                        )
                                    }
                                }
                            }

                            FavoriteTypeFilter.VIDEO -> {
                                if (favoriteVideos.isEmpty()) {
                                    item {
                                        EmptyLibraryPlaceholder(
                                            title = "No Favorite Videos",
                                            subtitle = "Tap the heart icon on any video to add it here.",
                                            onActionClick = {},
                                            actionLabel = null
                                        )
                                    }
                                } else {
                                    items(favoriteVideos, key = { it.uriString }) { media ->
                                        MediaItemCard(
                                            media = media,
                                            isCurrentlyPlaying = currentlyPlayingUri == media.uriString,
                                            isFavorite = true,
                                            onPlayClick = { onPlayMedia(media, favoriteVideos) },
                                            onFavoriteToggle = { onToggleFavorite(media) },
                                            onAddToPlaylistClick = { onAddToPlaylistRequest(media) },
                                            onOpenInStudioClick = { onOpenStudioForMedia(media) },
                                            onShareClick = { onShareMedia(media) },
                                            onSetRingtoneClick = { onSetRingtoneRequest(media) },
                                            onFileInfoClick = { onShowFileInfoRequest(media) }
                                        )
                                    }
                                }
                            }
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
