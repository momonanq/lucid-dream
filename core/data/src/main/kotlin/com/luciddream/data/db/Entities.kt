package com.luciddream.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.luciddream.model.*

@Entity(tableName = "night_sessions")
data class NightSessionEntity(
    @PrimaryKey val id: String,
    val startTimeMs: Long,
    val endTimeMs: Long?,
    val mode: String,
    val status: String,
    val cuesPlanned: Int,
    val cuesTriggered: Int,
    val cooldownMinutes: Int,
    val earliestCueMinutes: Int,
    val hapticIntensity: Double,
    val audioEnabled: Boolean,
    val wbtbAlarmTimeMs: Long?
)

@Entity(tableName = "cue_events")
data class CueEventEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val timestampMs: Long,
    val minutesFromSleepStart: Long,
    val cueType: String,
    val intensity: Double,
    val confidenceScoreAtTrigger: Double,
    val wakeSpikeAfter: Boolean,
    val outcome: String
)

@Entity(tableName = "sensor_windows")
data class SensorWindowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val meanHr: Double,
    val minHr: Double,
    val maxHr: Double,
    val hrStdDev: Double,
    val ibiMeanMs: Double,
    val rmssd: Double,
    val sdnn: Double,
    val movementIndex: Double,
    val sampleCount: Int,
    val confidence: Double,
    val hrSampleCount: Int,
    val ibiSampleCount: Int,
    val motionSampleCount: Int,
    val isDataSufficient: Boolean
)

@Entity(tableName = "dream_entries")
data class DreamEntryEntity(
    @PrimaryKey val id: String,
    val dateIso: String,
    val timestampMs: Long,
    val title: String,
    val transcript: String,
    val tags: List<DreamTag>,
    val signs: List<DreamSign>,
    val clarityRating: Int = 3,
    val lucidityLevel: Int = 0,
    val sessionId: String? = null,
    val audioNoteUri: String? = null
)

@Entity(tableName = "morning_reports")
data class MorningReportEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val timestampMs: Long,
    val hadDreams: Boolean,
    val recallScore: Int,
    val lucidSuccess: Boolean,
    val cueDetectedInDream: Boolean,
    val falseAwakening: Boolean,
    val notes: String
)

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val bedtimeTargetMinutes: Int,
    val wakeTargetMinutes: Int,
    val lucidGoal: String,
    val cuePreference: String,
    val watchModel: String,
    val baselineHeartRate: Double,
    val baselineIbiVariance: Double,
    val preferredHapticIntensity: Double,
    val maxCuesPerNight: Int,
    val cooldownMinutes: Int,
    val earliestCueMinutesAfterOnset: Int,
    val confidenceThreshold: Double,
    val calibrationNightsCompleted: Int,
    val screening: SafetyScreening = SafetyScreening()
)

@Entity(tableName = "queued_sync_events")
data class QueuedSyncEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val payloadJson: String,
    val timestampMs: Long
)
