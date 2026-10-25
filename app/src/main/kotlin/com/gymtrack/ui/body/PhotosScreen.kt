package com.gymtrack.ui.body

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gymtrack.GymTrackApp
import com.gymtrack.data.db.ProgressPhotoEntity
import com.gymtrack.domain.BodyWeightStats
import com.gymtrack.domain.Formatting
import com.gymtrack.domain.PhotoComparer
import com.gymtrack.domain.PhotoPose
import com.gymtrack.domain.WeightUnit
import com.gymtrack.ui.components.ChipSelector
import com.gymtrack.ui.components.Dates
import com.gymtrack.ui.components.EmptyState
import com.gymtrack.ui.components.GradientCapsuleButton
import com.gymtrack.ui.components.GymCard
import com.gymtrack.ui.components.LineChart
import com.gymtrack.ui.components.NumberInput
import com.gymtrack.ui.components.ScreenHeader
import com.gymtrack.ui.components.ScreenPadding
import com.gymtrack.ui.components.SecondaryButton
import com.gymtrack.ui.components.SectionTitle
import com.gymtrack.ui.theme.Brand
import java.io.File

@Composable
fun PhotosScreen(vm: BodyViewModel, contentPadding: PaddingValues, onBack: () -> Unit) {
    val c = Brand.colors
    val st by vm.state.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    val unit = st.settings.unit
    var adding by remember { mutableStateOf(false) }
    var logWeight by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ProgressPhotoEntity?>(null) }
    var comparing by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<List<String>>(emptyList()) }
    var showCompare by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        ScreenHeader("Fotos y peso", onBack = onBack, subtitle = "Guardado solo en tu teléfono")
        if (st.loading) return@Column
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(3) }) {
                WeightCard(st.weights.map { it.measuredAt to it.weightKg }, unit, onLog = { logWeight = true })
            }
            item(span = { GridItemSpan(3) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Fotos de progreso")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GradientCapsuleButton("AÑADIR FOTO", { adding = true }, Modifier.weight(1f), icon = Icons.Default.AddAPhoto)
                        if (st.photos.size >= 2) {
                            SecondaryButton(
                                if (comparing) "CANCELAR" else "COMPARAR",
                                { comparing = !comparing; picked = emptyList() },
                                Modifier.weight(1f), icon = Icons.Default.CompareArrows,
                            )
                        }
                    }
                    if (comparing) {
                        Text(
                            "Elige dos fotos (${picked.size}/2)",
                            style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                        )
                    }
                }
            }
            if (st.photos.isEmpty()) {
                item(span = { GridItemSpan(3) }) {
                    EmptyState(
                        Icons.Default.PhotoLibrary, "Aún no hay fotos",
                        "Haz una foto cada pocas semanas con la misma luz y pose para ver tus cambios. Se guardan privadas en tu teléfono y no se suben a ningún sitio.",
                    )
                }
            } else {
                items(st.photos, key = { it.id }) { p ->
                    val selected = p.id in picked
                    PhotoTile(vm.fileOf(p), p, selected) {
                        if (comparing) {
                            picked = when {
                                p.id in picked -> picked - p.id
                                picked.size < 2 -> picked + p.id
                                else -> picked
                            }
                            if (picked.size == 2) showCompare = true
                        } else detail = p
                    }
                }
            }
        }
    }

    if (adding) {
        AddPhotoDialog(
            unit = unit,
            onDismiss = { adding = false },
            onSave = { open, meta -> vm.addPhoto(open, meta) { adding = false } },
            onError = { vm.setMessage(it) },
        )
    }
    if (logWeight) {
        LogWeightDialog(unit, onDismiss = { logWeight = false }, onSave = { kg -> vm.addWeight(kg); logWeight = false })
    }
    detail?.let { p ->
        PhotoDetailDialog(vm.fileOf(p), p, unit, onDismiss = { detail = null }, onDelete = { vm.deletePhoto(p.id); detail = null })
    }
    if (showCompare && picked.size == 2) {
        val a = st.photos.firstOrNull { it.id == picked[0] }
        val b = st.photos.firstOrNull { it.id == picked[1] }
        if (a != null && b != null) {
            CompareDialog(vm, a, b, unit) { showCompare = false; comparing = false; picked = emptyList() }
        } else { showCompare = false; picked = emptyList() }
    }
    msg?.let {
        AlertDialog(
            onDismissRequest = { vm.setMessage(null) },
            confirmButton = { TextButton({ vm.setMessage(null) }) { Text("Entendido") } },
            title = { Text("Aviso") }, text = { Text(it) },
            containerColor = c.surfaceHigh,
        )
    }
}

