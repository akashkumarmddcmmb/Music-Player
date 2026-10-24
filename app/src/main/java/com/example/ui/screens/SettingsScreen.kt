package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.ads.OptionalAdManager
import com.example.data.preferences.SortOption
import com.example.data.preferences.UserPreferencesState
import com.example.data.preferences.VideoOrientationPreference
import com.example.ui.theme.AppThemeMode
import kotlin.math.abs

@Composable
fun ProfileAndSettingsScreen(
    userPreferences: UserPreferencesState,
    hasMediaPermission: Boolean,
    historyCount: Int,
    sleepTimerMinutes: Int = 0,
    sleepTimerStopAtEndOfTrack: Boolean = false,
    preampGainDb: Float = 0.0f,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetDefaultSpeed: (Float) -> Unit,
    onSetResumePlayback: (Boolean) -> Unit,
    onSetRecordHistory: (Boolean) -> Unit,
    onSetBackgroundPlayback: (Boolean) -> Unit = {},
    onSetKeepScreenOnDuringVideo: (Boolean) -> Unit = {},
    onSetVideoGestureControls: (Boolean) -> Unit = {},
    onSetVideoOrientationPref: (VideoOrientationPreference) -> Unit = {},
    onSetAllowExperimental4kSoftwareFallback: (Boolean) -> Unit = {},
    onSetAudioBalance: (Float) -> Unit = {},
    onSetPreampGainDb: (Float) -> Unit = {},
    onSetSleepTimerMinutes: (Int) -> Unit = {},
    onSetSleepTimerEndOfTrack: (Boolean) -> Unit = {},
    onSetSubtitlesEnabled: (Boolean) -> Unit,
    onSetSubtitleScale: (Float) -> Unit,
    onSetDefaultSort: (SortOption) -> Unit,
    onOpenEqualizerClick: () -> Unit,
    onRequestMediaPermissionClick: () -> Unit,
    onSetOptionalAdsConfig: (Boolean, Boolean) -> Unit,
    onClearHistoryOnlyClick: () -> Unit,
    onResetPreferencesClick: () -> Unit
) {
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showResetPrefsDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Local Playback History?") },
            text = {
                Text(
                    "This clears your local playback history records ($historyCount entries) from the SQLite database only.\n\n" +
                        "Your actual audio and video files on the device are NEVER modified or deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistoryOnlyClick()
                        showClearHistoryDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_history_dialog_button")
                ) {
                    Text("Clear History Records Only")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetPrefsDialog) {
        AlertDialog(
            onDismissRequest = { showResetPrefsDialog = false },
            title = { Text("Reset Application Preferences?") },
            text = {
                Text(
                    "Resets theme, playback speed, subtitle styling, sorting, video orientation, and equalizer settings to factory defaults. Playlists and media files are preserved."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetPreferencesClick()
                        showResetPrefsDialog = false
                    },
                    modifier = Modifier.testTag("confirm_reset_prefs_dialog_button")
                ) {
                    Text("Reset Preferences")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetPrefsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Open-Source Acknowledgements & Licenses") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(
                            text = "PulsePlay is an original Android codebase. Full license texts and notices are maintained in THIRD_PARTY_NOTICES.md.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        LicenseEntryCard(
                            name = "AndroidX Media3 (ExoPlayer, Session, UI, Common) 1.5.1",
                            owner = "Google LLC / The Android Open Source Project",
                            license = "Apache License 2.0"
                        )
                    }
                    item {
                        LicenseEntryCard(
                            name = "AndroidX Jetpack Compose, Room 2.7.0, DataStore 1.1.7, Lifecycle 2.8.7",
                            owner = "Google LLC / The Android Open Source Project",
                            license = "Apache License 2.0"
                        )
                    }
                    item {
                        LicenseEntryCard(
                            name = "Kotlin Stdlib & Coroutines 1.10.2",
                            owner = "JetBrains s.r.o.",
                            license = "Apache License 2.0"
                        )
                    }
                    item {
                        LicenseEntryCard(
                            name = "Space Grotesk, Plus Jakarta Sans, JetBrains Mono Fonts",
                            owner = "Florian Karsten, Tokotype, JetBrains",
                            license = "SIL Open Font License 1.1 (OFL-1.1)"
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showLicensesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen_list"),
        contentPadding = PaddingValues(bottom = 120.dp, start = 16.dp, end = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // Theme & Visual Appearance
        item {
            SettingsSectionCard(
                title = "Theme & Appearance",
                icon = Icons.Default.Palette
            ) {
                Text(
                    text = "Color Theme Mode",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = userPreferences.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            label = { Text(mode.name) },
                            leadingIcon = {
                                val modeIcon = when (mode) {
                                    AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                    AppThemeMode.DARK -> Icons.Default.DarkMode
                                    AppThemeMode.LIGHT -> Icons.Default.LightMode
                                }
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_mode_chip_${mode.name.lowercase()}")
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Material You Dynamic Colors",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Universal dynamic tonal engine active for all Android versions • Adapts to system wallpaper & mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = userPreferences.dynamicColor,
                        onCheckedChange = onSetDynamicColor,
                        modifier = Modifier.testTag("dynamic_color_switch")
                    )
                }
            }
        }

        // Video Screen, Rotation & Gesture Preferences
        item {
            SettingsSectionCard(
                title = "Video Screen, Orientation & Gestures",
                icon = Icons.Default.ScreenRotation
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keep Screen On During Video Playback", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Keeps display awake while video is actively playing; clears on pause/stop",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = userPreferences.keepScreenOnDuringVideo,
                        onCheckedChange = onSetKeepScreenOnDuringVideo,
                        modifier = Modifier.testTag("settings_keep_screen_on_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Video Gesture Controls", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Vertical swipe for brightness (left) & volume (right); double-tap play/pause",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = userPreferences.videoGestureControlsEnabled,
                        onCheckedChange = onSetVideoGestureControls,
                        modifier = Modifier.testTag("settings_video_gestures_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Optional 4K Software Decoder Fallback", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Resource-intensive CPU fallback when no hardware decoder supports 4K. Guarded by CPU core count, memory, thermal status & battery saver",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = userPreferences.allowExperimental4kSoftwareFallback,
                        onCheckedChange = onSetAllowExperimental4kSoftwareFallback,
                        modifier = Modifier.testTag("settings_4k_sw_fallback_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Default Video Orientation Mode",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(VideoOrientationPreference.entries) { pref ->
                        FilterChip(
                            selected = userPreferences.videoOrientationPreference == pref,
                            onClick = { onSetVideoOrientationPref(pref) },
                            label = { Text(pref.label) },
                            modifier = Modifier.testTag("settings_video_orientation_${pref.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Playback, Background Service, Speed (0.5x–3.0x), Sleep Timer & Library Sorting Preferences
        item {
            SettingsSectionCard(
                title = "Playback, Speed (0.5x–3.0x) & Sleep Timer",
                icon = Icons.Default.Speed
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Audio Playback", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Continues playing via MediaSessionService when screen turns off or app is backgrounded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = userPreferences.backgroundPlaybackEnabled,
                        onCheckedChange = onSetBackgroundPlayback,
                        modifier = Modifier.testTag("settings_background_playback_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Default Playback Speed (${userPreferences.defaultPlaybackSpeed}x • 0.5x–3.0x)",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)) { spd ->
                        FilterChip(
                            selected = abs(userPreferences.defaultPlaybackSpeed - spd) < 0.05f,
                            onClick = { onSetDefaultSpeed(spd) },
                            label = { Text("${spd}x") },
                            modifier = Modifier.testTag("settings_speed_chip_${spd}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Sleep Timer (Automatic Stop)",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(0, 5, 10, 15, 30, 45, 60)) { mins ->
                        val selected = if (mins == 0) {
                            sleepTimerMinutes == 0 && !sleepTimerStopAtEndOfTrack
                        } else {
                            !sleepTimerStopAtEndOfTrack && sleepTimerMinutes == mins
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { onSetSleepTimerMinutes(mins) },
                            label = { Text(if (mins == 0) "Off" else "$mins min") },
                            modifier = Modifier.testTag("settings_sleep_timer_$mins")
                        )
                    }
                    item {
                        FilterChip(
                            selected = sleepTimerStopAtEndOfTrack,
                            onClick = { onSetSleepTimerEndOfTrack(!sleepTimerStopAtEndOfTrack) },
                            label = { Text("End of Track") },
                            modifier = Modifier.testTag("settings_sleep_timer_eot")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Default Library Sort Order",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SortOption.entries) { sortOpt ->
                        FilterChip(
                            selected = userPreferences.defaultSortOption == sortOpt,
                            onClick = { onSetDefaultSort(sortOpt) },
                            label = { Text(sortOpt.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Resume Playback from Last Position", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = userPreferences.resumePlaybackEnabled,
                        onCheckedChange = onSetResumePlayback
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Record Local Playback History", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = userPreferences.recordPlaybackHistory,
                        onCheckedChange = onSetRecordHistory,
                        modifier = Modifier.testTag("record_history_switch")
                    )
                }
            }
        }

        // Audio Settings, Better Sound Experience & Lyrics (SRT / WebVTT)
        item {
            SettingsSectionCard(
                title = "Audio Settings, Lyrics (SRT/VTT) & Equalizer DSP",
                icon = Icons.Default.Subtitles
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Synchronized Lyrics & Subtitles (SRT / WebVTT)", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = userPreferences.subtitlesEnabledByDefault,
                        onCheckedChange = onSetSubtitlesEnabled
                    )
                }

                Text(
                    text = "Default Lyrics & Subtitle Font Scale: ${(userPreferences.subtitleFontScale * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = userPreferences.subtitleFontScale,
                    onValueChange = onSetSubtitleScale,
                    valueRange = 0.7f..1.8f
                )

                Text(
                    text = "Sound Enhancer Preamp Gain: %+.1f dB".format(preampGainDb),
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = preampGainDb,
                    onValueChange = onSetPreampGainDb,
                    valueRange = -6.0f..6.0f,
                    modifier = Modifier.testTag("settings_preamp_slider")
                )

                val balanceLabel = when {
                    abs(userPreferences.audioBalance) < 0.05f -> "Center"
                    userPreferences.audioBalance < 0f -> "L ${(abs(userPreferences.audioBalance) * 100).toInt()}%"
                    else -> "R ${(userPreferences.audioBalance * 100).toInt()}%"
                }
                Text(
                    text = "Stereo Channel Balance (Real-Time L/R DSP): $balanceLabel",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = userPreferences.audioBalance,
                    onValueChange = onSetAudioBalance,
                    valueRange = -1.0f..1.0f
                )

                OutlinedButton(
                    onClick = onOpenEqualizerClick,
                    modifier = Modifier.testTag("settings_open_eq_button")
                ) {
                    Icon(Icons.Default.Equalizer, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Configure Multi-Band Equalizer & Bass Boost")
                }
            }
        }

        // Privacy, Permissions & Optional Advertising Audit
        item {
            SettingsSectionCard(
                title = "Privacy, Permissions & Optional Ad Module",
                icon = Icons.Default.Security
            ) {
                Text(
                    text = "Permission Status: ${if (hasMediaPermission) "READ_MEDIA Granted" else "Using Zero-Permission SAF / Built-in Samples"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "PulsePlay never uploads media, filenames, thumbnails, playlists, or playback history. Network permissions (INTERNET / ACCESS_NETWORK_STATE) are explicitly stripped from the manifest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!hasMediaPermission) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onRequestMediaPermissionClick) {
                        Text("Request Shared MediaStore Access")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                val adAudit = OptionalAdManager.auditReport
                Text(
                    text = "Optional Advertising Sandbox (Default: Disabled)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "SDK: ${adAudit.sdkBundled} • Network: ${adAudit.networkRequestsPerformed}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Local Test Sponsor Slot (Requires Consent)", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = userPreferences.optionalAdsModuleEnabled && userPreferences.optionalAdsConsentGranted,
                        onCheckedChange = { enabled ->
                            onSetOptionalAdsConfig(enabled, enabled)
                        },
                        modifier = Modifier.testTag("optional_ads_toggle_switch")
                    )
                }
            }
        }

        // Data Management & Non-Destructive History Clearing
        item {
            SettingsSectionCard(
                title = "History & Preference Reset",
                icon = Icons.Default.DeleteSweep
            ) {
                Text(
                    text = "Clearing playback history removes local database records ($historyCount entries) only. It NEVER deletes your audio or video files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { showClearHistoryDialog = true },
                        modifier = Modifier.testTag("clear_history_settings_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear History")
                    }
                    OutlinedButton(
                        onClick = { showResetPrefsDialog = true },
                        modifier = Modifier.testTag("reset_preferences_button")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Prefs")
                    }
                }
            }
        }

        // Application Version & Open-Source Acknowledgements
        item {
            SettingsSectionCard(
                title = "About & Open-Source Licenses",
                icon = Icons.Default.Info
            ) {
                Text(
                    text = "Application: PulsePlay (Development Name)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Application ID: ${BuildConfig.APPLICATION_ID} • Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showLicensesDialog = true },
                    modifier = Modifier.testTag("open_licenses_dialog_button")
                ) {
                    Text("View Third-Party Notices & Licenses")
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun LicenseEntryCard(
    name: String,
    owner: String,
    license: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text = "Owner: $owner", style = MaterialTheme.typography.bodySmall)
            Text(
                text = "License: $license",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
