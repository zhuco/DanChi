package com.danchi.app.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.danchi.app.audio.AnswerFeedbackSound
import com.danchi.app.audio.DanChiTts
import com.danchi.app.data.DanChiRepository
import com.danchi.app.domain.Accent
import com.danchi.app.domain.FsrsBuildProgress
import com.danchi.app.domain.FsrsStudyItem
import com.danchi.app.domain.LibraryStats
import com.danchi.app.domain.StudyProfileStats
import com.danchi.app.domain.StudySettings
import com.danchi.app.domain.StudyWordOrder
import com.danchi.app.domain.TodayPlan
import com.danchi.app.domain.Word
import com.danchi.app.domain.WordbookProgress
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
    val sessionMode: SessionMode = SessionMode.Today,
    val session: List<Word> = emptyList(),
    val fsrsSession: List<FsrsStudyItem> = emptyList(),
    val fsrsSessionId: String = "",
    val fsrsTotalCount: Int = 0,
    val fsrsCompletedCount: Int = 0,
    val fsrsTailBuilding: Boolean = false,
    val fsrsTailBuildTitle: String = "",
    val currentIndex: Int = 0,
    val query: String = "",
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
    val currentWord: Word? get() = currentFsrsItem?.word
}

enum class MainTab(val label: String) {
    Home("首页"),
    Study("学习"),
    Dictionary("词典"),
    Stats("统计"),
    Me("我的")
}

enum class SessionMode(val label: String) {
    Today("今日学习")
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

    fun startSession(mode: SessionMode = SessionMode.Today) {
        viewModelScope.launch {
            uiState.update {
                it.copy(
                    busy = true,
                    sessionMode = mode,
                    activeTab = MainTab.Study,
                    currentIndex = 0,
                    session = emptyList(),
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
                    loadingTitle = "正在准备今日提分",
                    loadingDetail = "正在检查是否已有今日清单。",
                    loadingProgress = 0.05f,
                    message = null
                )
            }

            val settings = screenState.value.settings
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
                    message = when {
                        snapshot.items.isEmpty() || snapshot.currentPosition >= snapshot.totalCount -> "今日没有待学习内容"
                        snapshot.isResumed -> "继续今日提分"
                        else -> null
                    }
                )
            }
            snapshot.items.getOrNull(snapshot.currentPosition)?.word?.let {
                if (settings.autoPlayWord) autoSpeak(it)
            }
            if (snapshot.isBuilding) {
                continueTodayFsrsBuild(settings, snapshot.sessionId)
            }
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

    fun answerCurrentRecognition(optionId: String) {
        val state = uiState.value
        val item = state.currentFsrsItem ?: return
        if (state.fsrsAnswered) return
        viewModelScope.launch {
            val duration = (System.currentTimeMillis() - state.fsrsAnswerStartedAt).coerceAtLeast(0L)
            val selectedWordId = item.options.firstOrNull { it.id == optionId }?.wordId ?: optionId
            val correctOptionId = item.options.firstOrNull { it.wordId == item.word.id }?.id ?: item.word.id
            val correct = repository.answerRecognition(
                cardId = item.cardId,
                selectedWordId = selectedWordId,
                options = item.options.map { it.wordId },
                durationMs = duration,
                studySettings = screenState.value.settings,
                selectedOptionId = optionId
            )
            playAnswerFeedback(correct)
            uiState.update {
                it.copy(
                    fsrsAnswered = true,
                    fsrsSelectedOptionId = optionId,
                    fsrsCorrectOptionId = correctOptionId,
                    fsrsAnswerCorrect = correct,
                    fsrsInfoVisible = false,
                    fsrsCompletedCount = (it.fsrsCompletedCount + 1).coerceAtMost(it.fsrsTotalCount),
                    noteDraft = item.word.note.orEmpty()
                )
            }
        }
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

    fun continueAfterFsrsAnswer() {
        val state = uiState.value
        if (state.fsrsAnswered && !state.fsrsInfoVisible) {
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
                    if (screenState.value.settings.autoPlayWord) autoSpeak(item.word)
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

    fun moveNext() {
        val current = uiState.value
        val nextIndex = current.currentIndex + 1
        val nextWord = current.fsrsSession.getOrNull(nextIndex)?.word
        uiState.update {
            it.copy(
                currentIndex = nextIndex.coerceAtMost(current.fsrsSession.size),
                fsrsAnswerStartedAt = System.currentTimeMillis(),
                fsrsAnswered = false,
                fsrsSelectedOptionId = null,
                fsrsCorrectOptionId = null,
                fsrsAnswerCorrect = null,
                fsrsInfoVisible = false,
                noteDraft = nextWord?.note.orEmpty(),
                message = if (nextWord == null && !it.fsrsTailBuilding) "学习已完成" else null
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
        val word = uiState.value.currentWord ?: return
        viewModelScope.launch {
            if (muteConfirmToday) {
                repository.updateMasteryConfirmMutedUntil(endOfBeijingDayMillis())
            }
            repository.markMastered(word)
            removeCurrentWordFromSession()
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
    fun updateAutoPlayWord(value: Boolean) = viewModelScope.launch { repository.updateAutoPlayWord(value) }
    fun updateAutoPlayExample(value: Boolean) = viewModelScope.launch { repository.updateAutoPlayExample(value) }
    fun updateDailyMinutes(value: Int) = viewModelScope.launch { repository.updateDailyMinutes(value) }
    fun updateWordOrder(value: StudyWordOrder) = viewModelScope.launch { repository.updateWordOrder(value) }
    fun updateAccent(value: Accent) = viewModelScope.launch { repository.updateAccent(value) }
    fun updateSpeechRate(value: Float) = viewModelScope.launch { repository.updateSpeechRate(value) }
    fun updateAutoPlayRepeatCount(value: Int) = viewModelScope.launch { repository.updateAutoPlayRepeatCount(value) }
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
                query = "",
                noteDraft = "",
                message = "已切换词库"
            )
        }
    }

    fun toggleAccent() {
        val next = if (screenState.value.settings.accent == Accent.Us) Accent.Uk else Accent.Us
        updateAccent(next)
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

    private fun removeCurrentWordFromSession() {
        val current = uiState.value
        val nextFsrsSession = current.fsrsSession.filterIndexed { index, _ -> index != current.currentIndex }
        val nextIndex = current.currentIndex.coerceAtMost((nextFsrsSession.size - 1).coerceAtLeast(0))
        val nextWord = nextFsrsSession.getOrNull(nextIndex)?.word
        uiState.update {
            it.copy(
                session = nextFsrsSession.map { item -> item.word },
                fsrsSession = nextFsrsSession,
                currentIndex = if (nextFsrsSession.isEmpty()) 0 else nextIndex,
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
