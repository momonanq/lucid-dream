package com.luciddream.model

import kotlinx.serialization.Serializable

@Serializable
enum class SessionStatus {
    SCHEDULED,
    RUNNING,
    COMPLETED,
    CANCELLED
}

@Serializable
data class NightSession(
    val id: String,
    val userId: String = "default_user",
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val mode: NightMode,
    val status: SessionStatus = SessionStatus.SCHEDULED,
    val cuesPlanned: Int = 5,
    val cuesTriggered: Int = 0,
    val cooldownMinutes: Int = 15,
    val earliestCueMinutes: Int = 90,
    val hapticIntensity: Double = 0.5,
    val audioEnabled: Boolean = false,
    val wbtbEnabled: Boolean = false,
    val wbtbAlarmTimeMs: Long? = null,
    val wbtbCompleted: Boolean = false,
    val cueEvents: List<CueEvent> = emptyList(),
    val sensorWindows: List<SensorWindow> = emptyList()
)
