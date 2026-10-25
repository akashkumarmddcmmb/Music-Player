package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.MediaEntity
import com.example.data.local.MediaType
import com.example.media.DetailedMediaFileInfo
import com.example.media.MediaFileActionsHelper
import com.example.media.PlayerHandoffAndVideoPolicy
import com.example.media.RingtoneOutcome
import com.example.media.ShareOutcome
import com.example.ui.components.MediaFileInformationDialog
import com.example.ui.components.PersistentMiniPlayerBar
import com.example.ui.components.RingtonePermissionDialog
import com.example.ui.screens.EqualizerBottomSheet
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.LibraryAndFoldersScreen
import com.example.ui.screens.MediaCatalogScreen
import com.example.ui.screens.NowPlayingOverlayScreen
import com.example.ui.screens.NowPlayingSidePanel
import com.example.ui.screens.ProfileAndSettingsScreen
import com.example.ui.screens.SearchAndActionHeader
import com.example.ui.screens.StudioEditorOverlayScreen
import com.example.ui.theme.PulsePlayTheme
import com.example.viewmodel.LibrarySubTab
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
            PulsePlayTheme(
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val playback = viewModel.playbackState.value
            val prefs = viewModel.userPreferences.value
            val isExpanded = viewModel.isNowPlayingExpanded.value
            val isStudioOpen = viewModel.isStudioSheetOpen.value
            val config = resources.configuration
            val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE ||
                config.screenWidthDp > config.screenHeightDp
            val shouldHide = PlayerHandoffAndVideoPolicy.shouldHideSystemBarsForVideo(
                isActivityResumed = true,
                isNowPlayingOverlayVisible = isExpanded && !isStudioOpen,
                isStudioSheetOpen = isStudioOpen,
                mediaType = playback.currentMedia?.mediaType,
                isFullscreenVideo = playback.isFullscreenVideo,
                isLandscapeViewport = isLandscape,
                orientationPreference = prefs.videoOrientationPreference
            )
            PlayerHandoffAndVideoPolicy.applyImmersiveSystemBars(window, shouldHide)
        }
    }

    private fun handleIncomingMediaIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                viewModel.importSafUri(uri)
            }
        }
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findHostActivity() }

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedLibSubTab by viewModel.selectedLibrarySubTab.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolderFilter.collectAsStateWithLifecycle()
    val selectedAlbum by viewModel.selectedAlbumFilter.collectAsStateWithLifecycle()
    val selectedArtist by viewModel.selectedArtistFilter.collectAsStateWithLifecycle()
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
    val videoSurfacePlayer by viewModel.videoSurfacePlayerFlow.collectAsStateWithLifecycle()
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
    var pendingRingtoneMedia by remember { mutableStateOf<MediaEntity?>(null) }
    var activeFileInfo by remember { mutableStateOf<DetailedMediaFileInfo?>(null) }
    var isActivityResumed by remember { mutableStateOf(true) }

    val applyRingtoneForMedia: (MediaEntity) -> Unit = { media ->
        when (val outcome = MediaFileActionsHelper.setAudioAsRingtone(context, media)) {
            is RingtoneOutcome.Success -> {
                pendingRingtoneMedia = null
                viewModel.postStatusBannerMessage(outcome.message)
            }
            is RingtoneOutcome.PermissionRequired -> {
                pendingRingtoneMedia = media
                viewModel.postStatusBannerMessage(outcome.message)
            }
            is RingtoneOutcome.Failure -> {
                pendingRingtoneMedia = null
                viewModel.postStatusBannerMessage(outcome.errorMessage)
            }
        }
    }

    val writeSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val pending = pendingRingtoneMedia
        if (pending != null && MediaFileActionsHelper.canWriteSystemSettings(context)) {
            applyRingtoneForMedia(pending)
        }
    }

    // Track Activity lifecycle for lifecycle-safe FLAG_KEEP_SCREEN_ON & ringtone permission return
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> {
                    isActivityResumed = true
                    viewModel.onAppForegrounded()
                    val pending = pendingRingtoneMedia
                    if (pending != null && MediaFileActionsHelper.canWriteSystemSettings(context)) {
                        applyRingtoneForMedia(pending)
                    }
                }
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> {
                    isActivityResumed = false
                    viewModel.onAppBackgrounded()
                    PlayerHandoffAndVideoPolicy.applyWindowKeepScreenOn(activity?.window, false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            PlayerHandoffAndVideoPolicy.applyWindowKeepScreenOn(activity?.window, false)
        }
    }

    // Lifecycle-safe Window FLAG_KEEP_SCREEN_ON management during active video playback
    val shouldKeepVideoScreenAwake = PlayerHandoffAndVideoPolicy.shouldKeepVideoScreenAwake(
        isActivityResumed = isActivityResumed,
        isNowPlayingOverlayVisible = isNowPlayingExpanded && !isStudioSheetOpen,
        mediaType = playbackState.currentMedia?.mediaType,
        isPlaying = playbackState.isPlaying,
        hasPlaybackError = playbackState.playbackError != null,
        keepScreenOnPreferenceEnabled = userPreferences.keepScreenOnDuringVideo
    )

    DisposableEffect(
        activity,
        shouldKeepVideoScreenAwake
    ) {
        PlayerHandoffAndVideoPolicy.applyWindowKeepScreenOn(
            window = activity?.window,
            shouldKeepAwake = shouldKeepVideoScreenAwake
        )
        onDispose {
            PlayerHandoffAndVideoPolicy.applyWindowKeepScreenOn(
                window = activity?.window,
                shouldKeepAwake = false
            )
        }
    }

    // Real Android Display Orientation & Immersive System Bars for Video Player
    val configuration = LocalConfiguration.current
    val isConfigurationLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp > configuration.screenHeightDp

    val targetOrientation = PlayerHandoffAndVideoPolicy.resolveRequestedScreenOrientation(
        isNowPlayingOverlayVisible = isNowPlayingExpanded && !isStudioSheetOpen,
        mediaType = playbackState.currentMedia?.mediaType,
        isFullscreenVideo = playbackState.isFullscreenVideo,
        orientationPreference = userPreferences.videoOrientationPreference
    )
    val shouldHideSystemBars = PlayerHandoffAndVideoPolicy.shouldHideSystemBarsForVideo(
        isActivityResumed = isActivityResumed,
        isNowPlayingOverlayVisible = isNowPlayingExpanded && !isStudioSheetOpen,
        isStudioSheetOpen = isStudioSheetOpen,
        mediaType = playbackState.currentMedia?.mediaType,
        isFullscreenVideo = playbackState.isFullscreenVideo,
        isLandscapeViewport = isConfigurationLandscape,
        orientationPreference = userPreferences.videoOrientationPreference
    )

    DisposableEffect(
        activity,
        targetOrientation,
        shouldHideSystemBars,
        isConfigurationLandscape,
        isNowPlayingExpanded,
        isStudioSheetOpen,
        playbackState.currentMedia?.mediaType
    ) {
        if (activity != null) {
            if (activity.requestedOrientation != targetOrientation) {
                activity.requestedOrientation = targetOrientation
            }
            PlayerHandoffAndVideoPolicy.applyImmersiveSystemBars(
                window = activity.window,
                hideSystemBars = shouldHideSystemBars
            )
        }
        onDispose {
            if (activity != null && (!isNowPlayingExpanded || playbackState.currentMedia?.mediaType != MediaType.VIDEO)) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                PlayerHandoffAndVideoPolicy.applyImmersiveSystemBars(
                    window = activity.window,
                    hideSystemBars = false
                )
            }
        }
    }

    val playlistDetailItemsFlow = remember(selectedPlaylistId) {
        selectedPlaylistId?.let { viewModel.mediaRepository.observePlaylistItems(it) }
    }
    val playlistDetailItems by (playlistDetailItemsFlow?.collectAsStateWithLifecycle(emptyList())
        ?: remember { mutableStateOf(emptyList()) })

    var hasPromptedPermissionOnLaunch by rememberSaveable { mutableStateOf(false) }
    var hasPromptedAllFilesOnLaunch by rememberSaveable { mutableStateOf(false) }

    val checkAnyMediaOrAllFilesGranted = {
        val allFilesGranted = runCatching {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                Environment.isExternalStorageManager()
        }.getOrDefault(false)
        val mediaPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val mediaGranted = mediaPerms.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        allFilesGranted || mediaGranted
    }

    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = checkAnyMediaOrAllFilesGranted()
        viewModel.onPermissionResult(granted = granted)
    }

    val launchAllFilesAccessSettings = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            runCatching {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                allFilesAccessLauncher.launch(intent)
            }.onFailure {
                runCatching {
                    val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    allFilesAccessLauncher.launch(fallback)
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        val anyGranted = resultMap.values.any { it } || checkAnyMediaOrAllFilesGranted()
        viewModel.onPermissionResult(granted = anyGranted, permanentlyDenied = !anyGranted)
        if (!anyGranted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager() &&
            !hasPromptedAllFilesOnLaunch
        ) {
            hasPromptedAllFilesOnLaunch = true
            launchAllFilesAccessSettings()
        }
    }

    val requestMediaPermissions = {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        permissionLauncher.launch(perms)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager() &&
            checkAnyMediaOrAllFilesGranted()
        ) {
            launchAllFilesAccessSettings()
        }
    }

    val triggerFullScanOrPermission = {
        if (!checkAnyMediaOrAllFilesGranted()) {
            requestMediaPermissions()
        }
        viewModel.refreshLibrary(showStatusFeedback = true)
    }

    // Request Notification and Media permissions on launch if not yet granted
    LaunchedEffect(Unit) {
        val requiredRuntimePerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        val mediaOrAllFilesGranted = checkAnyMediaOrAllFilesGranted()
        viewModel.onPermissionResult(granted = mediaOrAllFilesGranted)

        if (!mediaOrAllFilesGranted && !hasPromptedPermissionOnLaunch) {
            hasPromptedPermissionOnLaunch = true
            permissionLauncher.launch(requiredRuntimePerms)
        }
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
            title = {
                Text(
                    text = "Add '${mediaToAdd.title}' to Playlist",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
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
                                Text(
                                    text = "${pl.name} (${pl.itemCount} tracks)",
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
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

    // Ringtone Permission Continuation Dialog
    if (pendingRingtoneMedia != null) {
        val ringtoneTarget = pendingRingtoneMedia!!
        RingtonePermissionDialog(
            media = ringtoneTarget,
            onOpenSettings = {
                runCatching {
                    writeSettingsLauncher.launch(
                        MediaFileActionsHelper.buildManageWriteSettingsIntent(context)
                    )
                }.onFailure {
                    viewModel.postStatusBannerMessage(
                        "Unable to open Modify System Settings screen on this device."
                    )
                }
            },
            onContinueSetRingtone = {
                if (MediaFileActionsHelper.canWriteSystemSettings(context)) {
                    applyRingtoneForMedia(ringtoneTarget)
                } else {
                    viewModel.postStatusBannerMessage(
                        "Modify System Settings permission is not granted yet. Tap 'Open System Settings' to allow it."
                    )
                }
            },
            onDismiss = { pendingRingtoneMedia = null }
        )
    }

    // File Information / Details Dialog
    if (activeFileInfo != null) {
        val info = activeFileInfo!!
        MediaFileInformationDialog(
            info = info,
            onCopyLocation = { locationText ->
                if (MediaFileActionsHelper.copyTextToClipboard(context, "Media File Location", locationText)) {
                    viewModel.postStatusBannerMessage("Copied file location to clipboard.")
                }
            },
            onDismiss = { activeFileInfo = null }
        )
    }

    // Equalizer Bottom Sheet
    if (isEqualizerSheetOpen) {
        EqualizerBottomSheet(
            eqState = eqState,
            audioBalance = userPreferences.audioBalance,
            onDismiss = { viewModel.setEqualizerSheetOpen(false) },
            onSetEnabled = { viewModel.setEqualizerEnabled(it) },
            onSelectPreset = { viewModel.selectEqualizerPreset(it) },
            onBandLevelChange = { band, lvl -> viewModel.updateEqualizerBand(band, lvl) },
            onBassBoostChange = { viewModel.updateBassBoost(it) },
            onAudioBalanceChange = { viewModel.setAudioBalance(it) },
            onPreampGainChange = { viewModel.setPreampGainDb(it) }
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
            playerInstance = videoSurfacePlayer ?: viewModel.getPlayerInstanceForVideoSurface(),
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
            onToggleControlsLock = { viewModel.setVideoControlsLocked(it) },
            onSelectVideoOrientation = { viewModel.setVideoOrientationPreference(it) },
            onToggleKeepScreenOn = { viewModel.setKeepScreenOnDuringVideo(it) },
            onToggleVideoGestureControls = { viewModel.setVideoGestureControlsEnabled(it) },
            onBrightnessGestureDelta = { viewModel.adjustBrightnessGesture(it) },
            onVolumeGestureDelta = { viewModel.adjustVolumeGesture(it) },
            onPickSubtitleFileClick = {
                safSubtitleLauncher.launch(arrayOf("text/*", "application/x-subrip", "*/*"))
            },
            onToggleSubtitlesEnabled = { viewModel.setSubtitlesEnabled(it) },
            onSetSubtitleFontScale = { viewModel.setSubtitleFontScale(it) },
            onSetSubtitleOffsetMs = { viewModel.setSubtitleSyncOffsetMs(it) },
            onSetSleepTimerMinutes = { viewModel.setSleepTimerMinutes(it) },
            onSetSleepTimerEndOfTrack = { viewModel.setSleepTimerEndOfTrack(it) },
            onToggleFavorite = {
                playbackState.currentMedia?.let { viewModel.toggleFavorite(it) }
            },
            onOpenEqualizer = { viewModel.setEqualizerSheetOpen(true) },
            onOpenStudio = {
                viewModel.setNowPlayingExpanded(false)
                viewModel.openStudioForMedia(playbackState.currentMedia)
            },
            onShareCurrentMedia = {
                playbackState.currentMedia?.let { media ->
                    when (val outcome = MediaFileActionsHelper.shareMediaFile(context, media)) {
                        is ShareOutcome.Success -> {
                            viewModel.postStatusBannerMessage(
                                "Sharing '${media.title}' (${outcome.mimeType})"
                            )
                        }
                        is ShareOutcome.Failure -> {
                            viewModel.postStatusBannerMessage(outcome.errorMessage)
                        }
                    }
                }
            },
            onSetRingtoneCurrentMedia = {
                playbackState.currentMedia?.let { media ->
                    applyRingtoneForMedia(media)
                }
            },
            onAddToPlaylistCurrentMedia = {
                playbackState.currentMedia?.let { media ->
                    pendingPlaylistAddMedia = media
                }
            },
            onShowFileInfoCurrentMedia = {
                playbackState.currentMedia?.let { media ->
                    activeFileInfo = MediaFileActionsHelper.extractDetailedMediaFileInfo(context, media)
                }
            },
            onSelectQueueItem = { item ->
                viewModel.playMedia(item, playbackState.queue)
            },
            onRemoveQueueItem = { idx ->
                viewModel.removeQueueItemAt(idx)
            },
            onMoveQueueItem = { fromIdx, toIdx ->
                viewModel.moveQueueItem(fromIdx, toIdx)
            },
            onSaveQueueAsPlaylist = { playlistName ->
                viewModel.saveCurrentQueueAsPlaylist(playlistName)
            },
            onRetryPlayback = { viewModel.retryCurrentPlayback() },
            onSelectAudioTrack = { track -> viewModel.selectAudioTrack(track) },
            onVideoSurfaceAttached = { attached -> viewModel.onVideoSurfaceAttached(attached) }
        )
        return
    }

    // Step-by-step back navigation unwinding: dialogs -> search query -> primary tab backstack
    val canPopTab = viewModel.canPopPrimaryTab()
    val hasPendingDialog = pendingPlaylistAddMedia != null || pendingRingtoneMedia != null || activeFileInfo != null
    val hasSearch = searchQuery.isNotEmpty()

    BackHandler(enabled = hasPendingDialog || hasSearch || canPopTab) {
        when {
            activeFileInfo != null -> activeFileInfo = null
            pendingRingtoneMedia != null -> pendingRingtoneMedia = null
            pendingPlaylistAddMedia != null -> pendingPlaylistAddMedia = null
            hasSearch -> viewModel.updateSearchQuery("")
            canPopTab -> viewModel.popPrimaryTab()
        }
    }

    // Adaptive Layout using BoxWithConstraints:
    // - Left: Scrollable NavigationRail in Landscape or >= 600.dp width; Bottom NavigationBar in Portrait Compact
    // - Center: Search/Sort Header + Active Library/Content Screen + Bottom PersistentMiniPlayerBar (never covering list or nav)
    // - Right: Adaptive NowPlayingSidePanel when sufficient landscape width is available (or toggled by user)
    var userSidePanelOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        val useNavigationRail = isLandscape || maxWidth >= 600.dp
        val isCompactHeight = maxHeight < 480.dp
        val canFitSideNowPlayingPanel = isLandscape && maxWidth >= 560.dp
        val isWideLandscape = maxWidth >= 840.dp || (isLandscape && maxWidth >= 700.dp)
        val sidePanelTargetWidth = if (maxWidth >= 900.dp) 340.dp else 284.dp

        val showRightNowPlayingPanel = canFitSideNowPlayingPanel && (
            userSidePanelOverride ?: (isWideLandscape && playbackState.currentMedia != null)
        )

        val toggleSidePanelOrExpandPlayer: () -> Unit = {
            if (canFitSideNowPlayingPanel) {
                userSidePanelOverride = !showRightNowPlayingPanel
            } else {
                viewModel.setNowPlayingExpanded(true)
            }
        }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavigationRail) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        // In Portrait mode, Persistent Mini-Player sits directly above Bottom NavigationBar
                        AnimatedVisibility(visible = playbackState.currentMedia != null) {
                            PersistentMiniPlayerBar(
                                playbackState = playbackState,
                                onExpandClick = { viewModel.setNowPlayingExpanded(true) },
                                onPlayPauseClick = { viewModel.togglePlayPause() },
                                onNextClick = { viewModel.skipToNext() },
                                onPreviousClick = { viewModel.skipToPrevious() }
                            )
                        }

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
                                    label = {
                                        Text(
                                            text = dest.label,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    alwaysShowLabel = true,
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
                // 1. LEFT: Compact Scrollable NavigationRail in Landscape / Expanded Width
                if (useNavigationRail) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("side_navigation_rail")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                            horizontalAlignment = Alignment.CenterHorizontally
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
                                    label = {
                                        Text(
                                            text = dest.label,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    alwaysShowLabel = true,
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }

                // 2. CENTER: Main Content Area + Bottom Persistent Mini-Player (in Landscape/Rail mode)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Status Feedback Banner
                    AnimatedVisibility(visible = statusBanner != null) {
                        statusBanner?.let { msg ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .testTag("status_feedback_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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

                    // Search, Sort & Quick Action Header on Home, Videos, Music, and Library tabs
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
                            onOpenStudioClick = { viewModel.openStudioForMedia(null) },
                            showActionChips = !isCompactHeight,
                            isCompactHeight = isCompactHeight,
                            showNowPlayingPanelToggle = isLandscape,
                            isNowPlayingPanelOpen = showRightNowPlayingPanel,
                            onToggleNowPlayingPanel = toggleSidePanelOrExpandPlayer
                        )
                    }

                    val currentlyPlayingUri = playbackState.currentMedia?.uriString
                    val onPlayMediaCallback: (MediaEntity, List<MediaEntity>) -> Unit = remember(viewModel) {
                        { item, queue -> viewModel.playMedia(item, queue) }
                    }
                    val onPlayVideoCallback: (MediaEntity, List<MediaEntity>) -> Unit = remember(viewModel) {
                        { item, queue ->
                            viewModel.playMedia(item, queue)
                            viewModel.setNowPlayingExpanded(true)
                        }
                    }
                    val onToggleFavoriteCallback: (MediaEntity) -> Unit = remember(viewModel) {
                        { viewModel.toggleFavorite(it) }
                    }
                    val onAddToPlaylistRequestCallback: (MediaEntity) -> Unit = remember {
                        { pendingPlaylistAddMedia = it }
                    }
                    val onOpenStudioForMediaCallback: (MediaEntity?) -> Unit = remember(viewModel) {
                        { viewModel.openStudioForMedia(it) }
                    }
                    val onShareMediaCallback: (MediaEntity) -> Unit = remember(context, viewModel) {
                        { media ->
                            when (val outcome = MediaFileActionsHelper.shareMediaFile(context, media)) {
                                is ShareOutcome.Success -> {
                                    viewModel.postStatusBannerMessage(
                                        "Sharing '${media.title}' (${outcome.mimeType})"
                                    )
                                }
                                is ShareOutcome.Failure -> {
                                    viewModel.postStatusBannerMessage(outcome.errorMessage)
                                }
                            }
                        }
                    }
                    val onSetRingtoneRequestCallback: (MediaEntity) -> Unit = remember(context, viewModel) {
                        { media ->
                            applyRingtoneForMedia(media)
                        }
                    }
                    val onShowFileInfoRequestCallback: (MediaEntity) -> Unit = remember(context) {
                        { media ->
                            activeFileInfo = MediaFileActionsHelper.extractDetailedMediaFileInfo(context, media)
                        }
                    }
                    val onOpenEqualizerCallback: () -> Unit = remember(viewModel) {
                        { viewModel.setEqualizerSheetOpen(true) }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            PrimaryTab.HOME -> {
                                HomeDashboardScreen(
                                    allMedia = allMedia,
                                    audioMedia = audioMedia,
                                    videoMedia = videoMedia,
                                    playlistsCount = playlists.size,
                                    recentHistory = history,
                                    favoriteUris = favoriteUris,
                                    currentlyPlayingUri = currentlyPlayingUri,
                                    hasMediaPermission = hasMediaPermission,
                                    permissionPermanentlyDenied = permissionPermanentlyDenied,
                                    userPreferences = userPreferences,
                                    onRequestPermissionClick = requestMediaPermissions,
                                    onPickSafFileClick = {
                                        safMediaLauncher.launch(arrayOf("audio/*", "video/*"))
                                    },
                                    onGenerateSamplesClick = {
                                        triggerFullScanOrPermission()
                                    },
                                    onNavigateToMusic = {
                                        viewModel.selectPrimaryTab(PrimaryTab.MUSIC)
                                    },
                                    onNavigateToVideos = {
                                        viewModel.selectPrimaryTab(PrimaryTab.VIDEOS)
                                    },
                                    onNavigateToPlaylists = {
                                        viewModel.selectPrimaryTab(PrimaryTab.LIBRARY)
                                        viewModel.selectLibrarySubTab(LibrarySubTab.PLAYLISTS)
                                    },
                                    onNavigateToFavorites = {
                                        viewModel.selectPrimaryTab(PrimaryTab.LIBRARY)
                                        viewModel.selectLibrarySubTab(LibrarySubTab.FAVORITES)
                                    },
                                    onNavigateToFolder = { folderName ->
                                        viewModel.selectPrimaryTab(PrimaryTab.LIBRARY)
                                        viewModel.selectLibrarySubTab(LibrarySubTab.FOLDERS)
                                        viewModel.selectFolderFilter(folderName)
                                    },
                                    onPlayMedia = onPlayMediaCallback,
                                    onToggleFavorite = onToggleFavoriteCallback,
                                    onAddToPlaylistRequest = onAddToPlaylistRequestCallback,
                                    onOpenStudioForMedia = onOpenStudioForMediaCallback,
                                    onShareMedia = onShareMediaCallback,
                                    onSetRingtoneRequest = onSetRingtoneRequestCallback,
                                    onShowFileInfoRequest = onShowFileInfoRequestCallback,
                                    onOpenEqualizer = onOpenEqualizerCallback
                                )
                            }

                            PrimaryTab.VIDEOS -> {
                                MediaCatalogScreen(
                                    title = "Video Cinema Vault (${videoMedia.size})",
                                    subtitle = "Hardware-accelerated Media3 ExoPlayer with SRT/WebVTT subtitles, orientation controls & gesture telemetry",
                                    mediaList = videoMedia,
                                    favoriteUris = favoriteUris,
                                    currentlyPlayingUri = currentlyPlayingUri,
                                    emptyTitle = "No Videos Found",
                                    emptySubtitle = "Tap 'Scan' to index all device video folders or open any MP4/MKV/WebM file via SAF.",
                                    onPlayMedia = onPlayVideoCallback,
                                    onToggleFavorite = onToggleFavoriteCallback,
                                    onAddToPlaylistRequest = onAddToPlaylistRequestCallback,
                                    onOpenStudioForMedia = onOpenStudioForMediaCallback,
                                    onShareMedia = onShareMediaCallback,
                                    onSetRingtoneRequest = onSetRingtoneRequestCallback,
                                    onShowFileInfoRequest = onShowFileInfoRequestCallback,
                                    onPickSafFileClick = {
                                        safMediaLauncher.launch(arrayOf("video/*"))
                                    },
                                    hasMediaPermission = hasMediaPermission,
                                    onRequestPermissionClick = requestMediaPermissions,
                                    onRescanClick = triggerFullScanOrPermission,
                                    searchQuery = searchQuery,
                                    onClearSearch = { viewModel.updateSearchQuery("") }
                                )
                            }

                            PrimaryTab.MUSIC -> {
                                MediaCatalogScreen(
                                    title = "Audio Studio Deck (${audioMedia.size})",
                                    subtitle = "Gapless queue, background MediaSessionService, multi-band Equalizer & lossless WAV/AAC editor",
                                    mediaList = audioMedia,
                                    favoriteUris = favoriteUris,
                                    currentlyPlayingUri = currentlyPlayingUri,
                                    emptyTitle = "No Audio Tracks Found",
                                    emptySubtitle = "Tap 'Scan' to index all device audio folders or open any local audio file via SAF.",
                                    onPlayMedia = onPlayMediaCallback,
                                    onToggleFavorite = onToggleFavoriteCallback,
                                    onAddToPlaylistRequest = onAddToPlaylistRequestCallback,
                                    onOpenStudioForMedia = onOpenStudioForMediaCallback,
                                    onShareMedia = onShareMediaCallback,
                                    onSetRingtoneRequest = onSetRingtoneRequestCallback,
                                    onShowFileInfoRequest = onShowFileInfoRequestCallback,
                                    onPickSafFileClick = {
                                        safMediaLauncher.launch(arrayOf("audio/*"))
                                    },
                                    hasMediaPermission = hasMediaPermission,
                                    onRequestPermissionClick = requestMediaPermissions,
                                    onRescanClick = triggerFullScanOrPermission,
                                    searchQuery = searchQuery,
                                    onClearSearch = { viewModel.updateSearchQuery("") }
                                )
                            }

                            PrimaryTab.LIBRARY -> {
                                LibraryAndFoldersScreen(
                                    selectedSubTab = selectedLibSubTab,
                                    onSelectSubTab = { viewModel.selectLibrarySubTab(it) },
                                    allMedia = allMedia,
                                    selectedFolder = selectedFolder,
                                    onSelectFolder = { viewModel.selectFolderFilter(it) },
                                    selectedAlbum = selectedAlbum,
                                    onSelectAlbum = { viewModel.selectAlbumFilter(it) },
                                    selectedArtist = selectedArtist,
                                    onSelectArtist = { viewModel.selectArtistFilter(it) },
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
                                    currentlyPlayingUri = currentlyPlayingUri,
                                    onPlayMedia = onPlayMediaCallback,
                                    onToggleFavorite = onToggleFavoriteCallback,
                                    onAddToPlaylistRequest = onAddToPlaylistRequestCallback,
                                    onOpenStudioForMedia = onOpenStudioForMediaCallback,
                                    onShareMedia = onShareMediaCallback,
                                    onSetRingtoneRequest = onSetRingtoneRequestCallback,
                                    onShowFileInfoRequest = onShowFileInfoRequestCallback,
                                    onOpenEqualizer = onOpenEqualizerCallback,
                                    onClearHistoryOnly = { viewModel.clearPlaybackHistoryOnly() },
                                    hasMediaPermission = hasMediaPermission,
                                    onRequestPermissionClick = requestMediaPermissions,
                                    onRescanClick = triggerFullScanOrPermission,
                                    canPopSubTab = viewModel.canPopLibrarySubTab(),
                                    onPopSubTab = { viewModel.popLibrarySubTab() },
                                    searchQuery = searchQuery,
                                    onClearSearch = { viewModel.updateSearchQuery("") }
                                )
                            }

                            PrimaryTab.SETTINGS -> {
                                ProfileAndSettingsScreen(
                                    userPreferences = userPreferences,
                                    hasMediaPermission = hasMediaPermission,
                                    historyCount = history.size,
                                    sleepTimerMinutes = playbackState.sleepTimerTotalMinutes,
                                    sleepTimerStopAtEndOfTrack = playbackState.sleepTimerStopAtEndOfTrack,
                                    preampGainDb = eqState.preampGainDb,
                                    onSetThemeMode = { viewModel.setThemeMode(it) },
                                    onSetDynamicColor = { viewModel.setDynamicColor(it) },
                                    onSetDefaultSpeed = { viewModel.setDefaultPlaybackSpeedPref(it) },
                                    onSetResumePlayback = { viewModel.setResumePlaybackEnabled(it) },
                                    onSetRecordHistory = { viewModel.setRecordPlaybackHistory(it) },
                                    onSetBackgroundPlayback = { viewModel.setBackgroundPlaybackEnabled(it) },
                                    onSetKeepScreenOnDuringVideo = { viewModel.setKeepScreenOnDuringVideo(it) },
                                    onSetVideoGestureControls = { viewModel.setVideoGestureControlsEnabled(it) },
                                    onSetVideoOrientationPref = { viewModel.setVideoOrientationPreference(it) },
                                    onSetAllowExperimental4kSoftwareFallback = {
                                        viewModel.setAllowExperimental4kSoftwareFallback(it)
                                    },
                                    onSetAudioBalance = { viewModel.setAudioBalance(it) },
                                    onSetPreampGainDb = { viewModel.setPreampGainDb(it) },
                                    onSetSleepTimerMinutes = { viewModel.setSleepTimerMinutes(it) },
                                    onSetSleepTimerEndOfTrack = { viewModel.setSleepTimerEndOfTrack(it) },
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

                    // In Landscape / NavigationRail mode, Persistent Mini-Player is docked at the bottom of the
                    // center column so it never overlaps the left NavigationRail, the song list, or the right Now Playing panel.
                    if (useNavigationRail) {
                        AnimatedVisibility(visible = playbackState.currentMedia != null) {
                            PersistentMiniPlayerBar(
                                playbackState = playbackState,
                                onExpandClick = { viewModel.setNowPlayingExpanded(true) },
                                onPlayPauseClick = { viewModel.togglePlayPause() },
                                onNextClick = { viewModel.skipToNext() },
                                onPreviousClick = { viewModel.skipToPrevious() },
                                showSidePanelToggle = canFitSideNowPlayingPanel,
                                isSidePanelOpen = showRightNowPlayingPanel,
                                onToggleSidePanelClick = toggleSidePanelOrExpandPlayer,
                                isCompactHeight = isCompactHeight
                            )
                        }
                    }
                }

                // 3. RIGHT: Dedicated Now Playing Side Panel when in Landscape with sufficient width (or toggled open)
                AnimatedVisibility(visible = showRightNowPlayingPanel) {
                    val panelWidth = sidePanelTargetWidth
                    NowPlayingSidePanel(
                        playbackState = playbackState,
                        userPreferences = userPreferences,
                        isFavorite = playbackState.currentMedia?.let { favoriteUris.contains(it.uriString) } ?: false,
                        onExpandFullPlayer = { viewModel.setNowPlayingExpanded(true) },
                        onCollapseSidePanel = { userSidePanelOverride = false },
                        onPlayPause = { viewModel.togglePlayPause() },
                        onSeekTo = { viewModel.seekTo(it) },
                        onPrevious = { viewModel.skipToPrevious() },
                        onNext = { viewModel.skipToNext() },
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onCycleRepeat = { viewModel.cycleRepeatMode() },
                        onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                        onToggleFavorite = {
                            playbackState.currentMedia?.let { viewModel.toggleFavorite(it) }
                        },
                        onOpenEqualizer = { viewModel.setEqualizerSheetOpen(true) },
                        onOpenStudio = { viewModel.openStudioForMedia(playbackState.currentMedia) },
                        onSelectQueueItem = { item -> viewModel.playMedia(item, playbackState.queue) },
                        onRemoveQueueItem = { idx -> viewModel.removeQueueItemAt(idx) },
                        modifier = Modifier.width(panelWidth)
                    )
                }
            }
        }
    }
}

