package com.gymtrack.data.settings

import android.content.Context

/**
 * Ajustes del temporizador de intervalos que no viven en la rutina: ciclos, descanso entre ciclos y cuenta atrás,
 * recordados por ejercicio (trabajo, descanso y rondas salen de las series del propio ejercicio).
 * No forman parte de la copia de seguridad JSON.
 */
object IntervalPrefs {
    data class Config(val cycles: Int, val cycleRestSec: Int, val prepSec: Int)

    private fun prefs(context: Context) = context.getSharedPreferences("intervals", Context.MODE_PRIVATE)

    fun get(context: Context, exerciseId: String): Config {
        val p = prefs(context)
        return Config(
            cycles = p.getInt("cycles_$exerciseId", 1).coerceIn(1, 30),
            cycleRestSec = p.getInt("cycleRest_$exerciseId", 30).coerceIn(0, 3600),
            prepSec = p.getInt("prep_$exerciseId", 10).coerceIn(0, 60),
        )
    }

    fun set(context: Context, exerciseId: String, config: Config) {
        prefs(context).edit()
            .putInt("cycles_$exerciseId", config.cycles.coerceIn(1, 30))
            .putInt("cycleRest_$exerciseId", config.cycleRestSec.coerceIn(0, 3600))
            .putInt("prep_$exerciseId", config.prepSec.coerceIn(0, 60))
            .apply()
    }
}
