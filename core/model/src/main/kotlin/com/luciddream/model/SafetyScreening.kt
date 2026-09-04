package com.luciddream.model

import kotlinx.serialization.Serializable

/**
 * Onboarding screening answers used to decide whether nocturnal cue modes are appropriate.
 *
 * The product is consumer wellness software, not a medical device, and deliberately delivers
 * sensory stimuli during sleep. That makes sleep fragmentation a real harm vector for people
 * whose sleep is already disordered, so cue-based modes are withheld from those groups rather
 * than merely disclaimed.
 *
 * All flags default to false ("not reported") so an unanswered screening never silently
 * excludes a user; [isComplete] distinguishes "answered no" from "never asked".
 */
@Serializable
data class SafetyScreening(
    val isComplete: Boolean = false,
    val ageYears: Int? = null,

    /** Diagnosed or suspected obstructive/central sleep apnea. */
    val sleepApnea: Boolean = false,

    /** Diagnosed chronic insomnia, or ongoing treatment for it. */
    val chronicInsomnia: Boolean = false,

    /** Sleepwalking, night terrors, REM sleep behaviour disorder, sleep paralysis distress. */
    val parasomnia: Boolean = false,

    /** Narcolepsy or other diagnosed hypersomnia. */
    val narcolepsy: Boolean = false,

    /** Epilepsy or a seizure disorder — sleep deprivation is a known seizure trigger. */
    val seizureDisorder: Boolean = false,

    /** Current treatment for a psychotic disorder, where dissociative practices carry added risk. */
    val psychosisTreatment: Boolean = false,

    /** User acknowledged the product is not a medical device and does not diagnose sleep stages. */
    val acknowledgedNotMedicalDevice: Boolean = false
) {

    /**
     * Conditions under which nocturnal cues are withheld, as human-readable reasons.
     * Empty means no exclusion applies.
     */
    val exclusionReasons: List<String>
        get() = buildList {
            if (ageYears != null && ageYears < MINIMUM_AGE_YEARS) {
                add("Ночные сигналы доступны с $MINIMUM_AGE_YEARS лет.")
            }
            if (sleepApnea) add("Апноэ сна: фрагментация сна усугубляет ночную гипоксию.")
            if (chronicInsomnia) add("Хроническая бессонница: ночные пробуждения закрепляют расстройство.")
            if (parasomnia) add("Парасомнии: внешние стимулы в REM могут провоцировать эпизоды.")
            if (narcolepsy) add("Нарколепсия: требуется наблюдение врача, а не самостоятельная практика.")
            if (seizureDisorder) add("Судорожные расстройства: депривация сна — известный триггер приступов.")
            if (psychosisTreatment) add("Лечение психотического расстройства: практики диссоциации несут дополнительный риск.")
            if (isComplete && !acknowledgedNotMedicalDevice) {
                add("Не подтверждено понимание, что приложение не является медицинским устройством.")
            }
        }

    /** True when cue-based night modes may be offered at all. */
    val allowsCueModes: Boolean
        get() = isComplete && exclusionReasons.isEmpty()

    companion object {
        const val MINIMUM_AGE_YEARS = 18
    }
}
