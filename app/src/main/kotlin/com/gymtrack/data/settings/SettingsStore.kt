package com.gymtrack.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gymtrack.domain.IncrementSettings
import com.gymtrack.domain.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Preferencias del usuario (entidad UserSettings del modelo). */
data class UserSettings(
    val unit: WeightUnit = WeightUnit.KG,
    val defaultRestSec: Int = 90,
    /** Descanso al terminar todas las series de un ejercicio y pasar al siguiente (0 = sin descanso). */
    val exerciseChangeRestSec: Int = 120,
    val autoRest: Boolean = true,
    val vibration: Boolean = true,
    val sound: Boolean = false,
    val backgroundAlert: Boolean = false,
    val bodyWeightKg: Double? = null,
    val increments: IncrementSettings = IncrementSettings(),
    /** Biblioteca ampliada (1.300+ ejercicios). */
    val extendedLibrary: Boolean = true,
    val datasetVersion: Int = 0,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val unit = stringPreferencesKey("unit")
        val rest = intPreferencesKey("default_rest_sec")
        val changeRest = intPreferencesKey("exercise_change_rest_sec")
        val autoRest = booleanPreferencesKey("auto_rest")
        val vibration = booleanPreferencesKey("vibration")
        val sound = booleanPreferencesKey("sound")
        val backgroundAlert = booleanPreferencesKey("background_alert")
        val bodyWeight = doublePreferencesKey("body_weight_kg")
        val incUpper = doublePreferencesKey("inc_upper")
        val incLower = doublePreferencesKey("inc_lower")
        val incDumbbell = doublePreferencesKey("inc_dumbbell")
        val incBarbell = doublePreferencesKey("inc_barbell")
        val incMachine = doublePreferencesKey("inc_machine")
        val incBodyweight = doublePreferencesKey("inc_bodyweight")
        val extendedLibrary = booleanPreferencesKey("extended_library")
        val datasetVersion = intPreferencesKey("dataset_version")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.dataStore.edit { prefs ->
            val new = transform(prefs.toSettings())
            prefs[Keys.unit] = new.unit.name
            prefs[Keys.rest] = new.defaultRestSec
            prefs[Keys.changeRest] = new.exerciseChangeRestSec
            prefs[Keys.autoRest] = new.autoRest
            prefs[Keys.vibration] = new.vibration
            prefs[Keys.sound] = new.sound
            prefs[Keys.backgroundAlert] = new.backgroundAlert
            if (new.bodyWeightKg != null) prefs[Keys.bodyWeight] = new.bodyWeightKg else prefs.remove(Keys.bodyWeight)
            prefs[Keys.incUpper] = new.increments.upperBodyKg
            prefs[Keys.incLower] = new.increments.lowerBodyKg
            prefs[Keys.incDumbbell] = new.increments.dumbbellKg
            prefs[Keys.incBarbell] = new.increments.barbellKg
            prefs[Keys.incMachine] = new.increments.machineKg
            prefs[Keys.incBodyweight] = new.increments.bodyweightKg
            prefs[Keys.extendedLibrary] = new.extendedLibrary
            prefs[Keys.datasetVersion] = new.datasetVersion
        }
    }

    /** Para la copia de seguridad: clave → valor en texto. */
    suspend fun exportMap(): Map<String, String> {
        val s = current()
        return buildMap {
            put("unit", s.unit.name)
            put("defaultRestSec", s.defaultRestSec.toString())
            put("exerciseChangeRestSec", s.exerciseChangeRestSec.toString())
            put("autoRest", s.autoRest.toString())
            put("vibration", s.vibration.toString())
            put("sound", s.sound.toString())
            s.bodyWeightKg?.let { put("bodyWeightKg", it.toString()) }
            put("incUpper", s.increments.upperBodyKg.toString())
            put("incLower", s.increments.lowerBodyKg.toString())
            put("incDumbbell", s.increments.dumbbellKg.toString())
            put("incBarbell", s.increments.barbellKg.toString())
            put("incMachine", s.increments.machineKg.toString())
            put("incBodyweight", s.increments.bodyweightKg.toString())
        }
    }

    suspend fun importMap(map: Map<String, String>) = update { cur ->
        val inc = cur.increments
        cur.copy(
            unit = map["unit"]?.let { n -> WeightUnit.values().firstOrNull { it.name == n } } ?: cur.unit,
            defaultRestSec = map["defaultRestSec"]?.toIntOrNull()?.takeIf { it in 5..1800 } ?: cur.defaultRestSec,
            exerciseChangeRestSec = map["exerciseChangeRestSec"]?.toIntOrNull()?.takeIf { it in 0..1800 } ?: cur.exerciseChangeRestSec,
            autoRest = map["autoRest"]?.toBooleanStrictOrNull() ?: cur.autoRest,
            vibration = map["vibration"]?.toBooleanStrictOrNull() ?: cur.vibration,
            sound = map["sound"]?.toBooleanStrictOrNull() ?: cur.sound,
            bodyWeightKg = map["bodyWeightKg"]?.toDoubleOrNull()?.takeIf { it in 20.0..500.0 } ?: cur.bodyWeightKg,
            increments = IncrementSettings(
                upperBodyKg = map["incUpper"].positive(inc.upperBodyKg),
                lowerBodyKg = map["incLower"].positive(inc.lowerBodyKg),
                dumbbellKg = map["incDumbbell"].positive(inc.dumbbellKg),
                barbellKg = map["incBarbell"].positive(inc.barbellKg),
                machineKg = map["incMachine"].positive(inc.machineKg),
                bodyweightKg = map["incBodyweight"].positive(inc.bodyweightKg),
            ),
        )
    }

    private fun String?.positive(default: Double): Double =
        this?.toDoubleOrNull()?.takeIf { it > 0 && it <= 100 } ?: default

    private fun Preferences.toSettings(): UserSettings {
        val d = IncrementSettings()
        return UserSettings(
            unit = this[Keys.unit]?.let { n -> WeightUnit.values().firstOrNull { it.name == n } } ?: WeightUnit.KG,
            defaultRestSec = this[Keys.rest] ?: 90,
            exerciseChangeRestSec = this[Keys.changeRest] ?: 120,
            autoRest = this[Keys.autoRest] ?: true,
            vibration = this[Keys.vibration] ?: true,
            sound = this[Keys.sound] ?: false,
            backgroundAlert = this[Keys.backgroundAlert] ?: false,
            bodyWeightKg = this[Keys.bodyWeight],
            increments = IncrementSettings(
                upperBodyKg = this[Keys.incUpper] ?: d.upperBodyKg,
                lowerBodyKg = this[Keys.incLower] ?: d.lowerBodyKg,
                dumbbellKg = this[Keys.incDumbbell] ?: d.dumbbellKg,
                barbellKg = this[Keys.incBarbell] ?: d.barbellKg,
                machineKg = this[Keys.incMachine] ?: d.machineKg,
                bodyweightKg = this[Keys.incBodyweight] ?: d.bodyweightKg,
            ),
            extendedLibrary = this[Keys.extendedLibrary] ?: true,
            datasetVersion = this[Keys.datasetVersion] ?: 0,
        )
    }
}
