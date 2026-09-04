package com.luciddream.data.repository

import com.luciddream.model.MorningReport
import com.luciddream.model.NightMode
import com.luciddream.model.NightSession
import kotlinx.serialization.Serializable

@Serializable
data class AggregateAnalytics(
    val totalSessionsCount: Int,
    val totalMorningReportsCount: Int,
    val recallPercentage: Double,
    val lucidPercentage: Double,
    val cueNoticedPercentage: Double,
    val wakeSpikePercentage: Double,
    val successByMode: Map<String, Double>,
    val averageSubjectiveQuality: Double
)

class AnalyticsRepository(
    private val sessionRepository: NightSessionRepository
) {

    fun computeAnalytics(
        sessions: List<NightSession>,
        reports: List<MorningReport>
    ): AggregateAnalytics {
        if (sessions.isEmpty() || reports.isEmpty()) {
            return AggregateAnalytics(
                totalSessionsCount = sessions.size,
                totalMorningReportsCount = reports.size,
                recallPercentage = 0.0,
                lucidPercentage = 0.0,
                cueNoticedPercentage = 0.0,
                wakeSpikePercentage = 0.0,
                successByMode = emptyMap(),
                averageSubjectiveQuality = 0.0
            )
        }

        val totalReports = reports.size
        val recallCount = reports.count { it.hadDreams }
        val lucidCount = reports.count { it.lucidSuccess }
        val cueNoticedCount = reports.count { it.cueDetectedInDream }

        val allCues = sessions.flatMap { it.cueEvents }
        val wakeSpikesCount = allCues.count { it.wakeSpikeAfter }
        val wakeSpikePercentage = if (allCues.isNotEmpty()) {
            (wakeSpikesCount.toDouble() / allCues.size.toDouble()) * 100.0
        } else {
            0.0
        }

        // Mode breakdown
        val modeSuccessMap = mutableMapOf<String, Double>()
        for (mode in NightMode.entries) {
            val sessionsInMode = sessions.filter { it.mode == mode }.map { it.id }.toSet()
            val reportsInMode = reports.filter { it.sessionId in sessionsInMode }
            if (reportsInMode.isNotEmpty()) {
                val lucidInMode = reportsInMode.count { it.lucidSuccess }
                modeSuccessMap[mode.name] = (lucidInMode.toDouble() / reportsInMode.size.toDouble()) * 100.0
            }
        }

        val avgQuality = reports.map { it.subjectiveSleepQuality }.average()

        return AggregateAnalytics(
            totalSessionsCount = sessions.size,
            totalMorningReportsCount = totalReports,
            recallPercentage = (recallCount.toDouble() / totalReports.toDouble()) * 100.0,
            lucidPercentage = (lucidCount.toDouble() / totalReports.toDouble()) * 100.0,
            cueNoticedPercentage = (cueNoticedCount.toDouble() / totalReports.toDouble()) * 100.0,
            wakeSpikePercentage = wakeSpikePercentage,
            successByMode = modeSuccessMap,
            averageSubjectiveQuality = if (avgQuality.isNaN()) 0.0 else avgQuality
        )
    }
}
