package com.example.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.data.preferences.UserPreferencesRepository
import com.example.media.EqualizerController
import com.example.media.EqualizerDspConfig
import com.example.media.HardwareEqualizerState
import com.example.media.PulseEqualizerAudioProcessor
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Authoritative Media3 [MediaSessionService] for background playback, notification controls,
 * lock-screen media controls, audio focus handling, headphone disconnection pause,
 * wake-lock management during screen-off playback, and real-time 5-band Biquad Equalizer + Bass Boost DSP.
 *
 * Exposes [serviceAudioSessionId] and supports [ACTION_GET_AUDIO_SESSION_ID] custom session
 * command so [com.example.viewmodel.PulsePlayViewModel] and [EqualizerController] always bind
 * to the authoritative service [ExoPlayer] without casting `MediaController` to `ExoPlayer`.
 */
@OptIn(UnstableApi::class)
class PulsePlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var serviceExoPlayer: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var backgroundPlaybackAllowed: Boolean = true

    private val sessionListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            _serviceAudioSessionId.value = audioSessionId
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _serviceIsPlaying.value = isPlaying
            val currentSessionId = serviceExoPlayer?.audioSessionId ?: 0
            if (currentSessionId > 0 && _serviceAudioSessionId.value != currentSessionId) {
                _serviceAudioSessionId.value = currentSessionId
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val currentSessionId = serviceExoPlayer?.audioSessionId ?: 0
            if (currentSessionId > 0 && _serviceAudioSessionId.value != currentSessionId) {
                _serviceAudioSessionId.value = currentSessionId
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = buildExoPlayerWithEqualizerDsp(
            context = this,
            audioProcessor = EqualizerController.sharedAudioProcessor
        )
        serviceExoPlayer = exoPlayer

        exoPlayer.addListener(sessionListener)
        _serviceAudioSessionId.value = exoPlayer.audioSessionId

        // Continuously synchronize persisted Equalizer + Bass Boost & Background Playback preferences
        serviceScope.launch {
            runCatching {
                UserPreferencesRepository(applicationContext).preferencesFlow.collect { prefs ->
                    backgroundPlaybackAllowed = prefs.backgroundPlaybackEnabled
                    val resolvedBands = if (prefs.customBandLevelsMb.size == HardwareEqualizerState.DEFAULT_BAND_FREQUENCIES_HZ.size) {
                        prefs.customBandLevelsMb
                    } else {
                        HardwareEqualizerState.presetCurveForIndex(prefs.equalizerPresetIndex)
                    }
                    EqualizerController.sharedAudioProcessor.updateDspConfig(
                        EqualizerDspConfig(
                            isEnabled = prefs.equalizerEnabled,
                            bypassForHardwareEffect = false,
                            bandLevelsMb = resolvedBands,
                            bandFrequenciesHz = HardwareEqualizerState.DEFAULT_BAND_FREQUENCIES_HZ,
                            bassBoostStrength = prefs.bassBoostStrength
                        )
                    )
                }
            }
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val baseResult = super.onConnect(session, controller)
                val sessionCommands = baseResult.availableSessionCommands
                    .buildUpon()
                    .add(SessionCommand(ACTION_GET_AUDIO_SESSION_ID, Bundle.EMPTY))
                    .build()
                return MediaSession.ConnectionResult.accept(
                    sessionCommands,
                    baseResult.availablePlayerCommands
                )
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == ACTION_GET_AUDIO_SESSION_ID) {
                    val currentId = serviceExoPlayer?.audioSessionId ?: _serviceAudioSessionId.value
                    if (currentId > 0) {
                        _serviceAudioSessionId.value = currentId
                    }
                    val extras = Bundle().apply {
                        putInt(EXTRA_AUDIO_SESSION_ID, currentId)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, extras))
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(callback)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (!backgroundPlaybackAllowed) {
            player?.pause()
            stopSelf()
            return
        }
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        _serviceAudioSessionId.value = 0
        _serviceIsPlaying.value = false
        serviceScope.cancel()
        mediaSession?.run {
            player.removeListener(sessionListener)
            player.release()
            release()
            mediaSession = null
        }
        serviceExoPlayer = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_GET_AUDIO_SESSION_ID = "com.example.service.action.GET_AUDIO_SESSION_ID"
        const val EXTRA_AUDIO_SESSION_ID = "extra_audio_session_id"

        private val _serviceAudioSessionId = MutableStateFlow(0)
        val serviceAudioSessionId: StateFlow<Int> = _serviceAudioSessionId.asStateFlow()

        private val _serviceIsPlaying = MutableStateFlow(false)
        val serviceIsPlaying: StateFlow<Boolean> = _serviceIsPlaying.asStateFlow()

        /**
         * Builds an [ExoPlayer] instance wired with:
         * - [PulseEqualizerAudioProcessor] in its [DefaultAudioSink] for real-time 5-band biquad EQ & Bass Boost
         * - [C.WAKE_MODE_LOCAL] so CPU wake lock is automatically held ONLY while actively playing/buffering
         *   with the screen off and released immediately on pause/stop
         * - [AudioAttributes] with `handleAudioFocus = true` and `setHandleAudioBecomingNoisy(true)`
         */
        @OptIn(UnstableApi::class)
        fun buildExoPlayerWithEqualizerDsp(
            context: Context,
            audioProcessor: PulseEqualizerAudioProcessor = EqualizerController.sharedAudioProcessor
        ): ExoPlayer {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            val renderersFactory = object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): AudioSink {
                    return DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(arrayOf(audioProcessor))
                        .build()
                }
            }

            return ExoPlayer.Builder(context, renderersFactory)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
        }
    }
}
