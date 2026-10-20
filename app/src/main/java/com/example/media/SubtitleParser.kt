package com.example.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class SubtitleCueItem(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String
)

enum class SubtitleFormat {
    SRT,
    WEBVTT,
    UNSUPPORTED
}

sealed class SubtitleLoadResult {
    data class Success(
        val sourceName: String,
        val format: SubtitleFormat,
        val cues: List<SubtitleCueItem>
    ) : SubtitleLoadResult()

    data class Error(val reason: String) : SubtitleLoadResult()
}

/**
 * Phase 9: Real SRT and WebVTT Subtitle Parser and Validator.
 * Does not label a subtitle format as supported merely because of its extension;
 * inspects and validates actual timestamped cue blocks and reports missing/corrupt errors clearly.
 */
object SubtitleParser {

    private val srtTimestampRegex = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})"""
    )

    private val vttShortTimestampRegex = Regex(
        """(\d{2}):(\d{2})[.](\d{3})\s*-->\s*(\d{2}):(\d{2})[.](\d{3})"""
    )

    suspend fun loadAndValidateSubtitleUri(
        context: Context,
        uri: Uri,
        displayNameHint: String = "External Subtitle"
    ): SubtitleLoadResult = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            } ?: return@withContext SubtitleLoadResult.Error(
                "Subtitle file could not be opened or URI permission was revoked."
            )

            parseSubtitleContent(content, displayNameHint)
        } catch (e: SecurityException) {
            SubtitleLoadResult.Error("Permission denied reading subtitle URI: ${e.message ?: "Revoked access"}")
        } catch (e: Exception) {
            SubtitleLoadResult.Error("Failed to read subtitle file: ${e.message ?: "Unknown I/O error"}")
        }
    }

    fun parseSubtitleContent(
        rawContent: String,
        sourceName: String = "Subtitle"
    ): SubtitleLoadResult {
        val cleaned = rawContent.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) {
            return SubtitleLoadResult.Error("Subtitle file '$sourceName' is empty.")
        }

        val isWebVttHeader = cleaned.startsWith("WEBVTT", ignoreCase = true)
        val lines = cleaned.lines()
        val cues = mutableListOf<SubtitleCueItem>()

        var i = 0
        var cueCounter = 1
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.equals("WEBVTT", ignoreCase = true) || line.startsWith("NOTE")) {
                i++
                continue
            }

            // Check if current line or next line is a timestamp line
            var timingLine = line
            val fullMatch = srtTimestampRegex.find(timingLine)
            val shortMatch = if (fullMatch == null) vttShortTimestampRegex.find(timingLine) else null

            val resolvedTimingMatch: MatchResult?
            val isShortVtt: Boolean

            if (fullMatch != null) {
                resolvedTimingMatch = fullMatch
                isShortVtt = false
                i++
            } else if (shortMatch != null) {
                resolvedTimingMatch = shortMatch
                isShortVtt = true
                i++
            } else if (i + 1 < lines.size) {
                val nextLine = lines[i + 1].trim()
                val nextFull = srtTimestampRegex.find(nextLine)
                val nextShort = if (nextFull == null) vttShortTimestampRegex.find(nextLine) else null
                if (nextFull != null || nextShort != null) {
                    resolvedTimingMatch = nextFull ?: nextShort
                    isShortVtt = nextFull == null
                    i += 2
                } else {
                    i++
                    continue
                }
            } else {
                i++
                continue
            }

            val (startMs, endMs) = if (!isShortVtt && resolvedTimingMatch != null) {
                val g = resolvedTimingMatch.groupValues
                val s = toMillis(g[1].toLong(), g[2].toLong(), g[3].toLong(), g[4].toLong())
                val e = toMillis(g[5].toLong(), g[6].toLong(), g[7].toLong(), g[8].toLong())
                s to e
            } else if (resolvedTimingMatch != null) {
                val g = resolvedTimingMatch.groupValues
                val s = toMillis(0L, g[1].toLong(), g[2].toLong(), g[3].toLong())
                val e = toMillis(0L, g[4].toLong(), g[5].toLong(), g[6].toLong())
                s to e
            } else {
                continue
            }

            val textBuilder = StringBuilder()
            while (i < lines.size && lines[i].trim().isNotEmpty()) {
                if (textBuilder.isNotEmpty()) textBuilder.append("\n")
                // Strip basic HTML-like formatting tags for clean rendering
                val stripped = lines[i].trim().replace(Regex("<[^>]*>"), "")
                textBuilder.append(stripped)
                i++
            }

            val cueText = textBuilder.toString().trim()
            if (cueText.isNotEmpty() && endMs > startMs) {
                cues.add(
                    SubtitleCueItem(
                        index = cueCounter++,
                        startMs = startMs,
                        endMs = endMs,
                        text = cueText
                    )
                )
            }
        }

        if (cues.isEmpty()) {
            return SubtitleLoadResult.Error(
                "Invalid or unsupported subtitle syntax in '$sourceName': no valid SRT or WebVTT timing cues found."
            )
        }

        val format = if (isWebVttHeader) SubtitleFormat.WEBVTT else SubtitleFormat.SRT
        return SubtitleLoadResult.Success(
            sourceName = sourceName,
            format = format,
            cues = cues
        )
    }

    /**
     * Finds the active subtitle cue(s) at [playbackPositionMs] taking into account
     * user-configured [syncOffsetMs].
     */
    fun findActiveCueText(
        cues: List<SubtitleCueItem>,
        playbackPositionMs: Long,
        syncOffsetMs: Long
    ): String? {
        if (cues.isEmpty()) return null
        val adjustedPos = playbackPositionMs - syncOffsetMs
        val active = cues.filter { adjustedPos in it.startMs..it.endMs }
        if (active.isEmpty()) return null
        return active.joinToString("\n") { it.text }
    }

    private fun toMillis(hours: Long, minutes: Long, seconds: Long, millis: Long): Long {
        return (((hours * 60L + minutes) * 60L) + seconds) * 1000L + millis
    }
}
