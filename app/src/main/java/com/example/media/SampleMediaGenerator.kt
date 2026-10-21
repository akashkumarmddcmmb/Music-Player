package com.example.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.net.Uri
import com.example.data.local.MediaEntity
import com.example.data.local.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates real, valid, playable audio (.wav, .m4a) and video (.mp4 with H.264/AVC + AAC)
 * sample fixtures directly on the device using Android MediaCodec, IsoMp4Muxer, and PCM synthesis.
 *
 * This guarantees that users and automated tests in fresh emulators without pre-loaded SD card
 * files can immediately test real audio playback, video playback, subtitle rendering,
 * audio trimming, video trimming/rotation, audio extraction, and format conversion.
 */
class SampleMediaGenerator(private val context: Context) {

    suspend fun ensureLegalSampleMediaGenerated(): List<MediaEntity> = withContext(Dispatchers.IO) {
        val sampleDir = File(context.filesDir, "pulse_samples").apply { mkdirs() }
        val results = mutableListOf<MediaEntity>()

        // 1. Generate Stereo WAV Audio Track 1: "Acoustic Dawn (432Hz & 528Hz Harmonic)"
        val wavFile1 = File(sampleDir, "Pulse_Acoustic_Dawn.wav")
        if (!wavFile1.exists() || wavFile1.length() < 1024L) {
            generateStereoWavFile(
                outputFile = wavFile1,
                durationSeconds = 8,
                freqLeftHz = 432.0,
                freqRightHz = 540.0
            )
        }
        results.add(
            MediaEntity(
                uriString = Uri.fromFile(wavFile1).toString(),
                mediaStoreId = null,
                title = "Acoustic Dawn (Harmonic Study)",
                artist = "PulsePlay Studio Synthesis",
                album = "Reference Audio Fixtures",
                folderName = "Pulse Studio Samples",
                durationMs = 8_000L,
                sizeBytes = wavFile1.length(),
                mimeType = "audio/wav",
                mediaType = MediaType.AUDIO,
                dateAddedEpochSec = System.currentTimeMillis() / 1000L,
                isAvailable = true,
                isGeneratedSample = true
            )
        )

        // 2. Generate Stereo WAV Audio Track 2: "Sub-Bass & Treble Sweep (EQ Reference)"
        val wavFile2 = File(sampleDir, "Pulse_EQ_Reference.wav")
        if (!wavFile2.exists() || wavFile2.length() < 1024L) {
            generateStereoWavFile(
                outputFile = wavFile2,
                durationSeconds = 10,
                freqLeftHz = 110.0,
                freqRightHz = 880.0
            )
        }
        results.add(
            MediaEntity(
                uriString = Uri.fromFile(wavFile2).toString(),
                mediaStoreId = null,
                title = "Sub-Bass & Treble EQ Reference",
                artist = "PulsePlay Audio Lab",
                album = "Reference Audio Fixtures",
                folderName = "Pulse Studio Samples",
                durationMs = 10_000L,
                sizeBytes = wavFile2.length(),
                mimeType = "audio/wav",
                mediaType = MediaType.AUDIO,
                dateAddedEpochSec = (System.currentTimeMillis() / 1000L) - 60,
                isAvailable = true,
                isGeneratedSample = true
            )
        )

        // 3. Generate AAC M4A Audio Track 3 using hardware/platform MediaCodec + IsoMp4Muxer
        val m4aFile = File(sampleDir, "Pulse_Midnight_Synth.m4a")
        if (!m4aFile.exists() || m4aFile.length() < 512L) {
            runCatching {
                generateAacM4aFile(m4aFile, durationSeconds = 6, baseFreqHz = 330.0)
            }
        }
        if (m4aFile.exists() && m4aFile.length() > 256L) {
            results.add(
                MediaEntity(
                    uriString = Uri.fromFile(m4aFile).toString(),
                    mediaStoreId = null,
                    title = "Midnight Synth (AAC-LC M4A)",
                    artist = "PulsePlay Codec Engine",
                    album = "AAC Studio Sessions",
                    folderName = "Pulse Studio Samples",
                    durationMs = 6_000L,
                    sizeBytes = m4aFile.length(),
                    mimeType = "audio/mp4",
                    mediaType = MediaType.AUDIO,
                    dateAddedEpochSec = (System.currentTimeMillis() / 1000L) - 120,
                    isAvailable = true,
                    isGeneratedSample = true
                )
            )
        }

        // 4. Generate Real Playable MP4 Video (H.264 Video + AAC Audio) + Companion SRT & VTT Subtitles
        val mp4File = File(sampleDir, "Pulse_Cinema_Test_Reel.mp4")
        val srtFile = File(sampleDir, "Pulse_Cinema_Test_Reel.srt")
        val vttFile = File(sampleDir, "Pulse_Cinema_Test_Reel.vtt")

        if (!srtFile.exists()) {
            srtFile.writeText(
                """
                1
                00:00:00,200 --> 00:00:02,000
                Welcome to PulsePlay Original Cinema Engine

                2
                00:00:02,100 --> 00:00:04,000
                Testing real-time SRT/WebVTT subtitle sync & styling

                3
                00:00:04,100 --> 00:00:06,000
                100% Offline • Zero Network • MediaCodec Verified
                """.trimIndent()
            )
        }

        if (!vttFile.exists()) {
            vttFile.writeText(
                """
                WEBVTT

                00:00.200 --> 00:02.000
                PulsePlay WebVTT Subtitle Stream Active

                00:02.100 --> 00:04.000
                Adjust font scale or sync offset in real time

                00:04.100 --> 00:06.000
                Ready for Video Trimming & Audio Extraction
                """.trimIndent()
            )
        }

        if (!mp4File.exists() || mp4File.length() < 1024L) {
            runCatching {
                generateMp4VideoWithAudio(
                    outputFile = mp4File,
                    width = 640,
                    height = 360,
                    durationSeconds = 6
                )
            }
        }

        if (mp4File.exists() && mp4File.length() > 512L) {
            results.add(
                MediaEntity(
                    uriString = Uri.fromFile(mp4File).toString(),
                    mediaStoreId = null,
                    title = "Cinema Calibration Reel (640x360 MP4)",
                    artist = "PulsePlay Video Lab",
                    album = "Reference Video Fixtures",
                    folderName = "Pulse Video Vault",
                    durationMs = 6_000L,
                    sizeBytes = mp4File.length(),
                    mimeType = "video/mp4",
                    mediaType = MediaType.VIDEO,
                    width = 640,
                    height = 360,
                    dateAddedEpochSec = System.currentTimeMillis() / 1000L,
                    isAvailable = true,
                    isGeneratedSample = true
                )
            )
        }

        results
    }

