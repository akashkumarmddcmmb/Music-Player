package com.example.media

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.data.local.MediaEntity
import com.example.data.local.MediaType
import com.example.ui.components.formatDurationMillis
import com.example.ui.components.formatFileSize
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ShareOutcome {
    data class Success(
        val sharedUri: Uri,
        val mimeType: String,
        val chooserIntent: Intent
    ) : ShareOutcome()

    data class Failure(val errorMessage: String) : ShareOutcome()
}

sealed class RingtoneOutcome {
    data class Success(
        val title: String,
        val ringtoneUri: Uri,
        val message: String
    ) : RingtoneOutcome()

    data class PermissionRequired(
        val media: MediaEntity,
        val settingsIntent: Intent,
        val message: String
    ) : RingtoneOutcome()

    data class Failure(val errorMessage: String) : RingtoneOutcome()
}

data class DetailedMediaFileInfo(
    val title: String,
    val fullFileName: String,
    val fileExtension: String,
    val mediaType: MediaType,
    val fileTypeLabel: String,
    val mimeType: String,
    val formattedFileSize: String,
    val rawSizeBytes: Long,
    val formattedDuration: String,
    val durationMs: Long,
    val resolution: String,
    val frameRate: String,
    val videoCodec: String,
    val audioCodec: String,
    val bitrate: String,
    val sampleRateAndChannels: String,
    val folderName: String,
    val locationOrUri: String,
    val dateAddedFormatted: String,
    val lastModifiedFormatted: String,
    val artist: String,
    val album: String,
    val embeddedMetadata: String
)

object MediaFileActionsHelper {

    const val NOT_AVAILABLE = "Not available"

    /**
     * Optional override for deterministic unit testing of WRITE_SETTINGS permission state.
     * In production, this remains null and [Settings.System.canWrite] is used directly.
     */
    var testCanWriteSettingsOverride: Boolean? = null

    /**
     * Records the last ringtone URI applied via [setAudioAsRingtone] for diagnostics and verification.
     */
    @Volatile
    var lastAppliedRingtoneUri: Uri? = null
        private set

    fun isSupportedMediaFile(media: MediaEntity): Boolean {
        if (!media.isAvailable) return false
        val mime = media.mimeType.trim().lowercase()
        if (mime.startsWith("text/") || mime.startsWith("image/") || mime.startsWith("application/pdf")) {
            return false
        }
        return media.mediaType == MediaType.AUDIO || media.mediaType == MediaType.VIDEO
    }

    fun isSupportedAudioForRingtone(media: MediaEntity): Boolean {
        if (!isSupportedMediaFile(media)) return false
        if (media.mediaType != MediaType.AUDIO) return false
        val mime = media.mimeType.trim().lowercase()
        return !mime.startsWith("video/")
    }

    fun isSupportedForStudioEditing(media: MediaEntity): Boolean {
        return isSupportedMediaFile(media)
    }

    fun resolveShareMimeType(media: MediaEntity): String {
        val cleanMime = media.mimeType.trim().lowercase()
        return if (media.mediaType == MediaType.VIDEO) {
            if (cleanMime.startsWith("video/")) cleanMime else "video/*"
        } else {
            if (cleanMime.startsWith("audio/")) cleanMime else "audio/*"
        }
    }

