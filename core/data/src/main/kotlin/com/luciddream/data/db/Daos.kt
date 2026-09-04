package com.luciddream.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NightSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: NightSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCue(cue: CueEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWindow(window: SensorWindowEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMorningReport(report: MorningReportEntity)

    @Query("SELECT * FROM night_sessions ORDER BY startTimeMs DESC")
    fun getAllSessions(): Flow<List<NightSessionEntity>>

    @Query("SELECT * FROM night_sessions WHERE status = 'RUNNING' LIMIT 1")
    fun getActiveSession(): Flow<NightSessionEntity?>

    @Query("SELECT * FROM night_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): NightSessionEntity?

    @Query("SELECT * FROM cue_events WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getCuesForSession(sessionId: String): List<CueEventEntity>

    @Query("SELECT * FROM sensor_windows WHERE sessionId = :sessionId ORDER BY startTimestampMs ASC")
    suspend fun getWindowsForSession(sessionId: String): List<SensorWindowEntity>

    @Query("SELECT * FROM morning_reports WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getMorningReportBySessionId(sessionId: String): MorningReportEntity?

    @Query("SELECT * FROM morning_reports ORDER BY timestampMs DESC")
    fun getAllMorningReports(): Flow<List<MorningReportEntity>>
}

@Dao
interface DreamJournalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DreamEntryEntity)

    @Query("SELECT * FROM dream_entries ORDER BY timestampMs DESC")
    fun getAllEntries(): Flow<List<DreamEntryEntity>>

    @Query("SELECT * FROM dream_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: String): DreamEntryEntity?

    @Query("DELETE FROM dream_entries WHERE id = :id")
    suspend fun deleteEntry(id: String)
}

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profiles WHERE id = :id LIMIT 1")
    fun getProfileFlow(id: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileDirect(id: String): UserProfileEntity?
}

@Dao
interface QueuedSyncEventDao {
    @Insert
    suspend fun enqueue(event: QueuedSyncEventEntity): Long

    @Query("SELECT * FROM queued_sync_events ORDER BY id ASC")
    suspend fun getPendingEvents(): List<QueuedSyncEventEntity>

    @Query("DELETE FROM queued_sync_events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    @Query("DELETE FROM queued_sync_events WHERE id IN (:ids)")
    suspend fun deleteEvents(ids: List<Long>)
}
