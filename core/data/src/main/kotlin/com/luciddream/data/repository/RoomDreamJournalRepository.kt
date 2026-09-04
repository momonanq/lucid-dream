package com.luciddream.data.repository

import com.luciddream.data.db.DreamEntryEntity
import com.luciddream.data.db.DreamJournalDao
import com.luciddream.model.DreamEntry
import com.luciddream.model.DreamSign
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDreamJournalRepository(
    private val dao: DreamJournalDao
) : DreamJournalRepository {

    override fun getAllEntries(): Flow<List<DreamEntry>> {
        return dao.getAllEntries().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getEntryById(id: String): DreamEntry? {
        return dao.getEntryById(id)?.toDomain()
    }

    override suspend fun saveEntry(entry: DreamEntry) {
        dao.insertEntry(entry.toEntity())
    }

    override suspend fun deleteEntry(id: String) {
        dao.deleteEntry(id)
    }

    override suspend fun extractDreamSigns(transcript: String): List<DreamSign> {
        val signs = mutableListOf<DreamSign>()
        val lowercase = transcript.lowercase()

        val keywordCategories = mapOf(
            "Action" to listOf("летал", "парил", "падал", "бежал", "исчез", "дышал", "телепорт"),
            "Context" to listOf("школ", "старая квартира", "город", "космос", "остров", "планет"),
            "Form" to listOf("чудовищ", "странные руки", "руки", "телефон", "зеркал", "двойник"),
            "Awareness" to listOf("подозрени", "нереальност", "ложное пробуждение", "темнот", "сонный паралич")
        )

        for ((category, keywords) in keywordCategories) {
            for (kw in keywords) {
                if (lowercase.contains(kw)) {
                    val canonicalName = when (kw) {
                        "школ" -> "школа"
                        "планет" -> "планета"
                        "город" -> "город"
                        "зеркал" -> "искажённое зеркало"
                        "чудовищ" -> "чудовище"
                        "темнот" -> "темнота"
                        "подозрени" -> "подозрение"
                        "нереальност" -> "нереальность"
                        else -> kw
                    }
                    if (signs.none { it.keyword == canonicalName }) {
                        signs.add(DreamSign(keyword = canonicalName, category = category, occurrenceCount = 1))
                    }
                }
            }
        }

        return signs
    }

    override fun getTopDreamSigns(): Flow<List<DreamSign>> {
        return getAllEntries().map { entries ->
            entries.flatMap { it.dreamSigns }
                .groupBy { it.keyword }
                .map { (keyword, list) ->
                    DreamSign(
                        keyword = keyword,
                        category = list.first().category,
                        occurrenceCount = list.sumOf { it.occurrenceCount }
                    )
                }
                .sortedByDescending { it.occurrenceCount }
        }
    }

    private fun DreamEntryEntity.toDomain(): DreamEntry {
        return DreamEntry(
            id = id,
            dateIso = dateIso,
            timestampMs = timestampMs,
            title = title,
            transcript = transcript,
            tags = tags.toSet(),
            dreamSigns = signs,
            clarityRating = clarityRating,
            lucidityLevel = lucidityLevel
        )
    }

    private fun DreamEntry.toEntity(): DreamEntryEntity {
        return DreamEntryEntity(
            id = id,
            dateIso = dateIso,
            timestampMs = timestampMs,
            title = title,
            transcript = transcript,
            tags = tags.toList(),
            signs = dreamSigns,
            clarityRating = clarityRating,
            lucidityLevel = lucidityLevel
        )
    }
}
