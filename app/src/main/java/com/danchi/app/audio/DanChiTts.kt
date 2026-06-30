package com.danchi.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import com.danchi.app.domain.Accent
import java.util.Locale

class DanChiTts(context: Context) : TextToSpeech.OnInitListener {
    private var ready = false
    private val tts = runCatching {
        TextToSpeech(context.applicationContext, this)
    }.getOrNull()

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
    }

    fun speak(text: String, accent: Accent, rate: Float, repeatCount: Int = 1) {
        if (!ready || text.isBlank()) return
        val engine = tts ?: return
        runCatching {
            engine.language = when (accent) {
                Accent.Us -> Locale.US
                Accent.Uk -> Locale.UK
            }
            engine.setSpeechRate(rate)
            val count = repeatCount.coerceIn(1, 3)
            repeat(count) { index ->
                val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                engine.speak(text, queueMode, null, "danchi-${System.nanoTime()}-$index")
                if (index < count - 1) {
                    engine.playSilentUtterance(220L, TextToSpeech.QUEUE_ADD, "danchi-gap-${System.nanoTime()}-$index")
                }
            }
        }.onFailure {
            ready = false
        }
    }

    fun shutdown() {
        runCatching { tts?.shutdown() }
    }
}
