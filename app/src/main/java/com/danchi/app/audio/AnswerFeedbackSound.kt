package com.danchi.app.audio

import android.media.AudioManager
import android.media.ToneGenerator

class AnswerFeedbackSound {
    private val toneGenerator = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, 72)
    }.getOrNull()

    fun playCorrect() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
    }

    fun playWrong() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 220)
    }

    fun release() {
        runCatching { toneGenerator?.release() }
    }
}
