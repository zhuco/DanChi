package com.danchi.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.danchi.app.domain.Word
import com.danchi.app.domain.normalizePos
import com.danchi.app.domain.normalizeWordMeanings
import com.danchi.app.domain.Wordbook
import com.danchi.app.domain.WordStatus
import com.danchi.app.scheduler.FsrsCardState
import com.danchi.app.scheduler.FsrsCardType
import com.danchi.app.scheduler.FsrsRating
import com.danchi.app.scheduler.SchedulerCard
import com.danchi.app.scheduler.SchedulerReviewLog
import com.danchi.app.scheduler.SchedulerSettings

@Entity(tableName = "dictionary_cache", indices = [Index(value = ["expiresAt"])])
data class DictionaryCacheEntity(
    @PrimaryKey val wordKey: String,
    val word: String,
    val payloadJson: String,
    val source: String,
    val cachedAt: Long,
    val expiresAt: Long
) {
    fun toDomain(): com.danchi.app.domain.DictionaryEntry? {
        return DictionaryJson.decode(payloadJson)
    }

    companion object {
        fun from(entry: com.danchi.app.domain.DictionaryEntry, ttlMillis: Long): DictionaryCacheEntity {
            val now = System.currentTimeMillis()
            return DictionaryCacheEntity(
                wordKey = entry.wordKey,
                word = entry.word,
                payloadJson = DictionaryJson.encode(entry),
                source = entry.source,
                cachedAt = now,
                expiresAt = now + ttlMillis.coerceAtLeast(0L)
            )
        }
    }
}

