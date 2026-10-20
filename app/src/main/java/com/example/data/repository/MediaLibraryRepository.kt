package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.data.local.FavoriteEntity
import com.example.data.local.MediaEntity
import com.example.data.local.MediaType
import com.example.data.local.PlaybackHistoryEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistWithCount
import com.example.data.local.PulseDao
import com.example.media.SampleMediaGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class MediaLibraryRepository(
    private val context: Context,
    private val dao: PulseDao,
    private val sampleGenerator: SampleMediaGenerator
) {
    val allMediaFlow: Flow<List<MediaEntity>> = dao.observeAllMedia()
    val audioMediaFlow: Flow<List<MediaEntity>> = dao.observeMediaByType(MediaType.AUDIO)
    val videoMediaFlow: Flow<List<MediaEntity>> = dao.observeMediaByType(MediaType.VIDEO)
    val favoritesFlow: Flow<List<MediaEntity>> = dao.observeFavoriteMediaItems()
    val favoriteEntitiesFlow: Flow<List<FavoriteEntity>> = dao.observeFavorites()
    val playlistsFlow: Flow<List<PlaylistWithCount>> = dao.observePlaylistsWithCount()
    val historyFlow: Flow<List<PlaybackHistoryEntity>> = dao.observePlaybackHistory()

    fun observePlaylistItems(playlistId: Long): Flow<List<MediaEntity>> =
        dao.observePlaylistMedia(playlistId)

    /**
     * Scans shared MediaStore for Audio and Video items when permission is granted,
     * and also ensures built-in legal reference samples are indexed so the user
     * can test playback, EQ, subtitles, trimming, and conversion immediately.
     */
    suspend fun scanAndSyncMediaLibrary(includeDeviceMediaStore: Boolean): Int =
        withContext(Dispatchers.IO) {
            val discovered = mutableListOf<MediaEntity>()

            // Always ensure legal built-in studio samples exist and are indexed
            val samples = runCatching {
                sampleGenerator.ensureLegalSampleMediaGenerated()
            }.getOrDefault(emptyList())
            discovered.addAll(samples)

            if (includeDeviceMediaStore) {
                discovered.addAll(queryMediaStoreAudio())
                discovered.addAll(queryMediaStoreVideo())
            }

            if (discovered.isNotEmpty()) {
                dao.upsertMediaItems(discovered)
            }
            discovered.size
        }

    private fun queryMediaStoreAudio(): List<MediaEntity> {
        val list = mutableListOf<MediaEntity>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("1000")
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val bucketCol = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                var count = 0
                while (cursor.moveToNext() && count < 500) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(nameCol)
                        ?: "Audio Track #$id"
                    val artist = cursor.getString(artistCol)
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: "Unknown Artist"
                    val album = cursor.getString(albumCol)
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: "Local Audio"
                    val folder = if (bucketCol >= 0) {
                        cursor.getString(bucketCol)?.takeIf { it.isNotBlank() } ?: "Music"
                    } else "Music"
                    val durationMs = cursor.getLong(durCol).coerceAtLeast(0L)
                    val sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val mime = cursor.getString(mimeCol) ?: "audio/*"
                    val dateAdded = cursor.getLong(dateCol)

                    list.add(
                        MediaEntity(
                            uriString = contentUri.toString(),
                            mediaStoreId = id,
                            title = title,
                            artist = artist,
                            album = album,
                            folderName = folder,
                            durationMs = durationMs,
                            sizeBytes = sizeBytes,
                            mimeType = mime,
                            mediaType = MediaType.AUDIO,
                            dateAddedEpochSec = dateAdded,
                            isAvailable = true,
                            isGeneratedSample = false
                        )
                    )
                    count++
                }
            }
        }
        return list
    }

    private fun queryMediaStoreVideo(): List<MediaEntity> {
        val list = mutableListOf<MediaEntity>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.ARTIST,
            MediaStore.Video.Media.ALBUM,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.ALBUM)
                val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                var count = 0
                while (cursor.moveToNext() && count < 500) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(nameCol)
                        ?: "Video Clip #$id"
                    val artist = cursor.getString(artistCol)
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: "Local Video"
                    val album = cursor.getString(albumCol)
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: "Videos"
                    val folder = if (bucketCol >= 0) {
                        cursor.getString(bucketCol)?.takeIf { it.isNotBlank() } ?: "Videos"
                    } else "Videos"
                    val durationMs = cursor.getLong(durCol).coerceAtLeast(0L)
                    val sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val mime = cursor.getString(mimeCol) ?: "video/*"
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val dateAdded = cursor.getLong(dateCol)

                    list.add(
                        MediaEntity(
                            uriString = contentUri.toString(),
                            mediaStoreId = id,
                            title = title,
                            artist = artist,
                            album = album,
                            folderName = folder,
                            durationMs = durationMs,
                            sizeBytes = sizeBytes,
                            mimeType = mime,
                            mediaType = MediaType.VIDEO,
                            width = width,
                            height = height,
                            dateAddedEpochSec = dateAdded,
                            isAvailable = true,
                            isGeneratedSample = false
                        )
                    )
                    count++
                }
            }
        }
        return list
    }

    /**
     * Imports a single user-selected SAF URI, persists read permission if supported,
     * extracts real metadata via MediaMetadataRetriever, and inserts into Room.
     */
    suspend fun importSafMediaUri(uri: Uri): MediaEntity? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val mimeType = context.contentResolver.getType(uri) ?: "audio/*"
        val isVideo = mimeType.startsWith("video")
        var displayName = "Selected Media"
        var sizeBytes = 0L

        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                    if (sizeIdx >= 0) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
        }

        var durationMs = 0L
        var artist = "SAF Imported"
        var album = "User Selected"
        var width = 0
        var height = 0

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: artist
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() } ?: album
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }

        val entity = MediaEntity(
            uriString = uri.toString(),
            mediaStoreId = null,
            title = displayName.substringBeforeLast('.'),
            artist = artist,
            album = album,
            folderName = "SAF Imports",
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            mimeType = mimeType,
            mediaType = if (isVideo) MediaType.VIDEO else MediaType.AUDIO,
            width = width,
            height = height,
            dateAddedEpochSec = System.currentTimeMillis() / 1000L,
            isAvailable = true,
            isGeneratedSample = false
        )
        dao.upsertMediaItem(entity)
        entity
    }

    /**
     * Checks if a media URI is still readable (handles deleted files or revoked SAF permissions).
     */
    suspend fun verifyUriAvailability(uriString: String): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val available = when (uri.scheme) {
            "file" -> uri.path?.let { File(it).exists() && File(it).canRead() } ?: false
            "content" -> runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            }.getOrDefault(false)
            else -> false
        }
        dao.markMediaAvailability(uriString, available)
        available
    }

    suspend fun toggleFavorite(uriString: String): Boolean = withContext(Dispatchers.IO) {
        val currentlyFav = dao.isFavorite(uriString)
        if (currentlyFav) {
            dao.removeFavorite(uriString)
            false
        } else {
            dao.addFavorite(FavoriteEntity(uriString = uriString))
            true
        }
    }

    suspend fun createPlaylist(name: String, description: String): Long =
        withContext(Dispatchers.IO) {
            dao.insertPlaylist(
                PlaylistEntity(
                    name = name.trim(),
                    description = description.trim()
                )
            )
        }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun addMediaToPlaylist(playlistId: Long, uriString: String) =
        withContext(Dispatchers.IO) {
            dao.addTrackToPlaylist(playlistId, uriString)
        }

    suspend fun removeMediaFromPlaylist(playlistId: Long, uriString: String) =
        withContext(Dispatchers.IO) {
            dao.removeMediaFromPlaylist(playlistId, uriString)
        }

    suspend fun recordPlaybackProgress(
        media: MediaEntity,
        positionMs: Long,
        durationMs: Long
    ) = withContext(Dispatchers.IO) {
        val existing = dao.getHistoryForUri(media.uriString)
        val nextCount = (existing?.playCount ?: 0) + 1
        dao.upsertHistory(
            PlaybackHistoryEntity(
                uriString = media.uriString,
                titleSnapshot = media.title,
                mediaType = media.mediaType,
                lastPositionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(media.durationMs),
                playCount = nextCount,
                lastPlayedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun getSavedResumePosition(uriString: String): Long = withContext(Dispatchers.IO) {
        dao.getHistoryForUri(uriString)?.lastPositionMs ?: 0L
    }

    /**
     * Phase 4 Requirement: Clearly distinguish clearing playback history from deleting actual
     * user media files. Never delete original media when clearing history.
     */
    suspend fun clearPlaybackHistoryOnly() = withContext(Dispatchers.IO) {
        dao.clearAllHistory()
    }
}
