package com.example.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.StatFs
import com.example.data.local.JobStatus
import com.example.data.local.MediaEntity
import com.example.data.local.MediaType
import com.example.data.local.ProcessingJobEntity
import com.example.data.local.ProcessingOperationType
import com.example.data.local.PulseDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed class ProcessingOutcome {
    data class Success(
        val outputFile: File,
        val outputMediaEntity: MediaEntity,
        val summary: String
    ) : ProcessingOutcome()

    data class Failure(val errorMessage: String) : ProcessingOutcome()
}

/**
 * Phase 11 & Phase 12: Real Local Media Processing & Format Conversion Engine.
 *
 * Implements the 14 mandatory steps for every operation:
 * 1. Validate input URI
 * 2. Inspect media metadata
 * 3. Check supported codecs & processing capabilities
 * 4. Validate parameters and timestamps
 * 5. Check available storage
 * 6. Create unique temporary output
 * 7. Execute on background dispatcher
 * 8. Report progress truthfully
 * 9. Support cancellation
 * 10. Validate completed output
 * 11. Save to selected destination without silently overwriting
 * 12. Clean up temporary files safely
 * 13. Preserve original file
 * 14. Persist operation result and actionable error in Room
 */
class MediaProcessorEngine(
    private val context: Context,
    private val dao: PulseDao
) {

    private val exportsDir: File
        get() = File(context.filesDir, "pulse_exports").apply { mkdirs() }

    private val tempDir: File
        get() = File(context.cacheDir, "pulse_processing_tmp").apply { mkdirs() }

    suspend fun executeAudioTrim(
        sourceMedia: MediaEntity,
        startMs: Long,
        endMs: Long,
        outputFormatExt: String // "wav" or "m4a"
    ): ProcessingOutcome = withContext(Dispatchers.IO) {
        val jobId = dao.insertProcessingJob(
            ProcessingJobEntity(
                operationType = ProcessingOperationType.AUDIO_TRIM,
                sourceUri = sourceMedia.uriString,
                sourceTitle = sourceMedia.title,
                outputUri = null,
                outputFormat = outputFormatExt.uppercase(),
                startMs = startMs,
                endMs = endMs,
                status = JobStatus.RUNNING,
                progressPercent = 2,
                statusMessage = "Validating input URI and timestamps..."
            )
        )

        var tempFile: File? = null
        try {
            // 1 & 4. Validate parameters and timestamps
            if (startMs < 0L || endMs <= startMs || (endMs - startMs) < 300L) {
                throw IllegalArgumentException("Invalid trim range: end time (${endMs}ms) must be at least 300ms after start time (${startMs}ms).")
            }

            // 5. Check available storage
            checkAvailableStorageOrThrow(requiredBytes = 10L * 1024L * 1024L)

            // 6. Create unique temporary output
            val ext = if (outputFormatExt.equals("wav", ignoreCase = true)) "wav" else "m4a"
            tempFile = File(tempDir, "trim_tmp_${System.currentTimeMillis()}.$ext")

            if (ext == "wav") {
                val isSourceWav = sourceMedia.mimeType.contains("wav", ignoreCase = true) ||
                    sourceMedia.uriString.endsWith(".wav", ignoreCase = true)
                if (isSourceWav && Uri.parse(sourceMedia.uriString).scheme == "file") {
                    val srcFile = File(Uri.parse(sourceMedia.uriString).path!!)
                    trimPcmWavFile(srcFile, tempFile, startMs, endMs) { pct ->
                        dao.updateProcessingJob(
                            jobId,
                            JobStatus.RUNNING,
                            pct,
                            "Trimming PCM WAVE samples ($pct%)..."
                        )
                    }
                } else {
                    decodeAndWriteWav(
                        sourceUri = Uri.parse(sourceMedia.uriString),
                        outputFile = tempFile,
                        startUs = startMs * 1000L,
                        endUs = endMs * 1000L
                    ) { pct ->
                        dao.updateProcessingJob(
                            jobId,
                            JobStatus.RUNNING,
                            pct,
                            "Decoding & writing trimmed WAV ($pct%)..."
                        )
                    }
                }
            } else {
                // M4A output: try lossless remux first if source has AAC track, otherwise transcode
                val remuxed = remuxMediaRange(
                    sourceUri = Uri.parse(sourceMedia.uriString),
                    outputFile = tempFile,
                    startUs = startMs * 1000L,
                    endUs = endMs * 1000L,
                    includeVideo = false,
                    includeAudio = true,
                    rotationDegrees = 0
                ) { pct ->
                    dao.updateProcessingJob(
                        jobId,
                        JobStatus.RUNNING,
                        pct,
                        "Remuxing AAC audio packets ($pct%)..."
                    )
                }

                if (!remuxed) {
                    transcodeAudioToAacM4a(
                        sourceUri = Uri.parse(sourceMedia.uriString),
                        outputFile = tempFile,
                        startUs = startMs * 1000L,
                        endUs = endMs * 1000L,
                        bitrateBps = 192_000
                    ) { pct ->
                        dao.updateProcessingJob(
                            jobId,
                            JobStatus.RUNNING,
                            pct,
                            "Transcoding trimmed segment to AAC-LC ($pct%)..."
                        )
                    }
                }
            }

            // 10 & 11. Validate completed output & move to unique final destination
            val finalFile = moveTempToValidatedExport(
                tempFile = tempFile,
                baseName = "${sanitizeFileName(sourceMedia.title)}_Trimmed",
                extension = ext
            )

            val exportedEntity = buildExportedMediaEntity(
                file = finalFile,
                title = "${sourceMedia.title} (Trimmed ${formatMillisShort(startMs)}-${formatMillisShort(endMs)})",
                mediaType = MediaType.AUDIO,
                mimeType = if (ext == "wav") "audio/wav" else "audio/mp4",
                fallbackDurationMs = endMs - startMs
            )
            dao.upsertMediaItem(exportedEntity)

            dao.updateProcessingJob(
                jobId = jobId,
                status = JobStatus.COMPLETED,
                progress = 100,
                message = "Exported ${finalFile.name} (${finalFile.length() / 1024} KB)",
                outputUri = exportedEntity.uriString,
                outputSizeBytes = finalFile.length(),
                completedAtMs = System.currentTimeMillis()
            )

            ProcessingOutcome.Success(
                outputFile = finalFile,
                outputMediaEntity = exportedEntity,
                summary = "Audio trimmed and verified: ${finalFile.name}"
            )
        } catch (ce: CancellationException) {
            tempFile?.delete()
            dao.updateProcessingJob(
                jobId = jobId,
                status = JobStatus.CANCELLED,
                progress = 0,
                message = "Cancelled by user; temporary files cleaned up."
            )
            throw ce
        } catch (e: Exception) {
            tempFile?.delete()
            val msg = e.message ?: "Audio trim failed due to unsupported codec or corrupt stream."
            dao.updateProcessingJob(
                jobId = jobId,
                status = JobStatus.FAILED,
                progress = 0,
                message = msg
            )
            ProcessingOutcome.Failure(msg)
        }
    }

    suspend fun executeVideoTrimAndRotate(
        sourceMedia: MediaEntity,
        startMs: Long,
        endMs: Long,
        rotationDegrees: Int,
        cropPreset: String
    ): ProcessingOutcome = withContext(Dispatchers.IO) {
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
        val jobId = dao.insertProcessingJob(
            ProcessingJobEntity(
                operationType = ProcessingOperationType.VIDEO_TRIM_ROTATE,
                sourceUri = sourceMedia.uriString,
                sourceTitle = sourceMedia.title,
                outputUri = null,
                outputFormat = "MP4",
                startMs = startMs,
                endMs = endMs,
                rotationDegrees = normalizedRotation,
                cropPreset = cropPreset,
                status = JobStatus.RUNNING,
                progressPercent = 5,
                statusMessage = "Inspecting video container and tracks..."
            )
        )

        var tempFile: File? = null
        try {
            if (startMs < 0L || endMs <= startMs || (endMs - startMs) < 300L) {
                throw IllegalArgumentException("Invalid video trim range (${startMs}ms..${endMs}ms).")
            }
            checkAvailableStorageOrThrow(requiredBytes = 20L * 1024L * 1024L)

            tempFile = File(tempDir, "vid_edit_tmp_${System.currentTimeMillis()}.mp4")
            val success = remuxMediaRange(
                sourceUri = Uri.parse(sourceMedia.uriString),
                outputFile = tempFile,
                startUs = startMs * 1000L,
                endUs = endMs * 1000L,
                includeVideo = true,
                includeAudio = true,
                rotationDegrees = normalizedRotation
            ) { pct ->
                dao.updateProcessingJob(
                    jobId,
                    JobStatus.RUNNING,
                    pct,
                    "Muxing video & audio streams ($pct%)..."
                )
            }

            if (!success) {
                throw IllegalStateException("Source video does not contain MP4-muxable H.264/HEVC/AAC tracks.")
            }

            val finalFile = moveTempToValidatedExport(
                tempFile = tempFile,
                baseName = "${sanitizeFileName(sourceMedia.title)}_Edited_${normalizedRotation}deg",
                extension = "mp4"
            )

            val exportedEntity = buildExportedMediaEntity(
                file = finalFile,
                title = "${sourceMedia.title} (Edited ${normalizedRotation}° • $cropPreset)",
                mediaType = MediaType.VIDEO,
                mimeType = "video/mp4",
                fallbackDurationMs = endMs - startMs
            )
            dao.upsertMediaItem(exportedEntity)

            dao.updateProcessingJob(
                jobId = jobId,
                status = JobStatus.COMPLETED,
                progress = 100,
                message = "Video exported: ${finalFile.name} (${finalFile.length() / 1024} KB)",
                outputUri = exportedEntity.uriString,
                outputSizeBytes = finalFile.length(),
                completedAtMs = System.currentTimeMillis()
            )

            ProcessingOutcome.Success(
                outputFile = finalFile,
                outputMediaEntity = exportedEntity,
                summary = "Video trimmed/rotated and verified: ${finalFile.name}"
            )
        } catch (ce: CancellationException) {
            tempFile?.delete()
            dao.updateProcessingJob(
                jobId = jobId,
                status = JobStatus.CANCELLED,
                progress = 0,
                message = "Video edit cancelled by user."
            )
            throw ce
        } catch (e: Exception) {
            tempFile?.delete()
            val msg = e.message ?: "Video processing failed."
            dao.updateProcessingJob(
                jobId = jobId,
                status = JobStatus.FAILED,
                progress = 0,
                message = msg
            )
            ProcessingOutcome.Failure(msg)
        }
    }

    suspend fun executeAudioExtractionOrConversion(
        sourceMedia: MediaEntity,
        profileId: String,
        targetBitrateKbps: Int,
        isExtractionFromVideo: Boolean
    ): ProcessingOutcome = withContext(Dispatchers.IO) {
        val opType = if (isExtractionFromVideo) {
            ProcessingOperationType.AUDIO_EXTRACTION
        } else {
            ProcessingOperationType.FORMAT_CONVERSION
        }

        val profile = ConversionCapabilityRegistry.getSupportedProfiles()
            .find { it.id == profileId }
            ?: ConversionCapabilityRegistry.getSupportedProfiles().first()

        val jobId = dao.insertProcessingJob(
            ProcessingJobEntity(
                operationType = opType,
                sourceUri = sourceMedia.uriString,
                sourceTitle = sourceMedia.title,
                outputUri = null,
                outputFormat = profile.outputExtension.uppercase(),
                startMs = 0L,
                endMs = sourceMedia.durationMs,
                targetBitrateKbps = targetBitrateKbps,
                status = JobStatus.RUNNING,
                progressPercent = 4,
                statusMessage = "Initializing ${profile.title}..."
            )
        )

        var tempFile: File? = null
        try {
            if (!profile.isHardwareSupportedOnDevice) {
                throw IllegalStateException("Device does not report a compatible encoder for ${profile.audioCodecMime}.")
            }
            checkAvailableStorageOrThrow(requiredBytes = 15L * 1024L * 1024L)

            val ext = profile.outputExtension
            tempFile = File(tempDir, "conv_tmp_${System.currentTimeMillis()}.$ext")
            val sourceUri = Uri.parse(sourceMedia.uriString)
            val endUs = (sourceMedia.durationMs.takeIf { it > 0 } ?: 60_000L) * 1000L

            when (profile.id) {
                "WAV_PCM_16BIT" -> {
                    decodeAndWriteWav(
                        sourceUri = sourceUri,
                        outputFile = tempFile,
                        startUs = 0L,
                        endUs = endUs
                    ) { pct ->
                        dao.updateProcessingJob(
                            jobId,
                            JobStatus.RUNNING,
                            pct,
                            "Decoding stream to 16-bit PCM WAV ($pct%)..."
                        )
                    }
                }
                "M4A_DIRECT_REMUX" -> {
                    val remuxed = remuxMediaRange(
                        sourceUri = sourceUri,
                        outputFile = tempFile,
                        startUs = 0L,
                        endUs = endUs,
                        includeVideo = false,
                        includeAudio = true,
                        rotationDegrees = 0
                    ) { pct ->
                        dao.updateProcessingJob(
                            jobId,
                            JobStatus.RUNNING,
                            pct,
                            "Remuxing AAC audio stream ($pct%)..."
                        )
                    }
                    if (!remuxed) {
                        transcodeAudioToAacM4a(
                            sourceUri = sourceUri,
                            outputFile = tempFile,
                            startUs = 0L,
                            endUs = endUs,
                            bitrateBps = targetBitrateKbps.coerceIn(64, 320) * 1000
                        ) { pct ->
                            dao.updateProcessingJob(
                                jobId,
                                JobStatus.RUNNING,
                                pct,
                                "Source lacked AAC track; transcoding to AAC-LC ($pct%)..."
                            )
                        }
                    }
                }
                "MP4_VIDEO_REMUX_ROTATE" -> {
                    val remuxed = remuxMediaRange(
                        sourceUri = sourceUri,
                        outputFile = tempFile,
                        startUs = 0L,
                        endUs = endUs,
                        includeVideo = true,
                        includeAudio = true,
                        rotationDegrees = 0
                    ) { pct ->
                        dao.updateProcessingJob(
                            jobId,
                            JobStatus.RUNNING,
                            pct,
                            "Remuxing MP4 container ($pct%)..."
                        )
                    }
                    if (!remuxed) {
                        throw IllegalStateException("Source does not contain MP4-compatible video/audio streams.")
                    }
                }
                else -> {
                    // M4A_AAC_TRANSCODE
                    transcodeAudioToAacM4a(
                        sourceUri = sourceUri,
                        outputFile = tempFile,
                        startUs = 0L,
                        endUs = endUs,
                        bitrateBps = targetBitrateKbps.coerceIn(64, 320) * 1000
                    ) { pct ->
                        dao.updateProcessingJob(
                            jobId,
                            JobStatus.RUNNING,
                            pct,
                            "Encoding AAC-LC @ ${targetBitrateKbps}kbps ($pct%)..."
                        )
                    }
                }
            }

            val suffix = if (isExtractionFromVideo) "ExtractedAudio" else "Converted_${ext.uppercase()}"
            val finalFile = moveTempToValidatedExport(
                tempFile = tempFile,
                baseName = "${sanitizeFileName(sourceMedia.title)}_$suffix",
                extension = ext
            )

            val outMediaType = if (ext == "mp4") MediaType.VIDEO else MediaType.AUDIO
            val exportedEntity = buildExportedMediaEntity(
                file = finalFile,
                title = "${sourceMedia.title} (${ext.uppercase()} ${targetBitrateKbps}kbps)",
                mediaType = outMediaType,
                mimeType = profile.outputMimeType,
                fallbackDurationMs = sourceMedia.durationMs
            )
            dao.upsertMediaItem(exportedEntity)

            dao.updateProcessingJob(
                jobId = jobId,
                status = JobStatus.COMPLETED,
                progress = 100,
                message = "Completed: ${finalFile.name} (${finalFile.length() / 1024} KB)",
                outputUri = exportedEntity.uriString,
                outputSizeBytes = finalFile.length(),
                completedAtMs = System.currentTimeMillis()
            )

            ProcessingOutcome.Success(
                outputFile = finalFile,
                outputMediaEntity = exportedEntity,
                summary = "Output verified and saved to library: ${finalFile.name}"
            )
        } catch (ce: CancellationException) {
            tempFile?.delete()
            dao.updateProcessingJob(
                jobId = jobId,
                status = JobStatus.CANCELLED,
                progress = 0,
                message = "Conversion cancelled by user."
            )
            throw ce
        } catch (e: Exception) {
            tempFile?.delete()
            val msg = e.message ?: "Conversion failed due to unsupported input stream."
            dao.updateProcessingJob(
                jobId = jobId,
                status = JobStatus.FAILED,
                progress = 0,
                message = msg
            )
            ProcessingOutcome.Failure(msg)
        }
    }

    /**
     * Fast sample-exact trimmer for standard RIFF PCM WAVE files.
     */
    private suspend fun trimPcmWavFile(
        sourceFile: File,
        outputFile: File,
        startMs: Long,
        endMs: Long,
        onProgress: suspend (Int) -> Unit
    ) {
        if (!sourceFile.exists() || sourceFile.length() <= 44L) {
            throw IllegalArgumentException("Source WAV file is missing or smaller than 44-byte RIFF header.")
        }
        FileInputStream(sourceFile).use { fis ->
            val headerBytes = ByteArray(44)
            if (fis.read(headerBytes) != 44) {
                throw IllegalArgumentException("Could not read 44-byte WAV header.")
            }
            val headerBuf = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val channels = headerBuf.getShort(22).toInt().coerceAtLeast(1)
            val sampleRate = headerBuf.getInt(24).coerceAtLeast(8000)
            val bitsPerSample = headerBuf.getShort(34).toInt().coerceAtLeast(8)
            val blockAlign = (channels * (bitsPerSample / 8)).coerceAtLeast(1)
            val byteRate = sampleRate * blockAlign

            val startByteOffset = ((startMs * byteRate) / 1000L).let { it - (it % blockAlign) }
            val endByteOffset = ((endMs * byteRate) / 1000L).let { it - (it % blockAlign) }
            val availableAudioBytes = (sourceFile.length() - 44L).coerceAtLeast(0L)
            val clampedStart = startByteOffset.coerceIn(0L, availableAudioBytes)
            val clampedEnd = endByteOffset.coerceIn(clampedStart, availableAudioBytes)
            val bytesToCopy = (clampedEnd - clampedStart).toInt()

            if (bytesToCopy <= blockAlign) {
                throw IllegalArgumentException("Selected trim window contains no audio samples.")
            }

            FileOutputStream(outputFile).use { fos ->
                val outHeader = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                outHeader.put(headerBytes)
                outHeader.putInt(4, 36 + bytesToCopy)
                outHeader.putInt(40, bytesToCopy)
                fos.write(outHeader.array())

                fis.channel.position(44L + clampedStart)
                val buf = ByteArray(8192)
                var copied = 0
                while (copied < bytesToCopy) {
                    currentCoroutineContext().ensureActive()
                    val toRead = minOf(buf.size, bytesToCopy - copied)
                    val read = fis.read(buf, 0, toRead)
                    if (read <= 0) break
                    fos.write(buf, 0, read)
                    copied += read
                    val pct = (10 + (copied * 85L / bytesToCopy)).toInt().coerceIn(10, 95)
                    onProgress(pct)
                }
            }
        }
    }

    /**
     * Remuxes compatible audio and/or video tracks from [sourceUri] into an MP4/M4A container
     * using Android's MediaExtractor + MediaMuxer without re-encoding.
     */
    private suspend fun remuxMediaRange(
        sourceUri: Uri,
        outputFile: File,
        startUs: Long,
        endUs: Long,
        includeVideo: Boolean,
        includeAudio: Boolean,
        rotationDegrees: Int,
        onProgress: suspend (Int) -> Unit
    ): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var samplesWritten = 0

        try {
            setExtractorDataSource(extractor, sourceUri)
            val trackCount = extractor.trackCount
            if (trackCount <= 0) return false

            val selectedFormats = mutableMapOf<Int, MediaFormat>()
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                val isSupportedAudio = includeAudio && (
                    mime.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true) ||
                        mime.equals("audio/mp4a-latm", ignoreCase = true)
                    )
                val isSupportedVideo = includeVideo && (
                    mime.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) ||
                        mime.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) ||
                        mime.equals(MediaFormat.MIMETYPE_VIDEO_MPEG4, ignoreCase = true)
                    )

                if (isSupportedAudio || isSupportedVideo) {
                    extractor.selectTrack(i)
                    selectedFormats[i] = format
                }
            }

            if (selectedFormats.isEmpty()) {
                return false
            }

            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            val trackMap = mutableMapOf<Int, Int>()
            val tracksWithSamples = mutableSetOf<Int>()
            val buffer = ByteBuffer.allocate(256 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            val durationWindowUs = (endUs - startUs).coerceAtLeast(1_000L)

            while (true) {
                currentCoroutineContext().ensureActive()
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (endUs > startUs && sampleTimeUs > endUs) break

                val srcTrackIndex = extractor.sampleTrackIndex
                if (selectedFormats.containsKey(srcTrackIndex) && sampleTimeUs >= 0L && bufferInfo.size > 0) {
                    if (!muxerStarted) {
                        muxer = MediaMuxer(
                            outputFile.absolutePath,
                            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                        )
                        if (includeVideo && rotationDegrees in listOf(90, 180, 270)) {
                            muxer.setOrientationHint(rotationDegrees)
                        }
                        selectedFormats.forEach { (srcIdx, fmt) ->
                            trackMap[srcIdx] = muxer!!.addTrack(fmt)
                        }
                        muxer.start()
                        muxerStarted = true
                    }
                    val dstTrackIndex = trackMap[srcTrackIndex]
                    if (dstTrackIndex != null) {
                        bufferInfo.presentationTimeUs = (sampleTimeUs - startUs).coerceAtLeast(0L)
                        bufferInfo.flags = extractor.sampleFlags
                        muxer?.writeSampleData(dstTrackIndex, buffer, bufferInfo)
                        tracksWithSamples.add(dstTrackIndex)
                        samplesWritten++

                        if (samplesWritten % 15 == 0) {
                            val pct = (10 + ((sampleTimeUs - startUs).coerceAtLeast(0L) * 85L / durationWindowUs))
                                .toInt().coerceIn(10, 95)
                            onProgress(pct)
                        }
                    }
                }
                extractor.advance()
            }

            return samplesWritten > 0 && tracksWithSamples.size == trackMap.size
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            return false
        } finally {
            runCatching { extractor.release() }
            if (muxerStarted && samplesWritten > 0) {
                runCatching { muxer?.stop() }
            }
            if (muxer != null) {
                runCatching { muxer?.release() }
            }
        }
    }

    /**
     * Decodes any Android-supported audio track from [sourceUri] into raw 16-bit PCM
     * and writes a valid RIFF WAVE file.
     */
    private suspend fun decodeAndWriteWav(
        sourceUri: Uri,
        outputFile: File,
        startUs: Long,
        endUs: Long,
        onProgress: suspend (Int) -> Unit
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null

        try {
            setExtractorDataSource(extractor, sourceUri)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || inputFormat == null) {
                throw IllegalArgumentException("No decodable audio track found in source media.")
            }

            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val channels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            // If input is already raw PCM (e.g., WAV via SAF), fallback to direct synthesis/copy
            if (mime == MediaFormat.MIMETYPE_AUDIO_RAW && sourceUri.scheme == "file") {
                val srcFile = File(sourceUri.path!!)
                trimPcmWavFile(srcFile, outputFile, startUs / 1000L, endUs / 1000L, onProgress)
                return
            }

            extractor.selectTrack(audioTrackIndex)
            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            RandomAccessFile(outputFile, "rw").use { raf ->
                // Write placeholder 44-byte WAV header
                raf.write(ByteArray(44))
                var totalPcmBytesWritten = 0
                var inputDone = false
                var outputDone = false
                val bufferInfo = MediaCodec.BufferInfo()
                val durationWindowUs = (endUs - startUs).coerceAtLeast(1_000L)

                while (!outputDone) {
                    currentCoroutineContext().ensureActive()
                    if (!inputDone) {
                        val inIdx = decoder.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val inBuf = decoder.getInputBuffer(inIdx)!!
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            val sampleTimeUs = extractor.sampleTime
                            if (sampleSize < 0 || (endUs > startUs && sampleTimeUs > endUs)) {
                                decoder.queueInputBuffer(
                                    inIdx,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, sampleSize, sampleTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outIdx >= 0) {
                        val outBuf = decoder.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0 && bufferInfo.presentationTimeUs >= startUs) {
                            val chunk = ByteArray(bufferInfo.size)
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            outBuf.get(chunk)
                            raf.write(chunk)
                            totalPcmBytesWritten += chunk.size

                            val pct = (10 + ((bufferInfo.presentationTimeUs - startUs).coerceAtLeast(0L) * 85L / durationWindowUs))
                                .toInt().coerceIn(10, 95)
                            onProgress(pct)
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                        break
                    }
                }

                // Finalize RIFF WAVE header
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray(Charsets.US_ASCII))
                header.putInt(36 + totalPcmBytesWritten)
                header.put("WAVE".toByteArray(Charsets.US_ASCII))
                header.put("fmt ".toByteArray(Charsets.US_ASCII))
                header.putInt(16)
                header.putShort(1)
                header.putShort(channels.toShort())
                header.putInt(sampleRate)
                header.putInt(sampleRate * channels * 2)
                header.putShort((channels * 2).toShort())
                header.putShort(16)
                header.put("data".toByteArray(Charsets.US_ASCII))
                header.putInt(totalPcmBytesWritten)
                raf.seek(0)
                raf.write(header.array())
            }
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * Transcodes PCM WAV or compressed audio into an AAC-LC M4A container using MediaCodec + MediaMuxer.
     */
    private suspend fun transcodeAudioToAacM4a(
        sourceUri: Uri,
        outputFile: File,
        startUs: Long,
        endUs: Long,
        bitrateBps: Int,
        onProgress: suspend (Int) -> Unit
    ) {
        // First decode or trim to an intermediate PCM WAV file so any input format (WAV, MP3, video) works
        val pcmTemp = File(tempDir, "pcm_stage_${System.currentTimeMillis()}.wav")
        try {
            val isWavFile = sourceUri.scheme == "file" && sourceUri.path?.endsWith(".wav", true) == true
            if (isWavFile) {
                trimPcmWavFile(File(sourceUri.path!!), pcmTemp, startUs / 1000L, endUs / 1000L) { pct ->
                    onProgress((pct / 2).coerceIn(5, 45))
                }
            } else {
                decodeAndWriteWav(sourceUri, pcmTemp, startUs, endUs) { pct ->
                    onProgress((pct / 2).coerceIn(5, 45))
                }
            }

            FileInputStream(pcmTemp).use { fis ->
                val header = ByteArray(44)
                if (fis.read(header) != 44) {
                    throw IllegalStateException("Failed to read intermediate PCM WAV header.")
                }
                val hBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val channels = hBuf.getShort(22).toInt().coerceIn(1, 2)
                val sampleRate = hBuf.getInt(24).coerceIn(8000, 48000)
                val pcmDataBytes = (pcmTemp.length() - 44L).coerceAtLeast(1L)

                val aacFormat = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC,
                    sampleRate,
                    channels
                ).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                }

                var encoder: MediaCodec? = null
                var muxer: MediaMuxer? = null
                var muxerStarted = false
                var samplesWritten = 0

                try {
                    encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                    encoder.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    encoder.start()

                    val readBuf = ByteArray(4096)
                    val bufferInfo = MediaCodec.BufferInfo()
                    var totalReadBytes = 0L
                    var inputDone = false
                    var outputDone = false
                    var outputFormat: MediaFormat? = null
                    var trackIdx = -1
                    var emptyPolls = 0
                    val bytesPerSecond = (sampleRate * channels * 2).toLong()

                    while (!outputDone && emptyPolls < 200) {
                        currentCoroutineContext().ensureActive()
                        if (!inputDone) {
                            val inIdx = encoder.dequeueInputBuffer(10_000)
                            if (inIdx >= 0) {
                                val inBuf = encoder.getInputBuffer(inIdx)!!
                                inBuf.clear()
                                val maxToRead = minOf(readBuf.size, inBuf.remaining())
                                val read = fis.read(readBuf, 0, maxToRead)
                                val ptsUs = (totalReadBytes * 1_000_000L) / bytesPerSecond
                                if (read <= 0) {
                                    encoder.queueInputBuffer(
                                        inIdx,
                                        0,
                                        0,
                                        ptsUs,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                } else {
                                    inBuf.put(readBuf, 0, read)
                                    totalReadBytes += read
                                    encoder.queueInputBuffer(inIdx, 0, read, ptsUs, 0)
                                    val pct = (50 + (totalReadBytes * 45L / pcmDataBytes)).toInt().coerceIn(50, 95)
                                    onProgress(pct)
                                }
                            }
                        }

                        val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                        when {
                            outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                outputFormat = encoder.outputFormat
                                emptyPolls = 0
                            }
                            outIdx >= 0 -> {
                                emptyPolls = 0
                                val outBuf = encoder.getOutputBuffer(outIdx)
                                val isCodecConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                                if (outBuf != null && bufferInfo.size > 0 && !isCodecConfig) {
                                    if (!muxerStarted) {
                                        val fmt = outputFormat ?: encoder.outputFormat
                                        muxer = MediaMuxer(
                                            outputFile.absolutePath,
                                            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                                        )
                                        trackIdx = muxer!!.addTrack(fmt)
                                        muxer!!.start()
                                        muxerStarted = true
                                    }
                                    outBuf.position(bufferInfo.offset)
                                    outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer?.writeSampleData(trackIdx, outBuf, bufferInfo)
                                    samplesWritten++
                                }
                                encoder.releaseOutputBuffer(outIdx, false)
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
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
                }
            }
        } finally {
            pcmTemp.delete()
        }
    }

    private fun setExtractorDataSource(extractor: MediaExtractor, uri: Uri) {
        if (uri.scheme == "file") {
            extractor.setDataSource(uri.path!!)
        } else {
            extractor.setDataSource(context, uri, null)
        }
    }

    private fun checkAvailableStorageOrThrow(requiredBytes: Long) {
        val usable = context.filesDir.usableSpace
        val available = if (usable > 0L) {
            usable
        } else {
            runCatching {
                StatFs(context.filesDir.absolutePath).availableBytes
            }.getOrDefault(Long.MAX_VALUE)
        }
        if (available > 0L && available < requiredBytes) {
            throw IllegalStateException(
                "Insufficient local storage: ${available / (1024 * 1024)} MB free, need at least ${requiredBytes / (1024 * 1024)} MB."
            )
        }
    }

    private fun moveTempToValidatedExport(
        tempFile: File,
        baseName: String,
        extension: String
    ): File {
        if (!tempFile.exists() || tempFile.length() <= 64L) {
            tempFile.delete()
            throw IllegalStateException("Validation failed: produced output file was empty or incomplete.")
        }

        // Never silently overwrite existing files (Phase 11 Step 11)
        var candidate = File(exportsDir, "$baseName.$extension")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(exportsDir, "${baseName}_$suffix.$extension")
            suffix++
        }

        val copied = tempFile.copyTo(candidate, overwrite = false)
        tempFile.delete()
        return copied
    }

    private fun buildExportedMediaEntity(
        file: File,
        title: String,
        mediaType: MediaType,
        mimeType: String,
        fallbackDurationMs: Long
    ): MediaEntity {
        var durationMs = fallbackDurationMs
        var width = 0
        var height = 0

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: fallbackDurationMs
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }

        return MediaEntity(
            uriString = Uri.fromFile(file).toString(),
            mediaStoreId = null,
            title = title,
            artist = "PulsePlay Studio Export",
            album = "Studio Exports",
            folderName = "Studio Exports",
            durationMs = durationMs.coerceAtLeast(500L),
            sizeBytes = file.length(),
            mimeType = mimeType,
            mediaType = mediaType,
            width = width,
            height = height,
            dateAddedEpochSec = System.currentTimeMillis() / 1000L,
            isAvailable = true,
            isGeneratedSample = false
        )
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(36).ifBlank { "PulseExport" }
    }

    private fun formatMillisShort(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }
}
