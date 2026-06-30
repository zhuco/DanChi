package com.danchi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirmStudyMachineTest {
    private val now = 1_800_000_000_000L

    @Test
    fun previewEnabledShowsPreviewForNewWord() {
        val record = WordStudyRecord(wordId = "w1", status = FirmStudyStatus.New, previewSeen = false)

        assertTrue(FirmStudyMachine.shouldShowPreview(record, StudySettings(enableNewWordPreview = true)))
    }

    @Test
    fun previewDisabledSkipsPreviewForNewWord() {
        val record = WordStudyRecord(wordId = "w1", status = FirmStudyStatus.New, previewSeen = false)

        assertFalse(FirmStudyMachine.shouldShowPreview(record, StudySettings(enableNewWordPreview = false)))
    }

    @Test
    fun previewDoneMarksSeenAndMovesToIntroWithoutRememberCount() {
        val record = WordStudyRecord(wordId = "w1", todayRememberCount = 2)

        val next = FirmStudyMachine.onPreviewDone(record, now)

        assertTrue(next.previewSeen)
        assertEquals(now, next.previewSeenAt)
        assertEquals(FirmStudyStatus.Intro, next.status)
        assertEquals(2, next.todayRememberCount)
        assertFalse(next.status == FirmStudyStatus.DayDone)
    }

    @Test
    fun choiceSelectedCountsWrongAndChoiceNextMovesToDetail() {
        val record = WordStudyRecord(wordId = "w1", status = FirmStudyStatus.Intro)

        val selected = FirmStudyMachine.onChoiceSelected(record, isCorrect = false, now)
        val detail = FirmStudyMachine.onChoiceNext(selected.record, now)

        assertFalse(selected.isCorrect)
        assertEquals(1, selected.record.todayWrongChoiceCount)
        assertEquals(FirmStudyStatus.Detail, selected.record.status)
        assertEquals(FirmStudyStatus.Detail, detail.status)
    }

    @Test
    fun correctChoiceDoesNotAddWrongCount() {
        val record = WordStudyRecord(wordId = "w1", status = FirmStudyStatus.Intro, todayWrongChoiceCount = 2)

        val selected = FirmStudyMachine.onChoiceSelected(record, isCorrect = true, now)

        assertTrue(selected.isCorrect)
        assertEquals(2, selected.record.todayWrongChoiceCount)
    }

    @Test
    fun selectedChoiceCanResumeAtDetailAfterExit() {
        val record = WordStudyRecord(wordId = "w1", status = FirmStudyStatus.Intro)

        val selected = FirmStudyMachine.onChoiceSelected(record, isCorrect = true, now)

        assertEquals(FirmStudyStatus.Detail, selected.record.status)
        assertEquals(now, selected.record.lastShownAt)
    }

    @Test
    fun detailNextMovesToLearning() {
        val record = WordStudyRecord(wordId = "w1", status = FirmStudyStatus.Detail)

        val next = FirmStudyMachine.onDetailNext(record, now, currentCardIndex = 4)

        assertEquals(FirmStudyStatus.Learning, next.status)
        assertEquals(6, next.nextDueCardIndex)
        assertTrue(next.nextDueAt > now)
    }

    @Test
    fun rememberOneAndTwoKeepLearningWithVisibleProgress() {
        val record = WordStudyRecord(wordId = "w1", status = FirmStudyStatus.Learning)

        val one = FirmStudyMachine.onRemember(record, now, 1)
        val two = FirmStudyMachine.onRemember(one, now + 1, 2)

        assertEquals(1, one.todayRememberCount)
        assertEquals(FirmStudyStatus.Learning, one.status)
        assertEquals(2, two.todayRememberCount)
        assertEquals(FirmStudyStatus.Learning, two.status)
    }

    @Test
    fun threeRemembersCompleteTheWordForToday() {
        val record = WordStudyRecord(wordId = "w1", status = FirmStudyStatus.Learning)

        val one = FirmStudyMachine.onRemember(record, now, 1)
        val two = FirmStudyMachine.onRemember(one, now + 1, 2)
        val three = FirmStudyMachine.onRemember(two, now + 2, 3)

        assertEquals(3, three.todayRememberCount)
        assertEquals(FirmStudyStatus.DayDone, three.status)
        assertTrue(three.nextDueAt > now)
    }

    @Test
    fun forgetResetsOneOfThreeToZero() {
        val record = WordStudyRecord(wordId = "w1", status = FirmStudyStatus.Learning, todayRememberCount = 1)

        val next = FirmStudyMachine.onForget(record, now, 2)

        assertEquals(0, next.todayRememberCount)
        assertEquals(1, next.todayForgetCount)
        assertEquals(FirmStudyStatus.Learning, next.status)
    }

    @Test
    fun forgetResetsTwoOfThreeToZero() {
        val record = WordStudyRecord(wordId = "w1", status = FirmStudyStatus.Learning, todayRememberCount = 2)

        val next = FirmStudyMachine.onForget(record, now, 2)

        assertEquals(0, next.todayRememberCount)
        assertFalse(next.status == FirmStudyStatus.DayDone)
    }

    @Test
    fun reviewForgetReturnsToLearningAndClearsRememberCount() {
        val record = WordStudyRecord(
            wordId = "w1",
            status = FirmStudyStatus.ReviewDue,
            todayRememberCount = 2,
            reviewLevel = 4
        )

        val next = FirmStudyMachine.onReviewForget(record, now, 5)

        assertEquals(FirmStudyStatus.Learning, next.status)
        assertEquals(0, next.todayRememberCount)
        assertEquals(3, next.reviewLevel)
    }

    @Test
    fun reviewRememberDelaysNextDueAt() {
        val record = WordStudyRecord(wordId = "w1", status = FirmStudyStatus.ReviewDue, reviewLevel = 1)

        val next = FirmStudyMachine.onReviewRemember(record, now)

        assertEquals(FirmStudyStatus.DayDone, next.status)
        assertEquals(2, next.reviewLevel)
        assertTrue(next.nextDueAt > now)
    }

    @Test
    fun crossDayResetsDailyCountsAndKeepsReviewData() {
        val yesterday = now - 24L * 60L * 60L * 1000L
        val record = WordStudyRecord(
            wordId = "w1",
            status = FirmStudyStatus.DayDone,
            todayRememberCount = 3,
            todayForgetCount = 2,
            todayWrongChoiceCount = 1,
            previewSeen = true,
            reviewLevel = 5,
            intervalDays = 15,
            nextDueAt = now + 10_000L,
            studyDayEpoch = FirmStudyMachine.todayEpoch(yesterday)
        )

        val next = FirmStudyMachine.normalizeForToday(record, now)

        assertEquals(0, next.todayRememberCount)
        assertEquals(0, next.todayForgetCount)
        assertEquals(0, next.todayWrongChoiceCount)
        assertTrue(next.previewSeen)
        assertEquals(5, next.reviewLevel)
        assertEquals(15, next.intervalDays)
        assertEquals(record.nextDueAt, next.nextDueAt)
    }

    @Test
    fun canShowAgainRequiresTimeCardIndexAndLearningStatus() {
        val record = WordStudyRecord(
            wordId = "w1",
            status = FirmStudyStatus.Learning,
            nextDueAt = now - 1,
            nextDueCardIndex = 3
        )

        assertTrue(FirmStudyMachine.canShowAgain(record, now, 3))
        assertFalse(FirmStudyMachine.canShowAgain(record.copy(status = FirmStudyStatus.Detail), now, 3))
    }
}
