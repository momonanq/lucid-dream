package com.luciddream.data.repository

import com.luciddream.data.db.CueEventEntity
import com.luciddream.data.db.MorningReportEntity
import com.luciddream.data.db.NightSessionDao
import com.luciddream.data.db.NightSessionEntity
import com.luciddream.data.db.SensorWindowEntity
import com.luciddream.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomNightSessionRepository(
    private val dao: NightSessionDao
) : NightSessionRepository {

    override fun getAllSessions(): Flow<List<NightSession>> {
        return dao.getAllSessions().map { entities ->
            entities.map { it.toDomain(emptyList(), emptyList()) }
        }
    }

    override fun getActiveSession(): Flow<NightSession?> {
        return dao.getActiveSession().map { entity ->
            entity?.let {
                val cues = dao.getCuesForSession(it.id).map { c -> c.toDomain() }
                val windows = dao.getWindowsForSession(it.id).map { w -> w.toDomain() }
                it.toDomain(cues, windows)
            }
        }
    }

    override suspend fun getSessionById(id: String): NightSession? {
        val entity = dao.getSessionById(id) ?: return null
        val cues = dao.getCuesForSession(id).map { it.toDomain() }
        val windows = dao.getWindowsForSession(id).map { it.toDomain() }
        return entity.toDomain(cues, windows)
    }

    override suspend fun saveSession(session: NightSession) {
        dao.insertSession(session.toEntity())
        for (cue in session.cueEvents) {
            dao.insertCue(cue.toEntity())
        }
        for (window in session.sensorWindows) {
            dao.insertWindow(window.toEntity(session.id))
        }
    }

    override suspend fun saveMorningReport(report: MorningReport) {
        dao.insertMorningReport(report.toEntity())
    }

    override fun getAllMorningReports(): Flow<List<MorningReport>> {
        return dao.getAllMorningReports().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getMorningReportBySessionId(sessionId: String): MorningReport? {
        return dao.getMorningReportBySessionId(sessionId)?.toDomain()
    }

    private fun NightSessionEntity.toDomain(cues: List<CueEvent>, windows: List<SensorWindow>): NightSession {
        return NightSession(
            id = id,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            mode = NightMode.valueOf(mode),
            status = SessionStatus.valueOf(status),
            cuesPlanned = cuesPlanned,
            cuesTriggered = cuesTriggered,
            cooldownMinutes = cooldownMinutes,
            earliestCueMinutes = earliestCueMinutes,
            hapticIntensity = hapticIntensity,
            audioEnabled = audioEnabled,
            wbtbAlarmTimeMs = wbtbAlarmTimeMs,
            cueEvents = cues,
            sensorWindows = windows
        )
    }

    private fun NightSession.toEntity(): NightSessionEntity {
        return NightSessionEntity(
            id = id,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            mode = mode.name,
            status = status.name,
            cuesPlanned = cuesPlanned,
            cuesTriggered = cuesTriggered,
            cooldownMinutes = cooldownMinutes,
            earliestCueMinutes = earliestCueMinutes,
            hapticIntensity = hapticIntensity,
            audioEnabled = audioEnabled,
            wbtbAlarmTimeMs = wbtbAlarmTimeMs
        )
    }

    private fun CueEventEntity.toDomain(): CueEvent {
        return CueEvent(
            id = id,
            sessionId = sessionId,
            timestampMs = timestampMs,
            minutesFromSleepStart = minutesFromSleepStart,
            cueType = CueType.valueOf(cueType),
            intensity = intensity,
            confidenceScoreAtTrigger = confidenceScoreAtTrigger,
            wakeSpikeAfter = wakeSpikeAfter,
            outcome = CueOutcome.valueOf(outcome)
        )
    }

    private fun CueEvent.toEntity(): CueEventEntity {
        return CueEventEntity(
            id = id,
            sessionId = sessionId,
            timestampMs = timestampMs,
            minutesFromSleepStart = minutesFromSleepStart,
            cueType = cueType.name,
            intensity = intensity,
            confidenceScoreAtTrigger = confidenceScoreAtTrigger,
            wakeSpikeAfter = wakeSpikeAfter,
            outcome = outcome.name
        )
    }

    private fun SensorWindowEntity.toDomain(): SensorWindow {
        return SensorWindow(
            startTimestampMs = startTimestampMs,
            endTimestampMs = endTimestampMs,
            meanHr = meanHr,
            minHr = minHr,
            maxHr = maxHr,
            hrStdDev = hrStdDev,
            ibiMeanMs = ibiMeanMs,
            rmssd = rmssd,
            sdnn = sdnn,
            movementIndex = movementIndex,
            sampleCount = sampleCount,
            confidence = confidence,
            hrSampleCount = hrSampleCount,
            ibiSampleCount = ibiSampleCount,
            motionSampleCount = motionSampleCount,
            isDataSufficient = isDataSufficient,
            hrvAvailable = hrvAvailable
        )
    }

    private fun SensorWindow.toEntity(sessionId: String): SensorWindowEntity {
        return SensorWindowEntity(
            sessionId = sessionId,
            startTimestampMs = startTimestampMs,
            endTimestampMs = endTimestampMs,
            meanHr = meanHr,
            minHr = minHr,
            maxHr = maxHr,
            hrStdDev = hrStdDev,
            ibiMeanMs = ibiMeanMs,
            rmssd = rmssd,
            sdnn = sdnn,
            movementIndex = movementIndex,
            sampleCount = sampleCount,
            confidence = confidence,
            hrSampleCount = hrSampleCount,
            ibiSampleCount = ibiSampleCount,
            motionSampleCount = motionSampleCount,
            isDataSufficient = isDataSufficient,
            hrvAvailable = hrvAvailable
        )
    }

    private fun MorningReportEntity.toDomain(): MorningReport {
        return MorningReport(
            id = id,
            sessionId = sessionId,
            timestampMs = timestampMs,
            hadDreams = hadDreams,
            recallScore = recallScore,
            lucidSuccess = lucidSuccess,
            cueDetectedInDream = cueDetectedInDream,
            falseAwakening = falseAwakening,
            notes = notes
        )
    }

    private fun MorningReport.toEntity(): MorningReportEntity {
        return MorningReportEntity(
            id = id,
            sessionId = sessionId,
            timestampMs = timestampMs,
            hadDreams = hadDreams,
            recallScore = recallScore,
            lucidSuccess = lucidSuccess,
            cueDetectedInDream = cueDetectedInDream,
            falseAwakening = falseAwakening,
            notes = notes
        )
    }
}
