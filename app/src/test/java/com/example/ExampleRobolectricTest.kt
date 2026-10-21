package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ads.OptionalAdManager
import com.example.data.local.FavoriteEntity
import com.example.data.local.JobStatus
import com.example.data.local.MediaEntity
import com.example.data.local.MediaType
import com.example.data.local.PlaybackHistoryEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.ProcessingJobEntity
import com.example.data.local.ProcessingOperationType
import com.example.data.local.PulseDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.media.BiquadCoefficients
import com.example.media.BiquadFilterType
import com.example.media.ConversionCapabilityRegistry
import com.example.media.EqualizerController
import com.example.media.EqualizerDiagnosticState
import com.example.media.EqualizerDspConfig
import com.example.media.EqualizerProcessingMode
import com.example.media.HardwareEqualizerState
import com.example.media.IsoMp4Muxer
import com.example.media.MediaProcessorEngine
import com.example.media.MultiBandBiquadFilterBank
import com.example.media.ProcessingOutcome
import com.example.media.PulseEqualizerAudioProcessor
import com.example.media.SampleMediaGenerator
import com.example.media.SubtitleFormat
import com.example.media.SubtitleLoadResult
import com.example.media.SubtitleParser
import com.example.service.PulsePlaybackService
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: PulseDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PulseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `app name and package identity match PulsePlay specification`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("PulsePlay", appName)
        assertEquals("com.aistudio.pulseplay.mpxvqr", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `subtitle parser validates real SRT and WebVTT cues and sync offset`() {
        val srtRaw = """
            1
            00:00:01,000 --> 00:00:03,500
            <i>First</i> SRT subtitle line

            2
            00:00:04,000 --> 00:00:06,000
            Second SRT subtitle line
        """.trimIndent()

        val srtResult = SubtitleParser.parseSubtitleContent(srtRaw, "sample.srt")
        assertTrue(srtResult is SubtitleLoadResult.Success)
        val successSrt = srtResult as SubtitleLoadResult.Success
        assertEquals(SubtitleFormat.SRT, successSrt.format)
        assertEquals(2, successSrt.cues.size)
        assertEquals("First SRT subtitle line", successSrt.cues[0].text)

        // Verify active cue lookup with 0ms and +500ms sync offset
        val cueAt2Sec = SubtitleParser.findActiveCueText(successSrt.cues, 2000L, 0L)
        assertEquals("First SRT subtitle line", cueAt2Sec)
        val cueWithOffset = SubtitleParser.findActiveCueText(successSrt.cues, 4200L, 1000L)
        assertEquals("First SRT subtitle line", cueWithOffset)

        // Verify WebVTT parsing
        val vttRaw = """
            WEBVTT

            00:01.200 --> 00:03.000
            WebVTT cue one
        """.trimIndent()
        val vttResult = SubtitleParser.parseSubtitleContent(vttRaw, "sample.vtt")
        assertTrue(vttResult is SubtitleLoadResult.Success)
        assertEquals(SubtitleFormat.WEBVTT, (vttResult as SubtitleLoadResult.Success).format)

        // Verify invalid/corrupt subtitle rejection (Phase 9 requirement)
        val invalidResult = SubtitleParser.parseSubtitleContent("Not a valid subtitle file at all", "fake.srt")
        assertTrue(invalidResult is SubtitleLoadResult.Error)
    }

    @Test
    fun `room database persists playlists favorites non-destructive history and job recovery`() = runBlocking {
        val dao = database.pulseDao()
        val sampleFile = File(context.cacheDir, "test_track.wav")
        SampleMediaGenerator(context).generateStereoWavFile(sampleFile, durationSeconds = 4)
        assertTrue(sampleFile.exists() && sampleFile.length() > 44L)

        val entity = MediaEntity(
            uriString = Uri.fromFile(sampleFile).toString(),
            mediaStoreId = null,
            title = "Studio Test Track",
            artist = "PulsePlay QA",
            album = "Unit Tests",
            folderName = "QA Samples",
            durationMs = 4000L,
            sizeBytes = sampleFile.length(),
            mimeType = "audio/wav",
            mediaType = MediaType.AUDIO,
            dateAddedEpochSec = 1700000000L
        )
        dao.upsertMediaItem(entity)

        // Favorites
        dao.addFavorite(FavoriteEntity(uriString = entity.uriString))
        assertTrue(dao.isFavorite(entity.uriString))
        val favList = dao.observeFavoriteMediaItems().first()
        assertEquals(1, favList.size)

        // Playlists & ordering transaction
        val playlistId = dao.insertPlaylist(PlaylistEntity(name = "Acoustic Test Set"))
        dao.addTrackToPlaylist(playlistId, entity.uriString)
        val plMedia = dao.observePlaylistMedia(playlistId).first()
        assertEquals(1, plMedia.size)
        assertEquals("Studio Test Track", plMedia[0].title)

        // History recording & non-destructive clearing (Phase 4 requirement: never deletes media file)
        dao.upsertHistory(
            PlaybackHistoryEntity(
                uriString = entity.uriString,
                titleSnapshot = entity.title,
                mediaType = MediaType.AUDIO,
                lastPositionMs = 2100L,
                durationMs = 4000L,
                playCount = 1
            )
        )
        assertEquals(2100L, dao.getHistoryForUri(entity.uriString)?.lastPositionMs)
        dao.clearAllHistory()
        assertNull(dao.getHistoryForUri(entity.uriString))
        assertTrue("Clearing history must never delete the underlying media file", sampleFile.exists())

        // Interrupted processing job recovery on startup (Phase 16)
        dao.insertProcessingJob(
            ProcessingJobEntity(
                operationType = ProcessingOperationType.AUDIO_TRIM,
                sourceUri = entity.uriString,
                sourceTitle = entity.title,
                outputUri = null,
                outputFormat = "WAV",
                status = JobStatus.RUNNING
            )
        )
        dao.recoverInterruptedJobsOnStartup()
        val jobs = dao.observeProcessingJobs().first()
        assertEquals(JobStatus.CANCELLED, jobs.first().status)
    }

    @Test
    fun `audio trimmer produces real valid RIFF WAVE output without overwriting original`() = runBlocking {
        val dao = database.pulseDao()
        val generator = SampleMediaGenerator(context)
        val sourceFile = File(context.filesDir, "source_trim_test.wav")
        generator.generateStereoWavFile(sourceFile, durationSeconds = 4)
        val originalSize = sourceFile.length()

        val sourceEntity = MediaEntity(
            uriString = Uri.fromFile(sourceFile).toString(),
            mediaStoreId = null,
            title = "Trimming Source WAV",
            artist = "PulsePlay QA",
            album = "Audio Editor Tests",
            folderName = "Audio Lab",
            durationMs = 4000L,
            sizeBytes = originalSize,
            mimeType = "audio/wav",
            mediaType = MediaType.AUDIO,
            dateAddedEpochSec = 1700000100L
        )
        dao.upsertMediaItem(sourceEntity)

        val engine = MediaProcessorEngine(context, dao)
        val outcome = engine.executeAudioTrim(
            sourceMedia = sourceEntity,
            startMs = 500L,
            endMs = 2500L,
            outputFormatExt = "wav"
        )

        assertTrue("Audio trim should succeed", outcome is ProcessingOutcome.Success)
        val success = outcome as ProcessingOutcome.Success
        assertTrue(success.outputFile.exists())
        assertTrue(success.outputFile.length() > 44L)
        assertTrue("Trimmed 2s file should be smaller than 4s source", success.outputFile.length() < originalSize)
        assertEquals("Original file must remain untouched", originalSize, sourceFile.length())

        // Verify RIFF header signature on exported WAV
        val header = ByteArray(12)
        success.outputFile.inputStream().use { it.read(header) }
        assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(header, 8, 4, Charsets.US_ASCII))

        // Verify format conversion to WAV_PCM_16BIT
        val convOutcome = engine.executeAudioExtractionOrConversion(
            sourceMedia = sourceEntity,
            profileId = "WAV_PCM_16BIT",
            targetBitrateKbps = 1411,
            isExtractionFromVideo = false
        )
        assertTrue("WAV conversion should succeed", convOutcome is ProcessingOutcome.Success)
        val convSuccess = convOutcome as ProcessingOutcome.Success
        assertTrue(convSuccess.outputFile.exists() && convSuccess.outputFile.length() > 44L)

        // Verify invalid trim range fails gracefully and records FAILED status
        val invalidOutcome = engine.executeAudioTrim(
            sourceMedia = sourceEntity,
            startMs = 2500L,
            endMs = 1000L,
            outputFormatExt = "wav"
        )
        assertTrue("Inverted trim range must fail gracefully", invalidOutcome is ProcessingOutcome.Failure)
    }

    @Test
    fun `biquad filter coefficients are stable across all frequency bands and gain limits`() {
        val sampleRates = listOf(22050.0, 44100.0, 48000.0, 96000.0)
        val frequencies = listOf(5.0, 60.0, 85.0, 230.0, 910.0, 3600.0, 14000.0, 25000.0)
        val gains = listOf(-40.0, -15.0, -6.0, 0.0, 6.0, 15.0, 40.0)

        for (sr in sampleRates) {
            for (freq in frequencies) {
                for (gain in gains) {
                    for (type in BiquadFilterType.entries) {
                        val coeff = BiquadCoefficients.design(
                            type = type,
                            centerFreqHz = freq,
                            sampleRateHz = sr,
                            gainDb = gain,
                            q = 1.0
                        )
                        assertTrue(
                            "Filter ($type, freq=$freq, sr=$sr, gain=$gain) must satisfy Jury stability criteria",
                            coeff.isStable()
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `flat equalizer and disabled equalizer leave PCM audio signal unchanged`() {
        val processor = PulseEqualizerAudioProcessor()
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        val inputSignal = synthesizeMultiTonePcm16Stereo(
            sampleRateHz = 44100,
            frameCount = 2048,
            frequenciesHz = listOf(60.0, 230.0, 910.0, 3600.0, 12000.0),
            amplitude = 0.25
        )

        // 1. Flat enabled EQ (all bands 0 mB, bass boost 0)
        processor.updateDspConfig(
            EqualizerDspConfig(
                isEnabled = true,
                bandLevelsMb = listOf(0, 0, 0, 0, 0),
                bassBoostStrength = 0
            )
        )
        val flatOut = runThroughAudioProcessor(processor, inputSignal)
        assertEquals(inputSignal.size, flatOut.size)
        for (i in inputSignal.indices) {
            assertEquals("Flat EQ sample $i must match input exactly", inputSignal[i].toInt(), flatOut[i].toInt())
        }

        // 2. Disabled EQ with non-zero band settings must bypass cleanly
        processor.updateDspConfig(
            EqualizerDspConfig(
                isEnabled = false,
                bandLevelsMb = listOf(1200, -1000, 900, -800, 1400),
                bassBoostStrength = 900
            )
        )
        val bypassedOut = runThroughAudioProcessor(processor, inputSignal)
        for (i in inputSignal.indices) {
            assertEquals("Disabled EQ sample $i must bypass cleanly", inputSignal[i].toInt(), bypassedOut[i].toInt())
        }
    }

    @Test
    fun `each equalizer frequency band and bass boost measurably alters target spectral energy without clipping`() {
        val sampleRate = 44100
        val frameCount = 4410 // 100 ms of stereo audio (exact integer cycles for 60, 230, 910, 3600, 14000 Hz)
        val testFreqs = listOf(60.0, 230.0, 910.0, 3600.0, 14000.0)
        val baseSignal = synthesizeMultiTonePcm16Stereo(
            sampleRateHz = sampleRate,
            frameCount = frameCount,
            frequenciesHz = testFreqs,
            amplitude = 0.14
        )

        val baseEnergies = testFreqs.map { freq ->
            computeGoertzelMagnitudeLeftChannel(baseSignal, sampleRate, freq)
        }

        // Verify boosting each individual band increases energy in its target frequency region
        // and cutting that band decreases energy in its target frequency region
        for (bandIdx in 0 until 5) {
            val boostLevels = MutableList(5) { 0 }.apply { this[bandIdx] = 1200 } // +12 dB
            val cutLevels = MutableList(5) { 0 }.apply { this[bandIdx] = -1200 }  // -12 dB

            val boostBank = MultiBandBiquadFilterBank(sampleRate, 2).apply {
                updateConfig(EqualizerDspConfig(isEnabled = true, bandLevelsMb = boostLevels))
            }
            val cutBank = MultiBandBiquadFilterBank(sampleRate, 2).apply {
                updateConfig(EqualizerDspConfig(isEnabled = true, bandLevelsMb = cutLevels))
            }

            val boostedSignal = boostBank.processPcm16Array(baseSignal)
            val cutSignal = cutBank.processPcm16Array(baseSignal)

            val boostedMag = computeGoertzelMagnitudeLeftChannel(boostedSignal, sampleRate, testFreqs[bandIdx])
            val cutMag = computeGoertzelMagnitudeLeftChannel(cutSignal, sampleRate, testFreqs[bandIdx])
            val origMag = baseEnergies[bandIdx]

            assertTrue(
                "Band $bandIdx (${testFreqs[bandIdx]} Hz) +12 dB boost must increase target energy (orig=$origMag, boosted=$boostedMag)",
                boostedMag > origMag * 1.45
            )
            assertTrue(
                "Band $bandIdx (${testFreqs[bandIdx]} Hz) -12 dB cut must decrease target energy (orig=$origMag, cut=$cutMag)",
                cutMag < origMag * 0.65
            )
        }

        // Verify Bass Boost (strength = 1000) measurably increases 60 Hz low-frequency energy while keeping 3600 Hz nearly unchanged
        val bassBank = MultiBandBiquadFilterBank(sampleRate, 2).apply {
            updateConfig(
                EqualizerDspConfig(
                    isEnabled = true,
                    bandLevelsMb = listOf(0, 0, 0, 0, 0),
                    bassBoostStrength = 1000
                )
            )
        }
        val bassSignal = bassBank.processPcm16Array(baseSignal)
        val bass60Mag = computeGoertzelMagnitudeLeftChannel(bassSignal, sampleRate, 60.0)
        val bass3600Mag = computeGoertzelMagnitudeLeftChannel(bassSignal, sampleRate, 3600.0)
        assertTrue(
            "Bass Boost must significantly increase 60 Hz energy (orig=${baseEnergies[0]}, boosted=$bass60Mag)",
            bass60Mag > baseEnergies[0] * 1.50
        )
        val highMidRatio = bass3600Mag / baseEnergies[3]
        assertTrue(
            "Bass Boost should not heavily alter 3.6 kHz band (ratio=$highMidRatio)",
            highMidRatio in 0.85..1.15
        )

        // Verify soft-knee limiter prevents wrap-around or clipping under extreme boost (+15 dB all bands + 1000 Bass Boost)
        val hotSignal = synthesizeMultiTonePcm16Stereo(
            sampleRateHz = sampleRate,
            frameCount = 2048,
            frequenciesHz = listOf(60.0, 230.0, 910.0),
            amplitude = 0.90
        )
        val extremeBank = MultiBandBiquadFilterBank(sampleRate, 2).apply {
            updateConfig(
                EqualizerDspConfig(
                    isEnabled = true,
                    bandLevelsMb = listOf(1500, 1500, 1500, 1500, 1500),
                    bassBoostStrength = 1000
                )
            )
        }
        val limitedOut = extremeBank.processPcm16Array(hotSignal)
        assertTrue(limitedOut.all { it in Short.MIN_VALUE..Short.MAX_VALUE })
        // Soft limiter caps below hard full-scale 32767
        assertTrue("Soft limiter must prevent hard digital saturation at 32767", limitedOut.all { abs(it.toInt()) < 32767 })
    }

    @Test
    fun `equalizer controller toggles presets persists state and handles session transitions cleanly`() = runBlocking {
        val processor = PulseEqualizerAudioProcessor()
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        val eqController = EqualizerController(audioProcessor = processor)

        // 1. Initially disabled
        assertEquals(EqualizerDiagnosticState.EQUALIZER_DISABLED, eqController.state.value.diagnosticState)
        assertEquals(EqualizerProcessingMode.BYPASSED, eqController.state.value.processingMode)

        // 2. Bind to sessionId == 0 with enabled = true -> active software DSP because processor stream is configured
        eqController.bindToAudioSession(
            audioSessionId = 0,
            enabled = true,
            presetIndex = 1,
            bassStrength = 400,
            savedBandLevelsMb = emptyList(),
            isPlaybackActive = true
        )
        var eqState = eqController.state.value
        assertEquals(5, eqState.bands.size)
        assertEquals(400, eqState.bassBoostStrength)
        assertEquals(EqualizerProcessingMode.SOFTWARE_BIQUAD_DSP, eqState.processingMode)
        assertEquals(
            HardwareEqualizerState.presetCurveForIndex(1),
            eqState.bands.map { it.currentLevelMb }
        )

        // 3. Verify all built-in presets produce distinct real filter curves and modify audio differently
        val testInput = synthesizeMultiTonePcm16Stereo(
            sampleRateHz = 44100,
            frameCount = 2048,
            frequenciesHz = listOf(60.0, 230.0, 910.0, 3600.0, 12000.0),
            amplitude = 0.20
        )
        eqController.setBassBoost(0)
        eqController.applyPreset(0) // Flat
        val flatPcm = runThroughAudioProcessor(processor, testInput)

        eqController.applyPreset(1) // Bass & Warmth
        val bassWarmthPcm = runThroughAudioProcessor(processor, testInput)

        eqController.applyPreset(2) // Vocal Clarity
        val vocalClarityPcm = runThroughAudioProcessor(processor, testInput)

        assertFalse("Bass & Warmth preset must alter PCM output vs Flat", flatPcm.contentEquals(bassWarmthPcm))
        assertFalse("Vocal Clarity preset must alter PCM output vs Bass & Warmth", bassWarmthPcm.contentEquals(vocalClarityPcm))

        // 4. Enable/disable toggle bypasses and restores processing
        eqController.setEnabled(false)
        assertEquals(EqualizerDiagnosticState.EQUALIZER_DISABLED, eqController.state.value.diagnosticState)
        val disabledPcm = runThroughAudioProcessor(processor, testInput)
        assertTrue("Disabled EQ must match Flat unboosted signal", flatPcm.contentEquals(disabledPcm))

        eqController.setEnabled(true)
        val reenabledPcm = runThroughAudioProcessor(processor, testInput)
        assertFalse("Re-enabled EQ must restore filtering", flatPcm.contentEquals(reenabledPcm))

        // 5. Unsupported hardware effect fallback verification
        eqController.setPreferHardwareEffect(true)
        eqController.bindToAudioSession(
            audioSessionId = 42,
            enabled = true,
            presetIndex = 2,
            bassStrength = 500,
            savedBandLevelsMb = HardwareEqualizerState.presetCurveForIndex(2),
            isPlaybackActive = true
        )
        eqState = eqController.state.value
        assertEquals(42, eqState.activeAudioSessionId)
        assertEquals(EqualizerProcessingMode.SOFTWARE_BIQUAD_DSP, eqState.processingMode)
        assertEquals(EqualizerDiagnosticState.HARDWARE_UNSUPPORTED_SOFTWARE_ACTIVE, eqState.diagnosticState)

        // 6. Session transition & preferences persistence
        val prefsRepo = UserPreferencesRepository(context)
        val customCurve = listOf(450, 250, -150, 300, 550)
        eqController.setPreferHardwareEffect(false)
        customCurve.forEachIndexed { idx, mb -> eqController.setBandLevel(idx, mb) }
        eqController.setBassBoost(650)

        prefsRepo.setEqualizerState(
            enabled = eqController.state.value.isEnabled,
            presetIndex = 3,
            bassBoostStrength = eqController.state.value.bassBoostStrength,
            bandLevelsMb = eqController.state.value.bands.map { it.currentLevelMb }
        )

        val savedPrefs = prefsRepo.preferencesFlow.first()
        assertTrue(savedPrefs.equalizerEnabled)
        assertEquals(3, savedPrefs.equalizerPresetIndex)
        assertEquals(650, savedPrefs.bassBoostStrength)
        assertEquals(customCurve, savedPrefs.customBandLevelsMb)

        // Simulate player recreation and new audio session binding
        val restoredController = EqualizerController(audioProcessor = processor)
        restoredController.bindToAudioSession(
            audioSessionId = 99,
            enabled = savedPrefs.equalizerEnabled,
            presetIndex = savedPrefs.equalizerPresetIndex,
            bassStrength = savedPrefs.bassBoostStrength,
            savedBandLevelsMb = savedPrefs.customBandLevelsMb,
            isPlaybackActive = true
        )
        assertEquals(99, restoredController.state.value.activeAudioSessionId)
        assertEquals(customCurve, restoredController.state.value.bands.map { it.currentLevelMb })
        assertEquals(650, restoredController.state.value.bassBoostStrength)

        // Verify ExoPlayer builder with custom AudioSink & PulseEqualizerAudioProcessor constructs cleanly
        val exoPlayer = PulsePlaybackService.buildExoPlayerWithEqualizerDsp(context, processor)
        assertNotNull(exoPlayer)
        exoPlayer.release()

        restoredController.releaseEffects()
        eqController.releaseEffects()
    }

    @Test
    fun `conversion registry and privacy defaults and IsoMp4Muxer behave deterministically`() {
        // Conversion capability registry
        val profiles = ConversionCapabilityRegistry.getSupportedProfiles()
        assertTrue(profiles.isNotEmpty())
        assertTrue(profiles.any { it.id == "WAV_PCM_16BIT" && it.isHardwareSupportedOnDevice })

        // Optional Advertising disabled by default (Phase 14)
        assertFalse(
            OptionalAdManager.canShowOptionalTestBanner(
                moduleEnabledInSettings = false,
                explicitUserConsentGranted = false
            )
        )
        assertEquals("None (Zero Third-Party Ad SDK Bundled)", OptionalAdManager.auditReport.sdkBundled)

        // Verify no INTERNET permission in package info (Phase 13)
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requestedPerms = pkgInfo.requestedPermissions?.toList().orEmpty()
        assertFalse(
            "INTERNET permission must not be present in offline-first manifest",
            requestedPerms.contains(android.Manifest.permission.INTERNET)
        )

        // Verify IsoMp4Muxer writes valid ftyp/mdat/moov MP4 container without native MPEG4Writer
        val testMp4 = File(context.cacheDir, "iso_mux_test.mp4")
        val muxer = IsoMp4Muxer(testMp4, majorBrand = "mp42")
        muxer.setOrientationHint(90)
        val vFmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 640, 360)
        val aFmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2)
        val vTrack = muxer.addTrack(vFmt)
        val aTrack = muxer.addTrack(aFmt)
        muxer.start()

        val annexBIdr = byteArrayOf(0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1E, 0, 0, 0, 1, 0x68, 0xCE.toByte(), 0x3C, 0x80.toByte(), 0, 0, 0, 1, 0x65, 0x88.toByte(), 0x84.toByte(), 0x21)
        val vInfo = MediaCodec.BufferInfo().apply {
            set(0, annexBIdr.size, 0L, MediaCodec.BUFFER_FLAG_KEY_FRAME)
        }
        muxer.writeSampleData(vTrack, ByteBuffer.wrap(annexBIdr), vInfo)

        val aacFrame = byteArrayOf(0x21, 0x10, 0x04, 0x60, 0x8C.toByte(), 0x1C)
        val aInfo = MediaCodec.BufferInfo().apply {
            set(0, aacFrame.size, 0L, MediaCodec.BUFFER_FLAG_KEY_FRAME)
        }
        muxer.writeSampleData(aTrack, ByteBuffer.wrap(aacFrame), aInfo)
        muxer.stop()
        muxer.release()

        assertTrue(testMp4.exists() && testMp4.length() > 128L)
        val mp4Bytes = testMp4.readBytes()
        assertEquals("ftyp", String(mp4Bytes, 4, 4, Charsets.US_ASCII))
        assertTrue(String(mp4Bytes, Charsets.ISO_8859_1).contains("moov"))
        assertTrue(String(mp4Bytes, Charsets.ISO_8859_1).contains("avc1"))
        assertTrue(String(mp4Bytes, Charsets.ISO_8859_1).contains("mp4a"))
        testMp4.delete()
    }

    private fun synthesizeMultiTonePcm16Stereo(
        sampleRateHz: Int,
        frameCount: Int,
        frequenciesHz: List<Double>,
        amplitude: Double
    ): ShortArray {
        val out = ShortArray(frameCount * 2)
        val perToneAmp = amplitude / frequenciesHz.size.coerceAtLeast(1)
        for (n in 0 until frameCount) {
            var sum = 0.0
            for (freq in frequenciesHz) {
                sum += perToneAmp * sin(2.0 * PI * freq * n / sampleRateHz)
            }
            val sampleShort = (sum * 32767.0).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            out[n * 2] = sampleShort
            out[n * 2 + 1] = sampleShort
        }
        return out
    }

    private fun runThroughAudioProcessor(
        processor: PulseEqualizerAudioProcessor,
        inputSamples: ShortArray
    ): ShortArray {
        processor.flush()
        val byteBuf = ByteBuffer.allocateDirect(inputSamples.size * 2).order(ByteOrder.nativeOrder())
        for (s in inputSamples) {
            byteBuf.putShort(s)
        }
        byteBuf.flip()
        processor.queueInput(byteBuf)
        val outBuf = processor.output.order(ByteOrder.nativeOrder())
        val outShorts = ShortArray(outBuf.remaining() / 2)
        for (i in outShorts.indices) {
            outShorts[i] = outBuf.short
        }
        return outShorts
    }

    /**
     * Computes the Goertzel spectral magnitude of the left channel at [targetFreqHz],
     * skipping the first 256 frames to allow biquad IIR transient settling.
     */
    private fun computeGoertzelMagnitudeLeftChannel(
        interleavedStereo: ShortArray,
        sampleRateHz: Int,
        targetFreqHz: Double
    ): Double {
        val totalFrames = interleavedStereo.size / 2
        val startFrame = 256.coerceAtMost(totalFrames / 4)
        val nFrames = (totalFrames - startFrame).coerceAtLeast(1)
        val omega = 2.0 * PI * targetFreqHz / sampleRateHz
        val coeff = 2.0 * cos(omega)
        var s0: Double
        var s1 = 0.0
        var s2 = 0.0
        for (frame in startFrame until totalFrames) {
            val x = interleavedStereo[frame * 2].toDouble() / 32768.0
            s0 = x + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
        return sqrt(power.coerceAtLeast(0.0)) / nFrames
    }
}
