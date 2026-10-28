package com.gymtrack.notify

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Voz para los avisos («Descanso», «Prepárate», «Go», cuenta atrás). Se inicia sola la primera vez que se necesita. */
class SpeechCues(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    /** Crea el motor de voz con antelación (tarda un instante) para que el primer aviso no se pierda. */
    fun warmUp() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val es = Locale("es", "ES")
                val engine = tts
                engine?.language = if ((engine?.isLanguageAvailable(es) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE) es else Locale.getDefault()
                ready = true
            }
        }
    }

    fun speak(text: String) {
        warmUp()
        if (!ready) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cue")
    }
}
