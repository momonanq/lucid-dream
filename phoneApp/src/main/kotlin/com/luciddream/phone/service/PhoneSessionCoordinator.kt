package com.luciddream.phone.service

import com.luciddream.algorithm.CalibrationEngine
import com.luciddream.data.repository.NightSessionRepository
import com.luciddream.data.repository.UserProfileRepository
import com.luciddream.data.samsung.SamsungHealthDataGateway
import com.luciddream.data.sync.*
import com.luciddream.model.*
import com.luciddream.phone.audio.TlrAudioEngine
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Phone side orchestrator managing the night session lifecycle, audio cue pairing,
 * post-hoc Samsung Health sleep import, and calibration.
 */
class PhoneSessionCoordinator(
    private val sessionRepository: NightSessionRepository,
    private val profileRepository: UserProfileRepository,
    private val samsungHealthGateway: SamsungHealthDataGateway,
    private val audioElement: TlrAudioEngine,
    private val calibrationEngine: CalibrationEngine = CalibrationEngine()
) {

    suspend fun startNightSession(
        mode: NightMode,
        audioEnabled: Boolean = true,
        wbtbAlarmTimeMs: Long? = null
    ): StartSessionPayload {
        val profile = profileRepository.getUserProfile().first()
        val sessionId = "session_${UUID.randomUUID().toString().take(8)}"
        val startTime = System.currentTimeMillis()

        val session = NightSession(
            id = sessionId,
            userId = profile.id,
            startTimeMs = startTime,
            mode = mode,
            status = SessionStatus.RUNNING,
            cuesPlanned = profile.maxCuesPerNight,
            cuesTriggered = 0,
            cooldownMinutes = profile.cooldownMinutes,
            earliestCueMinutes = profile.earliestCueMinutesAfterOnset,
            hapticIntensity = profile.preferredHapticIntensity,
            audioEnabled = audioEnabled,
            wbtbEnabled = mode == NightMode.WBTB,
            wbtbAlarmTimeMs = wbtbAlarmTimeMs
        )

        sessionRepository.saveSession(session)

        return StartSessionPayload(
            sessionId = sessionId,
            mode = mode,
            startTimeMs = startTime,
            earliestCueMinutes = profile.earliestCueMinutesAfterOnset,
            cooldownMinutes = profile.cooldownMinutes,
            maxCues = profile.maxCuesPerNight,
            hapticIntensity = profile.preferredHapticIntensity,
            audioEnabled = audioEnabled,
            wbtbAlarmTimeMs = wbtbAlarmTimeMs
        )
    }

    suspend fun handleLiveCueEvent(payload: CueTriggeredPayload) {
        val session = sessionRepository.getSessionById(payload.sessionId) ?: return

        // If audio cues are enabled in session or combined mode, play synced soft acoustic chime
        if (session.audioEnabled && (payload.cueType == CueType.AUDIO_BEEP || payload.cueType == CueType.COMBINED)) {
            audioElement.playLucidityChime(volume = 0.25)
        }

        val event = CueEvent(
            id = payload.cueId,
            sessionId = payload.sessionId,
            timestampMs = payload.timestampMs,
            minutesFromSleepStart = (payload.timestampMs - session.startTimeMs) / 60000,
            cueType = payload.cueType,
            intensity = payload.intensity,
            confidenceScoreAtTrigger = payload.confidence
        )

        val updated = session.copy(
            cuesTriggered = session.cuesTriggered + 1,
            cueEvents = session.cueEvents + event
        )
        sessionRepository.saveSession(updated)
    }

    suspend fun handleWakeSpikeEvent(payload: WakeSpikePayload) {
        val session = sessionRepository.getSessionById(payload.sessionId) ?: return
        val updatedCues = session.cueEvents.map { cue ->
            if (cue.id == payload.cueId) {
                cue.copy(wakeSpikeAfter = true, outcome = CueOutcome.WOKE_UP_IMMEDIATELY)
            } else cue
        }
        sessionRepository.saveSession(session.copy(cueEvents = updatedCues))
    }

    suspend fun completeMorningSession(
        sessionId: String,
        endTimeMs: Long = System.currentTimeMillis(),
        morningFeedback: QuickMorningFeedbackPayload? = null
    ): Pair<NightSession, CalibrationEngine.CalibrationResult> {
        val session = sessionRepository.getSessionById(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")

        val finishedSession = session.copy(
            endTimeMs = endTimeMs,
            status = SessionStatus.COMPLETED
        )
        sessionRepository.saveSession(finishedSession)

        // 1. Post-hoc import from Samsung Health Data SDK
        val sleepImport = samsungHealthGateway.importSleepSession(
            sessionId = sessionId,
            startMs = finishedSession.startTimeMs,
            endMs = endTimeMs
        )

        // 2. Create Morning Report
        val report = if (morningFeedback != null) {
            MorningReport(
                id = "report_$sessionId",
                sessionId = sessionId,
                timestampMs = System.currentTimeMillis(),
                hadDreams = morningFeedback.hadDream,
                recallScore = if (morningFeedback.hadDream) 4 else 1,
                lucidSuccess = morningFeedback.hadLucidDream,
                cueDetectedInDream = morningFeedback.noticedSignal,
                falseAwakening = false
            )
        } else null

        if (report != null) {
            sessionRepository.saveMorningReport(report)
        }

        // 3. Post-hoc Calibration & Threshold Adaptation
        val currentProfile = profileRepository.getUserProfile().first()
        val calibrationResult = calibrationEngine.calibrate(
            session = finishedSession,
            sleepImport = sleepImport,
            morningReport = report,
            currentProfile = currentProfile
        )

        profileRepository.updateProfile(calibrationResult.adaptedProfile)

        return Pair(finishedSession, calibrationResult)
    }
}
