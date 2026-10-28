package com.gymtrack.notify

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Lo que suena ahora en otra app de música. */
data class NowPlayingState(
    val title: String,
    val artist: String,
    val appName: String,
    val playing: Boolean,
    val art: Bitmap?,
)

/**
 * Controla la música de otras apps (YouTube Music, Spotify…) a través de sus sesiones multimedia,
 * sin salir de GymTrack. Necesita que el usuario conceda «Acceso a notificaciones» a la app.
 */
class NowPlaying(private val context: Context) {
    private val _state = MutableStateFlow<NowPlayingState?>(null)
    val state: StateFlow<NowPlayingState?> = _state
    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess

    private var controller: MediaController? = null
    private val component = ComponentName(context, MediaControlService::class.java)
    private val manager get() = context.getSystemService(MediaSessionManager::class.java)

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() { controller = null; pick(safeSessions()) }
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }
    private var listening = false

    fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Empieza a escuchar (al mostrarse la pantalla). Si no hay permiso, solo lo refleja en [hasAccess]. */
    fun start() {
        val ok = hasNotificationAccess()
        _hasAccess.value = ok
        if (!ok) { stop(); _state.value = null; return }
        if (listening) { pick(safeSessions()); return }
        try {
            manager?.addOnActiveSessionsChangedListener(sessionsListener, component)
            listening = true
            pick(safeSessions())
        } catch (_: SecurityException) {
            _hasAccess.value = false
        }
    }

    fun stop() {
        if (listening) {
            try { manager?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) { /* ya quitado */ }
            listening = false
        }
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun safeSessions(): List<MediaController> = try {
        manager?.getActiveSessions(component).orEmpty()
    } catch (_: SecurityException) {
        _hasAccess.value = false
        emptyList()
    }

    private fun pick(list: List<MediaController>) {
        val candidates = list.filter { it.packageName != context.packageName }
        val best = candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: candidates.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) { _state.value = null; return }
        val md = c.metadata
        if (md == null) { _state.value = null; return }
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrBlank()) { _state.value = null; return }
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val app = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString()
        }.getOrDefault(c.packageName)
        _state.value = NowPlayingState(title, artist, app, c.playbackState?.state == PlaybackState.STATE_PLAYING, art)
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    /** Abre la app de música que está sonando. */
    fun openSource() {
        try { controller?.sessionActivity?.send() } catch (_: Exception) { /* sin actividad asociada */ }
    }

    fun openAccessSettings() {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Abre YouTube Music si está instalada. */
    fun openYoutubeMusic(): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.youtube.music") ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
