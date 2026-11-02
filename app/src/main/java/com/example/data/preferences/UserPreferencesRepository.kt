package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemeMode
import com.example.update.UpdateDistributionChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pulseDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pulseplay_user_preferences"
)

enum class SortOption(val label: String) {
    TITLE_ASC("Title (A-Z)"),
    DATE_DESC("Newest First"),
    DURATION_DESC("Longest First"),
    SIZE_DESC("Largest Size")
}

enum class VideoOrientationPreference(val label: String) {
    AUTO("Auto Rotate"),
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait")
}

enum class HighResVideoPlaybackMode(
    val label: String,
    val description: String
) {
    AUTO(
        label = "Auto (Recommended)",
        description = "Automatically selects the best available playback method. Uses normal playback whenever it works properly, and automatically attempts a compatible fallback only when necessary."
    ),
    COMPATIBILITY(
        label = "Compatibility Mode",
        description = "Provides more conservative playback settings for older or resource-limited devices. Automatically uses compatible path where possible."
    ),
    HIGH_RESOLUTION(
        label = "High-Resolution Playback",
        description = "Attempts 4K/8K playback when supported by the device and video stream. Automatically falls back to standard playback when high-resolution playback is not supported."
    ),
    HARDWARE_PREFERENCE(
        label = "Hardware Decoder Preference",
        description = "Prefers hardware-accelerated playback when available."
    ),
    SOFTWARE_FALLBACK(
        label = "Software Decoder Fallback",
        description = "Attempts software decoding only when hardware decoding fails and the device can safely handle it."
    )
}

