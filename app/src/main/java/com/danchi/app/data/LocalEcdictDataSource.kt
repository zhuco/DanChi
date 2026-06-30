package com.danchi.app.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.danchi.app.domain.DictionaryEntry
import com.danchi.app.domain.DictionaryMeaning
import com.danchi.app.domain.WordbookMembership
import com.danchi.app.domain.normalizeWordMeanings
import java.io.File

class LocalEcdictDataSource(
    private val context: Context,
    private val assetPath: String = "databases/ecdict.db",
    private val databaseName: String = "ecdict.db"
) {
    fun lookup(word: String): DictionaryEntry? {
        val wordKey = DictionaryJson.normalizeWordKey(word)
        if (wordKey.isBlank()) return null
        return openDatabaseOrNull()?.use { db ->
            val table = findEntryTable(db) ?: return@use null
            queryEntry(db, table, wordKey)
        }
    }

    fun searchPrefix(query: String, limit: Int): List<DictionaryEntry> {
        val prefix = DictionaryJson.normalizeWordKey(query)
        if (prefix.isBlank()) return emptyList()
        return openDatabaseOrNull()?.use { db ->
            val table = findEntryTable(db) ?: return@use emptyList()
            queryPrefix(db, table, prefix, limit.coerceIn(1, 100))
        } ?: emptyList()
    }

    private fun openDatabaseOrNull(): SQLiteDatabase? {
        val file = ensureDatabaseFile() ?: return null
        return SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    }

    private fun ensureDatabaseFile(): File? {
        val target = context.getDatabasePath(databaseName)
        if (target.exists() && target.length() > 0L) return target
        return runCatching {
            context.assets.open(assetPath).use { input ->
                target.parentFile?.mkdirs()
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        }.getOrNull()
    }

    private fun findEntryTable(db: SQLiteDatabase): String? {
        val candidates = listOf("ecdict_entries", "stardict")
        return candidates.firstOrNull { table ->
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
                arrayOf(table)
            ).use { it.moveToFirst() }
        }
    }

    private fun queryEntry(db: SQLiteDatabase, table: String, wordKey: String): DictionaryEntry? {
        val columns = columns(db, table)
        val where = if ("word_key" in columns) {
            "lower(word)=? OR lower(word_key)=?"
        } else {
            "lower(word)=?"
        }
        val args = if ("word_key" in columns) arrayOf(wordKey, wordKey) else arrayOf(wordKey)
        return db.query(table, null, where, args, null, null, null, "1").use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.toDictionaryEntry(db, table)
        }
    }

    private fun queryPrefix(db: SQLiteDatabase, table: String, prefix: String, limit: Int): List<DictionaryEntry> {
        val columns = columns(db, table)
        val order = buildList {
            if ("bnc" in columns) add("CASE WHEN bnc IS NULL OR bnc='' THEN 999999 ELSE CAST(bnc AS INTEGER) END ASC")
            if ("frq" in columns) add("CASE WHEN frq IS NULL OR frq='' THEN 999999 ELSE CAST(frq AS INTEGER) END ASC")
            add("word COLLATE NOCASE ASC")
        }.joinToString(", ")
        return db.query(
            table,
            null,
            "lower(word) LIKE ?",
            arrayOf("$prefix%"),
            null,
            null,
            order,
            limit.toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toDictionaryEntry(db, table))
                }
            }
        }
    }

    private fun columns(db: SQLiteDatabase, table: String): Set<String> {
        return db.rawQuery("PRAGMA table_info($table)", emptyArray()).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(1).lowercase())
                }
            }
        }
    }

    private fun Cursor.toDictionaryEntry(db: SQLiteDatabase, table: String): DictionaryEntry {
        val word = stringColumn("word")
        val wordKey = stringColumn("word_key").ifBlank { DictionaryJson.normalizeWordKey(word) }
        val translation = stringColumn("translation")
        val definition = stringColumn("definition")
        val pos = stringColumn("pos")
        val meanings = normalizeWordMeanings(
            wordId = wordKey,
            rawMeaning = translation.ifBlank { definition },
            rawPos = pos
        ).map {
            DictionaryMeaning(
                id = it.id,
                pos = it.pos,
                posName = it.posName,
                meaning = it.meaning,
                example = it.example,
                translation = it.translation
            )
        }
        return DictionaryEntry(
            wordKey = wordKey,
            word = word,
            phonetic = stringColumn("phonetic"),
            definition = definition,
            translation = translation,
            pos = pos,
            meanings = meanings,
            collins = intColumn("collins"),
            oxford = intColumn("oxford"),
            tag = stringColumn("tag"),
            bnc = intColumn("bnc"),
            frq = intColumn("frq"),
            exchange = stringColumn("exchange"),
            detail = stringColumn("detail"),
            audio = stringColumn("audio"),
            source = stringColumn("source").ifBlank { "ecdict" },
            memberships = queryMemberships(db, wordKey)
        )
    }

    private fun queryMemberships(db: SQLiteDatabase, wordKey: String): List<WordbookMembership> {
        val hasMemberships = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='wordbook_words' LIMIT 1",
            emptyArray()
        ).use { it.moveToFirst() }
        if (!hasMemberships) return emptyList()
        return db.rawQuery(
            """
            SELECT ww.wordbook_id, ww.unit, ww.level, ww.tags, ww.priority, ww.sort_order, wb.title
            FROM wordbook_words ww
            LEFT JOIN wordbooks wb ON wb.id = ww.wordbook_id
            WHERE ww.word_key = ?
            ORDER BY ww.priority DESC, ww.sort_order ASC
            """.trimIndent(),
            arrayOf(wordKey)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        WordbookMembership(
                            wordbookId = cursor.stringColumn("wordbook_id"),
                            title = cursor.stringColumn("title"),
                            unit = cursor.stringColumn("unit"),
                            level = cursor.stringColumn("level"),
                            tags = cursor.stringColumn("tags").split(",").map { it.trim() }.filter { it.isNotBlank() },
                            priority = cursor.intColumn("priority"),
                            sortOrder = cursor.intColumn("sort_order")
                        )
                    )
                }
            }
        }
    }

    private fun Cursor.stringColumn(name: String): String {
        val index = getColumnIndex(name)
        return if (index >= 0) getString(index).orEmpty() else ""
    }

    private fun Cursor.intColumn(name: String): Int {
        val index = getColumnIndex(name)
        if (index < 0 || isNull(index)) return 0
        return runCatching { getInt(index) }.getOrDefault(0)
    }
}
