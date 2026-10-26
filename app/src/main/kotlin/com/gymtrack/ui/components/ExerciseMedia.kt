package com.gymtrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gymtrack.GymTrackApp
import com.gymtrack.R
import com.gymtrack.data.media.MediaRef
import com.gymtrack.ui.theme.Brand

@Composable
private fun rememberMediaRef(exerciseId: String): Pair<MediaRef?, GymTrackApp> {
    val app = LocalContext.current.applicationContext as GymTrackApp
    // Al añadir o quitar un video propio, la versión cambia y se vuelve a consultar.
    val version by app.container.media.version.collectAsState()
    return remember(exerciseId, version) { app.container.media.refFor(exerciseId) } to app
}

/** ¿Hay imagen o GIF para este ejercicio? */
@Composable
fun hasExerciseMedia(exerciseId: String): Boolean = rememberMediaRef(exerciseId).first != null

/** Miniatura cuadrada; no ocupa espacio si el ejercicio no tiene medio instalado. */
@Composable
fun ExerciseThumb(exerciseId: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val (ref, app) = rememberMediaRef(exerciseId)
    val uri: Any = ref?.thumbUri ?: ref?.gifUri ?: ref?.video ?: return
    AsyncImage(
        model = uri,
        contentDescription = null,
        imageLoader = app.container.imageLoader,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(RoundedCornerShape(12.dp)),
    )
}

/** Miniatura si hay imagen instalada; si no, un cuadrado con la inicial para mantener el ritmo visual. */
@Composable
fun ExerciseThumbOrInitial(exerciseId: String, name: String, size: Dp = 64.dp, modifier: Modifier = Modifier) {
    if (hasExerciseMedia(exerciseId)) {
        ExerciseThumb(exerciseId, modifier, size)
    } else {
        androidx.compose.foundation.layout.Box(
            modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Brand.colors.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.firstOrNull()?.uppercase().orEmpty(), style = MaterialTheme.typography.titleLarge, color = Brand.colors.textSecondary)
        }
    }
}

/** GIF animado (o la imagen si solo hay miniatura) con la atribución obligatoria. */
@Composable
fun ExerciseAnimation(exerciseId: String, modifier: Modifier = Modifier, size: Dp = 180.dp) {
    val (ref, app) = rememberMediaRef(exerciseId)
    val uri = ref?.gifUri ?: ref?.thumbUri ?: return
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AsyncImage(
            model = uri,
            contentDescription = "Demostración del ejercicio",
            imageLoader = app.container.imageLoader,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size).clip(RoundedCornerShape(14.dp)),
        )
        Text(stringResource(R.string.media_attribution), style = MaterialTheme.typography.bodySmall, color = Brand.colors.textSecondary)
    }
}

/** Demostración a pantalla completa y ajustada al ancho: GIF del catálogo o video propio en bucle. */
@Composable
fun ExerciseMediaDialog(exerciseId: String, title: String, onDismiss: () -> Unit) {
    val (ref, app) = rememberMediaRef(exerciseId)
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
            Column(Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Cerrar",
                            tint = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                    Text(
                        title, style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 2, modifier = Modifier.weight(1f),
                    )
                }
                androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val video = ref?.video
                    val image = ref?.gifUri ?: ref?.thumbUri
                    when {
                        video != null -> LoopingVideo(video, Modifier.fillMaxSize())
                        image != null -> AsyncImage(
                            model = image, contentDescription = "Demostración de $title", imageLoader = app.container.imageLoader,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White),
                        )
                        else -> Text("Sin demostración", color = androidx.compose.ui.graphics.Color.White)
                    }
                }
                if (ref?.video == null) {
                    Text(
                        stringResource(R.string.media_attribution), style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
            }
        }
    }
}

/** Video sin sonido en bucle, ajustado al espacio disponible. */
@Composable
fun LoopingVideo(file: java.io.File, modifier: Modifier = Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val frame = android.widget.FrameLayout(ctx)
            val view = android.widget.VideoView(ctx)
            frame.addView(view, android.widget.FrameLayout.LayoutParams(-1, -1, android.view.Gravity.CENTER))
            view.setOnPreparedListener { mp -> mp.isLooping = true; mp.setVolume(0f, 0f) }
            view.setVideoPath(file.absolutePath)
            view.start()
            frame
        },
        onRelease = { f -> (f.getChildAt(0) as? android.widget.VideoView)?.stopPlayback() },
    )
}

/** Imagen del ejercicio a ancho completo sobre fondo blanco (las ilustraciones del dataset son sobre blanco). */
@Composable
fun ExerciseImageFill(
    exerciseId: String, name: String, modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    /** true = GIF animado si existe (solo para pocos elementos en pantalla, p. ej. el entrenamiento). */
    animated: Boolean = false,
) {
    val (ref, app) = rememberMediaRef(exerciseId)
    val uri: Any? = (if (animated) ref?.gifUri ?: ref?.thumbUri else ref?.thumbUri ?: ref?.gifUri) ?: ref?.video
    if (uri == null) {
        androidx.compose.foundation.layout.Box(
            modifier.clip(shape).background(Brand.colors.surfaceHigh), contentAlignment = Alignment.Center,
        ) { Text(name.firstOrNull()?.uppercase().orEmpty(), style = MaterialTheme.typography.headlineMedium, color = Brand.colors.textSecondary) }
    } else {
        AsyncImage(
            model = uri, contentDescription = null, imageLoader = app.container.imageLoader,
            contentScale = ContentScale.Fit,
            modifier = modifier.clip(shape).background(androidx.compose.ui.graphics.Color.White),
        )
    }
}
