package com.luciddream.phone.ui

import com.luciddream.algorithm.CalibrationEngine
import com.luciddream.algorithm.PilotValidationEngine
import com.luciddream.data.repository.NightSessionRepository
import com.luciddream.data.repository.UserProfileRepository
import com.luciddream.data.samsung.SamsungHealthDataGateway
import com.luciddream.model.MorningReport
import com.luciddream.model.NightSession
import com.luciddream.model.SleepImport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class SleepReviewUiState(
    val session: NightSession? = null,
    val sleepImport: SleepImport? = null,
    val morningReport: MorningReport? = null,
    val calibrationResult: CalibrationEngine.CalibrationResult? = null,
    val validationMetrics: PilotValidationEngine.ValidationMetrics? = null,
    val pilotCsvData: String? = null,
    val isLoading: Boolean = false
)

class SleepReviewViewModel(
    private val sessionRepository: NightSessionRepository,
    private val profileRepository: UserProfileRepository,
    private val samsungHealthGateway: SamsungHealthDataGateway,
    private val calibrationEngine: CalibrationEngine = CalibrationEngine(),
    private val validationEngine: PilotValidationEngine = PilotValidationEngine()
) {

    private val _uiState = MutableStateFlow(SleepReviewUiState())
    val uiState: StateFlow<SleepReviewUiState> = _uiState.asStateFlow()

    suspend fun loadSessionReview(sessionId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)

        val session = sessionRepository.getSessionById(sessionId)
        val report = sessionRepository.getMorningReportBySessionId(sessionId)
        val profile = profileRepository.getUserProfile().first()

        val endMs = session?.endTimeMs
        val sleepImport = if (session != null && endMs != null) {
            samsungHealthGateway.importSleepSession(
                sessionId = sessionId,
                startMs = session.startTimeMs,
                endMs = endMs
            )
        } else null

        val calibrationResult = if (session != null) {
            calibrationEngine.calibrate(
                session = session,
                sleepImport = sleepImport,
                morningReport = report,
                currentProfile = profile
            )
        } else null

        val (validationMetrics, _) = if (session != null) {
            validationEngine.evaluateSession(session, sleepImport, profile.confidenceThreshold)
        } else (null to emptyList())

        val pilotCsv = if (session != null) {
            validationEngine.generatePilotCsv(session, sleepImport, profile.confidenceThreshold)
        } else null

        _uiState.value = SleepReviewUiState(
            session = session,
            sleepImport = sleepImport,
            morningReport = report,
            calibrationResult = calibrationResult,
            validationMetrics = validationMetrics,
            pilotCsvData = pilotCsv,
            isLoading = false
        )
    }
}
