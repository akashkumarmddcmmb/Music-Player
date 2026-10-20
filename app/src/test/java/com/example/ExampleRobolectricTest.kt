package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
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
import com.example.media.ConversionCapabilityRegistry
import com.example.media.EqualizerController
import com.example.media.MediaProcessorEngine
import com.example.media.ProcessingOutcome
import com.example.media.SampleMediaGenerator
import com.example.media.SubtitleFormat
import com.example.media.SubtitleLoadResult
import com.example.media.SubtitleParser
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
import java.io.File

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
    }

    @Test
    fun `equalizer fallback conversion registry and privacy defaults behave deterministically`() {
        // Equalizer graceful fallback when sessionId == 0
        val eqController = EqualizerController()
        eqController.bindToAudioSession(
            audioSessionId = 0,
            enabled = true,
            presetIndex = 1,
            bassStrength = 400,
            savedBandLevelsMb = listOf(300, 150, 0, 150, 300)
        )
        val eqState = eqController.state.value
        assertEquals(5, eqState.bands.size)
        assertEquals(400, eqState.bassBoostStrength)
        eqController.releaseEffects()

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
            requestedPerms.contains( android.Manifest.permission.INTERNET )
        )
    }
}