@Composable
private fun WeightCard(entries: List<Pair<Long, Double>>, unit: WeightUnit, onLog: () -> Unit) {
    val c = Brand.colors
    val trend = BodyWeightStats.trend(entries)
    GymCard(Modifier.fillMaxWidth()) {
        SectionTitle("Peso corporal")
        if (trend == null) {
            Text("Registra tu peso para ver la tendencia.", style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        } else {
            Text(Formatting.weight(trend.latestKg, unit), style = MaterialTheme.typography.headlineMedium)
            if (trend.entries > 1) {
                val d = trend.deltaKg
                val sign = if (d > 0) "+" else ""
                Text(
                    "$sign${Formatting.weight(d, unit)} desde el primer registro",
                    style = MaterialTheme.typography.bodySmall, color = c.textMuted,
                )
                val sorted = entries.sortedBy { it.first }
                LineChart(
                    sorted.map { it.second }, Modifier.padding(top = 12.dp),
                    description = "Evolución del peso corporal",
                    formatValue = { Formatting.weight(it, unit) },
                    startLabel = Dates.day(sorted.first().first),
                    endLabel = Dates.day(sorted.last().first),
                )
            }
        }
        SecondaryButton("REGISTRAR PESO", onLog, Modifier.padding(top = 12.dp), icon = Icons.Default.Scale)
    }
}

@Composable
private fun PhotoTile(file: File, p: ProgressPhotoEntity, selected: Boolean, onClick: () -> Unit) {
    val c = Brand.colors
    val app = LocalContext.current.applicationContext as GymTrackApp
    val pose = runCatching { PhotoPose.valueOf(p.pose) }.getOrDefault(PhotoPose.OTHER)
    Box(
        Modifier.aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)).background(c.surfaceHigh)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = file, imageLoader = app.container.imageLoader,
            contentDescription = "Foto ${pose.label} del ${Dates.dayYear(p.takenAt)}",
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(c.background.copy(alpha = 0.7f)).padding(6.dp),
        ) {
            Text(Dates.day(p.takenAt), style = MaterialTheme.typography.labelSmall)
            Text(pose.label, style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        }
        if (selected) Box(Modifier.fillMaxSize().background(c.primary.copy(alpha = 0.35f)))
    }
}