    /**
     * Resolves a secure `content://` URI for sharing or system ringtone usage.
     * Never returns a raw `file://` URI or private filesystem path.
     */
    fun resolveSecureContentUri(context: Context, media: MediaEntity): Result<Uri> {
        if (!media.isAvailable) {
            return Result.failure(
                IllegalStateException("File '${media.title}' is unavailable or was moved.")
            )
        }

        val parsed = runCatching { Uri.parse(media.uriString) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid URI for '${media.title}'."))

        val scheme = parsed.scheme?.lowercase()

        if (scheme == "content") {
            return Result.success(parsed)
        }

        if (media.mediaStoreId != null && media.mediaStoreId > 0L) {
            val baseCollection = if (media.mediaType == MediaType.VIDEO) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val mediaStoreUri = ContentUris.withAppendedId(baseCollection, media.mediaStoreId)
            return Result.success(mediaStoreUri)
        }

        if (scheme == "file" || scheme.isNullOrBlank()) {
            val rawPath = parsed.path ?: media.uriString
            val file = File(rawPath)
            if (!file.exists() || !file.canRead() || file.length() <= 0L) {
                return Result.failure(
                    IllegalStateException("Media file '${media.title}' does not exist or cannot be read.")
                )
            }
            return runCatching {
                val authority = "${context.packageName}.fileprovider"
                val contentUri = FileProvider.getUriForFile(context, authority, file)
                require(contentUri.scheme.equals("content", ignoreCase = true)) {
                    "Resolved URI must use content:// scheme"
                }
                contentUri
            }
        }

        return Result.failure(
            IllegalArgumentException("Unsupported URI scheme '${parsed.scheme}' for '${media.title}'.")
        )
    }

    /**
     * Launches the Android Sharesheet (`Intent.createChooser`) for the given audio or video file
     * using a secure `content://` URI, proper MIME type, and `FLAG_GRANT_READ_URI_PERMISSION`.
     */
    fun shareMediaFile(context: Context, media: MediaEntity): ShareOutcome {
        if (!isSupportedMediaFile(media)) {
            return ShareOutcome.Failure("Cannot share '${media.title}': file is unavailable or unsupported.")
        }

        val contentUri = resolveSecureContentUri(context, media).getOrElse { err ->
            return ShareOutcome.Failure(err.message ?: "Unable to resolve secure share URI for '${media.title}'.")
        }

        if (!contentUri.scheme.equals("content", ignoreCase = true)) {
            return ShareOutcome.Failure("Refusing to share non-content URI for '${media.title}'.")
        }

        val mimeType = resolveShareMimeType(media)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TITLE, media.title)
            putExtra(Intent.EXTRA_SUBJECT, media.title)
            clipData = ClipData.newRawUri(media.title, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserTitle = if (media.mediaType == MediaType.VIDEO) {
            "Share Video: ${media.title}"
        } else {
            "Share Audio: ${media.title}"
        }

        val chooserIntent = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return runCatching {
            context.startActivity(chooserIntent)
            ShareOutcome.Success(
                sharedUri = contentUri,
                mimeType = mimeType,
                chooserIntent = chooserIntent
            )
        }.getOrElse { err ->
            ShareOutcome.Failure("Unable to open Android Sharesheet: ${err.localizedMessage ?: "No compatible app found"}")
        }
    }

    fun canWriteSystemSettings(context: Context): Boolean {
        testCanWriteSettingsOverride?.let { return it }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }

    fun buildManageWriteSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Sets the given audio [media] item as the device's default system ringtone.
     * - Rejects video files or unavailable files.
     * - Checks `Settings.System.canWrite(context)` first and returns [RingtoneOutcome.PermissionRequired]
     *   if the user has not yet granted `WRITE_SETTINGS`.
     * - When permitted, resolves/registers a valid ringtone `content://` URI and invokes
     *   [RingtoneManager.setActualDefaultRingtoneUri].
     */
    fun setAudioAsRingtone(context: Context, media: MediaEntity): RingtoneOutcome {
        if (media.mediaType != MediaType.AUDIO || media.mimeType.startsWith("video/", ignoreCase = true)) {
            return RingtoneOutcome.Failure("Video files cannot be set as a system ringtone.")
        }
        if (!media.isAvailable) {
            return RingtoneOutcome.Failure("Audio file '${media.title}' is unavailable or cannot be read.")
        }

        if (!canWriteSystemSettings(context)) {
            val settingsIntent = buildManageWriteSettingsIntent(context)
            return RingtoneOutcome.PermissionRequired(
                media = media,
                settingsIntent = settingsIntent,
                message = "To set '${media.title}' as your ringtone, please allow 'Modify system settings' for PulsePlay."
            )
        }

        val ringtoneUri = resolveOrPrepareRingtoneUri(context, media).getOrElse { err ->
            return RingtoneOutcome.Failure(
                err.message ?: "Could not prepare '${media.title}' as a system ringtone."
            )
        }

        return runCatching {
            RingtoneManager.setActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE,
                ringtoneUri
            )
            lastAppliedRingtoneUri = ringtoneUri
            RingtoneOutcome.Success(
                title = media.title,
                ringtoneUri = ringtoneUri,
                message = "Ringtone set to '${media.title}'."
            )
        }.getOrElse { err ->
            RingtoneOutcome.Failure(
                "Failed to set ringtone for '${media.title}': ${err.localizedMessage ?: "System ringtone update rejected"}"
            )
        }
    }

