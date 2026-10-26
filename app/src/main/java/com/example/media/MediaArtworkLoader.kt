package com.example.media

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaMetadata
import com.example.data.local.MediaEntity
import com.example.data.local.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.util.Arrays

/**
 * Resolved artwork payload for a media item, unifying Media3 [MediaMetadata] (`artworkData` and
 * `artworkUri`), embedded audio/video tags (`MediaMetadataRetriever` / ID3v2 `APIC`), and
 * MediaStore album art so that the System Notification, Main Now Playing Screen, Landscape Deck,
 * Side Panel, and Mini-Player all display the exact same album artwork.
 */
data class ResolvedMediaArtwork(
    val mediaUri: String,
    val artworkData: ByteArray? = null,
    val artworkUri: Uri? = null,
    val sourceLabel: String = "none"
) {
    val artworkDataHash: Int = artworkData?.let { Arrays.hashCode(it) } ?: 0

    val hasArtwork: Boolean
        get() = (artworkData != null && artworkData.isNotEmpty()) || artworkUri != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResolvedMediaArtwork) return false
        return mediaUri == other.mediaUri &&
            artworkDataHash == other.artworkDataHash &&
            artworkUri == other.artworkUri &&
            sourceLabel == other.sourceLabel
    }

    override fun hashCode(): Int {
        var result = mediaUri.hashCode()
        result = 31 * result + artworkDataHash
        result = 31 * result + (artworkUri?.hashCode() ?: 0)
        result = 31 * result + sourceLabel.hashCode()
        return result
    }
}

object MediaArtworkLoader {

    private val ALBUM_ART_CONTENT_URI: Uri =
        Uri.parse("content://media/external/audio/albumart")

    private const val MAX_CACHE_SIZE_KB = 16 * 1024 // 16 MB in-memory LruCache

    private val bitmapCache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    private val metadataCache = object : LruCache<String, ResolvedMediaArtwork>(128) {}

    fun clearCache() {
        synchronized(bitmapCache) {
            bitmapCache.evictAll()
        }
        synchronized(metadataCache) {
            metadataCache.evictAll()
        }
    }

    fun buildCacheKey(
        mediaUri: String,
        artworkDataHash: Int,
        artworkUri: String?,
        targetSizePx: Int
    ): String {
        val bucketPx = when {
            targetSizePx <= 192 -> 192
            targetSizePx <= 512 -> 512
            else -> 768
        }
        return "$mediaUri|$artworkDataHash|${artworkUri.orEmpty()}|$bucketPx"
    }

    fun getCachedBitmap(
        mediaUri: String,
        artworkDataHash: Int,
        artworkUri: String?,
        targetSizePx: Int
    ): Bitmap? {
        if (mediaUri.isBlank() && artworkDataHash == 0 && artworkUri.isNullOrBlank()) return null
        val key = buildCacheKey(mediaUri, artworkDataHash, artworkUri, targetSizePx)
        synchronized(bitmapCache) {
            val cached = bitmapCache.get(key)
            if (cached != null && !cached.isRecycled) {
                return cached
            }
            for (altTarget in intArrayOf(512, 768, 192)) {
                val altKey = buildCacheKey(mediaUri, artworkDataHash, artworkUri, altTarget)
                val altCached = bitmapCache.get(altKey)
                if (altCached != null && !altCached.isRecycled) {
                    return altCached
                }
            }
        }
        // Also check if a higher-resolution or generic mediaUri entry is already cached when hash is 0
        if (artworkDataHash == 0 && artworkUri.isNullOrBlank()) {
            val cachedMeta = synchronized(metadataCache) { metadataCache.get(mediaUri) }
            if (cachedMeta != null && cachedMeta.hasArtwork) {
                for (altTarget in intArrayOf(targetSizePx, 512, 768, 192)) {
                    val resolvedKey = buildCacheKey(
                        mediaUri = mediaUri,
                        artworkDataHash = cachedMeta.artworkDataHash,
                        artworkUri = cachedMeta.artworkUri?.toString(),
                        targetSizePx = altTarget
                    )
                    synchronized(bitmapCache) {
                        val cached = bitmapCache.get(resolvedKey)
                        if (cached != null && !cached.isRecycled) {
                            return cached
                        }
                    }
                }
            }
        }
        return null
    }

