package com.danchi.app.domain

data class Word(
    val id: String,
    val text: String,
    val meaning: String,
    val pos: String = "",
    val meanings: List<WordMeaning> = emptyList(),
    val phonetic: String?,
    val example: String,
    val exampleCn: String,
    val root: String?,
    val synonyms: List<String>,
    val collocations: List<String>,
    val memoryTip: String?,
    val book: String,
    val unit: String,
    val level: String = "",
    val status: WordStatus,
    val dueAt: Long,
    val learnedAt: Long?,
    val reviewCount: Int,
    val lapseCount: Int,
    val stability: Double,
    val difficulty: Double,
    val isFavorite: Boolean,
    val note: String?,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class WordMeaning(
    val id: String,
    val pos: String = "",
    val posName: String = "",
    val meaning: String,
    val example: String = "",
    val translation: String = ""
)

object WordbookDefaults {
    const val DefaultId = "zhongkao"
}

data class Wordbook(
    val id: String,
    val title: String,
    val description: String,
    val sourceFile: String,
    val assetPath: String,
    val version: String,
    val wordCount: Int
)

data class WordbookProgress(
    val wordbook: Wordbook,
    val total: Int,
    val learned: Int
) {
    val progress: Float get() = if (total == 0) 0f else learned.toFloat() / total
}

enum class WordStatus {
    New,
    Learning,
    Review,
    Mastered
}

data class StudySettings(
    val dailyNewWords: Int = 10,
    val reviewLimit: Int = 50,
    val autoPlayWord: Boolean = true,
    val autoPlayExample: Boolean = false,
    val selectedWordbookId: String = WordbookDefaults.DefaultId,
    val wordOrder: StudyWordOrder = StudyWordOrder.Alphabetical,
    val accent: Accent = Accent.Us,
    val speechRate: Float = 0.9f,
    val autoPlayRepeatCount: Int = 1,
    val masteryConfirmMutedUntilMillis: Long = 0L,
    val dailyMinutes: Int = 10,
    val maxNewCardsPerDay: Int = 20
)

enum class StudyWordOrder(val label: String) {
    Random("随机"),
    Alphabetical("字母排序")
}

data class FsrsStudyItem(
    val cardId: Long,
    val word: Word,
    val options: List<MeaningChoiceOption>,
    val questionType: String = "recognition"
)

data class FsrsSessionSnapshot(
    val sessionId: String,
    val items: List<FsrsStudyItem>,
    val currentPosition: Int,
    val totalCount: Int,
    val completedCount: Int,
    val isResumed: Boolean,
    val isBuilding: Boolean = false
)

data class FsrsBuildProgress(
    val title: String,
    val detail: String,
    val step: Int,
    val totalSteps: Int
) {
    val fraction: Float
        get() = if (totalSteps <= 0) 0f else (step.toFloat() / totalSteps).coerceIn(0f, 1f)
}

object StudyPlanOptions {
    val DailyNewWordOptions = listOf(
        10, 20, 30, 50, 60, 70, 80, 90, 100,
        150, 200, 250, 300, 350, 400, 450, 500
    )
    val AutoPlayRepeatOptions = listOf(1, 2, 3)

    fun normalizeDailyNewWords(value: Int): Int {
        return DailyNewWordOptions.minBy { kotlin.math.abs(it - value) }
    }

    fun defaultReviewLimit(dailyNewWords: Int): Int {
        return normalizeDailyNewWords(dailyNewWords) * 5
    }

    fun normalizeAutoPlayRepeatCount(value: Int): Int {
        return value.coerceIn(1, 3)
    }
}

enum class Accent(val label: String) {
    Us("美式"),
    Uk("英式")
}

data class TodayPlan(
    val newCount: Int,
    val dueReviewCount: Int,
    val reviewLimit: Int,
    val estimatedMinutes: Int
)

data class StudyProfileStats(
    val streakDays: Int = 0,
    val totalStudyDays: Int = 0,
    val totalLearnedWords: Int = 0
)

data class LibraryStats(
    val total: Int = 0,
    val newWords: Int = 0,
    val learning: Int = 0,
    val review: Int = 0,
    val mastered: Int = 0,
    val favorite: Int = 0,
    val notes: Int = 0
) {
    val learned: Int get() = learning + review + mastered
    val progress: Float get() = if (total == 0) 0f else learned.toFloat() / total
}
