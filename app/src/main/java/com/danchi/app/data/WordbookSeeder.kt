package com.danchi.app.data

import android.content.Context
import com.danchi.app.domain.inferPosFromUnit
import com.danchi.app.domain.normalizeWordMeanings
import org.json.JSONArray
import org.json.JSONObject

data class WordbookSeedItem(
    val id: String,
    val title: String,
    val description: String,
    val sourceFile: String,
    val assetPath: String,
    val version: String,
    val wordCount: Int,
    val sortOrder: Int
) {
    fun toEntity(): WordbookEntity {
        return WordbookEntity(
            id = id,
            title = title,
            description = description,
            sourceFile = sourceFile,
            assetPath = assetPath,
            version = version,
            wordCount = wordCount,
            sortOrder = sortOrder
        )
    }
}

object WordbookSeeder {
    private const val ManifestPath = "wordbooks/manifest.json"
    private const val DefaultWordbookId = "zhongkao"
    private const val DefaultAssetPath = "wordbooks/zhongkao_words.json"
    private const val DefaultTitle = "初中中考"
    private const val DefaultSourceFile = "初中中考.txt"

    suspend fun seedIfNeeded(context: Context, wordbookDao: WordbookDao, wordDao: WordDao): Int {
        val wordbooks = loadManifest(context)
        wordbookDao.upsertAll(wordbooks.map { it.toEntity() })
        return wordbooks.sumOf { wordbook ->
            if (wordDao.countByWordbook(wordbook.id) > 0) {
                refreshLexicalFields(context, wordDao, wordbook)
                seedWordbook(context, wordDao, wordbook)
            } else {
                seedWordbook(context, wordDao, wordbook)
            }
        }
    }