    fun peekCachedArtworkMetadata(mediaUri: String): ResolvedMediaArtwork? {
        if (mediaUri.isBlank()) return null
        return synchronized(metadataCache) { metadataCache.get(mediaUri) }
    }

    fun cacheLiveMediaMetadataArtwork(mediaUri: String, playerMetadata: MediaMetadata?) {
        if (mediaUri.isBlank() || playerMetadata == null) return
        val metaBytes = playerMetadata.artworkData?.takeIf { it.isNotEmpty() }
        val metaUri = playerMetadata.artworkUri
        if (metaBytes == null && metaUri == null) return
        val resolved = ResolvedMediaArtwork(
            mediaUri = mediaUri,
            artworkData = metaBytes,
            artworkUri = metaUri,
            sourceLabel = if (metaBytes != null) "MediaMetadata.artworkData" else "MediaMetadata.artworkUri"
        )
        synchronized(metadataCache) {
            metadataCache.put(mediaUri, resolved)
        }
    }

    /**
     * Resolves artwork metadata (`artworkData` bytes and/or `artworkUri`) for [media], combining:
     * 1. Active Media3 [playerMetadata] (`artworkData` / `artworkUri`) if present
     * 2. Embedded picture in the audio/video file (`MediaMetadataRetriever.getEmbeddedPicture()` or ID3v2 `APIC`)
     * 3. MediaStore album art URI (`content://media/external/audio/albumart/<albumId>`)
     * 4. Explicit [MediaEntity.thumbnailUri] if populated
     * 5. Companion cover image file in the same directory (`cover.jpg`, `<filename>.jpg`, etc.)
     */
    fun resolveArtworkMetadata(
        context: Context,
        media: MediaEntity,
        playerMetadata: MediaMetadata? = null
    ): ResolvedMediaArtwork {
        val metaBytes = playerMetadata?.artworkData?.takeIf { it.isNotEmpty() }
        val metaUri = playerMetadata?.artworkUri

        if (metaBytes != null || metaUri != null) {
            val resolved = ResolvedMediaArtwork(
                mediaUri = media.uriString,
                artworkData = metaBytes,
                artworkUri = metaUri,
                sourceLabel = if (metaBytes != null) "MediaMetadata.artworkData" else "MediaMetadata.artworkUri"
            )
            synchronized(metadataCache) {
                metadataCache.put(media.uriString, resolved)
            }
            return resolved
        }

        synchronized(metadataCache) {
            metadataCache.get(media.uriString)?.let { cached ->
                if (cached.hasArtwork) {
                    return cached
                }
            }
        }

        // 1. Try embedded picture from MediaMetadataRetriever or ID3v2 APIC tag
        val embeddedBytes = extractEmbeddedArtworkBytes(context, media.uriString)
        if (embeddedBytes != null && embeddedBytes.isNotEmpty()) {
            val resolved = ResolvedMediaArtwork(
                mediaUri = media.uriString,
                artworkData = embeddedBytes,
                artworkUri = null,
                sourceLabel = "EmbeddedPicture"
            )
            synchronized(metadataCache) {
                metadataCache.put(media.uriString, resolved)
            }
            return resolved
        }

        // 2. Try MediaStore album art URI for audio tracks
        val albumArtUri = resolveMediaStoreAlbumArtUri(context, media)
        if (albumArtUri != null) {
            val resolved = ResolvedMediaArtwork(
                mediaUri = media.uriString,
                artworkData = null,
                artworkUri = albumArtUri,
                sourceLabel = "MediaStoreAlbumArt"
            )
            synchronized(metadataCache) {
                metadataCache.put(media.uriString, resolved)
            }
            return resolved
        }

        // 3. Try companion cover file in the same local folder
        val companionUri = resolveCompanionCoverFileUri(media.uriString)
        if (companionUri != null) {
            val resolved = ResolvedMediaArtwork(
                mediaUri = media.uriString,
                artworkData = null,
                artworkUri = companionUri,
                sourceLabel = "CompanionCoverFile"
            )
            synchronized(metadataCache) {
                metadataCache.put(media.uriString, resolved)
            }
            return resolved
        }

        val empty = ResolvedMediaArtwork(mediaUri = media.uriString)
        synchronized(metadataCache) {
            metadataCache.put(media.uriString, empty)
        }
        return empty
    }