    fun getBundledSampleSubtitleFiles(): List<File> {
        val sampleDir = File(context.filesDir, "pulse_samples")
        return listOf(
            File(sampleDir, "Pulse_Cinema_Test_Reel.srt"),
            File(sampleDir, "Pulse_Cinema_Test_Reel.vtt")
        ).filter { it.exists() }
    }

    fun generateStereoWavFile(
        outputFile: File,
        durationSeconds: Int,
        sampleRate: Int = 44100,
        freqLeftHz: Double = 440.0,
        freqRightHz: Double = 554.37
    ) {
        val numChannels = 2
        val bitsPerSample = 16
        val totalSamples = sampleRate * durationSeconds
        val dataSize = totalSamples * numChannels * (bitsPerSample / 8)
        val totalFileSize = 36 + dataSize

        FileOutputStream(outputFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(totalFileSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // PCM chunk size
            header.putShort(1) // AudioFormat = 1 (PCM)
            header.putShort(numChannels.toShort())
            header.putInt(sampleRate)
            val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
            header.putInt(byteRate)
            val blockAlign = (numChannels * (bitsPerSample / 8)).toShort()
            header.putShort(blockAlign)
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            fos.write(header.array())

            val chunkFrames = 2048
            val pcmBuffer = ByteBuffer.allocate(chunkFrames * numChannels * 2)
                .order(ByteOrder.LITTLE_ENDIAN)

            var frameIndex = 0
            while (frameIndex < totalSamples) {
                pcmBuffer.clear()
                val framesToWrite = minOf(chunkFrames, totalSamples - frameIndex)
                for (i in 0 until framesToWrite) {
                    val t = (frameIndex + i).toDouble() / sampleRate.toDouble()
                    // Gentle envelope Modulation so it sounds like a warm studio chord pad
                    val envelope = 0.65 + 0.25 * sin(2.0 * PI * 0.5 * t)
                    val sampleL = (sin(2.0 * PI * freqLeftHz * t) * envelope * 14000).toInt().toShort()
                    val sampleR = (sin(2.0 * PI * freqRightHz * t) * envelope * 14000).toInt().toShort()
                    pcmBuffer.putShort(sampleL)
                    pcmBuffer.putShort(sampleR)
                }
                fos.write(pcmBuffer.array(), 0, framesToWrite * numChannels * 2)
                frameIndex += framesToWrite
            }
        }
    }

    fun generateAacM4aFile(
        outputFile: File,
        durationSeconds: Int,
        sampleRate: Int = 44100,
        baseFreqHz: Double = 330.0
    ) {
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val format = MediaFormat.createAudioFormat(mime, sampleRate, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        var encoder: MediaCodec? = null
        var muxer: IsoMp4Muxer? = null
        var muxerStarted = false
        var samplesWritten = 0
        var lastPtsUs = -1L

        try {
            encoder = MediaCodec.createEncoderByType(mime)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val totalSamples = sampleRate * durationSeconds
            var samplesSubmitted = 0
            var inputDone = false
            var outputDone = false
            var outputFormat: MediaFormat? = null
            var trackIndex = -1
            val bufferInfo = MediaCodec.BufferInfo()
            val muxBufferInfo = MediaCodec.BufferInfo()
            var emptyPolls = 0

            while (!outputDone && emptyPolls < 200) {
                if (!inputDone) {
                    val inIndex = encoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = encoder.getInputBuffer(inIndex)!!
                        inBuf.clear()
                        if (samplesSubmitted >= totalSamples) {
                            encoder.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                (samplesSubmitted * 1_000_000L) / sampleRate,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val maxFrames = minOf(1024, totalSamples - samplesSubmitted, inBuf.remaining() / 4)
                            for (i in 0 until maxFrames) {
                                val t = (samplesSubmitted + i).toDouble() / sampleRate.toDouble()
                                val l = (sin(2.0 * PI * baseFreqHz * t) * 12000).toInt().toShort()
                                val r = (sin(2.0 * PI * (baseFreqHz * 1.25) * t) * 12000).toInt().toShort()
                                inBuf.put((l.toInt() and 0xFF).toByte())
                                inBuf.put(((l.toInt() shr 8) and 0xFF).toByte())
                                inBuf.put((r.toInt() and 0xFF).toByte())
                                inBuf.put(((r.toInt() shr 8) and 0xFF).toByte())
                            }
                            val ptsUs = (samplesSubmitted * 1_000_000L) / sampleRate
                            samplesSubmitted += maxFrames
                            encoder.queueInputBuffer(inIndex, 0, maxFrames * 4, ptsUs, 0)
                        }
                    }
                }

                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = encoder.outputFormat
                        emptyPolls = 0
                    }
                    outIndex >= 0 -> {
                        emptyPolls = 0
                        val outBuf = encoder.getOutputBuffer(outIndex)
                        val isCodecConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        if (outBuf != null && bufferInfo.size > 0 && !isCodecConfig) {
                            if (!muxerStarted) {
                                val fmt = outputFormat ?: encoder.outputFormat
                                muxer = IsoMp4Muxer(outputFile, majorBrand = "M4A ")
                                trackIndex = muxer!!.addTrack(fmt)
                                muxer!!.start()
                                muxerStarted = true
                            }
                            val rawPtsUs = bufferInfo.presentationTimeUs.coerceAtLeast(0L)
                            val safePtsUs = if (rawPtsUs <= lastPtsUs) lastPtsUs + 100L else rawPtsUs
                            lastPtsUs = safePtsUs

                            val safeFlags = bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME
                            muxBufferInfo.set(bufferInfo.offset, bufferInfo.size, safePtsUs, safeFlags)

                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer?.writeSampleData(trackIndex, outBuf, muxBufferInfo)
                            samplesWritten++
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (isEos) {
                            outputDone = true
                        }
                    }
                    else -> {
                        emptyPolls++
                    }
                }
            }
        } finally {
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            if (muxerStarted && samplesWritten > 0) {
                runCatching { muxer?.stop() }
            }
            if (muxer != null) {
                runCatching { muxer?.release() }
            }
            if (samplesWritten == 0 && outputFile.exists()) {
                outputFile.delete()
            }
        }
    }

