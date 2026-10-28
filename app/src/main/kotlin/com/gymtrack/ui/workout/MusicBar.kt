package com.gymtrack.ui.workout

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtrack.GymTrackApp
import com.gymtrack.ui.components.MinTouch
import com.gymtrack.ui.theme.Brand

private fun prefs(context: Context) = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

/**
 * Mini control de la música que suena en otra app (YouTube Music, Spotify…): título, pausa, anterior y siguiente
 * sin salir del entrenamiento. Pide una sola vez el permiso de «Acceso a notificaciones» (solo se usan las sesiones
 * multimedia; no se lee ninguna notificación).
 */
@Composable
fun MusicBar(modifier: Modifier = Modifier) {
    val c = Brand.colors
    val context = LocalContext.current
    val np = remember { (context.applicationContext as GymTrackApp).container.nowPlaying }
    val state by np.state.collectAsStateWithLifecycle()
    val access by np.hasAccess.collectAsStateWithLifecycle()
    var dismissed by remember { mutableStateOf(prefs(context).getBoolean("music_prompt_dismissed", false)) }

    // Escucha solo mientras la pantalla está visible; al volver de los ajustes del sistema se vuelve a comprobar el permiso.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> np.start()
                Lifecycle.Event.ON_PAUSE -> np.stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); np.stop() }
    }

    val shape = RoundedCornerShape(16.dp)
    val row = Modifier.fillMaxWidth().clip(shape).background(c.surfaceHigh)
    val st = state
    when {
        !access -> if (!dismissed) {
            Row(modifier.then(row).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = c.primary)
                Text(
                    "  Controla tu música sin salir", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { np.openAccessSettings() }) { Text("Activar", color = c.primary) }
                IconButton(onClick = { prefs(context).edit().putBoolean("music_prompt_dismissed", true).apply(); dismissed = true }, modifier = Modifier.size(MinTouch)) {
                    Icon(Icons.Default.Close, contentDescription = "No volver a mostrar", tint = c.textMuted)
                }
            }
        }
        st == null -> Row(modifier.then(row).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = c.textMuted)
            Text("  Nada suena ahora", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
            TextButton(onClick = { np.openYoutubeMusic() }) { Text("Abrir YT Music", color = c.primary) }
        }
        else -> Row(modifier.then(row).padding(horizontal = 8.dp).heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(c.surface).clickable { np.openSource() }, contentAlignment = Alignment.Center) {
                val art = st.art
                if (art != null) Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp))
                else Icon(Icons.Default.MusicNote, contentDescription = null, tint = c.textMuted)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable { np.openSource() }) {
                Text(st.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(st.artist, st.appName).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { np.previous() }, modifier = Modifier.size(MinTouch)) { Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior") }
            IconButton(onClick = { np.playPause() }, modifier = Modifier.size(MinTouch)) {
                Icon(if (st.playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (st.playing) "Pausar música" else "Reproducir música", tint = c.primary)
            }
            IconButton(onClick = { np.next() }, modifier = Modifier.size(MinTouch)) { Icon(Icons.Default.SkipNext, contentDescription = "Siguiente") }
        }
    }
}
