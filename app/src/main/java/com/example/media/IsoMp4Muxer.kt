package com.example.media

import android.media.MediaCodec
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Pure-Kotlin ISO Base Media File Format (ISO/IEC 14496-12) `.mp4` / `.m4a` container muxer.
 *
 * Replaces Android's native `MediaMuxer` (`MPEG4Writer`) for MP4/M4A output so that:
 * 1. Real H.264/AVC (`video/avc`) and AAC-LC (`audio/mp4a-latm`) streams encoded by `MediaCodec`
 *    or extracted by `MediaExtractor` are packaged into standards-compliant `ftyp` + `mdat` + `moov`
 *    containers playable by ExoPlayer/Media3 and Android platform extractors.
 * 2. Native `libstagefright` `MPEG4Writer::~MPEG4Writer() -> Track::~Track()` is never invoked,
 *    completely eliminating the AOSP native destructor logcat error
 *    `E/MPEG4Writer: Stop() called but track is not started or stopped`.
 */
class IsoMp4Muxer(
    private val outputFile: File,
    private val majorBrand: String = "mp42"
) {
    private class SampleEntry(
        val fileOffset: Long,
        val size: Int,
        val presentationTimeUs: Long,
        val isSyncSample: Boolean
    )

    private class TrackState(
        val trackId: Int,
        val format: MediaFormat,
        val mime: String,
        val isVideo: Boolean
    ) {
        val samples = mutableListOf<SampleEntry>()
        var spsNal: ByteArray? = null
        var ppsNal: ByteArray? = null
        var aacCsd: ByteArray? = null

        init {
            if (isVideo) {
                extractAvcCsdFromFormat(format)
            } else {
                extractAacCsdFromFormat(format)
            }
        }

        fun updateFormat(fmt: MediaFormat) {
            if (isVideo) {
                extractAvcCsdFromFormat(fmt)
            } else {
                extractAacCsdFromFormat(fmt)
            }
        }

        fun extractAvcCsdFromBytes(data: ByteArray) {
            val nals = splitAnnexBNals(data)
            for (nal in nals) {
                if (nal.isEmpty()) continue
                when (nal[0].toInt() and 0x1F) {
                    7 -> if (spsNal == null || spsNal!!.size < 8) spsNal = nal
                    8 -> if (ppsNal == null || ppsNal!!.size < 4) ppsNal = nal
                }
            }
        }

        private fun extractAvcCsdFromFormat(fmt: MediaFormat) {
            val csd0 = fmt.getByteBuffer("csd-0")?.toByteArray()
            val csd1 = fmt.getByteBuffer("csd-1")?.toByteArray()
            val nals = mutableListOf<ByteArray>()
            if (csd0 != null) nals.addAll(splitAnnexBNals(csd0))
            if (csd1 != null) nals.addAll(splitAnnexBNals(csd1))
            for (nal in nals) {
                if (nal.isEmpty()) continue
                when (nal[0].toInt() and 0x1F) {
                    7 -> if (spsNal == null || spsNal!!.size < 8) spsNal = nal
                    8 -> if (ppsNal == null || ppsNal!!.size < 4) ppsNal = nal
                }
            }
        }

        private fun extractAacCsdFromFormat(fmt: MediaFormat) {
            val csd0 = fmt.getByteBuffer("csd-0")?.toByteArray()
            if (csd0 != null && csd0.isNotEmpty()) {
                aacCsd = csd0
            } else {
                val sampleRate = fmt.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44100)
                val channels = fmt.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 2)
                aacCsd = buildAacAudioSpecificConfig(sampleRate, channels)
            }
        }
    }

    private val tracks = mutableListOf<TrackState>()
    private var rotationDegrees: Int = 0
    private var raf: RandomAccessFile? = null
    private var mdatHeaderOffset: Long = 0L
    private var mdatPayloadStartOffset: Long = 0L
    private var started: Boolean = false
    private var stopped: Boolean = false

    fun setOrientationHint(degrees: Int) {
        if (degrees in listOf(0, 90, 180, 270)) {
            rotationDegrees = degrees
        }
    }

    fun addTrack(format: MediaFormat): Int {
        check(!started) { "Cannot addTrack after muxer has started." }
        val mime = format.getString(MediaFormat.KEY_MIME) ?: "application/octet-stream"
        val isVideo = mime.startsWith("video/", ignoreCase = true)
        val trackIndex = tracks.size
        tracks.add(
            TrackState(
                trackId = trackIndex + 1,
                format = format,
                mime = mime,
                isVideo = isVideo
            )
        )
        return trackIndex
    }

    fun start() {
        check(!started) { "Muxer already started." }
        check(tracks.isNotEmpty()) { "Cannot start muxer with zero tracks." }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }
        val file = RandomAccessFile(outputFile, "rw")
        raf = file

        // Write 28-byte ftyp box
        val ftypBytes = buildBox("ftyp") { out ->
            out.writeAscii4(majorBrand)
            out.writeInt(0) // minor_version
            out.writeAscii4("isom")
            out.writeAscii4("mp42")
            out.writeAscii4("M4A ")
        }
        file.write(ftypBytes)

        // Reserve 8-byte mdat header; payload starts immediately after
        mdatHeaderOffset = file.filePointer
        file.writeInt(0) // placeholder size
        file.writeBytes("mdat")
        mdatPayloadStartOffset = file.filePointer
        started = true
    }

    fun updateTrackFormat(trackIndex: Int, format: MediaFormat) {
        tracks.getOrNull(trackIndex)?.updateFormat(format)
    }

    fun writeSampleData(
        trackIndex: Int,
        byteBuf: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        check(started && !stopped) { "Muxer is not in started state." }
        if (bufferInfo.size <= 0) return

        val track = tracks.getOrNull(trackIndex) ?: return
        val file = raf ?: return

        val rawBytes = ByteArray(bufferInfo.size)
        val dup = byteBuf.duplicate()
        dup.position(bufferInfo.offset)
        dup.limit(bufferInfo.offset + bufferInfo.size)
        dup.get(rawBytes)

        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            if (track.isVideo) {
                track.extractAvcCsdFromBytes(rawBytes)
            } else if (rawBytes.isNotEmpty()) {
                track.aacCsd = rawBytes
            }
            return
        }

        val payloadBytes = if (track.isVideo) {
            convertAvcSampleToLengthPrefixed(rawBytes, track)
        } else {
            stripAdtsHeaderIfPresent(rawBytes)
        }
        if (payloadBytes.isEmpty()) return

        val sampleOffset = file.filePointer
        file.write(payloadBytes)

        val isKeyFrame = if (track.isVideo) {
            (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0 ||
                containsIdrNal(rawBytes) ||
                track.samples.isEmpty()
        } else {
            true
        }

        track.samples.add(
            SampleEntry(
                fileOffset = sampleOffset,
                size = payloadBytes.size,
                presentationTimeUs = bufferInfo.presentationTimeUs.coerceAtLeast(0L),
                isSyncSample = isKeyFrame
            )
        )
    }

    fun stop() {
        if (!started || stopped) return
        stopped = true
        val file = raf ?: return

        val activeTracks = tracks.filter { it.samples.isNotEmpty() }
        if (activeTracks.isEmpty()) {
            runCatching { file.close() }
            raf = null
            outputFile.delete()
            throw IllegalStateException("No samples written to any track.")
        }

        val mdatEndOffset = file.filePointer
        val mdatSize = (mdatEndOffset - mdatHeaderOffset).toInt()

        // Patch mdat size header
        file.seek(mdatHeaderOffset)
        file.writeInt(mdatSize)
        file.seek(mdatEndOffset)

        // Write moov box at end of file
        val moovBytes = buildMoovBox(activeTracks)
        file.write(moovBytes)
    }

    fun release() {
        if (started && !stopped) {
            runCatching { stop() }
        }
        runCatching { raf?.close() }
        raf = null
    }

    private fun convertAvcSampleToLengthPrefixed(raw: ByteArray, track: TrackState): ByteArray {
        val hasAnnexBStartCode = (raw.size >= 4 && raw[0] == 0.toByte() && raw[1] == 0.toByte() &&
            ((raw[2] == 0.toByte() && raw[3] == 1.toByte()) || raw[2] == 1.toByte()))
        if (!hasAnnexBStartCode) {
            // Inspect if already 4-byte length-prefixed AVCC
            if (raw.size >= 5) {
                val firstLen = ((raw[0].toInt() and 0xFF) shl 24) or
                    ((raw[1].toInt() and 0xFF) shl 16) or
                    ((raw[2].toInt() and 0xFF) shl 8) or
                    (raw[3].toInt() and 0xFF)
                if (firstLen in 1..(raw.size - 4)) {
                    return raw
                }
            }
            // Wrap raw single NAL unit with 4-byte big-endian length prefix
            val out = ByteArrayOutputStream(raw.size + 4)
            val dos = DataOutputStream(out)
            dos.writeInt(raw.size)
            dos.write(raw)
            return out.toByteArray()
        }

        val nals = splitAnnexBNals(raw)
        val out = ByteArrayOutputStream(raw.size + 16)
        val dos = DataOutputStream(out)
        for (nal in nals) {
            if (nal.isEmpty()) continue
            val nalType = nal[0].toInt() and 0x1F
            if (nalType == 7) {
                if (track.spsNal == null) track.spsNal = nal
                continue
            }
            if (nalType == 8) {
                if (track.ppsNal == null) track.ppsNal = nal
                continue
            }
            dos.writeInt(nal.size)
            dos.write(nal)
        }
        return out.toByteArray()
    }

    private fun containsIdrNal(raw: ByteArray): Boolean {
        val nals = splitAnnexBNals(raw)
        return nals.any { it.isNotEmpty() && (it[0].toInt() and 0x1F) == 5 }
    }

    private fun stripAdtsHeaderIfPresent(raw: ByteArray): ByteArray {
        if (raw.size > 7 && (raw[0].toInt() and 0xFF) == 0xFF && (raw[1].toInt() and 0xF0) == 0xF0) {
            val protectionAbsent = raw[1].toInt() and 0x01
            val headerLen = if (protectionAbsent != 0) 7 else 9
            if (raw.size > headerLen) {
                return raw.copyOfRange(headerLen, raw.size)
            }
        }
        return raw
    }

    private fun buildMoovBox(activeTracks: List<TrackState>): ByteArray {
        val movieTimescale = 1000
        var maxMovieDurationTicks = 0L

        val trackBoxes = mutableListOf<ByteArray>()
        for (track in activeTracks) {
            val trackTimescale = if (track.isVideo) {
                90_000
            } else {
                track.format.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44100)
            }
            val sampleDeltas = computeSampleDeltas(track, trackTimescale)
            val trackDurationTicks = sampleDeltas.sumOf { it.toLong() }.coerceAtLeast(1L)
            val movieTrackDuration = ((trackDurationTicks * movieTimescale) / trackTimescale).coerceAtLeast(1L)
            if (movieTrackDuration > maxMovieDurationTicks) {
                maxMovieDurationTicks = movieTrackDuration
            }
            trackBoxes.add(
                buildTrakBox(
                    track = track,
                    trackTimescale = trackTimescale,
                    trackDurationTicks = trackDurationTicks,
                    movieTrackDuration = movieTrackDuration,
                    sampleDeltas = sampleDeltas
                )
            )
        }

        val mvhdBox = buildBox("mvhd") { out ->
            out.writeInt(0) // version 0 & flags 0
            out.writeInt(0) // creation_time
            out.writeInt(0) // modification_time
            out.writeInt(movieTimescale)
            out.writeInt(maxMovieDurationTicks.toInt())
            out.writeInt(0x00010000) // rate = 1.0
            out.writeShort(0x0100) // volume = 1.0
            out.writeShort(0) // reserved
            out.writeInt(0) // reserved
            out.writeInt(0) // reserved
            // Unity matrix
            writeMatrix(out, 0, 0, 0)
            for (i in 0 until 6) out.writeInt(0) // pre_defined
            out.writeInt(activeTracks.size + 1) // next_track_ID
        }

        return buildBox("moov") { out ->
            out.write(mvhdBox)
            for (trak in trackBoxes) {
                out.write(trak)
            }
        }
    }

    private fun computeSampleDeltas(track: TrackState, timescale: Int): IntArray {
        val n = track.samples.size
        val deltas = IntArray(n)
        val defaultDelta = if (track.isVideo) {
            val fps = track.format.getIntegerOrDefault(MediaFormat.KEY_FRAME_RATE, 15).coerceAtLeast(1)
            (timescale / fps).coerceAtLeast(1)
        } else {
            1024
        }
        if (n == 1) {
            deltas[0] = defaultDelta
            return deltas
        }
        val ticks = LongArray(n)
        for (i in 0 until n) {
            val rawTick = (track.samples[i].presentationTimeUs * timescale) / 1_000_000L
            ticks[i] = if (i == 0) rawTick.coerceAtLeast(0L) else maxOf(rawTick, ticks[i - 1] + 1L)
        }
        for (i in 0 until n - 1) {
            deltas[i] = (ticks[i + 1] - ticks[i]).toInt().coerceAtLeast(1)
        }
        deltas[n - 1] = deltas[n - 2]
        return deltas
    }

    private fun buildTrakBox(
        track: TrackState,
        trackTimescale: Int,
        trackDurationTicks: Long,
        movieTrackDuration: Long,
        sampleDeltas: IntArray
    ): ByteArray {
        val width = if (track.isVideo) track.format.getIntegerOrDefault(MediaFormat.KEY_WIDTH, 640) else 0
        val height = if (track.isVideo) track.format.getIntegerOrDefault(MediaFormat.KEY_HEIGHT, 360) else 0

        val tkhdBox = buildBox("tkhd") { out ->
            out.writeInt(0x00000007) // version 0, flags = enabled | in_movie | in_preview
            out.writeInt(0) // creation_time
            out.writeInt(0) // modification_time
            out.writeInt(track.trackId)
            out.writeInt(0) // reserved
            out.writeInt(movieTrackDuration.toInt())
            out.writeInt(0) // reserved
            out.writeInt(0) // reserved
            out.writeShort(0) // layer
            out.writeShort(0) // alternate_group
            out.writeShort(if (track.isVideo) 0 else 0x0100) // volume
            out.writeShort(0) // reserved
            writeMatrix(out, if (track.isVideo) rotationDegrees else 0, width, height)
            out.writeInt(width shl 16)
            out.writeInt(height shl 16)
        }

        val mdhdBox = buildBox("mdhd") { out ->
            out.writeInt(0) // version 0 & flags 0
            out.writeInt(0) // creation_time
            out.writeInt(0) // modification_time
            out.writeInt(trackTimescale)
            out.writeInt(trackDurationTicks.toInt())
            out.writeShort(0x55C4) // language = "und"
            out.writeShort(0) // pre_defined
        }

        val hdlrBox = buildBox("hdlr") { out ->
            out.writeInt(0) // version 0 & flags 0
            out.writeInt(0) // pre_defined
            out.writeAscii4(if (track.isVideo) "vide" else "soun")
            out.writeInt(0) // reserved
            out.writeInt(0) // reserved
            out.writeInt(0) // reserved
            val name = if (track.isVideo) "VideoHandler\u0000" else "SoundHandler\u0000"
            out.write(name.toByteArray(Charsets.US_ASCII))
        }

        val mediaHeaderBox = if (track.isVideo) {
            buildBox("vmhd") { out ->
                out.writeInt(0x00000001) // version 0, flags 1
                out.writeShort(0) // graphicsmode
                out.writeShort(0) // opcolor R
                out.writeShort(0) // opcolor G
                out.writeShort(0) // opcolor B
            }
        } else {
            buildBox("smhd") { out ->
                out.writeInt(0) // version 0, flags 0
                out.writeShort(0) // balance
                out.writeShort(0) // reserved
            }
        }

        val urlBox = buildBox("url ") { out ->
            out.writeInt(0x00000001) // self-contained
        }
        val drefBox = buildBox("dref") { out ->
            out.writeInt(0) // version & flags
            out.writeInt(1) // entry_count = 1
            out.write(urlBox)
        }
        val dinfBox = buildBox("dinf") { out ->
            out.write(drefBox)
        }

        val stsdBox = buildStsdBox(track, width, height)
        val sttsBox = buildSttsBox(sampleDeltas)
        val stssBox = if (track.isVideo) buildStssBox(track) else null
        val stscBox = buildBox("stsc") { out ->
            out.writeInt(0) // version & flags
            out.writeInt(1) // entry_count = 1
            out.writeInt(1) // first_chunk = 1
            out.writeInt(1) // samples_per_chunk = 1 (each sample is an independent chunk)
            out.writeInt(1) // sample_description_index = 1
        }
        val stszBox = buildBox("stsz") { out ->
            out.writeInt(0) // version & flags
            out.writeInt(0) // sample_size = 0 (variable)
            out.writeInt(track.samples.size)
            for (s in track.samples) {
                out.writeInt(s.size)
            }
        }
        val stcoBox = buildBox("stco") { out ->
            out.writeInt(0) // version & flags
            out.writeInt(track.samples.size)
            for (s in track.samples) {
                out.writeInt(s.fileOffset.toInt())
            }
        }

        val stblBox = buildBox("stbl") { out ->
            out.write(stsdBox)
            out.write(sttsBox)
            if (stssBox != null) out.write(stssBox)
            out.write(stscBox)
            out.write(stszBox)
            out.write(stcoBox)
        }

        val minfBox = buildBox("minf") { out ->
            out.write(mediaHeaderBox)
            out.write(dinfBox)
            out.write(stblBox)
        }

        val mdiaBox = buildBox("mdia") { out ->
            out.write(mdhdBox)
            out.write(hdlrBox)
            out.write(minfBox)
        }

        return buildBox("trak") { out ->
            out.write(tkhdBox)
            out.write(mdiaBox)
        }
    }

    private fun buildStsdBox(track: TrackState, width: Int, height: Int): ByteArray {
        val entryBox = if (track.isVideo) {
            val sps = track.spsNal?.takeIf { it.size >= 8 } ?: DEFAULT_VALID_AVC_SPS_640X360
            val pps = track.ppsNal?.takeIf { it.size >= 4 } ?: DEFAULT_VALID_AVC_PPS
            val profileIdc = if (sps.size > 1) sps[1].toInt() and 0xFF else 0x42
            val profileCompat = if (sps.size > 2) sps[2].toInt() and 0xFF else 0xC0
            val levelIdc = if (sps.size > 3) sps[3].toInt() and 0xFF else 0x1E

            val avcCBox = buildBox("avcC") { out ->
                out.writeByte(1) // configurationVersion = 1
                out.writeByte(profileIdc)
                out.writeByte(profileCompat)
                out.writeByte(levelIdc)
                out.writeByte(0xFF) // 4-byte NAL unit length (lengthSizeMinusOne = 3)
                out.writeByte(0xE1) // 1 SPS
                out.writeShort(sps.size)
                out.write(sps)
                out.writeByte(1) // 1 PPS
                out.writeShort(pps.size)
                out.write(pps)
            }

            buildBox("avc1") { out ->
                for (i in 0 until 6) out.writeByte(0) // reserved
                out.writeShort(1) // data_reference_index = 1
                out.writeShort(0) // pre_defined
                out.writeShort(0) // reserved
                out.writeInt(0) // pre_defined
                out.writeInt(0)
                out.writeInt(0)
                out.writeShort(width)
                out.writeShort(height)
                out.writeInt(0x00480000) // horizresolution 72 dpi
                out.writeInt(0x00480000) // vertresolution 72 dpi
                out.writeInt(0) // reserved
                out.writeShort(1) // frame_count = 1
                for (i in 0 until 32) out.writeByte(0) // compressorname
                out.writeShort(0x0018) // depth = 24
                out.writeShort(-1) // pre_defined = -1
                out.write(avcCBox)
            }
        } else {
            val sampleRate = track.format.getIntegerOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44100)
            val channels = track.format.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 2)
            val csd = track.aacCsd ?: buildAacAudioSpecificConfig(sampleRate, channels)

            val esdsBox = buildBox("esds") { out ->
                out.writeInt(0) // version & flags
                // ES_Descriptor (tag 0x03)
                out.writeByte(0x03)
                out.writeByte(23 + csd.size)
                out.writeShort(1) // ES_ID = 1
                out.writeByte(0) // flags = 0
                // DecoderConfigDescriptor (tag 0x04)
                out.writeByte(0x04)
                out.writeByte(15 + csd.size)
                out.writeByte(0x40) // Audio ISO/IEC 14496-3
                out.writeByte(0x15) // AudioStream (0x05 << 2 | 1)
                out.writeByte(0) // bufferSizeDB (24-bit)
                out.writeShort(0)
                out.writeInt(128_000) // maxBitrate
                out.writeInt(128_000) // avgBitrate
                // DecoderSpecificInfo (tag 0x05)
                out.writeByte(0x05)
                out.writeByte(csd.size)
                out.write(csd)
                // SLConfigDescriptor (tag 0x06)
                out.writeByte(0x06)
                out.writeByte(0x01)
                out.writeByte(0x02)
            }

            buildBox("mp4a") { out ->
                for (i in 0 until 6) out.writeByte(0) // reserved
                out.writeShort(1) // data_reference_index = 1
                out.writeInt(0) // reserved
                out.writeInt(0) // reserved
                out.writeShort(channels)
                out.writeShort(16) // samplesize = 16
                out.writeShort(0) // pre_defined
                out.writeShort(0) // reserved
                out.writeInt(sampleRate shl 16)
                out.write(esdsBox)
            }
        }

        return buildBox("stsd") { out ->
            out.writeInt(0) // version & flags
            out.writeInt(1) // entry_count = 1
            out.write(entryBox)
        }
    }

    private fun buildSttsBox(sampleDeltas: IntArray): ByteArray {
        val runs = mutableListOf<Pair<Int, Int>>()
        for (delta in sampleDeltas) {
            val last = runs.lastOrNull()
            if (last != null && last.second == delta) {
                runs[runs.lastIndex] = (last.first + 1) to delta
            } else {
                runs.add(1 to delta)
            }
        }
        return buildBox("stts") { out ->
            out.writeInt(0) // version & flags
            out.writeInt(runs.size)
            for ((count, delta) in runs) {
                out.writeInt(count)
                out.writeInt(delta)
            }
        }
    }

    private fun buildStssBox(track: TrackState): ByteArray {
        val syncIndices = mutableListOf<Int>()
        track.samples.forEachIndexed { idx, sample ->
            if (sample.isSyncSample) {
                syncIndices.add(idx + 1)
            }
        }
        if (syncIndices.isEmpty()) {
            syncIndices.add(1)
        }
        return buildBox("stss") { out ->
            out.writeInt(0) // version & flags
            out.writeInt(syncIndices.size)
            for (index in syncIndices) {
                out.writeInt(index)
            }
        }
    }

    private fun writeMatrix(out: DataOutputStream, degrees: Int, width: Int, height: Int) {
        val matrix = when (degrees) {
            90 -> intArrayOf(0, 0x00010000, 0, -0x00010000, 0, 0, height shl 16, 0, 0x40000000)
            180 -> intArrayOf(-0x00010000, 0, 0, 0, -0x00010000, 0, width shl 16, height shl 16, 0x40000000)
            270 -> intArrayOf(0, -0x00010000, 0, 0x00010000, 0, 0, 0, width shl 16, 0x40000000)
            else -> intArrayOf(0x00010000, 0, 0, 0, 0x00010000, 0, 0, 0, 0x40000000)
        }
        for (v in matrix) {
            out.writeInt(v)
        }
    }

    companion object {
        /**
         * Complete, standards-compliant H.264 Baseline Profile Level 3.0 SPS NAL unit (640x360)
         * that passes Media3 NalUnitUtil.parseSpsNalUnit / AvcConfig.parse without error.
         */
        val DEFAULT_VALID_AVC_SPS_640X360 = byteArrayOf(
            0x67.toByte(),
            0x42.toByte(),
            0xC0.toByte(),
            0x1E.toByte(),
            0xDA.toByte(),
            0x02.toByte(),
            0x80.toByte(),
            0xBE.toByte(),
            0x40.toByte()
        )

        val DEFAULT_VALID_AVC_PPS = byteArrayOf(
            0x68.toByte(),
            0xCE.toByte(),
            0x3C.toByte(),
            0x80.toByte()
        )

        private fun buildBox(type: String, writer: (DataOutputStream) -> Unit): ByteArray {
            val payloadBaos = ByteArrayOutputStream()
            val payloadDos = DataOutputStream(payloadBaos)
            writer(payloadDos)
            payloadDos.flush()
            val payload = payloadBaos.toByteArray()

            val boxBaos = ByteArrayOutputStream(8 + payload.size)
            val boxDos = DataOutputStream(boxBaos)
            boxDos.writeInt(8 + payload.size)
            boxDos.writeAscii4(type)
            boxDos.write(payload)
            boxDos.flush()
            return boxBaos.toByteArray()
        }

        private fun DataOutputStream.writeAscii4(fourCc: String) {
            val bytes = fourCc.padEnd(4, ' ').substring(0, 4).toByteArray(Charsets.US_ASCII)
            write(bytes)
        }

        private fun ByteBuffer.toByteArray(): ByteArray {
            val dup = duplicate()
            val arr = ByteArray(dup.remaining())
            dup.get(arr)
            return arr
        }

        private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int {
            return if (containsKey(key)) {
                runCatching { getInteger(key) }.getOrDefault(default)
            } else {
                default
            }
        }

        fun buildAacAudioSpecificConfig(sampleRate: Int, channelCount: Int): ByteArray {
            val freqIdx = when (sampleRate) {
                96000 -> 0
                88200 -> 1
                64000 -> 2
                48000 -> 3
                44100 -> 4
                32000 -> 5
                24000 -> 6
                22050 -> 7
                16000 -> 8
                12000 -> 9
                11025 -> 10
                8000 -> 11
                else -> 4
            }
            val ch = channelCount.coerceIn(1, 2)
            val b0 = (2 shl 3) or (freqIdx shr 1)
            val b1 = ((freqIdx and 1) shl 7) or (ch shl 3)
            return byteArrayOf(b0.toByte(), b1.toByte())
        }

        fun splitAnnexBNals(data: ByteArray): List<ByteArray> {
            val result = mutableListOf<ByteArray>()
            var i = 0
            var nalStart = -1

            while (i < data.size) {
                val isStartCode4 = i + 3 < data.size &&
                    data[i] == 0.toByte() &&
                    data[i + 1] == 0.toByte() &&
                    data[i + 2] == 0.toByte() &&
                    data[i + 3] == 1.toByte()
                val isStartCode3 = !isStartCode4 &&
                    i + 2 < data.size &&
                    data[i] == 0.toByte() &&
                    data[i + 1] == 0.toByte() &&
                    data[i + 2] == 1.toByte()

                if (isStartCode4 || isStartCode3) {
                    if (nalStart >= 0 && i > nalStart) {
                        result.add(data.copyOfRange(nalStart, i))
                    }
                    val step = if (isStartCode4) 4 else 3
                    nalStart = i + step
                    i += step
                } else {
                    i++
                }
            }
            if (nalStart in 0 until data.size) {
                result.add(data.copyOfRange(nalStart, data.size))
            } else if (nalStart < 0 && data.isNotEmpty()) {
                result.add(data)
            }
            return result
        }
    }
}
