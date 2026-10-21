package com.example.media

import android.content.pm.ActivityInfo
import android.view.Window
import android.view.WindowManager
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.example.data.local.MediaEntity
import com.example.data.local.MediaType
import com.example.data.preferences.VideoOrientationPreference

/**
 * Snapshot of playback state captured during a transition from a temporary fallback player
 * to the authoritative [androidx.media3.session.MediaController] or across service reconnections.
 */
data class PlayerHandoffSnapshot(
    val transferredFromFallback: Boolean,
    val mediaItems: List<MediaItem>,
    val currentMediaItemIndex: Int,
    val currentPositionMs: Long,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val playbackSpeed: Float,
    val repeatMode: Int,
    val shuffleModeEnabled: Boolean,
    val volume: Float
) {
    val hadActiveItems: Boolean get() = mediaItems.isNotEmpty()
    val currentIndex: Int get() = currentMediaItemIndex
    val positionMs: Long get() = currentPositionMs
    val shuffleEnabled: Boolean get() = shuffleModeEnabled
}

/**
 * Computed scale and rotation for 0°, 90°, 180°, and 270° video frame rotation inside a viewport
 * so the video content rotates cleanly without rotating transport controls or subtitles.
 */
data class VideoFrameTransform(
    val normalizedDegrees: Int,
    val scaleX: Float,
    val scaleY: Float
) {
    val rotationDegrees: Int get() = normalizedDegrees
}

object PlayerHandoffAndVideoPolicy {

    /**
     * Safely transfers playback state from [outgoingFallback] to [incomingTarget] and guarantees
     * that [outgoingFallback] is stopped and released before [incomingTarget] starts playing,
     * preventing two active players from ever playing simultaneously.
     */
    fun handoffFallbackToAuthoritativePlayer(
        outgoingFallback: Player?,
        incomingTarget: Player
    ): PlayerHandoffSnapshot {
        if (outgoingFallback != null && outgoingFallback !== incomingTarget) {
            val count = outgoingFallback.mediaItemCount
            val items = ArrayList<MediaItem>(count)
            for (i in 0 until count) {
                items.add(outgoingFallback.getMediaItemAt(i))
            }
            val startIndex = outgoingFallback.currentMediaItemIndex.coerceAtLeast(0)
            val startPos = outgoingFallback.currentPosition.coerceAtLeast(0L)
            val shouldPlay = outgoingFallback.isPlaying || outgoingFallback.playWhenReady
            val speed = outgoingFallback.playbackParameters.speed.coerceIn(0.25f, 3.0f)
            val repeat = outgoingFallback.repeatMode
            val shuffle = outgoingFallback.shuffleModeEnabled
            val vol = outgoingFallback.volume.coerceIn(0f, 1f)

            // Stop and release outgoing fallback player FIRST so two players never overlap
            runCatching {
                outgoingFallback.playWhenReady = false
                outgoingFallback.pause()
                outgoingFallback.stop()
                outgoingFallback.clearMediaItems()
                outgoingFallback.release()
            }

            if (items.isNotEmpty()) {
                incomingTarget.setMediaItems(items, startIndex, startPos)
                incomingTarget.playbackParameters = PlaybackParameters(speed)
                incomingTarget.repeatMode = repeat
                incomingTarget.shuffleModeEnabled = shuffle
                incomingTarget.volume = vol
                incomingTarget.prepare()
                incomingTarget.playWhenReady = shouldPlay
                if (shouldPlay) {
                    incomingTarget.play()
                }
                return PlayerHandoffSnapshot(
                    transferredFromFallback = true,
                    mediaItems = items,
                    currentMediaItemIndex = startIndex,
                    currentPositionMs = startPos,
                    playWhenReady = shouldPlay,
                    isPlaying = shouldPlay,
                    playbackSpeed = speed,
                    repeatMode = repeat,
                    shuffleModeEnabled = shuffle,
                    volume = vol
                )
            }
        }

        // No outgoing fallback items: inspect incoming authoritative player's existing session state
        val existingCount = incomingTarget.mediaItemCount
        val existingItems = ArrayList<MediaItem>(existingCount)
        for (i in 0 until existingCount) {
            existingItems.add(incomingTarget.getMediaItemAt(i))
        }
        return PlayerHandoffSnapshot(
            transferredFromFallback = false,
            mediaItems = existingItems,
            currentMediaItemIndex = incomingTarget.currentMediaItemIndex.coerceAtLeast(0),
            currentPositionMs = incomingTarget.currentPosition.coerceAtLeast(0L),
            playWhenReady = incomingTarget.playWhenReady,
            isPlaying = incomingTarget.isPlaying,
            playbackSpeed = incomingTarget.playbackParameters.speed.coerceIn(0.25f, 3.0f),
            repeatMode = incomingTarget.repeatMode,
            shuffleModeEnabled = incomingTarget.shuffleModeEnabled,
            volume = incomingTarget.volume.coerceIn(0f, 1f)
        )
    }

