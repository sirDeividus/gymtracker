package com.gymtrack.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtrack.AppContainer
import com.gymtrack.data.settings.UserSettings
import com.gymtrack.domain.BackupCodec
import com.gymtrack.domain.BackupParseResult
import com.gymtrack.domain.IncrementSettings
import com.gymtrack.domain.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<UserSettings> = c.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    /** Mensaje de resultado para mostrar al usuario (exportación, importación, errores). */
    val message = MutableStateFlow<String?>(null)

    /** Copia leída y validada, pendiente de que el usuario confirme que quiere sobrescribir. */
    val pendingImport = MutableStateFlow<BackupParseResult.Valid?>(null)

    val mediaAvailable: Boolean get() = c.media.available

    private fun update(f: (UserSettings) -> UserSettings) {
        viewModelScope.launch { c.settings.update(f) }
    }

    fun setUnit(u: WeightUnit) = update { it.copy(unit = u) }
    fun resetIncrementsForUnit(u: WeightUnit) = update { it.copy(increments = IncrementSettings.defaultsFor(u)) }
    fun setRest(sec: Int) = update { it.copy(defaultRestSec = sec.coerceIn(5, 1800)) }
    fun setChangeRest(sec: Int) = update { it.copy(exerciseChangeRestSec = sec.coerceIn(0, 1800)) }
    fun setAutoRest(on: Boolean) = update { it.copy(autoRest = on) }
    fun setVibration(on: Boolean) = update { it.copy(vibration = on) }
    fun setVoice(on: Boolean) = update { it.copy(voice = on) }
    fun setSound(on: Boolean) = update { it.copy(sound = on) }
    fun setBackgroundAlert(on: Boolean) = update { it.copy(backgroundAlert = on) }
    fun setBodyWeight(kg: Double?) = update { it.copy(bodyWeightKg = kg?.takeIf { v -> v in 20.0..500.0 }) }
    fun setIncrements(f: (IncrementSettings) -> IncrementSettings) = update { it.copy(increments = f(it.increments)) }

    fun setExtendedLibrary(on: Boolean) {
        viewModelScope.launch {
            c.settings.update { it.copy(extendedLibrary = on) }
            c.syncLibrary()
        }
    }

    // ------------------------------------------------------------ Copia de seguridad

    fun export(uri: Uri) {
        viewModelScope.launch {
            message.value = try {
                val json = c.backup.exportJson()
                withContext(Dispatchers.IO) {
                    c.context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error("No se pudo abrir el archivo de destino")
                }
                "Copia de seguridad guardada."
            } catch (e: Exception) {
                "No se pudo guardar la copia: ${e.message ?: "error desconocido"}"
            }
        }
    }

    fun readBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    c.context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("No se pudo abrir el archivo")
                }
                when (val r = BackupCodec.parse(text)) {
                    is BackupParseResult.Valid -> pendingImport.value = r
                    is BackupParseResult.Invalid -> message.value = "Copia no válida, no se ha modificado nada:\n• " + r.errors.take(5).joinToString("\n• ")
                }
            } catch (e: Exception) {
                message.value = "No se pudo leer el archivo: ${e.message ?: "error desconocido"}"
            }
        }
    }

    fun confirmImport() {
        val pending = pendingImport.value ?: return
        pendingImport.value = null
        viewModelScope.launch {
            if (c.workout.activeSession.first() != null) {
                message.value = "Hay un entrenamiento en curso. Finalízalo o descártalo antes de restaurar una copia."
                return@launch
            }
            message.value = try {
                c.backup.replaceAllWith(pending.file)
                "Copia restaurada: ${pending.summary.routines} rutinas, ${pending.summary.sessions} sesiones."
            } catch (e: Exception) {
                "No se pudo restaurar (no se ha modificado nada): ${e.message ?: "error desconocido"}"
            }
        }
    }

    fun cancelImport() { pendingImport.value = null }
    fun clearMessage() { message.value = null }
}
