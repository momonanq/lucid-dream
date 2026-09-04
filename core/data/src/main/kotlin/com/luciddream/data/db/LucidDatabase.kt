package com.luciddream.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        NightSessionEntity::class,
        CueEventEntity::class,
        SensorWindowEntity::class,
        DreamEntryEntity::class,
        MorningReportEntity::class,
        UserProfileEntity::class,
        QueuedSyncEventEntity::class
    ],
    // v2 adds UserProfileEntity.screening (onboarding safety screening).
    // fallbackToDestructiveMigration below is the project's pre-release policy: the schema change
    // wipes local data instead of migrating it. Replace with a real Migration before any release.
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LucidDatabase : RoomDatabase() {
    abstract fun nightSessionDao(): NightSessionDao
    abstract fun dreamJournalDao(): DreamJournalDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun queuedSyncEventDao(): QueuedSyncEventDao

    companion object {
        @Volatile
        private var INSTANCE: LucidDatabase? = null

        fun getInstance(context: Context): LucidDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LucidDatabase::class.java,
                    "lucid_dream_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
