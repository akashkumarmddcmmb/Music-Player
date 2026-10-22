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
    val audioBalance: Float = 0.0f, // -1.0f (Left) .. 0.0f (Center) .. +1.0f (Right)
    val subtitlesEnabledByDefault: Boolean = true,
    val subtitleFontScale: Float = 1.0f,
    val subtitleSyncOffsetMs: Long = 0L,
    val defaultSortOption: SortOption = SortOption.DATE_DESC,
    val equalizerEnabled: Boolean = false,
    val equalizerPresetIndex: Int = 0,
    val bassBoostStrength: Int = 0, // 0..1000
    val customBandLevelsMb: List<Int> = listOf(0, 0, 0, 0, 0),
    // Phase 14: Optional Advertising Module (Strictly disabled by default for privacy)
    val optionalAdsModuleEnabled: Boolean = false,
    val optionalAdsConsentGranted: Boolean = false
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
        val AUDIO_BALANCE = floatPreferencesKey("audio_balance")
        val SUBTITLES_ENABLED = booleanPreferencesKey("subtitles_enabled_default")
        val SUBTITLE_FONT_SCALE = floatPreferencesKey("subtitle_font_scale")
        val SUBTITLE_OFFSET_MS = longPreferencesKey("subtitle_sync_offset_ms")
        val SORT_OPTION = stringPreferencesKey("default_sort_option")
        val EQ_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val EQ_PRESET = intPreferencesKey("equalizer_preset_index")
        val BASS_STRENGTH = intPreferencesKey("bass_boost_strength")
        val EQ_BANDS_CSV = stringPreferencesKey("equalizer_bands_csv")
        val OPTIONAL_ADS_ENABLED = booleanPreferencesKey("optional_ads_module_enabled")
        val OPTIONAL_ADS_CONSENT = booleanPreferencesKey("optional_ads_consent_granted")
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
            audioBalance = (prefs[Keys.AUDIO_BALANCE] ?: 0.0f).coerceIn(-1.0f, 1.0f),
            subtitlesEnabledByDefault = prefs[Keys.SUBTITLES_ENABLED] ?: true,
            subtitleFontScale = (prefs[Keys.SUBTITLE_FONT_SCALE] ?: 1.0f).coerceIn(0.6f, 2.0f),
            subtitleSyncOffsetMs = prefs[Keys.SUBTITLE_OFFSET_MS] ?: 0L,
            defaultSortOption = sort,
            equalizerEnabled = prefs[Keys.EQ_ENABLED] ?: false,
            equalizerPresetIndex = prefs[Keys.EQ_PRESET] ?: 0,
            bassBoostStrength = (prefs[Keys.BASS_STRENGTH] ?: 0).coerceIn(0, 1000),
            customBandLevelsMb = parsedBands,
            optionalAdsModuleEnabled = prefs[Keys.OPTIONAL_ADS_ENABLED] ?: false,
            optionalAdsConsentGranted = prefs[Keys.OPTIONAL_ADS_CONSENT] ?: false
        )
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

    suspend fun setOptionalAdsConfiguration(enabled: Boolean, consentGranted: Boolean) {
        context.pulseDataStore.edit { prefs ->
            prefs[Keys.OPTIONAL_ADS_ENABLED] = enabled
            prefs[Keys.OPTIONAL_ADS_CONSENT] = if (enabled) consentGranted else false
        }
    }

    suspend fun resetAllPreferences() {
        context.pulseDataStore.edit { it.clear() }
    }
}
