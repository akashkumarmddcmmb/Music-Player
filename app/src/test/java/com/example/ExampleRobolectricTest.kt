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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.example.data.preferences.SortOption
import com.example.data.preferences.UserPreferencesState
import com.example.ui.components.MediaItemCard
import com.example.ui.screens.NowPlayingOverlayScreen
import com.example.ui.screens.NowPlayingSidePanel
import com.example.ui.screens.SearchAndActionHeader
import com.example.ui.theme.PulsePlayTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
        assertTrue(
            "INTERNET permission must be present for user-initiated GitHub Releases update checks",
            requestedPerms.contains(android.Manifest.permission.INTERNET)
        )
        assertTrue(
            "REQUEST_INSTALL_PACKAGES permission must be present for user-initiated APK update installation",
            requestedPerms.contains(android.Manifest.permission.REQUEST_INSTALL_PACKAGES)
        )
        assertFalse(
            "Location permissions must never be requested",
            requestedPerms.contains(android.Manifest.permission.ACCESS_FINE_LOCATION)
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

    @Test
    fun resilient4KCodecEngine_verifies1080pAnd4KCapabilitiesAcrossCodecsFrameRatesAndBitDepths() {
        val mimes = listOf(
            android.media.MediaFormat.MIMETYPE_VIDEO_AVC,
            android.media.MediaFormat.MIMETYPE_VIDEO_HEVC,
            android.media.MediaFormat.MIMETYPE_VIDEO_VP9,
            "video/av01"
        )

        for (mime in mimes) {
            // 1. Hardware decoder capable of 4K 60fps
            val hw4kCodec = androidx.media3.exoplayer.mediacodec.MediaCodecInfo.newInstance(
                "c2.qti.${mime.substringAfter('/')}.decoder.4k",
                mime,
                mime,
                null,
                /* hardwareAccelerated = */ true,
                /* softwareOnly = */ false,
                /* vendor = */ true,
                /* forceDisableAdaptive = */ false,
                /* forceSecure = */ false
            )
            // 2. Hardware decoder capped at 1080p 30fps (even though its name contains the codec token)
            val hw1080pOnlyCodec = androidx.media3.exoplayer.mediacodec.MediaCodecInfo.newInstance(
                "c2.vendor.${mime.substringAfter('/')}.decoder.1080p.30fps.8bit_only",
                mime,
                mime,
                null,
                /* hardwareAccelerated = */ true,
                /* softwareOnly = */ false,
                /* vendor = */ true,
                /* forceDisableAdaptive = */ false,
                /* forceSecure = */ false
            )
            // 3. Mismatched MIME decoder whose name deceptively contains "hevc.vp9.av1.avc"
            val wrongMimeCodec = androidx.media3.exoplayer.mediacodec.MediaCodecInfo.newInstance(
                "c2.deceptive.hevc.vp9.av1.avc.decoder",
                "video/3gpp",
                "video/3gpp",
                null,
                /* hardwareAccelerated = */ true,
                /* softwareOnly = */ false,
                /* vendor = */ true,
                /* forceDisableAdaptive = */ false,
                /* forceSecure = */ false
            )

            // Test 1080p 30fps 8-bit
            val format1080p30 = androidx.media3.common.Format.Builder()
                .setSampleMimeType(mime)
                .setWidth(1920)
                .setHeight(1080)
                .setFrameRate(30f)
                .build()

            val report1080pOn4k = com.example.media.Resilient4KCodecEngine.evaluateCodecCapability(hw4kCodec, format1080p30)
            val report1080pOnFhd = com.example.media.Resilient4KCodecEngine.evaluateCodecCapability(hw1080pOnlyCodec, format1080p30)
            val reportDeceptive = com.example.media.Resilient4KCodecEngine.evaluateCodecCapability(wrongMimeCodec, format1080p30)

            assertTrue("4K HW codec must support 1080p30 for $mime", report1080pOn4k.isFullyCompatible)
            assertTrue("1080p30 HW codec must support 1080p30 8-bit for $mime", report1080pOnFhd.isFullyCompatible)
            assertFalse("Codec with wrong MIME must be rejected even if name contains codec keywords", reportDeceptive.isFullyCompatible)

            // Test 4K (3840x2160) 60fps 8-bit
            val format4k60 = androidx.media3.common.Format.Builder()
                .setSampleMimeType(mime)
                .setWidth(3840)
                .setHeight(2160)
                .setFrameRate(60f)
                .build()

            val report4k60On4k = com.example.media.Resilient4KCodecEngine.evaluateCodecCapability(hw4kCodec, format4k60)
            val report4k60OnFhd = com.example.media.Resilient4KCodecEngine.evaluateCodecCapability(hw1080pOnlyCodec, format4k60)

            assertTrue("4K 60fps HW codec must support 3840x2160@60 for $mime", report4k60On4k.isFullyCompatible)
            assertFalse("1080p-capped codec must reject 3840x2160 for $mime", report4k60OnFhd.dimensionsSupported)
            assertFalse("1080p-capped codec must not be marked compatible for 4K", report4k60OnFhd.isCompatibleWithOperatingRateAdjustment)

            // Test 4K (3840x2160) 30fps 10-bit HDR
            val hdrColorInfo = androidx.media3.common.ColorInfo.Builder()
                .setColorSpace(androidx.media3.common.C.COLOR_SPACE_BT2020)
                .setColorTransfer(androidx.media3.common.C.COLOR_TRANSFER_ST2084)
                .setLumaBitdepth(10)
                .setChromaBitdepth(10)
                .build()
            val format4k10Bit = androidx.media3.common.Format.Builder()
                .setSampleMimeType(mime)
                .setWidth(3840)
                .setHeight(2160)
                .setFrameRate(30f)
                .setColorInfo(hdrColorInfo)
                .build()

            assertEquals(10, com.example.media.Resilient4KCodecEngine.extractBitDepth(format4k10Bit))
            val report10BitOn8BitOnly = com.example.media.Resilient4KCodecEngine.evaluateCodecCapability(
                hw1080pOnlyCodec,
                format4k10Bit.buildUpon().setWidth(1920).setHeight(1080).build()
            )
            assertFalse("8-bit-only decoder must reject 10-bit HDR stream for $mime", report10BitOn8BitOnly.profileAndBitDepthSupported)
        }
    }

    @Test
    fun resilient4KCodecEngine_preservesMediaFormatKeysUnlessCapabilityValidated() {
        val format4k30 = androidx.media3.common.Format.Builder()
            .setSampleMimeType(android.media.MediaFormat.MIMETYPE_VIDEO_HEVC)
            .setWidth(3840)
            .setHeight(2160)
            .setFrameRate(30f)
            .build()

        val mediaFormatWithinCaps = android.media.MediaFormat.createVideoFormat(
            android.media.MediaFormat.MIMETYPE_VIDEO_HEVC,
            3840,
            2160
        ).apply {
            setInteger(android.media.MediaFormat.KEY_MAX_WIDTH, 3840)
            setInteger(android.media.MediaFormat.KEY_MAX_HEIGHT, 2160)
            setFloat(android.media.MediaFormat.KEY_OPERATING_RATE, 30f)
        }

        val full4k30Report = com.example.media.CodecCapabilityReport(
            codecName = "c2.qti.hevc.decoder",
            codecMimeType = android.media.MediaFormat.MIMETYPE_VIDEO_HEVC,
            isHardwareAccelerated = true,
            isSoftwareOnly = false,
            mimeSupported = true,
            dimensionsSupported = true,
            frameRateSupported = true,
            profileAndBitDepthSupported = true,
            maxSupportedWidth = 3840,
            maxSupportedHeight = 2160,
            maxSupportedFrameRateAtSize = 30.0,
            diagnosticNote = "OK"
        )

        // When KEY_MAX_WIDTH, KEY_MAX_HEIGHT, and KEY_OPERATING_RATE are within capabilities, no adjustment is made
        val noAdjustment = com.example.media.Resilient4KCodecEngine.buildCapabilityValidatedAdjustedMediaFormat(
            source = mediaFormatWithinCaps,
            format = format4k30,
            report = full4k30Report
        )
        assertNull("MediaFormat within documented capabilities must not be modified", noAdjustment)

        // When ExoPlayer populates adaptive KEY_MAX_WIDTH=4096 or KEY_OPERATING_RATE=60f on a 3840x2160@30fps decoder,
        // only the exceeding values are clamped to documented limits and KEY_WIDTH/KEY_HEIGHT remain untouched
        val overHintedFormat = android.media.MediaFormat.createVideoFormat(
            android.media.MediaFormat.MIMETYPE_VIDEO_HEVC,
            3840,
            2160
        ).apply {
            setInteger(android.media.MediaFormat.KEY_MAX_WIDTH, 4096)
            setInteger(android.media.MediaFormat.KEY_MAX_HEIGHT, 2304)
            setFloat(android.media.MediaFormat.KEY_OPERATING_RATE, 60f)
        }

        val adjusted = com.example.media.Resilient4KCodecEngine.buildCapabilityValidatedAdjustedMediaFormat(
            source = overHintedFormat,
            format = format4k30,
            report = full4k30Report
        )
        assertNotNull("Over-hinted MediaFormat should receive capability-validated adjustment", adjusted)
        assertEquals(3840, adjusted!!.getInteger(android.media.MediaFormat.KEY_WIDTH))
        assertEquals(2160, adjusted.getInteger(android.media.MediaFormat.KEY_HEIGHT))
        assertEquals(3840, adjusted.getInteger(android.media.MediaFormat.KEY_MAX_WIDTH))
        assertEquals(2160, adjusted.getInteger(android.media.MediaFormat.KEY_MAX_HEIGHT))
        assertEquals(30f, adjusted.getFloat(android.media.MediaFormat.KEY_OPERATING_RATE), 0.01f)
    }

    private fun createStubMediaCodecAdapter(): androidx.media3.exoplayer.mediacodec.MediaCodecAdapter {
        return java.lang.reflect.Proxy.newProxyInstance(
            androidx.media3.exoplayer.mediacodec.MediaCodecAdapter::class.java.classLoader,
            arrayOf(androidx.media3.exoplayer.mediacodec.MediaCodecAdapter::class.java)
        ) { _, method, _ ->
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        } as androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
    }

    @Test
    fun resilient4KMediaCodecAdapterFactory_prefersHardwareAndFallsBackToSecondaryHardwareOnFailure() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.media.Resilient4KCodecEngine.allowExperimental4kSoftwareFallback = false
        com.example.media.Resilient4KCodecEngine.resetTelemetryForNewTrack(
            mimeType = android.media.MediaFormat.MIMETYPE_VIDEO_HEVC,
            width = 3840,
            height = 2160
        )

        val primaryHw = androidx.media3.exoplayer.mediacodec.MediaCodecInfo.newInstance(
            "c2.primary.hevc.decoder.4k",
            android.media.MediaFormat.MIMETYPE_VIDEO_HEVC,
            android.media.MediaFormat.MIMETYPE_VIDEO_HEVC,
            null,
            /* hardwareAccelerated = */ true,
            /* softwareOnly = */ false,
            /* vendor = */ true,
            /* forceDisableAdaptive = */ false,
            /* forceSecure = */ false
        )
        val secondaryHw = androidx.media3.exoplayer.mediacodec.MediaCodecInfo.newInstance(
            "c2.secondary.hevc.decoder.4k",
            android.media.MediaFormat.MIMETYPE_VIDEO_HEVC,
            android.media.MediaFormat.MIMETYPE_VIDEO_HEVC,
            null,
            /* hardwareAccelerated = */ true,
            /* softwareOnly = */ false,
            /* vendor = */ true,
            /* forceDisableAdaptive = */ false,
            /* forceSecure = */ false
        )
        val softwareCodec = androidx.media3.exoplayer.mediacodec.MediaCodecInfo.newInstance(
            "c2.android.hevc.decoder",
            android.media.MediaFormat.MIMETYPE_VIDEO_HEVC,
            android.media.MediaFormat.MIMETYPE_VIDEO_HEVC,
            null,
            /* hardwareAccelerated = */ false,
            /* softwareOnly = */ true,
            /* vendor = */ false,
            /* forceDisableAdaptive = */ false,
            /* forceSecure = */ false
        )

        val attemptedCodecs = mutableListOf<String>()
        val fakeAdapter = createStubMediaCodecAdapter()
        val delegate = androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Factory { config ->
            attemptedCodecs.add(config.codecInfo.name)
            if (config.codecInfo.name == primaryHw.name) {
                throw java.io.IOException("Simulated primary HW codec resource busy")
            }
            fakeAdapter
        }

        val factory = com.example.media.Resilient4KMediaCodecAdapterFactory(
            context = context,
            delegateFactory = delegate,
            candidateDecodersProvider = { _, _ -> listOf(primaryHw, secondaryHw, softwareCodec) }
        )

        val format4k = androidx.media3.common.Format.Builder()
            .setSampleMimeType(android.media.MediaFormat.MIMETYPE_VIDEO_HEVC)
            .setWidth(3840)
            .setHeight(2160)
            .setFrameRate(30f)
            .build()
        val mediaFormat4k = android.media.MediaFormat.createVideoFormat(
            android.media.MediaFormat.MIMETYPE_VIDEO_HEVC,
            3840,
            2160
        )
        val config = androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Configuration.createForVideoDecoding(
            primaryHw,
            mediaFormat4k,
            format4k,
            null,
            null
        )

        val resultingAdapter = factory.createAdapter(config)
        assertNotNull(resultingAdapter)
        assertEquals(listOf(primaryHw.name, secondaryHw.name), attemptedCodecs)

        val telemetry = com.example.media.Resilient4KCodecEngine.latestTelemetry.value
        assertEquals(secondaryHw.name, telemetry.selectedDecoderName)
        assertTrue(telemetry.isHardwareAccelerated)
        assertFalse(telemetry.isSoftwareFallbackActive)
        assertTrue(telemetry.isDecoderVerified)
        assertEquals(1, telemetry.fallbackAttemptCount)
    }

    @Test
    fun resilient4KMediaCodecAdapterFactory_guardsOptional4KSoftwareFallbackByCpuThermalBatteryAndDroppedFrames() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Default off: must reject 4K software fallback
        val guardDefaultOff = com.example.media.Resilient4KCodecEngine.evaluate4KSoftwareFallbackGuard(
            context = context,
            userEnabledSoftware4KFallback = false,
            cpuCoresOverride = 8,
            thermalStatusOverride = android.os.PowerManager.THERMAL_STATUS_NONE,
            lowMemoryOverride = false,
            powerSaveOverride = false
        )
        assertFalse(guardDefaultOff.canAttemptSoftware4KFallback)

        // 2. Insufficient CPU cores (< 6): must reject 4K software fallback
        val guardLowCores = com.example.media.Resilient4KCodecEngine.evaluate4KSoftwareFallbackGuard(
            context = context,
            userEnabledSoftware4KFallback = true,
            cpuCoresOverride = 4,
            thermalStatusOverride = android.os.PowerManager.THERMAL_STATUS_NONE,
            lowMemoryOverride = false,
            powerSaveOverride = false
        )
        assertFalse(guardLowCores.canAttemptSoftware4KFallback)

        // 3. Elevated thermal status (MODERATE or higher): must reject 4K software fallback
        val guardThermal = com.example.media.Resilient4KCodecEngine.evaluate4KSoftwareFallbackGuard(
            context = context,
            userEnabledSoftware4KFallback = true,
            cpuCoresOverride = 8,
            thermalStatusOverride = android.os.PowerManager.THERMAL_STATUS_MODERATE,
            lowMemoryOverride = false,
            powerSaveOverride = false
        )
        assertFalse(guardThermal.canAttemptSoftware4KFallback)

        // 4. Battery Saver active: must reject 4K software fallback
        val guardBatterySaver = com.example.media.Resilient4KCodecEngine.evaluate4KSoftwareFallbackGuard(
            context = context,
            userEnabledSoftware4KFallback = true,
            cpuCoresOverride = 8,
            thermalStatusOverride = android.os.PowerManager.THERMAL_STATUS_NONE,
            lowMemoryOverride = false,
            powerSaveOverride = true
        )
        assertFalse(guardBatterySaver.canAttemptSoftware4KFallback)

        // 5. Excessive dropped frames: must reject 4K software fallback
        val guardDroppedFrames = com.example.media.Resilient4KCodecEngine.evaluate4KSoftwareFallbackGuard(
            context = context,
            userEnabledSoftware4KFallback = true,
            droppedFrameCount = com.example.media.Resilient4KCodecEngine.MAX_ACCEPTABLE_4K_DROPPED_FRAMES,
            cpuCoresOverride = 8,
            thermalStatusOverride = android.os.PowerManager.THERMAL_STATUS_NONE,
            lowMemoryOverride = false,
            powerSaveOverride = false
        )
        assertFalse(guardDroppedFrames.canAttemptSoftware4KFallback)

        // 6. User opted in AND healthy CPU/thermal/memory/battery: allowed to attempt 4K software fallback
        val guardHealthy = com.example.media.Resilient4KCodecEngine.evaluate4KSoftwareFallbackGuard(
            context = context,
            userEnabledSoftware4KFallback = true,
            droppedFrameCount = 0,
            cpuCoresOverride = 8,
            thermalStatusOverride = android.os.PowerManager.THERMAL_STATUS_NONE,
            lowMemoryOverride = false,
            powerSaveOverride = false
        )
        assertTrue(guardHealthy.canAttemptSoftware4KFallback)

        // Verify factory behavior when HW fails and guard blocks vs allows SW fallback
        val hwCodec = androidx.media3.exoplayer.mediacodec.MediaCodecInfo.newInstance(
            "c2.qti.vp9.decoder",
            android.media.MediaFormat.MIMETYPE_VIDEO_VP9,
            android.media.MediaFormat.MIMETYPE_VIDEO_VP9,
            null,
            /* hardwareAccelerated = */ true,
            /* softwareOnly = */ false,
            /* vendor = */ true,
            /* forceDisableAdaptive = */ false,
            /* forceSecure = */ false
        )
        val swCodec = androidx.media3.exoplayer.mediacodec.MediaCodecInfo.newInstance(
            "c2.android.vp9.decoder",
            android.media.MediaFormat.MIMETYPE_VIDEO_VP9,
            android.media.MediaFormat.MIMETYPE_VIDEO_VP9,
            null,
            /* hardwareAccelerated = */ false,
            /* softwareOnly = */ true,
            /* vendor = */ false,
            /* forceDisableAdaptive = */ false,
            /* forceSecure = */ false
        )
        val fakeAdapter = createStubMediaCodecAdapter()
        val hwFailingDelegate = androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Factory { config ->
            if (config.codecInfo.name == hwCodec.name) {
                throw java.io.IOException("HW VP9 4K init failed")
            }
            fakeAdapter
        }

        val format4kVp9 = androidx.media3.common.Format.Builder()
            .setSampleMimeType(android.media.MediaFormat.MIMETYPE_VIDEO_VP9)
            .setWidth(3840)
            .setHeight(2160)
            .setFrameRate(30f)
            .build()
        val mediaFormat4kVp9 = android.media.MediaFormat.createVideoFormat(
            android.media.MediaFormat.MIMETYPE_VIDEO_VP9,
            3840,
            2160
        )
        val config = androidx.media3.exoplayer.mediacodec.MediaCodecAdapter.Configuration.createForVideoDecoding(
            hwCodec,
            mediaFormat4kVp9,
            format4kVp9,
            null,
            null
        )

        // When guard blocks SW fallback, factory throws IOException and records failure telemetry
        val blockedFactory = com.example.media.Resilient4KMediaCodecAdapterFactory(
            context = context,
            delegateFactory = hwFailingDelegate,
            candidateDecodersProvider = { _, _ -> listOf(hwCodec, swCodec) },
            fallbackGuardProvider = { guardDefaultOff }
        )
        var threwWhenBlocked = false
        try {
            blockedFactory.createAdapter(config)
        } catch (_: java.io.IOException) {
            threwWhenBlocked = true
        }
        assertTrue("4K software fallback must be blocked when guard rejects", threwWhenBlocked)
        assertFalse(com.example.media.Resilient4KCodecEngine.latestTelemetry.value.isDecoderVerified)
        assertNotNull(com.example.media.Resilient4KCodecEngine.latestTelemetry.value.failureReason)

        // When guard allows SW fallback, factory initializes compatible SW decoder and records SW fallback telemetry
        val allowedFactory = com.example.media.Resilient4KMediaCodecAdapterFactory(
            context = context,
            delegateFactory = hwFailingDelegate,
            candidateDecodersProvider = { _, _ -> listOf(hwCodec, swCodec) },
            fallbackGuardProvider = { guardHealthy }
        )
        val swAdapter = allowedFactory.createAdapter(config)
        assertNotNull(swAdapter)
        val swTelemetry = com.example.media.Resilient4KCodecEngine.latestTelemetry.value
        assertEquals(swCodec.name, swTelemetry.selectedDecoderName)
        assertFalse(swTelemetry.isHardwareAccelerated)
        assertTrue(swTelemetry.isSoftwareFallbackActive)
        assertTrue(swTelemetry.isDecoderVerified)

        // Simulate excessive dropped frames during SW fallback and verify degradation status
        com.example.media.Resilient4KCodecEngine.recordDroppedFrames(50)
        val degradedTelemetry = com.example.media.Resilient4KCodecEngine.latestTelemetry.value
        assertEquals(50, degradedTelemetry.droppedVideoFrames)
        assertTrue(degradedTelemetry.diagnosticSummary.contains("Degraded"))
    }

    @Test
    fun resilient4KResolutionBadge_requiresActual3840x2160MetadataAndSeparatesDecoderStatus() {
        assertNull(
            "Unknown dimensions (0x0) must never fabricate a resolution badge",
            com.example.media.Resilient4KCodecEngine.formatVerifiedResolutionBadge(0, 0)
        )
        assertFalse(com.example.media.Resilient4KCodecEngine.is4kUhdResolution(0, 0))
        assertFalse(com.example.media.Resilient4KCodecEngine.is4kUhdResolution(1920, 1080))
        assertEquals(
            "FHD 1080p",
            com.example.media.Resilient4KCodecEngine.formatVerifiedResolutionBadge(1920, 1080)
        )
        assertTrue(com.example.media.Resilient4KCodecEngine.is4kUhdResolution(3840, 2160))
        assertTrue(com.example.media.Resilient4KCodecEngine.is4kUhdResolution(2160, 3840))
        assertEquals(
            "4K UHD 2160p",
            com.example.media.Resilient4KCodecEngine.formatVerifiedResolutionBadge(3840, 2160)
        )
    }

    @Test
    fun safMediaClassification_classifiesVideoAudioAndRejectsUnsupportedOrEmptyFiles() = runBlocking<Unit> {
        val repo = com.example.data.repository.MediaLibraryRepository(
            context = context,
            dao = database.pulseDao(),
            sampleGenerator = SampleMediaGenerator(context)
        )

        // 1. Explicit video/* MIME from ContentResolver
        val explicitVideo = repo.classifyMediaTypeAndMime(
            resolverMime = "video/mp4",
            displayName = "clip",
            uriString = "content://media/external/video/123",
            retrieverMime = null,
            hasVideoTrackMetadata = false,
            hasAudioTrackMetadata = false,
            videoWidth = 0,
            videoHeight = 0
        )
        assertNotNull(explicitVideo)
        assertEquals(MediaType.VIDEO, explicitVideo!!.first)
        assertEquals("video/mp4", explicitVideo.second)

        // 2. Null ContentResolver MIME with .mp4 / .mkv / .webm extension -> VIDEO (never defaults to audio/*)
        val nullMimeMp4 = repo.classifyMediaTypeAndMime(
            resolverMime = null,
            displayName = "cinema_4k_sample.mp4",
            uriString = "file:///storage/emulated/0/Movies/cinema_4k_sample.mp4",
            retrieverMime = null,
            hasVideoTrackMetadata = false,
            hasAudioTrackMetadata = false,
            videoWidth = 0,
            videoHeight = 0
        )
        assertNotNull(nullMimeMp4)
        assertEquals(MediaType.VIDEO, nullMimeMp4!!.first)
        assertEquals("video/mp4", nullMimeMp4.second)

        // 3. Generic application/octet-stream MIME with .mkv extension -> VIDEO
        val octetStreamMkv = repo.classifyMediaTypeAndMime(
            resolverMime = "application/octet-stream",
            displayName = "movie_clip.mkv",
            uriString = "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fmovie_clip.mkv",
            retrieverMime = null,
            hasVideoTrackMetadata = false,
            hasAudioTrackMetadata = false,
            videoWidth = 0,
            videoHeight = 0
        )
        assertNotNull(octetStreamMkv)
        assertEquals(MediaType.VIDEO, octetStreamMkv!!.first)
        assertEquals("video/x-matroska", octetStreamMkv.second)

        // 4. Opaque SAF URI without extension, classified via MediaMetadataRetriever video track/dimensions
        val opaqueVideoRetriever = repo.classifyMediaTypeAndMime(
            resolverMime = "application/octet-stream",
            displayName = "content_100004921",
            uriString = "content://media/picker/0/com.android.providers.media.photopicker/media/100004921",
            retrieverMime = "video/hevc",
            hasVideoTrackMetadata = true,
            hasAudioTrackMetadata = true,
            videoWidth = 3840,
            videoHeight = 2160
        )
        assertNotNull(opaqueVideoRetriever)
        assertEquals(MediaType.VIDEO, opaqueVideoRetriever!!.first)
        assertEquals("video/hevc", opaqueVideoRetriever.second)

        // 5. Unsupported file (.pdf) with no audio/video tracks -> null
        val unsupportedPdf = repo.classifyMediaTypeAndMime(
            resolverMime = "application/pdf",
            displayName = "manual.pdf",
            uriString = "content://downloads/document/manual.pdf",
            retrieverMime = null,
            hasVideoTrackMetadata = false,
            hasAudioTrackMetadata = false,
            videoWidth = 0,
            videoHeight = 0
        )
        assertNull("Unsupported PDF must not be classified as audio or video", unsupportedPdf)

        // 6. Test importSafMediaUriDetailed on a real generated MP4 file (file:// URI has null ContentResolver MIME)
        val realVideoFile = File(context.cacheDir, "saf_test_reel.mp4")
        writeMinimalTestMp4File(realVideoFile)
        assertTrue(realVideoFile.exists() && realVideoFile.length() > 64L)

        val videoImportResult = repo.importSafMediaUriDetailed(Uri.fromFile(realVideoFile))
        assertTrue(
            "Valid MP4 file must succeed SAF import",
            videoImportResult is com.example.data.repository.MediaLibraryRepository.SafImportResult.Success
        )
        val importedVideo = (videoImportResult as com.example.data.repository.MediaLibraryRepository.SafImportResult.Success).entity
        assertEquals("MP4 file imported with null ContentResolver MIME must be classified as VIDEO", MediaType.VIDEO, importedVideo.mediaType)
        assertEquals("video/mp4", importedVideo.mimeType)

        // 7. Test importSafMediaUriDetailed on an empty 0-byte file -> CorruptedOrEmpty
        val emptyFile = File(context.cacheDir, "empty_broken.mp4")
        emptyFile.writeBytes(ByteArray(0))
        val emptyImportResult = repo.importSafMediaUriDetailed(Uri.fromFile(emptyFile))
        assertTrue(
            "0-byte file must be rejected as CorruptedOrEmpty",
            emptyImportResult is com.example.data.repository.MediaLibraryRepository.SafImportResult.CorruptedOrEmpty
        )

        // 8. Test importSafMediaUriDetailed on an unsupported text/pdf file -> UnsupportedFormat
        val fakePdfFile = File(context.cacheDir, "not_media.pdf")
        fakePdfFile.writeText("%PDF-1.7 fake document content that is over 32 bytes long for testing")
        val pdfImportResult = repo.importSafMediaUriDetailed(Uri.fromFile(fakePdfFile))
        assertTrue(
            "Non-media file must be rejected as UnsupportedFormat",
            pdfImportResult is com.example.data.repository.MediaLibraryRepository.SafImportResult.UnsupportedFormat
        )

        realVideoFile.delete()
        emptyFile.delete()
        fakePdfFile.delete()
    }

    @Test
    fun midPlaybackRotationAndSurfacePolicy_switchesSurfaceViewAndTextureViewAndCyclesRotation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PulsePlayViewModel(app)

        // Initial rotation is 0° (uses SurfaceView)
        assertEquals(0, vm.playbackState.value.videoRotationDegrees)
        assertEquals(0, PlayerHandoffAndVideoPolicy.computeVideoFrameTransform(vm.playbackState.value.videoRotationDegrees, 1920, 1080).rotationDegrees)

        // Rotate 0° -> 90° (switches to TextureView via R.layout.pulse_texture_player_view)
        vm.rotateVideoView90Degrees()
        assertEquals(90, vm.playbackState.value.videoRotationDegrees)
        val transform90 = PlayerHandoffAndVideoPolicy.computeVideoFrameTransform(vm.playbackState.value.videoRotationDegrees, 1920, 1080)
        assertEquals(90, transform90.rotationDegrees)
        assertEquals(1080f / 1920f, transform90.scaleX, 0.001f)

        // Rotate 90° -> 180° (uses TextureView)
        vm.rotateVideoView90Degrees()
        assertEquals(180, vm.playbackState.value.videoRotationDegrees)
        assertEquals(180, PlayerHandoffAndVideoPolicy.computeVideoFrameTransform(vm.playbackState.value.videoRotationDegrees, 1920, 1080).rotationDegrees)

        // Rotate 180° -> 270° (uses TextureView)
        vm.rotateVideoView90Degrees()
        assertEquals(270, vm.playbackState.value.videoRotationDegrees)
        assertEquals(270, PlayerHandoffAndVideoPolicy.computeVideoFrameTransform(vm.playbackState.value.videoRotationDegrees, 1920, 1080).rotationDegrees)

        // Rotate 270° -> 0° (returns to SurfaceView)
        vm.rotateVideoView90Degrees()
        assertEquals(0, vm.playbackState.value.videoRotationDegrees)
        assertEquals(0, PlayerHandoffAndVideoPolicy.computeVideoFrameTransform(vm.playbackState.value.videoRotationDegrees, 1920, 1080).rotationDegrees)

        // Verify TextureView PlayerView XML inflation and rotation transform application
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val playerView = android.view.LayoutInflater.from(activity)
            .inflate(R.layout.pulse_texture_player_view, null, false) as androidx.media3.ui.PlayerView
        assertTrue(playerView.videoSurfaceView is android.view.TextureView)
        playerView.videoSurfaceView!!.rotation = transform90.rotationDegrees.toFloat()
        assertEquals(90f, playerView.videoSurfaceView!!.rotation, 0.01f)
    }

    @Test
    fun decoderRecoveryAndErrorHandling_neverSetsIsPlayingTruePrematurelyAndBoundsRetries() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PulsePlayViewModel(app)

        val videoFile = File(context.cacheDir, "recovery_test_video.mp4")
        writeMinimalTestMp4File(videoFile)
        val videoEntity = MediaEntity(
            uriString = Uri.fromFile(videoFile).toString(),
            mediaStoreId = null,
            title = "4K Recovery Test Video",
            artist = "PulsePlay QA",
            album = "Video Cinema",
            folderName = "Movies",
            durationMs = 6000L,
            sizeBytes = videoFile.length(),
            mimeType = "video/hevc",
            mediaType = MediaType.VIDEO,
            width = 3840,
            height = 2160,
            dateAddedEpochSec = 1700000500L
        )

        vm.setQueue(listOf(videoEntity), initialIndex = 0)

        val decoderInitError = androidx.media3.common.PlaybackException(
            "MediaCodecVideoRenderer error: decoder init failed",
            IllegalStateException("c2.qti.hevc.decoder configure failed"),
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        )

        // 1st recoverable decoder failure -> triggers bounded retry #1 (MAX_DECODER_RECOVERY_RETRIES = 1), keeps isPlaying=false, preserves media
        vm.handlePlayerError(decoderInitError)
        val stateAfterRetry1 = vm.playbackState.value
        assertFalse("isPlaying must NEVER be set to true before recovery reaches STATE_READY", stateAfterRetry1.isPlaying)
        assertTrue("isBuffering must be true while decoder recovery is in progress", stateAfterRetry1.isBuffering)
        assertEquals(com.example.media.Resilient4KCodecEngine.MAX_DECODER_RECOVERY_RETRIES, stateAfterRetry1.decoderRecoveryAttempts)
        assertNull("playbackError should stay null during active recovery attempt #1", stateAfterRetry1.playbackError)
        assertEquals(videoEntity.uriString, stateAfterRetry1.currentMedia?.uriString)

        // 2nd decoder failure -> exceeds MAX_DECODER_RECOVERY_RETRIES (1), halts retry loop and surfaces diagnostic error
        vm.handlePlayerError(decoderInitError)
        val stateAfterExhausted = vm.playbackState.value
        assertFalse("isPlaying must be false when decoder recovery is exhausted", stateAfterExhausted.isPlaying)
        assertFalse("isBuffering must be false when decoder recovery is exhausted", stateAfterExhausted.isBuffering)
        assertNotNull("Diagnostic error must be reported once retries are exhausted", stateAfterExhausted.playbackError)
        assertTrue(
            "Diagnostic error should include resolution or codec context",
            stateAfterExhausted.playbackError!!.contains("3840x2160") ||
                stateAfterExhausted.playbackError!!.contains("video/hevc")
        )

        // Verify non-decoder error (e.g., IO error) does NOT trigger decoder fallback retry
        vm.setQueue(listOf(videoEntity), initialIndex = 0)
        val ioError = androidx.media3.common.PlaybackException(
            "Source file unreadable",
            java.io.FileNotFoundException("Missing file"),
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        )
        vm.handlePlayerError(ioError)
        val stateAfterIoError = vm.playbackState.value
        assertFalse(stateAfterIoError.isPlaying)
        assertEquals("Non-decoder error must not increment decoder recovery attempts", 0, stateAfterIoError.decoderRecoveryAttempts)
        assertNotNull(stateAfterIoError.playbackError)

        videoFile.delete()
    }

    @Test
    fun audioToVideoAndVideoToAudioSwitching_updatesPlaybackStateAndRejectsTruncatedFiles() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PulsePlayViewModel(app)
        val generator = SampleMediaGenerator(context)

        val audioFile = File(context.cacheDir, "switch_test_audio.wav")
        val videoFile = File(context.cacheDir, "switch_test_video.mp4")
        val truncatedFile = File(context.cacheDir, "truncated_broken.mp4")

        generator.generateStereoWavFile(audioFile, durationSeconds = 3)
        writeMinimalTestMp4File(videoFile)
        truncatedFile.writeBytes(ByteArray(0))

        val audioEntity = MediaEntity(
            uriString = Uri.fromFile(audioFile).toString(),
            mediaStoreId = null,
            title = "Switch Test Audio",
            artist = "PulsePlay",
            album = "Audio",
            folderName = "Music",
            durationMs = 3000L,
            sizeBytes = audioFile.length(),
            mimeType = "audio/wav",
            mediaType = MediaType.AUDIO,
            dateAddedEpochSec = 1700000600L
        )
        val videoEntity = MediaEntity(
            uriString = Uri.fromFile(videoFile).toString(),
            mediaStoreId = null,
            title = "Switch Test Video",
            artist = "PulsePlay",
            album = "Video",
            folderName = "Movies",
            durationMs = 6000L,
            sizeBytes = videoFile.length(),
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            width = 640,
            height = 360,
            dateAddedEpochSec = 1700000601L
        )
        val truncatedEntity = videoEntity.copy(
            uriString = Uri.fromFile(truncatedFile).toString(),
            title = "Truncated Video",
            sizeBytes = 0L
        )

        // 1. Start audio playback
        vm.setQueue(listOf(audioEntity, videoEntity), initialIndex = 0)
        assertEquals(MediaType.AUDIO, vm.playbackState.value.currentMedia?.mediaType)
        assertNull(vm.playbackState.value.playbackError)
        assertNotNull("Player instance must be available for binding", vm.getPlayerInstanceForVideoSurface())

        // 2. Switch from audio to video
        vm.setQueue(listOf(audioEntity, videoEntity), initialIndex = 1)
        assertEquals(MediaType.VIDEO, vm.playbackState.value.currentMedia?.mediaType)
        assertEquals(640, vm.playbackState.value.currentMedia?.width)
        assertEquals(360, vm.playbackState.value.currentMedia?.height)
        assertFalse(vm.playbackState.value.hasRenderedFirstFrame)

        // Simulate first frame rendered callback
        vm.onRenderedFirstFrame()
        assertTrue(vm.playbackState.value.hasRenderedFirstFrame)
        assertFalse(vm.playbackState.value.isBuffering)

        // 3. Switch from video back to audio
        vm.setQueue(listOf(audioEntity, videoEntity), initialIndex = 0)
        assertEquals(MediaType.AUDIO, vm.playbackState.value.currentMedia?.mediaType)

        // 4. Attempt to load truncated 0-byte video -> immediately rejected with clear error and isPlaying=false
        vm.setQueue(listOf(truncatedEntity), initialIndex = 0)
        assertFalse(vm.playbackState.value.isPlaying)
        assertNotNull(vm.playbackState.value.playbackError)
        assertTrue(vm.playbackState.value.playbackError!!.lowercase().contains("corrupted or empty"))

        audioFile.delete()
        videoFile.delete()
        truncatedFile.delete()
    }

    private fun writeMinimalTestMp4File(outMp4: File) {
        if (outMp4.exists()) outMp4.delete()
        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 640, 360).apply {
            setInteger(MediaFormat.KEY_FRAME_RATE, 15)
        }
        val muxer = IsoMp4Muxer(outMp4)
        val vTrack = muxer.addTrack(videoFormat)
        muxer.start()
        val fakeIdrNal = ByteArray(64) { idx -> if (idx == 0) 0x65.toByte() else (idx and 0xFF).toByte() }
        val info = MediaCodec.BufferInfo().apply {
            set(0, fakeIdrNal.size, 0L, MediaCodec.BUFFER_FLAG_KEY_FRAME)
        }
        muxer.writeSampleData(vTrack, ByteBuffer.wrap(fakeIdrNal), info)
        muxer.stop()
        muxer.release()
    }

    @Test
    fun responsiveLandscapeLayout_adaptsAcrossPortraitSmallLandscapeLargeLandscapeAndTabletAndPreservesPlaybackState() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PulsePlayViewModel(app)

        val track1 = MediaEntity(
            uriString = "file:///landscape_song_1.wav",
            mediaStoreId = null,
            title = "Landscape Horizon",
            artist = "PulsePlay Studio",
            album = "Adaptive Sessions",
            folderName = "Music",
            durationMs = 240_000L,
            sizeBytes = 4096L,
            mimeType = "audio/wav",
            mediaType = MediaType.AUDIO,
            dateAddedEpochSec = 1700000900L
        )
        val track2 = track1.copy(
            uriString = "file:///landscape_song_2.wav",
            title = "Sunset Synthwave"
        )
        vm.setQueue(listOf(track1, track2), initialIndex = 0)
        vm.setPlaybackSpeed(1.25f)

        var viewportSize by mutableStateOf(DpSize(390.dp, 844.dp)) // Start in Portrait

        composeTestRule.setContent {
            PulsePlayTheme {
                Box(modifier = Modifier.requiredSize(viewportSize.width, viewportSize.height)) {
                    PulsePlayAppRoot(viewModel = vm)
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. PORTRAIT (390dp x 844dp): Bottom NavigationBar is shown, NavigationRail & Side Panel are hidden
        composeTestRule.onNodeWithTag("bottom_navigation_bar").assertExists()
        assertEquals(0, composeTestRule.onAllNodesWithTag("side_navigation_rail").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("landscape_now_playing_panel").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("persistent_mini_player").assertExists()

        // 2. Rotate to SMALL LANDSCAPE (640dp x 360dp):
        // Left NavigationRail is shown, Bottom NavigationBar is hidden, compact header toggle & side-panel toggle exist
        viewportSize = DpSize(640.dp, 360.dp)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("side_navigation_rail").assertExists()
        assertEquals(0, composeTestRule.onAllNodesWithTag("bottom_navigation_bar").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("persistent_mini_player").assertExists()
        composeTestRule.onNodeWithTag("header_quick_actions_toggle_button").assertExists()
        composeTestRule.onNodeWithTag("header_toggle_now_playing_panel_button").assertExists()

        // In Small Landscape (640dp < 700dp), side panel is collapsed by default to give list space,
        // but can be toggled open via the header or mini-player side-panel button (never silently hidden!)
        assertEquals(0, composeTestRule.onAllNodesWithTag("landscape_now_playing_panel").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("header_toggle_now_playing_panel_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("landscape_now_playing_panel").assertExists()
        assertEquals(0, composeTestRule.onAllNodesWithTag("persistent_mini_player").fetchSemanticsNodes().size)

        // Collapse side panel again via its close button -> persistent mini-player returns
        composeTestRule.onNodeWithTag("side_panel_collapse_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodesWithTag("landscape_now_playing_panel").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("persistent_mini_player").assertExists()

        // Re-enable side panel via the persistent mini-player side-panel button -> mini-player hides so only 1 panel shows
        composeTestRule.onNodeWithTag("mini_player_toggle_side_panel_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("landscape_now_playing_panel").assertExists()
        assertEquals(0, composeTestRule.onAllNodesWithTag("persistent_mini_player").fetchSemanticsNodes().size)

        // 3. Resize to LARGE LANDSCAPE (844dp x 390dp):
        // Left NavigationRail + Center Content + Right Now Playing Side Panel (NO duplicate bottom mini-player)
        viewportSize = DpSize(844.dp, 390.dp)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("side_navigation_rail").assertExists()
        composeTestRule.onNodeWithTag("landscape_now_playing_panel").assertExists()
        assertEquals(0, composeTestRule.onAllNodesWithTag("persistent_mini_player").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("side_panel_play_pause_button").assertExists()
        composeTestRule.onNodeWithTag("side_panel_next_button").assertExists()
        composeTestRule.onNodeWithTag("side_panel_previous_button").assertExists()

        // Switch tabs via Left NavigationRail (Music -> Videos -> Library -> Settings -> Home) and verify playback state is preserved
        composeTestRule.onNodeWithTag("nav_tab_music")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals(PrimaryTab.MUSIC, vm.selectedTab.value)

        composeTestRule.onNodeWithTag("nav_tab_videos")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals(PrimaryTab.VIDEOS, vm.selectedTab.value)

        composeTestRule.onNodeWithTag("nav_tab_library")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals(PrimaryTab.LIBRARY, vm.selectedTab.value)

        composeTestRule.onNodeWithTag("nav_tab_settings")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals(PrimaryTab.SETTINGS, vm.selectedTab.value)

        composeTestRule.onNodeWithTag("nav_tab_home")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals(PrimaryTab.HOME, vm.selectedTab.value)

        // 4. Resize to TABLET LANDSCAPE (1280dp x 800dp):
        // Wide 4-column category row on Home + NavigationRail + Right NowPlayingSidePanel
        viewportSize = DpSize(1280.dp, 800.dp)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("side_navigation_rail").assertExists()
        composeTestRule.onNodeWithTag("landscape_now_playing_panel").assertExists()
        composeTestRule.onNodeWithTag("home_categories_row_wide").assertExists()

        // 5. Expand Full Now Playing Overlay in Landscape -> renders 2-column LandscapeAudioNowPlayingDeck
        composeTestRule.onNodeWithTag("side_panel_expand_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertTrue(vm.isNowPlayingExpanded.value)
        composeTestRule.onNodeWithTag("landscape_audio_now_playing_split").assertExists()
        composeTestRule.onNodeWithTag("transport_play_pause_button").assertExists()

        // 6. Rotate back to PORTRAIT (390dp x 844dp) while Now Playing is open -> switches to Portrait deck without losing track or speed
        viewportSize = DpSize(390.dp, 844.dp)
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodesWithTag("landscape_audio_now_playing_split").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("transport_play_pause_button").assertExists()
        assertEquals("Landscape Horizon", vm.playbackState.value.currentMedia?.title)
        assertEquals(1.25f, vm.playbackState.value.playbackSpeed, 0.01f)
        assertEquals(2, vm.playbackState.value.queue.size)
    }

    @Test
    fun mediaItemOverflowMenuAndSortDropdown_triggerAllActionsInLandscape() {
        val sampleTrack = MediaEntity(
            uriString = "file:///test_overflow_track.wav",
            mediaStoreId = null,
            title = "Overflow Test Song",
            artist = "Test Artist",
            album = "Test Album",
            folderName = "Music",
            durationMs = 195_000L,
            sizeBytes = 2048L,
            mimeType = "audio/wav",
            mediaType = MediaType.AUDIO,
            dateAddedEpochSec = 1700000950L
        )

        var playClicked = false
        var favoriteToggled = false
        var playlistClicked = false
        var studioClicked = false
        var selectedSort = SortOption.DATE_DESC

        composeTestRule.setContent {
            PulsePlayTheme {
                Box(modifier = Modifier.size(800.dp, 400.dp)) {
                    androidx.compose.foundation.layout.Column {
                        SearchAndActionHeader(
                            searchQuery = "",
                            onSearchQueryChange = {},
                            currentSort = selectedSort,
                            onSortChange = { selectedSort = it },
                            onPickSafFileClick = {},
                            onOpenStudioClick = {},
                            onOpenEqualizerClick = {},
                            onRescanClick = {},
                            isCompactHeight = true
                        )
                        MediaItemCard(
                            media = sampleTrack,
                            isFavorite = false,
                            isCurrentlyPlaying = false,
                            onPlayClick = { playClicked = true },
                            onFavoriteToggle = { favoriteToggled = true },
                            onAddToPlaylistClick = { playlistClicked = true },
                            onOpenInStudioClick = { studioClicked = true }
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // Verify sort dropdown menu works
        composeTestRule.onNodeWithTag("sort_menu_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sort_option_duration_desc").performClick()
        composeTestRule.waitForIdle()
        assertEquals(SortOption.DURATION_DESC, selectedSort)

        // Verify MediaItemCard direct favorite toggle button
        val safeSuffix = sampleTrack.title.take(12).replace(" ", "_")
        composeTestRule.onNodeWithTag("media_item_favorite_$safeSuffix").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Direct favorite toggle button must work", favoriteToggled)

        // Verify MediaItemCard 3-dot overflow menu actions (Add to Playlist, Edit in Studio, Play Now)
        composeTestRule.onNodeWithTag("media_item_overflow_$safeSuffix").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overflow_playlist_action").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Overflow 'Add to Playlist' must trigger callback", playlistClicked)

        composeTestRule.onNodeWithTag("media_item_overflow_$safeSuffix").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overflow_studio_action").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Overflow 'Edit in Media Studio' must trigger callback", studioClicked)

        composeTestRule.onNodeWithTag("media_item_overflow_$safeSuffix").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overflow_play_action").performClick()
        composeTestRule.waitForIdle()
        assertTrue("Overflow 'Play Now' must trigger callback", playClicked)
    }

    @Test
    fun `immersive system bars hide in landscape and fullscreen video and restore on library or audio screens`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val window = activity.window

        // 1. Landscape video playback (even before pressing explicit fullscreen toggle) -> MUST hide system bars
        val hideInLandscapeVideo = PlayerHandoffAndVideoPolicy.shouldHideSystemBarsForVideo(
            isActivityResumed = true,
            isNowPlayingOverlayVisible = true,
            isStudioSheetOpen = false,
            mediaType = MediaType.VIDEO,
            isFullscreenVideo = false,
            isLandscapeViewport = true,
            orientationPreference = VideoOrientationPreference.AUTO
        )
        assertTrue("System bars must hide in Landscape video playback", hideInLandscapeVideo)
        PlayerHandoffAndVideoPolicy.applyImmersiveSystemBars(window, hideInLandscapeVideo)
        assertTrue(
            "Window must have system bars hidden in Landscape video playback",
            PlayerHandoffAndVideoPolicy.areSystemBarsHiddenOnWindow(window)
        )

        // 2. Exiting fullscreen / rotating to Portrait non-fullscreen -> MUST restore system bars
        val hideInPortraitNonFullscreen = PlayerHandoffAndVideoPolicy.shouldHideSystemBarsForVideo(
            isActivityResumed = true,
            isNowPlayingOverlayVisible = true,
            isStudioSheetOpen = false,
            mediaType = MediaType.VIDEO,
            isFullscreenVideo = false,
            isLandscapeViewport = false,
            orientationPreference = VideoOrientationPreference.AUTO
        )
        assertFalse("System bars must be visible in Portrait non-fullscreen video", hideInPortraitNonFullscreen)
        PlayerHandoffAndVideoPolicy.applyImmersiveSystemBars(window, hideInPortraitNonFullscreen)
        assertFalse(
            "Window must restore system bars when exiting fullscreen to Portrait",
            PlayerHandoffAndVideoPolicy.areSystemBarsHiddenOnWindow(window)
        )

        // 3. Explicit Fullscreen video started -> MUST hide system bars
        val hideInExplicitFullscreen = PlayerHandoffAndVideoPolicy.shouldHideSystemBarsForVideo(
            isActivityResumed = true,
            isNowPlayingOverlayVisible = true,
            isStudioSheetOpen = false,
            mediaType = MediaType.VIDEO,
            isFullscreenVideo = true,
            isLandscapeViewport = false,
            orientationPreference = VideoOrientationPreference.AUTO
        )
        assertTrue("System bars must hide when fullscreen video is active", hideInExplicitFullscreen)
        PlayerHandoffAndVideoPolicy.applyImmersiveSystemBars(window, hideInExplicitFullscreen)
        assertTrue(PlayerHandoffAndVideoPolicy.areSystemBarsHiddenOnWindow(window))

        // 4. Leaving video player back to Music/Video Library -> MUST restore system bars
        val hideOnLibraryScreen = PlayerHandoffAndVideoPolicy.shouldHideSystemBarsForVideo(
            isActivityResumed = true,
            isNowPlayingOverlayVisible = false,
            isStudioSheetOpen = false,
            mediaType = MediaType.VIDEO,
            isFullscreenVideo = true,
            isLandscapeViewport = true,
            orientationPreference = VideoOrientationPreference.LANDSCAPE
        )
        assertFalse("System bars must never be hidden on Library screens", hideOnLibraryScreen)
        PlayerHandoffAndVideoPolicy.applyImmersiveSystemBars(window, hideOnLibraryScreen)
        assertFalse(PlayerHandoffAndVideoPolicy.areSystemBarsHiddenOnWindow(window))

        // 5. Audio Now Playing in Landscape -> MUST NOT hide system bars
        val hideOnLandscapeAudio = PlayerHandoffAndVideoPolicy.shouldHideSystemBarsForVideo(
            isActivityResumed = true,
            isNowPlayingOverlayVisible = true,
            isStudioSheetOpen = false,
            mediaType = MediaType.AUDIO,
            isFullscreenVideo = false,
            isLandscapeViewport = true,
            orientationPreference = VideoOrientationPreference.AUTO
        )
        assertFalse("System bars must not be hidden during Audio playback", hideOnLandscapeAudio)
    }

    @Test
    fun `video player controls auto-hide after inactivity and recover on tap during active playback and rotation`() {
        val sampleVideo = MediaEntity(
            uriString = "file:///storage/emulated/0/Movies/cinema_clip.mp4",
            mediaStoreId = null,
            title = "Cinema Test Clip",
            artist = "Pulse Director",
            album = "Movies",
            folderName = "Movies",
            durationMs = 180_000L,
            sizeBytes = 15_728_640L,
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            width = 1920,
            height = 1080,
            dateAddedEpochSec = 1700001000L
        )
        val secondVideo = sampleVideo.copy(
            uriString = "file:///storage/emulated/0/Movies/cinema_clip_2.mp4",
            title = "Cinema Clip Two"
        )

        var viewportSize by mutableStateOf(DpSize(892.dp, 412.dp)) // Start in Landscape
        var isPlayingState by mutableStateOf(true)
        var positionMsState by mutableStateOf(12_000L)
        var isFullscreenState by mutableStateOf(false)
        var orientationPrefState by mutableStateOf(VideoOrientationPreference.AUTO)
        var nextClickedCount = 0
        var prevClickedCount = 0

        composeTestRule.setContent {
            PulsePlayTheme {
                Box(modifier = Modifier.requiredSize(viewportSize)) {
                    NowPlayingOverlayScreen(
                        playbackState = com.example.viewmodel.PlaybackUiState(
                            currentMedia = sampleVideo,
                            queue = listOf(sampleVideo, secondVideo),
                            currentQueueIndex = 0,
                            isPlaying = isPlayingState,
                            currentPositionMs = positionMsState,
                            durationMs = sampleVideo.durationMs,
                            isFullscreenVideo = isFullscreenState,
                            activeSubtitleText = "Synchronized subtitle line"
                        ),
                        userPreferences = UserPreferencesState(
                            videoOrientationPreference = orientationPrefState,
                            subtitlesEnabledByDefault = true
                        ),
                        isFavorite = false,
                        playerInstance = null,
                        onCollapse = {},
                        onPlayPause = { isPlayingState = !isPlayingState },
                        onSeekTo = { positionMsState = it },
                        onPrevious = { prevClickedCount++ },
                        onNext = { nextClickedCount++ },
                        onToggleShuffle = {},
                        onCycleRepeat = {},
                        onSetPlaybackSpeed = {},
                        onCycleAspectMode = {},
                        onRotateVideo90 = {},
                        onToggleFullscreen = { isFullscreenState = it },
                        onSelectVideoOrientation = { orientationPrefState = it },
                        onBrightnessGestureDelta = {},
                        onVolumeGestureDelta = {},
                        onPickSubtitleFileClick = {},
                        onToggleSubtitlesEnabled = {},
                        onSetSubtitleFontScale = {},
                        onSetSubtitleOffsetMs = {},
                        onToggleFavorite = {},
                        onOpenEqualizer = {},
                        onOpenStudio = {},
                        onSelectQueueItem = {},
                        onRemoveQueueItem = {},
                        onRetryPlayback = {}
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(300L)
        composeTestRule.waitForIdle()

        // 1. Verify all video controls are initially visible in Landscape
        composeTestRule.onNodeWithTag("transport_play_pause_button").assertExists()
        composeTestRule.onNodeWithTag("now_playing_seek_slider").assertExists()
        composeTestRule.onNodeWithTag("video_current_time_text").assertExists()
        composeTestRule.onNodeWithTag("video_total_time_text").assertExists()
        composeTestRule.onNodeWithTag("transport_rewind_10_button").assertExists()
        composeTestRule.onNodeWithTag("transport_forward_10_button").assertExists()
        composeTestRule.onNodeWithTag("transport_previous_button").assertExists()
        composeTestRule.onNodeWithTag("transport_next_button").assertExists()
        composeTestRule.onNodeWithTag("video_audio_track_pill").assertExists()
        composeTestRule.onNodeWithTag("video_subtitles_chip").assertExists()
        composeTestRule.onNodeWithTag("video_fullscreen_chip").assertExists()
        composeTestRule.onNodeWithTag("active_subtitle_overlay").assertExists()

        // 2. Simulate active video playback with 200ms position ticks for 4.2 seconds of inactivity
        repeat(21) {
            positionMsState += 200L
            composeTestRule.mainClock.advanceTimeBy(200L)
        }
        composeTestRule.mainClock.advanceTimeBy(300L)
        composeTestRule.waitForIdle()

        // Controls must auto-hide after inactivity while subtitles remain visible
        composeTestRule.onNodeWithTag("transport_play_pause_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("now_playing_seek_slider").assertDoesNotExist()
        composeTestRule.onNodeWithTag("active_subtitle_overlay").assertExists()

        // 3. While playback position continues updating every 200ms, TAP the video surface to bring controls back
        positionMsState += 200L
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.onNodeWithTag("video_surface_container").performClick()
        positionMsState += 200L
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()

        // Controls MUST immediately recover and be visible again!
        composeTestRule.onNodeWithTag("transport_play_pause_button").assertExists()
        composeTestRule.onNodeWithTag("now_playing_seek_slider").assertExists()
        composeTestRule.onNodeWithTag("transport_forward_10_button").assertExists()
        composeTestRule.onNodeWithTag("video_fullscreen_chip").assertExists()

        // 4. Test transport controls: +10s forward, -10s rewind, Next, Previous, Play/Pause
        val posBeforeSeek = positionMsState
        composeTestRule.onNodeWithTag("transport_forward_10_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(100L)
        assertEquals(posBeforeSeek + 10_000L, positionMsState)

        composeTestRule.onNodeWithTag("transport_rewind_10_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(100L)
        assertEquals(posBeforeSeek, positionMsState)

        composeTestRule.onNodeWithTag("transport_next_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(100L)
        assertEquals(1, nextClickedCount)

        composeTestRule.onNodeWithTag("transport_previous_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(100L)
        assertEquals(1, prevClickedCount)

        // 5. Rotate from Landscape (892x412) -> Portrait (412x892) -> Landscape (892x412)
        viewportSize = DpSize(412.dp, 892.dp)
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()

        // Toggle Fullscreen from Portrait
        composeTestRule.onNodeWithTag("video_fullscreen_chip").performClick()
        composeTestRule.mainClock.advanceTimeBy(200L)
        assertTrue("Fullscreen toggle must activate fullscreen state", isFullscreenState)

        // Rotate to Landscape and verify state, position, and controls remain intact
        viewportSize = DpSize(892.dp, 412.dp)
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()
        assertEquals("Playback position must not reset on rotation", posBeforeSeek, positionMsState)
        composeTestRule.onNodeWithTag("transport_play_pause_button").assertExists()

        // Let controls auto-hide again in Landscape, then tap after rotation to verify tap still recovers controls
        repeat(21) {
            positionMsState += 200L
            composeTestRule.mainClock.advanceTimeBy(200L)
        }
        composeTestRule.mainClock.advanceTimeBy(300L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("transport_play_pause_button").assertDoesNotExist()

        composeTestRule.onNodeWithTag("video_surface_container").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("transport_play_pause_button").assertExists()
        composeTestRule.onNodeWithTag("now_playing_seek_slider").assertExists()

        composeTestRule.mainClock.autoAdvance = true
    }

    @Test
    fun landscapeOverflowAndFeatureAccessibilityAudit_acrossAllScreensAndDialogs() {
        val sampleVideo = MediaEntity(
            uriString = "file:///storage/emulated/0/Movies/PulsePlay_Cinema_1080p.mp4",
            mediaStoreId = null,
            title = "PulsePlay Cinema Masterpiece With Very Long Title For Overflow Audit",
            artist = "PulsePlay Studio Orchestra",
            album = "Landscape Audit Vault",
            folderName = "Movies_With_Long_Folder_Directory_Name",
            durationMs = 240_000L,
            sizeBytes = 42_000_000L,
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            width = 1920,
            height = 1080,
            dateAddedEpochSec = 1_700_000_000L,
            isAvailable = true
        )
        val sampleAudio = sampleVideo.copy(
            uriString = "file:///storage/emulated/0/Music/PulsePlay_Acoustic_Master.wav",
            title = "PulsePlay Acoustic Master With Long Track Title",
            mediaType = MediaType.AUDIO,
            mimeType = "audio/wav",
            width = 0,
            height = 0
        )

        var screenMode by mutableStateOf("VIDEO_HUD")
        var subtitleOffsetMs by mutableStateOf(0L)
        var selectedOrientationPref by mutableStateOf(VideoOrientationPreference.AUTO)
        var audioTrimTriggered by mutableStateOf(false)

        composeTestRule.setContent {
            PulsePlayTheme {
                Box(modifier = Modifier.requiredSize(DpSize(640.dp, 360.dp))) {
                    when (screenMode) {
                        "VIDEO_HUD" -> {
                            NowPlayingOverlayScreen(
                                playbackState = com.example.viewmodel.PlaybackUiState(
                                    currentMedia = sampleVideo,
                                    isPlaying = false,
                                    currentPositionMs = 45_000L,
                                    durationMs = 240_000L,
                                    queue = listOf(sampleVideo, sampleAudio),
                                    currentQueueIndex = 0,
                                    isFullscreenVideo = true,
                                    decoderStatusLabel = "Hardware H.264 Ready",
                                    isDecoderVerified = true
                                ),
                                userPreferences = UserPreferencesState(
                                    subtitleSyncOffsetMs = subtitleOffsetMs,
                                    videoOrientationPreference = selectedOrientationPref
                                ),
                                isFavorite = true,
                                playerInstance = null,
                                onCollapse = {},
                                onPlayPause = {},
                                onSeekTo = {},
                                onPrevious = {},
                                onNext = {},
                                onToggleShuffle = {},
                                onCycleRepeat = {},
                                onSetPlaybackSpeed = {},
                                onCycleAspectMode = {},
                                onRotateVideo90 = {},
                                onToggleFullscreen = {},
                                onToggleControlsLock = {},
                                onSelectVideoOrientation = { selectedOrientationPref = it },
                                onToggleKeepScreenOn = {},
                                onToggleVideoGestureControls = {},
                                onBrightnessGestureDelta = {},
                                onVolumeGestureDelta = {},
                                onPickSubtitleFileClick = {},
                                onToggleSubtitlesEnabled = {},
                                onSetSubtitleFontScale = {},
                                onSetSubtitleOffsetMs = { subtitleOffsetMs = it },
                                onSetSleepTimerMinutes = {},
                                onSetSleepTimerEndOfTrack = {},
                                onToggleFavorite = {},
                                onOpenEqualizer = {},
                                onOpenStudio = {},
                                onSelectQueueItem = {},
                                onRemoveQueueItem = {},
                                onMoveQueueItem = { _, _ -> },
                                onSaveQueueAsPlaylist = {},
                                onRetryPlayback = {}
                            )
                        }
                        "STUDIO" -> {
                            com.example.ui.screens.StudioEditorOverlayScreen(
                                allMedia = listOf(sampleAudio, sampleVideo),
                                initialTargetMedia = sampleAudio,
                                conversionProfiles = emptyList(),
                                processingJobs = emptyList(),
                                onClose = {},
                                onPreviewSegment = { _, _ -> },
                                onRunAudioTrim = { _, _, _, _ -> audioTrimTriggered = true },
                                onRunVideoTrimRotate = { _, _, _, _, _ -> },
                                onRunConversionOrExtraction = { _, _, _, _ -> },
                                onCancelActiveJob = {},
                                onClearCompletedJobs = {},
                                onPlayExportedUri = {}
                            )
                        }
                        "SETTINGS" -> {
                            com.example.ui.screens.ProfileAndSettingsScreen(
                                userPreferences = UserPreferencesState(
                                    videoOrientationPreference = selectedOrientationPref
                                ),
                                hasMediaPermission = true,
                                historyCount = 5,
                                sleepTimerMinutes = 0,
                                sleepTimerStopAtEndOfTrack = false,
                                preampGainDb = 0f,
                                onSetThemeMode = {},
                                onSetDynamicColor = {},
                                onSetDefaultSpeed = {},
                                onSetResumePlayback = {},
                                onSetRecordHistory = {},
                                onSetBackgroundPlayback = {},
                                onSetKeepScreenOnDuringVideo = {},
                                onSetVideoGestureControls = {},
                                onSetVideoOrientationPref = { pref -> selectedOrientationPref = pref },
                                onSetAllowExperimental4kSoftwareFallback = {},
                                onSetAudioBalance = {},
                                onSetPreampGainDb = {},
                                onSetSleepTimerMinutes = {},
                                onSetSleepTimerEndOfTrack = {},
                                onSetSubtitlesEnabled = {},
                                onSetSubtitleScale = {},
                                onSetDefaultSort = {},
                                onOpenEqualizerClick = {},
                                onRequestMediaPermissionClick = {},
                                onSetOptionalAdsConfig = { _, _ -> },
                                onClearHistoryOnlyClick = {},
                                onResetPreferencesClick = {}
                            )
                        }
                    }
                }
            }
        }

        // 1. In Compact Landscape (640x360) Video HUD: open Audio Track popup and verify it does not clip
        composeTestRule.onNodeWithTag("video_audio_track_pill")
            .assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        val audioSheetBounds = composeTestRule.onNodeWithTag("video_audio_track_sheet_card")
            .assertExists()
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue("Audio track sheet top must not clip off-screen in 360dp height", audioSheetBounds.top >= 0f)

        // 2. Open Subtitle Settings Drawer in Compact Landscape and verify +250ms button is reachable and not clipped
        composeTestRule.onNodeWithTag("video_subtitles_chip")
            .assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("subtitle_offset_plus_250_button")
            .assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals(250L, subtitleOffsetMs)

        // 3. Switch to Studio Editor in Compact Landscape (640x360) and verify Preview & Trim buttons work immediately
        screenMode = "STUDIO"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("preview_trim_segment_button").assertExists()
        composeTestRule.onNodeWithTag("execute_audio_trim_button")
            .assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertTrue("Trim & Export Audio button must be clickable in compact Landscape", audioTrimTriggered)

        // 4. Switch to Settings in Compact Landscape (640x360), verify Theme chips at top
        screenMode = "SETTINGS"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("theme_mode_chip_system").assertExists()
        composeTestRule.onNodeWithTag("theme_mode_chip_dark").assertExists()
        composeTestRule.onNodeWithTag("theme_mode_chip_light").assertExists()
    }

    @Test
    fun `threeDotOverflowMenu supports Share Ringtone FileInfo and existing actions for Audio and Video`() {
        val generator = SampleMediaGenerator(context)
        val audioFile = File(context.filesDir, "pulse_samples/menu_test_audio.wav").apply {
            parentFile?.mkdirs()
        }
        generator.generateStereoWavFile(audioFile, durationSeconds = 3)

        val videoFile = File(context.filesDir, "pulse_samples/menu_test_video.mp4").apply {
            parentFile?.mkdirs()
        }
        val muxer = IsoMp4Muxer(videoFile, majorBrand = "mp42")
        val vFmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720)
        val aFmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2)
        val vTrack = muxer.addTrack(vFmt)
        val aTrack = muxer.addTrack(aFmt)
        muxer.start()
        val annexBIdr = byteArrayOf(
            0, 0, 0, 1, 0x67, 0x42, 0x00, 0x1E,
            0, 0, 0, 1, 0x68, 0xCE.toByte(), 0x3C, 0x80.toByte(),
            0, 0, 0, 1, 0x65, 0x88.toByte(), 0x84.toByte(), 0x21
        )
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

        val audioEntity = MediaEntity(
            uriString = Uri.fromFile(audioFile).toString(),
            mediaStoreId = null,
            title = "Menu Test Acoustic Track",
            artist = "PulsePlay Ensemble",
            album = "Studio Masters",
            folderName = "pulse_samples",
            durationMs = 3_000L,
            sizeBytes = audioFile.length(),
            mimeType = "audio/wav",
            mediaType = MediaType.AUDIO,
            width = 0,
            height = 0,
            dateAddedEpochSec = 1_700_000_500L,
            isAvailable = true
        )
        val videoEntity = MediaEntity(
            uriString = Uri.fromFile(videoFile).toString(),
            mediaStoreId = null,
            title = "Menu Test Cinema Reel",
            artist = "PulsePlay Visuals",
            album = "Cinema Vault",
            folderName = "pulse_samples",
            durationMs = 15_000L,
            sizeBytes = videoFile.length(),
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            width = 1280,
            height = 720,
            dateAddedEpochSec = 1_700_000_600L,
            isAvailable = true
        )

        // 1. Verify secure Share action for BOTH Audio and Video (content:// URI, MIME type, FLAG_GRANT_READ_URI_PERMISSION)
        val audioShareOutcome = com.example.media.MediaFileActionsHelper.shareMediaFile(context, audioEntity)
        assertTrue("Audio share should succeed", audioShareOutcome is com.example.media.ShareOutcome.Success)
        val audioShareLaunched = audioShareOutcome as com.example.media.ShareOutcome.Success
        assertEquals("content", audioShareLaunched.sharedUri.scheme)
        assertEquals("audio/wav", audioShareLaunched.mimeType)
        assertEquals(android.content.Intent.ACTION_CHOOSER, audioShareLaunched.chooserIntent.action)
        @Suppress("DEPRECATION")
        val innerAudioSend = audioShareLaunched.chooserIntent.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)
        assertNotNull(innerAudioSend)
        assertEquals(android.content.Intent.ACTION_SEND, innerAudioSend!!.action)
        assertTrue(
            "Share intent must grant temporary read URI permission",
            (innerAudioSend.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
        )

        val videoShareOutcome = com.example.media.MediaFileActionsHelper.shareMediaFile(context, videoEntity)
        assertTrue("Video share should succeed", videoShareOutcome is com.example.media.ShareOutcome.Success)
        val videoShareLaunched = videoShareOutcome as com.example.media.ShareOutcome.Success
        assertEquals("content", videoShareLaunched.sharedUri.scheme)
        assertEquals("video/mp4", videoShareLaunched.mimeType)

        // 2. Verify Set as Ringtone flow: PermissionRequired -> Granted -> Success for Audio, and rejected for Video
        try {
            com.example.media.MediaFileActionsHelper.testCanWriteSettingsOverride = false
            val deniedOutcome = com.example.media.MediaFileActionsHelper.setAudioAsRingtone(context, audioEntity)
            assertTrue(
                "When WRITE_SETTINGS is not granted, setAudioAsRingtone must return PermissionRequired",
                deniedOutcome is com.example.media.RingtoneOutcome.PermissionRequired
            )
            val permReq = deniedOutcome as com.example.media.RingtoneOutcome.PermissionRequired
            assertEquals(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, permReq.settingsIntent.action)

            com.example.media.MediaFileActionsHelper.testCanWriteSettingsOverride = true
            val grantedOutcome = com.example.media.MediaFileActionsHelper.setAudioAsRingtone(context, audioEntity)
            assertTrue(
                "When WRITE_SETTINGS is granted, setAudioAsRingtone must succeed",
                grantedOutcome is com.example.media.RingtoneOutcome.Success
            )
            val ringtoneSuccess = grantedOutcome as com.example.media.RingtoneOutcome.Success
            assertEquals("content", ringtoneSuccess.ringtoneUri.scheme)
            assertEquals(ringtoneSuccess.ringtoneUri, com.example.media.MediaFileActionsHelper.lastAppliedRingtoneUri)

            val videoRingtoneOutcome = com.example.media.MediaFileActionsHelper.setAudioAsRingtone(context, videoEntity)
            assertTrue(
                "Video files must never be set as ringtone",
                videoRingtoneOutcome is com.example.media.RingtoneOutcome.Failure
            )
        } finally {
            com.example.media.MediaFileActionsHelper.testCanWriteSettingsOverride = null
        }

        // 3. Verify File Information extraction for Audio and Video (truthful metadata + "Not available" fallback)
        val audioInfo = com.example.media.MediaFileActionsHelper.extractDetailedMediaFileInfo(context, audioEntity)
        assertEquals("menu_test_audio.wav", audioInfo.fullFileName)
        assertEquals("WAV", audioInfo.fileExtension)
        assertTrue(audioInfo.mimeType.contains("audio/wav"))
        assertTrue(audioInfo.audioCodec.contains("PCM"))
        assertTrue(audioInfo.bitrate.contains("kbps"))
        assertEquals(com.example.media.MediaFileActionsHelper.NOT_AVAILABLE, audioInfo.resolution)
        assertEquals(com.example.media.MediaFileActionsHelper.NOT_AVAILABLE, audioInfo.videoCodec)

        val videoInfo = com.example.media.MediaFileActionsHelper.extractDetailedMediaFileInfo(context, videoEntity)
        assertEquals("menu_test_video.mp4", videoInfo.fullFileName)
        assertEquals("MP4", videoInfo.fileExtension)
        assertTrue(videoInfo.resolution.contains("1280 × 720"))
        assertTrue(videoInfo.videoCodec.contains("H.264") || videoInfo.videoCodec.contains("AVC"))
        assertTrue(videoInfo.audioCodec.contains("AAC"))

        // 4. Verify Compose UI: Audio vs Video 3-dot overflow menu items, File Info Dialog, and Ringtone Permission Dialog
        var currentCardMedia by mutableStateOf(audioEntity)
        var showFileInfoDialog by mutableStateOf<com.example.media.DetailedMediaFileInfo?>(null)
        var showRingtonePermDialog by mutableStateOf<MediaEntity?>(null)
        var playClickedCount = 0
        var shareClickedCount = 0
        var ringtoneClickedCount = 0
        var fileInfoClickedCount = 0
        var favoriteClickedCount = 0
        var playlistClickedCount = 0
        var studioClickedCount = 0
        var settingsOpenedCount = 0

        composeTestRule.setContent {
            PulsePlayTheme {
                Box(modifier = Modifier.requiredSize(DpSize(640.dp, 360.dp))) {
                    MediaItemCard(
                        media = currentCardMedia,
                        isFavorite = false,
                        isCurrentlyPlaying = false,
                        onPlayClick = { playClickedCount++ },
                        onFavoriteToggle = { favoriteClickedCount++ },
                        onAddToPlaylistClick = { playlistClickedCount++ },
                        onOpenInStudioClick = { studioClickedCount++ },
                        onShareClick = { shareClickedCount++ },
                        onSetRingtoneClick = {
                            ringtoneClickedCount++
                            showRingtonePermDialog = currentCardMedia
                        },
                        onFileInfoClick = {
                            fileInfoClickedCount++
                            showFileInfoDialog = com.example.media.MediaFileActionsHelper.extractDetailedMediaFileInfo(
                                context,
                                currentCardMedia
                            )
                        }
                    )

                    if (showFileInfoDialog != null) {
                        com.example.ui.components.MediaFileInformationDialog(
                            info = showFileInfoDialog!!,
                            onCopyLocation = { loc ->
                                com.example.media.MediaFileActionsHelper.copyTextToClipboard(context, "Path", loc)
                            },
                            onDismiss = { showFileInfoDialog = null }
                        )
                    }

                    if (showRingtonePermDialog != null) {
                        com.example.ui.components.RingtonePermissionDialog(
                            media = showRingtonePermDialog!!,
                            onOpenSettings = { settingsOpenedCount++ },
                            onContinueSetRingtone = { showRingtonePermDialog = null },
                            onDismiss = { showRingtonePermDialog = null }
                        )
                    }
                }
            }
        }

        val audioOverflowTag = "media_item_overflow_${audioEntity.title.take(12).replace(" ", "_")}"
        val videoOverflowTag = "media_item_overflow_${videoEntity.title.take(12).replace(" ", "_")}"

        // Open 3-dot menu on AUDIO item -> verify all 7 options exist including Set as Ringtone
        composeTestRule.onNodeWithTag(audioOverflowTag).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overflow_play_action").assertExists()
        composeTestRule.onNodeWithTag("overflow_share_action").assertExists()
        composeTestRule.onNodeWithTag("overflow_ringtone_action").assertExists()
        composeTestRule.onNodeWithTag("overflow_file_info_action").assertExists()
        composeTestRule.onNodeWithTag("overflow_favorite_action").assertExists()
        composeTestRule.onNodeWithTag("overflow_playlist_action").assertExists()
        composeTestRule.onNodeWithTag("overflow_studio_action").assertExists()

        // Click Share on Audio item
        composeTestRule.onNodeWithTag("overflow_share_action").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, shareClickedCount)

        // Open 3-dot menu and click Set as Ringtone -> opens RingtonePermissionDialog
        composeTestRule.onNodeWithTag(audioOverflowTag).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overflow_ringtone_action").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, ringtoneClickedCount)
        composeTestRule.onNodeWithTag("ringtone_permission_dialog").assertExists()
        composeTestRule.onNodeWithTag("ringtone_open_settings_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, settingsOpenedCount)
        composeTestRule.onNodeWithTag("ringtone_continue_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ringtone_permission_dialog").assertDoesNotExist()

        // Open 3-dot menu and click File Information -> opens scrollable File Information dialog
        composeTestRule.onNodeWithTag(audioOverflowTag).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overflow_file_info_action").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, fileInfoClickedCount)
        composeTestRule.onNodeWithTag("file_info_dialog").assertExists()
        composeTestRule.onNodeWithTag("file_info_row_name").assertExists()
        composeTestRule.onNodeWithTag("file_info_row_extension").assertExists()
        composeTestRule.onNodeWithTag("file_info_row_mime").assertExists()
        composeTestRule.onNodeWithTag("file_info_row_size").assertExists()
        composeTestRule.onNodeWithTag("file_info_row_duration").assertExists()
        composeTestRule.onNodeWithTag("file_info_row_audio_codec").assertExists()
        composeTestRule.onNodeWithTag("file_info_row_bitrate").assertExists()
        composeTestRule.onNodeWithTag("file_info_row_location").assertExists()
        composeTestRule.onNodeWithTag("file_info_copy_location_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("file_info_close_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("file_info_dialog").assertDoesNotExist()

        // Verify existing actions (Play Now, Add to Favorites, Add to Playlist, Edit in Media Studio) remain intact
        composeTestRule.onNodeWithTag(audioOverflowTag).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overflow_play_action").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, playClickedCount)

        composeTestRule.onNodeWithTag(audioOverflowTag).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overflow_favorite_action").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, favoriteClickedCount)

        composeTestRule.onNodeWithTag(audioOverflowTag).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overflow_playlist_action").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, playlistClickedCount)

        composeTestRule.onNodeWithTag(audioOverflowTag).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overflow_studio_action").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, studioClickedCount)

        // Switch card to VIDEO item -> verify Set as Ringtone is NOT shown, while Share and File Info ARE shown
        currentCardMedia = videoEntity
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(videoOverflowTag).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("overflow_play_action").assertExists()
        composeTestRule.onNodeWithTag("overflow_share_action").assertExists()
        composeTestRule.onNodeWithTag("overflow_file_info_action").assertExists()
        composeTestRule.onNodeWithTag("overflow_favorite_action").assertExists()
        composeTestRule.onNodeWithTag("overflow_playlist_action").assertExists()
        composeTestRule.onNodeWithTag("overflow_studio_action").assertExists()
        composeTestRule.onNodeWithTag("overflow_ringtone_action").assertDoesNotExist()

        // Open File Information for Video item -> verify video metadata dialog opens cleanly
        composeTestRule.onNodeWithTag("overflow_file_info_action").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("file_info_dialog").assertExists()
        composeTestRule.onNodeWithTag("file_info_row_resolution").assertExists()
        composeTestRule.onNodeWithTag("file_info_close_button").performClick()
        composeTestRule.waitForIdle()

        audioFile.delete()
        videoFile.delete()
    }

    @Test
    fun `musicPlayerNowPlaying shows threeDotMenuInCorner and consolidatesAllOutsideOptionsIntoDropdown`() {
        val sampleAudio = MediaEntity(
            uriString = "file:///storage/emulated/0/Music/PulsePlay_Clean_Player.wav",
            mediaStoreId = null,
            title = "Clean Player Acoustic Track",
            artist = "PulsePlay Artist",
            album = "Studio Album",
            folderName = "Music",
            durationMs = 210_000L,
            sizeBytes = 8_400_000L,
            mimeType = "audio/wav",
            mediaType = MediaType.AUDIO,
            width = 0,
            height = 0,
            dateAddedEpochSec = 1_700_002_000L,
            isAvailable = true
        )

        var eqOpened = false
        var studioOpened = false
        var shareTriggered = false
        var ringtoneTriggered = false
        var addToPlaylistTriggered = false
        var fileInfoTriggered = false
        var favoriteToggled = false
        var viewportSize by mutableStateOf(DpSize(300.dp, 318.dp))

        composeTestRule.setContent {
            PulsePlayTheme {
                Box(modifier = Modifier.requiredSize(viewportSize)) {
                    NowPlayingOverlayScreen(
                        playbackState = com.example.viewmodel.PlaybackUiState(
                            currentMedia = sampleAudio,
                            queue = listOf(sampleAudio),
                            currentQueueIndex = 0,
                            isPlaying = true,
                            currentPositionMs = 30_000L,
                            durationMs = sampleAudio.durationMs
                        ),
                        userPreferences = UserPreferencesState(),
                        isFavorite = false,
                        playerInstance = null,
                        onCollapse = {},
                        onPlayPause = {},
                        onSeekTo = {},
                        onPrevious = {},
                        onNext = {},
                        onToggleShuffle = {},
                        onCycleRepeat = {},
                        onSetPlaybackSpeed = {},
                        onCycleAspectMode = {},
                        onRotateVideo90 = {},
                        onToggleFullscreen = {},
                        onBrightnessGestureDelta = {},
                        onVolumeGestureDelta = {},
                        onPickSubtitleFileClick = {},
                        onToggleSubtitlesEnabled = {},
                        onSetSubtitleFontScale = {},
                        onSetSubtitleOffsetMs = {},
                        onToggleFavorite = { favoriteToggled = true },
                        onOpenEqualizer = { eqOpened = true },
                        onOpenStudio = { studioOpened = true },
                        onShareCurrentMedia = { shareTriggered = true },
                        onSetRingtoneCurrentMedia = { ringtoneTriggered = true },
                        onAddToPlaylistCurrentMedia = { addToPlaylistTriggered = true },
                        onShowFileInfoCurrentMedia = { fileInfoTriggered = true },
                        onSelectQueueItem = {},
                        onRemoveQueueItem = {},
                        onRetryPlayback = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // 1. Verify top-right 3-dot icon exists while outer scissors icon and clutter chips are NOT shown outside
        composeTestRule.onNodeWithTag("now_playing_more_options_button").assertExists()
        composeTestRule.onNodeWithTag("now_playing_studio_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("now_playing_feature_chips_row").assertDoesNotExist()

        // 2. Open the 3-dot menu in the top-right corner and verify all consolidated options exist inside
        composeTestRule.onNodeWithTag("now_playing_more_options_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("now_playing_overflow_dropdown_menu").assertExists()
        composeTestRule.onNodeWithTag("now_playing_menu_speed").assertExists()
        composeTestRule.onNodeWithTag("now_playing_menu_equalizer").assertExists()
        composeTestRule.onNodeWithTag("now_playing_menu_sleep_timer").assertExists()
        composeTestRule.onNodeWithTag("now_playing_menu_lyrics").assertExists()
        composeTestRule.onNodeWithTag("now_playing_menu_studio").assertExists()
        composeTestRule.onNodeWithTag("now_playing_menu_share").assertExists()
        composeTestRule.onNodeWithTag("now_playing_menu_ringtone").assertExists()
        composeTestRule.onNodeWithTag("now_playing_menu_add_to_playlist").assertExists()
        composeTestRule.onNodeWithTag("now_playing_menu_favorite").assertExists()
        composeTestRule.onNodeWithTag("now_playing_menu_file_info").assertExists()

        // 3. Select Playback Speed from inside the 3-dot menu -> opens PlaybackSpeedControlCard
        composeTestRule.onNodeWithTag("now_playing_menu_speed").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("playback_speed_panel").assertExists()

        // 4. Re-open 3-dot menu and trigger Edit in Media Studio
        composeTestRule.onNodeWithTag("now_playing_more_options_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("now_playing_menu_studio").performClick()
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()
        assertTrue(studioOpened)

        // 5. Verify Landscape Audio Now Playing also shows the 3-dot menu in the corner without outer clutter
        viewportSize = DpSize(460.dp, 318.dp)
        composeTestRule.mainClock.advanceTimeBy(250L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("landscape_audio_now_playing_split").assertExists()
        composeTestRule.onNodeWithTag("now_playing_more_options_button").assertExists()
        composeTestRule.onNodeWithTag("now_playing_studio_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("now_playing_feature_chips_row").assertDoesNotExist()
    }

    @Test
    fun `albumArtworkPipeline loadsEmbeddedAndMediaMetadataArtwork updatesOnTrackSwitch andFallsBackCleanly`() {
        com.example.media.MediaArtworkLoader.clearCache()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PulsePlayViewModel(app)

        val coverBytesTrack1 = createTestAlbumCoverPngBytes(android.graphics.Color.rgb(220, 40, 90))
        val coverBytesTrack2 = createTestAlbumCoverPngBytes(android.graphics.Color.rgb(30, 140, 230))

        val mp3WithApicFile = File(context.cacheDir, "track_with_id3_apic.mp3")
        writeTestMp3WithId3ApicCover(mp3WithApicFile, coverBytesTrack1)

        val wavWithoutArtFile = File(context.cacheDir, "track_without_artwork.wav")
        SampleMediaGenerator(context).generateStereoWavFile(wavWithoutArtFile, durationSeconds = 2)

        val trackWithArt = MediaEntity(
            uriString = Uri.fromFile(mp3WithApicFile).toString(),
            mediaStoreId = null,
            title = "Track With Embedded Cover",
            artist = "PulsePlay Studio",
            album = "Visual Album",
            folderName = "Music",
            durationMs = 180_000L,
            sizeBytes = mp3WithApicFile.length(),
            mimeType = "audio/mpeg",
            mediaType = MediaType.AUDIO,
            dateAddedEpochSec = 1_700_003_000L
        )

        val trackWithoutArt = MediaEntity(
            uriString = Uri.fromFile(wavWithoutArtFile).toString(),
            mediaStoreId = null,
            title = "Track Without Cover",
            artist = "PulsePlay Acoustic",
            album = "Unplugged",
            folderName = "Music",
            durationMs = 120_000L,
            sizeBytes = wavWithoutArtFile.length(),
            mimeType = "audio/mpeg",
            mediaType = MediaType.AUDIO,
            dateAddedEpochSec = 1_700_003_010L
        )

        val trackWithMetadataArt = MediaEntity(
            uriString = "file:///storage/emulated/0/Music/Notification_Metadata_Track.mp3",
            mediaStoreId = null,
            title = "Notification Metadata Track",
            artist = "PulsePlay Live",
            album = "Session Art",
            folderName = "Music",
            durationMs = 200_000L,
            sizeBytes = 4_096_000L,
            mimeType = "audio/mpeg",
            mediaType = MediaType.AUDIO,
            dateAddedEpochSec = 1_700_003_020L
        )

        // 1. Verify unified MediaArtworkLoader extracts ID3v2 APIC cover and populates MediaMetadata for both Notification & UI
        val resolvedApic = com.example.media.MediaArtworkLoader.resolveArtworkMetadata(app, trackWithArt)
        assertTrue("Embedded ID3v2 APIC artwork should be detected", resolvedApic.hasArtwork)
        assertNotNull(resolvedApic.artworkData)
        assertTrue(resolvedApic.artworkData!!.contentEquals(coverBytesTrack1))

        val notificationMediaMetadata = com.example.media.MediaArtworkLoader.buildMediaMetadataForEntity(app, trackWithArt)
        assertNotNull("MediaMetadata built for MediaSession notification must contain artworkData", notificationMediaMetadata.artworkData)
        assertTrue(notificationMediaMetadata.artworkData!!.contentEquals(coverBytesTrack1))

        val decodedBitmap = com.example.media.MediaArtworkLoader.loadArtworkBitmapBlocking(
            context = app,
            media = trackWithArt,
            artworkData = resolvedApic.artworkData,
            artworkUri = null,
            targetSizePx = 512
        )
        assertNotNull("Valid embedded artwork bytes must decode to a non-null Bitmap", decodedBitmap)

        // 2. Verify corrupt artwork bytes never crash and safely return null
        val corruptBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x7F)
        val corruptBitmap = com.example.media.MediaArtworkLoader.loadArtworkBitmapBlocking(
            context = app,
            media = trackWithoutArt,
            artworkData = corruptBytes,
            artworkUri = null,
            targetSizePx = 512
        )
        assertNull("Corrupt artwork bytes must safely return null without crashing", corruptBitmap)

        // 3. Verify ViewModel queue & track switching (Artwork Track -> No-Artwork Track -> Metadata Artwork Track)
        vm.setQueue(listOf(trackWithArt, trackWithoutArt, trackWithMetadataArt), initialIndex = 0)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNotNull("Active track with embedded art must populate currentArtworkData", vm.playbackState.value.currentArtworkData)

        // Switch to Next track (trackWithoutArt) -> stale artwork from previous track MUST be cleared immediately
        vm.skipToNext()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(trackWithoutArt.uriString, vm.playbackState.value.currentMedia?.uriString)
        assertNull("Switching to a track without artwork must clear previous track's artworkData", vm.playbackState.value.currentArtworkData)
        assertNull("Switching to a track without artwork must clear previous track's artworkUri", vm.playbackState.value.currentArtworkUri)

        // Switch to Next track (trackWithMetadataArt) and simulate ExoPlayer / Notification MediaMetadata update
        vm.skipToNext()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(trackWithMetadataArt.uriString, vm.playbackState.value.currentMedia?.uriString)

        val livePlayerMetadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(trackWithMetadataArt.title)
            .setArtist(trackWithMetadataArt.artist)
            .maybeSetArtworkData(coverBytesTrack2, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()
        vm.onPlayerMediaMetadataUpdated(livePlayerMetadata)
        assertNotNull("Live MediaMetadata.artworkData from ExoPlayer must update PlaybackUiState", vm.playbackState.value.currentArtworkData)
        assertTrue(vm.playbackState.value.currentArtworkData!!.contentEquals(coverBytesTrack2))

        // Switch Previous back to trackWithoutArt -> must not keep trackWithMetadataArt's photo
        vm.skipToPrevious()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(trackWithoutArt.uriString, vm.playbackState.value.currentMedia?.uriString)
        assertNull(vm.playbackState.value.currentArtworkData)

        // Switch Previous back to trackWithArt -> must restore trackWithArt's cover
        vm.skipToPrevious()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(trackWithArt.uriString, vm.playbackState.value.currentMedia?.uriString)
        assertNotNull(vm.playbackState.value.currentArtworkData)
        assertTrue(vm.playbackState.value.currentArtworkData!!.contentEquals(coverBytesTrack1))

        // 4. Verify Compose UI renders real album artwork in Now Playing & Mini-Player when available, and placeholder when absent
        var uiPlaybackState by mutableStateOf(
            com.example.viewmodel.PlaybackUiState(
                currentMedia = trackWithArt,
                queue = listOf(trackWithArt, trackWithoutArt),
                currentQueueIndex = 0,
                isPlaying = true,
                currentPositionMs = 15_000L,
                durationMs = trackWithArt.durationMs,
                currentArtworkData = coverBytesTrack1,
                currentArtworkSource = "EmbeddedPicture"
            )
        )

        composeTestRule.setContent {
            PulsePlayTheme {
                androidx.compose.foundation.layout.Column(modifier = Modifier.requiredSize(DpSize(360.dp, 640.dp))) {
                    com.example.ui.components.PersistentMiniPlayerBar(
                        playbackState = uiPlaybackState,
                        isSidePanelOpen = false,
                        showSidePanelToggle = false,
                        onExpandClick = {},
                        onPlayPauseClick = {},
                        onNextClick = {}
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        NowPlayingOverlayScreen(
                            playbackState = uiPlaybackState,
                            userPreferences = UserPreferencesState(),
                            isFavorite = false,
                            playerInstance = null,
                            onCollapse = {},
                            onPlayPause = {},
                            onSeekTo = {},
                            onPrevious = {},
                            onNext = {},
                            onToggleShuffle = {},
                            onCycleRepeat = {},
                            onSetPlaybackSpeed = {},
                            onCycleAspectMode = {},
                            onRotateVideo90 = {},
                            onToggleFullscreen = {},
                            onBrightnessGestureDelta = {},
                            onVolumeGestureDelta = {},
                            onPickSubtitleFileClick = {},
                            onToggleSubtitlesEnabled = {},
                            onSetSubtitleFontScale = {},
                            onSetSubtitleOffsetMs = {},
                            onToggleFavorite = {},
                            onOpenEqualizer = {},
                            onOpenStudio = {},
                            onSelectQueueItem = {},
                            onRemoveQueueItem = {},
                            onRetryPlayback = {}
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        // When artwork is available: real album artwork image is shown in both Now Playing and Mini-Player (no generic MPEG disc placeholder)
        composeTestRule.onNodeWithTag("now_playing_album_artwork_image").assertExists()
        composeTestRule.onNodeWithTag("mini_player_artwork_image", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("now_playing_default_artwork_placeholder").assertDoesNotExist()

        // Switch UI state to trackWithoutArt -> default visualizer placeholder is shown and stale album artwork image is gone
        uiPlaybackState = com.example.viewmodel.PlaybackUiState(
            currentMedia = trackWithoutArt,
            queue = listOf(trackWithArt, trackWithoutArt),
            currentQueueIndex = 1,
            isPlaying = true,
            currentPositionMs = 5_000L,
            durationMs = trackWithoutArt.durationMs,
            currentArtworkData = null,
            currentArtworkUri = null
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("now_playing_default_artwork_placeholder").assertExists()
        composeTestRule.onNodeWithTag("now_playing_album_artwork_image").assertDoesNotExist()
        composeTestRule.onNodeWithTag("mini_player_artwork_image", useUnmergedTree = true).assertDoesNotExist()

        mp3WithApicFile.delete()
        wavWithoutArtFile.delete()
    }

    @Test
    fun landscapeHome_showsOnlySingleNowPlayingPanelAndAllowsSettingsAndTransportControls() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = PulsePlayViewModel(app)

        val trackA = MediaEntity(
            uriString = "file:///landscape_single_panel_a.wav",
            mediaStoreId = null,
            title = "Aurora Pulse",
            artist = "PulsePlay Synth",
            album = "Horizon Sessions",
            folderName = "Music",
            durationMs = 210_000L,
            sizeBytes = 4096L,
            mimeType = "audio/wav",
            mediaType = MediaType.AUDIO,
            dateAddedEpochSec = 1700002000L
        )
        val trackB = trackA.copy(
            uriString = "file:///landscape_single_panel_b.wav",
            title = "Midnight Echo"
        )
        vm.setQueue(listOf(trackA, trackB), initialIndex = 0)

        var viewportSize by mutableStateOf(DpSize(800.dp, 360.dp))

        composeTestRule.setContent {
            PulsePlayTheme {
                Box(modifier = Modifier.requiredSize(viewportSize.width, viewportSize.height)) {
                    PulsePlayAppRoot(viewModel = vm)
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. In Landscape Home screen (800dp x 360dp), only the right-side NowPlayingSidePanel is shown;
        // the bottom PersistentMiniPlayerBar is NOT rendered (no duplicate player).
        composeTestRule.onNodeWithTag("landscape_now_playing_panel").assertExists()
        assertEquals(0, composeTestRule.onAllNodesWithTag("persistent_mini_player").fetchSemanticsNodes().size)

        // 2. Verify all 5 NavigationRail items (Home, Videos, Music, Library, Settings) exist and are clickable
        composeTestRule.onNodeWithTag("nav_tab_home").assertExists().assertHasClickAction()
        composeTestRule.onNodeWithTag("nav_tab_videos").assertExists().assertHasClickAction()
        composeTestRule.onNodeWithTag("nav_tab_music").assertExists().assertHasClickAction()
        composeTestRule.onNodeWithTag("nav_tab_library").assertExists().assertHasClickAction()
        composeTestRule.onNodeWithTag("nav_tab_settings").assertExists().assertHasClickAction()

        // 3. Verify right-side NowPlayingSidePanel has transport controls and they operate playback state
        composeTestRule.onNodeWithTag("side_panel_play_pause_button").assertExists()
        composeTestRule.onNodeWithTag("side_panel_previous_button").assertExists()
        composeTestRule.onNodeWithTag("side_panel_next_button").assertExists()
        composeTestRule.onNodeWithTag("side_panel_shuffle_button").assertExists()
        composeTestRule.onNodeWithTag("side_panel_repeat_button").assertExists()
        composeTestRule.onNodeWithTag("side_panel_seek_slider").assertExists()

        // Trigger Next and Previous in right-side Now Playing panel
        assertEquals("Aurora Pulse", vm.playbackState.value.currentMedia?.title)
        composeTestRule.onNodeWithTag("side_panel_next_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals("Midnight Echo", vm.playbackState.value.currentMedia?.title)

        composeTestRule.onNodeWithTag("side_panel_previous_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals("Aurora Pulse", vm.playbackState.value.currentMedia?.title)

        // 4. Click Settings icon in NavigationRail -> opens Settings screen cleanly without interception
        composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
        composeTestRule.waitForIdle()
        assertEquals(PrimaryTab.SETTINGS, vm.selectedTab.value)
        composeTestRule.onNodeWithTag("settings_screen_list").assertExists()

        // 5. Switch to Portrait (390dp x 844dp) -> PersistentMiniPlayerBar is shown and side panel is hidden
        viewportSize = DpSize(390.dp, 844.dp)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("persistent_mini_player").assertExists()
        assertEquals(0, composeTestRule.onAllNodesWithTag("landscape_now_playing_panel").fetchSemanticsNodes().size)
    }

    private fun createTestAlbumCoverPngBytes(color: Int): ByteArray {
        val bmp = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private fun writeTestMp3WithId3ApicCover(targetFile: File, imageBytes: ByteArray) {
        val mimeBytes = "image/png".toByteArray(Charsets.ISO_8859_1)
        // APIC payload: encoding(1=0) + mime + 0x00 + picType(1=3 front cover) + desc(0x00) + imageBytes
        val framePayloadSize = 1 + mimeBytes.size + 1 + 1 + 1 + imageBytes.size
        val totalTagPayloadSize = 10 + framePayloadSize // 10-byte APIC frame header + payload
        val out = java.io.ByteArrayOutputStream()
        // ID3v2.3 header (10 bytes)
        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0))
        // Synchsafe 28-bit tag size
        out.write((totalTagPayloadSize shr 21) and 0x7F)
        out.write((totalTagPayloadSize shr 14) and 0x7F)
        out.write((totalTagPayloadSize shr 7) and 0x7F)
        out.write(totalTagPayloadSize and 0x7F)

        // APIC frame header (10 bytes for ID3v2.3: 4-byte ID + 4-byte big-endian size + 2-byte flags)
        out.write("APIC".toByteArray(Charsets.ISO_8859_1))
        out.write((framePayloadSize shr 24) and 0xFF)
        out.write((framePayloadSize shr 16) and 0xFF)
        out.write((framePayloadSize shr 8) and 0xFF)
        out.write(framePayloadSize and 0xFF)
        out.write(0)
        out.write(0)

        // APIC frame payload
        out.write(0) // ISO-8859-1 encoding
        out.write(mimeBytes)
        out.write(0) // null terminator for MIME
        out.write(3) // Front Cover picture type
        out.write(0) // empty description null terminator
        out.write(imageBytes)

        // Minimal dummy MPEG frame bytes so file size > 64 bytes
        out.write(ByteArray(128) { 0xFF.toByte() })
        targetFile.writeBytes(out.toByteArray())
    }

    @Test
    fun testAudioAndVideoMediaSwitchDoesNotShowExplanatorySubtitleTextWhilePreservingRealSubtitles() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.viewmodel.PulsePlayViewModel(app)

        val audioFile1 = File(app.cacheDir, "track_one_clean.mp3").apply {
            writeBytes(ByteArray(256) { 0x55 })
        }
        val audioFile2 = File(app.cacheDir, "track_two_clean.mp3").apply {
            writeBytes(ByteArray(256) { 0x66 })
        }
        val videoFile1 = File(app.cacheDir, "clip_one_clean.mp4").apply {
            writeBytes(ByteArray(512) { 0x77 })
        }

        val song1 = MediaEntity(
            uriString = Uri.fromFile(audioFile1).toString(),
            mediaStoreId = null,
            title = "Clean Song One",
            artist = "Studio Artist",
            album = "Clean Album",
            folderName = "Music",
            durationMs = 180_000L,
            sizeBytes = audioFile1.length(),
            mimeType = "audio/mpeg",
            mediaType = MediaType.AUDIO,
            dateAddedEpochSec = 1710000100L
        )
        val song2 = song1.copy(
            uriString = Uri.fromFile(audioFile2).toString(),
            title = "Clean Song Two"
        )
        val video1 = MediaEntity(
            uriString = Uri.fromFile(videoFile1).toString(),
            mediaStoreId = null,
            title = "Clean Video One",
            artist = "",
            album = "Clips",
            folderName = "Videos",
            durationMs = 60_000L,
            sizeBytes = videoFile1.length(),
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            width = 1280,
            height = 720,
            dateAddedEpochSec = 1710000200L
        )

        // 1. Play Audio Song 1 -> no explanatory subtitle text or auto-loaded sample cues
        vm.setQueue(listOf(song1, song2, video1), initialIndex = 0)
        vm.seekTo(2_500L)
        assertNull("Audio track should not auto-load explanatory subtitle text", vm.playbackState.value.activeSubtitleText)
        assertTrue("Audio track should have empty subtitle cues by default", vm.playbackState.value.loadedSubtitleCues.isEmpty())

        // 2. Skip to Next (Audio Song 2) -> still no explanatory subtitle text
        vm.skipToNext()
        vm.seekTo(2_500L)
        assertEquals("Clean Song Two", vm.playbackState.value.currentMedia?.title)
        assertNull("Next audio track must not show explanatory subtitle text", vm.playbackState.value.activeSubtitleText)

        // 3. Explicitly load a real user SRT subtitle file and verify real subtitle functionality works
        val customSrt = File(app.cacheDir, "user_lyrics.srt").apply {
            writeText(
                """
                1
                00:00:01,000 --> 00:00:05,000
                Real user synchronized lyric line
                """.trimIndent()
            )
        }
        vm.loadSubtitleFromFile(customSrt)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        vm.seekTo(2_500L)
        assertEquals("Real user synchronized lyric line", vm.playbackState.value.activeSubtitleText)

        // Verify sync offset calculation with SubtitleParser remains functional
        val shiftedCue = com.example.media.SubtitleParser.findActiveCueText(
            cues = vm.playbackState.value.loadedSubtitleCues,
            playbackPositionMs = 500L,
            syncOffsetMs = -1_000L
        )
        assertEquals("Real user synchronized lyric line", shiftedCue)

        // 4. Switch to Video 1 -> previous track's subtitle cues are cleared and no explanatory text appears
        vm.skipToNext()
        vm.seekTo(2_500L)
        assertEquals("Clean Video One", vm.playbackState.value.currentMedia?.title)
        assertNull("Switching to video track must clear previous subtitle text and not show explanatory text", vm.playbackState.value.activeSubtitleText)
        assertTrue("Switching tracks must clear previous subtitle cues", vm.playbackState.value.loadedSubtitleCues.isEmpty())

        // 5. Render NowPlayingOverlayScreen for Audio and verify active_lyrics_banner is not shown by default
        vm.setQueue(listOf(song1, song2), initialIndex = 0)
        composeTestRule.setContent {
            PulsePlayTheme {
                NowPlayingOverlayScreen(
                    playbackState = vm.playbackState.value,
                    userPreferences = UserPreferencesState(subtitlesEnabledByDefault = true),
                    isFavorite = false,
                    playerInstance = null,
                    onCollapse = {},
                    onPlayPause = {},
                    onSeekTo = {},
                    onPrevious = {},
                    onNext = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onSetPlaybackSpeed = {},
                    onCycleAspectMode = {},
                    onRotateVideo90 = {},
                    onToggleFullscreen = {},
                    onSelectVideoOrientation = {},
                    onBrightnessGestureDelta = {},
                    onVolumeGestureDelta = {},
                    onPickSubtitleFileClick = {},
                    onToggleSubtitlesEnabled = {},
                    onSetSubtitleFontScale = {},
                    onSetSubtitleOffsetMs = {},
                    onToggleFavorite = {},
                    onOpenEqualizer = {},
                    onOpenStudio = {},
                    onSelectQueueItem = {},
                    onRemoveQueueItem = {},
                    onRetryPlayback = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodesWithTag("active_lyrics_banner").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("active_subtitle_overlay").fetchSemanticsNodes().size)

        audioFile1.delete()
        audioFile2.delete()
        videoFile1.delete()
        customSrt.delete()
    }

    @Test
    fun `githubAppUpdateManager parsesReleases comparesSemanticVersions verifiesSha256AndApkAndHandlesConfigRequired`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val updateManager = com.example.update.AppUpdateManager(app)

        // 1. Semantic version comparisons (v-prefix, multi-part, pre-release vs stable)
        assertTrue(com.example.update.AppUpdateManager.compareSemanticVersions("1.3.0", "1.2.9") > 0)
        assertTrue(com.example.update.AppUpdateManager.compareSemanticVersions("v2.0.0", "1.9.9") > 0)
        assertEquals(0, com.example.update.AppUpdateManager.compareSemanticVersions("v1.2.0", "1.2.0"))
        assertTrue(
            "Stable release 1.2.0 must rank higher than pre-release 1.2.0-beta.1",
            com.example.update.AppUpdateManager.compareSemanticVersions("1.2.0", "1.2.0-beta.1") > 0
        )
        assertTrue(
            com.example.update.AppUpdateManager.isRemoteReleaseNewer(
                remoteVersionName = "1.3.0",
                remoteVersionCode = 13L,
                installedVersionName = "1.2.0",
                installedVersionCode = 12L
            )
        )
        assertFalse(
            "Same version must not be flagged as newer",
            com.example.update.AppUpdateManager.isRemoteReleaseNewer(
                remoteVersionName = "1.2.0",
                remoteVersionCode = 12L,
                installedVersionName = "1.2.0",
                installedVersionCode = 12L
            )
        )
        assertFalse(
            "Older version must never trigger downgrade",
            com.example.update.AppUpdateManager.isRemoteReleaseNewer(
                remoteVersionName = "1.1.0",
                remoteVersionCode = 11L,
                installedVersionName = "1.2.0",
                installedVersionCode = 12L
            )
        )

        // 2. Parse GitHub Release JSON with APK asset, SHA256SUMS.txt asset, and embedded SHA-256 in body
        val sampleSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val releaseJson = org.json.JSONObject().apply {
            put("tag_name", "v1.4.0")
            put("name", "PulsePlay v1.4.0")
            put("body", "### What's New\n- versionCode: 14\n- Checksum: $sampleSha256  PulsePlay-v1.4.0-release.apk")
            put("prerelease", false)
            put("draft", false)
            put("published_at", "2026-10-26T00:00:00Z")
            put("html_url", "https://github.com/example-org/pulseplay/releases/tag/v1.4.0")
            val assetsArray = org.json.JSONArray()
            assetsArray.put(
                org.json.JSONObject().apply {
                    put("name", "PulsePlay-v1.4.0-release.apk")
                    put("browser_download_url", "https://github.com/example-org/pulseplay/releases/download/v1.4.0/PulsePlay-v1.4.0-release.apk")
                    put("size", 12_582_912L)
                    put("content_type", "application/vnd.android.package-archive")
                }
            )
            assetsArray.put(
                org.json.JSONObject().apply {
                    put("name", "SHA256SUMS.txt")
                    put("browser_download_url", "https://github.com/example-org/pulseplay/releases/download/v1.4.0/SHA256SUMS.txt")
                    put("size", 128L)
                    put("content_type", "text/plain")
                }
            )
            put("assets", assetsArray)
        }

        val parsedRelease = com.example.update.AppUpdateManager.parseGitHubReleaseJson(releaseJson)
        assertNotNull(parsedRelease)
        assertEquals("v1.4.0", parsedRelease!!.tagName)
        assertEquals("1.4.0", parsedRelease.versionName)
        assertEquals(14L, parsedRelease.remoteVersionCode)
        assertEquals("PulsePlay-v1.4.0-release.apk", parsedRelease.apkAsset.name)
        assertNotNull(parsedRelease.checksumAsset)
        assertEquals(sampleSha256, parsedRelease.expectedSha256)

        // 3. Verify SHA-256 computation and checksum mismatch detection
        val dummyApkFile = File(app.cacheDir, "dummy_update.apk").apply {
            writeBytes(ByteArray(256) { 0x42 })
        }
        val computedHash = com.example.update.AppUpdateManager.computeFileSha256Hex(dummyApkFile)
        assertEquals(64, computedHash.length)

        val mismatchVerification = updateManager.verifyDownloadedApk(
            apkFile = dummyApkFile,
            expectedSha256Hex = "0000000000000000000000000000000000000000000000000000000000000000"
        )
        assertFalse(
            "Mismatched SHA-256 must fail verification before package installer is ever invoked",
            mismatchVerification.isValid
        )
        assertTrue(
            mismatchVerification.errorReason.orEmpty().contains("SHA-256 checksum mismatch")
        )

        val invalidArchiveVerification = updateManager.verifyDownloadedApk(
            apkFile = dummyApkFile,
            expectedSha256Hex = computedHash
        )
        assertFalse(
            "Corrupt/unparseable APK file must fail PackageManager archive inspection even if hash matches",
            invalidArchiveVerification.isValid
        )
        dummyApkFile.delete()

        // 4. Empty repository slug must emit ConfigurationRequired (never a hardcoded fake owner/repo)
        val unconfiguredState = updateManager.checkForUpdates(
            configuredRepositorySlug = "",
            includePrereleases = false
        )
        assertTrue(
            "Blank repository slug must return ConfigurationRequired",
            unconfiguredState is com.example.update.UpdateCheckState.ConfigurationRequired
        )
    }

    @Test
    fun `studioEditorUndoRedoHistory orphanedTempCleanup videoPipAndSettingsUpdateUiWorkEndToEnd`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        // 1. Test orphaned temp file cleanup in MediaProcessorEngine
        val engine = MediaProcessorEngine(app, database.pulseDao())
        val tmpDir = File(app.cacheDir, "pulse_processing_tmp").apply { mkdirs() }
        val staleTemp = File(tmpDir, "trim_tmp_999999.wav").apply {
            writeBytes(ByteArray(128) { 0x11 })
        }
        assertTrue(staleTemp.exists())
        val deletedCount = engine.cleanupOrphanedTempFiles()
        assertTrue("cleanupOrphanedTempFiles must remove orphaned temp files in pulse_processing_tmp", deletedCount >= 1)
        assertFalse(staleTemp.exists())

        // 2. Test EditorUndoRedoHistory parameter stack
        val initialSnap = com.example.ui.screens.EditorParameterSnapshot(
            mode = com.example.ui.screens.StudioMode.AUDIO_TRIM,
            trimStartFraction = 0.0f,
            trimEndFraction = 1.0f,
            audioTrimFormat = "wav",
            videoRotationDegrees = 0,
            selectedProfileId = "WAV_PCM_16BIT"
        )
        val history = com.example.ui.screens.EditorUndoRedoHistory(initialSnap)
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)

        val editedSnap1 = initialSnap.copy(trimStartFraction = 0.2f, trimEndFraction = 0.8f)
        val editedSnap2 = editedSnap1.copy(audioTrimFormat = "m4a", videoRotationDegrees = 90)
        history.recordChange(editedSnap1)
        assertTrue(history.canUndo)
        history.recordChange(editedSnap2)

        val undone1 = history.undo()
        assertEquals(editedSnap1, undone1)
        assertTrue(history.canRedo)

        val undone2 = history.undo()
        assertEquals(initialSnap, undone2)
        assertFalse(history.canUndo)

        val redone1 = history.redo()
        assertEquals(editedSnap1, redone1)

        // 3. Verify Studio UI Undo/Redo buttons and Settings GitHub Update UI + Video PiP chip
        val sampleVideo = MediaEntity(
            uriString = "file:///storage/emulated/0/Movies/pip_test_clip.mp4",
            mediaStoreId = null,
            title = "PiP Test Clip",
            artist = "PulsePlay",
            album = "Videos",
            folderName = "Movies",
            durationMs = 60_000L,
            sizeBytes = 4_096_000L,
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            width = 1920,
            height = 1080,
            dateAddedEpochSec = 1710001000L
        )

        var pipClicked = false
        var checkUpdatesClicked = false
        var activeView by mutableStateOf("VIDEO")

        composeTestRule.setContent {
            PulsePlayTheme {
                Box(modifier = Modifier.requiredSize(DpSize(800.dp, 2400.dp))) {
                    when (activeView) {
                        "VIDEO" -> {
                            NowPlayingOverlayScreen(
                                playbackState = com.example.viewmodel.PlaybackUiState(
                                    currentMedia = sampleVideo,
                                    queue = listOf(sampleVideo),
                                    currentQueueIndex = 0,
                                    isPlaying = true,
                                    currentPositionMs = 10_000L,
                                    durationMs = sampleVideo.durationMs
                                ),
                                userPreferences = UserPreferencesState(),
                                isFavorite = false,
                                playerInstance = null,
                                onCollapse = {},
                                onPlayPause = {},
                                onSeekTo = {},
                                onPrevious = {},
                                onNext = {},
                                onToggleShuffle = {},
                                onCycleRepeat = {},
                                onSetPlaybackSpeed = {},
                                onCycleAspectMode = {},
                                onRotateVideo90 = {},
                                onToggleFullscreen = {},
                                onEnterPipClick = { pipClicked = true },
                                onBrightnessGestureDelta = {},
                                onVolumeGestureDelta = {},
                                onPickSubtitleFileClick = {},
                                onToggleSubtitlesEnabled = {},
                                onSetSubtitleFontScale = {},
                                onSetSubtitleOffsetMs = {},
                                onToggleFavorite = {},
                                onOpenEqualizer = {},
                                onOpenStudio = {},
                                onSelectQueueItem = {},
                                onRemoveQueueItem = {},
                                onRetryPlayback = {}
                            )
                        }
                        "STUDIO" -> {
                            com.example.ui.screens.StudioEditorOverlayScreen(
                                allMedia = listOf(sampleVideo),
                                initialTargetMedia = sampleVideo,
                                conversionProfiles = ConversionCapabilityRegistry.getSupportedProfiles(),
                                processingJobs = emptyList(),
                                onClose = {},
                                onPreviewSegment = { _, _ -> },
                                onRunAudioTrim = { _, _, _, _ -> },
                                onRunVideoTrimRotate = { _, _, _, _, _ -> },
                                onRunConversionOrExtraction = { _, _, _, _ -> },
                                onCancelActiveJob = {},
                                onClearCompletedJobs = {},
                                onPlayExportedUri = {}
                            )
                        }
                        "SETTINGS" -> {
                            com.example.ui.screens.ProfileAndSettingsScreen(
                                userPreferences = UserPreferencesState(
                                    githubRepositorySlug = "example-owner/pulseplay"
                                ),
                                hasMediaPermission = true,
                                historyCount = 2,
                                updateState = com.example.update.UpdateCheckState.UpToDate(
                                    installedVersionName = "1.2.0",
                                    installedVersionCode = 3L,
                                    latestRemoteTag = "v1.2.0"
                                ),
                                onSetThemeMode = {},
                                onSetDynamicColor = {},
                                onSetDefaultSpeed = {},
                                onSetResumePlayback = {},
                                onSetRecordHistory = {},
                                onSetBackgroundPlayback = {},
                                onSetKeepScreenOnDuringVideo = {},
                                onSetVideoGestureControls = {},
                                onSetVideoOrientationPref = {},
                                onSetAllowExperimental4kSoftwareFallback = {},
                                onSetAudioBalance = {},
                                onSetPreampGainDb = {},
                                onSetSleepTimerMinutes = {},
                                onSetSleepTimerEndOfTrack = {},
                                onSetSubtitlesEnabled = {},
                                onSetSubtitleScale = {},
                                onSetDefaultSort = {},
                                onSetGitHubRepositorySlug = {},
                                onSetIncludePrereleaseUpdates = {},
                                onCheckForUpdatesClick = { checkUpdatesClicked = true },
                                onDownloadUpdateClick = {},
                                onCancelUpdateDownloadClick = {},
                                onInstallVerifiedUpdateClick = {},
                                onOpenEqualizerClick = {},
                                onRequestMediaPermissionClick = {},
                                onSetOptionalAdsConfig = { _, _ -> },
                                onClearHistoryOnlyClick = {},
                                onResetPreferencesClick = {}
                            )
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        // Verify PiP button in Video Player HUD
        composeTestRule.onNodeWithTag("video_pip_chip")
            .assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertTrue("PiP button should invoke onEnterPipClick", pipClicked)

        // Switch to Studio Editor and verify Undo/Redo buttons exist
        activeView = "STUDIO"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_undo_button").assertExists()
        composeTestRule.onNodeWithTag("studio_redo_button").assertExists()

        // Switch to Settings and verify GitHub Update controls exist
        activeView = "SETTINGS"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen_list")
            .performSemanticsAction(SemanticsActions.ScrollToIndex) { it(7) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("installed_version_label").assertExists()
        composeTestRule.onNodeWithTag("github_repo_slug_input").assertExists()
        composeTestRule.onNodeWithTag("check_for_updates_button")
            .assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertTrue("Check for Updates button must trigger callback", checkUpdatesClicked)
    }

    @Test
    fun `dualUpdateDistribution gitHubAndPlayStoreProviders sourcePolicy signingProtectionAndDataPreservationWorkEndToEnd`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()

        // 1. UpdateSourcePolicy multi-signal evaluation:
        //    a) Verified Google Play installation (com.android.vending)
        val playSignals = com.example.update.InstallSourceSignals(
            installingPackageName = "com.android.vending",
            initiatingPackageName = "com.android.vending",
            originatingPackageName = null,
            installedSigningCertSha256Set = setOf("aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"),
            isPlayStoreAppInstalledOnDevice = true
        )
        val playEvaluation = com.example.update.UpdateSourcePolicy.evaluateSignals(playSignals)
        assertEquals(com.example.update.InstallChannel.GOOGLE_PLAY, playEvaluation.detectedChannel)
        assertEquals(com.example.update.UpdateDistributionChannel.GOOGLE_PLAY, playEvaluation.recommendedChannel)
        assertFalse("Play-installed app must not auto-install GitHub APKs", playEvaluation.canAutoInstallGitHubApk)
        assertTrue("Play-installed app must block GitHub APK overwrite", playEvaluation.blockGitHubApkOverwriteOfPlayInstall)

        //    b) Spoofed initiatingPackageName where installingPackageName is packageinstaller must NOT be treated as Google Play
        val spoofedSignals = com.example.update.InstallSourceSignals(
            installingPackageName = "com.google.android.packageinstaller",
            initiatingPackageName = "com.android.vending",
            originatingPackageName = "com.example.browser",
            installedSigningCertSha256Set = setOf("11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff"),
            isPlayStoreAppInstalledOnDevice = true
        )
        val spoofedEval = com.example.update.UpdateSourcePolicy.evaluateSignals(spoofedSignals)
        assertEquals(com.example.update.InstallChannel.GITHUB_OR_SIDELOAD, spoofedEval.detectedChannel)

        //    c) Unknown / ambiguous installation source offers both channels safely without assuming source
        val unknownSignals = com.example.update.InstallSourceSignals(
            installingPackageName = null,
            initiatingPackageName = null,
            originatingPackageName = null,
            installedSigningCertSha256Set = setOf("11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff"),
            isPlayStoreAppInstalledOnDevice = false
        )
        val unknownEval = com.example.update.UpdateSourcePolicy.evaluateSignals(unknownSignals)
        assertEquals(com.example.update.InstallChannel.UNKNOWN, unknownEval.detectedChannel)
        assertTrue(unknownEval.allowedChannels.contains(com.example.update.UpdateDistributionChannel.GITHUB_RELEASES))
        assertTrue(unknownEval.allowedChannels.contains(com.example.update.UpdateDistributionChannel.GOOGLE_PLAY))

        // 2. Prevent a GitHub update from replacing a Play-installed app & reject incompatible signing certificates
        val blockedPlayCrossUpdate = com.example.update.UpdateSourcePolicy.validateGitHubApkInstallAttempt(
            evaluation = playEvaluation,
            installedVersionCode = 10L,
            candidateVersionCode = 11L,
            candidateSigningCertSha256Set = playSignals.installedSigningCertSha256Set
        )
        assertFalse("GitHub APK must never replace a Play-installed app", blockedPlayCrossUpdate.isAllowed)
        assertTrue(blockedPlayCrossUpdate.reason.contains("Blocked by UpdateSourcePolicy"))

        val mismatchedCertValidation = com.example.update.UpdateSourcePolicy.validateGitHubApkInstallAttempt(
            evaluation = spoofedEval,
            installedVersionCode = 10L,
            candidateVersionCode = 11L,
            candidateSigningCertSha256Set = setOf("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
        )
        assertFalse("Incompatible signing certificate must be rejected before package installer", mismatchedCertValidation.isAllowed)
        assertTrue(mismatchedCertValidation.requiresManualMigration)
        assertTrue(mismatchedCertValidation.reason.contains("Incompatible signing certificate"))
        assertNotNull(mismatchedCertValidation.migrationInstructions)

        // 3. Monotonic versionCode ordering across channels
        assertTrue(com.example.update.UpdateSourcePolicy.validateMonotonicVersionCode(10L, 11L))
        assertFalse(com.example.update.UpdateSourcePolicy.validateMonotonicVersionCode(10L, 10L))
        assertFalse(com.example.update.UpdateSourcePolicy.validateMonotonicVersionCode(10L, 9L))

        // 4. Official Google Play In-App Updates provider tested against Play Core's FakeAppUpdateManager:
        //    - No update available handling
        //    - Flexible update availability, background download progress, DownloadedReadyToComplete, and completeUpdate()
        //    - High-priority Immediate update availability and resume on interrupted update
        val fakePlayCore = com.google.android.play.core.appupdate.testing.FakeAppUpdateManager(app)
        val playProvider = com.example.update.PlayStoreUpdateProvider(
            context = app,
            playCoreManagerProvider = { fakePlayCore }
        )

        // 4a. Play update unavailable / not published
        fakePlayCore.setUpdateNotAvailable()
        val noUpdateState = playProvider.checkForPlayStoreUpdate()
        assertTrue(
            "When no update is available on Play Store, provider must return NoUpdateAvailable (never fake success)",
            noUpdateState is com.example.update.PlayStoreUpdateState.NoUpdateAvailable
        )

        // 4b. Flexible Play update flow + background download progress + completion confirmation
        val targetPlayVersionCode = BuildConfig.VERSION_CODE + 15
        fakePlayCore.setUpdateAvailable(targetPlayVersionCode, com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE)
        fakePlayCore.setUpdatePriority(2)
        fakePlayCore.setClientVersionStalenessDays(3)
        val availState = playProvider.checkForPlayStoreUpdate()
        assertTrue(availState is com.example.update.PlayStoreUpdateState.UpdateAvailable)
        val avail = availState as com.example.update.PlayStoreUpdateState.UpdateAvailable
        assertEquals(targetPlayVersionCode, avail.availableVersionCode)
        assertTrue(avail.isFlexibleAllowed)
        assertEquals(com.google.android.play.core.install.model.AppUpdateType.FLEXIBLE, avail.recommendedUpdateType)

        // Simulate background download progress via InstallStateUpdatedListener
        fakePlayCore.setTotalBytesToDownload(10_000_000L)
        fakePlayCore.setBytesDownloaded(4_500_000L)
        playProvider.handleInstallStateUpdate(
            com.google.android.play.core.install.InstallState.zza(
                com.google.android.play.core.install.model.InstallStatus.DOWNLOADING,
                4_500_000L,
                10_000_000L,
                com.google.android.play.core.install.model.InstallErrorCode.NO_ERROR,
                app.packageName
            )
        )
        val downloadingState = playProvider.state.value
        assertTrue(downloadingState is com.example.update.PlayStoreUpdateState.FlexibleDownloading)
        assertEquals(45, (downloadingState as com.example.update.PlayStoreUpdateState.FlexibleDownloading).progressPercent)

        // Simulate background download finishing -> DownloadedReadyToComplete
        playProvider.handleInstallStateUpdate(
            com.google.android.play.core.install.InstallState.zza(
                com.google.android.play.core.install.model.InstallStatus.DOWNLOADED,
                10_000_000L,
                10_000_000L,
                com.google.android.play.core.install.model.InstallErrorCode.NO_ERROR,
                app.packageName
            )
        )
        assertTrue(playProvider.state.value is com.example.update.PlayStoreUpdateState.DownloadedReadyToComplete)

        // 4c. High-priority Immediate update recommendation (priority >= 4)
        val criticalVersionCode = BuildConfig.VERSION_CODE + 25
        fakePlayCore.setUpdateAvailable(criticalVersionCode, com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE)
        fakePlayCore.setUpdatePriority(5)
        val immediateAvail = playProvider.checkForPlayStoreUpdate()
        assertTrue(immediateAvail is com.example.update.PlayStoreUpdateState.UpdateAvailable)
        val imm = immediateAvail as com.example.update.PlayStoreUpdateState.UpdateAvailable
        assertTrue(imm.isImmediateAllowed)
        assertEquals(com.google.android.play.core.install.model.AppUpdateType.IMMEDIATE, imm.recommendedUpdateType)

        // 5. Shared UpdateManager enforces Play-install protection & preserves Room DB + DataStore preferences
        val dao = database.pulseDao()
        val prefsRepo = UserPreferencesRepository(app)
        prefsRepo.setGitHubRepositorySlug("official-org/pulseplay")
        val playlistId = dao.insertPlaylist(
            com.example.data.local.PlaylistEntity(
                name = "Pre-Update Playlist",
                description = "Must survive update checks and in-place upgrades"
            )
        )

        val sharedUpdateManager = com.example.update.UpdateManager(
            context = app,
            playStoreProvider = playProvider
        )
        // Simulate Google Play installation in UpdateManager
        sharedUpdateManager.refreshInstallSourceEvaluation(playSignals)
        assertFalse(
            "Selecting GitHub channel must be rejected when app is installed from Google Play",
            sharedUpdateManager.selectChannel(com.example.update.UpdateDistributionChannel.GITHUB_RELEASES)
        )
        val blockedGhCheck = sharedUpdateManager.checkGitHubUpdates("official-org/pulseplay")
        assertTrue(blockedGhCheck is com.example.update.UpdateCheckState.VerificationFailed)

        // Verify Room playlists and DataStore preferences are 100% intact
        val playlistsAfterCheck = dao.observePlaylistsWithCount().first()
        assertTrue(playlistsAfterCheck.any { it.playlistId == playlistId && it.name == "Pre-Update Playlist" })
        assertEquals("official-org/pulseplay", prefsRepo.preferencesFlow.first().githubRepositorySlug)

        // 6. Verify UpdateSettingsScreen UI renders ONLY the selected provider panel at a time
        var playCheckClicked = false
        var flexibleClicked = false
        composeTestRule.setContent {
            PulsePlayTheme {
                Box(modifier = Modifier.requiredSize(DpSize(800.dp, 2400.dp))) {
                    com.example.update.UpdateSettingsScreen(
                        installedVersionName = "1.2.0",
                        installedVersionCode = 12L,
                        githubRepositorySlug = "official-org/pulseplay",
                        includePrereleaseUpdates = false,
                        updateState = com.example.update.UpdateCheckState.Idle,
                        playStoreState = avail,
                        sourceEvaluation = playEvaluation,
                        activeChannel = com.example.update.UpdateDistributionChannel.GOOGLE_PLAY,
                        policyBlockMessage = sharedUpdateManager.lastPolicyBlockMessage.value,
                        onSetGitHubRepositorySlug = {},
                        onSetIncludePrereleaseUpdates = {},
                        onCheckForUpdatesClick = {},
                        onDownloadUpdateClick = {},
                        onCancelUpdateDownloadClick = {},
                        onInstallVerifiedUpdateClick = {},
                        onCheckPlayStoreUpdatesClick = { playCheckClicked = true },
                        onStartPlayFlexibleUpdateClick = { flexibleClicked = true }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("install_source_badge").assertExists()
        // While Google Play Store is selected, only Play Store controls exist and GitHub controls are hidden
        composeTestRule.onNodeWithTag("play_store_update_section").assertExists()
        composeTestRule.onNodeWithTag("github_update_section").assertDoesNotExist()
        composeTestRule.onNodeWithTag("check_play_store_updates_button")
            .assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithTag("play_flexible_update_button")
            .assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertTrue(playCheckClicked)
        assertTrue(flexibleClicked)

        // Switching to GitHub Releases panel displays only the GitHub panel + Play-install policy block banner
        composeTestRule.onNodeWithTag("update_channel_chip_github")
            .assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("github_update_section").assertExists()
        composeTestRule.onNodeWithTag("play_install_blocks_github_banner").assertExists()
        composeTestRule.onNodeWithTag("play_store_update_section").assertDoesNotExist()
    }

    @Test
    fun `updateSettingsScreen exclusiveProviderPanels repeatedSwitching reopenRestorationAndProviderActionsWorkCorrectly`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefsRepo = UserPreferencesRepository(app)
        val updateManager = com.example.update.UpdateManager(context = app)

        var gitHubCheckInvocations = 0
        var gitHubSaveRepoInvocations = 0
        var playCheckInvocations = 0
        var playListingInvocations = 0
        var activeChannel by androidx.compose.runtime.mutableStateOf(updateManager.activeChannel.value)
        var isSettingsScreenOpen by androidx.compose.runtime.mutableStateOf(true)

        composeTestRule.setContent {
            PulsePlayTheme {
                Box(modifier = Modifier.requiredSize(DpSize(800.dp, 2000.dp))) {
                    if (isSettingsScreenOpen) {
                        com.example.update.UpdateSettingsScreen(
                            installedVersionName = "1.2.0",
                            installedVersionCode = 12L,
                            githubRepositorySlug = "official-org/pulseplay",
                            includePrereleaseUpdates = false,
                            updateState = updateManager.gitHubState.value,
                            playStoreState = updateManager.playStoreState.value,
                            sourceEvaluation = updateManager.sourceEvaluation.value,
                            activeChannel = activeChannel,
                            policyBlockMessage = updateManager.lastPolicyBlockMessage.value,
                            onSelectChannel = { selected ->
                                updateManager.selectChannel(selected)
                                activeChannel = updateManager.activeChannel.value
                                kotlinx.coroutines.runBlocking {
                                    prefsRepo.setSelectedUpdateChannel(selected)
                                }
                            },
                            onSetGitHubRepositorySlug = { gitHubSaveRepoInvocations++ },
                            onSetIncludePrereleaseUpdates = {},
                            onCheckForUpdatesClick = { gitHubCheckInvocations++ },
                            onDownloadUpdateClick = {},
                            onCancelUpdateDownloadClick = {},
                            onInstallVerifiedUpdateClick = {},
                            onCheckPlayStoreUpdatesClick = { playCheckInvocations++ },
                            onOpenPlayStoreListingClick = { playListingInvocations++ }
                        )
                    } else {
                        androidx.compose.material3.Text(text = "Home Screen")
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Select GitHub: only GitHub update controls are visible; Google Play panel is completely absent
        composeTestRule.onNodeWithTag("update_channel_chip_github")
            .assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("github_update_section").assertExists()
        composeTestRule.onNodeWithTag("github_repo_slug_input").assertExists()
        composeTestRule.onNodeWithTag("include_prerelease_updates_switch").assertExists()
        composeTestRule.onNodeWithTag("save_github_repo_slug_button").assertExists()
        composeTestRule.onNodeWithTag("check_for_updates_button").assertExists()
        composeTestRule.onNodeWithTag("github_update_state_panel").assertExists()

        composeTestRule.onNodeWithTag("play_store_update_section").assertDoesNotExist()
        composeTestRule.onNodeWithTag("check_play_store_updates_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("open_play_store_listing_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("play_store_update_state_panel").assertDoesNotExist()

        // Verify GitHub update actions invoke only the GitHub provider
        composeTestRule.onNodeWithTag("save_github_repo_slug_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithTag("check_for_updates_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals(1, gitHubSaveRepoInvocations)
        assertEquals(1, gitHubCheckInvocations)
        assertEquals("GitHub actions must never invoke Play Store provider", 0, playCheckInvocations)

        // 2. Select Google Play Store: only Play update controls are visible; GitHub panel is completely absent
        composeTestRule.onNodeWithTag("update_channel_chip_play")
            .assertExists()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        // Switching providers must not trigger any update check automatically
        assertEquals(1, gitHubCheckInvocations)
        assertEquals(0, playCheckInvocations)

        composeTestRule.onNodeWithTag("play_store_update_section").assertExists()
        composeTestRule.onNodeWithTag("check_play_store_updates_button").assertExists()
        composeTestRule.onNodeWithTag("open_play_store_listing_button").assertExists()
        composeTestRule.onNodeWithTag("play_store_update_state_panel").assertExists()

        composeTestRule.onNodeWithTag("github_update_section").assertDoesNotExist()
        composeTestRule.onNodeWithTag("github_repo_slug_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("include_prerelease_updates_switch").assertDoesNotExist()
        composeTestRule.onNodeWithTag("save_github_repo_slug_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("check_for_updates_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("github_update_state_panel").assertDoesNotExist()

        // Verify Google Play update actions invoke only the Play Store provider
        composeTestRule.onNodeWithTag("check_play_store_updates_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithTag("open_play_store_listing_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        assertEquals(1, playCheckInvocations)
        assertEquals(1, playListingInvocations)
        assertEquals("Play Store actions must never invoke GitHub provider", 1, gitHubCheckInvocations)

        // 3. Switch between them repeatedly: exactly one provider panel remains visible at all times
        repeat(4) {
            composeTestRule.onNodeWithTag("update_channel_chip_github")
                .performSemanticsAction(SemanticsActions.OnClick)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("github_update_section").assertExists()
            composeTestRule.onNodeWithTag("play_store_update_section").assertDoesNotExist()

            composeTestRule.onNodeWithTag("update_channel_chip_play")
                .performSemanticsAction(SemanticsActions.OnClick)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("play_store_update_section").assertExists()
            composeTestRule.onNodeWithTag("github_update_section").assertDoesNotExist()
        }

        // 4. Close Settings and reopen Settings: the previously selected provider (Google Play Store) is restored
        isSettingsScreenOpen = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("update_settings_screen_card").assertDoesNotExist()

        isSettingsScreenOpen = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("update_settings_screen_card").assertExists()
        composeTestRule.onNodeWithTag("play_store_update_section").assertExists()
        composeTestRule.onNodeWithTag("github_update_section").assertDoesNotExist()
        assertEquals(
            com.example.update.UpdateDistributionChannel.GOOGLE_PLAY,
            prefsRepo.preferencesFlow.first().selectedUpdateChannel
        )
    }

    @Test
    fun temporaryNotificationsAutoDismissAndPersistentErrorsRemainVisible_acceptanceTest() {
        com.example.update.UpdateProviderSelectionSessionStore.clearForTesting()

        var currentNotification by mutableStateOf<com.example.ui.components.InAppNotification?>(null)
        var currentGitHubState by mutableStateOf<com.example.update.UpdateCheckState>(
            com.example.update.UpdateCheckState.Idle
        )
        var currentPlayState by mutableStateOf<com.example.update.PlayStoreUpdateState>(
            com.example.update.PlayStoreUpdateState.Idle
        )
        var configuredRepoSlug by mutableStateOf("")

        val showTempNotification: (String, com.example.ui.components.InAppNotificationType) -> Unit = { msg, type ->
            currentNotification = com.example.ui.components.InAppNotification(
                message = msg,
                type = type,
                durationMs = 3_500L
            )
        }

        composeTestRule.setContent {
            PulsePlayTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    com.example.ui.components.InAppNotificationBanner(
                        notification = currentNotification,
                        onDismiss = { currentNotification = null }
                    )
                    com.example.update.UpdateSettingsScreen(
                        installedVersionName = "1.0.0",
                        installedVersionCode = 1L,
                        githubRepositorySlug = configuredRepoSlug,
                        includePrereleaseUpdates = false,
                        updateState = currentGitHubState,
                        playStoreState = currentPlayState,
                        activeChannel = com.example.update.UpdateDistributionChannel.GITHUB_RELEASES,
                        autoDismissDurationMs = 3_500L,
                        onSelectChannel = {},
                        onSetGitHubRepositorySlug = { configuredRepoSlug = it },
                        onSetIncludePrereleaseUpdates = {},
                        onCheckForUpdatesClick = {
                            if (configuredRepoSlug.isBlank()) {
                                val warnMsg = "Configure your GitHub repository ('owner/repo') in Settings to check for official APK updates."
                                currentGitHubState = com.example.update.UpdateCheckState.ConfigurationRequired(warnMsg)
                                showTempNotification(
                                    warnMsg,
                                    com.example.ui.components.InAppNotificationType.TEMPORARY_WARNING
                                )
                            } else {
                                currentGitHubState = com.example.update.UpdateCheckState.UpToDate(
                                    installedVersionName = "1.0.0",
                                    installedVersionCode = 1L,
                                    latestRemoteTag = "v1.0.0"
                                )
                                showTempNotification(
                                    "PulsePlay v1.0.0 is up to date.",
                                    com.example.ui.components.InAppNotificationType.TEMPORARY_SUCCESS
                                )
                            }
                        },
                        onDownloadUpdateClick = {},
                        onCancelUpdateDownloadClick = {},
                        onInstallVerifiedUpdateClick = {}
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()

        // 1. Trigger GitHub configuration warning when repository is not configured
        composeTestRule.onNodeWithTag("check_for_updates_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()

        // Both the top status banner and the GitHub panel warning appear immediately
        composeTestRule.onNodeWithTag("status_feedback_banner").assertExists()
        composeTestRule.onNodeWithText("Repository Configuration Required").assertExists()
        assertEquals("Repository configuration must remain unchanged", "", configuredRepoSlug)

        // Still visible at 2.5 seconds (before the 3.5s dismissal window completes)
        composeTestRule.mainClock.advanceTimeBy(2_500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("status_feedback_banner").assertExists()
        composeTestRule.onNodeWithText("Repository Configuration Required").assertExists()

        // Automatically disappears after ~3.5 seconds without tapping X/close
        composeTestRule.mainClock.advanceTimeBy(1_200L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("status_feedback_banner").assertDoesNotExist()
        composeTestRule.onNodeWithText("Repository Configuration Required").assertDoesNotExist()
        assertEquals("Repository configuration must still remain unchanged", "", configuredRepoSlug)

        // 2. Trigger again while repository is still unconfigured: warning must appear again and auto-dismiss again
        composeTestRule.onNodeWithTag("check_for_updates_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("status_feedback_banner").assertExists()
        composeTestRule.onNodeWithText("Repository Configuration Required").assertExists()

        // Advance 2.0s, then re-trigger the exact same message: timer must restart for a full 3.5s
        composeTestRule.mainClock.advanceTimeBy(2_000L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("check_for_updates_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()

        // 2.0s after re-trigger (4.0s total since first trigger, but only 2.0s since restart), must STILL be visible
        composeTestRule.mainClock.advanceTimeBy(2_000L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("status_feedback_banner").assertExists()
        composeTestRule.onNodeWithText("Repository Configuration Required").assertExists()

        // Complete the remaining 1.7s of the restarted 3.5s timer: now it auto-dismisses
        composeTestRule.mainClock.advanceTimeBy(1_700L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("status_feedback_banner").assertDoesNotExist()
        composeTestRule.onNodeWithText("Repository Configuration Required").assertDoesNotExist()

        // 3. Configure repository and trigger an ordinary update-check result ("UpToDate")
        configuredRepoSlug = "pulseplay-org/pulseplay"
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.onNodeWithTag("check_for_updates_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("status_feedback_banner").assertExists()
        composeTestRule.onNodeWithText("You are running the latest version (v1.0.0).").assertExists()

        // After ~3.5 seconds, ordinary update-check result disappears automatically without user interaction
        composeTestRule.mainClock.advanceTimeBy(3_600L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("status_feedback_banner").assertDoesNotExist()
        composeTestRule.onNodeWithText("You are running the latest version (v1.0.0).").assertDoesNotExist()

        // 4. Verify important, persistent security/verification errors and ongoing downloads are NOT hidden prematurely
        val criticalErrorMsg = "SHA-256 checksum mismatch: APK integrity verification failed."
        currentGitHubState = com.example.update.UpdateCheckState.VerificationFailed(
            releaseInfo = null,
            reason = criticalErrorMsg
        )
        showTempNotification(
            criticalErrorMsg,
            com.example.ui.components.InAppNotificationType.PERSISTENT_ERROR
        )
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("status_feedback_banner").assertExists()
        composeTestRule.onNodeWithText("APK Verification Failed").assertExists()

        // Advance 6 full seconds: persistent error must remain visible until explicitly dismissed or resolved
        composeTestRule.mainClock.advanceTimeBy(6_000L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("status_feedback_banner").assertExists()
        composeTestRule.onNodeWithText("APK Verification Failed").assertExists()

        composeTestRule.mainClock.autoAdvance = true
    }
}
