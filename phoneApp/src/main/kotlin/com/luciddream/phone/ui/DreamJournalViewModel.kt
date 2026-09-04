package com.luciddream.phone.ui

import com.luciddream.data.repository.DreamJournalRepository
import com.luciddream.model.DreamEntry
import com.luciddream.model.DreamSign
import com.luciddream.model.DreamTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID

data class DreamJournalUiState(
    val entries: List<DreamEntry> = emptyList(),
    val topDreamSigns: List<DreamSign> = emptyList(),
    val isRecordingVoice: Boolean = false,
    val draftTitle: String = "",
    val draftTranscript: String = "",
    val draftTags: Set<DreamTag> = emptySet(),
    val draftLucidityRating: Int = 0
)

class DreamJournalViewModel(
    private val journalRepository: DreamJournalRepository
) {

    private val _uiState = MutableStateFlow(DreamJournalUiState())
    val uiState: StateFlow<DreamJournalUiState> = _uiState.asStateFlow()

    suspend fun loadEntries() {
        val entries = journalRepository.getAllEntries().first()
        val topSigns = journalRepository.getTopDreamSigns().first()
        _uiState.value = _uiState.value.copy(
            entries = entries,
            topDreamSigns = topSigns
        )
    }

    fun updateDraftTitle(title: String) {
        _uiState.value = _uiState.value.copy(draftTitle = title)
    }

    fun updateDraftTranscript(transcript: String) {
        _uiState.value = _uiState.value.copy(draftTranscript = transcript)
    }

    fun toggleTag(tag: DreamTag) {
        val current = _uiState.value.draftTags.toMutableSet()
        if (current.contains(tag)) {
            current.remove(tag)
        } else {
            current.add(tag)
        }
        _uiState.value = _uiState.value.copy(draftTags = current)
    }

    fun setLucidityRating(level: Int) {
        _uiState.value = _uiState.value.copy(draftLucidityRating = level.coerceIn(0, 5))
    }

    suspend fun saveCurrentEntry(): DreamEntry {
        val state = _uiState.value
        val extractedSigns = journalRepository.extractDreamSigns(state.draftTranscript)

        val entry = DreamEntry(
            id = "dream_${UUID.randomUUID().toString().take(8)}",
            dateIso = LocalDate.now().toString(),
            timestampMs = System.currentTimeMillis(),
            title = state.draftTitle.ifBlank { "Сон от ${LocalDate.now()}" },
            transcript = state.draftTranscript,
            tags = state.draftTags,
            dreamSigns = extractedSigns,
            lucidityLevel = state.draftLucidityRating
        )

        journalRepository.saveEntry(entry)
        loadEntries()

        // Reset draft
        _uiState.value = _uiState.value.copy(
            draftTitle = "",
            draftTranscript = "",
            draftTags = emptySet(),
            draftLucidityRating = 0
        )

        return entry
    }
}
