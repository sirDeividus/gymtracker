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
                // Se trata como una guía de voz: el sistema y las apps de música bajan su volumen mientras habla.
                tts?.setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
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
        AudioDucker.duck(2200)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cue")
    }
}
