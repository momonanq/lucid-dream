package com.luciddream.model

import kotlinx.serialization.Serializable

/**
 * Supported induction modes in the Samsung Lucid MVP.
 */
@Serializable
enum class NightMode(val displayName: String, val description: String) {
    /**
     * Dream journal, reality checks, morning recall drills, no night cues.
     */
    BEGINNER(
        displayName = "Beginner",
        description = "Дневник снов, reality checks, утренний recall, без ночных триггеров"
    ),

    /**
     * Wake-Back-To-Bed: Gentle alarm after 4.5–6 hours of sleep, followed by MILD/TLR.
     */
    WBTB(
        displayName = "WBTB",
        description = "Мягкий будильник через 4.5–6 часов после сна, затем MILD/TLR"
    ),

    /**
     * Targeted Lucidity Reactivation: pre-sleep intention paired with soft sound/vibration cues.
     */
    TLR(
        displayName = "TLR",
        description = "Связка предсонного намерения с аудио/вибро cue во время сна"
    ),

    /**
     * Probabilistic nocturnal haptic cues based on continuous Galaxy Watch biometric sensors.
     */
    WATCH_ASSIST(
        displayName = "Watch Assist",
        description = "Вероятностные ночные хаптические cue на основе watch-метрик"
    )
}
