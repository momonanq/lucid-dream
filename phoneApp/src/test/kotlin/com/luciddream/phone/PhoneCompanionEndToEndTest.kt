package com.luciddream.phone

import com.luciddream.data.repository.InMemoryDreamJournalRepository
import com.luciddream.data.repository.InMemoryNightSessionRepository
import com.luciddream.data.repository.InMemoryUserProfileRepository
import com.luciddream.data.samsung.MockSamsungHealthDataGateway
import com.luciddream.data.sync.CueTriggeredPayload
import com.luciddream.data.sync.QuickMorningFeedbackPayload
import com.luciddream.model.CueType
import com.luciddream.model.NightMode
import com.luciddream.phone.audio.TlrAudioEngine
import com.luciddream.phone.service.PhoneSessionCoordinator
import com.luciddream.phone.ui.DreamJournalViewModel
import com.luciddream.phone.ui.TonightViewModel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PhoneCompanionEndToEndTest {

    @Test
    fun `end to end phone workflow with dream journal and night session coordination`() = runTest {
        val sessionRepo = InMemoryNightSessionRepository()
        val profileRepo = InMemoryUserProfileRepository()
        val gateway = MockSamsungHealthDataGateway()
        val audioEngine = TlrAudioEngine()
        val coordinator = PhoneSessionCoordinator(sessionRepo, profileRepo, gateway, audioEngine)

        val tonightVm = TonightViewModel(coordinator, sessionRepo, profileRepo)
        val journalVm = DreamJournalViewModel(InMemoryDreamJournalRepository())

        // 1. Dream Journal logging
        journalVm.updateDraftTitle("Первый осознанный полет")
        journalVm.updateDraftTranscript("Я заметил искажённое зеркало, сделал проверку реальности и понял, что летаю.")
        journalVm.setLucidityRating(4)
        val savedDream = journalVm.saveCurrentEntry()

        assertEquals("Первый осознанный полет", savedDream.title)
        assertTrue(savedDream.dreamSigns.isNotEmpty())

        // 2. Start Tonight Session
        tonightVm.loadState()
        tonightVm.selectMode(NightMode.TLR)
        tonightVm.toggleAudioCues(true)
        val session = tonightVm.startTonightSession()
        assertNotNull(session)

        // 3. Receive Live Cue from Watch
        val cuePayload = CueTriggeredPayload(
            cueId = "cue_live_1",
            sessionId = session.id,
            timestampMs = session.startTimeMs + (300 * 60 * 1000L),
            cueType = CueType.COMBINED,
            intensity = 0.5,
            confidence = 0.82
        )
        coordinator.handleLiveCueEvent(cuePayload)

        // Verify audio cue was played by TlrAudioEngine
        val audioHistory = audioEngine.getHistory()
        assertEquals(1, audioHistory.size)
        assertEquals(432.0, audioHistory.first().frequencyHz)

        // 4. Morning completion with Samsung Health Data sync
        val morningFeedback = QuickMorningFeedbackPayload(
            sessionId = session.id,
            timestampMs = session.startTimeMs + (480 * 60 * 1000L),
            hadDream = true,
            hadLucidDream = true,
            noticedSignal = true
        )

        val (finishedSession, calibration) = coordinator.completeMorningSession(
            sessionId = session.id,
            endTimeMs = session.startTimeMs + (480 * 60 * 1000L),
            morningFeedback = morningFeedback
        )

        assertEquals(1, finishedSession.cuesTriggered)
        assertEquals(1, calibration.totalCuesDelivered)
        assertTrue(calibration.remAccuracyProxy >= 0.0)
        assertEquals(1, calibration.adaptedProfile.calibrationNightsCompleted)
    }
}
