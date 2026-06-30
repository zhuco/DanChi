package com.danchi.app.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.danchi.app.audio.AnswerFeedbackSound
import com.danchi.app.audio.DanChiTts
import com.danchi.app.data.DanChiRepository
import com.danchi.app.domain.Accent
import com.danchi.app.domain.FirmCardKind
import com.danchi.app.domain.FirmStudyCard
import com.danchi.app.domain.FirmTodaySummary
import com.danchi.app.domain.FsrsBuildProgress
import com.danchi.app.domain.FsrsStudyItem
import com.danchi.app.domain.LibraryStats
import com.danchi.app.domain.ReviewGrade
import com.danchi.app.domain.StudyProfileStats
import com.danchi.app.domain.StudySettings
import com.danchi.app.domain.StudyWordOrder
import com.danchi.app.domain.TodayPlan
import com.danchi.app.domain.Word
import com.danchi.app.domain.WordbookProgress
import com.danchi.app.domain.WordStudyRecord
import com.danchi.app.domain.displayMeanings
import com.danchi.app.domain.meaningChoiceOptionId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class UiState(
    val seededCount: Int = 0,
    val activeTab: MainTab = MainTab.Home,
    val sessionMode: SessionMode = SessionMode.NewWords,
    val session: List<Word> = emptyList(),
    val fsrsSession: List<FsrsStudyItem> = emptyList(),
    val fsrsSessionId: String = "",
    val fsrsTotalCount: Int = 0,
    val fsrsCompletedCount: Int = 0,
    val fsrsTailBuilding: Boolean = false,
    val fsrsTailBuildTitle: String = "",
    val currentIndex: Int = 0,
    val query: String = "",
    val spellingInput: String = "",
    val spellingResult: Boolean? = null,
    val firmCard: FirmStudyCard = FirmStudyCard(FirmCardKind.Done),
    val firmSummary: FirmTodaySummary = FirmTodaySummary(),
    val firmCardIndex: Int = 0,
    val firmSelectedOptionId: String? = null,
    val firmCorrectOptionId: String? = null,
    val firmChoiceCorrect: Boolean? = null,
    val firmForgetDetailWord: Word? = null,
    val firmDetailRecord: WordStudyRecord? = null,
    val fsrsAnswerStartedAt: Long = 0L,
    val fsrsAnswered: Boolean = false,
    val fsrsSelectedOptionId: String? = null,
    val fsrsCorrectOptionId: String? = null,
    val fsrsAnswerCorrect: Boolean? = null,
    val fsrsInfoVisible: Boolean = false,
    val noteDraft: String = "",
    val busy: Boolean = false,
    val loadingTitle: String = "",
    val loadingDetail: String = "",
    val loadingProgress: Float = 0f,
    val message: String? = null
) {
    val currentFsrsItem: FsrsStudyItem? get() = fsrsSession.getOrNull(currentIndex)

    val currentWord: Word? get() = if (sessionMode == SessionMode.Firm) {
        firmForgetDetailWord ?: firmCard.word ?: firmCard.previewWords.firstOrNull()
    } else if (sessionMode == SessionMode.Today) {
        currentFsrsItem?.word
    } else {
        session.getOrNull(currentIndex)
    }
}

enum class MainTab(val label: String) {
    Home("首页"),
    Study("学习"),
    Dictionary("词典"),
    Stats("统计"),
    Me("我的")
}

enum class SessionMode(val label: String) {
    Today("今日学习"),
    Firm("牢记模式"),
    NewWords("新学"),
    Review("复习"),
    Spelling("拼写"),
    Meaning("选义"),
    Dictation("听写")
}

data class DanChiScreenState(
    val ui: UiState = UiState(),
    val stats: LibraryStats = LibraryStats(),
    val profileStats: StudyProfileStats = StudyProfileStats(),
    val plan: TodayPlan = TodayPlan(0, 0, 0, 0),
    val settings: StudySettings = StudySettings(),
    val dictionary: List<Word> = emptyList(),
    val wordbooks: List<WordbookProgress> = emptyList()
) {
    val selectedWordbook: WordbookProgress?
        get() = wordbooks.firstOrNull { it.wordbook.id == settings.selectedWordbookId }
}

private data class CatalogState(
    val dictionary: List<Word>,
    val wordbooks: List<WordbookProgress>
)

