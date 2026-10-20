package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.MediaEntity
import com.example.ui.components.PersistentMiniPlayerBar
import com.example.ui.screens.EqualizerBottomSheet
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.LibraryAndFoldersScreen
import com.example.ui.screens.MediaCatalogScreen
import com.example.ui.screens.NowPlayingOverlayScreen
import com.example.ui.screens.ProfileAndSettingsScreen
import com.example.ui.screens.SearchAndActionHeader
import com.example.ui.screens.StudioEditorOverlayScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.PrimaryTab
import com.example.viewmodel.PulsePlayViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PulsePlayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle external VIEW intents for audio/* or video/* content URIs
        handleIncomingMediaIntent(intent)

        setContent {
            val prefs by viewModel.userPreferences.collectAsStateWithLifecycle()
            MyApplicationTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor
            ) {
                PulsePlayAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingMediaIntent(intent)
    }

    private fun handleIncomingMediaIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                viewModel.importSafUri(uri)
            }
        }
    }
}

private data class NavDestinationItem(
    val tab: PrimaryTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val primaryDestinations = listOf(
    NavDestinationItem(
        tab = PrimaryTab.HOME,
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "nav_tab_home"
    ),
    NavDestinationItem(
        tab = PrimaryTab.VIDEOS,
        label = "Videos",
        selectedIcon = Icons.Filled.Movie,
        unselectedIcon = Icons.Outlined.Movie,
        testTag = "nav_tab_videos"
    ),
    NavDestinationItem(
        tab = PrimaryTab.MUSIC,
        label = "Music",
        selectedIcon = Icons.Filled.LibraryMusic,
        unselectedIcon = Icons.Outlined.LibraryMusic,
        testTag = "nav_tab_music"
    ),
    NavDestinationItem(
        tab = PrimaryTab.LIBRARY,
        label = "Library",
        selectedIcon = Icons.Filled.FolderSpecial,
        unselectedIcon = Icons.Outlined.FolderSpecial,
        testTag = "nav_tab_library"
    ),
    NavDestinationItem(
        tab = PrimaryTab.SETTINGS,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_tab_settings"
    )
)

@Composable
fun PulsePlayAppRoot(viewModel: PulsePlayViewModel) {
    val context = LocalContext.current

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedLibSubTab by viewModel.selectedLibrarySubTab.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolderFilter.collectAsStateWithLifecycle()
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()

    val allMedia by viewModel.allMedia.collectAsStateWithLifecycle()
    val audioMedia by viewModel.audioMedia.collectAsStateWithLifecycle()
    val videoMedia by viewModel.videoMedia.collectAsStateWithLifecycle()
    val favoriteMedia by viewModel.favoriteMedia.collectAsStateWithLifecycle()
    val favoriteUris by viewModel.favoriteUriSet.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val history by viewModel.playbackHistory.collectAsStateWithLifecycle()
    val processingJobs by viewModel.processingJobs.collectAsStateWithLifecycle()

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val eqState by viewModel.equalizerState.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val hasMediaPermission by viewModel.hasMediaPermission.collectAsStateWithLifecycle()
    val permissionPermanentlyDenied by viewModel.permissionPermanentlyDenied.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    val isNowPlayingExpanded by viewModel.isNowPlayingExpanded.collectAsStateWithLifecycle()
    val isStudioSheetOpen by viewModel.isStudioSheetOpen.collectAsStateWithLifecycle()
    val studioTargetMedia by viewModel.studioTargetMedia.collectAsStateWithLifecycle()
    val isEqualizerSheetOpen by viewModel.isEqualizerSheetOpen.collectAsStateWithLifecycle()

    var pendingPlaylistAddMedia by remember { mutableStateOf<MediaEntity?>(null) }

    val playlistDetailItemsFlow = remember(selectedPlaylistId) {
        selectedPlaylistId?.let { viewModel.mediaRepository.observePlaylistItems(it) }
    }
    val playlistDetailItems by (playlistDetailItemsFlow?.collectAsStateWithLifecycle(emptyList())
        ?: remember { mutableStateOf(emptyList()) })

    // Runtime permission check on launch
    LaunchedEffect(Unit) {
        val requiredPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val granted = requiredPerms.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        viewModel.onPermissionResult(granted = granted)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        val anyGranted = resultMap.values.any { it }
        viewModel.onPermissionResult(granted = anyGranted, permanentlyDenied = !anyGranted)
    }

    val requestMediaPermissions = {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(perms)
    }

    // SAF Media Picker Launcher
    val safMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importSafUri(uri)
        }
    }

    // SAF Subtitle Picker Launcher (.srt / .vtt)
    val safSubtitleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadExternalSubtitleUri(uri)
        }
    }

    // Add to Playlist Dialog
    if (pendingPlaylistAddMedia != null) {
        val mediaToAdd = pendingPlaylistAddMedia!!
        AlertDialog(
            onDismissRequest = { pendingPlaylistAddMedia = null },
            title = { Text("Add '${mediaToAdd.title}' to Playlist") },
            text = {
                Column {
                    if (playlists.isEmpty()) {
                        Text(
                            "No playlists yet. Create a new playlist instantly:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.createNewPlaylist("My Studio Favorites", "Local playlist")
                            }
                        ) {
                            Text("Create 'My Studio Favorites'")
                        }
                    } else {
                        playlists.forEach { pl ->
                            TextButton(
                                onClick = {
                                    viewModel.addTrackToPlaylist(pl.playlistId, mediaToAdd)
                                    pendingPlaylistAddMedia = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("${pl.name} (${pl.itemCount} tracks)")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingPlaylistAddMedia = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Equalizer Bottom Sheet
    if (isEqualizerSheetOpen) {
        EqualizerBottomSheet(
            eqState = eqState,
            onDismiss = { viewModel.setEqualizerSheetOpen(false) },
            onSetEnabled = { viewModel.setEqualizerEnabled(it) },
            onSelectPreset = { viewModel.selectEqualizerPreset(it) },
            onBandLevelChange = { band, lvl -> viewModel.updateEqualizerBand(band, lvl) },
            onBassBoostChange = { viewModel.updateBassBoost(it) }
        )
    }

    // Full-screen Overlays: Studio Editor or Now Playing
    if (isStudioSheetOpen) {
        StudioEditorOverlayScreen(
            allMedia = allMedia,
            initialTargetMedia = studioTargetMedia,
            conversionProfiles = viewModel.conversionProfiles,
            processingJobs = processingJobs,
            onClose = { viewModel.closeStudioSheet() },
            onPreviewSegment = { media, startMs ->
                viewModel.playMedia(media, listOf(media))
                viewModel.seekTo(startMs)
            },
            onRunAudioTrim = { src, start, end, ext ->
                viewModel.runAudioTrimJob(src, start, end, ext)
            },
            onRunVideoTrimRotate = { src, start, end, rot, crop ->
                viewModel.runVideoTrimAndRotateJob(src, start, end, rot, crop)
            },
            onRunConversionOrExtraction = { src, profId, kbps, isExtract ->
                viewModel.runConversionOrExtractionJob(src, profId, kbps, isExtract)
            },
            onCancelActiveJob = { viewModel.cancelActiveProcessingJob() },
            onClearCompletedJobs = { viewModel.clearCompletedProcessingJobs() },
            onPlayExportedUri = { outUri ->
                val exported = allMedia.find { it.uriString == outUri }
                if (exported != null) {
                    viewModel.closeStudioSheet()
                    viewModel.playMedia(exported, allMedia)
                    viewModel.setNowPlayingExpanded(true)
                }
            }
        )
        return
    }

    if (isNowPlayingExpanded) {
        NowPlayingOverlayScreen(
            playbackState = playbackState,
            userPreferences = userPreferences,
            isFavorite = playbackState.currentMedia?.let { favoriteUris.contains(it.uriString) } ?: false,
            playerInstance = viewModel.getPlayerInstanceForVideoSurface(),
            onCollapse = { viewModel.setNowPlayingExpanded(false) },
            onPlayPause = { viewModel.togglePlayPause() },
            onSeekTo = { viewModel.seekTo(it) },
            onPrevious = { viewModel.skipToPrevious() },
            onNext = { viewModel.skipToNext() },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onCycleRepeat = { viewModel.cycleRepeatMode() },
            onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
            onCycleAspectMode = { viewModel.cycleVideoAspectMode() },
            onRotateVideo90 = { viewModel.rotateVideoView90Degrees() },
            onToggleFullscreen = { viewModel.setFullscreenVideo(it) },
            onBrightnessGestureDelta = { viewModel.adjustBrightnessGesture(it) },
            onVolumeGestureDelta = { viewModel.adjustVolumeGesture(it) },
            onPickSubtitleFileClick = {
                safSubtitleLauncher.launch(arrayOf("text/*", "application/x-subrip", "*/*"))
            },
            onToggleSubtitlesEnabled = { viewModel.setSubtitlesEnabled(it) },
            onSetSubtitleFontScale = { viewModel.setSubtitleFontScale(it) },
            onSetSubtitleOffsetMs = { viewModel.setSubtitleSyncOffsetMs(it) },
            onToggleFavorite = {
                playbackState.currentMedia?.let { viewModel.toggleFavorite(it) }
            },
            onOpenEqualizer = { viewModel.setEqualizerSheetOpen(true) },
            onOpenStudio = {
                viewModel.setNowPlayingExpanded(false)
                viewModel.openStudioForMedia(playbackState.currentMedia)
            },
            onSelectQueueItem = { item ->
                viewModel.playMedia(item, playbackState.queue)
            },
            onRemoveQueueItem = { idx ->
                viewModel.removeQueueItemAt(idx)
            },
            onRetryPlayback = { viewModel.retryCurrentPlayback() }
        )
        return
    }

    // Adaptive Layout using BoxWithConstraints (NavigationRail on >= 600.dp, NavigationBar on Compact)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    // Persistent Mini-Player ONLY when playback is active (Phase 3 requirement)
                    AnimatedVisibility(visible = playbackState.currentMedia != null) {
                        PersistentMiniPlayerBar(
                            playbackState = playbackState,
                            onExpandClick = { viewModel.setNowPlayingExpanded(true) },
                            onPlayPauseClick = { viewModel.togglePlayPause() },
                            onNextClick = { viewModel.skipToNext() }
                        )
                    }

                    if (!isExpandedScreen) {
                        NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                            primaryDestinations.forEach { dest ->
                                val selected = selectedTab == dest.tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectPrimaryTab(dest.tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("side_navigation_rail")
                    ) {
                        primaryDestinations.forEach { dest ->
                            val selected = selectedTab == dest.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectPrimaryTab(dest.tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Status Feedback Banner
                    AnimatedVisibility(visible = statusBanner != null) {
                        statusBanner?.let { msg ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .testTag("status_feedback_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { viewModel.dismissStatusBanner() }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Dismiss message"
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Search & Quick Action Header on Home, Videos, Music, and Library tabs
                    if (selectedTab != PrimaryTab.SETTINGS) {
                        SearchAndActionHeader(
                            searchQuery = searchQuery,
                            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                            currentSort = userPreferences.defaultSortOption,
                            onSortChange = { viewModel.setDefaultSortOption(it) },
                            onPickSafFileClick = {
                                safMediaLauncher.launch(arrayOf("audio/*", "video/*"))
                            },
                            onRescanClick = { viewModel.refreshLibrary() },
                            onOpenEqualizerClick = { viewModel.setEqualizerSheetOpen(true) },
                            onOpenStudioClick = { viewModel.openStudioForMedia(null) }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            PrimaryTab.HOME -> {
                                HomeDashboardScreen(
                                    allMedia = allMedia,
                                    audioMedia = audioMedia,
                                    videoMedia = videoMedia,
                                    recentHistory = history,
                                    favoriteUris = favoriteUris,
                                    currentlyPlayingUri = playbackState.currentMedia?.uriString,
                                    hasMediaPermission = hasMediaPermission,
                                    permissionPermanentlyDenied = permissionPermanentlyDenied,
                                    userPreferences = userPreferences,
                                    onRequestPermissionClick = requestMediaPermissions,
                                    onPickSafFileClick = {
                                        safMediaLauncher.launch(arrayOf("audio/*", "video/*"))
                                    },
                                    onGenerateSamplesClick = {
                                        viewModel.generateBuiltInSamplesExplicitly()
                                    },
                                    onPlayMedia = { item, queue ->
                                        viewModel.playMedia(item, queue)
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onAddToPlaylistRequest = { pendingPlaylistAddMedia = it },
                                    onOpenStudioForMedia = { viewModel.openStudioForMedia(it) },
                                    onOpenEqualizer = { viewModel.setEqualizerSheetOpen(true) }
                                )
                            }

                            PrimaryTab.VIDEOS -> {
                                MediaCatalogScreen(
                                    title = "Video Cinema Vault (${videoMedia.size})",
                                    subtitle = "Hardware-accelerated Media3 ExoPlayer with SRT/WebVTT subtitles, aspect controls & gesture telemetry",
                                    mediaList = videoMedia,
                                    favoriteUris = favoriteUris,
                                    currentlyPlayingUri = playbackState.currentMedia?.uriString,
                                    emptyTitle = "No Videos Found",
                                    emptySubtitle = "Open any MP4/MKV/WebM file via Storage Access Framework or generate the built-in 640x360 MP4 test reel.",
                                    onPlayMedia = { item, queue ->
                                        viewModel.playMedia(item, queue)
                                        viewModel.setNowPlayingExpanded(true)
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onAddToPlaylistRequest = { pendingPlaylistAddMedia = it },
                                    onOpenStudioForMedia = { viewModel.openStudioForMedia(it) },
                                    onPickSafFileClick = {
                                        safMediaLauncher.launch(arrayOf("video/*"))
                                    }
                                )
                            }

                            PrimaryTab.MUSIC -> {
                                MediaCatalogScreen(
                                    title = "Audio Studio Deck (${audioMedia.size})",
                                    subtitle = "Gapless queue, background MediaSessionService, multi-band Equalizer & lossless WAV/AAC editor",
                                    mediaList = audioMedia,
                                    favoriteUris = favoriteUris,
                                    currentlyPlayingUri = playbackState.currentMedia?.uriString,
                                    emptyTitle = "No Audio Tracks Found",
                                    emptySubtitle = "Open any local audio file via SAF or use the built-in synthesized studio WAV & M4A tracks.",
                                    onPlayMedia = { item, queue ->
                                        viewModel.playMedia(item, queue)
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onAddToPlaylistRequest = { pendingPlaylistAddMedia = it },
                                    onOpenStudioForMedia = { viewModel.openStudioForMedia(it) },
                                    onPickSafFileClick = {
                                        safMediaLauncher.launch(arrayOf("audio/*"))
                                    }
                                )
                            }

                            PrimaryTab.LIBRARY -> {
                                LibraryAndFoldersScreen(
                                    selectedSubTab = selectedLibSubTab,
                                    onSelectSubTab = { viewModel.selectLibrarySubTab(it) },
                                    allMedia = allMedia,
                                    selectedFolder = selectedFolder,
                                    onSelectFolder = { viewModel.selectFolderFilter(it) },
                                    playlists = playlists,
                                    selectedPlaylistId = selectedPlaylistId,
                                    playlistItems = playlistDetailItems,
                                    onSelectPlaylist = { viewModel.selectPlaylistDetail(it) },
                                    onCreatePlaylist = { name, desc ->
                                        viewModel.createNewPlaylist(name, desc)
                                    },
                                    onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                    favoriteMedia = favoriteMedia,
                                    favoriteUris = favoriteUris,
                                    historyItems = history,
                                    currentlyPlayingUri = playbackState.currentMedia?.uriString,
                                    onPlayMedia = { item, queue ->
                                        viewModel.playMedia(item, queue)
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onAddToPlaylistRequest = { pendingPlaylistAddMedia = it },
                                    onOpenStudioForMedia = { viewModel.openStudioForMedia(it) },
                                    onClearHistoryOnly = { viewModel.clearPlaybackHistoryOnly() }
                                )
                            }

                            PrimaryTab.SETTINGS -> {
                                ProfileAndSettingsScreen(
                                    userPreferences = userPreferences,
                                    hasMediaPermission = hasMediaPermission,
                                    historyCount = history.size,
                                    onSetThemeMode = { viewModel.setThemeMode(it) },
                                    onSetDynamicColor = { viewModel.setDynamicColor(it) },
                                    onSetDefaultSpeed = { viewModel.setDefaultPlaybackSpeedPref(it) },
                                    onSetResumePlayback = { viewModel.setResumePlaybackEnabled(it) },
                                    onSetRecordHistory = { viewModel.setRecordPlaybackHistory(it) },
                                    onSetSubtitlesEnabled = { viewModel.setSubtitlesEnabled(it) },
                                    onSetSubtitleScale = { viewModel.setSubtitleFontScale(it) },
                                    onSetDefaultSort = { viewModel.setDefaultSortOption(it) },
                                    onOpenEqualizerClick = { viewModel.setEqualizerSheetOpen(true) },
                                    onRequestMediaPermissionClick = requestMediaPermissions,
                                    onSetOptionalAdsConfig = { enabled, consent ->
                                        viewModel.setOptionalAdsConfig(enabled, consent)
                                    },
                                    onClearHistoryOnlyClick = { viewModel.clearPlaybackHistoryOnly() },
                                    onResetPreferencesClick = { viewModel.resetAllPreferences() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
