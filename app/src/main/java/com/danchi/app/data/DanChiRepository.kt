package com.danchi.app.data

import android.content.Context
import com.danchi.app.domain.DictionaryEntry
import com.danchi.app.domain.DictionaryRepository
import com.danchi.app.domain.FsrsBuildProgress
import com.danchi.app.domain.FsrsSessionSnapshot
import com.danchi.app.domain.FsrsStudyItem
import com.danchi.app.domain.LibraryStats
import com.danchi.app.domain.MeaningChoiceOption
import com.danchi.app.domain.StudyProfileStats
import com.danchi.app.domain.StudySettings
import com.danchi.app.domain.StudyWordOrder
import com.danchi.app.domain.TodayPlan
import com.danchi.app.domain.Word
import com.danchi.app.domain.Wordbook
import com.danchi.app.domain.WordbookProgress
import com.danchi.app.domain.WordStatus
import com.danchi.app.domain.WordPatch
import com.danchi.app.scheduler.AnswerCardInput
import com.danchi.app.scheduler.FsrsCardType
import com.danchi.app.scheduler.FsrsScheduler
import com.danchi.app.scheduler.SchedulerCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections

class DanChiRepository(
    private val context: Context,
    private val wordDao: WordDao,
    private val wordbookDao: WordbookDao,
    private val cardDao: CardDao,
    private val reviewDao: ReviewDao,
    private val noteDao: NoteDao,
    private val userFsrsSettingDao: UserFsrsSettingDao,
    private val learningStateDao: LearningStateDao,
    private val dictionaryRepository: DictionaryRepository
) {
    private val settingsStore = SettingsStore(context)
    private val activeFsrsTailBuilds = Collections.synchronizedSet(mutableSetOf<String>())

    val settings = settingsStore.settings

    private companion object {
        const val InitialFsrsBatchSize = 5
        const val OptionCandidateLimit = 120
    }

    suspend fun seedWordbook(): Int {
        val inserted = WordbookSeeder.seedIfNeeded(context, wordbookDao, wordDao)
        syncLearningMirror()
        return inserted
    }

    fun observeWordbooks(): Flow<List<Wordbook>> {
        return wordbookDao.observeAll().map { rows -> rows.map { it.toDomain() } }
    }

    fun observeWordbookProgress(): Flow<List<WordbookProgress>> {
        return wordbookDao.observeProgress().map { rows ->
            rows.map {
                WordbookProgress(
                    wordbook = Wordbook(
                        id = it.id,
                        title = it.title,
                        description = it.description,
                        sourceFile = it.sourceFile,
                        assetPath = it.assetPath,
                        version = it.version,
                        wordCount = it.wordCount
                    ),
                    total = it.total,
                    learned = it.learned
                )
            }
        }
    }

    fun observeStats(): Flow<LibraryStats> {
        return settings.flatMapLatest { settings ->
            val wordbookId = settings.selectedWordbookId
            combine(
                wordDao.observeNewCount(wordbookId),
                wordDao.observeLearnedCount(wordbookId),
                wordDao.observeStatusCount(wordbookId, WordStatus.Learning.name),
                wordDao.observeStatusCount(wordbookId, WordStatus.Review.name),
                wordDao.observeStatusCount(wordbookId, WordStatus.Mastered.name),
                wordDao.observeFavoriteCount(wordbookId),
                noteDao.observeNoteCount(wordbookId)
            ) { values ->
                val newWords = values[0] as Int
                val learned = values[1] as Int
                val learning = values[2] as Int
                val review = values[3] as Int
                val mastered = values[4] as Int
                val favorite = values[5] as Int
                val notes = values[6] as Int
                LibraryStats(
                    total = newWords + learned,
                    newWords = newWords,
                    learning = learning,
                    review = review,
                    mastered = mastered,
                    favorite = favorite,
                    notes = notes
                )
            }
        }
    }

    fun observeTodayPlan(): Flow<TodayPlan> {
        val now = System.currentTimeMillis()
        return settings.flatMapLatest { settings ->
            val studyDayEpoch = todayEpoch(now)
            val sessionId = todaySessionId(settings.selectedWordbookId, studyDayEpoch)
            combine(
                wordDao.observeNewCount(settings.selectedWordbookId),
                cardDao.observeDueLearningCount(DefaultUserId, settings.selectedWordbookId, now),
                cardDao.observeDueReviewCount(DefaultUserId, settings.selectedWordbookId, now),
                learningStateDao.observeStudySessionItemCount(sessionId),
                learningStateDao.observePendingSessionNewCount(sessionId),
                learningStateDao.observePendingSessionDueCount(sessionId)
            ) { values: Array<Int> ->
                val availableNewWords = values[0]
                val dueCount = values[1] + values[2]
                val sessionItemCount = values[3]
                val pendingNewCount = values[4]
                val pendingDueCount = values[5]
                val newCount = if (sessionItemCount > 0) {
                    pendingNewCount
                } else {
                    availableNewWords.coerceAtMost(settings.dailyNewWords.coerceAtLeast(0))
                }
                val reviewCount = if (sessionItemCount > 0) pendingDueCount else dueCount
                val estimatedSeconds = reviewCount * 18 + newCount * 45
                val estimatedMinutes = ((estimatedSeconds + 59) / 60)
                    .coerceAtLeast(if (reviewCount + newCount > 0) 1 else 0)
                TodayPlan(
                    newCount = newCount,
                    dueReviewCount = reviewCount,
                    reviewLimit = reviewCount,
                    estimatedMinutes = estimatedMinutes
                )
            }
        }
    }

    fun observeStudyProfileStats(): Flow<StudyProfileStats> {
        return settings.flatMapLatest { settings ->
            val wordbookId = settings.selectedWordbookId
            combine(
                wordDao.observeLearnedTimestamps(wordbookId),
                reviewDao.observeStudyTimestamps(wordbookId),
                wordDao.observeLearnedCount(wordbookId)
            ) { learnedTimestamps, reviewTimestamps, learnedCount ->
                val studyDays = (learnedTimestamps + reviewTimestamps)
                    .filter { it > 0L }
                    .map { it.toLocalDate() }
                    .toSet()

                StudyProfileStats(
                    streakDays = currentStreakDays(studyDays),
                    totalStudyDays = studyDays.size,
                    totalLearnedWords = learnedCount
                )
            }
        }
    }

    fun observeDictionary(query: String): Flow<List<Word>> {
        val trimmed = query.trim()
        return settings.flatMapLatest { settings ->
            val flow = if (trimmed.isBlank()) {
                wordDao.firstWords(settings.selectedWordbookId)
            } else {
                wordDao.search(settings.selectedWordbookId, trimmed)
            }
            flow.map { entities -> entities.map { it.toDomain() } }
        }
    }

    suspend fun lookupDictionaryEntry(word: String): DictionaryEntry? {
        return dictionaryRepository.lookup(word)
    }

    suspend fun searchDictionaryPrefix(query: String, limit: Int = 20): List<DictionaryEntry> {
        return dictionaryRepository.searchPrefix(query, limit)
    }

    suspend fun buildDictionaryPatch(word: String): WordPatch? {
        return dictionaryRepository.buildWordPatch(word)
    }

    suspend fun buildTodayFsrsSession(
        settings: StudySettings,
        onProgress: suspend (FsrsBuildProgress) -> Unit = {}
    ): FsrsSessionSnapshot {
        suspend fun progress(step: Int, title: String, detail: String) {
            onProgress(FsrsBuildProgress(title = title, detail = detail, step = step, totalSteps = 5))
        }

        val now = System.currentTimeMillis()
        progress(1, "检查今日清单", "如果今天已经生成过，会直接恢复原进度。")
        loadFsrsSettings(settings)
        val studyDayEpoch = todayEpoch(now)
        val sessionId = todaySessionId(settings.selectedWordbookId, studyDayEpoch)

        learningStateDao.studySessionById(sessionId)?.let { existing ->
            progress(2, "读取已保存清单", "正在恢复未完成题目和学习位置。")
            if (existing.status != "building") {
                progress(3, "检查到期学习卡", "如果上一题进入 learning 阶段，会补入本轮到期卡。")
                appendDueLearningCardsToSession(existing, settings, now)
            }
            progress(4, "刷新清单进度", "同步已完成、未完成和当前位置。")
            learningStateDao.refreshSessionProgress(sessionId, now)
            progress(5, "进入今日提分", "已定位到上次未完成的位置。")
            val snapshot = buildFsrsSessionSnapshot(sessionId, isResumed = true, settings = settings, now = now)
            return snapshot
        }

        val reviewLimit = settings.reviewLimit.coerceAtLeast(0)
        val newLimit = settings.dailyNewWords.coerceAtLeast(0)
        progress(2, "读取首批复习卡", "优先取到期的 learning、relearning 和 review。")
        progress(3, "补入首批新词", "复习卡不足 5 题时，再用新词补齐。")
        val items = buildFsrsSessionItems(
            sessionId = sessionId,
            wordbookId = settings.selectedWordbookId,
            positionStart = 0,
            dueLimit = InitialFsrsBatchSize.coerceAtMost(reviewLimit),
            newLimit = InitialFsrsBatchSize.coerceAtMost(newLimit),
            maxItems = InitialFsrsBatchSize,
            wordOrder = settings.wordOrder,
            now = now
        )
        if (items.isEmpty()) {
            progress(5, "今日暂无任务", "没有到期复习，也没有可补入的新词。")
            return FsrsSessionSnapshot(
                sessionId = sessionId,
                items = emptyList(),
                currentPosition = 0,
                totalCount = 0,
                completedCount = 0,
                isResumed = false,
                isBuilding = false
            )
        }

        progress(4, "固定首批题目", "正在保存前 ${items.size} 题，进入后后台继续补齐。")
        learningStateDao.upsertStudySession(
            StudySessionEntity(
                id = sessionId,
                wordbookId = settings.selectedWordbookId,
                planId = LearningKeys.planId(DefaultUserId, settings.selectedWordbookId),
                studyDayEpoch = studyDayEpoch,
                mode = LearningKeys.TodayFsrsSessionMode,
                status = "building",
                totalCount = items.size,
                settingsFingerprint = fsrsSettingsFingerprint(settings),
                startedAt = now,
                lastActiveAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
        learningStateDao.upsertStudySessionItems(items)
        progress(5, "进入今日提分", "首批题目已准备好，后续题目会在后台继续生成。")
        return buildFsrsSessionSnapshot(sessionId, isResumed = false, settings = settings, now = now)
    }

    suspend fun completeTodayFsrsSession(
        settings: StudySettings,
        onProgress: suspend (FsrsBuildProgress) -> Unit = {}
    ): FsrsSessionSnapshot {
        suspend fun progress(step: Int, title: String, detail: String) {
            onProgress(FsrsBuildProgress(title = title, detail = detail, step = step, totalSteps = 4))
        }

        val now = System.currentTimeMillis()
        val studyDayEpoch = todayEpoch(now)
        val sessionId = todaySessionId(settings.selectedWordbookId, studyDayEpoch)
        if (!activeFsrsTailBuilds.add(sessionId)) {
            return buildFsrsSessionSnapshot(sessionId, isResumed = true, settings = settings, now = now)
        }
        try {
            val session = learningStateDao.studySessionById(sessionId)
                ?: return buildTodayFsrsSession(settings, onProgress)
            if (session.status != "building") {
                return buildFsrsSessionSnapshot(sessionId, isResumed = true, settings = settings, now = now)
            }

            progress(1, "准备后续复习卡", "继续补齐今日到期的 FSRS 复习卡。")
            val rows = learningStateDao.studySessionItems(sessionId)
            val existingDueCount = rows.count { it.queueReason != "new" }
            val existingNewCount = rows.count { it.queueReason == "new" }
            val remainingDue = (settings.reviewLimit.coerceAtLeast(0) - existingDueCount).coerceAtLeast(0)
            val remainingNew = (settings.dailyNewWords.coerceAtLeast(0) - existingNewCount).coerceAtLeast(0)
            val startPosition = learningStateDao.maxStudySessionPosition(sessionId) + 1

            progress(2, "补入后续新词", "使用数据库索引排除已掌握、已建卡和已在清单中的单词。")
            val appended = buildFsrsSessionItems(
                sessionId = sessionId,
                wordbookId = settings.selectedWordbookId,
                positionStart = startPosition,
                dueLimit = remainingDue,
                newLimit = remainingNew,
                maxItems = remainingDue + remainingNew,
                wordOrder = settings.wordOrder,
                now = now
            )
            if (appended.isNotEmpty()) {
                progress(3, "保存后续题目", "正在写入 ${appended.size} 道后续题目和固定选项。")
                learningStateDao.upsertStudySessionItems(appended)
            }
            progress(4, "后续题目已准备好", "完整今日清单已保存，下次进入会直接恢复。")
            learningStateDao.finishSessionBuild(sessionId, now)
            return buildFsrsSessionSnapshot(sessionId, isResumed = true, settings = settings, now = now)
        } finally {
            activeFsrsTailBuilds.remove(sessionId)
        }
    }

    suspend fun answerRecognition(
        cardId: Long,
        selectedWordId: String,
        options: List<String>,
        durationMs: Long,
        studySettings: StudySettings,
        selectedOptionId: String = selectedWordId,
        usedHint: Boolean = false
    ): Boolean {
        val cardEntity = cardDao.getById(cardId) ?: return false
        val word = wordDao.getById(cardEntity.wordId) ?: return false
        val now = System.currentTimeMillis()
        val settings = loadFsrsSettings(studySettings)
        val recentCorrectCount = reviewDao.recentCorrects(cardId)
            .takeWhile { it }
            .size
        val correct = selectedWordId == word.id
        val result = FsrsScheduler.answerCard(
            AnswerCardInput(
                card = cardEntity.toSchedulerCard(),
                settings = settings,
                firstAnswerCorrect = correct,
                usedHint = usedHint,
                durationMs = durationMs,
                questionType = FsrsCardType.Recognition.value,
                optionCount = options.size,
                answerSnapshot = buildAnswerSnapshot(selectedWordId, word.id, options),
                recentCorrectCount = recentCorrectCount,
                now = now
            )
        )
        val wordKey = word.resolvedWordKey()
        val learningWordId = word.resolvedLearningWordId()
        val sessionId = todaySessionId(word.wordbookId, todayEpoch(now))
        cardDao.update(
            result.card.toEntity().copy(
                wordKey = wordKey,
                learningWordId = learningWordId
            )
        )
        reviewDao.insert(
            result.reviewLog.toEntity().copy(
                wordKey = wordKey,
                learningWordId = learningWordId,
                wordbookId = word.wordbookId,
                sessionId = sessionId
            )
        )
        syncWordState(result.card)
        learningStateDao.completeSessionItemByCard(
            sessionId = sessionId,
            cardId = cardId,
            answeredAt = now,
            selectedOptionId = selectedOptionId,
            durationMs = durationMs,
            result = if (correct) "correct" else "wrong",
            cardStateBefore = result.reviewLog.stateBefore.value,
            cardStateAfter = result.reviewLog.stateAfter.value
        )
        learningStateDao.refreshSessionProgress(sessionId, now)
        return correct
    }

    suspend fun revealTodayRecognitionAnswer(cardId: Long, correctOptionId: String) {
        val now = System.currentTimeMillis()
        val card = cardDao.getById(cardId) ?: return
        val word = wordDao.getById(card.wordId) ?: return
        val sessionId = todaySessionId(word.wordbookId, todayEpoch(now))
        learningStateDao.revealSessionItemByCard(sessionId, cardId, correctOptionId, now)
        learningStateDao.refreshSessionProgress(sessionId, now)
    }

    suspend fun setFavorite(word: Word, favorite: Boolean) {
        val entity = wordDao.getById(word.id) ?: return
        val now = System.currentTimeMillis()
        wordDao.setFavorite(word.id, favorite, now)
        val wordKey = entity.resolvedWordKey()
        val learningWordId = entity.resolvedLearningWordId()
        if (favorite) {
            learningStateDao.upsertFavoriteWord(
                UserFavoriteWordEntity(
                    wordKey = wordKey,
                    word = entity.text,
                    firstWordbookId = entity.wordbookId,
                    firstLearningWordId = learningWordId,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            learningStateDao.deleteFavoriteWord(DefaultUserId, wordKey)
        }
    }

    suspend fun markMastered(word: Word) {
        val now = System.currentTimeMillis()
        val entity = wordDao.getById(word.id) ?: return
        wordDao.update(
            entity.copy(
                status = WordStatus.Mastered.name,
                dueAt = Long.MAX_VALUE,
                learnedAt = entity.learnedAt ?: now,
                updatedAt = now
            )
        )
        val masteredWord = entity.copy(
            status = WordStatus.Mastered.name,
            dueAt = Long.MAX_VALUE,
            learnedAt = entity.learnedAt ?: now,
            updatedAt = now
        )
        syncMasteredWord(masteredWord, now)
        cardDao.deferCards(DefaultUserId, word.id, Long.MAX_VALUE, now)
        val sessionId = todaySessionId(entity.wordbookId, todayEpoch(now))
        learningStateDao.completeSessionItemByWord(sessionId, word.id, now, "mastered")
        learningStateDao.refreshSessionProgress(sessionId, now)
    }

    suspend fun saveNote(word: Word, body: String) {
        val entity = wordDao.getById(word.id) ?: return
        val wordKey = entity.resolvedWordKey()
        val learningWordId = entity.resolvedLearningWordId()
        val now = System.currentTimeMillis()
        if (body.isBlank()) {
            noteDao.deleteByWordId(word.id)
            learningStateDao.deleteUserWordNote(DefaultUserId, wordKey)
        } else {
            val trimmed = body.trim()
            noteDao.upsert(NoteEntity(wordId = word.id, body = trimmed, updatedAt = now))
            learningStateDao.upsertUserWordNote(
                UserWordNoteEntity(
                    wordKey = wordKey,
                    word = entity.text,
                    body = trimmed,
                    firstWordbookId = entity.wordbookId,
                    firstLearningWordId = learningWordId,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun updateDailyNew(value: Int) = settingsStore.updateDailyNew(value)
    suspend fun updateReviewLimit(value: Int) = settingsStore.updateReviewLimit(value)
    suspend fun updateAutoPlayWord(value: Boolean) = settingsStore.updateAutoPlayWord(value)
    suspend fun updateAutoPlayExample(value: Boolean) = settingsStore.updateAutoPlayExample(value)
    suspend fun updateDailyMinutes(value: Int) = settingsStore.updateDailyMinutes(value)
    suspend fun updateSelectedWordbook(wordbookId: String) = settingsStore.updateSelectedWordbook(wordbookId)
    suspend fun updateWordOrder(value: StudyWordOrder) = settingsStore.updateWordOrder(value)
    suspend fun updateAccent(value: com.danchi.app.domain.Accent) = settingsStore.updateAccent(value)
    suspend fun updateSpeechRate(value: Float) = settingsStore.updateSpeechRate(value)
    suspend fun updateAutoPlayRepeatCount(value: Int) = settingsStore.updateAutoPlayRepeatCount(value)
    suspend fun updateMasteryConfirmMutedUntil(value: Long) = settingsStore.updateMasteryConfirmMutedUntil(value)

    private suspend fun withNote(entity: WordEntity): Word {
        return entity.toDomain(noteDao.getByWordId(entity.id)?.body)
    }

    private suspend fun buildFsrsSessionItems(
        sessionId: String,
        wordbookId: String,
        positionStart: Int,
        dueLimit: Int,
        newLimit: Int,
        maxItems: Int,
        wordOrder: StudyWordOrder,
        now: Long
    ): List<StudySessionItemEntity> {
        if (maxItems <= 0 || (dueLimit <= 0 && newLimit <= 0)) return emptyList()
        val dueCards = if (dueLimit <= 0) {
            emptyList()
        } else {
            val learning = cardDao.dueLearningCardsForSession(
                userId = DefaultUserId,
                wordbookId = wordbookId,
                now = now,
                sessionId = sessionId,
                limit = dueLimit
            )
            val remainingReviewLimit = (dueLimit - learning.size).coerceAtLeast(0)
            val review = if (remainingReviewLimit > 0) {
                cardDao.dueReviewCardsForSession(
                    userId = DefaultUserId,
                    wordbookId = wordbookId,
                    now = now,
                    sessionId = sessionId,
                    limit = remainingReviewLimit
                )
            } else {
                emptyList()
            }
            FsrsScheduler.getDueQueue((learning + review).map { it.toSchedulerCard() }, now)
                .take(dueLimit)
        }

        val remainingSlots = (maxItems - dueCards.size).coerceAtLeast(0)
        val newWords = if (newLimit <= 0 || remainingSlots <= 0) {
            emptyList()
        } else {
            nextNewWordsForSession(
                wordbookId = wordbookId,
                sessionId = sessionId,
                limit = minOf(newLimit, remainingSlots),
                order = wordOrder
            )
        }
        val newCards = newWords.mapNotNull { word -> createRecognitionCard(word.id, now)?.toSchedulerCard() }
        val cards = (dueCards + newCards)
            .distinctBy { it.id }
            .take(maxItems)
        if (cards.isEmpty()) return emptyList()

        val wordById = wordDao.getByIds(cards.map { it.wordId }.distinct()).associateBy { it.id }
        return cards.mapIndexedNotNull { index, card ->
            val wordEntity = wordById[card.wordId] ?: return@mapIndexedNotNull null
            val word = withNote(wordEntity)
            val options = recognitionOptionsFor(word, wordbookId)
            StudySessionItemEntity(
                sessionId = sessionId,
                position = positionStart + index,
                wordbookId = wordbookId,
                learningWordId = wordEntity.resolvedLearningWordId(),
                wordKey = wordEntity.resolvedWordKey(),
                wordId = wordEntity.id,
                cardId = card.id,
                questionType = FsrsCardType.Recognition.value,
                queueReason = queueReasonFor(card),
                optionsJson = encodeMeaningOptions(options),
                correctOptionId = options.firstOrNull { it.isCorrect }?.id.orEmpty(),
                cardStateBefore = card.state.value,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    private suspend fun nextNewWordsForSession(
        wordbookId: String,
        sessionId: String,
        limit: Int,
        order: StudyWordOrder
    ): List<WordEntity> {
        if (limit <= 0) return emptyList()
        return when (order) {
            StudyWordOrder.Random -> wordDao.randomNewWordsForSession(
                wordbookId = wordbookId,
                userId = DefaultUserId,
                cardType = FsrsCardType.Recognition.value,
                sessionId = sessionId,
                limit = limit
            )
            StudyWordOrder.Alphabetical -> wordDao.nextNewWordsForSession(
                wordbookId = wordbookId,
                userId = DefaultUserId,
                cardType = FsrsCardType.Recognition.value,
                sessionId = sessionId,
                limit = limit
            )
        }
    }

    private suspend fun recognitionOptionsFor(word: Word, wordbookId: String): List<MeaningChoiceOption> {
        val candidates = wordDao.optionCandidateWords(
            wordbookId = wordbookId,
            targetWordId = word.id,
            targetPos = word.pos,
            targetBook = word.book,
            targetUnit = word.unit,
            limit = OptionCandidateLimit
        ).map { it.toDomain() }
        return FsrsScheduler.recognitionOptions(word, candidates)
    }

    private fun queueReasonFor(card: SchedulerCard): String {
        return when (card.state) {
            com.danchi.app.scheduler.FsrsCardState.New -> "new"
            com.danchi.app.scheduler.FsrsCardState.Review -> "due_review"
            com.danchi.app.scheduler.FsrsCardState.Learning,
            com.danchi.app.scheduler.FsrsCardState.Relearning -> "learning_step_due"
        }
    }

    private suspend fun buildFsrsStudyItems(
        cards: List<SchedulerCard>,
        wordbookId: String
    ): List<FsrsStudyItem> {
        if (cards.isEmpty()) return emptyList()
        val words = wordDao.getByIds(cards.map { it.wordId }.distinct()).associate { it.id to withNote(it) }
        return cards.mapNotNull { card ->
            val word = words[card.wordId] ?: return@mapNotNull null
            FsrsStudyItem(
                cardId = card.id,
                word = word,
                options = recognitionOptionsFor(word, wordbookId),
                questionType = FsrsCardType.Recognition.value
            )
        }
    }

    private suspend fun buildFsrsSessionSnapshot(
        sessionId: String,
        isResumed: Boolean,
        settings: StudySettings,
        now: Long
    ): FsrsSessionSnapshot {
        val session = learningStateDao.studySessionById(sessionId)
        val rows = learningStateDao.studySessionItems(sessionId)
        if (session == null || rows.isEmpty()) {
            return FsrsSessionSnapshot(
                sessionId = sessionId,
                items = emptyList(),
                currentPosition = 0,
                totalCount = 0,
                completedCount = 0,
                isResumed = isResumed
            )
        }
        val wordById = wordDao.getByIds(rows.map { it.wordId }.distinct()).associateBy { it.id }
        val items = rows.mapNotNull { row ->
            val wordEntity = wordById[row.wordId] ?: return@mapNotNull null
            val word = withNote(wordEntity)
            val options = decodeMeaningOptions(row.optionsJson).ifEmpty {
                recognitionOptionsFor(word, settings.selectedWordbookId).also { generated ->
                    learningStateDao.updateSessionItemOptions(
                        sessionId = row.sessionId,
                        position = row.position,
                        optionsJson = encodeMeaningOptions(generated),
                        correctOptionId = generated.firstOrNull { it.isCorrect }?.id.orEmpty(),
                        updatedAt = now
                    )
                }
            }
            FsrsStudyItem(
                cardId = row.cardId,
                word = word,
                options = options,
                questionType = row.questionType.ifBlank { FsrsCardType.Recognition.value }
            )
        }
        val totalCount = session.totalCount.takeIf { it > 0 } ?: rows.size
        val completedCount = rows.count { it.status == "completed" }
        val currentPosition = session.currentPosition
            .coerceAtLeast(0)
            .coerceAtMost(totalCount)
        return FsrsSessionSnapshot(
            sessionId = sessionId,
            items = items,
            currentPosition = currentPosition,
            totalCount = totalCount,
            completedCount = completedCount,
            isResumed = isResumed,
            isBuilding = session.status == "building"
        )
    }

    private suspend fun appendDueLearningCardsToSession(
        session: StudySessionEntity,
        settings: StudySettings,
        now: Long
    ) {
        if (session.studyDayEpoch != todayEpoch(now)) return
        val rows = learningStateDao.studySessionItems(session.id)
        val activeCardIds = rows
            .filter { it.status != "completed" }
            .map { it.cardId }
            .toSet()
        val dueCards = cardDao
            .dueLearningCards(DefaultUserId, session.wordbookId, now, settings.reviewLimit.coerceAtLeast(1))
            .filter { it.id !in activeCardIds }
        if (dueCards.isEmpty()) return

        val startPosition = learningStateDao.maxStudySessionPosition(session.id) + 1
        val wordById = wordDao.getByIds(dueCards.map { it.wordId }.distinct()).associateBy { it.id }
        val appended = dueCards.mapIndexedNotNull { index, card ->
            val wordEntity = wordById[card.wordId] ?: return@mapIndexedNotNull null
            val word = withNote(wordEntity)
            val options = recognitionOptionsFor(word, session.wordbookId)
            StudySessionItemEntity(
                sessionId = session.id,
                position = startPosition + index,
                wordbookId = session.wordbookId,
                learningWordId = wordEntity.resolvedLearningWordId(),
                wordKey = wordEntity.resolvedWordKey(),
                wordId = wordEntity.id,
                cardId = card.id,
                questionType = FsrsCardType.Recognition.value,
                queueReason = "learning_step_due",
                optionsJson = encodeMeaningOptions(options),
                correctOptionId = options.firstOrNull { it.isCorrect }?.id.orEmpty(),
                cardStateBefore = card.state,
                createdAt = now,
                updatedAt = now
            )
        }
        if (appended.isEmpty()) return
        learningStateDao.upsertStudySession(
            session.copy(
                status = "active",
                totalCount = rows.size + appended.size,
                finishedAt = null,
                lastActiveAt = now,
                updatedAt = now
            )
        )
        learningStateDao.upsertStudySessionItems(appended)
    }

    private suspend fun createRecognitionCard(wordId: String, now: Long): CardEntity? {
        cardDao.getByWordAndType(DefaultUserId, wordId, FsrsCardType.Recognition.value)?.let { return it }
        val word = wordDao.getById(wordId) ?: return null
        val entity = FsrsScheduler.createCard(
            userId = DefaultUserId,
            wordId = wordId,
            cardType = FsrsCardType.Recognition,
            now = now
        ).toEntity().copy(
            id = 0L,
            wordKey = word.resolvedWordKey(),
            learningWordId = word.resolvedLearningWordId()
        )
        val id = cardDao.insert(entity)
        return if (id > 0L) {
            entity.copy(id = id)
        } else {
            cardDao.getByWordAndType(DefaultUserId, wordId, FsrsCardType.Recognition.value)
        }
    }

    private suspend fun loadFsrsSettings(studySettings: StudySettings): com.danchi.app.scheduler.SchedulerSettings {
        val existing = userFsrsSettingDao.get(DefaultUserId) ?: UserFsrsSettingEntity()
        val merged = existing.copy(
            dailyMinutes = studySettings.dailyMinutes,
            maxNewCardsPerDay = studySettings.maxNewCardsPerDay.coerceAtLeast(0),
            updatedAt = System.currentTimeMillis()
        )
        if (merged != existing) {
            userFsrsSettingDao.upsert(merged)
        }
        return merged.toSchedulerSettings()
    }

    private suspend fun syncLearningMirror() {
        val now = System.currentTimeMillis()
        val wordbooks = wordbookDao.all()
        val wordsBeforeNormalize = wordDao.allWords()
        val masteredKeys = learningStateDao.masteredWordKeys(DefaultUserId).toMutableSet()

        wordsBeforeNormalize
            .filter { it.status == WordStatus.Mastered.name }
            .forEach { word ->
                val key = word.resolvedWordKey()
                masteredKeys += key
                learningStateDao.upsertMasteredWord(word.toMasteredEntity(now, "word_status"))
            }

        val words = wordsBeforeNormalize.map { word ->
            val normalizedKey = word.resolvedWordKey()
            val normalizedLearningWordId = word.resolvedLearningWordId()
            val normalized = if (word.wordKey != normalizedKey || word.learningWordId != normalizedLearningWordId) {
                word.copy(wordKey = normalizedKey, learningWordId = normalizedLearningWordId)
            } else {
                word
            }
            val shouldSkipAsMastered = normalized.status == WordStatus.New.name && masteredKeys.contains(normalizedKey)
            val updated = if (shouldSkipAsMastered) {
                normalized.copy(
                    status = WordStatus.Mastered.name,
                    dueAt = Long.MAX_VALUE,
                    learnedAt = normalized.learnedAt ?: now,
                    updatedAt = now
                )
            } else {
                normalized
            }
            if (updated != word) {
                wordDao.update(updated)
            }
            updated
        }

        if (wordbooks.isNotEmpty()) {
            learningStateDao.upsertLocalWordbooks(wordbooks.map { it.toLocalWordbookEntity(now) })
        }
        if (words.isNotEmpty()) {
            learningStateDao.upsertLocalWordbookWords(
                words.groupBy { it.wordbookId }.flatMap { (_, rows) ->
                    rows.sortedBy { it.resolvedWordKey() }.mapIndexed { index, word ->
                        word.toLocalWordbookWordEntity(index, now)
                    }
                }
            )
            learningStateDao.upsertLearningWords(words.map { it.toLearningWordEntity(now) })
        }

        wordbooks.forEach { wordbook ->
            val planId = LearningKeys.planId(DefaultUserId, wordbook.id)
            learningStateDao.upsertStudyPlan(
                StudyPlanEntity(
                    id = planId,
                    wordbookId = wordbook.id,
                    title = wordbook.title,
                    createdAt = wordbook.updatedAt.takeIf { it > 0L } ?: now,
                    updatedAt = now
                )
            )
            val planItems = words
                .filter { it.wordbookId == wordbook.id }
                .sortedBy { it.resolvedWordKey() }
                .mapIndexed { index, word ->
                    word.toStudyPlanItemEntity(planId, index, now)
                }
            if (planItems.isNotEmpty()) {
                learningStateDao.upsertStudyPlanItems(planItems)
            }
        }
    }

    private suspend fun syncWordState(card: SchedulerCard) {
        val entity = wordDao.getById(card.wordId) ?: return
        val now = System.currentTimeMillis()
        val nextStatus = when (card.state) {
            com.danchi.app.scheduler.FsrsCardState.New -> WordStatus.New
            com.danchi.app.scheduler.FsrsCardState.Learning,
            com.danchi.app.scheduler.FsrsCardState.Relearning -> WordStatus.Learning
            com.danchi.app.scheduler.FsrsCardState.Review -> WordStatus.Review
        }
        val nextEntity = entity.copy(
            status = nextStatus.name,
            dueAt = card.dueAt,
            learnedAt = entity.learnedAt ?: card.lastReviewAt,
            reviewCount = card.reps,
            lapseCount = card.lapses,
            stability = card.stability ?: entity.stability,
            difficulty = card.difficulty ?: entity.difficulty,
            updatedAt = now
        )
        wordDao.update(nextEntity)
        syncLearningWordState(nextEntity)
    }

    private suspend fun syncLearningWordState(word: WordEntity) {
        val now = System.currentTimeMillis()
        learningStateDao.upsertLearningWords(listOf(word.toLearningWordEntity(now)))
        val status = when (word.status) {
            WordStatus.New.name -> "pending"
            WordStatus.Mastered.name -> "completed"
            else -> "active"
        }
        learningStateDao.updatePlanItemStatus(
            userId = DefaultUserId,
            learningWordId = word.resolvedLearningWordId(),
            status = status,
            startedAt = if (status != "pending") word.learnedAt ?: now else null,
            completedAt = if (status == "completed") word.learnedAt ?: now else null,
            skippedAt = null,
            updatedAt = now
        )
        if (word.status == WordStatus.Mastered.name) {
            learningStateDao.upsertMasteredWord(word.toMasteredEntity(now, "word_status"))
        }
    }

    private suspend fun syncMasteredWord(word: WordEntity, now: Long) {
        val wordKey = word.resolvedWordKey()
        learningStateDao.upsertMasteredWord(word.toMasteredEntity(now, "manual"))
        val copies = wordDao.getByWordKey(wordKey)
        copies.forEach { copy ->
            val mastered = copy.copy(
                status = WordStatus.Mastered.name,
                dueAt = Long.MAX_VALUE,
                learnedAt = copy.learnedAt ?: now,
                updatedAt = now
            )
            if (mastered != copy) {
                wordDao.update(mastered)
            }
            syncLearningWordState(mastered)
            learningStateDao.updatePlanItemStatus(
                userId = DefaultUserId,
                learningWordId = mastered.resolvedLearningWordId(),
                status = if (mastered.id == word.id) "completed" else "skipped",
                startedAt = mastered.learnedAt ?: now,
                completedAt = if (mastered.id == word.id) mastered.learnedAt ?: now else null,
                skippedAt = if (mastered.id == word.id) null else now,
                updatedAt = now
            )
        }
        learningStateDao.upsertMasteredWord(word.toMasteredEntity(now, "manual"))
    }

    private fun todayEpoch(now: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.of("Asia/Shanghai")
        return Instant.ofEpochMilli(now)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun todaySessionId(wordbookId: String, studyDayEpoch: Long): String {
        return LearningKeys.sessionId(
            userId = DefaultUserId,
            wordbookId = wordbookId,
            studyDayEpoch = studyDayEpoch,
            mode = LearningKeys.TodayFsrsSessionMode
        )
    }

    private fun WordEntity.resolvedWordKey(): String {
        return wordKey.ifBlank { LearningKeys.wordKey(text) }
    }

    private fun WordEntity.resolvedLearningWordId(): String {
        return learningWordId.ifBlank { LearningKeys.learningWordId(wordbookId, resolvedWordKey()) }
    }

    private fun WordbookEntity.toLocalWordbookEntity(now: Long): LocalWordbookEntity {
        val timestamp = updatedAt.takeIf { it > 0L } ?: now
        return LocalWordbookEntity(
            id = id,
            title = title,
            description = description,
            sourceFile = sourceFile,
            assetPath = assetPath,
            version = version,
            wordCount = wordCount,
            sortOrder = sortOrder,
            downloadedAt = timestamp,
            activatedAt = timestamp,
            updatedAt = now
        )
    }

    private fun WordEntity.toLocalWordbookWordEntity(position: Int, now: Long): LocalWordbookWordEntity {
        return LocalWordbookWordEntity(
            wordbookId = wordbookId,
            wordKey = resolvedWordKey(),
            word = text,
            sortOrder = position,
            book = book,
            unit = unit,
            level = level,
            source = source,
            createdAt = createdAt.takeIf { it > 0L } ?: now,
            updatedAt = updatedAt.takeIf { it > 0L } ?: now
        )
    }

    private fun WordEntity.toLearningWordEntity(now: Long): LearningWordEntity {
        return LearningWordEntity(
            id = resolvedLearningWordId(),
            wordbookId = wordbookId,
            wordKey = resolvedWordKey(),
            wordId = id,
            text = text,
            meaning = meaning,
            pos = pos,
            meaningsJson = meaningsJson,
            phonetic = phonetic,
            example = example,
            exampleCn = exampleCn,
            root = root,
            synonyms = synonyms,
            collocations = collocations,
            memoryTip = memoryTip,
            book = book,
            unit = unit,
            level = level,
            source = source,
            status = status,
            dueAt = dueAt,
            learnedAt = learnedAt,
            reviewCount = reviewCount,
            lapseCount = lapseCount,
            stability = stability,
            difficulty = difficulty,
            createdAt = createdAt.takeIf { it > 0L } ?: now,
            updatedAt = updatedAt.takeIf { it > 0L } ?: now
        )
    }

    private fun WordEntity.toStudyPlanItemEntity(planId: String, position: Int, now: Long): StudyPlanItemEntity {
        val itemStatus = when (status) {
            WordStatus.New.name -> "pending"
            WordStatus.Mastered.name -> "completed"
            else -> "active"
        }
        return StudyPlanItemEntity(
            planId = planId,
            learningWordId = resolvedLearningWordId(),
            wordbookId = wordbookId,
            wordKey = resolvedWordKey(),
            wordId = id,
            position = position,
            status = itemStatus,
            assignedAt = createdAt.takeIf { it > 0L } ?: now,
            startedAt = if (itemStatus != "pending") learnedAt ?: updatedAt.takeIf { it > 0L } ?: now else null,
            completedAt = if (itemStatus == "completed") learnedAt ?: updatedAt.takeIf { it > 0L } ?: now else null,
            updatedAt = updatedAt.takeIf { it > 0L } ?: now
        )
    }

    private fun WordEntity.toMasteredEntity(now: Long, source: String): UserMasteredWordEntity {
        return UserMasteredWordEntity(
            wordKey = resolvedWordKey(),
            word = text,
            firstWordbookId = wordbookId,
            firstLearningWordId = resolvedLearningWordId(),
            masteredAt = learnedAt ?: now,
            source = source,
            updatedAt = now
        )
    }

    private fun encodeMeaningOptions(options: List<MeaningChoiceOption>): String {
        return JSONArray().also { array ->
            options.forEach { option ->
                array.put(
                    JSONObject()
                        .put("id", option.id)
                        .put("wordId", option.wordId)
                        .put("meaningId", option.meaningId)
                        .put("pos", option.pos)
                        .put("posName", option.posName)
                        .put("meaning", option.meaning)
                        .put("isCorrect", option.isCorrect)
                )
            }
        }.toString()
    }

    private fun decodeMeaningOptions(json: String): List<MeaningChoiceOption> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        MeaningChoiceOption(
                            id = item.optString("id"),
                            wordId = item.optString("wordId"),
                            meaningId = item.optString("meaningId"),
                            pos = item.optString("pos"),
                            posName = item.optString("posName"),
                            meaning = item.optString("meaning"),
                            isCorrect = item.optBoolean("isCorrect")
                        )
                    )
                }
            }.filter { it.id.isNotBlank() && it.wordId.isNotBlank() && it.meaning.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun fsrsSettingsFingerprint(settings: StudySettings): String {
        return listOf(
            settings.selectedWordbookId,
            settings.dailyNewWords,
            settings.reviewLimit,
            settings.wordOrder.name,
            settings.dailyMinutes,
            settings.maxNewCardsPerDay
        ).joinToString("|")
    }

    private fun buildAnswerSnapshot(selectedWordId: String, correctWordId: String, options: List<String>): String {
        return "selected=$selectedWordId;correct=$correctWordId;options=${options.joinToString("|")}"
    }

    private fun Long.toLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
        return Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
    }

    private fun currentStreakDays(days: Set<LocalDate>, today: LocalDate = LocalDate.now()): Int {
        if (days.isEmpty()) return 0
        val latest = days.maxOrNull() ?: return 0
        if (latest != today && latest != today.minusDays(1)) return 0
        var cursor = latest
        var streak = 0
        while (days.contains(cursor)) {
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
