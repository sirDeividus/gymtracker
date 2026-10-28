package com.gymtrack.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtrack.BuildConfig
import com.gymtrack.R
import com.gymtrack.domain.Formatting
import com.gymtrack.domain.WeightUnit
import com.gymtrack.ui.components.ChipSelector
import com.gymtrack.ui.components.ConfirmDialog
import com.gymtrack.ui.components.Dates
import com.gymtrack.ui.components.GymCard
import com.gymtrack.ui.components.gymSwitchColors
import com.gymtrack.ui.components.NumberInput
import com.gymtrack.ui.components.ScreenHeader
import com.gymtrack.ui.components.ScreenPadding
import com.gymtrack.ui.components.SecondaryButton
import com.gymtrack.ui.components.SectionTitle
import com.gymtrack.ui.theme.Brand
import java.time.LocalDate

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val c = Brand.colors
    val context = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val pending by vm.pendingImport.collectAsStateWithLifecycle()
    val unit = s.unit
    var licenses by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::export) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readBackup) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setBackgroundAlert(granted) }

    Column(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding).imePadding()) {
        ScreenHeader("Ajustes", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                GymCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Unidades")
                    ChipSelector(WeightUnit.values().toList(), setOf(unit), { it.symbol }, { vm.setUnit(it) }, Modifier.padding(top = 6.dp))
                    Text("Los datos se guardan siempre en kg y se convierten al mostrarlos, así que puedes cambiar sin perder precisión.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
                }
            }
            item {
                GymCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Descanso")
                    Text("Descanso por defecto en ejercicios nuevos", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                    var restDialog by remember { mutableStateOf<String?>(null) }
                    ChipSelector(com.gymtrack.ui.components.restOptions(listOf(45, 60, 90, 120, 180, 240), s.defaultRestSec), setOf(s.defaultRestSec), { "${it} s" }, { vm.setRest(it) }, Modifier.padding(top = 6.dp))
                    TextButton(onClick = { restDialog = "default" }) { Text("Personalizado…", color = com.gymtrack.ui.theme.Brand.colors.primary) }
                    if (restDialog == "default") com.gymtrack.ui.components.RestTimeDialog("Descanso por defecto", s.defaultRestSec, { restDialog = null }, { vm.setRest(it); restDialog = null }, minSec = 5)
                    if (restDialog == "change") com.gymtrack.ui.components.RestTimeDialog("Descanso al cambiar de ejercicio", s.exerciseChangeRestSec, { restDialog = null }, { vm.setChangeRest(it); restDialog = null })
                    Text("Se usa cuando completas la última serie de un ejercicio y sigues con otro.", style = MaterialTheme.typography.bodySmall, color = com.gymtrack.ui.theme.Brand.colors.textSecondary, modifier = Modifier.padding(top = 10.dp))
                    Text("Descanso al cambiar de ejercicio", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                    ChipSelector(com.gymtrack.ui.components.restOptions(listOf(0, 60, 90, 120, 180, 240, 300), s.exerciseChangeRestSec), setOf(s.exerciseChangeRestSec), { if (it == 0) "Sin descanso" else "${it} s" }, { vm.setChangeRest(it) }, Modifier.padding(top = 6.dp))
                    TextButton(onClick = { restDialog = "change" }) { Text("Personalizado…", color = com.gymtrack.ui.theme.Brand.colors.primary) }
                    ToggleRow("Iniciar el descanso al completar una serie", s.autoRest, vm::setAutoRest)
                    ToggleRow("Vibración al terminar", s.vibration, vm::setVibration)
                    ToggleRow("Voz en los avisos (3, 2, 1 · Descanso · Prepárate · Go)", s.voice, vm::setVoice)
                    ToggleRow("Sonido al terminar", s.sound, vm::setSound)
                    ToggleRow("Controles de música en el entrenamiento", s.musicControls, vm::setMusicControls)
                    ToggleRow("Aviso con la app en segundo plano", s.backgroundAlert) { on ->
                        if (on && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setBackgroundAlert(on)
                    }
                    Text("El aviso en segundo plano usa una notificación; solo se pide permiso si activas esta opción. Android puede retrasarlo si tienes el ahorro de batería muy restrictivo.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            item {
                GymCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Peso corporal (opcional)")
                    var text by remember { mutableStateOf(s.bodyWeightKg?.let { Formatting.weight(it, unit, false) }.orEmpty()) }
                    NumberInput(text, "—", {
                        text = it
                        vm.setBodyWeight(if (it.isBlank()) null else Formatting.parseDecimal(it)?.let { v -> unit.toKg(v) })
                    }, modifier = Modifier.padding(top = 6.dp).fillMaxWidth(), description = "Peso corporal en ${unit.symbol}")
                    Text("Solo como referencia. Nunca se exige para registrar ejercicios de peso corporal.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
                }
            }
            item {
                GymCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Incrementos sugeridos (${unit.symbol})")
                    Text("Tamaño del salto al sugerir más carga. La app nunca sube el peso sola: solo lo propone.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(vertical = 6.dp))
                    val inc = s.increments
                    IncField("Tren superior", inc.upperBodyKg, unit) { v -> vm.setIncrements { it.copy(upperBodyKg = v) } }
                    IncField("Tren inferior", inc.lowerBodyKg, unit) { v -> vm.setIncrements { it.copy(lowerBodyKg = v) } }
                    IncField("Mancuernas", inc.dumbbellKg, unit) { v -> vm.setIncrements { it.copy(dumbbellKg = v) } }
                    IncField("Barra", inc.barbellKg, unit) { v -> vm.setIncrements { it.copy(barbellKg = v) } }
                    IncField("Máquinas y poleas", inc.machineKg, unit) { v -> vm.setIncrements { it.copy(machineKg = v) } }
                    IncField("Peso corporal (lastre / asistencia)", inc.bodyweightKg, unit) { v -> vm.setIncrements { it.copy(bodyweightKg = v) } }
                    SecondaryButton("Restablecer valores para ${unit.symbol}", { vm.resetIncrementsForUnit(unit) }, Modifier.padding(top = 8.dp))
                }
            }
            item {
                GymCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Biblioteca de ejercicios")
                    ToggleRow("Biblioteca ampliada (más de 1.300 ejercicios)", s.extendedLibrary, vm::setExtendedLibrary)
                    Text(
                        "Nombres en inglés con instrucciones en español. Al desactivarla se ocultan, pero tus rutinas e historial no cambian.",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                    Text(
                        if (vm.mediaAvailable) "Imágenes y GIF: instalados (${stringRes(R.string.media_attribution)})"
                        else "Imágenes y GIF: no instalados. Para uso personal, en Android Studio ejecuta Gradle ▸ gymtrack ▸ fetchExerciseMedia y vuelve a instalar la app (ver THIRD_PARTY_NOTICES.md).",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp),
                    )
                    SecondaryButton("Gestionar ejercicios", onOpenLibrary, Modifier.padding(top = 8.dp))
                }
            }
            item {
                GymCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Tus datos")
                    Text("Todo se guarda en este dispositivo y funciona sin conexión ni cuenta. Exporta una copia para tenerla a salvo o llevarla a otro móvil.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(vertical = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Exportar copia", { exportLauncher.launch("gymtrack-copia-${LocalDate.now()}.json") }, Modifier.weight(1f))
                        SecondaryButton("Importar copia", { importLauncher.launch(arrayOf("*/*")) }, Modifier.weight(1f))
                    }
                    Text("La copia incluye rutinas, ejercicios personalizados, historial finalizado y ajustes. Al importar se sustituyen los datos actuales (te pediré confirmación).", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
                }
            }
            item {
                GymCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Acerca de")
                    Text("${stringRes(R.string.app_name)} ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                    Text(stringRes(R.string.brand_tagline), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    SecondaryButton("Licencias y créditos", { licenses = true }, Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    message?.let {
        AlertDialog(
            onDismissRequest = vm::clearMessage, containerColor = c.surfaceHigh,
            title = { Text("Copia de seguridad") }, text = { Text(it) },
            confirmButton = { TextButton(onClick = vm::clearMessage) { Text("Aceptar", color = c.primary) } },
        )
    }
    pending?.let { p ->
        ConfirmDialog(
            title = "¿Sustituir tus datos?",
            message = "Copia del ${Dates.dayYear(p.summary.exportedAt)}: ${p.summary.routines} rutinas, ${p.summary.sessions} sesiones, ${p.summary.sets} series y ${p.summary.customExercises} ejercicios personalizados.\n\nSe sustituirán TODOS tus datos actuales (rutinas e historial). Si no estás seguro, cancela y exporta antes una copia de lo que tienes ahora.",
            confirmText = "Sustituir mis datos", destructive = true,
            onConfirm = vm::confirmImport, onDismiss = vm::cancelImport,
        )
    }
    if (licenses) LicensesDialog { licenses = false }
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = gymSwitchColors())
    }
}

@Composable
private fun IncField(label: String, valueKg: Double, unit: WeightUnit, onChange: (Double) -> Unit) {
    var text by remember(unit) { mutableStateOf(Formatting.number(unit.fromKg(valueKg), 2)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        NumberInput(text, "0", {
            text = it
            Formatting.parseDecimal(it)?.takeIf { v -> v > 0 && v <= 100 }?.let { v -> onChange(unit.toKg(v)) }
        }, modifier = Modifier.padding(start = 12.dp).fillMaxWidth(0.35f), description = "Incremento $label")
    }
}

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val c = Brand.colors
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surfaceHigh,
        title = { Text("Licencias y créditos") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Biblioteca ampliada", style = MaterialTheme.typography.titleMedium)
                Text("Datos de ejercicios de hasaneyldrm/exercises-dataset (https://github.com/hasaneyldrm/exercises-dataset), licencia MIT. Copyright (c) 2026 Hasan Emir Yıldırım.", style = MaterialTheme.typography.bodySmall)
                Text("Imágenes y animaciones", style = MaterialTheme.typography.titleMedium)
                Text("Si están instaladas, son propiedad de Gym visual (https://gymvisual.com/), no están cubiertas por la licencia MIT y se usan aquí solo de forma personal y no comercial, con su atribución. Sus condiciones: https://gymvisual.com/content/3-terms-and-conditions-of-use", style = MaterialTheme.typography.bodySmall)
                Text("Bibliotecas", style = MaterialTheme.typography.titleMedium)
                Text("AndroidX, Jetpack Compose, Room, DataStore, Navigation (Apache 2.0); Kotlin y kotlinx.serialization (Apache 2.0); Coil (Apache 2.0).", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar", color = c.primary) } },
    )
}