    /**
     * Determines whether `FLAG_KEEP_SCREEN_ON` should be active on the host [Window].
     */
    fun shouldKeepVideoScreenAwake(
        isActivityResumed: Boolean,
        isNowPlayingOverlayVisible: Boolean,
        mediaType: MediaType?,
        isPlaying: Boolean,
        hasPlaybackError: Boolean,
        keepScreenOnPreferenceEnabled: Boolean = true
    ): Boolean {
        if (!keepScreenOnPreferenceEnabled) return false
        if (!isActivityResumed) return false
        if (!isNowPlayingOverlayVisible) return false
        if (mediaType != MediaType.VIDEO) return false
        if (!isPlaying) return false
        if (hasPlaybackError) return false
        return true
    }

    fun shouldKeepVideoScreenAwake(
        keepScreenOnPreference: Boolean,
        isActivityResumedOrStarted: Boolean,
        isNowPlayingVisible: Boolean,
        currentMedia: MediaEntity?,
        isPlaying: Boolean,
        playbackError: String?
    ): Boolean = shouldKeepVideoScreenAwake(
        isActivityResumed = isActivityResumedOrStarted,
        isNowPlayingOverlayVisible = isNowPlayingVisible,
        mediaType = currentMedia?.mediaType,
        isPlaying = isPlaying,
        hasPlaybackError = playbackError != null,
        keepScreenOnPreferenceEnabled = keepScreenOnPreference
    )

    /**
     * Applies or clears [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON] on [window] idempotently.
     */
    fun applyWindowKeepScreenOn(window: Window?, shouldKeepAwake: Boolean) {
        if (window == null) return
        val currentFlags = window.attributes?.flags ?: 0
        val isCurrentlySet = (currentFlags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (shouldKeepAwake && !isCurrentlySet) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else if (!shouldKeepAwake && isCurrentlySet) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * Resolves the Android [ActivityInfo] screen orientation constant for the current UI state.
     */
    fun resolveRequestedScreenOrientation(
        isNowPlayingOverlayVisible: Boolean,
        mediaType: MediaType?,
        isFullscreenVideo: Boolean,
        orientationPreference: VideoOrientationPreference
    ): Int {
        if (!isNowPlayingOverlayVisible || mediaType != MediaType.VIDEO) {
            return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        return when (orientationPreference) {
            VideoOrientationPreference.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            VideoOrientationPreference.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            VideoOrientationPreference.AUTO -> {
                if (isFullscreenVideo) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_USER
                }
            }
        }
    }

    fun resolveRequestedScreenOrientation(
        isNowPlayingVisible: Boolean,
        currentMedia: MediaEntity?,
        isFullscreenVideo: Boolean,
        orientationPreference: VideoOrientationPreference
    ): Int = resolveRequestedScreenOrientation(
        isNowPlayingOverlayVisible = isNowPlayingVisible,
        mediaType = currentMedia?.mediaType,
        isFullscreenVideo = isFullscreenVideo,
        orientationPreference = orientationPreference
    )

    /**
     * Normalizes video rotation degrees to `{0, 90, 180, 270}` and computes aspect-compensated
     * scaling so a 90° or 270° rotated video frame fits inside `[containerWidth, containerHeight]`
     * without clipping or rotating transport controls/subtitles.
     */
    fun computeVideoFrameTransform(
        rotationDegrees: Int,
        containerWidthPx: Float,
        containerHeightPx: Float
    ): VideoFrameTransform {
        val norm = ((rotationDegrees % 360) + 360) % 360
        val snapped = when (norm) {
            in 45..134 -> 90
            in 135..224 -> 180
            in 225..314 -> 270
            else -> 0
        }
        if (snapped == 90 || snapped == 270) {
            val safeW = containerWidthPx.coerceAtLeast(1f)
            val safeH = containerHeightPx.coerceAtLeast(1f)
            val fitScale = minOf(safeW / safeH, safeH / safeW).coerceIn(0.35f, 1.0f)
            return VideoFrameTransform(
                normalizedDegrees = snapped,
                scaleX = fitScale,
                scaleY = fitScale
            )
        }
        return VideoFrameTransform(
            normalizedDegrees = snapped,
            scaleX = 1f,
            scaleY = 1f
        )
    }

    fun computeVideoFrameTransform(
        rotationDegrees: Int,
        containerWidthPx: Int,
        containerHeightPx: Int
    ): VideoFrameTransform = computeVideoFrameTransform(
        rotationDegrees = rotationDegrees,
        containerWidthPx = containerWidthPx.toFloat(),
        containerHeightPx = containerHeightPx.toFloat()
    )
}
