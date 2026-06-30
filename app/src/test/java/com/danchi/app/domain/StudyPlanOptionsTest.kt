package com.danchi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlanOptionsTest {
    @Test
    fun dailyNewOptionsMatchProductRequirement() {
        assertEquals(
            listOf(10, 20, 30, 50, 60, 70, 80, 90, 100, 150, 200, 250, 300, 350, 400, 450, 500),
            StudyPlanOptions.DailyNewWordOptions
        )
    }

    @Test
    fun reviewLimitDefaultsToFiveTimesDailyNewWords() {
        StudyPlanOptions.DailyNewWordOptions.forEach { dailyNewWords ->
            assertEquals(dailyNewWords * 5, StudyPlanOptions.defaultReviewLimit(dailyNewWords))
        }
    }

    @Test
    fun arbitraryDailyNewValueNormalizesToSupportedOption() {
        val normalized = StudyPlanOptions.normalizeDailyNewWords(87)

        assertTrue(normalized in StudyPlanOptions.DailyNewWordOptions)
        assertEquals(90, normalized)
    }

    @Test
    fun wordOrderOptionsMatchStudyPlanLabels() {
        assertEquals("随机", StudyWordOrder.Random.label)
        assertEquals("字母排序", StudyWordOrder.Alphabetical.label)
        assertEquals(StudyWordOrder.Alphabetical, StudySettings().wordOrder)
    }
}
