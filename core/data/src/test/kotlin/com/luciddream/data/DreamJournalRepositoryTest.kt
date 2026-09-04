package com.luciddream.data

import com.luciddream.data.repository.InMemoryDreamJournalRepository
import com.luciddream.model.DreamEntry
import com.luciddream.model.DreamTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DreamJournalRepositoryTest {

    private val repository = InMemoryDreamJournalRepository()

    @Test
    fun `extracts dream signs from narrative text`() = runTest {
        val narrative = "Я летал над школой, а затем посмотрел на свои странные руки и понял, что сплю."
        val signs = repository.extractDreamSigns(narrative)

        val keywords = signs.map { it.keyword }
        assertTrue(keywords.contains("летал"))
        assertTrue(keywords.contains("школа"))
        assertTrue(keywords.contains("странные руки"))
    }

    @Test
    fun `saves and retrieves dream entries with aggregated top signs`() = runTest {
        val signs = repository.extractDreamSigns("Сегодня ночью снова летал над городом.")
        val entry = DreamEntry(
            id = "e1",
            dateIso = "2026-08-26",
            timestampMs = System.currentTimeMillis(),
            title = "Полёт",
            transcript = "Сегодня ночью снова летал над городом.",
            tags = setOf(DreamTag.LUCID, DreamTag.FLYING),
            dreamSigns = signs,
            lucidityLevel = 4
        )

        repository.saveEntry(entry)
        val entries = repository.getAllEntries().first()
        assertEquals(1, entries.size)
        assertEquals("Полёт", entries.first().title)

        val topSigns = repository.getTopDreamSigns().first()
        assertTrue(topSigns.any { it.keyword == "летал" })
    }
}
