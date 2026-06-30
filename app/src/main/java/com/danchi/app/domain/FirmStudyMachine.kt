package com.danchi.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class FirmChoiceResult(
    val record: WordStudyRecord,
    val isCorrect: Boolean
)

object FirmStudyMachine {
    private const val DayMillis = 24L * 60L * 60L * 1000L
    private val intervals = listOf(1, 2, 4, 7, 15, 30, 60, 120)

    fun shouldShowPreview(record: WordStudyRecord, settings: StudySettings): Boolean {
        return settings.enableNewWordPreview &&
            record.status == FirmStudyStatus.New &&
            !record.previewSeen
    }

    fun onPreviewDone(record: WordStudyRecord, now: Long): WordStudyRecord {
        return record.copy(
            previewSeen = true,
            previewSeenAt = now,
            status = FirmStudyStatus.Intro,
            lastShownAt = now
        )
    }

    fun onGroupPreviewDone(records: List<WordStudyRecord>, now: Long): List<WordStudyRecord> {
        return records.map { record ->
            record.copy(
                previewSeen = true,
                previewSeenAt = now,
                status = if (record.status == FirmStudyStatus.New || record.status == FirmStudyStatus.Preview) {
                    FirmStudyStatus.Intro
                } else {
                    record.status
                },
                lastShownAt = now
            )
        }
    }

    fun onChoiceSelected(record: WordStudyRecord, isCorrect: Boolean, now: Long): FirmChoiceResult {
        return FirmChoiceResult(
            record = record.copy(
                status = FirmStudyStatus.Detail,
                todayWrongChoiceCount = record.todayWrongChoiceCount + if (!isCorrect) 1 else 0,
                lastShownAt = now
            ),
            isCorrect = isCorrect
        )
    }

    fun onChoiceNext(record: WordStudyRecord, now: Long): WordStudyRecord {
        return record.copy(status = FirmStudyStatus.Detail, lastShownAt = now)
    }

    fun onDetailNext(record: WordStudyRecord, now: Long, currentCardIndex: Int): WordStudyRecord {
        return record.copy(
            status = FirmStudyStatus.Learning,
            nextDueAt = now + FirmModeConfig.minDelaySeconds * 1000L,
            nextDueCardIndex = currentCardIndex + FirmModeConfig.minDelayCards,
            lastShownAt = now,
            lastShownCardIndex = currentCardIndex,
            firstLearnedAt = record.firstLearnedAt ?: now
        )
    }

    fun onRemember(record: WordStudyRecord, now: Long, currentCardIndex: Int): WordStudyRecord {
        val nextRememberCount = record.todayRememberCount + 1
        return if (nextRememberCount >= record.requiredRememberCount) {
            val nextReviewLevel = record.reviewLevel.coerceAtLeast(1)
            val nextIntervalDays = getNextIntervalDays(nextReviewLevel)
            record.copy(
                status = FirmStudyStatus.DayDone,
                todayRememberCount = nextRememberCount,
                completedTodayAt = now,
                reviewLevel = nextReviewLevel,
                intervalDays = nextIntervalDays,
                nextDueAt = startOfNextDayPlusDays(nextIntervalDays, now),
                lastShownAt = now,
                lastShownCardIndex = currentCardIndex,
                firstLearnedAt = record.firstLearnedAt ?: now
            )
        } else {
            record.copy(
                status = FirmStudyStatus.Learning,
                todayRememberCount = nextRememberCount,
                nextDueAt = now + FirmModeConfig.minDelaySeconds * 1000L,
                nextDueCardIndex = currentCardIndex + FirmModeConfig.minDelayCards,
                lastShownAt = now,
                lastShownCardIndex = currentCardIndex,
                firstLearnedAt = record.firstLearnedAt ?: now
            )
        }
    }

    fun onForget(record: WordStudyRecord, now: Long, currentCardIndex: Int): WordStudyRecord {
        return record.copy(
            status = FirmStudyStatus.Learning,
            todayForgetCount = record.todayForgetCount + 1,
            todayRememberCount = 0,
            nextDueAt = now + FirmModeConfig.forgetDelaySeconds * 1000L,
            nextDueCardIndex = currentCardIndex + 1,
            lastShownAt = now,
            lastShownCardIndex = currentCardIndex,
            firstLearnedAt = record.firstLearnedAt ?: now
        )
    }

    fun onReviewRemember(record: WordStudyRecord, now: Long): WordStudyRecord {
        val nextReviewLevel = record.reviewLevel + 1
        val nextIntervalDays = getNextIntervalDays(nextReviewLevel)
        return record.copy(
            status = FirmStudyStatus.DayDone,
            reviewLevel = nextReviewLevel,
            intervalDays = nextIntervalDays,
            nextDueAt = startOfNextDayPlusDays(nextIntervalDays, now),
            lastReviewedAt = now,
            completedTodayAt = now,
            lastShownAt = now
        )
    }

    fun onReviewForget(record: WordStudyRecord, now: Long, currentCardIndex: Int): WordStudyRecord {
        return record.copy(
            status = FirmStudyStatus.Learning,
            reviewLevel = record.reviewLevel.minus(1).coerceAtLeast(1),
            todayRememberCount = 0,
            requiredRememberCount = FirmModeConfig.requiredRememberCount,
            todayForgetCount = record.todayForgetCount + 1,
            nextDueAt = now + FirmModeConfig.forgetDelaySeconds * 1000L,
            nextDueCardIndex = currentCardIndex + 1,
            lastShownAt = now,
            lastShownCardIndex = currentCardIndex
        )
    }

    fun canShowAgain(record: WordStudyRecord, now: Long, currentCardIndex: Int): Boolean {
        return now >= record.nextDueAt &&
            currentCardIndex >= record.nextDueCardIndex &&
            record.status == FirmStudyStatus.Learning
    }

    fun getNextIntervalDays(reviewLevel: Int): Int {
        return intervals[(reviewLevel - 1).coerceIn(0, intervals.lastIndex)]
    }

    fun startOfNextDayPlusDays(days: Int, now: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val date = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        return date.plusDays(1L + days).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun startOfToday(now: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun todayEpoch(now: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return LocalDate.from(Instant.ofEpochMilli(now).atZone(zoneId)).toEpochDay()
    }

    fun normalizeForToday(record: WordStudyRecord, now: Long): WordStudyRecord {
        val today = todayEpoch(now)
        if (record.studyDayEpoch == today) return record

        val dueStatus = when {
            record.status == FirmStudyStatus.Mastered -> FirmStudyStatus.Mastered
            record.nextDueAt > 0L && record.nextDueAt <= now -> FirmStudyStatus.ReviewDue
            record.status == FirmStudyStatus.DayDone -> FirmStudyStatus.DayDone
            else -> record.status
        }
        return record.copy(
            status = dueStatus,
            todayRememberCount = 0,
            todayForgetCount = 0,
            todayWrongChoiceCount = 0,
            lastShownCardIndex = 0,
            nextDueCardIndex = 0,
            studyDayEpoch = today
        )
    }

    fun isDoneForToday(record: WordStudyRecord): Boolean {
        return record.status == FirmStudyStatus.DayDone || record.status == FirmStudyStatus.Mastered
    }
}