data class UserPreferencesState(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val dynamicColor: Boolean = true,
    val defaultPlaybackSpeed: Float = 1.0f,
    val resumePlaybackEnabled: Boolean = true,
    val recordPlaybackHistory: Boolean = true,
    val keepScreenOnDuringVideo: Boolean = true,
    val backgroundPlaybackEnabled: Boolean = true,
    val videoGestureControlsEnabled: Boolean = true,
    val videoOrientationPreference: VideoOrientationPreference = VideoOrientationPreference.AUTO,
    val highResVideoPlaybackMode: HighResVideoPlaybackMode = HighResVideoPlaybackMode.AUTO,
    val audioBalance: Float = 0.0f, // -1.0f (Left) .. 0.0f (Center) .. +1.0f (Right)
    val subtitlesEnabledByDefault: Boolean = true,
    val subtitleFontScale: Float = 1.0f,
    val subtitleSyncOffsetMs: Long = 0L,
    val defaultSortOption: SortOption = SortOption.DATE_DESC,
    val equalizerEnabled: Boolean = false,
    val equalizerPresetIndex: Int = 0,
    val bassBoostStrength: Int = 0, // 0..1000
    val preampGainDb: Float = 0.0f, // -12.0f .. +12.0f
    val customBandLevelsMb: List<Int> = listOf(0, 0, 0, 0, 0),
    // Music Play Settings & Edit Player Customization
    val crossfadeDurationSeconds: Int = 0, // 0 = Off, 2, 4, 6, 8, 12
    val gaplessPlaybackEnabled: Boolean = true,
    val playerShowWaveform: Boolean = true,
    val playerShowQuickActionRow: Boolean = true,
    val playerShowRemainingTime: Boolean = true,
    val playerShowAbAndBookmarkStrip: Boolean = true,
    // Hardware Decoder Preference (Simple ON/OFF switch, enabled by default)
    val hardwareDecoderPreferenceEnabled: Boolean = true,
    // Software Decoder Fallback (Simple ON/OFF switch, disabled by default)
    val allowExperimental4kSoftwareFallback: Boolean = false,
    // Phase 14: Optional Advertising Module (Strictly disabled by default for privacy)
    val optionalAdsModuleEnabled: Boolean = false,
    val optionalAdsConsentGranted: Boolean = false,
    // GitHub Release & Dual-Distribution Auto-Update Configuration
    val githubRepositorySlug: String = "",
    val includePrereleaseUpdates: Boolean = false,
    val selectedUpdateChannel: UpdateDistributionChannel? = null
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULT_SPEED = floatPreferencesKey("default_playback_speed")
        val RESUME_ENABLED = booleanPreferencesKey("resume_playback_enabled")
        val RECORD_HISTORY = booleanPreferencesKey("record_playback_history")
        val KEEP_SCREEN_ON_VIDEO = booleanPreferencesKey("keep_screen_on_during_video")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback_enabled")
        val VIDEO_GESTURE_CONTROLS = booleanPreferencesKey("video_gesture_controls_enabled")
        val VIDEO_ORIENTATION_PREF = stringPreferencesKey("video_orientation_preference")
        val HIGH_RES_VIDEO_MODE = stringPreferencesKey("high_res_video_playback_mode")
        val AUDIO_BALANCE = floatPreferencesKey("audio_balance")
        val SUBTITLES_ENABLED = booleanPreferencesKey("subtitles_enabled_default")
        val SUBTITLE_FONT_SCALE = floatPreferencesKey("subtitle_font_scale")
        val SUBTITLE_OFFSET_MS = longPreferencesKey("subtitle_sync_offset_ms")
        val SORT_OPTION = stringPreferencesKey("default_sort_option")
        val EQ_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val EQ_PRESET = intPreferencesKey("equalizer_preset_index")
        val BASS_STRENGTH = intPreferencesKey("bass_boost_strength")
        val PREAMP_GAIN_DB = floatPreferencesKey("preamp_gain_db")
        val EQ_BANDS_CSV = stringPreferencesKey("equalizer_bands_csv")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_duration_seconds")
        val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback_enabled")
        val PLAYER_SHOW_WAVEFORM = booleanPreferencesKey("player_show_waveform")
        val PLAYER_SHOW_QUICK_ROW = booleanPreferencesKey("player_show_quick_action_row")
        val PLAYER_SHOW_REMAINING_TIME = booleanPreferencesKey("player_show_remaining_time")
        val PLAYER_SHOW_AB_BOOKMARK_STRIP = booleanPreferencesKey("player_show_ab_bookmark_strip")
        val HARDWARE_DECODER_PREFERENCE = booleanPreferencesKey("hardware_decoder_preference_enabled")
        val ALLOW_4K_SW_FALLBACK = booleanPreferencesKey("allow_experimental_4k_software_fallback")
        val OPTIONAL_ADS_ENABLED = booleanPreferencesKey("optional_ads_module_enabled")
        val OPTIONAL_ADS_CONSENT = booleanPreferencesKey("optional_ads_consent_granted")
        val GITHUB_REPO_SLUG = stringPreferencesKey("github_repository_slug")
        val INCLUDE_PRERELEASE_UPDATES = booleanPreferencesKey("include_prerelease_updates")
        val SELECTED_UPDATE_CHANNEL = stringPreferencesKey("selected_update_channel")
    }

    val preferencesFlow: Flow<UserPreferencesState> = context.pulseDataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]?.let {
            runCatching { AppThemeMode.valueOf(it) }.getOrNull()
        } ?: AppThemeMode.DARK

        val sort = prefs[Keys.SORT_OPTION]?.let {
            runCatching { SortOption.valueOf(it) }.getOrNull()
        } ?: SortOption.DATE_DESC

        val orientationPref = prefs[Keys.VIDEO_ORIENTATION_PREF]?.let {
            runCatching { VideoOrientationPreference.valueOf(it) }.getOrNull()
        } ?: VideoOrientationPreference.AUTO

        val allowSwFallback = prefs[Keys.ALLOW_4K_SW_FALLBACK] ?: false
        val hwDecoderPref = prefs[Keys.HARDWARE_DECODER_PREFERENCE] ?: true
        val highResMode = prefs[Keys.HIGH_RES_VIDEO_MODE]?.let {
            runCatching { HighResVideoPlaybackMode.valueOf(it) }.getOrNull()
        } ?: if (allowSwFallback) HighResVideoPlaybackMode.SOFTWARE_FALLBACK else HighResVideoPlaybackMode.AUTO

        val savedUpdateChannel = prefs[Keys.SELECTED_UPDATE_CHANNEL]?.let {
            runCatching { UpdateDistributionChannel.valueOf(it) }.getOrNull()
        }

        val bandsCsv = prefs[Keys.EQ_BANDS_CSV] ?: "0,0,0,0,0"
        val parsedBands = bandsCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .ifEmpty { listOf(0, 0, 0, 0, 0) }

        UserPreferencesState(
            themeMode = theme,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            defaultPlaybackSpeed = (prefs[Keys.DEFAULT_SPEED] ?: 1.0f).coerceIn(0.25f, 3.0f),
            resumePlaybackEnabled = prefs[Keys.RESUME_ENABLED] ?: true,
            recordPlaybackHistory = prefs[Keys.RECORD_HISTORY] ?: true,
            keepScreenOnDuringVideo = prefs[Keys.KEEP_SCREEN_ON_VIDEO] ?: true,
            backgroundPlaybackEnabled = prefs[Keys.BACKGROUND_PLAYBACK] ?: true,
            videoGestureControlsEnabled = prefs[Keys.VIDEO_GESTURE_CONTROLS] ?: true,
            videoOrientationPreference = orientationPref,
            highResVideoPlaybackMode = highResMode,
            audioBalance = (prefs[Keys.AUDIO_BALANCE] ?: 0.0f).coerceIn(-1.0f, 1.0f),
            subtitlesEnabledByDefault = prefs[Keys.SUBTITLES_ENABLED] ?: true,
            subtitleFontScale = (prefs[Keys.SUBTITLE_FONT_SCALE] ?: 1.0f).coerceIn(0.6f, 2.0f),
            subtitleSyncOffsetMs = prefs[Keys.SUBTITLE_OFFSET_MS] ?: 0L,
            defaultSortOption = sort,
            equalizerEnabled = prefs[Keys.EQ_ENABLED] ?: false,
            equalizerPresetIndex = prefs[Keys.EQ_PRESET] ?: 0,
            bassBoostStrength = (prefs[Keys.BASS_STRENGTH] ?: 0).coerceIn(0, 1000),
            preampGainDb = (prefs[Keys.PREAMP_GAIN_DB] ?: 0.0f).coerceIn(-12.0f, 12.0f),
            customBandLevelsMb = parsedBands,
            crossfadeDurationSeconds = (prefs[Keys.CROSSFADE_SECONDS] ?: 0).coerceIn(0, 15),
            gaplessPlaybackEnabled = prefs[Keys.GAPLESS_PLAYBACK] ?: true,
            playerShowWaveform = prefs[Keys.PLAYER_SHOW_WAVEFORM] ?: true,
            playerShowQuickActionRow = prefs[Keys.PLAYER_SHOW_QUICK_ROW] ?: true,
            playerShowRemainingTime = prefs[Keys.PLAYER_SHOW_REMAINING_TIME] ?: true,
            playerShowAbAndBookmarkStrip = prefs[Keys.PLAYER_SHOW_AB_BOOKMARK_STRIP] ?: true,
            hardwareDecoderPreferenceEnabled = hwDecoderPref,
            allowExperimental4kSoftwareFallback = allowSwFallback || highResMode == HighResVideoPlaybackMode.SOFTWARE_FALLBACK,
            optionalAdsModuleEnabled = prefs[Keys.OPTIONAL_ADS_ENABLED] ?: false,
            optionalAdsConsentGranted = prefs[Keys.OPTIONAL_ADS_CONSENT] ?: false,
            githubRepositorySlug = prefs[Keys.GITHUB_REPO_SLUG] ?: "",
            includePrereleaseUpdates = prefs[Keys.INCLUDE_PRERELEASE_UPDATES] ?: false,
            selectedUpdateChannel = savedUpdateChannel
        )
    }

    suspend fun setCrossfadeDurationSeconds(seconds: Int) {
        context.pulseDataStore.edit { it[Keys.CROSSFADE_SECONDS] = seconds.coerceIn(0, 15) }
    }

    suspend fun setGaplessPlaybackEnabled(enabled: Boolean) {
        context.pulseDataStore.edit { it[Keys.GAPLESS_PLAYBACK] = enabled }
    }

    suspend fun setPlayerShowWaveform(show: Boolean) {
        context.pulseDataStore.edit { it[Keys.PLAYER_SHOW_WAVEFORM] = show }
    }

    suspend fun setPlayerShowQuickActionRow(show: Boolean) {
        context.pulseDataStore.edit { it[Keys.PLAYER_SHOW_QUICK_ROW] = show }
    }

    suspend fun setPlayerShowRemainingTime(show: Boolean) {
        context.pulseDataStore.edit { it[Keys.PLAYER_SHOW_REMAINING_TIME] = show }
    }

    suspend fun setPlayerShowAbAndBookmarkStrip(show: Boolean) {
        context.pulseDataStore.edit { it[Keys.PLAYER_SHOW_AB_BOOKMARK_STRIP] = show }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.pulseDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.pulseDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDefaultPlaybackSpeed(speed: Float) {
        context.pulseDataStore.edit { it[Keys.DEFAULT_SPEED] = speed.coerceIn(0.25f, 3.0f) }
    }

    suspend fun setResumePlaybackEnabled(enabled: Boolean) {
        context.pulseDataStore.edit { it[Keys.RESUME_ENABLED] = enabled }
    }

    suspend fun setRecordPlaybackHistory(enabled: Boolean) {
        context.pulseDataStore.edit { it[Keys.RECORD_HISTORY] = enabled }
    }

    suspend fun setKeepScreenOnDuringVideo(enabled: Boolean) {
        context.pulseDataStore.edit { it[Keys.KEEP_SCREEN_ON_VIDEO] = enabled }
    }

    suspend fun setBackgroundPlaybackEnabled(enabled: Boolean) {
        context.pulseDataStore.edit { it[Keys.BACKGROUND_PLAYBACK] = enabled }
    }

    suspend fun setVideoGestureControlsEnabled(enabled: Boolean) {
        context.pulseDataStore.edit { it[Keys.VIDEO_GESTURE_CONTROLS] = enabled }
    }

    suspend fun setVideoOrientationPreference(pref: VideoOrientationPreference) {
        context.pulseDataStore.edit { it[Keys.VIDEO_ORIENTATION_PREF] = pref.name }
    }

    suspend fun setAudioBalance(balance: Float) {
        context.pulseDataStore.edit { it[Keys.AUDIO_BALANCE] = balance.coerceIn(-1.0f, 1.0f) }
    }

    suspend fun setPreampGainDb(gainDb: Float) {
        context.pulseDataStore.edit { it[Keys.PREAMP_GAIN_DB] = gainDb.coerceIn(-12.0f, 12.0f) }
    }

    suspend fun setSubtitlesEnabled(enabled: Boolean) {
        context.pulseDataStore.edit { it[Keys.SUBTITLES_ENABLED] = enabled }
    }

    suspend fun setSubtitleFontScale(scale: Float) {
        context.pulseDataStore.edit { it[Keys.SUBTITLE_FONT_SCALE] = scale.coerceIn(0.6f, 2.0f) }
    }

    suspend fun setSubtitleSyncOffsetMs(offsetMs: Long) {
        context.pulseDataStore.edit { it[Keys.SUBTITLE_OFFSET_MS] = offsetMs.coerceIn(-10_000L, 10_000L) }
    }

    suspend fun setDefaultSortOption(option: SortOption) {
        context.pulseDataStore.edit { it[Keys.SORT_OPTION] = option.name }
    }

    suspend fun setEqualizerState(
        enabled: Boolean,
        presetIndex: Int,
        bassBoostStrength: Int,
        bandLevelsMb: List<Int>
    ) {
        context.pulseDataStore.edit { prefs ->
            prefs[Keys.EQ_ENABLED] = enabled
            prefs[Keys.EQ_PRESET] = presetIndex
            prefs[Keys.BASS_STRENGTH] = bassBoostStrength.coerceIn(0, 1000)
            prefs[Keys.EQ_BANDS_CSV] = bandLevelsMb.joinToString(",")
        }
    }

    suspend fun setHardwareDecoderPreference(enabled: Boolean) {
        context.pulseDataStore.edit { prefs ->
            prefs[Keys.HARDWARE_DECODER_PREFERENCE] = enabled
        }
    }

    suspend fun setHighResVideoPlaybackMode(mode: HighResVideoPlaybackMode) {
        context.pulseDataStore.edit { prefs ->
            prefs[Keys.HIGH_RES_VIDEO_MODE] = mode.name
            prefs[Keys.ALLOW_4K_SW_FALLBACK] = (mode == HighResVideoPlaybackMode.SOFTWARE_FALLBACK)
        }
    }

    suspend fun setAllowExperimental4kSoftwareFallback(enabled: Boolean) {
        context.pulseDataStore.edit { prefs ->
            prefs[Keys.ALLOW_4K_SW_FALLBACK] = enabled
            if (enabled) {
                prefs[Keys.HIGH_RES_VIDEO_MODE] = HighResVideoPlaybackMode.SOFTWARE_FALLBACK.name
            } else if (prefs[Keys.HIGH_RES_VIDEO_MODE] == HighResVideoPlaybackMode.SOFTWARE_FALLBACK.name) {
                prefs[Keys.HIGH_RES_VIDEO_MODE] = HighResVideoPlaybackMode.AUTO.name
            }
        }
    }

    suspend fun setOptionalAdsConfiguration(enabled: Boolean, consentGranted: Boolean) {
        context.pulseDataStore.edit { prefs ->
            prefs[Keys.OPTIONAL_ADS_ENABLED] = enabled
            prefs[Keys.OPTIONAL_ADS_CONSENT] = if (enabled) consentGranted else false
        }
    }

    suspend fun setGitHubRepositorySlug(slug: String) {
        context.pulseDataStore.edit { prefs ->
            prefs[Keys.GITHUB_REPO_SLUG] = slug.trim()
        }
    }

    suspend fun setIncludePrereleaseUpdates(include: Boolean) {
        context.pulseDataStore.edit { prefs ->
            prefs[Keys.INCLUDE_PRERELEASE_UPDATES] = include
        }
    }

    suspend fun setSelectedUpdateChannel(channel: UpdateDistributionChannel) {
        context.pulseDataStore.edit { prefs ->
            prefs[Keys.SELECTED_UPDATE_CHANNEL] = channel.name
        }
    }

    suspend fun resetAllPreferences() {
        context.pulseDataStore.edit { it.clear() }
    }
}
