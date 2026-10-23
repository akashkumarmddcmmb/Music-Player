package com.example.media

import android.content.Context
import android.media.MediaFormat
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.DefaultMediaCodecAdapterFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import java.io.IOException

/**
 * Resilient 4K UHD (3840x2160) & High-Bitrate Video Decoder Engine for Media3 ExoPlayer.
 *
 * Solves `ERROR_CODE_DECODER_INIT_FAILED` on 4K (3840x2160) and QHD (2560x1440) videos across
 * all Android devices by:
 * 1. Ensuring both hardware and platform C2/OMX software decoders (`c2.android.*`, `OMX.google.*`)
 *    are always discoverable via [Resilient4KMediaCodecSelector].
 * 2. Prioritizing decoders whose `VideoCapabilities.isSizeSupported(width, height)` actually
 *    supports 4K resolution when `width > 1920 || height > 1080`, instead of blindly failing on
 *    1080p-limited hardware decoders.
 * 3. Stripping `KEY_OPERATING_RATE`, `KEY_PRIORITY`, `KEY_MAX_WIDTH`, `KEY_MAX_HEIGHT`,
 *    `KEY_MAX_INPUT_SIZE`, `KEY_PROFILE`, and `KEY_LEVEL` when configuring high-resolution codecs
 *    so Android's `ResourceManagerService` and adaptive buffer allocator do not reject 4K streams.
 * 4. Providing progressive fallback strategies (Sanitized 4K Format -> Original Format ->
 *    Minimal SDR-Safe Format -> Clamped XML-Limit Gate Bypass Format) across all candidate
 *    hardware and software decoders.
 */
@OptIn(UnstableApi::class)
object Resilient4KMediaCodecSelector : MediaCodecSelector {
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        val primary = runCatching {
            MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )
        }.getOrDefault(emptyList())

        val unrestricted = if (requiresSecureDecoder || requiresTunnelingDecoder) {
            runCatching {
                MediaCodecUtil.getDecoderInfos(mimeType, false, false)
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val combined = LinkedHashMap<String, MediaCodecInfo>()
        for (info in primary) {
            combined[info.name] = info
        }
        for (info in unrestricted) {
            if (!combined.containsKey(info.name)) {
                combined[info.name] = info
            }
        }
        return combined.values.toList()
    }
}

