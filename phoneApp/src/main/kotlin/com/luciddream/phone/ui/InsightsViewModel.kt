package com.luciddream.phone.ui

import com.luciddream.data.repository.AggregateAnalytics
import com.luciddream.data.repository.AnalyticsRepository
import com.luciddream.data.repository.NightSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class InsightsUiState(
    val analytics: AggregateAnalytics = AggregateAnalytics(
        totalSessionsCount = 0,
        totalMorningReportsCount = 0,
        recallPercentage = 0.0,
        lucidPercentage = 0.0,
        cueNoticedPercentage = 0.0,
        wakeSpikePercentage = 0.0,
        successByMode = emptyMap(),
        averageSubjectiveQuality = 0.0
    ),
    val keyFindings: List<String> = emptyList()
)

class InsightsViewModel(
    private val sessionRepository: NightSessionRepository,
    private val analyticsRepository: AnalyticsRepository
) {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    suspend fun loadInsights() {
        val sessions = sessionRepository.getAllSessions().first()
        val reports = sessionRepository.getAllMorningReports().first()

        val analytics = analyticsRepository.computeAnalytics(sessions, reports)
        val findings = mutableListOf<String>()

        if (analytics.recallPercentage >= 70.0) {
            findings.add("Высокий уровень вспоминания снов (${String.format("%.1f", analytics.recallPercentage)}%). Это отличная база для техник MILD и SSILD.")
        }
        if (analytics.lucidPercentage > 0.0) {
            findings.add("Частота осознанных снов составила ${String.format("%.1f", analytics.lucidPercentage)}% от всех ночей.")
        }
        if (analytics.wakeSpikePercentage < 20.0 && analytics.totalSessionsCount > 0) {
            findings.add("Ночные сигналы мягко интегрируются в сон: доля пробуждений от cue менее ${String.format("%.1f", analytics.wakeSpikePercentage)}%.")
        }

        _uiState.value = InsightsUiState(
            analytics = analytics,
            keyFindings = findings
        )
    }
}
