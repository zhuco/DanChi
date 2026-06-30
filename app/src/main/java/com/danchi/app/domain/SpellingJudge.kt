package com.danchi.app.domain

object SpellingJudge {
    fun normalize(input: String): String {
        return input.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    fun isCorrect(input: String, answer: String): Boolean {
        return normalize(input) == normalize(answer)
    }
}
