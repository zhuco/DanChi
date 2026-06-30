package com.danchi.app.data

import java.util.Locale

object LearningKeys {
    const val DefaultPlanName = "default"
    const val TodayFsrsSessionMode = "fsrs_today"

    fun wordKey(word: String): String {
        return word.trim().lowercase(Locale.ROOT)
    }

    fun learningWordId(wordbookId: String, wordKey: String): String {
        return "$wordbookId:$wordKey"
    }

    fun planId(userId: String, wordbookId: String, name: String = DefaultPlanName): String {
        return "$userId:$wordbookId:$name"
    }

    fun sessionId(userId: String, wordbookId: String, studyDayEpoch: Long, mode: String): String {
        return "$userId:$wordbookId:$studyDayEpoch:$mode"
    }
}
