package com.luciddream.data.samsung

import com.luciddream.model.SleepImport
import com.luciddream.model.SleepStage
import com.luciddream.model.SleepStageInterval

/**
 * Gateway abstraction for Samsung Health Data SDK.
 * Enables import of sleep sessions, sleep stage timelines, blood oxygen (SpO2), and skin temperature.
 */
interface SamsungHealthDataGateway {
    suspend fun isSamsungHealthAvailable(): Boolean
    suspend fun requestPermissions(): Boolean
    suspend fun importSleepSession(sessionId: String, startMs: Long, endMs: Long): SleepImport?
}

class MockSamsungHealthDataGateway : SamsungHealthDataGateway {
    override suspend fun isSamsungHealthAvailable(): Boolean = true
    override suspend fun requestPermissions(): Boolean = true

    override suspend fun importSleepSession(sessionId: String, startMs: Long, endMs: Long): SleepImport {
        val totalDurationMs = endMs - startMs
        val totalMinutes = (totalDurationMs / 60000).toInt()

        // Generate synthetic realistic sleep architecture
        val stages = mutableListOf<SleepStageInterval>()
        var cursor = startMs

        // 1. Initial falling asleep & Light N1-N2 (~30 min)
        val stage1End = (cursor + 30 * 60 * 1000L).coerceAtMost(endMs)
        stages.add(SleepStageInterval(SleepStage.LIGHT, cursor, stage1End))
        cursor = stage1End

        // 2. Slow-wave deep sleep N3 (~60 min)
        val stage2End = (cursor + 60 * 60 * 1000L).coerceAtMost(endMs)
        stages.add(SleepStageInterval(SleepStage.DEEP, cursor, stage2End))
        cursor = stage2End

        // 3. Cycle 1 early brief REM (~20 min: 90m - 110m)
        val stage3End = (cursor + 20 * 60 * 1000L).coerceAtMost(endMs)
        stages.add(SleepStageInterval(SleepStage.REM, cursor, stage3End))
        cursor = stage3End

        // 4. Cycle 2 NREM (~90 min: 110m - 200m)
        val stage4End = (cursor + 90 * 60 * 1000L).coerceAtMost(endMs)
        stages.add(SleepStageInterval(SleepStage.LIGHT, cursor, stage4End))
        cursor = stage4End

        // 5. Cycle 2 expanded REM (~45 min: 200m - 245m)
        val stage5End = (cursor + 45 * 60 * 1000L).coerceAtMost(endMs)
        stages.add(SleepStageInterval(SleepStage.REM, cursor, stage5End))
        cursor = stage5End

        // 6. Cycle 3 NREM (~65 min: 245m - 310m)
        val stage6End = (cursor + 65 * 60 * 1000L).coerceAtMost(endMs)
        stages.add(SleepStageInterval(SleepStage.LIGHT, cursor, stage6End))
        cursor = stage6End

        // 7. Cycle 3 Peak REM (~70 min: 310m - 380m)
        val stage7End = (cursor + 70 * 60 * 1000L).coerceAtMost(endMs)
        stages.add(SleepStageInterval(SleepStage.REM, cursor, stage7End))
        cursor = stage7End

        // 8. Remaining time -> Light/Awake
        if (cursor < endMs) {
            stages.add(SleepStageInterval(SleepStage.LIGHT, cursor, endMs))
        }

        val remMinutes = stages.filter { it.stage == SleepStage.REM }.sumOf { it.durationMinutes }.toInt()
        val deepMinutes = stages.filter { it.stage == SleepStage.DEEP }.sumOf { it.durationMinutes }.toInt()
        val lightMinutes = stages.filter { it.stage == SleepStage.LIGHT }.sumOf { it.durationMinutes }.toInt()
        val awakeMinutes = stages.filter { it.stage == SleepStage.AWAKE }.sumOf { it.durationMinutes }.toInt()

        return SleepImport(
            id = "samsung_sleep_$sessionId",
            sessionId = sessionId,
            source = "Samsung Health Data SDK (Galaxy Watch 4+)",
            sleepScore = 84,
            totalSleepMinutes = totalMinutes,
            remMinutes = remMinutes,
            deepMinutes = deepMinutes,
            lightMinutes = lightMinutes,
            awakeMinutes = awakeMinutes,
            stages = stages,
            averageBloodOxygenPercentage = 97.2,
            skinTemperatureCelsius = 35.8
        )
    }
}
