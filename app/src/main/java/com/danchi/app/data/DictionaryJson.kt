package com.danchi.app.data

import com.danchi.app.domain.DictionaryEntry
import com.danchi.app.domain.DictionaryMeaning
import com.danchi.app.domain.WordbookMembership
import com.danchi.app.domain.getPosName
import com.danchi.app.domain.normalizePos
import org.json.JSONArray
import org.json.JSONObject

object DictionaryJson {
    fun encode(entry: DictionaryEntry): String {
        val root = JSONObject()
            .put("wordKey", entry.wordKey)
            .put("word", entry.word)
            .put("phonetic", entry.phonetic)
            .put("definition", entry.definition)
            .put("translation", entry.translation)
            .put("pos", entry.pos)
            .put("meanings", encodeMeanings(entry.meanings))
            .put("collins", entry.collins)
            .put("oxford", entry.oxford)
            .put("tag", entry.tag)
            .put("bnc", entry.bnc)
            .put("frq", entry.frq)
            .put("exchange", entry.exchange)
            .put("detail", entry.detail)
            .put("audio", entry.audio)
            .put("source", entry.source)
            .put("memberships", encodeMemberships(entry.memberships))
        return root.toString()
    }

    fun decode(json: String): DictionaryEntry? {
        if (json.isBlank()) return null
        return runCatching { decode(JSONObject(json)) }.getOrNull()
    }

    fun decode(root: JSONObject): DictionaryEntry {
        return DictionaryEntry(
            wordKey = root.optString("wordKey").ifBlank { normalizeWordKey(root.optString("word")) },
            word = root.optString("word"),
            phonetic = root.optString("phonetic"),
            definition = root.optString("definition"),
            translation = root.optString("translation"),
            pos = normalizePos(root.optString("pos")),
            meanings = decodeMeanings(root.optJSONArray("meanings")),
            collins = root.optInt("collins"),
            oxford = root.optInt("oxford"),
            tag = root.optString("tag"),
            bnc = root.optInt("bnc"),
            frq = root.optInt("frq"),
            exchange = root.optString("exchange"),
            detail = root.optString("detail"),
            audio = root.optString("audio"),
            source = root.optString("source", "ecdict"),
            memberships = decodeMemberships(root.optJSONArray("memberships"))
        )
    }

    fun encodeMeanings(meanings: List<DictionaryMeaning>): JSONArray {
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
        return array
    }

    fun decodeMeanings(array: JSONArray?): List<DictionaryMeaning> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val text = item.optString("meaning")
                    .ifBlank { item.optString("translation") }
                    .ifBlank { item.optString("definition") }
                if (text.isBlank()) continue
                val pos = normalizePos(item.optString("pos"))
                add(
                    DictionaryMeaning(
                        id = item.optString("id").ifBlank { "meaning-$index" },
                        pos = pos,
                        posName = item.optString("posName").ifBlank { getPosName(pos) },
                        meaning = text,
                        example = item.optString("example"),
                        translation = item.optString("translation")
                    )
                )
            }
        }
    }

    private fun encodeMemberships(memberships: List<WordbookMembership>): JSONArray {
        val array = JSONArray()
        memberships.forEach { item ->
            array.put(
                JSONObject()
                    .put("wordbookId", item.wordbookId)
                    .put("title", item.title)
                    .put("unit", item.unit)
                    .put("level", item.level)
                    .put("tags", JSONArray(item.tags))
                    .put("priority", item.priority)
                    .put("sortOrder", item.sortOrder)
            )
        }
        return array
    }

    private fun decodeMemberships(array: JSONArray?): List<WordbookMembership> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    WordbookMembership(
                        wordbookId = item.optString("wordbookId"),
                        title = item.optString("title"),
                        unit = item.optString("unit"),
                        level = item.optString("level"),
                        tags = jsonStringList(item.optJSONArray("tags")),
                        priority = item.optInt("priority"),
                        sortOrder = item.optInt("sortOrder")
                    )
                )
            }
        }
    }

    fun normalizeWordKey(word: String): String {
        return word.trim().lowercase()
    }

    private fun jsonStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }
}
