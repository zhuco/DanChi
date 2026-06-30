package com.danchi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyPlannerTest {
    @Test
    fun todayPlanRespectsDailyLimits() {
        val plan = StudyPlanner.todayPlan(
            remainingNewWords = 100,
            dueReviewWords = 300,
            settings = StudySettings(dailyNewWords = 12, reviewLimit = 40)
        )

        assertEquals(12, plan.newCount)
        assertEquals(40, plan.dueReviewCount)
        assertEquals(40, plan.reviewLimit)
    }

    @Test
    fun emptyPlanHasZeroMinutes() {
        val plan = StudyPlanner.todayPlan(
            remainingNewWords = 0,
            dueReviewWords = 0,
            settings = StudySettings()
        )

        assertEquals(0, plan.estimatedMinutes)
    }
}
