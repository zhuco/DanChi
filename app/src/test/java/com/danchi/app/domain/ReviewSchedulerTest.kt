package com.danchi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSchedulerTest {
    @Test
    fun goodGradeSchedulesFutureReview() {
        val now = 1_000_000L
        val next = ReviewScheduler.next(
            state = ReviewState(
                status = WordStatus.New,
                dueAt = 0L,
                learnedAt = null,
                reviewCount = 0,
                lapseCount = 0,
                stability = 1.0,
                difficulty = 5.0
            ),
            grade = ReviewGrade.Good,
            now = now
        )

        assertEquals(WordStatus.Review, next.status)
        assertEquals(now, next.learnedAt)
        assertEquals(1, next.reviewCount)
        assertTrue(next.dueAt > now)
        assertTrue(next.stability > 1.0)
    }

    @Test
    fun againGradeKeepsWordInLearningAndCountsLapseAfterFirstReview() {
        val next = ReviewScheduler.next(
            state = ReviewState(
                status = WordStatus.Review,
                dueAt = 0L,
                learnedAt = 100L,
                reviewCount = 3,
                lapseCount = 1,
                stability = 8.0,
                difficulty = 4.0
            ),
            grade = ReviewGrade.Again,
            now = 1_000L
        )

        assertEquals(WordStatus.Learning, next.status)
        assertEquals(4, next.reviewCount)
        assertEquals(2, next.lapseCount)
        assertTrue(next.stability < 8.0)
    }
}
