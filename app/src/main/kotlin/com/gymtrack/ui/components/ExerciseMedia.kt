package com.gymtrack.ui.components

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
    return remember(exerciseId) { app.container.media.refFor(exerciseId) } to app
}

/** ¿Hay imagen o GIF para este ejercicio? */
@Composable
fun hasExerciseMedia(exerciseId: String): Boolean = rememberMediaRef(exerciseId).first != null

/** Miniatura cuadrada; no ocupa espacio si el ejercicio no tiene medio instalado. */
@Composable
fun ExerciseThumb(exerciseId: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val (ref, app) = rememberMediaRef(exerciseId)
    val uri = ref?.thumbUri ?: ref?.gifUri ?: return
    AsyncImage(
        model = uri,
        contentDescription = null,
        imageLoader = app.container.imageLoader,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(RoundedCornerShape(10.dp)),
    )
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

@Composable
fun ExerciseMediaDialog(exerciseId: String, title: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Brand.colors.surfaceHigh,
        title = { Text(title) },
        text = { ExerciseAnimation(exerciseId) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = Brand.colors.primary) } },
    )
}
