package com.danchi.app.scheduler

import com.danchi.app.domain.MeaningChoiceOption
import com.danchi.app.domain.Word
import com.danchi.app.domain.displayMeanings
import com.danchi.app.domain.getPosName
import com.danchi.app.domain.meaningChoiceOptionId
import com.danchi.app.domain.normalizePos
import com.danchi.app.domain.primaryMeaningText
import io.github.openspacedrepetition.Card
import io.github.openspacedrepetition.Rating
import io.github.openspacedrepetition.Scheduler
import io.github.openspacedrepetition.State
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class FsrsRating(val value: Int) {
    Again(1),
    Hard(2),
    Good(3),
    Easy(4);

    fun toOpenRating(): Rating {
        return when (this) {
            Again -> Rating.AGAIN
            Hard -> Rating.HARD
            Good -> Rating.GOOD
            Easy -> Rating.EASY
        }
    }

    companion object {
        fun fromValue(value: Int): FsrsRating {
            return entries.firstOrNull { it.value == value } ?: Good
        }
    }
}

enum class FsrsCardType(val value: String) {
    Recognition("recognition"),
    Recall("recall"),
    Audio("audio"),
    Sentence("sentence");

    companion object {
        fun fromValue(value: String): FsrsCardType {
            return entries.firstOrNull { it.value == value } ?: Recognition
        }
    }
}

enum class FsrsCardState(val value: String) {
    New("new"),
    Learning("learning"),
    Review("review"),
    Relearning("relearning");

    fun toOpenState(): State? {
        return when (this) {
            New -> null
            Learning -> State.LEARNING
            Review -> State.REVIEW
            Relearning -> State.RELEARNING
        }
    }

    companion object {
        fun fromValue(value: String): FsrsCardState {
            return entries.firstOrNull { it.value == value } ?: New
        }

        fun fromOpenState(state: State?): FsrsCardState {
            return when (state) {
                State.LEARNING -> Learning
                State.REVIEW -> Review
                State.RELEARNING -> Relearning
                null -> New
            }
        }
    }
}

data class SchedulerSettings(
    val desiredRetention: Double = 0.90,
    val maximumInterval: Int = 36500,
    val enableFuzz: Boolean = true,
    val enableShortTerm: Boolean = true,
    val learningSteps: List<String> = listOf("1m", "10m"),
    val relearningSteps: List<String> = listOf("10m"),
    val fsrsParamsJson: String? = null,
    val dailyMinutes: Int = 10,
    val maxNewCardsPerDay: Int = 20
)

