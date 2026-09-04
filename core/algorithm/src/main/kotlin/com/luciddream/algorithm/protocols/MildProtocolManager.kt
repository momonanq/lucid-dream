package com.luciddream.algorithm.protocols

import kotlinx.serialization.Serializable

/**
 * MILD (Mnemonic Induction of Lucid Dreams) Protocol Manager.
 * Implements the laboratory-verified Aspy/LaBerge protocol for prospective memory training.
 */
class MildProtocolManager {

    @Serializable
    data class MildStep(
        val stepNumber: Int,
        val title: String,
        val description: String,
        val durationSeconds: Int,
        val mantraSuggestion: String? = null
    )

    fun getRehearsalSteps(customDreamSign: String? = null): List<MildStep> {
        val mantra = if (customDreamSign != null) {
            "В следующий раз, когда я увижу '$customDreamSign' или любой сюжет во сне, я вспомню, что сплю."
        } else {
            "В следующий раз, когда я буду видеть сон, я вспомню, что это сон."
        }

        return listOf(
            MildStep(
                stepNumber = 1,
                title = "Воспоминание недавнего сна (Dream Recall)",
                description = "Вспомните сюжет недавнего сна во всех доступных деталях: цвета, окружение, персонажей.",
                durationSeconds = 120
            ),
            MildStep(
                stepNumber = 2,
                title = "Поиск маркера сна (Dream Sign Identification)",
                description = "Найдите в сюжете сна необычный элемент, странность или невозможное событие (Dream Sign).",
                durationSeconds = 60
            ),
            MildStep(
                stepNumber = 3,
                title = "Формирование намерения (Prospective Intention)",
                description = "В расслабленном состоянии повторяйте про себя намерение осознать сон.",
                durationSeconds = 180,
                mantraSuggestion = mantra
            ),
            MildStep(
                stepNumber = 4,
                title = "Визуализация осознания (Mental Rehearsal)",
                description = "Представьте себя снова внутри этого сна, замечающим маркер сна и восклицающим: 'Это сон!' Почувствуйте уверенность и стабильность.",
                durationSeconds = 180
            ),
            MildStep(
                stepNumber = 5,
                title = "Отпускание и засыпание (Passive Release)",
                description = "Отпустите технику, примите удобную позу и позвольте себе заснуть в течение 5–10 минут.",
                durationSeconds = 300
            )
        )
    }
}
