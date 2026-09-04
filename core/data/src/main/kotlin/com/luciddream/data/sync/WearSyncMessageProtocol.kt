package com.luciddream.data.sync

import com.luciddream.model.CueType
import com.luciddream.model.NightMode
import kotlinx.serialization.Serializable

/**
 * Binary & JSON serialization contracts exchanged via Wearable DataClient / MessageClient.
 */
object WearSyncPaths {
    const val PATH_START_SESSION = "/lucid/session/start"
    const val PATH_STOP_SESSION = "/lucid/session/stop"
    const val PATH_CUE_TRIGGERED = "/lucid/event/cue"
    const val PATH_WAKE_SPIKE = "/lucid/event/wake_spike"
    const val PATH_MORNING_FEEDBACK = "/lucid/morning/feedback"
    const val PATH_HEARTBEAT = "/lucid/status/heartbeat"
}

@Serializable
data class StartSessionPayload(
    val sessionId: String,
    val mode: NightMode,
    val startTimeMs: Long,
    val earliestCueMinutes: Int,
    val cooldownMinutes: Int,
    val maxCues: Int,
    val hapticIntensity: Double,
    val audioEnabled: Boolean,
    val wbtbAlarmTimeMs: Long? = null
)

@Serializable
data class StopSessionPayload(
    val sessionId: String,
    val endTimeMs: Long,
    val stoppedBy: String // "PHONE" or "WATCH"
)

@Serializable
data class CueTriggeredPayload(
    val cueId: String,
    val sessionId: String,
    val timestampMs: Long,
    val cueType: CueType,
    val intensity: Double,
    val confidence: Double
)

@Serializable
data class WakeSpikePayload(
    val sessionId: String,
    val cueId: String,
    val timestampMs: Long,
    val movementIndex: Double,
    val hrSurgeBpm: Double
)

@Serializable
data class QuickMorningFeedbackPayload(
    val sessionId: String,
    val timestampMs: Long,
    val hadDream: Boolean,
    val hadLucidDream: Boolean,
    val noticedSignal: Boolean
)
