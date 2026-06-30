package com.danchi.app.domain

data class DictionaryMeaning(
    val id: String,
    val pos: String = "",
    val posName: String = "",
    val meaning: String,
    val example: String = "",
    val translation: String = ""
)

data class WordbookMembership(
    val wordbookId: String,
    val title: String = "",
    val unit: String = "",
    val level: String = "",
    val tags: List<String> = emptyList(),
    val priority: Int = 0,
    val sortOrder: Int = 0
)

data class DictionaryEntry(
    val wordKey: String,
    val word: String,
    val phonetic: String = "",
    val definition: String = "",
    val translation: String = "",
    val pos: String = "",
    val meanings: List<DictionaryMeaning> = emptyList(),
    val collins: Int = 0,
    val oxford: Int = 0,
    val tag: String = "",
    val bnc: Int = 0,
    val frq: Int = 0,
    val exchange: String = "",
    val detail: String = "",
    val audio: String = "",
    val source: String = "ecdict",
    val memberships: List<WordbookMembership> = emptyList()
)

data class WordPatch(
    val word: String,
    val phonetic: String = "",
    val meanings: List<WordMeaning> = emptyList(),
    val meaning: String = "",
    val translation: String = "",
    val source: String = "ecdict",
    val reason: String = ""
)

interface DictionaryRepository {
    suspend fun lookup(word: String): DictionaryEntry?
    suspend fun searchPrefix(query: String, limit: Int = 20): List<DictionaryEntry>
    suspend fun buildWordPatch(word: String): WordPatch?
}

fun DictionaryEntry.toWordPatch(reason: String = "dictionary lookup"): WordPatch {
    val wordMeanings = meanings.mapIndexed { index, meaning ->
        WordMeaning(
            id = meaning.id.ifBlank { "$wordKey-dict-$index" },
            pos = normalizePos(meaning.pos),
            posName = meaning.posName.ifBlank { getPosName(meaning.pos) },
            meaning = meaning.meaning,
            example = meaning.example,
            translation = meaning.translation
        )
    }
    return WordPatch(
        word = word,
        phonetic = phonetic,
        meanings = wordMeanings,
        meaning = wordMeanings.firstOrNull()?.meaning ?: translation,
        translation = translation,
        source = source,
        reason = reason
    )
}
