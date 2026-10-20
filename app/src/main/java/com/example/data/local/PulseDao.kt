package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCount(
    val playlistId: Long,
    val name: String,
    val description: String,
    val createdAtMs: Long,
    val itemCount: Int
)

@Dao
interface PulseDao {

    // --- Media Items ---
    @Query("SELECT * FROM media_items ORDER BY dateAddedEpochSec DESC")
    fun observeAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE mediaType = :type ORDER BY dateAddedEpochSec DESC")
    fun observeMediaByType(type: MediaType): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE uriString = :uri LIMIT 1")
    suspend fun getMediaByUri(uri: String): MediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMediaItems(items: List<MediaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMediaItem(item: MediaEntity)

    @Query("UPDATE media_items SET isAvailable = :available WHERE uriString = :uri")
    suspend fun markMediaAvailability(uri: String, available: Boolean)

    @Query("DELETE FROM media_items WHERE uriString = :uri")
    suspend fun removeMediaRecord(uri: String)

    // --- Favorites ---
    @Query("SELECT * FROM favorites ORDER BY addedAtMs DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Query(
        """
        SELECT m.* FROM media_items m
        INNER JOIN favorites f ON m.uriString = f.uriString
        ORDER BY f.addedAtMs DESC
        """
    )
    fun observeFavoriteMediaItems(): Flow<List<MediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE uriString = :uri")
    suspend fun removeFavorite(uri: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE uriString = :uri)")
    suspend fun isFavorite(uri: String): Boolean

    // --- Playlists ---
    @Query(
        """
        SELECT p.playlistId, p.name, p.description, p.createdAtMs,
               COUNT(pi.uriString) AS itemCount
        FROM playlists p
        LEFT JOIN playlist_items pi ON p.playlistId = pi.playlistId
        GROUP BY p.playlistId
        ORDER BY p.updatedAtMs DESC
        """
    )
    fun observePlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Query(
        """
        SELECT m.* FROM media_items m
        INNER JOIN playlist_items pi ON m.uriString = pi.uriString
        WHERE pi.playlistId = :playlistId
        ORDER BY pi.sortOrder ASC
        """
    )
    fun observePlaylistMedia(playlistId: Long): Flow<List<MediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getNextPlaylistSortOrder(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND uriString = :uri")
    suspend fun removeMediaFromPlaylist(playlistId: Long, uri: String)

    @Query("UPDATE playlists SET updatedAtMs = :now WHERE playlistId = :playlistId")
    suspend fun touchPlaylist(playlistId: Long, now: Long = System.currentTimeMillis())

    @Transaction
    suspend fun addTrackToPlaylist(playlistId: Long, uriString: String) {
        val nextOrder = getNextPlaylistSortOrder(playlistId)
        insertPlaylistItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                uriString = uriString,
                sortOrder = nextOrder
            )
        )
        touchPlaylist(playlistId)
    }

    // --- Playback History ---
    @Query("SELECT * FROM playback_history ORDER BY lastPlayedAtMs DESC LIMIT 100")
    fun observePlaybackHistory(): Flow<List<PlaybackHistoryEntity>>

    @Query("SELECT * FROM playback_history WHERE uriString = :uri LIMIT 1")
    suspend fun getHistoryForUri(uri: String): PlaybackHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entry: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history")
    suspend fun clearAllHistory()

    // --- Processing Jobs (Editor & Converter) ---
    @Query("SELECT * FROM processing_jobs ORDER BY createdAtMs DESC LIMIT 50")
    fun observeProcessingJobs(): Flow<List<ProcessingJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProcessingJob(job: ProcessingJobEntity): Long

    @Query(
        """
        UPDATE processing_jobs
        SET status = :status,
            progressPercent = :progress,
            statusMessage = :message,
            outputUri = :outputUri,
            outputSizeBytes = :outputSizeBytes,
            completedAtMs = :completedAtMs
        WHERE jobId = :jobId
        """
    )
    suspend fun updateProcessingJob(
        jobId: Long,
        status: JobStatus,
        progress: Int,
        message: String,
        outputUri: String? = null,
        outputSizeBytes: Long = 0L,
        completedAtMs: Long? = null
    )

    @Query(
        """
        UPDATE processing_jobs
        SET status = 'CANCELLED',
            statusMessage = 'Interrupted by application closure; safe to retry'
        WHERE status = 'RUNNING' OR status = 'PENDING'
        """
    )
    suspend fun recoverInterruptedJobsOnStartup()

    @Query("DELETE FROM processing_jobs WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearCompletedJobs()
}
