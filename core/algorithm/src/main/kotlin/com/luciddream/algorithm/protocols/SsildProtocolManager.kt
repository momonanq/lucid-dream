package com.luciddream.algorithm.protocols

import kotlinx.serialization.Serializable

/**
 * SSILD (Senses Initiated Lucid Dream) Protocol Manager.
 * Orchestrates cyclic sensory switching between Sight, Sound, and Touch modalities.
 */
class SsildProtocolManager {

    @Serializable
    enum class SensoryModality(val title: String, val prompt: String) {
        SIGHT(
            title = "Зрение (Sight)",
            prompt = "Закройте глаза. Направьте внимание на темноту за веками, световые пятна или гипнагогические образы, не напрягая глазные мышцы."
        ),
        SOUND(
            title = "Слух (Sound)",
            prompt = "Слушайте окружающие звуки (шум за окном, тишину комнаты, внутренний гул или звук дыхания)."
        ),
        TOUCH(
            title = "Осязание (Touch)",
            prompt = "Почувствуйте контакт тела с постелью, вес одеяла, температуру воздуха на лице, расслабленность мышц."
        )
    }

    @Serializable
    data class SsildCycle(
        val cycleIndex: Int,
        val isFastCycle: Boolean,
        val modality: SensoryModality,
        val durationSeconds: Int
    )

    /**
     * Generates a standard SSILD session routine:
     * - 4 quick cycles (5s per modality) to prime sensory attention
     * - 4 slow cycles (30s per modality) to induce hypnagogic threshold sensitivity
     */
    fun generateRoutine(
        quickCycleCount: Int = 4,
        quickCycleDurationSec: Int = 5,
        slowCycleCount: Int = 4,
        slowCycleDurationSec: Int = 30
    ): List<SsildCycle> {
        val routine = mutableListOf<SsildCycle>()
        var index = 1

        // Phase 1: Fast warmup cycles
        for (c in 1..quickCycleCount) {
            for (modality in SensoryModality.entries) {
                routine.add(
                    SsildCycle(
                        cycleIndex = index++,
                        isFastCycle = true,
                        modality = modality,
                        durationSeconds = quickCycleDurationSec
                    )
                )
            }
        }

        // Phase 2: Slow deep cycles
        for (c in 1..slowCycleCount) {
            for (modality in SensoryModality.entries) {
                routine.add(
                    SsildCycle(
                        cycleIndex = index++,
                        isFastCycle = false,
                        modality = modality,
                        durationSeconds = slowCycleDurationSec
                    )
                )
            }
        }

        return routine
    }
}
