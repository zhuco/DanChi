package com.danchi.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryCacheDao {
    @Query("SELECT * FROM dictionary_cache WHERE wordKey = :wordKey AND expiresAt > :now LIMIT 1")
    suspend fun getFresh(wordKey: String, now: Long): DictionaryCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DictionaryCacheEntity)

    @Query("DELETE FROM dictionary_cache WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis()): Int
}

@Dao
interface WordDao {
    @Query("SELECT COUNT(*) FROM words")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM words WHERE wordbookId = :wordbookId")
    suspend fun countByWordbook(wordbookId: String): Int

    @Query("SELECT COUNT(*) FROM words WHERE wordbookId = :wordbookId AND status = :status")
    fun observeStatusCount(wordbookId: String, status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE wordbookId = :wordbookId AND status = 'New'")
    fun observeNewCount(wordbookId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE wordbookId = :wordbookId AND status != 'New'")
    fun observeLearnedCount(wordbookId: String): Flow<Int>

    @Query("SELECT learnedAt FROM words WHERE wordbookId = :wordbookId AND learnedAt IS NOT NULL")
    fun observeLearnedTimestamps(wordbookId: String): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM words WHERE wordbookId = :wordbookId AND dueAt <= :now AND status IN ('Learning', 'Review')")
    fun observeDueCount(wordbookId: String, now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM words WHERE wordbookId = :wordbookId AND isFavorite = 1")
    fun observeFavoriteCount(wordbookId: String): Flow<Int>

    @Query("SELECT * FROM words WHERE wordbookId = :wordbookId AND status = 'New' ORDER BY text COLLATE NOCASE LIMIT :limit")
    suspend fun nextNewWords(wordbookId: String, limit: Int): List<WordEntity>

    @Query("SELECT * FROM words WHERE wordbookId = :wordbookId AND status = 'New' ORDER BY RANDOM() LIMIT :limit")
    suspend fun randomNewWords(wordbookId: String, limit: Int): List<WordEntity>

    @Query(
        """
        SELECT words.* FROM words
        LEFT JOIN cards ON cards.wordId = words.id
            AND cards.userId = :userId
            AND cards.cardType = :cardType
        WHERE words.wordbookId = :wordbookId
            AND words.status = 'New'
            AND cards.id IS NULL
        ORDER BY words.text COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun nextWordsWithoutCard(
        wordbookId: String,
        userId: String,
        cardType: String,
        limit: Int
    ): List<WordEntity>

    @Query(
        """
        SELECT words.* FROM words
        LEFT JOIN cards ON cards.wordId = words.id
            AND cards.userId = :userId
            AND cards.cardType = :cardType
        WHERE words.wordbookId = :wordbookId
            AND words.status = 'New'
            AND cards.id IS NULL
        ORDER BY RANDOM()
        LIMIT :limit
        """
    )
    suspend fun randomWordsWithoutCard(
        wordbookId: String,
        userId: String,
        cardType: String,
        limit: Int
    ): List<WordEntity>

    @Query(
        """
        SELECT COUNT(*) FROM words
        LEFT JOIN cards ON cards.wordId = words.id
            AND cards.userId = :userId
            AND cards.cardType = :cardType
        WHERE words.wordbookId = :wordbookId
            AND words.status = 'New'
            AND cards.id IS NULL
        """
    )
    fun observeWordsWithoutCardCount(wordbookId: String, userId: String, cardType: String): Flow<Int>

    @Query("SELECT * FROM words WHERE wordbookId = :wordbookId ORDER BY text COLLATE NOCASE")
    suspend fun candidateWords(wordbookId: String): List<WordEntity>

    @Query(
        """
        SELECT * FROM words
        WHERE wordbookId = :wordbookId
            AND id != :targetWordId
            AND meaning != ''
        ORDER BY
            CASE WHEN :targetPos != '' AND pos = :targetPos THEN 0 ELSE 1 END,
            CASE WHEN :targetBook != '' AND book = :targetBook THEN 0 ELSE 1 END,
            CASE WHEN :targetUnit != '' AND unit = :targetUnit THEN 0 ELSE 1 END,
            text COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun optionCandidateWords(
        wordbookId: String,
        targetWordId: String,
        targetPos: String,
        targetBook: String,
        targetUnit: String,
        limit: Int
    ): List<WordEntity>

    @Query(
        """
        SELECT words.* FROM words
        LEFT JOIN user_mastered_words mastered
            ON mastered.userId = :userId
            AND mastered.wordKey = words.wordKey
        LEFT JOIN cards
            ON cards.userId = :userId
            AND cards.wordId = words.id
            AND cards.cardType = :cardType
        LEFT JOIN study_session_items existing_items
            ON existing_items.sessionId = :sessionId
            AND existing_items.wordKey = words.wordKey
        WHERE words.wordbookId = :wordbookId
            AND words.status = 'New'
            AND mastered.wordKey IS NULL
            AND cards.id IS NULL
            AND existing_items.wordKey IS NULL
        ORDER BY words.text COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun nextNewWordsForSession(
        wordbookId: String,
        userId: String,
        cardType: String,
        sessionId: String,
        limit: Int
    ): List<WordEntity>

    @Query(
        """
        SELECT words.* FROM words
        LEFT JOIN user_mastered_words mastered
            ON mastered.userId = :userId
            AND mastered.wordKey = words.wordKey
        LEFT JOIN cards
            ON cards.userId = :userId
            AND cards.wordId = words.id
            AND cards.cardType = :cardType
        LEFT JOIN study_session_items existing_items
            ON existing_items.sessionId = :sessionId
            AND existing_items.wordKey = words.wordKey
        WHERE words.wordbookId = :wordbookId
            AND words.status = 'New'
            AND mastered.wordKey IS NULL
            AND cards.id IS NULL
            AND existing_items.wordKey IS NULL
        ORDER BY RANDOM()
        LIMIT :limit
        """
    )
    suspend fun randomNewWordsForSession(
        wordbookId: String,
        userId: String,
        cardType: String,
        sessionId: String,
        limit: Int
    ): List<WordEntity>

    @Query("SELECT * FROM words WHERE wordbookId = :wordbookId AND dueAt <= :now AND status IN ('Learning', 'Review') ORDER BY dueAt ASC LIMIT :limit")
    suspend fun dueWords(wordbookId: String, now: Long, limit: Int): List<WordEntity>

    @Query("SELECT * FROM words WHERE wordbookId = :wordbookId AND (text LIKE '%' || :query || '%' OR meaning LIKE '%' || :query || '%') ORDER BY text COLLATE NOCASE LIMIT :limit")
    fun search(wordbookId: String, query: String, limit: Int = 80): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE wordbookId = :wordbookId ORDER BY text COLLATE NOCASE LIMIT :limit")
    fun firstWords(wordbookId: String, limit: Int = 80): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getById(id: String): WordEntity?

    @Query("SELECT * FROM words WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<WordEntity>

    @Query("SELECT * FROM words WHERE wordKey = :wordKey")
    suspend fun getByWordKey(wordKey: String): List<WordEntity>

    @Query("SELECT * FROM words")
    suspend fun allWords(): List<WordEntity>

    @Query("SELECT * FROM words WHERE wordbookId = :wordbookId ORDER BY text COLLATE NOCASE")
    suspend fun allByWordbook(wordbookId: String): List<WordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(words: List<WordEntity>): List<Long>

    @Update
    suspend fun update(word: WordEntity)

    @Query("UPDATE words SET isFavorite = :favorite, updatedAt = :updatedAt WHERE id = :wordId")
    suspend fun setFavorite(wordId: String, favorite: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE words
        SET
            meaning = :meaning,
            pos = :pos,
            meaningsJson = :meaningsJson,
            phonetic = :phonetic,
            example = :example,
            exampleCn = :exampleCn,
            root = :root,
            synonyms = :synonyms,
            collocations = :collocations,
            memoryTip = :memoryTip,
            updatedAt = :updatedAt
        WHERE id = :wordId
        """
    )
    suspend fun updateLexicalFields(
        wordId: String,
        meaning: String,
        pos: String,
        meaningsJson: String,
        phonetic: String,
        example: String,
        exampleCn: String,
        root: String,
        synonyms: String,
        collocations: String,
        memoryTip: String,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    @Query(
        """
        UPDATE words
        SET
            meaning = :meaning,
            pos = :pos,
            meaningsJson = :meaningsJson,
            phonetic = :phonetic,
            example = :example,
            exampleCn = :exampleCn,
            root = :root,
            synonyms = :synonyms,
            collocations = :collocations,
            memoryTip = :memoryTip,
            updatedAt = :updatedAt
        WHERE wordbookId = :wordbookId
            AND text = :text
        """
    )
    suspend fun updateLexicalFieldsByText(
        wordbookId: String,
        text: String,
        meaning: String,
        pos: String,
        meaningsJson: String,
        phonetic: String,
        example: String,
        exampleCn: String,
        root: String,
        synonyms: String,
        collocations: String,
        memoryTip: String,
        updatedAt: Long = System.currentTimeMillis()
    ): Int
}

@Dao
interface WordbookDao {
    @Query("SELECT * FROM wordbooks ORDER BY sortOrder ASC, title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<WordbookEntity>>

    @Query("SELECT * FROM wordbooks ORDER BY sortOrder ASC, title COLLATE NOCASE ASC")
    suspend fun all(): List<WordbookEntity>

    @Query(
        """
        SELECT
            wb.id,
            wb.title,
            wb.description,
            wb.sourceFile,
            wb.assetPath,
            wb.version,
            wb.wordCount,
            COUNT(w.id) AS total,
            COALESCE(SUM(CASE WHEN w.status != 'New' THEN 1 ELSE 0 END), 0) AS learned
        FROM wordbooks wb
        LEFT JOIN words w ON w.wordbookId = wb.id
        GROUP BY wb.id, wb.title, wb.description, wb.sourceFile, wb.assetPath, wb.version, wb.wordCount, wb.sortOrder
        ORDER BY wb.sortOrder ASC, wb.title COLLATE NOCASE ASC
        """
    )
    fun observeProgress(): Flow<List<WordbookProgressRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(wordbooks: List<WordbookEntity>)
}

@Dao
interface LearningStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalWordbooks(wordbooks: List<LocalWordbookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalWordbookWords(words: List<LocalWordbookWordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLearningWords(words: List<LearningWordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudyPlan(plan: StudyPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudyPlanItems(items: List<StudyPlanItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudySession(session: StudySessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudySessionItems(items: List<StudySessionItemEntity>)

    @Query("SELECT * FROM study_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun studySessionById(sessionId: String): StudySessionEntity?

    @Query(
        """
        SELECT * FROM study_sessions
        WHERE userId = :userId
            AND wordbookId = :wordbookId
            AND studyDayEpoch = :studyDayEpoch
            AND mode = :mode
        LIMIT 1
        """
    )
    suspend fun studySession(userId: String, wordbookId: String, studyDayEpoch: Long, mode: String): StudySessionEntity?

    @Query("SELECT * FROM study_session_items WHERE sessionId = :sessionId ORDER BY position ASC")
    suspend fun studySessionItems(sessionId: String): List<StudySessionItemEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM study_session_items WHERE sessionId = :sessionId")
    suspend fun maxStudySessionPosition(sessionId: String): Int

    @Query("DELETE FROM study_session_items WHERE sessionId = :sessionId")
    suspend fun deleteStudySessionItems(sessionId: String)

    @Query(
        """
        UPDATE study_session_items
        SET
            optionsJson = :optionsJson,
            correctOptionId = :correctOptionId,
            updatedAt = :updatedAt
        WHERE sessionId = :sessionId
            AND position = :position
        """
    )
    suspend fun updateSessionItemOptions(
        sessionId: String,
        position: Int,
        optionsJson: String,
        correctOptionId: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMasteredWord(word: UserMasteredWordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavoriteWord(word: UserFavoriteWordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserWordNote(note: UserWordNoteEntity)

    @Query("SELECT wordKey FROM user_mastered_words WHERE userId = :userId")
    suspend fun masteredWordKeys(userId: String): List<String>

    @Query("DELETE FROM user_favorite_words WHERE userId = :userId AND wordKey = :wordKey")
    suspend fun deleteFavoriteWord(userId: String, wordKey: String)

    @Query("DELETE FROM user_word_notes WHERE userId = :userId AND wordKey = :wordKey")
    suspend fun deleteUserWordNote(userId: String, wordKey: String)

    @Query(
        """
        UPDATE learning_words
        SET
            status = :status,
            dueAt = :dueAt,
            learnedAt = :learnedAt,
            reviewCount = :reviewCount,
            lapseCount = :lapseCount,
            stability = :stability,
            difficulty = :difficulty,
            updatedAt = :updatedAt
        WHERE id = :learningWordId
        """
    )
    suspend fun updateLearningWordProgress(
        learningWordId: String,
        status: String,
        dueAt: Long,
        learnedAt: Long?,
        reviewCount: Int,
        lapseCount: Int,
        stability: Double,
        difficulty: Double,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE study_plan_items
        SET
            status = :status,
            startedAt = COALESCE(startedAt, :startedAt),
            completedAt = CASE WHEN :completedAt IS NULL THEN completedAt ELSE COALESCE(completedAt, :completedAt) END,
            skippedAt = CASE WHEN :skippedAt IS NULL THEN skippedAt ELSE COALESCE(skippedAt, :skippedAt) END,
            updatedAt = :updatedAt
        WHERE userId = :userId
            AND learningWordId = :learningWordId
        """
    )
    suspend fun updatePlanItemStatus(
        userId: String,
        learningWordId: String,
        status: String,
        startedAt: Long?,
        completedAt: Long?,
        skippedAt: Long?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE study_session_items
        SET
            status = 'revealed',
            correctOptionId = :correctOptionId,
            revealedAt = COALESCE(revealedAt, :revealedAt),
            updatedAt = :revealedAt
        WHERE sessionId = :sessionId
            AND cardId = :cardId
            AND status != 'completed'
        """
    )
    suspend fun revealSessionItemByCard(sessionId: String, cardId: Long, correctOptionId: String, revealedAt: Long)

    @Query(
        """
        UPDATE study_session_items
        SET
            status = 'completed',
            selectedOptionId = :selectedOptionId,
            answeredAt = COALESCE(answeredAt, :answeredAt),
            completedAt = COALESCE(completedAt, :answeredAt),
            durationMs = :durationMs,
            result = :result,
            cardStateBefore = :cardStateBefore,
            cardStateAfter = :cardStateAfter,
            updatedAt = :answeredAt
        WHERE sessionId = :sessionId
            AND cardId = :cardId
            AND status != 'completed'
        """
    )
    suspend fun completeSessionItemByCard(
        sessionId: String,
        cardId: Long,
        answeredAt: Long,
        selectedOptionId: String,
        durationMs: Long,
        result: String,
        cardStateBefore: String,
        cardStateAfter: String
    )

    @Query(
        """
        UPDATE study_session_items
        SET
            status = 'completed',
            answeredAt = COALESCE(answeredAt, :answeredAt),
            completedAt = COALESCE(completedAt, :answeredAt),
            result = :result,
            updatedAt = :answeredAt
        WHERE sessionId = :sessionId
            AND wordId = :wordId
            AND status != 'completed'
        """
    )
    suspend fun completeSessionItemByWord(sessionId: String, wordId: String, answeredAt: Long, result: String)

    @Query(
        """
        UPDATE study_sessions
        SET
            completedCount = (
                SELECT COUNT(*) FROM study_session_items
                WHERE sessionId = :sessionId AND status = 'completed'
            ),
            totalCount = (
                SELECT COUNT(*) FROM study_session_items
                WHERE sessionId = :sessionId
            ),
            currentPosition = COALESCE(
                (
                    SELECT MIN(position) FROM study_session_items
                    WHERE sessionId = :sessionId AND status != 'completed'
                ),
                (
                    SELECT COUNT(*) FROM study_session_items
                    WHERE sessionId = :sessionId
                )
            ),
            status = CASE
                WHEN (
                    SELECT COUNT(*) FROM study_session_items
                    WHERE sessionId = :sessionId AND status != 'completed'
                ) = 0 THEN 'completed'
                WHEN status = 'building' THEN 'building'
                ELSE 'active'
            END,
            finishedAt = CASE
                WHEN (
                    SELECT COUNT(*) FROM study_session_items
                    WHERE sessionId = :sessionId AND status != 'completed'
                ) = 0 THEN :updatedAt
                ELSE finishedAt
            END,
            lastActiveAt = :updatedAt,
            updatedAt = :updatedAt
        WHERE id = :sessionId
        """
    )
    suspend fun refreshSessionProgress(sessionId: String, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE study_sessions
        SET
            completedCount = (
                SELECT COUNT(*) FROM study_session_items
                WHERE sessionId = :sessionId AND status = 'completed'
            ),
            totalCount = (
                SELECT COUNT(*) FROM study_session_items
                WHERE sessionId = :sessionId
            ),
            currentPosition = COALESCE(
                (
                    SELECT MIN(position) FROM study_session_items
                    WHERE sessionId = :sessionId AND status != 'completed'
                ),
                (
                    SELECT COUNT(*) FROM study_session_items
                    WHERE sessionId = :sessionId
                )
            ),
            status = CASE
                WHEN (
                    SELECT COUNT(*) FROM study_session_items
                    WHERE sessionId = :sessionId AND status != 'completed'
                ) = 0 THEN 'completed'
                ELSE 'active'
            END,
            finishedAt = CASE
                WHEN (
                    SELECT COUNT(*) FROM study_session_items
                    WHERE sessionId = :sessionId AND status != 'completed'
                ) = 0 THEN :updatedAt
                ELSE finishedAt
            END,
            lastActiveAt = :updatedAt,
            updatedAt = :updatedAt
        WHERE id = :sessionId
        """
    )
    suspend fun finishSessionBuild(sessionId: String, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface ReviewDao {
    @Insert
    suspend fun insert(log: ReviewLogEntity)

    @Query("SELECT COUNT(*) FROM review_logs INNER JOIN words ON words.id = review_logs.wordId WHERE words.wordbookId = :wordbookId AND review_logs.mode = 'spelling'")
    fun observeSpellingTotal(wordbookId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM review_logs INNER JOIN words ON words.id = review_logs.wordId WHERE words.wordbookId = :wordbookId AND review_logs.mode = 'spelling' AND review_logs.correct = 1")
    fun observeSpellingCorrect(wordbookId: String): Flow<Int>

    @Query("SELECT review_logs.createdAt FROM review_logs INNER JOIN words ON words.id = review_logs.wordId WHERE words.wordbookId = :wordbookId")
    fun observeStudyTimestamps(wordbookId: String): Flow<List<Long>>

    @Query(
        """
        SELECT COALESCE(AVG(durationMs), 18000) FROM (
            SELECT durationMs FROM review_logs
            WHERE userId = :userId AND durationMs > 0
            ORDER BY reviewedAt DESC
            LIMIT :limit
        )
        """
    )
    suspend fun averageDurationMs(userId: String, limit: Int = 200): Double

    @Query("SELECT isCorrect FROM review_logs WHERE cardId = :cardId ORDER BY reviewedAt DESC LIMIT :limit")
    suspend fun recentCorrects(cardId: Long, limit: Int = 5): List<Boolean>

    @Query("SELECT COUNT(*) FROM review_logs WHERE userId = :userId")
    suspend fun countByUser(userId: String): Int
}

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE id = :cardId LIMIT 1")
    suspend fun getById(cardId: Long): CardEntity?

    @Query("SELECT * FROM cards WHERE id IN (:cardIds)")
    suspend fun getByIds(cardIds: List<Long>): List<CardEntity>

    @Query("SELECT * FROM cards WHERE userId = :userId AND wordId = :wordId AND cardType = :cardType LIMIT 1")
    suspend fun getByWordAndType(userId: String, wordId: String, cardType: String): CardEntity?

    @Query(
        """
        SELECT cards.* FROM cards
        INNER JOIN words ON words.id = cards.wordId
        WHERE cards.userId = :userId
            AND words.wordbookId = :wordbookId
            AND cards.dueAt <= :now
            AND cards.state IN ('learning', 'relearning')
        ORDER BY cards.dueAt ASC
        LIMIT :limit
        """
    )
    suspend fun dueLearningCards(userId: String, wordbookId: String, now: Long, limit: Int): List<CardEntity>

    @Query(
        """
        SELECT cards.* FROM cards
        INNER JOIN words ON words.id = cards.wordId
        WHERE cards.userId = :userId
            AND words.wordbookId = :wordbookId
            AND cards.dueAt <= :now
            AND cards.state IN ('learning', 'relearning')
            AND NOT EXISTS (
                SELECT 1 FROM study_session_items
                WHERE study_session_items.sessionId = :sessionId
                    AND study_session_items.cardId = cards.id
            )
        ORDER BY cards.dueAt ASC
        LIMIT :limit
        """
    )
    suspend fun dueLearningCardsForSession(
        userId: String,
        wordbookId: String,
        now: Long,
        sessionId: String,
        limit: Int
    ): List<CardEntity>

    @Query(
        """
        SELECT cards.* FROM cards
        INNER JOIN words ON words.id = cards.wordId
        WHERE cards.userId = :userId
            AND words.wordbookId = :wordbookId
            AND cards.dueAt <= :now
            AND cards.state = 'review'
        ORDER BY cards.dueAt ASC
        LIMIT :limit
        """
    )
    suspend fun dueReviewCards(userId: String, wordbookId: String, now: Long, limit: Int): List<CardEntity>

    @Query(
        """
        SELECT cards.* FROM cards
        INNER JOIN words ON words.id = cards.wordId
        WHERE cards.userId = :userId
            AND words.wordbookId = :wordbookId
            AND cards.dueAt <= :now
            AND cards.state = 'review'
            AND NOT EXISTS (
                SELECT 1 FROM study_session_items
                WHERE study_session_items.sessionId = :sessionId
                    AND study_session_items.cardId = cards.id
            )
        ORDER BY cards.dueAt ASC
        LIMIT :limit
        """
    )
    suspend fun dueReviewCardsForSession(
        userId: String,
        wordbookId: String,
        now: Long,
        sessionId: String,
        limit: Int
    ): List<CardEntity>

    @Query(
        """
        SELECT cards.* FROM cards
        INNER JOIN words ON words.id = cards.wordId
        WHERE cards.userId = :userId
            AND cards.cardType = :cardType
            AND words.wordbookId = :wordbookId
            AND cards.state = 'new'
        ORDER BY cards.createdAt ASC
        LIMIT :limit
        """
    )
    suspend fun newCards(userId: String, wordbookId: String, cardType: String, limit: Int): List<CardEntity>

    @Query(
        """
        SELECT COUNT(*) FROM cards
        INNER JOIN words ON words.id = cards.wordId
        WHERE cards.userId = :userId
            AND words.wordbookId = :wordbookId
            AND cards.dueAt <= :now
            AND cards.state IN ('learning', 'relearning')
        """
    )
    fun observeDueLearningCount(userId: String, wordbookId: String, now: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM cards
        INNER JOIN words ON words.id = cards.wordId
        WHERE cards.userId = :userId
            AND words.wordbookId = :wordbookId
            AND cards.dueAt <= :now
            AND cards.state = 'review'
        """
    )
    fun observeDueReviewCount(userId: String, wordbookId: String, now: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM cards
        INNER JOIN words ON words.id = cards.wordId
        WHERE cards.userId = :userId
            AND cards.cardType = :cardType
            AND words.wordbookId = :wordbookId
            AND cards.state = 'new'
        """
    )
    fun observeNewCardCount(userId: String, wordbookId: String, cardType: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(card: CardEntity): Long

    @Update
    suspend fun update(card: CardEntity)

    @Query(
        """
        UPDATE cards
        SET state = 'review',
            dueAt = :dueAt,
            scheduledDays = 36500,
            updatedAt = :updatedAt
        WHERE userId = :userId
            AND wordId = :wordId
        """
    )
    suspend fun deferCards(userId: String, wordId: String, dueAt: Long, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface TodayFsrsSessionDao {
    @Query(
        """
        SELECT * FROM today_fsrs_session_items
        WHERE userId = :userId
            AND wordbookId = :wordbookId
            AND studyDayEpoch = :studyDayEpoch
        ORDER BY position ASC
        """
    )
    suspend fun sessionItems(userId: String, wordbookId: String, studyDayEpoch: Long): List<TodayFsrsSessionItemEntity>

    @Query(
        """
        SELECT COUNT(*) FROM today_fsrs_session_items
        WHERE userId = :userId
            AND wordbookId = :wordbookId
            AND studyDayEpoch = :studyDayEpoch
        """
    )
    fun observeSessionItemCount(userId: String, wordbookId: String, studyDayEpoch: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM today_fsrs_session_items
        INNER JOIN cards ON cards.id = today_fsrs_session_items.cardId
        WHERE today_fsrs_session_items.userId = :userId
            AND today_fsrs_session_items.wordbookId = :wordbookId
            AND today_fsrs_session_items.studyDayEpoch = :studyDayEpoch
            AND today_fsrs_session_items.completedAt IS NULL
            AND cards.state = 'new'
        """
    )
    fun observePendingNewCount(userId: String, wordbookId: String, studyDayEpoch: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM today_fsrs_session_items
        INNER JOIN cards ON cards.id = today_fsrs_session_items.cardId
        WHERE today_fsrs_session_items.userId = :userId
            AND today_fsrs_session_items.wordbookId = :wordbookId
            AND today_fsrs_session_items.studyDayEpoch = :studyDayEpoch
            AND today_fsrs_session_items.completedAt IS NULL
            AND cards.state != 'new'
        """
    )
    fun observePendingDueCount(userId: String, wordbookId: String, studyDayEpoch: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TodayFsrsSessionItemEntity>)

    @Query(
        """
        UPDATE today_fsrs_session_items
        SET completedAt = COALESCE(completedAt, :completedAt),
            updatedAt = :completedAt
        WHERE userId = :userId
            AND studyDayEpoch = :studyDayEpoch
            AND cardId = :cardId
        """
    )
    suspend fun markCompleted(userId: String, cardId: Long, studyDayEpoch: Long, completedAt: Long)

    @Query(
        """
        UPDATE today_fsrs_session_items
        SET completedAt = COALESCE(completedAt, :completedAt),
            updatedAt = :completedAt
        WHERE userId = :userId
            AND studyDayEpoch = :studyDayEpoch
            AND wordId = :wordId
        """
    )
    suspend fun markCompletedByWord(userId: String, wordId: String, studyDayEpoch: Long, completedAt: Long)

    @Query(
        """
        DELETE FROM today_fsrs_session_items
        WHERE userId = :userId
            AND wordbookId = :wordbookId
            AND studyDayEpoch = :studyDayEpoch
        """
    )
    suspend fun deleteSession(userId: String, wordbookId: String, studyDayEpoch: Long)

    @Query(
        """
        DELETE FROM today_fsrs_session_items
        WHERE userId = :userId
            AND wordbookId = :wordbookId
            AND studyDayEpoch < :studyDayEpoch
        """
    )
    suspend fun deleteOldSessions(userId: String, wordbookId: String, studyDayEpoch: Long)
}

@Dao
interface UserFsrsSettingDao {
    @Query("SELECT * FROM user_fsrs_setting WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: String): UserFsrsSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: UserFsrsSettingEntity)
}

@Dao
interface WordStudyRecordDao {
    @Query("SELECT * FROM word_study_records WHERE wordId = :wordId LIMIT 1")
    suspend fun getByWordId(wordId: String): WordStudyRecordEntity?

    @Query("SELECT * FROM word_study_records WHERE wordId IN (:wordIds)")
    suspend fun getByWordIds(wordIds: List<String>): List<WordStudyRecordEntity>

    @Query(
        """
        SELECT firstLearnedAt FROM word_study_records
        INNER JOIN words ON words.id = word_study_records.wordId
        WHERE words.wordbookId = :wordbookId AND firstLearnedAt IS NOT NULL
        UNION
        SELECT completedTodayAt FROM word_study_records
        INNER JOIN words ON words.id = word_study_records.wordId
        WHERE words.wordbookId = :wordbookId AND completedTodayAt IS NOT NULL
        UNION
        SELECT lastReviewedAt FROM word_study_records
        INNER JOIN words ON words.id = word_study_records.wordId
        WHERE words.wordbookId = :wordbookId AND lastReviewedAt IS NOT NULL
        """
    )
    fun observeStudyTimestamps(wordbookId: String): Flow<List<Long>>

    @Query(
        "SELECT word_study_records.* FROM word_study_records " +
            "INNER JOIN words ON words.id = word_study_records.wordId " +
            "WHERE words.wordbookId = :wordbookId " +
            "AND word_study_records.status IN ('New', 'Preview', 'Intro', 'Detail', 'Learning', 'ReviewDue') " +
            "ORDER BY word_study_records.firstLearnedAt ASC, word_study_records.lastShownAt ASC"
    )
    suspend fun activeRecords(wordbookId: String): List<WordStudyRecordEntity>

    @Query("SELECT COUNT(*) FROM word_study_records INNER JOIN words ON words.id = word_study_records.wordId WHERE words.wordbookId = :wordbookId AND word_study_records.firstLearnedAt >= :startOfToday")
    suspend fun countTodayStarted(wordbookId: String, startOfToday: Long): Int

    @Query("SELECT COUNT(*) FROM word_study_records INNER JOIN words ON words.id = word_study_records.wordId WHERE words.wordbookId = :wordbookId AND word_study_records.completedTodayAt >= :startOfToday")
    suspend fun countCompletedToday(wordbookId: String, startOfToday: Long): Int

    @Query("SELECT COALESCE(SUM(word_study_records.todayForgetCount), 0) FROM word_study_records INNER JOIN words ON words.id = word_study_records.wordId WHERE words.wordbookId = :wordbookId AND word_study_records.studyDayEpoch = :todayEpoch")
    suspend fun todayForgetCount(wordbookId: String, todayEpoch: Long): Int

    @Query("SELECT COALESCE(SUM(word_study_records.todayWrongChoiceCount), 0) FROM word_study_records INNER JOIN words ON words.id = word_study_records.wordId WHERE words.wordbookId = :wordbookId AND word_study_records.studyDayEpoch = :todayEpoch")
    suspend fun todayWrongChoiceCount(wordbookId: String, todayEpoch: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: WordStudyRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<WordStudyRecordEntity>)
}

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE wordId = :wordId LIMIT 1")
    suspend fun getByWordId(wordId: String): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes INNER JOIN words ON words.id = notes.wordId WHERE words.wordbookId = :wordbookId AND length(notes.body) > 0")
    fun observeNoteCount(wordbookId: String): Flow<Int>

    @Query("DELETE FROM notes WHERE wordId = :wordId")
    suspend fun deleteByWordId(wordId: String)
}
