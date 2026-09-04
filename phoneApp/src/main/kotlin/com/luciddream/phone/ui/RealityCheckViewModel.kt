package com.luciddream.phone.ui

import com.luciddream.algorithm.protocols.RealityCheckScheduler
import com.luciddream.model.RealityCheckLog
import com.luciddream.model.RealityCheckPrompt
import com.luciddream.model.RealityCheckType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class RealityCheckUiState(
    val prompts: List<RealityCheckPrompt> = emptyList(),
    val dailyScheduleMinutes: List<Int> = emptyList(),
    val todayLogs: List<RealityCheckLog> = emptyList(),
    val totalMindfulCompletedToday: Int = 0
)

class RealityCheckViewModel(
    private val scheduler: RealityCheckScheduler = RealityCheckScheduler()
) {

    private val _uiState = MutableStateFlow(RealityCheckUiState())
    val uiState: StateFlow<RealityCheckUiState> = _uiState.asStateFlow()

    fun loadSchedule() {
        val prompts = scheduler.getDefaultPrompts()
        val schedule = scheduler.generateDailySchedule()
        _uiState.value = _uiState.value.copy(
            prompts = prompts,
            dailyScheduleMinutes = schedule
        )
    }

    fun logRealityCheck(
        type: RealityCheckType,
        wasMindful: Boolean,
        doubtReality: Boolean,
        note: String = ""
    ): RealityCheckLog {
        val log = RealityCheckLog(
            id = "rc_${UUID.randomUUID().toString().take(8)}",
            timestampMs = System.currentTimeMillis(),
            type = type,
            wasMindful = wasMindful,
            doubtRealityGenerated = doubtReality,
            note = note
        )

        val updatedLogs = _uiState.value.todayLogs + log
        _uiState.value = _uiState.value.copy(
            todayLogs = updatedLogs,
            totalMindfulCompletedToday = updatedLogs.count { it.wasMindful }
        )
        return log
    }
}
