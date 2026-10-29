package com.example.media

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
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
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Arrays

/**
 * Resolved artwork payload for a media item, unifying Media3 [MediaMetadata] (`artworkData` and
 * `artworkUri`), embedded audio/video tags (ID3v2 `APIC`/`PIC`, FLAC `PICTURE`, MP4/M4A `covr`),
 * and MediaStore album art so that the System Notification, Main Now Playing Screen, Landscape Deck,
 * Side Panel, and Mini-Player all display the exact same album artwork without triggering native
 * `MediaMetadataRetrieverJNI` error logs on tracks without embedded pictures.
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

    private val metadataCache = object : LruCache<String, ResolvedMediaArtwork>(256) {}

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
     * 2. Embedded picture in the audio/video file (ID3v2 `APIC`/`PIC`, FLAC `PICTURE`, MP4 `covr`)
     * 3. MediaStore album art URI (`content://media/external/audio/albumart/<albumId>`)
     * 4. Companion cover image file in the same directory (`cover.jpg`, `<filename>.jpg`, etc.)
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
                return cached
            }
        }

        // 1. Try embedded picture from container tags (ID3v2 APIC/PIC, FLAC PICTURE, MP4 covr)
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
        val companionUri = resolveCompanionCoverFileUri(media.uriString, media.album)
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
     * MediaStore album art. Never throws on corrupt or missing images.
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
     * Extracts embedded cover picture bytes (`APIC` / `PIC` / `covr` / `PICTURE`) from an audio/video URI
     * in pure Kotlin without calling `MediaMetadataRetriever.getEmbeddedPicture()`, avoiding native
     * `E/MediaMetadataRetrieverJNI: getEmbeddedPicture: Call to getEmbeddedPicture failed.` log spam
     * when scanning tracks without embedded cover art.
     */
    fun extractEmbeddedArtworkBytes(context: Context, uriString: String): ByteArray? {
        if (uriString.isBlank()) return null
        val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        return extractId3v2ApicPictureBytes(context, parsed)
    }

    /**
     * Parses ID3v2 (`APIC` / `PIC`), FLAC (`PICTURE`), and MP4/M4A (`covr`) embedded picture frames
     * directly from the container stream.
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
                val header = ByteArray(12)
                val readHeader = readFully(stream, header, 12)
                if (readHeader < 10) return null

                // 1. WAV ("RIFF"...."WAVE") has no standard cover image in the header
                if (readHeader >= 12 &&
                    header[0] == 'R'.code.toByte() &&
                    header[1] == 'I'.code.toByte() &&
                    header[2] == 'F'.code.toByte() &&
                    header[3] == 'F'.code.toByte() &&
                    header[8] == 'W'.code.toByte() &&
                    header[9] == 'A'.code.toByte() &&
                    header[10] == 'V'.code.toByte() &&
                    header[11] == 'E'.code.toByte()
                ) {
                    return null
                }

                // 2. ID3v2 ("ID3") tag header
                if (header[0] == 'I'.code.toByte() &&
                    header[1] == 'D'.code.toByte() &&
                    header[2] == '3'.code.toByte()
                ) {
                    val versionMajor = header[3].toInt() and 0xFF
                    val flags = header[5].toInt() and 0xFF
                    val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                        ((header[7].toInt() and 0x7F) shl 14) or
                        ((header[8].toInt() and 0x7F) shl 7) or
                        (header[9].toInt() and 0x7F)
                    val maxRead = tagSize.coerceIn(0, 10 * 1024 * 1024)
                    if (maxRead <= 10) return null

                    val tagBytes = ByteArray(maxRead)
                    var initialCopied = 0
                    val extraFromHeader = readHeader - 10
                    if (extraFromHeader > 0) {
                        val copyLen = extraFromHeader.coerceAtMost(maxRead)
                        System.arraycopy(header, 10, tagBytes, 0, copyLen)
                        initialCopied = copyLen
                    }
                    val restRead = readFully(stream, tagBytes, maxRead - initialCopied, initialCopied)
                    val totalRead = initialCopied + restRead
                    return parseId3v2FramesForPicture(tagBytes, totalRead, versionMajor, flags)
                }

                // 3. FLAC ("fLaC") stream with METADATA_BLOCK_PICTURE (block type 6)
                if (header[0] == 'f'.code.toByte() &&
                    header[1] == 'L'.code.toByte() &&
                    header[2] == 'a'.code.toByte() &&
                    header[3] == 'C'.code.toByte()
                ) {
                    return parseFlacPictureBlock(stream, header, readHeader)
                }

                // 4. MP4 / M4A container ("ftyp" at offset 4..7) with "covr" -> "data" atom
                if (readHeader >= 8 &&
                    header[4] == 'f'.code.toByte() &&
                    header[5] == 't'.code.toByte() &&
                    header[6] == 'y'.code.toByte() &&
                    header[7] == 'p'.code.toByte()
                ) {
                    return parseMp4CovrAtom(stream, header, readHeader)
                }

                null
            }
        }.getOrNull()
    }

    private fun readFully(stream: InputStream, buffer: ByteArray, length: Int, offset: Int = 0): Int {
        var total = 0
        while (total < length) {
            val r = stream.read(buffer, offset + total, length - total)
            if (r <= 0) break
            total += r
        }
        return total
    }

    private fun parseId3v2FramesForPicture(
        tagBytes: ByteArray,
        totalRead: Int,
        versionMajor: Int,
        tagFlags: Int
    ): ByteArray? {
        var pos = 0
        // Skip optional ID3v2.3 / ID3v2.4 extended header if bit 6 is set
        if ((tagFlags and 0x40) != 0 && totalRead >= 4) {
            val extSize = if (versionMajor == 4) {
                ((tagBytes[0].toInt() and 0x7F) shl 21) or
                    ((tagBytes[1].toInt() and 0x7F) shl 14) or
                    ((tagBytes[2].toInt() and 0x7F) shl 7) or
                    (tagBytes[3].toInt() and 0x7F)
            } else {
                ((tagBytes[0].toInt() and 0xFF) shl 24) or
                    ((tagBytes[1].toInt() and 0xFF) shl 16) or
                    ((tagBytes[2].toInt() and 0xFF) shl 8) or
                    (tagBytes[3].toInt() and 0xFF) + 4
            }
            if (extSize in 4 until totalRead) {
                pos = extSize
            }
        }

        if (versionMajor == 2) {
            // ID3v2.2 uses 6-byte frame headers (3-byte ID + 3-byte size) and "PIC" frame ID
            while (pos + 6 <= totalRead) {
                if (tagBytes[pos].toInt() == 0 && tagBytes[pos + 1].toInt() == 0 && tagBytes[pos + 2].toInt() == 0) break
                val frameId = String(byteArrayOf(tagBytes[pos], tagBytes[pos + 1], tagBytes[pos + 2]), Charset.forName("ISO-8859-1"))
                val frameSize = ((tagBytes[pos + 3].toInt() and 0xFF) shl 16) or
                    ((tagBytes[pos + 4].toInt() and 0xFF) shl 8) or
                    (tagBytes[pos + 5].toInt() and 0xFF)
                pos += 6
                if (frameSize <= 0 || pos + frameSize > totalRead) break
                if (frameId == "PIC" && frameSize > 10) {
                    val frameEnd = pos + frameSize
                    findImageHeaderOffset(tagBytes, pos + 5, frameEnd)?.let { imgStart ->
                        return tagBytes.copyOfRange(imgStart, frameEnd)
                    }
                }
                pos += frameSize
            }
            return null
        }

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
                if (cursor < frameEnd) {
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
                        val candidate = tagBytes.copyOfRange(cursor, frameEnd)
                        if (hasValidImageMagicHeader(candidate)) {
                            return candidate
                        }
                    }
                }
                // Fallback scan for JPEG/PNG/WebP magic bytes inside the APIC frame payload
                findImageHeaderOffset(tagBytes, pos + 1, frameEnd)?.let { imgStart ->
                    return tagBytes.copyOfRange(imgStart, frameEnd)
                }
            }
            pos += frameSize
        }
        return null
    }

    private fun parseFlacPictureBlock(stream: InputStream, initialHeader: ByteArray, initialLen: Int): ByteArray? {
        val maxProbe = 2 * 1024 * 1024
        val buf = ByteArray(maxProbe)
        System.arraycopy(initialHeader, 0, buf, 0, initialLen)
        val extra = readFully(stream, buf, maxProbe - initialLen, initialLen)
        val total = initialLen + extra
        var pos = 4
        while (pos + 4 <= total) {
            val headerByte = buf[pos].toInt() and 0xFF
            val isLast = (headerByte and 0x80) != 0
            val blockType = headerByte and 0x7F
            val blockLen = ((buf[pos + 1].toInt() and 0xFF) shl 16) or
                ((buf[pos + 2].toInt() and 0xFF) shl 8) or
                (buf[pos + 3].toInt() and 0xFF)
            pos += 4
            if (blockLen < 0 || pos + blockLen > total) break
            if (blockType == 6 && blockLen > 32) {
                val blockEnd = pos + blockLen
                findImageHeaderOffset(buf, pos, blockEnd)?.let { imgStart ->
                    return buf.copyOfRange(imgStart, blockEnd)
                }
            }
            pos += blockLen
            if (isLast) break
        }
        return null
    }

    private fun parseMp4CovrAtom(stream: InputStream, initialHeader: ByteArray, initialLen: Int): ByteArray? {
        val maxProbe = 2 * 1024 * 1024
        val buf = ByteArray(maxProbe)
        System.arraycopy(initialHeader, 0, buf, 0, initialLen)
        val extra = readFully(stream, buf, maxProbe - initialLen, initialLen)
        val total = initialLen + extra
        var i = 0
        while (i + 24 < total) {
            if (buf[i] == 'c'.code.toByte() &&
                buf[i + 1] == 'o'.code.toByte() &&
                buf[i + 2] == 'v'.code.toByte() &&
                buf[i + 3] == 'r'.code.toByte() &&
                i >= 4
            ) {
                val covrSize = ((buf[i - 4].toInt() and 0xFF) shl 24) or
                    ((buf[i - 3].toInt() and 0xFF) shl 16) or
                    ((buf[i - 2].toInt() and 0xFF) shl 8) or
                    (buf[i - 1].toInt() and 0xFF)
                val covrEnd = ((i - 4) + covrSize).coerceIn(i + 4, total)
                findImageHeaderOffset(buf, i + 4, covrEnd)?.let { imgStart ->
                    return buf.copyOfRange(imgStart, covrEnd)
                }
            }
            i++
        }
        return null
    }

    private fun findImageHeaderOffset(buf: ByteArray, start: Int, end: Int): Int? {
        var idx = start.coerceAtLeast(0)
        val limit = (end - 4).coerceAtMost(buf.size - 4)
        while (idx <= limit) {
            val b0 = buf[idx].toInt() and 0xFF
            val b1 = buf[idx + 1].toInt() and 0xFF
            val b2 = buf[idx + 2].toInt() and 0xFF
            val b3 = buf[idx + 3].toInt() and 0xFF
            // JPEG: FF D8 FF
            if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return idx
            // PNG: 89 50 4E 47
            if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return idx
            // GIF: "GIF8"
            if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38) return idx
            // WebP: "RIFF"...."WEBP"
            if (idx + 12 <= end &&
                b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46 &&
                (buf[idx + 8].toInt() and 0xFF) == 0x57 &&
                (buf[idx + 9].toInt() and 0xFF) == 0x45 &&
                (buf[idx + 10].toInt() and 0xFF) == 0x42 &&
                (buf[idx + 11].toInt() and 0xFF) == 0x50
            ) {
                return idx
            }
            idx++
        }
        return null
    }

    fun isGenericAlbumName(album: String?): Boolean {
        if (album.isNullOrBlank()) return true
        val lower = album.trim().lowercase()
        return lower == "<unknown>" ||
            lower == "unknown" ||
            lower == "unknown album" ||
            lower == "unknown artist" ||
            lower == "music" ||
            lower == "download" ||
            lower == "downloads" ||
            lower == "audio" ||
            lower == "pulseplay" ||
            lower == "internal shared storage" ||
            lower == "sdcard" ||
            lower == "storage"
    }

    private fun resolveMediaStoreAlbumArtUri(context: Context, media: MediaEntity): Uri? {
        if (media.mediaType != MediaType.AUDIO) return null
        // Never map generic/unverified album titles to MediaStore album art
        if (isGenericAlbumName(media.album)) return null

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
                            // Verify the stream is actually openable and has a valid image magic header
                            context.contentResolver.openInputStream(artUri)?.use { stream ->
                                val probe = ByteArray(16)
                                val read = stream.read(probe)
                                if (read >= 4 && hasValidImageMagicHeader(probe.copyOf(read))) {
                                    return artUri
                                }
                            }
                        }
                    }
                }
            }
            null
        }.getOrNull()
    }

    private fun resolveCompanionCoverFileUri(uriString: String, albumName: String? = null): Uri? {
        return runCatching {
            val parsed = Uri.parse(uriString)
            if (parsed.scheme?.lowercase() != "file") return null
            val mediaFile = parsed.path?.let { File(it) } ?: return null
            val parentDir = mediaFile.parentFile ?: return null
            if (!parentDir.exists() || !parentDir.isDirectory) return null

            val baseName = mediaFile.nameWithoutExtension
            // 1. Explicit track-specific companion file (e.g., "mysong.jpg" alongside "mysong.mp3")
            val trackCandidates = listOf(
                "$baseName.jpg",
                "$baseName.jpeg",
                "$baseName.png",
                "$baseName.webp"
            )
            for (name in trackCandidates) {
                val candidate = File(parentDir, name)
                if (candidate.exists() && candidate.isFile && candidate.length() > 64L) {
                    return Uri.fromFile(candidate)
                }
            }

            // 2. Folder-wide album art ("cover.jpg", "folder.jpg") ONLY if parent directory is a dedicated album folder
            val dirName = parentDir.name.trim().lowercase()
            val isSharedBucket = dirName == "download" ||
                dirName == "downloads" ||
                dirName == "music" ||
                dirName == "audio" ||
                dirName == "whatsapp audio" ||
                dirName == "telegram audio" ||
                dirName == "bluetooth" ||
                dirName == "0" ||
                dirName == "emulated" ||
                dirName == "sdcard"
            if (!isSharedBucket && !isGenericAlbumName(albumName)) {
                val albumCandidates = listOf(
                    "cover.jpg",
                    "cover.jpeg",
                    "cover.png",
                    "cover.webp",
                    "album.jpg",
                    "album.jpeg",
                    "album.png",
                    "folder.jpg",
                    "folder.png"
                )
                for (name in albumCandidates) {
                    val candidate = File(parentDir, name)
                    if (candidate.exists() && candidate.isFile && candidate.length() > 64L) {
                        return Uri.fromFile(candidate)
                    }
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
