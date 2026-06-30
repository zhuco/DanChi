package com.danchi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordMeaningsTest {
    @Test
    fun normalizesPosAliasesAndNames() {
        assertEquals("n.", normalizePos("n"))
        assertEquals("n.", normalizePos("n."))
        assertEquals("vt.", normalizePos("vt"))
        assertEquals("abbr.", normalizePos("abbr"))
        assertEquals("det.", normalizePos("det"))
        assertEquals("及物动词", getPosName("vt."))
        assertEquals("缩写", getPosName("abbr"))
        assertTrue(isKnownPos("modal"))
        assertFalse(isKnownPos("custom"))
    }

    @Test
    fun parsesLegacyMeaningWithEnglishPosPrefix() {
        val meanings = parseMeaningText("n. 苹果", fallbackId = "apple")

        assertEquals(1, meanings.size)
        assertEquals("n.", meanings[0].pos)
        assertEquals("名词", meanings[0].posName)
        assertEquals("苹果", meanings[0].meaning)
    }

    @Test
    fun parsesMeaningWithoutSpaceAfterPos() {
        val meanings = parseMeaningText("adj.漂亮的", fallbackId = "beautiful")

        assertEquals(1, meanings.size)
        assertEquals("adj.", meanings[0].pos)
        assertEquals("漂亮的", meanings[0].meaning)
    }

    @Test
    fun parsesMultiLineMeaningWithMultiplePos() {
        val meanings = parseMeaningText("v. 申请；应用\nvt. 应用；涂\nvi. 申请；适用", fallbackId = "apply")

        assertEquals(3, meanings.size)
        assertEquals("v.", meanings[0].pos)
        assertEquals("申请；应用", meanings[0].meaning)
        assertEquals("vt.", meanings[1].pos)
        assertEquals("应用；涂", meanings[1].meaning)
        assertEquals("vi.", meanings[2].pos)
        assertEquals("申请；适用", meanings[2].meaning)
    }

    @Test
    fun parsesChinesePosPrefix() {
        val meanings = parseMeaningText("形容词 漂亮的", fallbackId = "beautiful")

        assertEquals(1, meanings.size)
        assertEquals("adj.", meanings[0].pos)
        assertEquals("漂亮的", meanings[0].meaning)
    }

    @Test
    fun parsesLegacyMeaningWithoutPosPrefix() {
        val meanings = parseMeaningText("苹果", fallbackId = "apple")

        assertEquals(1, meanings.size)
        assertEquals("", meanings[0].pos)
        assertEquals("苹果", meanings[0].meaning)
    }

    @Test
    fun normalizesExplicitStructuredMeanings() {
        val meanings = normalizeWordMeanings(
            wordId = "apply",
            rawMeaning = "应用",
            rawPos = "vt",
            rawMeanings = listOf(
                WordMeaning(id = "m1", pos = "vt", meaning = "应用；使用"),
                WordMeaning(id = "m2", pos = "vi", meaning = "申请")
            )
        )

        assertEquals(2, meanings.size)
        assertEquals("vt.", meanings[0].pos)
        assertEquals("应用；使用", meanings[0].meaning)
        assertEquals("vi.", meanings[1].pos)
        assertEquals("申请", meanings[1].meaning)
    }

    @Test
    fun normalizesLegacyWordFields() {
        val meanings = normalizeWordMeanings(
            wordId = "apple",
            rawMeaning = "苹果",
            rawPos = "n."
        )

        assertEquals(1, meanings.size)
        assertEquals("n.", meanings[0].pos)
        assertEquals("苹果", meanings[0].meaning)
    }

    @Test
    fun normalizesLegacyMeaningTextWithPosInsideMeaning() {
        val meanings = normalizeWordMeanings(
            wordId = "apple",
            rawMeaning = "n. 苹果"
        )

        assertEquals(1, meanings.size)
        assertEquals("n.", meanings[0].pos)
        assertEquals("苹果", meanings[0].meaning)
    }

    @Test
    fun doesNotCrashWhenPosOrMeaningsAreMissing() {
        val meanings = normalizeWordMeanings(
            wordId = "unknown",
            rawMeaning = "未知释义"
        )

        assertEquals(1, meanings.size)
        assertEquals("", meanings[0].pos)
        assertEquals("未知释义", meanings[0].meaning)
    }

    @Test
    fun infersPosFromChineseUnit() {
        assertEquals("adj.", inferPosFromUnit("叙事，状物类形容词"))
        assertEquals("vt.", inferPosFromUnit("由指人名词转化成的及物动词"))
        assertEquals("vi.", inferPosFromUnit("不及物动词"))
    }

    @Test
    fun meaningChoicesBindByWordIdAndCarryPos() {
        val correct = testWord(
            id = "apple",
            text = "apple",
            meaning = "n. 苹果"
        )
        val candidates = listOf(
            correct,
            testWord("apply", "apply", "vt. 应用；使用"),
            testWord("happy", "happy", "adj. 高兴的"),
            testWord("quickly", "quickly", "adv. 快速地")
        )

        val options = buildMeaningChoiceOptions(correct, candidates)
        val correctOption = options.single { it.isCorrect }

        assertEquals("apple:${correctOption.meaningId}", correctOption.id)
        assertEquals("apple", correctOption.wordId)
        assertTrue(correctOption.meaningId.startsWith("apple-"))
        assertEquals("n.", correctOption.pos)
        assertEquals("n. 苹果", correctOption.displayText)
        assertFalse(options.any { it.wordId != correct.id && it.isCorrect })
    }

    @Test
    fun meaningChoiceStillDisplaysMeaningWhenDistractorHasNoPos() {
        val correct = testWord("apple", "apple", "n. 苹果")
        val noPos = testWord("unknown", "unknown", "未知释义")
        val candidates = listOf(
            correct,
            noPos,
            testWord("apply", "apply", "vt. 应用；使用"),
            testWord("happy", "happy", "adj. 高兴的")
        )

        val option = buildMeaningChoiceOptions(correct, candidates).single { it.wordId == "unknown" }

        assertEquals("", option.pos)
        assertEquals("未知释义", option.displayText)
    }

    @Test
    fun formatsMeaningWithPosNameForDetail() {
        assertEquals(
            "vt. 及物动词：应用",
            formatMeaningWithPos(pos = "vt", posName = "", meaning = "应用", showPosName = true)
        )
        assertEquals("苹果", formatMeaningWithPos(pos = "", meaning = "苹果"))
    }

    private fun testWord(id: String, text: String, meaning: String): Word {
        val meanings = normalizeWordMeanings(
            wordId = id,
            rawMeaning = meaning
        )
        return Word(
            id = id,
            text = text,
            meaning = meaning,
            pos = meanings.firstOrNull()?.pos.orEmpty(),
            meanings = meanings,
            phonetic = null,
            example = "",
            exampleCn = "",
            root = null,
            synonyms = emptyList(),
            collocations = emptyList(),
            memoryTip = null,
            book = "Book",
            unit = "Unit",
            status = WordStatus.New,
            dueAt = 0L,
            learnedAt = null,
            reviewCount = 0,
            lapseCount = 0,
            stability = 1.0,
            difficulty = 5.0,
            isFavorite = false,
            note = null
        )
    }
}
