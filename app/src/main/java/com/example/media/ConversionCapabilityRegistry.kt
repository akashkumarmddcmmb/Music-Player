package com.example.media

import android.media.MediaCodecList
import android.media.MediaFormat

data class ConversionProfile(
    val id: String,
    val title: String,
    val sourceDescription: String,
    val outputContainer: String,
    val outputExtension: String,
    val outputMimeType: String,
    val audioCodecMime: String,
    val videoCodecMime: String?,
    val requiresReEncoding: Boolean,
    val supportedBitratesKbps: List<Int>,
    val compatibilityNotes: String,
    val isHardwareSupportedOnDevice: Boolean
)

/**
 * Phase 12: Explicit Format Conversion Capability Registry.
 * Queries Android's MediaCodecList on the current device instead of promising arbitrary conversion.
 */
object ConversionCapabilityRegistry {

    fun isEncoderSupported(mimeType: String): Boolean {
        return runCatching {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            codecList.codecInfos.any { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        }.getOrDefault(false)
    }

    fun isDecoderSupported(mimeType: String): Boolean {
        return runCatching {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            codecList.codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
            }
        }.getOrDefault(false)
    }

    fun getSupportedProfiles(): List<ConversionProfile> {
        val aacEncoderAvailable = isEncoderSupported(MediaFormat.MIMETYPE_AUDIO_AAC)
        val avcEncoderAvailable = isEncoderSupported(MediaFormat.MIMETYPE_VIDEO_AVC)

        return listOf(
            ConversionProfile(
                id = "WAV_PCM_16BIT",
                title = "Uncompressed Studio WAV (PCM 16-bit)",
                sourceDescription = "Any decodable Audio/Video track (WAV, AAC, MP3, FLAC, OGG, MP4)",
                outputContainer = "RIFF WAVE (.wav)",
                outputExtension = "wav",
                outputMimeType = "audio/wav",
                audioCodecMime = MediaFormat.MIMETYPE_AUDIO_RAW,
                videoCodecMime = null,
                requiresReEncoding = true,
                supportedBitratesKbps = listOf(1411),
                compatibilityNotes = "Decodes source audio to 16-bit little-endian stereo/mono PCM and writes a verified RIFF WAVE header. Universal compatibility.",
                isHardwareSupportedOnDevice = true
            ),
            ConversionProfile(
                id = "M4A_AAC_TRANSCODE",
                title = "AAC-LC in MPEG-4 Audio (.m4a)",
                sourceDescription = "WAV, MP3, FLAC, OGG, or Video audio tracks",
                outputContainer = "MPEG-4 Audio (.m4a)",
                outputExtension = "m4a",
                outputMimeType = "audio/mp4",
                audioCodecMime = MediaFormat.MIMETYPE_AUDIO_AAC,
                videoCodecMime = null,
                requiresReEncoding = true,
                supportedBitratesKbps = listOf(96, 128, 192, 256),
                compatibilityNotes = "Decodes input PCM and re-encodes via Android platform AAC-LC hardware/software MediaCodec into an MP4/M4A container.",
                isHardwareSupportedOnDevice = aacEncoderAvailable
            ),
            ConversionProfile(
                id = "M4A_DIRECT_REMUX",
                title = "Lossless AAC Stream Remux (.m4a)",
                sourceDescription = "MP4/M4A/MOV containing AAC audio track",
                outputContainer = "MPEG-4 Audio (.m4a)",
                outputExtension = "m4a",
                outputMimeType = "audio/mp4",
                audioCodecMime = MediaFormat.MIMETYPE_AUDIO_AAC,
                videoCodecMime = null,
                requiresReEncoding = false,
                supportedBitratesKbps = listOf(128, 192),
                compatibilityNotes = "Zero generation loss when input already contains an AAC stream; falls back to AAC transcode if source is PCM/MP3.",
                isHardwareSupportedOnDevice = aacEncoderAvailable
            ),
            ConversionProfile(
                id = "MP4_VIDEO_REMUX_ROTATE",
                title = "MP4 Video Stream Remux & Orientation (.mp4)",
                sourceDescription = "MP4/MOV with H.264/HEVC video + AAC audio",
                outputContainer = "MPEG-4 Part 14 (.mp4)",
                outputExtension = "mp4",
                outputMimeType = "video/mp4",
                audioCodecMime = MediaFormat.MIMETYPE_AUDIO_AAC,
                videoCodecMime = MediaFormat.MIMETYPE_VIDEO_AVC,
                requiresReEncoding = false,
                supportedBitratesKbps = listOf(1500, 3000),
                compatibilityNotes = "Fast lossless packet remuxing with keyframe-aligned trimming and orientation matrix updates.",
                isHardwareSupportedOnDevice = avcEncoderAvailable
            )
        )
    }
}
