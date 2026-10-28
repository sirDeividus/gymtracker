package com.gymtrack.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Baja el volumen de la música de otras apps (YouTube Music, Spotify…) mientras suena un aviso nuestro
 * (pitido o voz) y lo devuelve al terminar. Usa el «enfoque de audio» de Android con ducking: la app de música
 * es quien baja su volumen, y lo hacen las principales.
 */
object AudioDucker {
    private var audio: AudioManager? = null
    private var request: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val release = Runnable { abandon() }

    fun init(context: Context) {
        if (audio == null) audio = context.applicationContext.getSystemService(AudioManager::class.java)
    }

    /** Atenúa la música durante [holdMs] milisegundos (se alarga si llega otro aviso antes de que acabe). */
    fun duck(holdMs: Long = 1500) {
        val am = audio ?: return
        handler.removeCallbacks(release)
        if (request == null) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener { }
                .build()
            if (am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) request = req
        }
        handler.postDelayed(release, holdMs)
    }

    private fun abandon() {
        val req = request ?: return
        audio?.abandonAudioFocusRequest(req)
        request = null
    }
}