class DanChiViewModel(
    private val repository: DanChiRepository,
    application: Application
) : ViewModel() {
    private val uiState = MutableStateFlow(UiState())
    private val tts = DanChiTts(application)
    private val answerSound = AnswerFeedbackSound()

    private val dictionary = uiState
        .flatMapLatest { repository.observeDictionary(it.query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val catalog = combine(
        dictionary,
        repository.observeWordbookProgress()
    ) { words, wordbooks ->
        CatalogState(words, wordbooks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogState(emptyList(), emptyList()))

    private val baseScreenState = combine(
        uiState,
        repository.observeStats(),
        repository.observeStudyProfileStats(),
        repository.observeTodayPlan(),
        repository.settings
    ) { ui, stats, profileStats, plan, settings ->
        DanChiScreenState(ui, stats, profileStats, plan, settings)
    }

    val screenState: StateFlow<DanChiScreenState> = combine(
        baseScreenState,
        catalog
    ) { state, catalog ->
        state.copy(dictionary = catalog.dictionary, wordbooks = catalog.wordbooks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DanChiScreenState())

    init {
        viewModelScope.launch {
            val inserted = repository.seedWordbook()
            uiState.update {
                it.copy(
                    seededCount = inserted,
                    message = if (inserted > 0) "已导入 $inserted 个词条" else null
                )
            }
        }
    }

    fun selectTab(tab: MainTab) {
        uiState.update { it.copy(activeTab = tab, message = null) }
    }

    fun updateQuery(query: String) {
        uiState.update { it.copy(query = query) }
    }

    fun startSession(mode: SessionMode) {
        viewModelScope.launch {
            uiState.update {
                it.copy(
                    busy = true,
                    sessionMode = mode,
                    activeTab = MainTab.Study,
                    currentIndex = 0,
                    firmCardIndex = 0,
                    firmSelectedOptionId = null,
                    firmCorrectOptionId = null,
                    firmChoiceCorrect = null,
                    firmForgetDetailWord = null,
                    firmDetailRecord = null,
                    fsrsSession = emptyList(),
                    fsrsSessionId = "",
                    fsrsTotalCount = 0,
                    fsrsCompletedCount = 0,
                    fsrsTailBuilding = false,
                    fsrsTailBuildTitle = "",
                    fsrsAnswerStartedAt = System.currentTimeMillis(),
                    fsrsAnswered = false,
                    fsrsSelectedOptionId = null,
                    fsrsCorrectOptionId = null,
                    fsrsAnswerCorrect = null,
                    fsrsInfoVisible = false,
                    spellingInput = "",
                    spellingResult = null,
                    loadingTitle = if (mode == SessionMode.Today) "正在准备今日提分" else "正在准备学习队列",
                    loadingDetail = if (mode == SessionMode.Today) {
                        "正在检查是否已有今日清单。"
                    } else {
                        "正在读取当前模式的学习内容。"
                    },
                    loadingProgress = 0.05f,
                    message = null
                )
            }
            val settings = screenState.value.settings
            if (mode == SessionMode.Today) {
                val snapshot = withContext(Dispatchers.Default) {
                    repository.buildTodayFsrsSession(settings) { progress ->
                        updateLoadingProgress(progress)
                    }
                }
                uiState.update {
                    it.copy(
                        busy = false,
                        loadingTitle = "",
                        loadingDetail = "",
                        loadingProgress = 0f,
                        session = snapshot.items.map { item -> item.word },
                        fsrsSession = snapshot.items,
                        fsrsSessionId = snapshot.sessionId,
                        fsrsTotalCount = snapshot.totalCount,
                        fsrsCompletedCount = snapshot.completedCount,
                        fsrsTailBuilding = snapshot.isBuilding,
                        fsrsTailBuildTitle = if (snapshot.isBuilding) "正在准备后续题目" else "",
                        currentIndex = snapshot.currentPosition,
                        fsrsAnswerStartedAt = System.currentTimeMillis(),
                        noteDraft = snapshot.items.getOrNull(snapshot.currentPosition)?.word?.note.orEmpty(),
                        message = if (snapshot.items.isEmpty() || snapshot.currentPosition >= snapshot.totalCount) "今日没有待学习内容" else if (snapshot.isResumed) "继续今日提分" else null
                    )
                }
                snapshot.items.getOrNull(snapshot.currentPosition)?.word?.let {
                    if (settings.autoPlayWord) autoSpeak(it)
                }
                if (snapshot.isBuilding) {
                    continueTodayFsrsBuild(settings, snapshot.sessionId)
                }
                return@launch
            }
            if (mode == SessionMode.Firm) {
                val resumeIndex = repository.resumeFirmCardIndex(settings)
                val card = repository.buildFirmCard(settings, resumeIndex)
                val summary = repository.firmTodaySummary(settings)
                uiState.update {
                    it.copy(
                        busy = false,
                        loadingTitle = "",
                        loadingDetail = "",
                        loadingProgress = 0f,
                        session = emptyList(),
                        firmCard = card,
                        firmSummary = summary,
                        firmCardIndex = resumeIndex,
                        noteDraft = card.word?.note.orEmpty(),
                        message = if (card.kind == FirmCardKind.Done) "今日已完成" else null
                    )
                }
                card.word?.let { if (settings.autoPlayWord) autoSpeak(it) }
                return@launch
            }
            val words = when (mode) {
                SessionMode.Today -> emptyList()
                SessionMode.Firm -> emptyList()
                SessionMode.NewWords -> repository.buildNewSession(settings)
                SessionMode.Review -> repository.buildReviewSession(settings.selectedWordbookId, settings.reviewLimit)
                SessionMode.Spelling -> repository.buildReviewSession(settings.selectedWordbookId, settings.reviewLimit).ifEmpty {
                    repository.buildNewSession(settings)
                }
                SessionMode.Meaning -> repository.buildReviewSession(settings.selectedWordbookId, settings.reviewLimit).ifEmpty {
                    repository.buildNewSession(settings)
                }
                SessionMode.Dictation -> repository.buildReviewSession(settings.selectedWordbookId, settings.reviewLimit).ifEmpty {
                    repository.buildNewSession(settings)
                }
            }
            uiState.update {
                it.copy(
                    busy = false,
                    loadingTitle = "",
                    loadingDetail = "",
                    loadingProgress = 0f,
                    session = words,
                    noteDraft = words.firstOrNull()?.note.orEmpty(),
                    message = if (words.isEmpty()) "当前没有可学习词条" else null
                )
            }
            if (words.isNotEmpty() && settings.autoPlayWord) autoSpeak(words.first())
        }
    }

    private fun updateLoadingProgress(progress: FsrsBuildProgress) {
        uiState.update {
            it.copy(
                loadingTitle = progress.title,
                loadingDetail = progress.detail,
                loadingProgress = progress.fraction
            )
        }
    }

    fun gradeCurrent(grade: ReviewGrade) {
        val word = uiState.value.currentWord ?: return
        viewModelScope.launch {
            repository.grade(word, grade)
            moveNext()
        }
    }

    fun answerMeaningPractice(correct: Boolean) {
        playAnswerFeedback(correct)
        gradeCurrent(if (correct) ReviewGrade.Good else ReviewGrade.Again)
    }

    fun answerCurrentRecognition(optionId: String) {
        answerCurrentRecognition(optionId = optionId, selectedOptionId = optionId)
    }

    fun revealCurrentRecognitionAnswer() {
        val state = uiState.value
        val item = state.currentFsrsItem ?: return
        if (state.fsrsAnswered) return
        val correctOptionId = item.options.firstOrNull { it.wordId == item.word.id }?.id ?: item.word.id
        viewModelScope.launch {
            repository.revealTodayRecognitionAnswer(item.cardId, correctOptionId)
            uiState.update {
                it.copy(
                    fsrsAnswered = true,
                    fsrsSelectedOptionId = null,
                    fsrsCorrectOptionId = correctOptionId,
                    fsrsAnswerCorrect = null,
                    fsrsInfoVisible = false,
                    noteDraft = item.word.note.orEmpty()
                )
            }
        }
    }

    private fun answerCurrentRecognition(optionId: String, selectedOptionId: String?) {
        val state = uiState.value
        val item = state.currentFsrsItem ?: return
        if (state.fsrsAnswered) return
        viewModelScope.launch {
            val duration = (System.currentTimeMillis() - state.fsrsAnswerStartedAt).coerceAtLeast(0L)
            val selectedWordId = selectedOptionId
                ?.let { id -> item.options.firstOrNull { it.id == id }?.wordId }
                ?: optionId
            val correctOptionId = item.options.firstOrNull { it.wordId == item.word.id }?.id ?: item.word.id
            val correct = repository.answerRecognition(
                cardId = item.cardId,
                selectedWordId = selectedWordId,
                options = item.options.map { it.wordId },
                durationMs = duration,
                studySettings = screenState.value.settings,
                selectedOptionId = optionId
            )
            if (selectedOptionId != null) {
                playAnswerFeedback(correct)
            }
            uiState.update {
                it.copy(
                    fsrsAnswered = true,
                    fsrsSelectedOptionId = selectedOptionId,
                    fsrsCorrectOptionId = correctOptionId,
                    fsrsAnswerCorrect = correct,
                    fsrsInfoVisible = false,
                    fsrsCompletedCount = (it.fsrsCompletedCount + 1).coerceAtMost(it.fsrsTotalCount),
                    noteDraft = item.word.note.orEmpty()
                )
            }
        }
    }

    fun continueAfterFsrsAnswer() {
        val state = uiState.value
        if (state.sessionMode == SessionMode.Today && state.fsrsAnswered && !state.fsrsInfoVisible) {
            if (state.fsrsAnswerCorrect == null) {
                val item = state.currentFsrsItem ?: return
                viewModelScope.launch {
                    val duration = (System.currentTimeMillis() - state.fsrsAnswerStartedAt).coerceAtLeast(0L)
                    val correct = repository.answerRecognition(
                        cardId = item.cardId,
                        selectedWordId = "",
                        options = item.options.map { it.wordId },
                        durationMs = duration,
                        studySettings = screenState.value.settings,
                        selectedOptionId = "",
                        usedHint = true
                    )
                    uiState.update {
                        it.copy(
                            fsrsAnswerCorrect = correct,
                            fsrsInfoVisible = true,
                            fsrsCompletedCount = (it.fsrsCompletedCount + 1).coerceAtMost(it.fsrsTotalCount)
                        )
                    }
                    state.currentWord?.let { word ->
                        if (screenState.value.settings.autoPlayWord) autoSpeak(word)
                    }
                }
                return
            }
            uiState.update { it.copy(fsrsInfoVisible = true) }
            state.currentWord?.let { word ->
                if (screenState.value.settings.autoPlayWord) autoSpeak(word)
            }
        } else {
            moveNext()
        }
    }

    fun completeFirmPreview() {
        val card = uiState.value.firmCard
        viewModelScope.launch {
            repository.completeFirmPreview(card.previewRecords.map { it.wordId })
            advanceFirmCard()
        }
    }

    fun selectFirmChoice(optionId: String) {
        val state = uiState.value
        val word = state.firmCard.word ?: return
        if (state.firmSelectedOptionId != null) return
        val correctMeaningId = word.displayMeanings.firstOrNull()?.id ?: word.id
        val correctOptionId = meaningChoiceOptionId(word.id, correctMeaningId)
        viewModelScope.launch {
            val correct = optionId == correctOptionId
            repository.selectFirmChoice(word.id, correct)
            playAnswerFeedback(correct)
            uiState.update {
                it.copy(
                    firmSelectedOptionId = optionId,
                    firmCorrectOptionId = correctOptionId,
                    firmChoiceCorrect = correct
                )
            }
        }
    }

    fun continueAfterFirmChoice() {
        val word = uiState.value.firmCard.word ?: return
        viewModelScope.launch {
            repository.moveFirmChoiceToDetail(word.id)
            advanceFirmCard()
        }
    }

    fun firmDetailNext() {
        val state = uiState.value
        val word = state.firmForgetDetailWord ?: state.firmCard.word ?: return
        viewModelScope.launch {
            if (state.firmForgetDetailWord == null) {
                repository.moveFirmDetailToLearning(word.id, state.firmCardIndex)
            }
            advanceFirmCard()
        }
    }

    fun firmRemember() {
        val word = uiState.value.firmCard.word ?: return
        viewModelScope.launch {
            val record = repository.firmRemember(word.id, uiState.value.firmCardIndex)
            uiState.update { it.copy(firmForgetDetailWord = word, firmDetailRecord = record) }
            if (screenState.value.settings.autoPlayWord) autoSpeak(word)
        }
    }

    fun firmForget() {
        val word = uiState.value.firmCard.word ?: return
        viewModelScope.launch {
            val record = repository.firmForget(word.id, uiState.value.firmCardIndex)
            uiState.update { it.copy(firmForgetDetailWord = word, firmDetailRecord = record) }
            if (screenState.value.settings.autoPlayWord) autoSpeak(word)
        }
    }

    fun submitSpelling() {
        val state = uiState.value
        val word = state.currentWord ?: return
        viewModelScope.launch {
            val correct = repository.submitSpelling(word, state.spellingInput)
            uiState.update { it.copy(spellingResult = correct) }
        }
    }

    fun updateSpellingInput(value: String) {
        uiState.update { it.copy(spellingInput = value, spellingResult = null) }
    }

    fun moveNext() {
        val current = uiState.value
        val nextIndex = current.currentIndex + 1
        val nextWord = current.session.getOrNull(nextIndex)
        uiState.update {
            it.copy(
                currentIndex = nextIndex.coerceAtMost(current.session.size),
                spellingInput = "",
                spellingResult = null,
                fsrsAnswerStartedAt = System.currentTimeMillis(),
                fsrsAnswered = false,
                fsrsSelectedOptionId = null,
                fsrsCorrectOptionId = null,
                fsrsAnswerCorrect = null,
                fsrsInfoVisible = false,
                noteDraft = nextWord?.note.orEmpty(),
                message = if (nextWord == null) "学习已完成" else null
            )
        }
        if (nextWord != null && screenState.value.settings.autoPlayWord) autoSpeak(nextWord)
    }

    fun speak(word: Word? = uiState.value.currentWord, example: Boolean = false, repeatCount: Int = 1) {
        val settings = screenState.value.settings
        val text = if (example) word?.example else word?.text
        tts.speak(text.orEmpty(), settings.accent, settings.speechRate, repeatCount)
    }

    private fun autoSpeak(word: Word) {
        speak(word, repeatCount = screenState.value.settings.autoPlayRepeatCount)
    }

    private fun playAnswerFeedback(correct: Boolean) {
        if (correct) answerSound.playCorrect() else answerSound.playWrong()
    }

    fun toggleFavorite(word: Word) {
        viewModelScope.launch {
            repository.setFavorite(word, !word.isFavorite)
            uiState.update { it.copy(message = if (word.isFavorite) "已取消收藏" else "已加入生词本") }
        }
    }

    fun markCurrentMastered(muteConfirmToday: Boolean = false) {
        val state = uiState.value
        val word = state.currentWord ?: return
        viewModelScope.launch {
            if (muteConfirmToday) {
                repository.updateMasteryConfirmMutedUntil(endOfBeijingDayMillis())
            }
            repository.markMastered(word)
            if (state.sessionMode == SessionMode.Firm) {
                advanceFirmCard()
            } else {
                removeCurrentWordFromSession()
            }
            uiState.update { it.copy(message = "已标记熟练") }
        }
    }

    fun updateNoteDraft(value: String) {
        uiState.update { it.copy(noteDraft = value) }
    }

    fun saveCurrentNote() {
        val word = uiState.value.currentWord ?: return
        viewModelScope.launch {
            repository.saveNote(word, uiState.value.noteDraft)
            uiState.update { it.copy(message = "笔记已保存") }
        }
    }

    fun updateDailyNew(value: Int) = viewModelScope.launch { repository.updateDailyNew(value) }
    fun updateReviewLimit(value: Int) = viewModelScope.launch { repository.updateReviewLimit(value) }
    fun updateAutoPlayWord(value: Boolean) = viewModelScope.launch { repository.updateAutoPlayWord(value) }
    fun updateAutoPlayExample(value: Boolean) = viewModelScope.launch { repository.updateAutoPlayExample(value) }
    fun updateEnableNewWordPreview(value: Boolean) = viewModelScope.launch { repository.updateEnableNewWordPreview(value) }
    fun updateDailyMinutes(value: Int) = viewModelScope.launch { repository.updateDailyMinutes(value) }
    fun updateWordOrder(value: StudyWordOrder) = viewModelScope.launch { repository.updateWordOrder(value) }
    fun updateSelectedWordbook(wordbookId: String) = viewModelScope.launch {
        repository.updateSelectedWordbook(wordbookId)
        uiState.update {
            it.copy(
                session = emptyList(),
                fsrsSession = emptyList(),
                fsrsSessionId = "",
                fsrsTotalCount = 0,
                fsrsCompletedCount = 0,
                fsrsTailBuilding = false,
                fsrsTailBuildTitle = "",
                currentIndex = 0,
                firmCard = FirmStudyCard(FirmCardKind.Done),
                firmSummary = FirmTodaySummary(),
                firmCardIndex = 0,
                query = "",
                noteDraft = "",
                message = "已切换词库"
            )
        }
    }

    private fun continueTodayFsrsBuild(settings: StudySettings, sessionId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val snapshot = repository.completeTodayFsrsSession(settings) { progress ->
                uiState.update {
                    if (it.fsrsSessionId == sessionId) {
                        it.copy(fsrsTailBuildTitle = progress.title, fsrsTailBuilding = true)
                    } else {
                        it
                    }
                }
            }
            uiState.update {
                if (it.fsrsSessionId == sessionId) {
                    val nextIndex = it.currentIndex.coerceAtMost(snapshot.items.size)
                    val nextNote = snapshot.items.getOrNull(nextIndex)?.word?.note
                    it.copy(
                        session = snapshot.items.map { item -> item.word },
                        fsrsSession = snapshot.items,
                        fsrsTotalCount = snapshot.totalCount,
                        fsrsCompletedCount = snapshot.completedCount,
                        fsrsTailBuilding = snapshot.isBuilding,
                        fsrsTailBuildTitle = if (snapshot.isBuilding) it.fsrsTailBuildTitle else "",
                        currentIndex = nextIndex,
                        noteDraft = nextNote ?: it.noteDraft
                    )
                } else {
                    it
                }
            }
        }
    }
    fun updateAccent(value: Accent) = viewModelScope.launch { repository.updateAccent(value) }
    fun updateSpeechRate(value: Float) = viewModelScope.launch { repository.updateSpeechRate(value) }
    fun updateAutoPlayRepeatCount(value: Int) = viewModelScope.launch { repository.updateAutoPlayRepeatCount(value) }
    fun toggleAccent() {
        val next = if (screenState.value.settings.accent == Accent.Us) Accent.Uk else Accent.Us
        updateAccent(next)
    }

    private fun removeCurrentWordFromSession() {
        val current = uiState.value
        val nextSession = current.session.filterIndexed { index, _ -> index != current.currentIndex }
        val nextFsrsSession = current.fsrsSession.filterIndexed { index, _ -> index != current.currentIndex }
        val hasFsrsSession = current.fsrsSession.isNotEmpty()
        val nextCount = if (hasFsrsSession) nextFsrsSession.size else nextSession.size
        val nextIndex = current.currentIndex.coerceAtMost((nextCount - 1).coerceAtLeast(0))
        val nextWord = if (hasFsrsSession) {
            nextFsrsSession.getOrNull(nextIndex)?.word
        } else {
            nextSession.getOrNull(nextIndex)
        }
        uiState.update {
            it.copy(
                session = nextSession,
                fsrsSession = nextFsrsSession,
                currentIndex = if (nextCount == 0) 0 else nextIndex,
                spellingInput = "",
                spellingResult = null,
                fsrsAnswerStartedAt = System.currentTimeMillis(),
                fsrsAnswered = false,
                fsrsSelectedOptionId = null,
                fsrsCorrectOptionId = null,
                fsrsAnswerCorrect = null,
                fsrsInfoVisible = false,
                noteDraft = nextWord?.note.orEmpty(),
                message = if (nextWord == null) "学习已完成" else it.message
            )
        }
        if (nextWord != null && screenState.value.settings.autoPlayWord) autoSpeak(nextWord)
    }

    private fun endOfBeijingDayMillis(): Long {
        val zone = ZoneId.of("Asia/Shanghai")
        return ZonedDateTime.now(zone)
            .toLocalDate()
            .atTime(LocalTime.of(23, 59, 59))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    private suspend fun advanceFirmCard() {
        val nextIndex = uiState.value.firmCardIndex + 1
        val settings = screenState.value.settings
        val card = repository.buildFirmCard(settings, nextIndex)
        val summary = repository.firmTodaySummary(settings)
        uiState.update {
            it.copy(
                firmCard = card,
                firmSummary = summary,
                firmCardIndex = nextIndex,
                firmSelectedOptionId = null,
                firmCorrectOptionId = null,
                firmChoiceCorrect = null,
                firmForgetDetailWord = null,
                firmDetailRecord = null,
                noteDraft = card.word?.note.orEmpty(),
                message = if (card.kind == FirmCardKind.Done) "今日已完成" else null
            )
        }
        card.word?.let { if (settings.autoPlayWord) autoSpeak(it) }
    }

    override fun onCleared() {
        tts.shutdown()
        answerSound.release()
        super.onCleared()
    }
}

class DanChiViewModelFactory(
    private val repository: DanChiRepository,
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DanChiViewModel(repository, application) as T
    }
}