@Entity(
    tableName = "words",
    indices = [
        Index(value = ["wordbookId"]),
        Index(value = ["wordbookId", "text"], unique = true),
        Index(value = ["wordbookId", "status", "text"]),
        Index(value = ["wordbookId", "wordKey"]),
        Index(value = ["wordbookId", "pos"]),
        Index(value = ["status"]),
        Index(value = ["dueAt"]),
        Index(value = ["isFavorite"])
    ]
)
data class WordEntity(
    @PrimaryKey val id: String,
    val wordbookId: String,
    val text: String,
    val wordKey: String = LearningKeys.wordKey(text),
    val learningWordId: String = LearningKeys.learningWordId(wordbookId, wordKey),
    val meaning: String,
    val pos: String = "",
    val meaningsJson: String = "",
    val phonetic: String = "",
    val example: String = "",
    val exampleCn: String = "",
    val root: String = "",
    val synonyms: String = "",
    val collocations: String = "",
    val memoryTip: String = "",
    val book: String,
    val unit: String,
    val level: String = "",
    val source: String,
    val status: String = WordStatus.New.name,
    val dueAt: Long = 0L,
    val learnedAt: Long? = null,
    val reviewCount: Int = 0,
    val lapseCount: Int = 0,
    val stability: Double = 1.0,
    val difficulty: Double = 5.0,
    val isFavorite: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(note: String? = null): Word {
        val resolvedExample = example.ifBlank { defaultExample(text) }
        val resolvedExampleCn = exampleCn.ifBlank { defaultExampleCn(text) }
        val resolvedMeanings = normalizeWordMeanings(
            wordId = id,
            rawMeaning = meaning,
            rawPos = pos,
            rawMeanings = WordMeaningJson.decode(meaningsJson),
            example = resolvedExample,
            translation = resolvedExampleCn
        )
        val resolvedPos = normalizePos(pos).ifBlank { resolvedMeanings.firstOrNull()?.pos.orEmpty() }
        return Word(
            id = id,
            text = text,
            meaning = meaning,
            pos = resolvedPos,
            meanings = resolvedMeanings,
            phonetic = phonetic.ifBlank { null },
            example = resolvedExample,
            exampleCn = resolvedExampleCn,
            root = root.ifBlank { null },
            synonyms = splitList(synonyms),
            collocations = splitList(collocations),
            memoryTip = memoryTip.ifBlank { null },
            book = book,
            unit = unit,
            level = level,
            status = WordStatus.valueOf(status),
            dueAt = dueAt,
            learnedAt = learnedAt,
            reviewCount = reviewCount,
            lapseCount = lapseCount,
            stability = stability,
            difficulty = difficulty,
            isFavorite = isFavorite,
            note = note,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

private fun splitList(value: String): List<String> {
    return value.split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private fun defaultExample(word: String): String {
    return "I wrote the word \"$word\" next to its meaning in my notebook."
}

private fun defaultExampleCn(word: String): String {
    return "我把“$word”和它的中文释义写在笔记本上。"
}

@Entity(tableName = "wordbooks", indices = [Index(value = ["title"], unique = true)])
data class WordbookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val sourceFile: String,
    val assetPath: String,
    val version: String,
    val wordCount: Int,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Wordbook {
        return Wordbook(
            id = id,
            title = title,
            description = description,
            sourceFile = sourceFile,
            assetPath = assetPath,
            version = version,
            wordCount = wordCount
        )
    }
}

data class WordbookProgressRow(
    val id: String,
    val title: String,
    val description: String,
    val sourceFile: String,
    val assetPath: String,
    val version: String,
    val wordCount: Int,
    val total: Int,
    val learned: Int
)

@Entity(tableName = "local_wordbooks", indices = [Index(value = ["status"])])
data class LocalWordbookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val sourceFile: String = "",
    val assetPath: String = "",
    val version: String = "",
    val wordCount: Int = 0,
    val sortOrder: Int = 0,
    val packageChecksum: String = "",
    val status: String = "active",
    val downloadedAt: Long = System.currentTimeMillis(),
    val activatedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "local_wordbook_words",
    primaryKeys = ["wordbookId", "wordKey"],
    indices = [
        Index(value = ["wordKey"]),
        Index(value = ["wordbookId", "sortOrder"])
    ]
)
data class LocalWordbookWordEntity(
    val wordbookId: String,
    val wordKey: String,
    val word: String,
    val sortOrder: Int = 0,
    val book: String = "",
    val unit: String = "",
    val level: String = "",
    val source: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "learning_words",
    indices = [
        Index(value = ["userId", "wordbookId"]),
        Index(value = ["userId", "wordbookId", "wordKey"]),
        Index(value = ["userId", "wordbookId", "status"]),
        Index(value = ["userId", "wordKey"]),
        Index(value = ["status"]),
        Index(value = ["dueAt"])
    ]
)
data class LearningWordEntity(
    @PrimaryKey val id: String,
    val userId: String = DefaultUserId,
    val wordbookId: String,
    val wordKey: String,
    val wordId: String,
    val text: String,
    val meaning: String,
    val pos: String = "",
    val meaningsJson: String = "",
    val phonetic: String = "",
    val example: String = "",
    val exampleCn: String = "",
    val root: String = "",
    val synonyms: String = "",
    val collocations: String = "",
    val memoryTip: String = "",
    val book: String = "",
    val unit: String = "",
    val level: String = "",
    val source: String = "",
    val status: String = WordStatus.New.name,
    val dueAt: Long = 0L,
    val learnedAt: Long? = null,
    val reviewCount: Int = 0,
    val lapseCount: Int = 0,
    val stability: Double = 1.0,
    val difficulty: Double = 5.0,
    val createdAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_plans",
    indices = [
        Index(value = ["userId", "wordbookId", "status"]),
        Index(value = ["updatedAt"])
    ]
)
data class StudyPlanEntity(
    @PrimaryKey val id: String,
    val userId: String = DefaultUserId,
    val wordbookId: String,
    val title: String = "",
    val dailyNewWords: Int = 10,
    val reviewLimit: Int = 50,
    val wordOrder: String = "",
    val status: String = "active",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_plan_items",
    primaryKeys = ["planId", "learningWordId"],
    indices = [
        Index(value = ["userId", "wordbookId", "status"]),
        Index(value = ["userId", "wordKey"]),
        Index(value = ["planId", "position"])
    ]
)
data class StudyPlanItemEntity(
    val planId: String,
    val learningWordId: String,
    val userId: String = DefaultUserId,
    val wordbookId: String,
    val wordKey: String,
    val wordId: String,
    val position: Int = 0,
    val status: String = "pending",
    val assignedAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val skippedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_sessions",
    indices = [
        Index(value = ["userId", "wordbookId", "studyDayEpoch", "mode"]),
        Index(value = ["status"]),
        Index(value = ["lastActiveAt"])
    ]
)
data class StudySessionEntity(
    @PrimaryKey val id: String,
    val userId: String = DefaultUserId,
    val wordbookId: String,
    val planId: String = "",
    val studyDayEpoch: Long,
    val mode: String,
    val status: String = "active",
    val currentPosition: Int = 0,
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val settingsFingerprint: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_session_items",
    primaryKeys = ["sessionId", "position"],
    indices = [
        Index(value = ["userId", "wordbookId", "status"]),
        Index(value = ["sessionId", "status", "position"]),
        Index(value = ["sessionId", "wordKey"]),
        Index(value = ["sessionId", "cardId"]),
        Index(value = ["learningWordId"]),
        Index(value = ["wordKey"]),
        Index(value = ["wordId"]),
        Index(value = ["cardId"])
    ]
)
data class StudySessionItemEntity(
    val sessionId: String,
    val position: Int,
    val userId: String = DefaultUserId,
    val wordbookId: String,
    val learningWordId: String,
    val wordKey: String,
    val wordId: String,
    val cardId: Long = 0L,
    val questionType: String = "",
    val queueReason: String = "new",
    val status: String = "pending",
    val optionsJson: String = "",
    val correctOptionId: String = "",
    val selectedOptionId: String = "",
    val answeredAt: Long? = null,
    val revealedAt: Long? = null,
    val completedAt: Long? = null,
    val durationMs: Long = 0L,
    val result: String = "",
    val cardStateBefore: String = "",
    val cardStateAfter: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "user_mastered_words",
    primaryKeys = ["userId", "wordKey"],
    indices = [
        Index(value = ["wordKey"]),
        Index(value = ["masteredAt"])
    ]
)
data class UserMasteredWordEntity(
    val userId: String = DefaultUserId,
    val wordKey: String,
    val word: String,
    val firstWordbookId: String = "",
    val firstLearningWordId: String = "",
    val masteredAt: Long = System.currentTimeMillis(),
    val confidence: Double = 1.0,
    val source: String = "manual",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "user_favorite_words",
    primaryKeys = ["userId", "wordKey"],
    indices = [
        Index(value = ["wordKey"]),
        Index(value = ["updatedAt"])
    ]
)
data class UserFavoriteWordEntity(
    val userId: String = DefaultUserId,
    val wordKey: String,
    val word: String,
    val firstWordbookId: String = "",
    val firstLearningWordId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "user_word_notes",
    primaryKeys = ["userId", "wordKey"],
    indices = [
        Index(value = ["wordKey"]),
        Index(value = ["updatedAt"])
    ]
)
data class UserWordNoteEntity(
    val userId: String = DefaultUserId,
    val wordKey: String,
    val word: String,
    val body: String,
    val firstWordbookId: String = "",
    val firstLearningWordId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "review_logs",
    indices = [
        Index(value = ["wordId"]),
        Index(value = ["wordKey"]),
        Index(value = ["learningWordId"]),
        Index(value = ["wordbookId"]),
        Index(value = ["sessionId"]),
        Index(value = ["createdAt"])
    ]
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: String,
    val wordKey: String = "",
    val learningWordId: String = wordId,
    val wordbookId: String = "",
    val sessionId: String = "",
    val grade: String = "",
    val mode: String = "",
    val correct: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val userId: String = DefaultUserId,
    val cardId: Long = 0L,
    val reviewedAt: Long = createdAt,
    val rating: Int = 0,
    val elapsedDays: Int = 0,
    val scheduledDays: Int = 0,
    val durationMs: Long = 0L,
    val questionType: String = "",
    val optionCount: Int = 0,
    @ColumnInfo(name = "isCorrect") val answerCorrect: Boolean = correct,
    val firstAnswerCorrect: Boolean = correct,
    val usedHint: Boolean = false,
    val answerSnapshot: String = "",
    val stateBefore: String = "",
    val stateAfter: String = ""
)

const val DefaultUserId = "local"

@Entity(
    tableName = "cards",
    indices = [
        Index(value = ["userId", "wordId", "cardType"], unique = true),
        Index(value = ["userId", "state", "dueAt"]),
        Index(value = ["wordId"])
    ]
)
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String = DefaultUserId,
    val wordId: String,
    val wordKey: String = "",
    val learningWordId: String = wordId,
    val cardType: String = FsrsCardType.Recognition.value,
    val state: String = FsrsCardState.New.value,
    val dueAt: Long = 0L,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val elapsedDays: Int = 0,
    val scheduledDays: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val fsrsStep: Int? = null,
    val lastReviewAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toSchedulerCard(): SchedulerCard {
        return SchedulerCard(
            id = id,
            userId = userId,
            wordId = wordId,
            cardType = FsrsCardType.fromValue(cardType),
            state = FsrsCardState.fromValue(state),
            dueAt = dueAt,
            stability = stability,
            difficulty = difficulty,
            elapsedDays = elapsedDays,
            scheduledDays = scheduledDays,
            reps = reps,
            lapses = lapses,
            fsrsStep = fsrsStep,
            lastReviewAt = lastReviewAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

@Entity(tableName = "user_fsrs_setting")
data class UserFsrsSettingEntity(
    @PrimaryKey val userId: String = DefaultUserId,
    val desiredRetention: Double = 0.90,
    val maximumInterval: Int = 36500,
    val enableFuzz: Boolean = true,
    val enableShortTerm: Boolean = true,
    val learningSteps: String = "1m,10m",
    val relearningSteps: String = "10m",
    val fsrsParamsJson: String = "",
    val dailyMinutes: Int = 10,
    val maxNewCardsPerDay: Int = 20,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toSchedulerSettings(): SchedulerSettings {
        return SchedulerSettings(
            desiredRetention = desiredRetention,
            maximumInterval = maximumInterval,
            enableFuzz = enableFuzz,
            enableShortTerm = enableShortTerm,
            learningSteps = learningSteps.splitCsv(),
            relearningSteps = relearningSteps.splitCsv(),
            fsrsParamsJson = fsrsParamsJson.ifBlank { null },
            dailyMinutes = dailyMinutes,
            maxNewCardsPerDay = maxNewCardsPerDay
        )
    }
}

@Entity(tableName = "notes", indices = [Index(value = ["wordId"], unique = true)])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: String,
    val body: String,
    val updatedAt: Long = System.currentTimeMillis()
)

fun SchedulerCard.toEntity(): CardEntity {
    return CardEntity(
        id = id,
        userId = userId,
        wordId = wordId,
        cardType = cardType.value,
        state = state.value,
        dueAt = dueAt,
        stability = stability,
        difficulty = difficulty,
        elapsedDays = elapsedDays,
        scheduledDays = scheduledDays,
        reps = reps,
        lapses = lapses,
        fsrsStep = fsrsStep,
        lastReviewAt = lastReviewAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun SchedulerReviewLog.toEntity(): ReviewLogEntity {
    return ReviewLogEntity(
        wordId = wordId,
        learningWordId = wordId,
        grade = rating.name,
        mode = questionType,
        correct = isCorrect,
        createdAt = reviewedAt,
        userId = userId,
        cardId = cardId,
        reviewedAt = reviewedAt,
        rating = rating.value,
        elapsedDays = elapsedDays,
        scheduledDays = scheduledDays,
        durationMs = durationMs,
        questionType = questionType,
        optionCount = optionCount,
        answerCorrect = isCorrect,
        firstAnswerCorrect = firstAnswerCorrect,
        usedHint = usedHint,
        answerSnapshot = answerSnapshot,
        stateBefore = stateBefore.value,
        stateAfter = stateAfter.value
    )
}

private fun String.splitCsv(): List<String> {
    return split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
