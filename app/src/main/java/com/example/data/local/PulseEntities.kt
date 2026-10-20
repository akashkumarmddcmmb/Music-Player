package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MediaType {
    AUDIO,
    VIDEO
}

enum class JobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class ProcessingOperationType {
    AUDIO_TRIM,
    VIDEO_TRIM_ROTATE,
    AUDIO_EXTRACTION,
    FORMAT_CONVERSION
}

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["mediaType"]),
        Index(value = ["folderName"]),
        Index(value = ["title"]),
        Index(value = ["dateAddedEpochSec"])
    ]
)
data class MediaEntity(
    @PrimaryKey val uriString: String,
    val mediaStoreId: Long?,
    val title: String,
    val artist: String,
    val album: String,
    val folderName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val mediaType: MediaType,
    val width: Int = 0,
    val height: Int = 0,
    val dateAddedEpochSec: Long,
    val isAvailable: Boolean = true,
    val isGeneratedSample: Boolean = false
)

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["name"], unique = false)]
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    val description: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "uriString"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId", "sortOrder"]),
        Index(value = ["uriString"])
    ]
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val uriString: String,
    val sortOrder: Int,
    val addedAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "favorites",
    indices = [Index(value = ["addedAtMs"])]
)
data class FavoriteEntity(
    @PrimaryKey val uriString: String,
    val addedAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playback_history",
    indices = [Index(value = ["lastPlayedAtMs"])]
)
data class PlaybackHistoryEntity(
    @PrimaryKey val uriString: String,
    val titleSnapshot: String,
    val mediaType: MediaType,
    val lastPositionMs: Long,
    val durationMs: Long,
    val playCount: Int = 1,
    val lastPlayedAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "processing_jobs",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAtMs"])
    ]
)
data class ProcessingJobEntity(
    @PrimaryKey(autoGenerate = true) val jobId: Long = 0,
    val operationType: ProcessingOperationType,
    val sourceUri: String,
    val sourceTitle: String,
    val outputUri: String?,
    val outputFormat: String,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val rotationDegrees: Int = 0,
    val cropPreset: String = "ORIGINAL",
    val targetBitrateKbps: Int = 192,
    val status: JobStatus = JobStatus.PENDING,
    val progressPercent: Int = 0,
    val statusMessage: String = "",
    val outputSizeBytes: Long = 0L,
    val createdAtMs: Long = System.currentTimeMillis(),
    val completedAtMs: Long? = null
)
