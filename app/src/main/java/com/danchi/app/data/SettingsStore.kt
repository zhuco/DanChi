package com.danchi.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.danchi.app.domain.Accent
import com.danchi.app.domain.StudyPlanOptions
import com.danchi.app.domain.StudySettings
import com.danchi.app.domain.StudyWordOrder
import com.danchi.app.domain.WordbookDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.round

private val Context.settingsDataStore by preferencesDataStore(name = "study_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val DailyNew = intPreferencesKey("daily_new")
        val ReviewLimit = intPreferencesKey("review_limit")
        val AutoPlayWord = booleanPreferencesKey("auto_play_word")
        val AutoPlayExample = booleanPreferencesKey("auto_play_example")
        val EnableNewWordPreview = booleanPreferencesKey("enable_new_word_preview")
        val SelectedWordbookId = stringPreferencesKey("selected_wordbook_id")
        val WordOrder = stringPreferencesKey("word_order")
        val Accent = stringPreferencesKey("accent")
        val SpeechRate = floatPreferencesKey("speech_rate")
        val AutoPlayRepeatCount = intPreferencesKey("auto_play_repeat_count")
        val MasteryConfirmMutedUntil = longPreferencesKey("mastery_confirm_muted_until")
        val DailyMinutes = intPreferencesKey("daily_minutes")
        val MaxNewCardsPerDay = intPreferencesKey("max_new_cards_per_day")
    }

    val settings: Flow<StudySettings> = context.settingsDataStore.data.map { values ->
        val dailyNewWords = StudyPlanOptions.normalizeDailyNewWords(values[Keys.DailyNew] ?: 10)
        val defaultReviewLimit = StudyPlanOptions.defaultReviewLimit(dailyNewWords)
        StudySettings(
            dailyNewWords = dailyNewWords,
            reviewLimit = values[Keys.ReviewLimit]?.coerceAtLeast(1) ?: defaultReviewLimit,
            autoPlayWord = values[Keys.AutoPlayWord] ?: true,
            autoPlayExample = values[Keys.AutoPlayExample] ?: false,
            enableNewWordPreview = values[Keys.EnableNewWordPreview] ?: true,
            selectedWordbookId = values[Keys.SelectedWordbookId] ?: WordbookDefaults.DefaultId,
            wordOrder = runCatching {
                StudyWordOrder.valueOf(values[Keys.WordOrder] ?: StudyWordOrder.Alphabetical.name)
            }.getOrDefault(StudyWordOrder.Alphabetical),
            accent = runCatching { Accent.valueOf(values[Keys.Accent] ?: Accent.Us.name) }.getOrDefault(Accent.Us),
            speechRate = normalizeSpeechRate(values[Keys.SpeechRate] ?: 0.9f),
            autoPlayRepeatCount = StudyPlanOptions.normalizeAutoPlayRepeatCount(values[Keys.AutoPlayRepeatCount] ?: 1),
            masteryConfirmMutedUntilMillis = values[Keys.MasteryConfirmMutedUntil] ?: 0L,
            dailyMinutes = values[Keys.DailyMinutes]?.coerceIn(1, 240) ?: 10,
            maxNewCardsPerDay = values[Keys.MaxNewCardsPerDay]?.coerceIn(0, 200) ?: dailyNewWords.coerceAtMost(20)
        )
    }

    suspend fun updateDailyNew(value: Int) {
        val dailyNewWords = StudyPlanOptions.normalizeDailyNewWords(value)
        context.settingsDataStore.edit {
            it[Keys.DailyNew] = dailyNewWords
            it[Keys.ReviewLimit] = StudyPlanOptions.defaultReviewLimit(dailyNewWords)
            it[Keys.MaxNewCardsPerDay] = dailyNewWords.coerceAtMost(200)
        }
    }

    suspend fun updateReviewLimit(value: Int) {
        context.settingsDataStore.edit { it[Keys.ReviewLimit] = value.coerceAtLeast(50) }
    }

    suspend fun updateAutoPlayWord(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.AutoPlayWord] = value }
    }

    suspend fun updateAutoPlayExample(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.AutoPlayExample] = value }
    }

    suspend fun updateEnableNewWordPreview(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.EnableNewWordPreview] = value }
    }

    suspend fun updateSelectedWordbook(wordbookId: String) {
        context.settingsDataStore.edit { it[Keys.SelectedWordbookId] = wordbookId }
    }

    suspend fun updateWordOrder(value: StudyWordOrder) {
        context.settingsDataStore.edit { it[Keys.WordOrder] = value.name }
    }

    suspend fun updateAccent(value: Accent) {
        context.settingsDataStore.edit { it[Keys.Accent] = value.name }
    }

    suspend fun updateSpeechRate(value: Float) {
        context.settingsDataStore.edit { it[Keys.SpeechRate] = normalizeSpeechRate(value) }
    }

    suspend fun updateAutoPlayRepeatCount(value: Int) {
        context.settingsDataStore.edit {
            it[Keys.AutoPlayRepeatCount] = StudyPlanOptions.normalizeAutoPlayRepeatCount(value)
        }
    }

    suspend fun updateMasteryConfirmMutedUntil(value: Long) {
        context.settingsDataStore.edit { it[Keys.MasteryConfirmMutedUntil] = value.coerceAtLeast(0L) }
    }

    suspend fun updateDailyMinutes(value: Int) {
        context.settingsDataStore.edit { it[Keys.DailyMinutes] = value.coerceIn(1, 240) }
    }

    private fun normalizeSpeechRate(value: Float): Float {
        return (round(value.coerceIn(0.65f, 1.25f) * 100f) / 100f)
    }
}
