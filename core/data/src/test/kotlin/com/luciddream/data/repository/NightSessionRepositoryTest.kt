package com.luciddream.data.repository

import com.luciddream.model.NightMode
import com.luciddream.model.NightSession
import com.luciddream.model.SessionStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NightSessionRepositoryTest {

    @Test
    fun `getActiveSession reactively emits current running session upon status change`() = runTest {
        val repo = InMemoryNightSessionRepository()

        // 1. Initially no active session
        val initialActive = repo.getActiveSession().first()
        assertNull(initialActive)

        // 2. Start running session
        val session1 = NightSession(
            id = "s1",
            startTimeMs = 1000L,
            mode = NightMode.WATCH_ASSIST,
            status = SessionStatus.RUNNING
        )
        repo.saveSession(session1)

        val activeNow = repo.getActiveSession().first()
        assertNotNull(activeNow)
        assertEquals("s1", activeNow?.id)

        // 3. Complete session
        repo.saveSession(session1.copy(status = SessionStatus.COMPLETED, endTimeMs = 2000L))

        val afterCompletion = repo.getActiveSession().first()
        assertNull(afterCompletion, "Completed session should no longer be returned as active")
    }
}
