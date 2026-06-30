package com.danchi.app.data

import com.danchi.app.domain.DictionaryEntry
import com.danchi.app.domain.DictionaryMeaning
import com.danchi.app.domain.WordbookMembership
import com.danchi.app.domain.toWordPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DictionaryJsonTest {
    @Test
    fun encodesAndDecodesDictionaryEntryWithMemberships() {
        val entry = DictionaryEntry(
            wordKey = "apply",
            word = "apply",
            phonetic = "/əˈplaɪ/",
            translation = "申请；应用",
            pos = "v.",
            meanings = listOf(
                DictionaryMeaning(
                    id = "apply-v-0",
                    pos = "v",
                    meaning = "申请；应用"
                )
            ),
            memberships = listOf(
                WordbookMembership(
                    wordbookId = "zhongkao_core",
                    title = "中考核心",
                    unit = "核心动词",
                    level = "middle",
                    tags = listOf("exam"),
                    priority = 10,
                    sortOrder = 1
                ),
                WordbookMembership(
                    wordbookId = "grade8_core",
                    title = "八年级核心",
                    unit = "Unit 3",
                    level = "grade8",
                    tags = listOf("core"),
                    priority = 5,
                    sortOrder = 2
                )
            )
        )

        val decoded = DictionaryJson.decode(DictionaryJson.encode(entry))

        assertNotNull(decoded)
        assertEquals("apply", decoded?.wordKey)
        assertEquals("v.", decoded?.meanings?.single()?.pos)
        assertEquals("动词", decoded?.meanings?.single()?.posName)
        assertEquals(listOf("zhongkao_core", "grade8_core"), decoded?.memberships?.map { it.wordbookId })
    }

    @Test
    fun dictionaryEntryBuildsWordPatchWithoutChangingWordId() {
        val entry = DictionaryEntry(
            wordKey = "book",
            word = "book",
            phonetic = "/bʊk/",
            translation = "书；本子",
            meanings = listOf(DictionaryMeaning(id = "book-n-0", pos = "n.", meaning = "书；本子"))
        )

        val patch = entry.toWordPatch("unit test")

        assertEquals("book", patch.word)
        assertEquals("/bʊk/", patch.phonetic)
        assertEquals("n.", patch.meanings.single().pos)
        assertEquals("书；本子", patch.meaning)
        assertEquals("unit test", patch.reason)
    }
}