    private fun generateMp4VideoWithAudio(
        outputFile: File,
        width: Int,
        height: Int,
        durationSeconds: Int
    ) {
        val frameRate = 15
        val totalFrames = frameRate * durationSeconds
        val videoMime = MediaFormat.MIMETYPE_VIDEO_AVC
        val audioMime = MediaFormat.MIMETYPE_AUDIO_AAC
        val sampleRate = 44100

        @Suppress("DEPRECATION")
        val videoFormat = MediaFormat.createVideoFormat(videoMime, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 600_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val audioFormat = MediaFormat.createAudioFormat(audioMime, sampleRate, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        var videoEncoder: MediaCodec? = null
        var audioEncoder: MediaCodec? = null
        var muxer: IsoMp4Muxer? = null
        var muxerStarted = false
        var samplesWritten = 0
        var lastVideoPtsUs = -1L
        var lastAudioPtsUs = -1L

        try {
            videoEncoder = MediaCodec.createEncoderByType(videoMime)
            videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            videoEncoder.start()

            audioEncoder = MediaCodec.createEncoderByType(audioMime)
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()

            muxer = IsoMp4Muxer(outputFile, majorBrand = "mp42")
            val videoTrackIndex = muxer.addTrack(videoFormat)
            val audioTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()
            muxerStarted = true

            val frameSize = width * height * 3 / 2
            val yuvFrame = ByteArray(frameSize)
            val bufferInfo = MediaCodec.BufferInfo()
            val muxBufferInfo = MediaCodec.BufferInfo()

            // 1. Encode Video Track
            var submittedFrames = 0
            var inputDone = false
            var outputDone = false
            var emptyPolls = 0

            while (!outputDone && emptyPolls < 200) {
                if (!inputDone) {
                    val inIdx = videoEncoder.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = videoEncoder.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        if (submittedFrames >= totalFrames) {
                            val ptsUs = (submittedFrames * 1_000_000L) / frameRate
                            videoEncoder.queueInputBuffer(
                                inIdx,
                                0,
                                0,
                                ptsUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val lumaSize = width * height
                            val barX = (submittedFrames * 12) % width
                            for (y in 0 until height) {
                                val rowOffset = y * width
                                val baseLuma = (40 + (y * 140 / height)).toByte()
                                for (x in 0 until width) {
                                    yuvFrame[rowOffset + x] =
                                        if (kotlin.math.abs(x - barX) < 18) 235.toByte() else baseLuma
                                }
                            }
                            for (i in lumaSize until frameSize) {
                                yuvFrame[i] = ((128 + (submittedFrames * 3)) and 0xFF).toByte()
                            }
                            val bytesToCopy = minOf(inBuf.remaining(), frameSize)
                            inBuf.put(yuvFrame, 0, bytesToCopy)
                            val ptsUs = (submittedFrames * 1_000_000L) / frameRate
                            submittedFrames++
                            videoEncoder.queueInputBuffer(inIdx, 0, bytesToCopy, ptsUs, 0)
                        }
                    }
                }

                val outIdx = videoEncoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        emptyPolls = 0
                    }
                    outIdx >= 0 -> {
                        emptyPolls = 0
                        val outBuf = videoEncoder.getOutputBuffer(outIdx)
                        val isCodecConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        if (outBuf != null && bufferInfo.size > 0 && !isCodecConfig) {
                            val rawPtsUs = bufferInfo.presentationTimeUs.coerceAtLeast(0L)
                            val safePtsUs = if (rawPtsUs <= lastVideoPtsUs) lastVideoPtsUs + 100L else rawPtsUs
                            lastVideoPtsUs = safePtsUs

                            val safeFlags = bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME
                            muxBufferInfo.set(bufferInfo.offset, bufferInfo.size, safePtsUs, safeFlags)

                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, outBuf, muxBufferInfo)
                            samplesWritten++
                        }
                        videoEncoder.releaseOutputBuffer(outIdx, false)
                        if (isEos) {
                            outputDone = true
                        }
                    }
                    else -> {
                        emptyPolls++
                    }
                }
            }

            // 2. Encode Companion AAC Audio Track for Video
            val totalAudioSamples = sampleRate * durationSeconds
            var audioSubmitted = 0
            var audioInputDone = false
            var audioOutputDone = false
            var audioEmptyPolls = 0

            while (!audioOutputDone && audioEmptyPolls < 200) {
                if (!audioInputDone) {
                    val inIdx = audioEncoder.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = audioEncoder.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        if (audioSubmitted >= totalAudioSamples) {
                            audioEncoder.queueInputBuffer(
                                inIdx,
                                0,
                                0,
                                (audioSubmitted * 1_000_000L) / sampleRate,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            audioInputDone = true
                        } else {
                            val maxFrames = minOf(1024, totalAudioSamples - audioSubmitted, inBuf.remaining() / 4)
                            for (i in 0 until maxFrames) {
                                val t = (audioSubmitted + i).toDouble() / sampleRate.toDouble()
                                val l = (sin(2.0 * PI * 392.0 * t) * 10000).toInt().toShort()
                                val r = (sin(2.0 * PI * 493.88 * t) * 10000).toInt().toShort()
                                inBuf.put((l.toInt() and 0xFF).toByte())
                                inBuf.put(((l.toInt() shr 8) and 0xFF).toByte())
                                inBuf.put((r.toInt() and 0xFF).toByte())
                                inBuf.put(((r.toInt() shr 8) and 0xFF).toByte())
                            }
                            val ptsUs = (audioSubmitted * 1_000_000L) / sampleRate
                            audioSubmitted += maxFrames
                            audioEncoder.queueInputBuffer(inIdx, 0, maxFrames * 4, ptsUs, 0)
                        }
                    }
                }

                val outIdx = audioEncoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        audioEmptyPolls = 0
                    }
                    outIdx >= 0 -> {
                        audioEmptyPolls = 0
                        val outBuf = audioEncoder.getOutputBuffer(outIdx)
                        val isCodecConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        if (outBuf != null && bufferInfo.size > 0 && !isCodecConfig) {
                            val rawPtsUs = bufferInfo.presentationTimeUs.coerceAtLeast(0L)
                            val safePtsUs = if (rawPtsUs <= lastAudioPtsUs) lastAudioPtsUs + 100L else rawPtsUs
                            lastAudioPtsUs = safePtsUs

                            val safeFlags = bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME
                            muxBufferInfo.set(bufferInfo.offset, bufferInfo.size, safePtsUs, safeFlags)

                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(audioTrackIndex, outBuf, muxBufferInfo)
                            samplesWritten++
                        }
                        audioEncoder.releaseOutputBuffer(outIdx, false)
                        if (isEos) {
                            audioOutputDone = true
                        }
                    }
                    else -> {
                        audioEmptyPolls++
                    }
                }
            }
        } finally {
            runCatching { videoEncoder?.stop() }
            runCatching { videoEncoder?.release() }
            runCatching { audioEncoder?.stop() }
            runCatching { audioEncoder?.release() }
            if (muxerStarted && samplesWritten > 0) {
                runCatching { muxer?.stop() }
            }
            if (muxer != null) {
                runCatching { muxer?.release() }
            }
            if (samplesWritten == 0 && outputFile.exists()) {
                outputFile.delete()
            }
        }
    }
}
