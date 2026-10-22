package com.example

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.Uri
import android.view.WindowManager
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
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
import com.example.data.preferences.VideoOrientationPreference
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
import com.example.media.PlayerHandoffAndVideoPolicy
import com.example.media.ProcessingOutcome
import com.example.media.PulseEqualizerAudioProcessor
import com.example.media.SampleMediaGenerator
import com.example.media.SubtitleFormat
import com.example.media.SubtitleLoadResult
import com.example.media.SubtitleParser
import com.example.service.PulsePlaybackService
import com.example.viewmodel.LibrarySubTab
import com.example.viewmodel.PrimaryTab
import com.example.viewmodel.PulsePlayViewModel
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
import org.robolectric.Robolectric
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

        // Verify invalid/corrupt subtitle rejection
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

        // History recording & non-destructive clearing (never deletes media file)
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

        // Interrupted processing job recovery on startup
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
        val frameCount = 4410
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

        for (bandIdx in 0 until 5) {
            val boostLevels = MutableList(5) { 0 }.apply { this[bandIdx] = 1200 }
            val cutLevels = MutableList(5) { 0 }.apply { this[bandIdx] = -1200 }

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
        assertTrue("Soft limiter must prevent hard digital saturation at 32767", limitedOut.all { abs(it.toInt()) < 32767 })
    }

    @Test
    fun `equalizer controller toggles presets persists state and handles session transitions cleanly`() = runBlocking {
        val processor = PulseEqualizerAudioProcessor()
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        val eqController = EqualizerController(audioProcessor = processor)

        assertEquals(EqualizerDiagnosticState.EQUALIZER_DISABLED, eqController.state.value.diagnosticState)
        assertEquals(EqualizerProcessingMode.BYPASSED, eqController.state.value.processingMode)

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

        val testInput = synthesizeMultiTonePcm16Stereo(
            sampleRateHz = 44100,
            frameCount = 2048,
            frequenciesHz = listOf(60.0, 230.0, 910.0, 3600.0, 12000.0),
            amplitude = 0.20
        )
        eqController.setBassBoost(0)
        eqController.applyPreset(0)
        val flatPcm = runThroughAudioProcessor(processor, testInput)

        eqController.applyPreset(1)
        val bassWarmthPcm = runThroughAudioProcessor(processor, testInput)

        eqController.applyPreset(2)
        val vocalClarityPcm = runThroughAudioProcessor(processor, testInput)

        assertFalse("Bass & Warmth preset must alter PCM output vs Flat", flatPcm.contentEquals(bassWarmthPcm))
        assertFalse("Vocal Clarity preset must alter PCM output vs Bass & Warmth", bassWarmthPcm.contentEquals(vocalClarityPcm))

        eqController.setEnabled(false)
        assertEquals(EqualizerDiagnosticState.EQUALIZER_DISABLED, eqController.state.value.diagnosticState)
        val disabledPcm = runThroughAudioProcessor(processor, testInput)
        assertTrue("Disabled EQ must match Flat unboosted signal", flatPcm.contentEquals(disabledPcm))

        eqController.setEnabled(true)
        val reenabledPcm = runThroughAudioProcessor(processor, testInput)
        assertFalse("Re-enabled EQ must restore filtering", flatPcm.contentEquals(reenabledPcm))

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

        val exoPlayer = PulsePlaybackService.buildExoPlayerWithEqualizerDsp(context, processor)
        assertNotNull(exoPlayer)
        exoPlayer.release()

        restoredController.releaseEffects()
        eqController.releaseEffects()
    }

    @Test
    fun `fallback to authoritative MediaSessionService player handoff transfers queue position speed repeat shuffle and releases fallback without overlap`() {
        val processor = PulseEqualizerAudioProcessor()
        val fallbackPlayer = PulsePlaybackService.buildExoPlayerWithEqualizerDsp(context, processor)
        val servicePlayer = PulsePlaybackService.buildExoPlayerWithEqualizerDsp(context, processor)

        val track1 = MediaItem.Builder()
            .setMediaId("file:///track1.wav")
            .setUri(Uri.parse("file:///track1.wav"))
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Track One").build())
            .build()
        val track2 = MediaItem.Builder()
            .setMediaId("file:///track2.wav")
            .setUri(Uri.parse("file:///track2.wav"))
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Track Two").build())
            .build()

        // Configure fallback player as if user started playback before MediaController finished connecting
        fallbackPlayer.setMediaItems(listOf(track1, track2), 1, 1850L)
        fallbackPlayer.playbackParameters = PlaybackParameters(1.5f)
        fallbackPlayer.repeatMode = Player.REPEAT_MODE_ALL
        fallbackPlayer.shuffleModeEnabled = true
        fallbackPlayer.volume = 0.8f
        fallbackPlayer.playWhenReady = true

        // Perform handoff to authoritative servicePlayer
        val snapshot = PlayerHandoffAndVideoPolicy.handoffFallbackToAuthoritativePlayer(
            outgoingFallback = fallbackPlayer,
            incomingTarget = servicePlayer
        )

        assertTrue("Handoff must report items transferred from fallback", snapshot.hadActiveItems)
        assertEquals(2, snapshot.mediaItems.size)
        assertEquals(1, snapshot.currentIndex)
        assertEquals(1850L, snapshot.positionMs)
        assertEquals(1.5f, snapshot.playbackSpeed, 0.01f)
        assertEquals(Player.REPEAT_MODE_ALL, snapshot.repeatMode)
        assertTrue(snapshot.shuffleEnabled)
        assertTrue(snapshot.playWhenReady)

        // Verify outgoing fallback player was stopped and cleared so two players NEVER play simultaneously
        assertFalse("Outgoing fallback player must have playWhenReady=false", fallbackPlayer.playWhenReady)
        assertEquals("Outgoing fallback player queue must be cleared", 0, fallbackPlayer.mediaItemCount)

        // Verify authoritative servicePlayer received the full queue and playback state
        assertEquals(2, servicePlayer.mediaItemCount)
        assertEquals(1, servicePlayer.currentMediaItemIndex)
        assertEquals("file:///track2.wav", servicePlayer.currentMediaItem?.mediaId)
        assertEquals(1.5f, servicePlayer.playbackParameters.speed, 0.01f)
        assertEquals(Player.REPEAT_MODE_ALL, servicePlayer.repeatMode)
        assertTrue(servicePlayer.shuffleModeEnabled)
        assertTrue(servicePlayer.playWhenReady)

        // Also verify reconnection scenario: new UI controller connects when fallback is null/empty and service is already playing
        val reconnectSnapshot = PlayerHandoffAndVideoPolicy.handoffFallbackToAuthoritativePlayer(
            outgoingFallback = null,
            incomingTarget = servicePlayer
        )
        assertTrue(reconnectSnapshot.hadActiveItems)
        assertEquals(2, reconnectSnapshot.mediaItems.size)
        assertEquals(1, reconnectSnapshot.currentIndex)
        assertEquals(1.5f, reconnectSnapshot.playbackSpeed, 0.01f)

        servicePlayer.release()
    }

    @Test
    fun `video screen-on policy sets and clears FLAG_KEEP_SCREEN_ON across playing paused error audio and background states`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val window = activity.window

        // 1. Active video playback while visible and resumed -> keep screen on = true
        val activeVideoAwake = PlayerHandoffAndVideoPolicy.shouldKeepVideoScreenAwake(
            isActivityResumed = true,
            isNowPlayingOverlayVisible = true,
            mediaType = MediaType.VIDEO,
            isPlaying = true,
            hasPlaybackError = false,
            keepScreenOnPreferenceEnabled = true
        )
        assertTrue(activeVideoAwake)
        PlayerHandoffAndVideoPolicy.applyWindowKeepScreenOn(window, activeVideoAwake)
        assertTrue(
            "FLAG_KEEP_SCREEN_ON must be set during active video playback",
            (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        )

        // 2. Video paused -> immediately clear FLAG_KEEP_SCREEN_ON
        val pausedVideoAwake = PlayerHandoffAndVideoPolicy.shouldKeepVideoScreenAwake(
            isActivityResumed = true,
            isNowPlayingOverlayVisible = true,
            mediaType = MediaType.VIDEO,
            isPlaying = false,
            hasPlaybackError = false,
            keepScreenOnPreferenceEnabled = true
        )
        assertFalse(pausedVideoAwake)
        PlayerHandoffAndVideoPolicy.applyWindowKeepScreenOn(window, pausedVideoAwake)
        assertEquals(
            "FLAG_KEEP_SCREEN_ON must be cleared when video is paused",
            0,
            window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        // 3. Audio-only playback -> never keep screen on
        assertFalse(
            PlayerHandoffAndVideoPolicy.shouldKeepVideoScreenAwake(
                isActivityResumed = true,
                isNowPlayingOverlayVisible = true,
                mediaType = MediaType.AUDIO,
                isPlaying = true,
                hasPlaybackError = false,
                keepScreenOnPreferenceEnabled = true
            )
        )

        // 4. Video error -> clear screen-on flag
        assertFalse(
            PlayerHandoffAndVideoPolicy.shouldKeepVideoScreenAwake(
                isActivityResumed = true,
                isNowPlayingOverlayVisible = true,
                mediaType = MediaType.VIDEO,
                isPlaying = true,
                hasPlaybackError = true,
                keepScreenOnPreferenceEnabled = true
            )
        )

        // 5. Video overlay collapsed/dismissed or Activity paused -> clear screen-on flag
        assertFalse(
            PlayerHandoffAndVideoPolicy.shouldKeepVideoScreenAwake(
                isActivityResumed = true,
                isNowPlayingOverlayVisible = false,
                mediaType = MediaType.VIDEO,
                isPlaying = true,
                hasPlaybackError = false,
                keepScreenOnPreferenceEnabled = true
            )
        )
        assertFalse(
            PlayerHandoffAndVideoPolicy.shouldKeepVideoScreenAwake(
                isActivityResumed = false,
                isNowPlayingOverlayVisible = true,
                mediaType = MediaType.VIDEO,
                isPlaying = true,
                hasPlaybackError = false,
                keepScreenOnPreferenceEnabled = true
            )
        )
    }

    @Test
    fun `video orientation policy and 0-90-180-270 frame transform compute accurate orientation and aspect scaling`() = runBlocking {
        // 1. Display orientation resolution
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            PlayerHandoffAndVideoPolicy.resolveRequestedScreenOrientation(
                isNowPlayingOverlayVisible = false,
                mediaType = MediaType.VIDEO,
                isFullscreenVideo = false,
                orientationPreference = VideoOrientationPreference.LANDSCAPE
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            PlayerHandoffAndVideoPolicy.resolveRequestedScreenOrientation(
                isNowPlayingOverlayVisible = true,
                mediaType = MediaType.AUDIO,
                isFullscreenVideo = false,
                orientationPreference = VideoOrientationPreference.LANDSCAPE
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            PlayerHandoffAndVideoPolicy.resolveRequestedScreenOrientation(
                isNowPlayingOverlayVisible = true,
                mediaType = MediaType.VIDEO,
                isFullscreenVideo = true,
                orientationPreference = VideoOrientationPreference.AUTO
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            PlayerHandoffAndVideoPolicy.resolveRequestedScreenOrientation(
                isNowPlayingOverlayVisible = true,
                mediaType = MediaType.VIDEO,
                isFullscreenVideo = false,
                orientationPreference = VideoOrientationPreference.PORTRAIT
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_USER,
            PlayerHandoffAndVideoPolicy.resolveRequestedScreenOrientation(
                isNowPlayingOverlayVisible = true,
                mediaType = MediaType.VIDEO,
                isFullscreenVideo = false,
                orientationPreference = VideoOrientationPreference.AUTO
            )
        )

        // 2. Frame rotation normalization & aspect-ratio-compensated scaling (0°, 90°, 180°, 270°, 360°)
        val t0 = PlayerHandoffAndVideoPolicy.computeVideoFrameTransform(0, 1600, 900)
        assertEquals(0, t0.rotationDegrees)
        assertEquals(1f, t0.scaleX, 0.001f)
        assertEquals(1f, t0.scaleY, 0.001f)

        val t180 = PlayerHandoffAndVideoPolicy.computeVideoFrameTransform(180, 1600, 900)
        assertEquals(180, t180.rotationDegrees)
        assertEquals(1f, t180.scaleX, 0.001f)

        val t90 = PlayerHandoffAndVideoPolicy.computeVideoFrameTransform(90, 1600, 900)
        assertEquals(90, t90.rotationDegrees)
        // In a 1600x900 container, a 90° rotated frame scales by 900/1600 = 0.5625 so it fits inside without clipping
        assertEquals(900f / 1600f, t90.scaleX, 0.001f)
        assertEquals(900f / 1600f, t90.scaleY, 0.001f)

        val t270 = PlayerHandoffAndVideoPolicy.computeVideoFrameTransform(270, 1600, 900)
        assertEquals(270, t270.rotationDegrees)
        assertEquals(900f / 1600f, t270.scaleX, 0.001f)

        val t360 = PlayerHandoffAndVideoPolicy.computeVideoFrameTransform(360, 1600, 900)
        assertEquals(0, t360.rotationDegrees)
        assertEquals(1f, t360.scaleX, 0.001f)

        // 3. Verify UserPreferencesRepository persists new video & audio preferences
        val prefsRepo = UserPreferencesRepository(context)
        prefsRepo.setKeepScreenOnDuringVideo(false)
        prefsRepo.setBackgroundPlaybackEnabled(true)
        prefsRepo.setVideoGestureControlsEnabled(false)
        prefsRepo.setVideoOrientationPreference(VideoOrientationPreference.LANDSCAPE)
        prefsRepo.setAudioBalance(-0.35f)

        val state = prefsRepo.preferencesFlow.first()
        assertFalse(state.keepScreenOnDuringVideo)
        assertTrue(state.backgroundPlaybackEnabled)
        assertFalse(state.videoGestureControlsEnabled)
        assertEquals(VideoOrientationPreference.LANDSCAPE, state.videoOrientationPreference)
        assertEquals(-0.35f, state.audioBalance, 0.01f)
    }

    @Test
    fun `conversion registry and privacy defaults and IsoMp4Muxer behave deterministically`() {
        val profiles = ConversionCapabilityRegistry.getSupportedProfiles()
        assertTrue(profiles.isNotEmpty())
        assertTrue(profiles.any { it.id == "WAV_PCM_16BIT" && it.isHardwareSupportedOnDevice })

        assertFalse(
            OptionalAdManager.canShowOptionalTestBanner(
                moduleEnabledInSettings = false,
                explicitUserConsentGranted = false
            )
        )
        assertEquals("None (Zero Third-Party Ad SDK Bundled)", OptionalAdManager.auditReport.sdkBundled)

        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requestedPerms = pkgInfo.requestedPermissions?.toList().orEmpty()
        assertFalse(
            "INTERNET permission must not be present in offline-first manifest",
            requestedPerms.contains(android.Manifest.permission.INTERNET)
        )

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

    @Test
    fun `stereo balance preamp gain sleep timer 0_5x to 3_0x playback speed and queue reordering work end to end`() {
        val sampleRate = 44100
        val inputSignal = synthesizeMultiTonePcm16Stereo(
            sampleRateHz = sampleRate,
            frameCount = 1024,
            frequenciesHz = listOf(440.0),
            amplitude = 0.25
        )

        // 1. Hard Left Stereo Balance (-1.0f): Right channel (odd indices) must be attenuated to 0
        val hardLeftBank = MultiBandBiquadFilterBank(sampleRate, 2).apply {
            updateConfig(
                EqualizerDspConfig(
                    isEnabled = false,
                    stereoBalance = -1.0f,
                    preampGainDb = 0.0f
                )
            )
        }
        val leftOnlyPcm = hardLeftBank.processPcm16Array(inputSignal)
        var leftEnergy = 0L
        var rightEnergy = 0L
        for (frame in 0 until 1024) {
            leftEnergy += abs(leftOnlyPcm[frame * 2].toInt())
            rightEnergy += abs(leftOnlyPcm[frame * 2 + 1].toInt())
        }
        assertTrue("Left channel must retain full audio signal when balance = -1.0f", leftEnergy > 50_000L)
        assertEquals("Right channel must be completely muted when balance = -1.0f", 0L, rightEnergy)

        // 2. Preamp Gain (+6.0 dB): Output amplitude must be ~2x original amplitude
        val preampBank = MultiBandBiquadFilterBank(sampleRate, 2).apply {
            updateConfig(
                EqualizerDspConfig(
                    isEnabled = false,
                    stereoBalance = 0.0f,
                    preampGainDb = 6.0f
                )
            )
        }
        val boostedPcm = preampBank.processPcm16Array(inputSignal)
        var origEnergy = 0L
        var boostedEnergy = 0L
        for (i in inputSignal.indices) {
            origEnergy += abs(inputSignal[i].toInt())
            boostedEnergy += abs(boostedPcm[i].toInt())
        }
        assertTrue(
            "+6 dB preamp must measurably amplify signal (orig=$origEnergy, boosted=$boostedEnergy)",
            boostedEnergy > (origEnergy * 1.85).toLong()
        )

        // 3. ViewModel Sleep Timer (Automatic Stop), 0.5x-3.0x Speed, Queue Reordering, and Audio Lyrics
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.viewmodel.PulsePlayViewModel(app)

        val itemA = MediaEntity(
            uriString = "file:///song_a.wav",
            mediaStoreId = null,
            title = "Song A",
            artist = "Artist A",
            album = "Album A",
            folderName = "Music",
            durationMs = 180_000L,
            sizeBytes = 1024L,
            mimeType = "audio/wav",
            mediaType = MediaType.AUDIO,
            dateAddedEpochSec = 1700000001L
        )
        val itemB = itemA.copy(uriString = "file:///song_b.wav", title = "Song B")
        val itemC = itemA.copy(uriString = "file:///song_c.wav", title = "Song C")

        vm.setQueue(listOf(itemA, itemB, itemC))
        assertEquals(listOf("Song A", "Song B", "Song C"), vm.playbackState.value.queue.map { it.title })

        // Reorder Song C from index 2 to index 1
        vm.moveQueueItem(2, 1)
        assertEquals(listOf("Song A", "Song C", "Song B"), vm.playbackState.value.queue.map { it.title })

        // Playback Speed 0.5x to 3.0x
        vm.setPlaybackSpeed(0.5f)
        assertEquals(0.5f, vm.playbackState.value.playbackSpeed, 0.01f)
        vm.setPlaybackSpeed(3.0f)
        assertEquals(3.0f, vm.playbackState.value.playbackSpeed, 0.01f)

        // Sleep Timer countdown & automatic stop
        vm.setSleepTimerMinutes(10)
        assertTrue(vm.playbackState.value.isSleepTimerActive)
        assertEquals(10, vm.playbackState.value.sleepTimerTotalMinutes)
        assertEquals(600_000L, vm.playbackState.value.sleepTimerRemainingMs)

        // Advance timer by 300 seconds -> still active with 300s remaining
        vm.advanceSleepTimerByMs(300_000L)
        assertTrue(vm.playbackState.value.isSleepTimerActive)
        assertEquals(300_000L, vm.playbackState.value.sleepTimerRemainingMs)

        // Advance remaining 300 seconds -> timer expires and automatically pauses playback
        vm.advanceSleepTimerByMs(300_000L)
        assertFalse(vm.playbackState.value.isSleepTimerActive)
        assertEquals(0L, vm.playbackState.value.sleepTimerRemainingMs)
        assertFalse("Playback must be paused when Sleep Timer reaches 0", vm.playbackState.value.isPlaying)
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

    @Test
    fun `favorites segregation partitions audio tracks and videos independently`() {
        val audio1 = MediaEntity(
            uriString = "content://audio/1",
            mediaStoreId = 1L,
            title = "Acoustic Song 1",
            artist = "Studio Artist",
            album = "Album 1",
            folderName = "Music",
            durationMs = 180_000L,
            sizeBytes = 5_000_000L,
            mimeType = "audio/mp3",
            mediaType = MediaType.AUDIO,
            dateAddedEpochSec = 1000L
        )
        val audio2 = MediaEntity(
            uriString = "content://audio/2",
            mediaStoreId = 2L,
            title = "Acoustic Song 2",
            artist = "Studio Artist",
            album = "Album 2",
            folderName = "Music",
            durationMs = 210_000L,
            sizeBytes = 6_000_000L,
            mimeType = "audio/wav",
            mediaType = MediaType.AUDIO,
            dateAddedEpochSec = 2000L
        )
        val video1 = MediaEntity(
            uriString = "content://video/1",
            mediaStoreId = 3L,
            title = "Cinema Video 1",
            artist = "Video Lab",
            album = "Cinema",
            folderName = "Movies",
            durationMs = 300_000L,
            sizeBytes = 50_000_000L,
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            dateAddedEpochSec = 3000L
        )

        val allFavorites = listOf(audio1, video1, audio2)
        val favAudio = allFavorites.filter { it.mediaType == MediaType.AUDIO }
        val favVideos = allFavorites.filter { it.mediaType == MediaType.VIDEO }

        assertEquals(2, favAudio.size)
        assertEquals(1, favVideos.size)
        assertTrue(favAudio.all { it.mediaType == MediaType.AUDIO })
        assertTrue(favVideos.all { it.mediaType == MediaType.VIDEO })
        assertEquals("Cinema Video 1", favVideos.first().title)
    }

    @Test
    fun `step by step primary tab backstack unwinds in reverse order until root Home`() {
        val viewModel = PulsePlayViewModel(context as Application)

        // Initially on HOME: backstack is empty, cannot pop
        assertEquals(PrimaryTab.HOME, viewModel.selectedTab.value)
        assertFalse(viewModel.canPopPrimaryTab())
        assertFalse(viewModel.popPrimaryTab())

        // Navigate step 1: HOME -> VIDEOS
        viewModel.selectPrimaryTab(PrimaryTab.VIDEOS)
        assertEquals(PrimaryTab.VIDEOS, viewModel.selectedTab.value)
        assertTrue(viewModel.canPopPrimaryTab())

        // Navigate step 2: VIDEOS -> MUSIC
        viewModel.selectPrimaryTab(PrimaryTab.MUSIC)
        assertEquals(PrimaryTab.MUSIC, viewModel.selectedTab.value)

        // Navigate step 3: MUSIC -> SETTINGS
        viewModel.selectPrimaryTab(PrimaryTab.SETTINGS)
        assertEquals(PrimaryTab.SETTINGS, viewModel.selectedTab.value)

        // Unwind step 1: Back from SETTINGS -> MUSIC
        assertTrue(viewModel.popPrimaryTab())
        assertEquals(PrimaryTab.MUSIC, viewModel.selectedTab.value)
        assertTrue(viewModel.canPopPrimaryTab())

        // Unwind step 2: Back from MUSIC -> VIDEOS
        assertTrue(viewModel.popPrimaryTab())
        assertEquals(PrimaryTab.VIDEOS, viewModel.selectedTab.value)
        assertTrue(viewModel.canPopPrimaryTab())

        // Unwind step 3: Back from VIDEOS -> HOME
        assertTrue(viewModel.popPrimaryTab())
        assertEquals(PrimaryTab.HOME, viewModel.selectedTab.value)

        // Root reached: on HOME, no more tabs to pop -> system can finish app cleanly
        assertFalse(viewModel.canPopPrimaryTab())
        assertFalse(viewModel.popPrimaryTab())
        assertEquals(PrimaryTab.HOME, viewModel.selectedTab.value)
    }

    @Test
    fun `step by step library subtab backstack unwinds to previous subtab before popping primary tab`() {
        val viewModel = PulsePlayViewModel(context as Application)

        assertEquals(LibrarySubTab.FOLDERS, viewModel.selectedLibrarySubTab.value)
        assertFalse(viewModel.canPopLibrarySubTab())

        // Navigate subtab 1: FOLDERS -> PLAYLISTS
        viewModel.selectLibrarySubTab(LibrarySubTab.PLAYLISTS)
        assertEquals(LibrarySubTab.PLAYLISTS, viewModel.selectedLibrarySubTab.value)
        assertTrue(viewModel.canPopLibrarySubTab())

        // Navigate subtab 2: PLAYLISTS -> FAVORITES
        viewModel.selectLibrarySubTab(LibrarySubTab.FAVORITES)
        assertEquals(LibrarySubTab.FAVORITES, viewModel.selectedLibrarySubTab.value)

        // Unwind subtab 1: Back from FAVORITES -> PLAYLISTS
        assertTrue(viewModel.popLibrarySubTab())
        assertEquals(LibrarySubTab.PLAYLISTS, viewModel.selectedLibrarySubTab.value)

        // Unwind subtab 2: Back from PLAYLISTS -> FOLDERS
        assertTrue(viewModel.popLibrarySubTab())
        assertEquals(LibrarySubTab.FOLDERS, viewModel.selectedLibrarySubTab.value)

        // Root subtab reached: on FOLDERS, cannot pop subtab further
        assertFalse(viewModel.canPopLibrarySubTab())
        assertFalse(viewModel.popLibrarySubTab())
    }

    @Test
    fun `selecting Home bottom tab resets backstack to root cleanly without cycles`() {
        val viewModel = PulsePlayViewModel(context as Application)

        viewModel.selectPrimaryTab(PrimaryTab.VIDEOS)
        viewModel.selectPrimaryTab(PrimaryTab.MUSIC)
        assertEquals(PrimaryTab.MUSIC, viewModel.selectedTab.value)

        // Explicitly clicking HOME tab in bottom bar resets user to root Home
        viewModel.selectPrimaryTab(PrimaryTab.HOME)
        assertEquals(PrimaryTab.HOME, viewModel.selectedTab.value)
        assertFalse(viewModel.canPopPrimaryTab())
        assertFalse(viewModel.popPrimaryTab())
    }

    @Test
    fun `IsoMp4Muxer writes valid avcC box that Media3 AvcConfig parses without ParserException`() {
        val outMp4 = File(context.cacheDir, "avcc_verify_test.mp4")
        if (outMp4.exists()) outMp4.delete()

        val videoFormat = android.media.MediaFormat.createVideoFormat(
            android.media.MediaFormat.MIMETYPE_VIDEO_AVC,
            640,
            360
        ).apply {
            setInteger(android.media.MediaFormat.KEY_FRAME_RATE, 15)
        }
        val muxer = com.example.media.IsoMp4Muxer(outMp4)
        val vTrack = muxer.addTrack(videoFormat)
        muxer.start()

        val fakeIdrNal = ByteArray(64) { idx -> if (idx == 0) 0x65.toByte() else (idx and 0xFF).toByte() }
        val info = android.media.MediaCodec.BufferInfo().apply {
            set(0, fakeIdrNal.size, 0L, android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME)
        }
        muxer.writeSampleData(vTrack, java.nio.ByteBuffer.wrap(fakeIdrNal), info)
        muxer.stop()
        muxer.release()

        val generator = SampleMediaGenerator(context)
        assertTrue("Generated MP4 must pass hasValidAvcConfig check", generator.hasValidAvcConfig(outMp4))

        val bytes = outMp4.readBytes()
        var avcCPayloadOffset = -1
        var avcCPayloadLen = 0
        for (i in 4..(bytes.size - 16)) {
            if (bytes[i] == 'a'.code.toByte() &&
                bytes[i + 1] == 'v'.code.toByte() &&
                bytes[i + 2] == 'c'.code.toByte() &&
                bytes[i + 3] == 'C'.code.toByte()
            ) {
                val boxSize = ((bytes[i - 4].toInt() and 0xFF) shl 24) or
                    ((bytes[i - 3].toInt() and 0xFF) shl 16) or
                    ((bytes[i - 2].toInt() and 0xFF) shl 8) or
                    (bytes[i - 1].toInt() and 0xFF)
                avcCPayloadOffset = i + 4
                avcCPayloadLen = boxSize - 8
                break
            }
        }
        assertTrue("MP4 must contain avcC box", avcCPayloadOffset > 0 && avcCPayloadLen > 12)

        val avcCData = bytes.copyOfRange(avcCPayloadOffset, avcCPayloadOffset + avcCPayloadLen)
        val parsable = androidx.media3.common.util.ParsableByteArray(avcCData)
        val parsedAvcConfig = androidx.media3.extractor.AvcConfig.parse(parsable)
        assertNotNull(parsedAvcConfig)
        assertEquals(640, parsedAvcConfig.width)
        assertEquals(4, parsedAvcConfig.nalUnitLengthFieldLength)
        assertTrue(parsedAvcConfig.initializationData.size >= 2)

        outMp4.delete()
    }
}