data class SchedulerCard(
    val id: Long,
    val userId: String,
    val wordId: String,
    val cardType: FsrsCardType = FsrsCardType.Recognition,
    val state: FsrsCardState = FsrsCardState.New,
    val dueAt: Long,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val elapsedDays: Int = 0,
    val scheduledDays: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val fsrsStep: Int? = null,
    val lastReviewAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

data class AnswerRatingInput(
    val firstAnswerCorrect: Boolean,
    val usedHint: Boolean,
    val durationMs: Long,
    val cardState: FsrsCardState,
    val reps: Int,
    val recentCorrectCount: Int
)

data class AnswerCardInput(
    val card: SchedulerCard,
    val settings: SchedulerSettings,
    val firstAnswerCorrect: Boolean,
    val usedHint: Boolean,
    val durationMs: Long,
    val questionType: String,
    val optionCount: Int,
    val answerSnapshot: String,
    val recentCorrectCount: Int,
    val now: Long = System.currentTimeMillis()
)

data class SchedulerReviewLog(
    val userId: String,
    val cardId: Long,
    val wordId: String,
    val reviewedAt: Long,
    val rating: FsrsRating,
    val elapsedDays: Int,
    val scheduledDays: Int,
    val durationMs: Long,
    val questionType: String,
    val optionCount: Int,
    val isCorrect: Boolean,
    val firstAnswerCorrect: Boolean,
    val usedHint: Boolean,
    val answerSnapshot: String,
    val stateBefore: FsrsCardState,
    val stateAfter: FsrsCardState
)

data class AnswerCardResult(
    val card: SchedulerCard,
    val reviewLog: SchedulerReviewLog
)

data class DailyFsrsPlan(
    val learningDueCount: Int,
    val reviewDueCount: Int,
    val dueCount: Int,
    val newCount: Int,
    val avgReviewSeconds: Double,
    val estimatedMinutes: Int
)

data class OptimizeParamsResult(
    val status: Status,
    val message: String,
    val fsrsParamsJson: String? = null
) {
    enum class Status {
        NotEnoughData,
        ExternalOptimizerRequired,
        Optimized
    }
}

object FsrsScheduler {
    private const val DefaultNewCardSeconds = 45.0
    private const val MinimumOptimizationLogs = 400

    fun createCard(
        id: Long = 0L,
        userId: String,
        wordId: String,
        cardType: FsrsCardType = FsrsCardType.Recognition,
        now: Long = System.currentTimeMillis()
    ): SchedulerCard {
        return SchedulerCard(
            id = id,
            userId = userId,
            wordId = wordId,
            cardType = cardType,
            state = FsrsCardState.New,
            dueAt = now,
            createdAt = now,
            updatedAt = now
        )
    }

    fun answerCard(input: AnswerCardInput): AnswerCardResult {
        val rating = mapAnswerToRating(
            AnswerRatingInput(
                firstAnswerCorrect = input.firstAnswerCorrect,
                usedHint = input.usedHint,
                durationMs = input.durationMs,
                cardState = input.card.state,
                reps = input.card.reps,
                recentCorrectCount = input.recentCorrectCount
            )
        )
        val scheduler = buildScheduler(input.settings)
        val reviewedAt = Instant.ofEpochMilli(input.now)
        val result = scheduler.reviewCard(
            input.card.toOpenCard(),
            rating.toOpenRating(),
            reviewedAt,
            input.durationMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        )
        val nextOpenCard = result.card()
        val nextState = FsrsCardState.fromOpenState(nextOpenCard.state)
        val elapsedDays = elapsedDays(input.card.lastReviewAt, input.now)
        val scheduledDays = scheduledDays(input.now, nextOpenCard.due)
        val nextCard = input.card.copy(
            state = nextState,
            dueAt = nextOpenCard.due.toEpochMilli(),
            stability = nextOpenCard.stability,
            difficulty = nextOpenCard.difficulty,
            elapsedDays = elapsedDays,
            scheduledDays = scheduledDays,
            reps = input.card.reps + 1,
            lapses = input.card.lapses + if (input.card.state == FsrsCardState.Review && rating == FsrsRating.Again) 1 else 0,
            fsrsStep = nextOpenCard.step,
            lastReviewAt = input.now,
            updatedAt = input.now
        )
        return AnswerCardResult(
            card = nextCard,
            reviewLog = SchedulerReviewLog(
                userId = input.card.userId,
                cardId = input.card.id,
                wordId = input.card.wordId,
                reviewedAt = input.now,
                rating = rating,
                elapsedDays = elapsedDays,
                scheduledDays = scheduledDays,
                durationMs = input.durationMs,
                questionType = input.questionType,
                optionCount = input.optionCount,
                isCorrect = input.firstAnswerCorrect,
                firstAnswerCorrect = input.firstAnswerCorrect,
                usedHint = input.usedHint,
                answerSnapshot = input.answerSnapshot,
                stateBefore = input.card.state,
                stateAfter = nextState
            )
        )
    }

    fun mapAnswerToRating(input: AnswerRatingInput): FsrsRating {
        if (!input.firstAnswerCorrect) return FsrsRating.Again
        if (input.usedHint) return FsrsRating.Hard
        if (input.durationMs > 8_000L) return FsrsRating.Hard
        if (input.durationMs > 3_000L) return FsrsRating.Good
        return if (
            input.cardState == FsrsCardState.Review &&
            input.recentCorrectCount >= 2 &&
            input.reps > 0
        ) {
            FsrsRating.Easy
        } else {
            FsrsRating.Good
        }
    }

    fun getDueQueue(cards: List<SchedulerCard>, now: Long = System.currentTimeMillis()): List<SchedulerCard> {
        return cards
            .filter { it.dueAt <= now || it.state == FsrsCardState.New }
            .sortedWith(
                compareBy<SchedulerCard> {
                    when (it.state) {
                        FsrsCardState.Learning, FsrsCardState.Relearning -> 0
                        FsrsCardState.Review -> 1
                        FsrsCardState.New -> 2
                    }
                }.thenBy { it.dueAt }
            )
    }

    fun generateDailyPlan(
        learningDueCount: Int,
        reviewDueCount: Int,
        remainingNewCards: Int,
        avgReviewSeconds: Double,
        dailyMinutes: Int,
        maxNewCardsPerDay: Int
    ): DailyFsrsPlan {
        val safeAvgSeconds = avgReviewSeconds.coerceAtLeast(5.0)
        val dueCount = learningDueCount + reviewDueCount
        val reviewMinutes = dueCount * safeAvgSeconds / 60.0
        val newCount = if (reviewMinutes >= dailyMinutes) {
            0
        } else {
            val remainingSeconds = ((dailyMinutes - reviewMinutes) * 60.0).coerceAtLeast(0.0)
            min(
                min(remainingNewCards, maxNewCardsPerDay.coerceAtLeast(0)),
                (remainingSeconds / DefaultNewCardSeconds).toInt()
            )
        }
        val estimatedSeconds = dueCount * safeAvgSeconds + newCount * DefaultNewCardSeconds
        return DailyFsrsPlan(
            learningDueCount = learningDueCount,
            reviewDueCount = reviewDueCount,
            dueCount = dueCount,
            newCount = newCount,
            avgReviewSeconds = safeAvgSeconds,
            estimatedMinutes = ceil(estimatedSeconds / 60.0).toInt().coerceAtLeast(if (dueCount + newCount > 0) 1 else 0)
        )
    }

    fun calcRetrievability(
        card: SchedulerCard,
        settings: SchedulerSettings,
        now: Long = System.currentTimeMillis()
    ): Double? {
        if (card.state == FsrsCardState.New || card.stability == null || card.lastReviewAt == null) return null
        return buildScheduler(settings).getCardRetrievability(card.toOpenCard(), Instant.ofEpochMilli(now))
    }

    fun optimizeParams(reviewLogCount: Int): OptimizeParamsResult {
        return if (reviewLogCount < MinimumOptimizationLogs) {
            OptimizeParamsResult(
                status = OptimizeParamsResult.Status.NotEnoughData,
                message = "Need at least $MinimumOptimizationLogs review logs before per-user FSRS parameter optimization."
            )
        } else {
            OptimizeParamsResult(
                status = OptimizeParamsResult.Status.ExternalOptimizerRequired,
                message = "java-fsrs does not include an optimizer; export logs to fsrs-rs or py-fsrs, then store optimized parameters for future reviews."
            )
        }
    }

    fun buildDistractors(
        target: Word,
        candidates: List<Word>,
        confusionWordIds: Set<String> = emptySet(),
        count: Int = 3
    ): List<MeaningChoiceOption> {
        val targetPos = normalizePos(target.displayMeanings.firstOrNull()?.pos ?: target.pos)
        val targetBook = target.book
        val targetUnit = target.unit
        val scored = candidates
            .asSequence()
            .filter { it.id != target.id && it.primaryMeaningText.isNotBlank() }
            .distinctBy { it.id }
            .map { candidate ->
                val candidatePos = normalizePos(candidate.displayMeanings.firstOrNull()?.pos ?: candidate.pos)
                val score = listOf(
                    if (candidatePos.isNotBlank() && candidatePos == targetPos) 100 else 0,
                    if (candidate.book == targetBook) 20 else 0,
                    if (candidate.unit == targetUnit) 12 else 0,
                    if (confusionWordIds.contains(candidate.id)) 50 else 0,
                    semanticOverlap(target.primaryMeaningText, candidate.primaryMeaningText)
                ).sum()
                candidate to score
            }
            .sortedWith(
                compareByDescending<Pair<Word, Int>> { it.second }
                    .thenBy { "${target.id}:${it.first.id}".hashCode() }
            )
            .toList()

        val samePos = scored.filter { pair ->
            normalizePos(pair.first.displayMeanings.firstOrNull()?.pos ?: pair.first.pos) == targetPos
        }
        val selected = (samePos + scored)
            .distinctBy { it.first.id }
            .take(count)
            .map { it.first.toMeaningChoiceOption(false) }
        return selected
    }

    fun recognitionOptions(
        target: Word,
        candidates: List<Word>,
        confusionWordIds: Set<String> = emptySet()
    ): List<MeaningChoiceOption> {
        return (buildDistractors(target, candidates, confusionWordIds) + target.toMeaningChoiceOption(true))
            .distinctBy { it.wordId }
            .sortedBy { "${target.id}:fsrs:${it.wordId}".hashCode() }
    }

    private fun buildScheduler(settings: SchedulerSettings): Scheduler {
        val schedulerJson = settings.fsrsParamsJson?.trim().orEmpty()
        if (schedulerJson.startsWith("{")) {
            return runCatching { Scheduler.fromJson(schedulerJson) }.getOrElse { defaultScheduler(settings) }
        }
        val params = if (schedulerJson.startsWith("[")) parseDoubleArray(schedulerJson) else null
        return defaultScheduler(settings, params)
    }

    private fun defaultScheduler(settings: SchedulerSettings, params: DoubleArray? = null): Scheduler {
        val builder = Scheduler.builder()
            .desiredRetention(settings.desiredRetention.coerceIn(0.70, 0.99))
            .maximumInterval(settings.maximumInterval.coerceAtLeast(1))
            .enableFuzzing(settings.enableFuzz)
            .learningSteps(if (settings.enableShortTerm) settings.learningSteps.toDurations() else emptyArray())
            .relearningSteps(if (settings.enableShortTerm) settings.relearningSteps.toDurations() else emptyArray())
        if (params != null) builder.parameters(params)
        return builder.build()
    }

    private fun SchedulerCard.toOpenCard(): Card {
        val builder = Card.builder().cardId(id.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
        state.toOpenState()?.let { builder.state(it) }
        fsrsStep?.let { builder.step(it) }
        stability?.let { builder.stability(it) }
        difficulty?.let { builder.difficulty(it) }
        builder.due(Instant.ofEpochMilli(dueAt))
        lastReviewAt?.let { builder.lastReview(Instant.ofEpochMilli(it)) }
        return builder.build()
    }

    private fun elapsedDays(lastReviewAt: Long?, now: Long): Int {
        if (lastReviewAt == null) return 0
        return Duration.between(Instant.ofEpochMilli(lastReviewAt), Instant.ofEpochMilli(now)).toDays()
            .coerceAtLeast(0L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    private fun scheduledDays(now: Long, due: Instant): Int {
        val seconds = Duration.between(Instant.ofEpochMilli(now), due).seconds
        if (seconds <= 0L) return 0
        return ceil(seconds / 86_400.0).toInt().coerceAtLeast(0)
    }

    private fun List<String>.toDurations(): Array<Duration> {
        return mapNotNull { parseDuration(it) }.toTypedArray()
    }

    private fun parseDuration(value: String): Duration? {
        val match = Regex("""^\s*(\d+)\s*([mhd])\s*$""", RegexOption.IGNORE_CASE).find(value) ?: return null
        val amount = match.groupValues[1].toLong()
        return when (match.groupValues[2].lowercase()) {
            "m" -> Duration.ofMinutes(amount)
            "h" -> Duration.ofHours(amount)
            "d" -> Duration.ofDays(amount)
            else -> null
        }
    }

    private fun parseDoubleArray(json: String): DoubleArray? {
        val values = json
            .trim('[', ']')
            .split(',')
            .mapNotNull { it.trim().toDoubleOrNull() }
        return values.takeIf { it.isNotEmpty() }?.toDoubleArray()
    }

    private fun semanticOverlap(a: String, b: String): Int {
        val left = a.asMeaningTokens()
        val right = b.asMeaningTokens()
        return left.intersect(right).size.coerceAtMost(8)
    }

    private fun String.asMeaningTokens(): Set<String> {
        return split(Regex("""[\s,，;；、/]+"""))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .toSet()
    }
}

private fun Word.toMeaningChoiceOption(isCorrect: Boolean): MeaningChoiceOption {
    val meaning = displayMeanings.firstOrNull()
    val meaningId = meaning?.id ?: id
    val resolvedPos = meaning?.pos ?: normalizePos(this.pos)
    return MeaningChoiceOption(
        id = meaningChoiceOptionId(id, meaningId),
        wordId = id,
        meaningId = meaningId,
        pos = resolvedPos,
        posName = meaning?.posName.orEmpty().ifBlank { getPosName(resolvedPos) },
        meaning = meaning?.meaning ?: this.meaning,
        isCorrect = isCorrect
    )
}