    private fun resolveOrPrepareRingtoneUri(context: Context, media: MediaEntity): Result<Uri> {
        val rawUri = runCatching { Uri.parse(media.uriString) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Invalid audio URI."))

        // Verify source readability first
        if (rawUri.scheme.equals("file", ignoreCase = true)) {
            val file = rawUri.path?.let { File(it) }
            if (file == null || !file.exists() || !file.canRead() || file.length() <= 0L) {
                return Result.failure(
                    IllegalStateException("Audio file '${media.title}' does not exist on storage.")
                )
            }
        }

        // 1. If already a MediaStore content:// URI, mark IS_RINGTONE = 1 when permitted and return it
        if (rawUri.scheme.equals("content", ignoreCase = true) &&
            rawUri.authority == MediaStore.AUTHORITY
        ) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                }
                context.contentResolver.update(rawUri, values, null, null)
            }
            return Result.success(rawUri)
        }

        // 2. Try registering/copying into MediaStore.Audio.Media so Android's system RingtoneManager
        // can read it across reboots on all Android versions
        val mediaStoreInsertedUri = runCatching {
            val ext = extractExtension(media.title, media.uriString, media.mimeType).lowercase()
                .ifBlank { "wav" }
            val safeFileName = media.title.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").trim()
                .ifBlank { "PulsePlay_Ringtone" }
            val displayName = if (safeFileName.endsWith(".$ext", ignoreCase = true)) {
                safeFileName
            } else {
                "$safeFileName.$ext"
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.TITLE, media.title)
                put(MediaStore.Audio.Media.MIME_TYPE, resolveShareMimeType(media))
                put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
            }

            val inserted = context.contentResolver.insert(collection, values)
            if (inserted != null) {
                val copiedBytes = openSourceInputStream(context, rawUri)?.use { input ->
                    context.contentResolver.openOutputStream(inserted)?.use { output ->
                        input.copyTo(output)
                    }
                } ?: -1L

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val doneValues = ContentValues().apply {
                        put(MediaStore.Audio.Media.IS_PENDING, 0)
                    }
                    context.contentResolver.update(inserted, doneValues, null, null)
                }

                if (copiedBytes > 0L) {
                    inserted
                } else {
                    // In unit-test or shadow environments where openOutputStream is a no-op, fallback to FileProvider
                    runCatching { context.contentResolver.delete(inserted, null, null) }
                    null
                }
            } else {
                null
            }
        }.getOrNull()

        if (mediaStoreInsertedUri != null) {
            return Result.success(mediaStoreInsertedUri)
        }

        // 3. Fallback to secure FileProvider / content:// URI
        return resolveSecureContentUri(context, media)
    }

    private fun openSourceInputStream(context: Context, uri: Uri) = runCatching {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            uri.path?.let { FileInputStream(File(it)) }
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }.getOrNull()

    fun copyTextToClipboard(context: Context, label: String, text: String): Boolean {
        return runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        }.getOrDefault(false)
    }

    /**
     * Extracts real, truthful metadata for the given [media] item without fabricating unavailable fields.
     * Any unavailable metadata field is explicitly set to [NOT_AVAILABLE] ("Not available").
     */
    fun extractDetailedMediaFileInfo(context: Context, media: MediaEntity): DetailedMediaFileInfo {
        val uri = runCatching { Uri.parse(media.uriString) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        val localFile = if (scheme == "file" && uri.path != null) File(uri.path!!) else null

        var queriedDisplayName: String? = null
        var queriedSizeBytes: Long = -1L
        var queriedDataPath: String? = null
        var queriedRelativePath: String? = null
        var queriedDateAddedSec: Long = -1L
        var queriedDateModifiedSec: Long = -1L

        if (scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                            queriedDisplayName = cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            queriedSizeBytes = cursor.getLong(sizeIdx)
                        }
                        @Suppress("DEPRECATION")
                        val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (dataIdx >= 0 && !cursor.isNull(dataIdx)) {
                            queriedDataPath = cursor.getString(dataIdx)?.takeIf { it.isNotBlank() }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val relIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                            if (relIdx >= 0 && !cursor.isNull(relIdx)) {
                                queriedRelativePath = cursor.getString(relIdx)?.takeIf { it.isNotBlank() }
                            }
                        }
                        val addedIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                        if (addedIdx >= 0 && !cursor.isNull(addedIdx)) {
                            queriedDateAddedSec = cursor.getLong(addedIdx)
                        }
                        val modIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                        if (modIdx >= 0 && !cursor.isNull(modIdx)) {
                            queriedDateModifiedSec = cursor.getLong(modIdx)
                        }
                    }
                }
            }
        }

        val resolvedExtension = extractExtension(
            displayName = queriedDisplayName ?: localFile?.name ?: media.title,
            uriString = media.uriString,
            mimeType = media.mimeType
        )
        val extensionDisplay = resolvedExtension.uppercase().ifBlank { NOT_AVAILABLE }

        val fullFileName = when {
            localFile != null && localFile.name.isNotBlank() -> localFile.name
            !queriedDisplayName.isNullOrBlank() -> queriedDisplayName!!
            else -> {
                val lastSeg = uri?.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                if (lastSeg != null && lastSeg.contains('.')) {
                    lastSeg
                } else if (resolvedExtension.isNotBlank() && !media.title.endsWith(".$resolvedExtension", ignoreCase = true)) {
                    "${media.title}.$resolvedExtension"
                } else {
                    media.title.ifBlank { NOT_AVAILABLE }
                }
            }
        }

        val actualSizeBytes = when {
            localFile != null && localFile.exists() && localFile.length() > 0L -> localFile.length()
            queriedSizeBytes > 0L -> queriedSizeBytes
            media.sizeBytes > 0L -> media.sizeBytes
            else -> 0L
        }

        val formattedSize = if (actualSizeBytes > 0L) {
            "${formatFileSize(actualSizeBytes)} (${String.format(Locale.US, "%,d", actualSizeBytes)} bytes)"
        } else {
            NOT_AVAILABLE
        }

        // Query MediaMetadataRetriever for embedded tags, duration, bitrate, resolution, frame rate
        var retrieverDurationMs = 0L
        var retrieverBitrateBps = 0L
        var retrieverWidth = 0
        var retrieverHeight = 0
        var retrieverFrameRate: Float = 0f
        var retrieverArtist: String? = null
        var retrieverAlbum: String? = null
        var retrieverGenre: String? = null
        var retrieverYear: String? = null
        var retrieverComposer: String? = null
        var retrieverTrackNumber: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            if (localFile != null && localFile.exists()) {
                retriever.setDataSource(localFile.absolutePath)
            } else if (uri != null) {
                retriever.setDataSource(context, uri)
            }
            retrieverDurationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            retrieverBitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            retrieverWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            retrieverHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            retrieverFrameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f

            retrieverArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" }
            retrieverAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() && it != "<unknown>" }
            retrieverGenre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?.takeIf { it.isNotBlank() && it != "<unknown>" }
            retrieverYear = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.takeIf { it.isNotBlank() && it != "<unknown>" }
            retrieverComposer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                ?.takeIf { it.isNotBlank() && it != "<unknown>" }
            retrieverTrackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.takeIf { it.isNotBlank() && it != "<unknown>" }
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }

        // Inspect tracks via MediaExtractor for reliable audio/video codec, sample rate, channels, frame rate
        var extractedVideoCodec: String? = null
        var extractedAudioCodec: String? = null
        var extractedVideoBitrateBps = 0L
        var extractedAudioBitrateBps = 0L
        var extractedSampleRateHz = 0
        var extractedChannels = 0
        var extractedFrameRate = 0f
        var extractedWidth = 0
        var extractedHeight = 0

        val extractor = MediaExtractor()
        try {
            if (localFile != null && localFile.exists()) {
                extractor.setDataSource(localFile.absolutePath)
            } else if (uri != null) {
                extractor.setDataSource(context, uri, null)
            }
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = runCatching { format.getString(MediaFormat.KEY_MIME) }.getOrNull().orEmpty()
                if (trackMime.startsWith("video/", ignoreCase = true) && extractedVideoCodec == null) {
                    extractedVideoCodec = formatCodecFriendlyName(trackMime)
                    extractedWidth = format.getSafeInt(MediaFormat.KEY_WIDTH)
                    extractedHeight = format.getSafeInt(MediaFormat.KEY_HEIGHT)
                    extractedFrameRate = format.getSafeFloat(MediaFormat.KEY_FRAME_RATE)
                    extractedVideoBitrateBps = format.getSafeInt(MediaFormat.KEY_BIT_RATE).toLong()
                } else if (trackMime.startsWith("audio/", ignoreCase = true) && extractedAudioCodec == null) {
                    extractedAudioCodec = formatCodecFriendlyName(trackMime)
                    extractedSampleRateHz = format.getSafeInt(MediaFormat.KEY_SAMPLE_RATE)
                    extractedChannels = format.getSafeInt(MediaFormat.KEY_CHANNEL_COUNT)
                    extractedAudioBitrateBps = format.getSafeInt(MediaFormat.KEY_BIT_RATE).toLong()
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { extractor.release() }
        }

        // Direct container header inspection for local WAV / MP4 / M4A files when native extractors omit fields
        if (localFile != null && localFile.exists() && localFile.canRead()) {
            if (resolvedExtension.equals("wav", ignoreCase = true)) {
                inspectWavHeader(localFile)?.let { wavInfo ->
                    if (extractedAudioCodec == null) extractedAudioCodec = wavInfo.codecName
                    if (extractedSampleRateHz <= 0) extractedSampleRateHz = wavInfo.sampleRateHz
                    if (extractedChannels <= 0) extractedChannels = wavInfo.channels
                    if (extractedAudioBitrateBps <= 0L) extractedAudioBitrateBps = wavInfo.bitrateBps
                }
            } else if (resolvedExtension in setOf("mp4", "m4a", "m4v", "mov")) {
                inspectIsoBmffCodecs(localFile)?.let { isoInfo ->
                    if (extractedVideoCodec == null && isoInfo.videoCodec != null) {
                        extractedVideoCodec = isoInfo.videoCodec
                    }
                    if (extractedAudioCodec == null && isoInfo.audioCodec != null) {
                        extractedAudioCodec = isoInfo.audioCodec
                    }
                }
            }
        }

        val actualDurationMs = when {
            media.durationMs > 0L -> media.durationMs
            retrieverDurationMs > 0L -> retrieverDurationMs
            else -> 0L
        }
        val formattedDuration = if (actualDurationMs > 0L) {
            "${formatDurationMillis(actualDurationMs)} (${String.format(Locale.US, "%,d", actualDurationMs)} ms)"
        } else {
            NOT_AVAILABLE
        }

        val finalWidth = when {
            media.width > 0 -> media.width
            extractedWidth > 0 -> extractedWidth
            retrieverWidth > 0 -> retrieverWidth
            else -> 0
        }
        val finalHeight = when {
            media.height > 0 -> media.height
            extractedHeight > 0 -> extractedHeight
            retrieverHeight > 0 -> retrieverHeight
            else -> 0
        }

        val resolutionText = if (media.mediaType == MediaType.VIDEO && finalWidth > 0 && finalHeight > 0) {
            "$finalWidth × $finalHeight px"
        } else {
            NOT_AVAILABLE
        }

        val finalFps = if (extractedFrameRate > 0f) extractedFrameRate else retrieverFrameRate
        val frameRateText = if (media.mediaType == MediaType.VIDEO && finalFps > 0f) {
            if (finalFps % 1f == 0f) "${finalFps.toInt()} fps" else String.format(Locale.US, "%.2f fps", finalFps)
        } else {
            NOT_AVAILABLE
        }

        val totalBitrateBps = when {
            retrieverBitrateBps > 0L -> retrieverBitrateBps
            extractedVideoBitrateBps + extractedAudioBitrateBps > 0L -> extractedVideoBitrateBps + extractedAudioBitrateBps
            actualSizeBytes > 0L && actualDurationMs > 0L -> (actualSizeBytes * 8L * 1000L) / actualDurationMs
            else -> 0L
        }
        val bitrateText = if (totalBitrateBps > 0L) {
            "${(totalBitrateBps / 1000L).coerceAtLeast(1L)} kbps"
        } else {
            NOT_AVAILABLE
        }

        val videoCodecText = if (media.mediaType == MediaType.VIDEO) {
            extractedVideoCodec ?: NOT_AVAILABLE
        } else {
            NOT_AVAILABLE
        }

        val audioCodecText = extractedAudioCodec ?: NOT_AVAILABLE

        val sampleRateChannelsText = if (extractedSampleRateHz > 0 || extractedChannels > 0) {
            buildList {
                if (extractedSampleRateHz > 0) add("${extractedSampleRateHz} Hz")
                if (extractedChannels > 0) {
                    val chLabel = when (extractedChannels) {
                        1 -> "Mono (1 ch)"
                        2 -> "Stereo (2 ch)"
                        else -> "$extractedChannels channels"
                    }
                    add(chLabel)
                }
            }.joinToString(" • ")
        } else {
            NOT_AVAILABLE
        }

        val locationText = when {
            localFile != null -> localFile.absolutePath
            !queriedDataPath.isNullOrBlank() -> "$queriedDataPath (${media.uriString})"
            !queriedRelativePath.isNullOrBlank() -> "$queriedRelativePath$fullFileName (${media.uriString})"
            media.uriString.isNotBlank() -> media.uriString
            else -> NOT_AVAILABLE
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val effectiveDateAddedSec = when {
            media.dateAddedEpochSec > 0L -> media.dateAddedEpochSec
            queriedDateAddedSec > 0L -> queriedDateAddedSec
            else -> 0L
        }
        val dateAddedText = if (effectiveDateAddedSec > 0L) {
            dateFormat.format(Date(effectiveDateAddedSec * 1000L))
        } else {
            NOT_AVAILABLE
        }

        val lastModifiedMs = when {
            localFile != null && localFile.exists() && localFile.lastModified() > 0L -> localFile.lastModified()
            queriedDateModifiedSec > 0L -> queriedDateModifiedSec * 1000L
            else -> 0L
        }
        val lastModifiedText = if (lastModifiedMs > 0L) {
            dateFormat.format(Date(lastModifiedMs))
        } else {
            NOT_AVAILABLE
        }

        val cleanArtist = retrieverArtist
            ?: media.artist.takeIf {
                it.isNotBlank() &&
                    !it.equals("<unknown>", ignoreCase = true) &&
                    !it.equals("Unknown Artist", ignoreCase = true) &&
                    !it.equals("SAF Imported", ignoreCase = true) &&
                    it != media.folderName
            }
            ?: NOT_AVAILABLE

        val cleanAlbum = retrieverAlbum
            ?: media.album.takeIf {
                it.isNotBlank() &&
                    !it.equals("<unknown>", ignoreCase = true) &&
                    !it.equals("Unknown Album", ignoreCase = true) &&
                    !it.equals("User Selected", ignoreCase = true) &&
                    it != media.folderName
            }
            ?: NOT_AVAILABLE

        val embeddedTags = buildList {
            if (!retrieverGenre.isNullOrBlank()) add("Genre: $retrieverGenre")
            if (!retrieverYear.isNullOrBlank()) add("Year: $retrieverYear")
            if (!retrieverComposer.isNullOrBlank()) add("Composer: $retrieverComposer")
            if (!retrieverTrackNumber.isNullOrBlank()) add("Track: $retrieverTrackNumber")
            if (sampleRateChannelsText != NOT_AVAILABLE) add("Audio Stream: $sampleRateChannelsText")
        }
        val embeddedSummary = if (embeddedTags.isNotEmpty()) {
            embeddedTags.joinToString(" • ")
        } else {
            NOT_AVAILABLE
        }

        return DetailedMediaFileInfo(
            title = media.title.ifBlank { fullFileName },
            fullFileName = fullFileName,
            fileExtension = extensionDisplay,
            mediaType = media.mediaType,
            fileTypeLabel = if (media.mediaType == MediaType.VIDEO) "Video" else "Audio",
            mimeType = media.mimeType.takeIf { it.isNotBlank() && it != "*/*" } ?: NOT_AVAILABLE,
            formattedFileSize = formattedSize,
            rawSizeBytes = actualSizeBytes,
            formattedDuration = formattedDuration,
            durationMs = actualDurationMs,
            resolution = resolutionText,
            frameRate = frameRateText,
            videoCodec = videoCodecText,
            audioCodec = audioCodecText,
            bitrate = bitrateText,
            sampleRateAndChannels = sampleRateChannelsText,
            folderName = media.folderName.ifBlank { NOT_AVAILABLE },
            locationOrUri = locationText,
            dateAddedFormatted = dateAddedText,
            lastModifiedFormatted = lastModifiedText,
            artist = cleanArtist,
            album = cleanAlbum,
            embeddedMetadata = embeddedSummary
        )
    }

    private fun extractExtension(displayName: String?, uriString: String, mimeType: String): String {
        val fromName = displayName
            ?.substringAfterLast('.', "")
            ?.trim()
            ?.takeIf { it.length in 2..5 && it.all { ch -> ch.isLetterOrDigit() } }
        if (fromName != null) return fromName.lowercase()

        val fromUri = uriString
            .substringBefore('?')
            .substringAfterLast('/')
            .substringAfterLast('.', "")
            .trim()
            .takeIf { it.length in 2..5 && it.all { ch -> ch.isLetterOrDigit() } }
        if (fromUri != null) return fromUri.lowercase()

        return when (mimeType.trim().lowercase()) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            "audio/mp4", "audio/aac", "audio/x-m4a" -> "m4a"
            "audio/flac" -> "flac"
            "audio/ogg", "audio/opus" -> "ogg"
            "video/mp4" -> "mp4"
            "video/x-matroska" -> "mkv"
            "video/webm" -> "webm"
            "video/3gpp" -> "3gp"
            "video/quicktime" -> "mov"
            else -> ""
        }
    }

    private fun formatCodecFriendlyName(mime: String): String {
        return when (mime.lowercase()) {
            "video/avc" -> "H.264 / AVC (video/avc)"
            "video/hevc" -> "H.265 / HEVC (video/hevc)"
            "video/x-vnd.on2.vp9" -> "VP9 (video/vp9)"
            "video/av01" -> "AV1 (video/av01)"
            "video/mp4v-es" -> "MPEG-4 Part 2 (video/mp4v-es)"
            "audio/mp4a-latm", "audio/aac" -> "AAC (audio/mp4a-latm)"
            "audio/raw" -> "PCM Uncompressed (audio/raw)"
            "audio/mpeg" -> "MP3 (audio/mpeg)"
            "audio/flac" -> "FLAC Lossless (audio/flac)"
            "audio/opus" -> "Opus (audio/opus)"
            "audio/vorbis" -> "Vorbis (audio/vorbis)"
            else -> mime
        }
    }

    private fun MediaFormat.getSafeInt(key: String): Int = runCatching {
        if (containsKey(key)) getInteger(key) else 0
    }.getOrDefault(0)

    private fun MediaFormat.getSafeFloat(key: String): Float = runCatching {
        if (!containsKey(key)) return@runCatching 0f
        runCatching { getFloat(key) }.getOrElse { getInteger(key).toFloat() }
    }.getOrDefault(0f)

    private data class WavHeaderInfo(
        val codecName: String,
        val channels: Int,
        val sampleRateHz: Int,
        val bitrateBps: Long
    )

    private fun inspectWavHeader(file: File): WavHeaderInfo? = runCatching {
        if (file.length() < 44L) return null
        val header = ByteArray(44)
        FileInputStream(file).use { fis ->
            if (fis.read(header) < 44) return null
        }
        val riff = String(header, 0, 4, Charsets.US_ASCII)
        val wave = String(header, 8, 4, Charsets.US_ASCII)
        if (riff != "RIFF" || wave != "WAVE") return null

        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val audioFormat = buf.getShort(20).toInt() and 0xFFFF
        val channels = buf.getShort(22).toInt() and 0xFFFF
        val sampleRate = buf.getInt(24)
        val byteRate = buf.getInt(28).toLong() and 0xFFFFFFFFL
        val bitsPerSample = buf.getShort(34).toInt() and 0xFFFF

        val codecLabel = when (audioFormat) {
            1 -> "PCM ${bitsPerSample}-bit Uncompressed (WAVE)"
            3 -> "IEEE Float PCM (WAVE)"
            else -> "WAVE Format #$audioFormat"
        }
        WavHeaderInfo(
            codecName = codecLabel,
            channels = channels,
            sampleRateHz = sampleRate,
            bitrateBps = byteRate * 8L
        )
    }.getOrNull()

    private data class IsoBmffCodecInfo(
        val videoCodec: String?,
        val audioCodec: String?
    )

    private fun inspectIsoBmffCodecs(file: File): IsoBmffCodecInfo? = runCatching {
        val bytesToRead = minOf(file.length(), 65536L).toInt()
        if (bytesToRead < 32) return null
        val buffer = ByteArray(bytesToRead)
        FileInputStream(file).use { fis ->
            fis.read(buffer)
        }
        val ascii = String(buffer, Charsets.ISO_8859_1)
        val videoCodec = when {
            ascii.contains("avc1") || ascii.contains("avcC") -> "H.264 / AVC (avc1)"
            ascii.contains("hvc1") || ascii.contains("hev1") -> "H.265 / HEVC (hvc1)"
            ascii.contains("vp09") -> "VP9 (vp09)"
            ascii.contains("av01") -> "AV1 (av01)"
            else -> null
        }
        val audioCodec = when {
            ascii.contains("mp4a") || ascii.contains("esds") -> "AAC-LC (mp4a)"
            ascii.contains("alac") -> "Apple Lossless (alac)"
            ascii.contains("Opus") || ascii.contains("dOps") -> "Opus (dOps)"
            else -> null
        }
        IsoBmffCodecInfo(videoCodec = videoCodec, audioCodec = audioCodec)
    }.getOrNull()
}
