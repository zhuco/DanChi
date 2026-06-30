package com.danchi.app.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

data class ReviewState(
    val status: WordStatus,
    val dueAt: Long,
    val learnedAt: Long?,
    val reviewCount: Int,
    val lapseCount: Int,
    val stability: Double,
    val difficulty: Double
)

object ReviewScheduler {
    private const val DayMillis = 24L * 60L * 60L * 1000L

    fun next(
        state: ReviewState,
        grade: ReviewGrade,
        now: Long = System.currentTimeMillis()
    ): ReviewState {
        val firstReview = state.learnedAt == null
        val nextDifficulty = clamp(
            state.difficulty + when (grade) {
                ReviewGrade.Again -> 0.9
                ReviewGrade.Hard -> 0.35
                ReviewGrade.Good -> -0.1
                ReviewGrade.Easy -> -0.35
            },
            1.0,
            10.0
        )

        val baseStability = max(0.5, state.stability)
        val nextStability = when (grade) {
            ReviewGrade.Again -> max(0.5, baseStability * 0.45)
            ReviewGrade.Hard -> baseStability * 1.25
            ReviewGrade.Good -> baseStability * 2.1
            ReviewGrade.Easy -> baseStability * 3.2
        }

        val intervalDays = when (grade) {
            ReviewGrade.Again -> 0.05
            ReviewGrade.Hard -> min(3.0, nextStability)
            ReviewGrade.Good -> min(30.0, nextStability)
            ReviewGrade.Easy -> min(90.0, nextStability * 1.2)
        }

        val status = when {
            grade == ReviewGrade.Again -> WordStatus.Learning
            nextStability >= 45.0 && state.reviewCount >= 4 -> WordStatus.Mastered
            else -> WordStatus.Review
        }

        return ReviewState(
            status = status,
            dueAt = now + (intervalDays * DayMillis).roundToLong(),
            learnedAt = state.learnedAt ?: now,
            reviewCount = state.reviewCount + 1,
            lapseCount = state.lapseCount + if (grade == ReviewGrade.Again && !firstReview) 1 else 0,
            stability = clamp(nextStability, 0.5, 120.0),
            difficulty = nextDifficulty
        )
    }

    private fun clamp(value: Double, min: Double, max: Double): Double {
        return max(min, min(max, value))
    }
}
