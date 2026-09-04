package com.luciddream.phone.ui

import com.luciddream.algorithm.SleepSafetyGuardian
import com.luciddream.data.repository.NightSessionRepository
import com.luciddream.data.repository.UserProfileRepository
import com.luciddream.model.NightMode
import com.luciddream.model.NightSession
import com.luciddream.model.UserProfile
import com.luciddream.phone.service.PhoneSessionCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class TonightUiState(
    val userProfile: UserProfile = UserProfile(),
    val selectedMode: NightMode = NightMode.TLR,
    val audioCueEnabled: Boolean = true,
    val wbtbEnabled: Boolean = false,
    val activeSession: NightSession? = null,
    val past7NightsCount: Int = 0,
    val past7NightsRecallCount: Int = 0,
    val past7NightsLucidCount: Int = 0,
    val isStarting: Boolean = false,
    /** Sleep-fragmentation ruling for the currently selected mode; null until evaluated. */
    val guardrail: SleepSafetyGuardian.Decision? = null
) {
    /** True when tonight is a recovery night and nocturnal cues will not be delivered. */
    val cuesWithheldTonight: Boolean
        get() = guardrail is SleepSafetyGuardian.Decision.RestNight

    /**
     * True when the screening specifically is what withholds cues — the one rest-night cause the
     * user can act on, as opposed to a weekly cap or a recovery period that only time resolves.
     */
    val blockedByScreening: Boolean
        get() = (guardrail as? SleepSafetyGuardian.Decision.RestNight)
            ?.reasons
            ?.any { it.trigger == SleepSafetyGuardian.Trigger.SCREENING_EXCLUSION } == true

    /** User-facing explanations for a recovery night, or advisories when cues are allowed. */
    val guardrailMessages: List<String>
        get() = when (val decision = guardrail) {
            is SleepSafetyGuardian.Decision.RestNight -> decision.explanations
            is SleepSafetyGuardian.Decision.Allowed -> decision.advisories
            null -> emptyList()
        }
}

class TonightViewModel(
    private val sessionCoordinator: PhoneSessionCoordinator,
    private val sessionRepository: NightSessionRepository,
    private val profileRepository: UserProfileRepository
) {

    private val _uiState = MutableStateFlow(TonightUiState())
    val uiState: StateFlow<TonightUiState> = _uiState.asStateFlow()

    suspend fun loadState() {
        val profile = profileRepository.getUserProfile().first()
        val sessions = sessionRepository.getAllSessions().first()
        val reports = sessionRepository.getAllMorningReports().first()

        val recentReports = reports.take(7)
        val recallCount = recentReports.count { it.hadDreams }
        val lucidCount = recentReports.count { it.lucidSuccess }

        _uiState.value = _uiState.value.copy(
            userProfile = profile,
            past7NightsCount = sessions.take(7).size,
            past7NightsRecallCount = recallCount,
            past7NightsLucidCount = lucidCount,
            guardrail = sessionCoordinator.evaluateTonight(_uiState.value.selectedMode)
        )
    }

    fun selectMode(mode: NightMode) {
        _uiState.value = _uiState.value.copy(
            selectedMode = mode,
            wbtbEnabled = mode == NightMode.WBTB,
            // Limits depend on the mode, so the previous ruling no longer applies.
            guardrail = null
        )
    }

    /** Re-checks the sleep-fragmentation limits for the selected mode, e.g. after [selectMode]. */
    suspend fun refreshGuardrail() {
        _uiState.value = _uiState.value.copy(
            guardrail = sessionCoordinator.evaluateTonight(_uiState.value.selectedMode)
        )
    }

    fun toggleAudioCues(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(audioCueEnabled = enabled)
    }

    suspend fun startTonightSession(): NightSession {
        _uiState.value = _uiState.value.copy(isStarting = true)
        val result = sessionCoordinator.startNightSession(
            mode = _uiState.value.selectedMode,
            audioEnabled = _uiState.value.audioCueEnabled
        )
        val session = sessionRepository.getSessionById(result.payload.sessionId)!!
        _uiState.value = _uiState.value.copy(
            activeSession = session,
            isStarting = false,
            guardrail = result.guardrail
        )
        return session
    }
}
