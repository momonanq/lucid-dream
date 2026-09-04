package com.luciddream.data.repository

import com.luciddream.model.MorningReport
import com.luciddream.model.NightSession
import com.luciddream.model.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface NightSessionRepository {
    fun getAllSessions(): Flow<List<NightSession>>
    fun getActiveSession(): Flow<NightSession?>
    suspend fun getSessionById(id: String): NightSession?
    suspend fun saveSession(session: NightSession)
    suspend fun saveMorningReport(report: MorningReport)
    fun getAllMorningReports(): Flow<List<MorningReport>>
    suspend fun getMorningReportBySessionId(sessionId: String): MorningReport?
}

class InMemoryNightSessionRepository : NightSessionRepository {
    private val sessionsState = MutableStateFlow<List<NightSession>>(emptyList())
    private val reportsState = MutableStateFlow<List<MorningReport>>(emptyList())

    override fun getAllSessions(): Flow<List<NightSession>> = sessionsState.asStateFlow()

    override fun getActiveSession(): Flow<NightSession?> {
        val active = sessionsState.value.find { it.status == SessionStatus.RUNNING }
        return MutableStateFlow(active).asStateFlow()
    }

    override suspend fun getSessionById(id: String): NightSession? {
        return sessionsState.value.find { it.id == id }
    }

    override suspend fun saveSession(session: NightSession) {
        val current = sessionsState.value.toMutableList()
        val index = current.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            current[index] = session
        } else {
            current.add(0, session)
        }
        sessionsState.value = current
    }

    override suspend fun saveMorningReport(report: MorningReport) {
        val current = reportsState.value.toMutableList()
        val index = current.indexOfFirst { it.id == report.id }
        if (index >= 0) {
            current[index] = report
        } else {
            current.add(0, report)
        }
        reportsState.value = current
    }

    override fun getAllMorningReports(): Flow<List<MorningReport>> = reportsState.asStateFlow()

    override suspend fun getMorningReportBySessionId(sessionId: String): MorningReport? {
        return reportsState.value.find { it.sessionId == sessionId }
    }
}
