package com.danchi.app.scheduler

import com.danchi.app.domain.Word
import com.danchi.app.domain.WordMeaning
import com.danchi.app.domain.WordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsSchedulerTest {
    private val now = 1_800_000_000_000L
    private val settings = SchedulerSettings(enableFuzz = false)

    @Test
    fun newCardWrongSchedulesAgainAndWritesAgainRating() {
        val card = FsrsScheduler.createCard(id = 1L, userId = "u1", wordId = "w1", now = now)

        val result = FsrsScheduler.answerCard(
            AnswerCardInput(
                card = card,
                settings = settings,
                firstAnswerCorrect = false,
                usedHint = false,
                durationMs = 1_200L,
                questionType = "recognition",
                optionCount = 4,
                answerSnapshot = "selected=w2;correct=w1",
                recentCorrectCount = 0,
                now = now
            )
        )

        assertEquals(FsrsRating.Again, result.reviewLog.rating)
        assertEquals(FsrsCardState.Learning, result.card.state)
        assertEquals(1, result.card.reps)
        assertTrue(result.card.dueAt > now)
    }

    @Test
    fun newCardCorrectMapsToGoodNotEasy() {
        val card = FsrsScheduler.createCard(id = 1L, userId = "u1", wordId = "w1", now = now)

        val result = FsrsScheduler.answerCard(
            AnswerCardInput(
                card = card,
                settings = settings,
                firstAnswerCorrect = true,
                usedHint = false,
                durationMs = 900L,
                questionType = "recognition",
                optionCount = 4,
                answerSnapshot = "selected=w1;correct=w1",
                recentCorrectCount = 3,
                now = now
            )
        )

        assertEquals(FsrsRating.Good, result.reviewLog.rating)
        assertEquals(1, result.card.reps)
        assertTrue(result.card.dueAt > now)
    }

    @Test
    fun reviewCardWrongMovesToRelearningAndCountsLapse() {
        val card = reviewCard().copy(dueAt = now - 1_000L)

        val result = FsrsScheduler.answerCard(
            AnswerCardInput(
                card = card,
                settings = settings,
                firstAnswerCorrect = false,
                usedHint = false,
                durationMs = 2_000L,
                questionType = "recognition",
                optionCount = 4,
                answerSnapshot = "selected=w2;correct=w1",
                recentCorrectCount = 0,
                now = now
            )
        )

        assertEquals(FsrsRating.Again, result.reviewLog.rating)
        assertEquals(FsrsCardState.Relearning, result.card.state)
        assertEquals(card.lapses + 1, result.card.lapses)
    }

    @Test
    fun reviewCardCorrectUpdatesDueAt() {
        val card = reviewCard().copy(dueAt = now - 1_000L)

        val result = FsrsScheduler.answerCard(
            AnswerCardInput(
                card = card,
                settings = settings,
                firstAnswerCorrect = true,
                usedHint = false,
                durationMs = 2_500L,
                questionType = "recognition",
                optionCount = 4,
                answerSnapshot = "selected=w1;correct=w1",
                recentCorrectCount = 3,
                now = now
            )
        )

        assertEquals(FsrsRating.Easy, result.reviewLog.rating)
        assertEquals(FsrsCardState.Review, result.card.state)
        assertTrue(result.card.dueAt > now)
        assertTrue(result.card.scheduledDays >= 1)
    }

    @Test
    fun tooManyDueReviewsSchedulesNoNewCards() {
        val plan = FsrsScheduler.generateDailyPlan(
            learningDueCount = 0,
            reviewDueCount = 100,
            remainingNewCards = 50,
            avgReviewSeconds = 18.0,
            dailyMinutes = 10,
            maxNewCardsPerDay = 20
        )

        assertEquals(0, plan.newCount)
    }

    @Test
    fun distractorsPreferSamePartOfSpeech() {
        val target = word("abandon", "v.", "放弃")
        val candidates = listOf(
            word("banana", "n.", "香蕉"),
            word("quit", "v.", "停止；放弃"),
            word("leave", "v.", "离开"),
            word("red", "adj.", "红色的"),
            word("drop", "v.", "丢下")
        )

        val distractors = FsrsScheduler.buildDistractors(target, candidates)

        assertEquals(3, distractors.size)
        assertTrue(distractors.take(3).all { it.pos == "v." })
    }

    @Test
    fun creatingCardForInfoPageDoesNotCreateReviewLog() {
        val card = FsrsScheduler.createCard(id = 1L, userId = "u1", wordId = "w1", now = now)

        assertEquals(0, card.reps)
        assertEquals(FsrsCardState.New, card.state)
    }

    private fun reviewCard(): SchedulerCard {
        return SchedulerCard(
            id = 1L,
            userId = "u1",
            wordId = "w1",
            state = FsrsCardState.Review,
            dueAt = now,
            stability = 12.0,
            difficulty = 5.0,
            elapsedDays = 12,
            scheduledDays = 12,
            reps = 3,
            lapses = 0,
            lastReviewAt = now - 12L * 24L * 60L * 60L * 1000L,
            createdAt = now - 30L * 24L * 60L * 60L * 1000L,
            updatedAt = now
        )
    }

    private fun word(text: String, pos: String, meaning: String): Word {
        return Word(
            id = text,
            text = text,
            meaning = meaning,
            pos = pos,
            meanings = listOf(WordMeaning(id = "$text-0", pos = pos, meaning = meaning)),
            phonetic = null,
            example = "",
            exampleCn = "",
            root = null,
            synonyms = emptyList(),
            collocations = emptyList(),
            memoryTip = null,
            book = "zk",
            unit = "unit",
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
