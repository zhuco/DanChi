package com.danchi.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpellingJudgeTest {
    @Test
    fun spellingIgnoresCaseAndExtraSpaces() {
        assertTrue(SpellingJudge.isCorrect("  Look   after ", "look after"))
    }

    @Test
    fun spellingRejectsDifferentWord() {
        assertFalse(SpellingJudge.isCorrect("affect", "effect"))
    }
}