    /**
     * Builds a Media3 [MediaMetadata] instance for [item] that includes title, artist, album,
     * and any resolved embedded `artworkData` or `artworkUri` so that both the MediaSession
     * Notification and the Now Playing UI receive the same artwork metadata.
     */
    fun buildMediaMetadataForEntity(
        context: Context,
        item: MediaEntity,
        existingMetadata: MediaMetadata? = null
    ): MediaMetadata {
        val resolved = resolveArtworkMetadata(context, item, existingMetadata)
        val builder = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist)
            .setAlbumTitle(item.album)

        if (resolved.artworkData != null && resolved.artworkData.isNotEmpty()) {
            builder.maybeSetArtworkData(
                resolved.artworkData,
                MediaMetadata.PICTURE_TYPE_FRONT_COVER
            )
        }
        if (resolved.artworkUri != null) {
            builder.setArtworkUri(resolved.artworkUri)
        }
        return builder.build()
    }

    /**
     * Loads and downsamples the album artwork [Bitmap] for [media], using in-memory [LruCache]
     * first and falling back to decoding [artworkData], [artworkUri], embedded file tags, or
     * MediaStore thumbnails. Never throws on corrupt or missing images.
     */
    fun loadArtworkBitmapBlocking(
        context: Context,
        media: MediaEntity?,
        artworkData: ByteArray? = null,
        artworkUri: String? = null,
        targetSizePx: Int = 512
    ): Bitmap? {
        if (media == null && artworkData == null && artworkUri.isNullOrBlank()) return null
        val mediaUri = media?.uriString.orEmpty()
        val dataHash = artworkData?.let { Arrays.hashCode(it) } ?: 0

        getCachedBitmap(mediaUri, dataHash, artworkUri, targetSizePx)?.let { return it }

        val safeTargetPx = targetSizePx.coerceIn(96, 1024)

        // 1. Direct artworkData bytes (e.g. from ExoPlayer MediaMetadata.artworkData)
        if (artworkData != null && artworkData.isNotEmpty()) {
            decodeSampledBitmapFromBytes(artworkData, safeTargetPx)?.let { bmp ->
                putInCache(mediaUri, dataHash, artworkUri, safeTargetPx, bmp)
                return bmp
            }
        }

        // 2. Direct artworkUri (e.g. from ExoPlayer MediaMetadata.artworkUri or MediaStore albumart)
        if (!artworkUri.isNullOrBlank()) {
            val parsedArtworkUri = runCatching { Uri.parse(artworkUri) }.getOrNull()
            if (parsedArtworkUri != null) {
                decodeSampledBitmapFromUri(context, parsedArtworkUri, safeTargetPx)?.let { bmp ->
                    putInCache(mediaUri, dataHash, artworkUri, safeTargetPx, bmp)
                    return bmp
                }
            }
        }

        if (media == null) return null

        // 3. Resolve from media file (embedded picture, MediaStore album art, or companion cover)
        val resolved = resolveArtworkMetadata(context, media, null)
        if (resolved.artworkData != null && resolved.artworkData.isNotEmpty()) {
            decodeSampledBitmapFromBytes(resolved.artworkData, safeTargetPx)?.let { bmp ->
                putInCache(mediaUri, resolved.artworkDataHash, null, safeTargetPx, bmp)
                putInCache(mediaUri, dataHash, artworkUri, safeTargetPx, bmp)
                return bmp
            }
        }
        if (resolved.artworkUri != null) {
            decodeSampledBitmapFromUri(context, resolved.artworkUri, safeTargetPx)?.let { bmp ->
                putInCache(mediaUri, 0, resolved.artworkUri.toString(), safeTargetPx, bmp)
                putInCache(mediaUri, dataHash, artworkUri, safeTargetPx, bmp)
                return bmp
            }
        }

        // 4. Android Q+ ContentResolver.loadThumbnail fallback for content:// media URIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaUri.startsWith("content://")) {
            val thumb = runCatching {
                context.contentResolver.loadThumbnail(
                    Uri.parse(mediaUri),
                    Size(safeTargetPx, safeTargetPx),
                    null
                )
            }.getOrNull()
            if (thumb != null && thumb.width > 0 && thumb.height > 0) {
                putInCache(mediaUri, dataHash, artworkUri, safeTargetPx, thumb)
                return thumb
            }
        }

        return null
    }

    private fun putInCache(
        mediaUri: String,
        artworkDataHash: Int,
        artworkUri: String?,
        targetSizePx: Int,
        bitmap: Bitmap
    ) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return
        val key = buildCacheKey(mediaUri, artworkDataHash, artworkUri, targetSizePx)
        synchronized(bitmapCache) {
            bitmapCache.put(key, bitmap)
        }
    }

    /**
     * Extracts embedded cover picture bytes (`APIC` / `covr` / `PICTURE`) from an audio/video URI.
     * Uses Android's [MediaMetadataRetriever] first, and falls back to direct ID3v2 `APIC` frame
     * parsing for local files so embedded MP3 artwork works reliably across all runtimes.
     */
    fun extractEmbeddedArtworkBytes(context: Context, uriString: String): ByteArray? {
        if (uriString.isBlank()) return null
        val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null

        // 1. Try MediaMetadataRetriever.getEmbeddedPicture()
        val retrieverBytes = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                when (parsed.scheme?.lowercase()) {
                    "file" -> {
                        val path = parsed.path
                        if (!path.isNullOrBlank() && File(path).exists()) {
                            retriever.setDataSource(path)
                        }
                    }
                    "content" -> {
                        context.contentResolver.openFileDescriptor(parsed, "r")?.use { pfd ->
                            retriever.setDataSource(pfd.fileDescriptor)
                        } ?: retriever.setDataSource(context, parsed)
                    }
                    else -> {
                        retriever.setDataSource(context, parsed)
                    }
                }
                retriever.embeddedPicture
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()

        if (retrieverBytes != null && retrieverBytes.isNotEmpty()) {
            return retrieverBytes
        }

        // 2. Fallback: Direct ID3v2 APIC frame extraction for local or content-URI MP3/audio files
        return extractId3v2ApicPictureBytes(context, parsed)
    }

    /**
     * Parses ID3v2.3 / ID3v2.4 `APIC` (Attached Picture) frames directly from the header of an
     * audio stream if `MediaMetadataRetriever` did not return the embedded picture (e.g. in JVM
     * unit tests or certain custom ID3v2 tags).
     */
    fun extractId3v2ApicPictureBytes(context: Context, uri: Uri): ByteArray? {
        return runCatching {
            val inputStream = when (uri.scheme?.lowercase()) {
                "file" -> {
                    val file = uri.path?.let { File(it) } ?: return null
                    if (!file.exists() || file.length() < 32L) return null
                    FileInputStream(file)
                }
                "content" -> context.contentResolver.openInputStream(uri)
                else -> null
            } ?: return null

            inputStream.use { stream ->
                val header = ByteArray(10)
                if (stream.read(header) != 10) return null
                if (header[0] != 'I'.code.toByte() ||
                    header[1] != 'D'.code.toByte() ||
                    header[2] != '3'.code.toByte()
                ) {
                    return null
                }
                val versionMajor = header[3].toInt() and 0xFF
                val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                    ((header[7].toInt() and 0x7F) shl 14) or
                    ((header[8].toInt() and 0x7F) shl 7) or
                    (header[9].toInt() and 0x7F)
                val maxRead = tagSize.coerceIn(0, 4 * 1024 * 1024)
                if (maxRead <= 10) return null

                val tagBytes = ByteArray(maxRead)
                var totalRead = 0
                while (totalRead < maxRead) {
                    val r = stream.read(tagBytes, totalRead, maxRead - totalRead)
                    if (r <= 0) break
                    totalRead += r
                }

                var pos = 0
                while (pos + 10 <= totalRead) {
                    val f0 = tagBytes[pos].toInt() and 0xFF
                    val f1 = tagBytes[pos + 1].toInt() and 0xFF
                    val f2 = tagBytes[pos + 2].toInt() and 0xFF
                    val f3 = tagBytes[pos + 3].toInt() and 0xFF
                    if (f0 == 0 && f1 == 0 && f2 == 0 && f3 == 0) break

                    val frameId = String(
                        byteArrayOf(tagBytes[pos], tagBytes[pos + 1], tagBytes[pos + 2], tagBytes[pos + 3]),
                        Charset.forName("ISO-8859-1")
                    )
                    val frameSize = if (versionMajor == 4) {
                        ((tagBytes[pos + 4].toInt() and 0x7F) shl 21) or
                            ((tagBytes[pos + 5].toInt() and 0x7F) shl 14) or
                            ((tagBytes[pos + 6].toInt() and 0x7F) shl 7) or
                            (tagBytes[pos + 7].toInt() and 0x7F)
                    } else {
                        ((tagBytes[pos + 4].toInt() and 0xFF) shl 24) or
                            ((tagBytes[pos + 5].toInt() and 0xFF) shl 16) or
                            ((tagBytes[pos + 6].toInt() and 0xFF) shl 8) or
                            (tagBytes[pos + 7].toInt() and 0xFF)
                    }
                    pos += 10
                    if (frameSize <= 0 || pos + frameSize > totalRead) break

                    if (frameId == "APIC" && frameSize > 16) {
                        val frameEnd = pos + frameSize
                        val encoding = tagBytes[pos].toInt() and 0xFF
                        var cursor = pos + 1
                        // Null-terminated MIME type (ISO-8859-1)
                        while (cursor < frameEnd && tagBytes[cursor].toInt() != 0) {
                            cursor++
                        }
                        cursor++ // skip null terminator
                        if (cursor >= frameEnd) break
                        cursor++ // skip picture type byte (1 byte)
                        // Skip null-terminated description based on text encoding
                        if (encoding == 1 || encoding == 2) {
                            while (cursor + 1 < frameEnd &&
                                !(tagBytes[cursor].toInt() == 0 && tagBytes[cursor + 1].toInt() == 0)
                            ) {
                                cursor += 2
                            }
                            cursor += 2
                        } else {
                            while (cursor < frameEnd && tagBytes[cursor].toInt() != 0) {
                                cursor++
                            }
                            cursor++
                        }
                        if (cursor < frameEnd) {
                            return tagBytes.copyOfRange(cursor, frameEnd)
                        }
                    }
                    pos += frameSize
                }
                null
            }
        }.getOrNull()
    }

    private fun resolveMediaStoreAlbumArtUri(context: Context, media: MediaEntity): Uri? {
        if (media.mediaType != MediaType.AUDIO) return null
        return runCatching {
            val queryUri = when {
                media.uriString.startsWith("content://media/") -> Uri.parse(media.uriString)
                media.mediaStoreId != null && media.mediaStoreId > 0L -> ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    media.mediaStoreId
                )
                else -> null
            } ?: return null

            val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)
            context.contentResolver.query(queryUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                    if (col >= 0) {
                        val albumId = cursor.getLong(col)
                        if (albumId > 0L) {
                            val artUri = ContentUris.withAppendedId(ALBUM_ART_CONTENT_URI, albumId)
                            // Verify the stream is actually openable before returning it
                            context.contentResolver.openInputStream(artUri)?.use {
                                return artUri
                            }
                        }
                    }
                }
            }
            null
        }.getOrNull()
    }

    private fun resolveCompanionCoverFileUri(uriString: String): Uri? {
        return runCatching {
            val parsed = Uri.parse(uriString)
            if (parsed.scheme?.lowercase() != "file") return null
            val mediaFile = parsed.path?.let { File(it) } ?: return null
            val parentDir = mediaFile.parentFile ?: return null
            if (!parentDir.exists() || !parentDir.isDirectory) return null

            val baseName = mediaFile.nameWithoutExtension
            val candidates = listOf(
                "$baseName.jpg",
                "$baseName.png",
                "cover.jpg",
                "cover.png",
                "album.jpg",
                "album.png",
                "folder.jpg",
                "folder.png"
            )
            for (name in candidates) {
                val candidate = File(parentDir, name)
                if (candidate.exists() && candidate.isFile && candidate.length() > 64L) {
                    return Uri.fromFile(candidate)
                }
            }
            null
        }.getOrNull()
    }

    /**
     * Checks whether [data] begins with a recognized image magic header (PNG, JPEG, WebP, GIF, BMP).
     * Rejects truncated or corrupt non-image byte arrays before decoding.
     */
    fun hasValidImageMagicHeader(data: ByteArray): Boolean {
        if (data.size < 4) return false
        val b0 = data[0].toInt() and 0xFF
        val b1 = data[1].toInt() and 0xFF
        val b2 = data[2].toInt() and 0xFF
        val b3 = data[3].toInt() and 0xFF

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (data.size >= 8 && b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return true
        // JPEG: FF D8 FF
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return true
        // GIF: "GIF8"
        if (data.size >= 6 && b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38) return true
        // WebP: "RIFF"...."WEBP"
        if (data.size >= 12 &&
            b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46 &&
            (data[8].toInt() and 0xFF) == 0x57 &&
            (data[9].toInt() and 0xFF) == 0x45 &&
            (data[10].toInt() and 0xFF) == 0x42 &&
            (data[11].toInt() and 0xFF) == 0x50
        ) {
            return true
        }
        // BMP: "BM"
        if (data.size >= 14 && b0 == 0x42 && b1 == 0x4D) return true

        return false
    }

    /**
     * Decodes a [ByteArray] into a downsampled [Bitmap] no larger than roughly [targetSizePx] on
     * each side. Returns `null` safely if the bytes are corrupt or not a valid image.
     */
    fun decodeSampledBitmapFromBytes(data: ByteArray, targetSizePx: Int): Bitmap? {
        if (!hasValidImageMagicHeader(data)) return null
        return runCatching {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, boundsOptions)
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                val direct = BitmapFactory.decodeByteArray(data, 0, data.size)
                return if (direct != null && direct.width > 0 && direct.height > 0) direct else null
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    boundsOptions.outWidth,
                    boundsOptions.outHeight,
                    targetSizePx,
                    targetSizePx
                )
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inJustDecodeBounds = false
            }
            val decoded = BitmapFactory.decodeByteArray(data, 0, data.size, decodeOptions)
            if (decoded != null && decoded.width > 0 && decoded.height > 0) decoded else null
        }.getOrNull()
    }

    /**
     * Decodes a [Uri] (`content://` or `file://`) into a downsampled [Bitmap].
     */
    fun decodeSampledBitmapFromUri(context: Context, uri: Uri, targetSizePx: Int): Bitmap? {
        return runCatching {
            val bytes = when (uri.scheme?.lowercase()) {
                "file" -> {
                    val file = uri.path?.let { File(it) } ?: return null
                    if (!file.exists() || !file.isFile || file.length() <= 0L || file.length() > 20 * 1024 * 1024L) {
                        return null
                    }
                    file.readBytes()
                }
                else -> {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
            } ?: return null

            decodeSampledBitmapFromBytes(bytes, targetSizePx)
        }.getOrNull()
    }

    fun calculateInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}