    private suspend fun seedWordbook(context: Context, wordDao: WordDao, wordbook: WordbookSeedItem): Int {
        val json = context.assets.open(wordbook.assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val array = JSONArray(json)
        val words = buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val text = item.optString("word").trim()
                if (text.isBlank()) continue
                val wordId = item.optString("id").ifBlank { "${wordbook.id}_${index + 1}" }
                val meaning = lexicalMeaning(item)
                val unit = item.optString("unit", "默认单元")
                val pos = lexicalPos(item, unit)
                val example = lexicalExample(item)
                val exampleCn = lexicalExampleCn(item)
                add(
                    WordEntity(
                        id = wordId,
                        wordbookId = wordbook.id,
                        text = text,
                        meaning = meaning,
                        pos = pos,
                        meaningsJson = lexicalMeaningsJson(item, wordId, meaning, pos, example, exampleCn),
                        phonetic = item.optString("phonetic"),
                        example = example,
                        exampleCn = exampleCn,
                        root = item.optString("root"),
                        synonyms = jsonArrayToText(item.optJSONArray("synonyms")),
                        collocations = jsonArrayToText(item.optJSONArray("collocations")),
                        memoryTip = item.optString("memoryTip"),
                        book = item.optString("book", wordbook.title),
                        unit = unit,
                        source = item.optString("source", wordbook.sourceFile)
                    )
                )
            }
        }
        return wordDao.insertAll(words).count { it != -1L }
    }

    private suspend fun refreshLexicalFields(context: Context, wordDao: WordDao, wordbook: WordbookSeedItem) {
        val json = context.assets.open(wordbook.assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val array = JSONArray(json)
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val wordId = item.optString("id").ifBlank { "${wordbook.id}_${index + 1}" }
            if (wordId.isBlank()) continue
            val text = item.optString("word").trim()
            if (text.isBlank()) continue
            val meaning = lexicalMeaning(item)
            val unit = item.optString("unit", "默认单元")
            val pos = lexicalPos(item, unit)
            val example = lexicalExample(item)
            val exampleCn = lexicalExampleCn(item)
            val updatedById = wordDao.updateLexicalFields(
                wordId = wordId,
                meaning = meaning,
                pos = pos,
                meaningsJson = lexicalMeaningsJson(item, wordId, meaning, pos, example, exampleCn),
                phonetic = item.optString("phonetic"),
                example = example,
                exampleCn = exampleCn,
                root = item.optString("root"),
                synonyms = jsonArrayToText(item.optJSONArray("synonyms")),
                collocations = jsonArrayToText(item.optJSONArray("collocations")),
                memoryTip = item.optString("memoryTip")
            )
            if (updatedById == 0) {
                wordDao.updateLexicalFieldsByText(
                    wordbookId = wordbook.id,
                    text = text,
                    meaning = meaning,
                    pos = pos,
                    meaningsJson = lexicalMeaningsJson(item, wordId, meaning, pos, example, exampleCn),
                    phonetic = item.optString("phonetic"),
                    example = example,
                    exampleCn = exampleCn,
                    root = item.optString("root"),
                    synonyms = jsonArrayToText(item.optJSONArray("synonyms")),
                    collocations = jsonArrayToText(item.optJSONArray("collocations")),
                    memoryTip = item.optString("memoryTip")
                )
            }
        }
    }

    private fun loadManifest(context: Context): List<WordbookSeedItem> {
        val json = runCatching {
            context.assets.open(ManifestPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull() ?: return listOf(defaultWordbook())

        val array = JSONArray(json)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    WordbookSeedItem(
                        id = item.optString("id", DefaultWordbookId),
                        title = item.optString("title", DefaultTitle),
                        description = item.optString("description", ""),
                        sourceFile = item.optString("sourceFile", DefaultSourceFile),
                        assetPath = item.optString("assetPath", DefaultAssetPath),
                        version = item.optString("version", "1.0.0"),
                        wordCount = item.optInt("wordCount", 0),
                        sortOrder = item.optInt("sortOrder", index)
                    )
                )
            }
        }.ifEmpty { listOf(defaultWordbook()) }
    }

    private fun defaultWordbook(): WordbookSeedItem {
        return WordbookSeedItem(
            id = DefaultWordbookId,
            title = DefaultTitle,
            description = "中考核心词汇离线词库",
            sourceFile = DefaultSourceFile,
            assetPath = DefaultAssetPath,
            version = "1.1.0",
            wordCount = 1974,
            sortOrder = 0
        )
    }

    private fun lexicalMeaning(item: JSONObject): String {
        return item.optString("meaning")
            .ifBlank { item.optString("translation") }
            .ifBlank { item.optString("definition") }
            .ifBlank { item.optString("text") }
            .ifBlank { "暂未补充释义" }
    }

    private fun lexicalPos(item: JSONObject, unit: String): String {
        return item.optString("pos")
            .ifBlank { item.optString("partOfSpeech") }
            .ifBlank { item.optString("part_of_speech") }
            .ifBlank { item.optString("wordType") }
            .ifBlank { item.optString("type") }
            .ifBlank { inferPosFromUnit(unit) }
    }

    private fun lexicalMeaningsJson(
        item: JSONObject,
        wordId: String,
        meaning: String,
        pos: String,
        example: String,
        exampleCn: String
    ): String {
        val rawMeanings = WordMeaningJson.fromJsonArray(item.optJSONArray("meanings")) +
            WordMeaningJson.fromJsonArray(item.optJSONArray("definitions"))
        val meanings = normalizeWordMeanings(
            wordId = wordId,
            rawMeaning = meaning,
            rawPos = pos,
            rawMeanings = rawMeanings,
            example = example,
            translation = exampleCn
        )
        return WordMeaningJson.encode(meanings)
    }

    private fun lexicalExample(item: JSONObject): String {
        return item.optString("example").ifBlank {
            val word = item.optString("word")
            "I wrote the word \"$word\" next to its meaning in my notebook."
        }
    }

    private fun lexicalExampleCn(item: JSONObject): String {
        return item.optString("exampleCn").ifBlank { item.optString("exampleTranslation") }.ifBlank {
            val word = item.optString("word")
            "我把“$word”和它的中文释义写在笔记本上。"
        }
    }

    private fun jsonArrayToText(array: JSONArray?): String {
        if (array == null) return ""
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty()) add(value)
            }
        }.joinToString("\n")
    }
}
