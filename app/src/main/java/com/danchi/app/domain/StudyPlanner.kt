package com.danchi.app.domain

object StudyPlanner {
    fun todayPlan(
        remainingNewWords: Int,
        dueReviewWords: Int,
        settings: StudySettings
    ): TodayPlan {
        val newCount = remainingNewWords.coerceAtMost(settings.dailyNewWords)
        val reviewCount = dueReviewWords.coerceAtMost(settings.reviewLimit)
        val minutes = ((newCount * 42) + (reviewCount * 18)) / 60
        return TodayPlan(
            newCount = newCount,
            dueReviewCount = reviewCount,
            reviewLimit = settings.reviewLimit,
            estimatedMinutes = minutes.coerceAtLeast(if (newCount + reviewCount > 0) 1 else 0)
        )
    }
}