@Composable
private fun AddPhotoDialog(
    unit: WeightUnit,
    onDismiss: () -> Unit,
    onSave: (open: () -> java.io.InputStream?, PhotoMeta) -> Unit,
    onError: (String) -> Unit,
) {
    val c = Brand.colors
    val context = LocalContext.current
    var pose by remember { mutableStateOf(PhotoPose.FRONT) }
    var weight by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var picked by remember { mutableStateOf<Uri?>(null) }

    fun meta(): PhotoMeta? {
        val w = weight.replace(',', '.').toDoubleOrNull()
        val kg = w?.let { it * unit.kgPerUnit }
        if (weight.isNotBlank() && (kg == null || !BodyWeightStats.isValid(kg))) {
            onError("Peso no válido"); return null
        }
        return PhotoMeta(pose, kg, note.trim())
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) {
            val m = meta() ?: return@rememberLauncherForActivityResult
            onSave({ runCatching { f.inputStream() }.getOrNull() }, m)
        } else if (f != null) f.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> picked = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text("Nueva foto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ChipSelector(PhotoPose.values().toList(), setOf(pose), { it.label }, { pose = it })
                NumberInput(weight, "Peso (${unit.symbol}, opcional)", { weight = it })
                NumberInput(note, "Nota (opcional)", { note = it }, decimal = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("CÁMARA", {
                        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                        val f = File.createTempFile("shot_", ".jpg", dir)
                        cameraFile = f
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                        runCatching { camera.launch(uri) }.onFailure { onError("No se pudo abrir la cámara") }
                    }, Modifier.weight(1f), icon = Icons.Default.AddAPhoto)
                    SecondaryButton("GALERÍA", {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, Modifier.weight(1f), icon = Icons.Default.PhotoLibrary)
                }
                picked?.let { Text("Imagen seleccionada ✓", color = c.primary, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val uri = picked ?: return@TextButton
                    val m = meta() ?: return@TextButton
                    onSave({ context.contentResolver.openInputStream(uri) }, m)
                },
                enabled = picked != null,
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun LogWeightDialog(unit: WeightUnit, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    val v = text.replace(',', '.').toDoubleOrNull()
    val kg = v?.let { it * unit.kgPerUnit }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Brand.colors.surfaceHigh,
        title = { Text("Registrar peso") },
        text = { NumberInput(text, unit.symbol, { text = it }) },
        confirmButton = {
            TextButton({ kg?.let(onSave) }, enabled = kg != null && BodyWeightStats.isValid(kg)) { Text("Guardar") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PhotoDetailDialog(file: File, p: ProgressPhotoEntity, unit: WeightUnit, onDismiss: () -> Unit, onDelete: () -> Unit) {
    val c = Brand.colors
    val app = LocalContext.current.applicationContext as GymTrackApp
    var confirm by remember { mutableStateOf(false) }
    val pose = runCatching { PhotoPose.valueOf(p.pose) }.getOrDefault(PhotoPose.OTHER)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text("${pose.label} · ${Dates.dayYear(p.takenAt)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AsyncImage(
                    model = file, imageLoader = app.container.imageLoader,
                    contentDescription = "Foto de progreso",
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().aspectRatio(0.75f),
                )
                p.weightKg?.let { Text(Formatting.weight(it, unit), color = c.textMuted) }
                if (p.note.isNotBlank()) Text(p.note, color = c.textMuted)
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cerrar") } },
        dismissButton = { TextButton({ confirm = true }) { Text("Eliminar", color = c.error) } },
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = c.surfaceHigh,
            title = { Text("¿Eliminar foto?") },
            text = { Text("Se borrará del teléfono y no se puede recuperar.") },
            confirmButton = { TextButton(onDelete) { Text("Eliminar", color = c.error) } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CompareDialog(vm: BodyViewModel, x: ProgressPhotoEntity, y: ProgressPhotoEntity, unit: WeightUnit, onDismiss: () -> Unit) {
    val c = Brand.colors
    val app = LocalContext.current.applicationContext as GymTrackApp
    val (swap, cmp) = PhotoComparer.compare(x.takenAt, x.weightKg, y.takenAt, y.weightKg, Dates.zone)
    val before = if (swap) y else x
    val after = if (swap) x else y
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceHigh,
        title = { Text("Antes y después") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(before to "ANTES", after to "DESPUÉS").forEach { (p, label) ->
                        Column(Modifier.weight(1f)) {
                            AsyncImage(
                                model = vm.fileOf(p), imageLoader = app.container.imageLoader,
                                contentDescription = "$label: ${Dates.dayYear(p.takenAt)}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)),
                            )
                            Text(label, style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                            Text(Dates.dayYear(p.takenAt), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text("${cmp.days} días de diferencia", color = c.textMuted)
                cmp.weightDeltaKg?.let {
                    val sign = if (it > 0) "+" else ""
                    Text("Peso: $sign${Formatting.weight(it, unit)}", color = c.textMuted)
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cerrar") } },
    )
}
