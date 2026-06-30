package com.danchi.app.data

import com.danchi.app.domain.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class EntityMappingTest {
    @Test
    fun blankLexicalFieldsUseReadableFallbacks() {
        val word = WordEntity(
            id = "w1",
            wordbookId = "book",
            text = "apple",
            meaning = "n. 苹果",
            book = "Book",
            unit = "Unit 1",
            source = "test"
        ).toDomain()

        assertEquals(WordStatus.New, word.status)
        assertEquals("I wrote the word \"apple\" next to its meaning in my notebook.", word.example)
        assertEquals("我把“apple”和它的中文释义写在笔记本上。", word.exampleCn)
        assertEquals(emptyList<String>(), word.collocations)
        assertEquals("n.", word.pos)
        assertEquals("n.", word.meanings.single().pos)
    }

    @Test
    fun entityMappingUsesExplicitPosWhenMeaningHasNoPrefix() {
        val word = WordEntity(
            id = "w2",
            wordbookId = "book",
            text = "apply",
            meaning = "use or ask for formally",
            pos = "vt",
            book = "Book",
            unit = "Unit 1",
            source = "test"
        ).toDomain()

        assertEquals("vt.", word.pos)
        assertEquals("vt.", word.meanings.single().pos)
        assertEquals("use or ask for formally", word.meanings.single().meaning)
    }

    @Test
    fun wordEntityDerivesStableLearningIdentity() {
        val word = WordEntity(
            id = "book_1",
            wordbookId = "book",
            text = " Apple ",
            meaning = "n. apple",
            book = "Book",
            unit = "Unit 1",
            source = "test"
        )

        assertEquals("apple", word.wordKey)
        assertEquals("book:apple", word.learningWordId)
    }
}
