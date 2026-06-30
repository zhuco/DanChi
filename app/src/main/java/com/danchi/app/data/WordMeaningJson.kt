package com.danchi.app.data

import com.danchi.app.domain.WordMeaning
import com.danchi.app.domain.getPosName
import com.danchi.app.domain.normalizePos
import org.json.JSONArray
import org.json.JSONObject

object WordMeaningJson {
    fun encode(meanings: List<WordMeaning>): String {
        if (meanings.isEmpty()) return ""
        val array = JSONArray()
        meanings.forEach { meaning ->
            val pos = normalizePos(meaning.pos)
            array.put(
                JSONObject()
                    .put("id", meaning.id)
                    .put("pos", pos)
                    .put("posName", meaning.posName.ifBlank { getPosName(pos) })
                    .put("meaning", meaning.meaning)
                    .put("example", meaning.example)
                    .put("translation", meaning.translation)
            )
        }
        return array.toString()
    }

    fun decode(json: String): List<WordMeaning> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            fromJsonArray(JSONArray(json))
        }.getOrDefault(emptyList())
    }

    fun fromJsonArray(array: JSONArray?): List<WordMeaning> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.opt(index)
                when (value) {
                    is JSONObject -> value.toWordMeaning(index)?.let(::add)
                    is String -> {
                        val text = value.trim()
                        if (text.isNotBlank()) {
                            add(
                                WordMeaning(
                                    id = "meaning-$index",
                                    meaning = text
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun JSONObject.toWordMeaning(index: Int): WordMeaning? {
        val rawMeaning = firstString("meaning", "translation", "definition", "cn", "text")
        if (rawMeaning.isBlank()) return null
        val rawPos = firstString("pos", "partOfSpeech", "part_of_speech", "wordType", "type")
        val pos = normalizePos(rawPos)
        return WordMeaning(
            id = optString("id").ifBlank { "meaning-$index" },
            pos = pos,
            posName = firstString("posName", "pos_name").ifBlank { getPosName(pos) },
            meaning = rawMeaning.trim(),
            example = firstString("example", "sentence"),
            translation = firstString("translation", "exampleCn", "translationExample", "sentenceCn")
        )
    }

    private fun JSONObject.firstString(vararg names: String): String {
        for (name in names) {
            val value = optString(name).trim()
            if (value.isNotBlank()) return value
        }
        return ""
    }
}