@OptIn(UnstableApi::class)
class Resilient4KMediaCodecAdapterFactory(
    context: Context
) : MediaCodecAdapter.Factory {

    private val appContext = context.applicationContext
    private val defaultFactory = DefaultMediaCodecAdapterFactory(appContext)

    companion object {
        @Volatile
        var preferSoftwareForHighRes: Boolean = false
    }

    override fun createAdapter(configuration: MediaCodecAdapter.Configuration): MediaCodecAdapter {
        val sampleMime = configuration.format.sampleMimeType.orEmpty()
        val isVideo = sampleMime.startsWith("video/") ||
            configuration.mediaFormat.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/")

        if (!isVideo) {
            return defaultFactory.createAdapter(configuration)
        }

        val width = configuration.format.width.takeIf { it > 0 }
            ?: configuration.mediaFormat.getIntegerSafe(MediaFormat.KEY_WIDTH, 1920)
        val height = configuration.format.height.takeIf { it > 0 }
            ?: configuration.mediaFormat.getIntegerSafe(MediaFormat.KEY_HEIGHT, 1080)
        val isHighRes = width > 1920 || height > 1080

        // Standard <= 1080p fast path first when software preference is not forced
        if (!isHighRes && !preferSoftwareForHighRes) {
            runCatching {
                return defaultFactory.createAdapter(configuration)
            }
        }

        val candidates = buildOrderedCandidateDecoders(
            initialCodec = configuration.codecInfo,
            format = configuration.format,
            width = width,
            height = height,
            isHighRes = isHighRes
        )

        var lastError: Throwable? = null

        // Pass 1: Try each candidate decoder with Sanitized 4K Format, Original Format, and Minimal SDR-Safe Format
        for (codec in candidates) {
            val mimeForCodec = codec.codecMimeType.ifBlank {
                sampleMime.ifBlank { "video/avc" }
            }

            val formatStrategies = buildFormatStrategiesForCodec(
                originalMediaFormat = configuration.mediaFormat,
                format = configuration.format,
                targetMime = mimeForCodec,
                width = width,
                height = height,
                isHighRes = isHighRes
            )

            for (candidateMediaFormat in formatStrategies) {
                try {
                    val candidateConfig = MediaCodecAdapter.Configuration.createForVideoDecoding(
                        codec,
                        candidateMediaFormat,
                        configuration.format,
                        configuration.surface,
                        configuration.crypto
                    )
                    return defaultFactory.createAdapter(candidateConfig)
                } catch (t: Throwable) {
                    lastError = t
                }
            }
        }

        // Pass 2 (Last-Resort XML Resolution Gate Bypass for 4K on strict 1080p-capped devices):
        // Some devices declare max="1920x1080" or "2048x2048" in media_codecs.xml for all decoders,
        // causing CCodecConfig to reject KEY_WIDTH=3840 / KEY_HEIGHT=2160 during configure() even
        // though the software/hardware bitstream decoder dynamically resizes on the SPS/VPS header.
        if (isHighRes) {
            val softwareFirst = candidates.sortedByDescending { isSoftwareDecoder(it) }
            for (codec in softwareFirst) {
                val mimeForCodec = codec.codecMimeType.ifBlank {
                    sampleMime.ifBlank { "video/avc" }
                }
                val videoCaps = codec.capabilities?.videoCapabilities
                val clampedWidth = (videoCaps?.supportedWidths?.upper ?: 1920).coerceIn(640, 1920)
                val clampedHeight = (videoCaps?.supportedHeights?.upper ?: 1080).coerceIn(360, 1088)

                val clampedWithCsd = createMinimalVideoMediaFormat(
                    mime = mimeForCodec,
                    width = clampedWidth,
                    height = clampedHeight,
                    sourceMediaFormat = configuration.mediaFormat,
                    format = configuration.format,
                    includeCsd = true
                )
                val clampedWithoutCsd = createMinimalVideoMediaFormat(
                    mime = mimeForCodec,
                    width = clampedWidth,
                    height = clampedHeight,
                    sourceMediaFormat = configuration.mediaFormat,
                    format = configuration.format,
                    includeCsd = false
                )

                for (clampedFormat in listOf(clampedWithCsd, clampedWithoutCsd)) {
                    try {
                        val clampedConfig = MediaCodecAdapter.Configuration.createForVideoDecoding(
                            codec,
                            clampedFormat,
                            configuration.format,
                            configuration.surface,
                            configuration.crypto
                        )
                        return defaultFactory.createAdapter(clampedConfig)
                    } catch (t: Throwable) {
                        lastError = t
                    }
                }
            }
        }

        throw (lastError as? IOException) ?: IOException(
            "Unable to initialize any video decoder for ${width}x${height} ($sampleMime)",
            lastError
        )
    }

    private fun buildOrderedCandidateDecoders(
        initialCodec: MediaCodecInfo,
        format: Format,
        width: Int,
        height: Int,
        isHighRes: Boolean
    ): List<MediaCodecInfo> {
        val primaryMime = format.sampleMimeType.orEmpty()
        val altMime = runCatching {
            MediaCodecUtil.getAlternativeCodecMimeType(format)
        }.getOrNull()

        val allMap = LinkedHashMap<String, MediaCodecInfo>()
        allMap[initialCodec.name] = initialCodec

        if (primaryMime.isNotBlank()) {
            val list = runCatching {
                MediaCodecUtil.getDecoderInfos(primaryMime, false, false)
            }.getOrDefault(emptyList())
            for (info in list) {
                if (!allMap.containsKey(info.name)) {
                    allMap[info.name] = info
                }
            }
        }

        if (!altMime.isNullOrBlank()) {
            val altList = runCatching {
                MediaCodecUtil.getDecoderInfos(altMime, false, false)
            }.getOrDefault(emptyList())
            for (info in altList) {
                if (!allMap.containsKey(info.name)) {
                    allMap[info.name] = info
                }
            }
        }

        val allCandidates = allMap.values.toList()
        if (!isHighRes && !preferSoftwareForHighRes) {
            return allCandidates
        }

        return allCandidates.sortedWith(
            Comparator { a, b ->
                val aSw = isSoftwareDecoder(a)
                val bSw = isSoftwareDecoder(b)
                val aSupports4K = supportsDimensions(a, width, height)
                val bSupports4K = supportsDimensions(b, width, height)

                if (preferSoftwareForHighRes && aSw != bSw) {
                    return@Comparator if (aSw) -1 else 1
                }
                if (aSupports4K != bSupports4K) {
                    return@Comparator if (aSupports4K) -1 else 1
                }
                // If neither explicitly claims 4K in XML, prefer software decoder over a 1080p-capped HW ASIC
                if (!aSupports4K && !bSupports4K && aSw != bSw) {
                    return@Comparator if (aSw) -1 else 1
                }
                0
            }
        )
    }

    private fun supportsDimensions(codec: MediaCodecInfo, width: Int, height: Int): Boolean {
        val videoCaps = codec.capabilities?.videoCapabilities ?: return false
        return runCatching {
            videoCaps.isSizeSupported(width, height) ||
                (width != height && videoCaps.isSizeSupported(height, width))
        }.getOrDefault(false)
    }

    private fun isSoftwareDecoder(codec: MediaCodecInfo): Boolean {
        val lower = codec.name.lowercase()
        return codec.softwareOnly ||
            lower.startsWith("c2.android.") ||
            lower.startsWith("omx.google.") ||
            lower.contains(".sw.") ||
            lower.contains("ffmpeg") ||
            lower.contains("dav1d") ||
            lower.contains("gav1")
    }

    private fun buildFormatStrategiesForCodec(
        originalMediaFormat: MediaFormat,
        format: Format,
        targetMime: String,
        width: Int,
        height: Int,
        isHighRes: Boolean
    ): List<MediaFormat> {
        val sanitized4K = createSanitizedHighResMediaFormat(
            source = originalMediaFormat,
            format = format,
            targetMime = targetMime,
            width = width,
            height = height
        )
        val minimalSdrSafe = createMinimalVideoMediaFormat(
            mime = targetMime,
            width = width,
            height = height,
            sourceMediaFormat = originalMediaFormat,
            format = format,
            includeCsd = true
        )
        return if (isHighRes) {
            listOf(sanitized4K, minimalSdrSafe, originalMediaFormat)
        } else {
            listOf(originalMediaFormat, sanitized4K, minimalSdrSafe)
        }
    }

    private fun createSanitizedHighResMediaFormat(
        source: MediaFormat,
        format: Format,
        targetMime: String,
        width: Int,
        height: Int
    ): MediaFormat {
        val out = MediaFormat.createVideoFormat(targetMime, width, height)
        copyCsdBuffers(source, format, out)

        // Copy rotation if present
        if (source.containsKey(MediaFormat.KEY_ROTATION)) {
            runCatching {
                out.setInteger(MediaFormat.KEY_ROTATION, source.getInteger(MediaFormat.KEY_ROTATION))
            }
        }
        // Intentionally omit KEY_MAX_WIDTH, KEY_MAX_HEIGHT, KEY_MAX_INPUT_SIZE,
        // KEY_OPERATING_RATE, KEY_PRIORITY, KEY_PROFILE, and KEY_LEVEL so 4K codecs
        // never fail ResourceManager frame-rate checks or adaptive double-buffer allocation.
        return out
    }

    private fun createMinimalVideoMediaFormat(
        mime: String,
        width: Int,
        height: Int,
        sourceMediaFormat: MediaFormat,
        format: Format,
        includeCsd: Boolean
    ): MediaFormat {
        val out = MediaFormat.createVideoFormat(mime, width, height)
        if (includeCsd) {
            copyCsdBuffers(sourceMediaFormat, format, out)
        }
        return out
    }

    private fun copyCsdBuffers(source: MediaFormat, format: Format, target: MediaFormat) {
        var copiedAny = false
        for (i in 0..3) {
            val key = "csd-$i"
            if (source.containsKey(key)) {
                val buf = source.getByteBuffer(key)
                if (buf != null) {
                    val dup = buf.duplicate()
                    target.setByteBuffer(key, dup)
                    copiedAny = true
                }
            }
        }
        if (!copiedAny && format.initializationData.isNotEmpty()) {
            format.initializationData.forEachIndexed { index, bytes ->
                target.setByteBuffer("csd-$index", java.nio.ByteBuffer.wrap(bytes))
            }
        }
    }

    private fun MediaFormat.getIntegerSafe(key: String, defaultValue: Int): Int {
        return if (containsKey(key)) {
            runCatching { getInteger(key) }.getOrDefault(defaultValue)
        } else {
            defaultValue
        }
    }
}
