package com.luciddream.phone.ui

import com.luciddream.data.repository.UserProfileRepository
import com.luciddream.model.SafetyScreening
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * One screening question. Keeping them in an enum lets the screen render a single loop and keeps
 * the mapping to [SafetyScreening] in one place, so adding a condition cannot silently miss the UI.
 */
enum class ScreeningQuestion(val prompt: String, val detail: String) {
    SLEEP_APNEA(
        prompt = "Апноэ сна",
        detail = "Диагностированное или подозреваемое апноэ, в том числе при использовании CPAP"
    ),
    CHRONIC_INSOMNIA(
        prompt = "Хроническая бессонница",
        detail = "Диагноз или текущее лечение бессонницы"
    ),
    PARASOMNIA(
        prompt = "Парасомнии",
        detail = "Снохождение, ночные ужасы, RBD, тяжело переносимый сонный паралич"
    ),
    NARCOLEPSY(
        prompt = "Нарколепсия",
        detail = "Нарколепсия или другая диагностированная гиперсомния"
    ),
    SEIZURE_DISORDER(
        prompt = "Судорожные расстройства",
        detail = "Эпилепсия или судорожный синдром"
    ),
    PSYCHOSIS_TREATMENT(
        prompt = "Лечение психотического расстройства",
        detail = "Текущая терапия психотического расстройства"
    );
}

data class ScreeningUiState(
    val ageInput: String = "",
    val reportedConditions: Set<ScreeningQuestion> = emptySet(),
    val acknowledgedNotMedicalDevice: Boolean = false,
    val isSaving: Boolean = false,
    val savedScreening: SafetyScreening? = null
) {

    val ageYears: Int? get() = ageInput.trim().toIntOrNull()

    /** Non-null when the typed age cannot be used, for inline display under the field. */
    val ageError: String?
        get() {
            val trimmed = ageInput.trim()
            if (trimmed.isEmpty()) return null
            val parsed = trimmed.toIntOrNull() ?: return "Введите возраст числом."
            if (parsed !in MIN_PLAUSIBLE_AGE..MAX_PLAUSIBLE_AGE) return "Проверьте возраст."
            return null
        }

    /**
     * Age is required rather than optional: without it the guardrails cannot apply the
     * under-18 exclusion, and an unknown age must never be treated as an adult one.
     */
    val canSubmit: Boolean
        get() = acknowledgedNotMedicalDevice && ageInput.isNotBlank() && ageError == null

    /** The screening this form would produce if submitted right now. */
    fun toScreening(): SafetyScreening = SafetyScreening(
        isComplete = true,
        ageYears = ageYears,
        sleepApnea = ScreeningQuestion.SLEEP_APNEA in reportedConditions,
        chronicInsomnia = ScreeningQuestion.CHRONIC_INSOMNIA in reportedConditions,
        parasomnia = ScreeningQuestion.PARASOMNIA in reportedConditions,
        narcolepsy = ScreeningQuestion.NARCOLEPSY in reportedConditions,
        seizureDisorder = ScreeningQuestion.SEIZURE_DISORDER in reportedConditions,
        psychosisTreatment = ScreeningQuestion.PSYCHOSIS_TREATMENT in reportedConditions,
        acknowledgedNotMedicalDevice = acknowledgedNotMedicalDevice
    )

    /**
     * What the current answers would cost, shown live so the outcome is never a surprise
     * after submitting.
     */
    val previewExclusions: List<String> get() = toScreening().exclusionReasons

    private companion object {
        const val MIN_PLAUSIBLE_AGE = 5
        const val MAX_PLAUSIBLE_AGE = 120
    }
}

/**
 * Backs the onboarding safety screening.
 *
 * Until this is submitted, [SafetyScreening.isComplete] stays false and SleepSafetyGuardian
 * withholds every nocturnal cue, so the screen is the gate to the whole night contour. It is
 * deliberately re-openable: the conditions it asks about can develop after onboarding.
 */
class ScreeningViewModel(
    private val profileRepository: UserProfileRepository
) {

    private val _uiState = MutableStateFlow(ScreeningUiState())
    val uiState: StateFlow<ScreeningUiState> = _uiState.asStateFlow()

    /** Prefills the form from a previously submitted screening, for re-taking it. */
    suspend fun loadExisting() {
        val existing = profileRepository.getUserProfile().first().screening
        if (!existing.isComplete) return

        _uiState.value = ScreeningUiState(
            ageInput = existing.ageYears?.toString().orEmpty(),
            reportedConditions = buildSet {
                if (existing.sleepApnea) add(ScreeningQuestion.SLEEP_APNEA)
                if (existing.chronicInsomnia) add(ScreeningQuestion.CHRONIC_INSOMNIA)
                if (existing.parasomnia) add(ScreeningQuestion.PARASOMNIA)
                if (existing.narcolepsy) add(ScreeningQuestion.NARCOLEPSY)
                if (existing.seizureDisorder) add(ScreeningQuestion.SEIZURE_DISORDER)
                if (existing.psychosisTreatment) add(ScreeningQuestion.PSYCHOSIS_TREATMENT)
            },
            acknowledgedNotMedicalDevice = existing.acknowledgedNotMedicalDevice
        )
    }

    fun updateAge(value: String) {
        // Digits only: the field is numeric and a stray character would silently invalidate it.
        _uiState.value = _uiState.value.copy(ageInput = value.filter { it.isDigit() }.take(3))
    }

    fun setCondition(question: ScreeningQuestion, reported: Boolean) {
        val current = _uiState.value.reportedConditions
        _uiState.value = _uiState.value.copy(
            reportedConditions = if (reported) current + question else current - question
        )
    }

    fun setAcknowledged(value: Boolean) {
        _uiState.value = _uiState.value.copy(acknowledgedNotMedicalDevice = value)
    }

    /**
     * Persists the screening onto the user profile.
     *
     * @return the stored screening, or null when the form is not yet valid.
     */
    suspend fun submit(): SafetyScreening? {
        val state = _uiState.value
        if (!state.canSubmit) return null

        _uiState.value = state.copy(isSaving = true)

        val screening = state.toScreening()
        val profile = profileRepository.getUserProfile().first()
        profileRepository.updateProfile(profile.copy(screening = screening))

        _uiState.value = _uiState.value.copy(isSaving = false, savedScreening = screening)
        return screening
    }
}