/**
 * Composable state holder that remembers and asynchronously loads the current media's album
 * artwork [ImageBitmap].
 *
 * - Immediately checks the synchronous [MediaArtworkLoader] LruCache for `(media.uriString, artworkDataHash, artworkUri)`
 *   so cached artwork appears on the very first frame without flicker.
 * - Resets immediately when `media.uriString` changes (so the previous track's artwork NEVER lingers
 *   on Next/Previous/Queue changes).
 * - Decodes asynchronously on [Dispatchers.IO] whenever `media.uriString`, `artworkData`, or
 *   `artworkUri` updates.
 */
@Composable
fun rememberMediaArtworkBitmap(
    media: MediaEntity?,
    artworkData: ByteArray?,
    artworkUri: String?,
    targetSizePx: Int = 512
): State<ImageBitmap?> {
    val context = LocalContext.current
    val mediaUri = media?.uriString.orEmpty()
    val dataHash = remember(artworkData) { artworkData?.let { Arrays.hashCode(it) } ?: 0 }

    val state = remember(mediaUri, dataHash, artworkUri, targetSizePx) {
        val initialCached = MediaArtworkLoader.getCachedBitmap(
            mediaUri = mediaUri,
            artworkDataHash = dataHash,
            artworkUri = artworkUri,
            targetSizePx = targetSizePx
        )?.asImageBitmap()
        mutableStateOf(initialCached)
    }

    LaunchedEffect(mediaUri, dataHash, artworkUri, targetSizePx) {
        if (media == null && artworkData == null && artworkUri.isNullOrBlank()) {
            state.value = null
            return@LaunchedEffect
        }
        if (state.value != null) return@LaunchedEffect

        val loaded = withContext(Dispatchers.IO) {
            MediaArtworkLoader.loadArtworkBitmapBlocking(
                context = context,
                media = media,
                artworkData = artworkData,
                artworkUri = artworkUri,
                targetSizePx = targetSizePx
            )
        }
        state.value = loaded?.asImageBitmap()
    }

    return state
}
