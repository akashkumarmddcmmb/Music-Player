package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class PulseTypeConverters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType =
        runCatching { MediaType.valueOf(value) }.getOrDefault(MediaType.AUDIO)

    @TypeConverter
    fun fromJobStatus(value: JobStatus): String = value.name

    @TypeConverter
    fun toJobStatus(value: String): JobStatus =
        runCatching { JobStatus.valueOf(value) }.getOrDefault(JobStatus.FAILED)

    @TypeConverter
    fun fromOperationType(value: ProcessingOperationType): String = value.name

    @TypeConverter
    fun toOperationType(value: String): ProcessingOperationType =
        runCatching { ProcessingOperationType.valueOf(value) }
            .getOrDefault(ProcessingOperationType.AUDIO_TRIM)
}

@Database(
    entities = [
        MediaEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        FavoriteEntity::class,
        PlaybackHistoryEntity::class,
        ProcessingJobEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(PulseTypeConverters::class)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun pulseDao(): PulseDao

    companion object {
        @Volatile
        private var INSTANCE: PulseDatabase? = null

        // Explicit baseline migration strategy documented per Phase 5 & Phase 16
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reserved for future schema evolution without destructive data loss
            }
        }

        fun getInstance(context: Context): PulseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PulseDatabase::class.java,
                    "pulseplay_studio.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
