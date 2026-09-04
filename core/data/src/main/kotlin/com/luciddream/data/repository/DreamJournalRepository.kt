package com.luciddream.data.repository

import com.luciddream.model.DreamEntry
import com.luciddream.model.DreamSign
import com.luciddream.model.DreamTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

interface DreamJournalRepository {
    fun getAllEntries(): Flow<List<DreamEntry>>
    suspend fun getEntryById(id: String): DreamEntry?
    suspend fun saveEntry(entry: DreamEntry)
    suspend fun deleteEntry(id: String)
    suspend fun extractDreamSigns(transcript: String): List<DreamSign>
    fun getTopDreamSigns(): Flow<List<DreamSign>>
}

class InMemoryDreamJournalRepository : DreamJournalRepository {
    private val entriesState = MutableStateFlow<List<DreamEntry>>(emptyList())

    override fun getAllEntries(): Flow<List<DreamEntry>> = entriesState.asStateFlow()

    override suspend fun getEntryById(id: String): DreamEntry? {
        return entriesState.value.find { it.id == id }
    }

    override suspend fun saveEntry(entry: DreamEntry) {
        val current = entriesState.value.toMutableList()
        val index = current.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            current[index] = entry
        } else {
            current.add(0, entry)
        }
        entriesState.value = current
    }

    override suspend fun deleteEntry(id: String) {
        entriesState.value = entriesState.value.filterNot { it.id == id }
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
        val allSigns = entriesState.value.flatMap { it.dreamSigns }
        val aggregated = allSigns.groupBy { it.keyword }.map { (keyword, list) ->
            DreamSign(
                keyword = keyword,
                category = list.first().category,
                occurrenceCount = list.sumOf { it.occurrenceCount }
            )
        }.sortedByDescending { it.occurrenceCount }

        return MutableStateFlow(aggregated).asStateFlow()
    }
}
