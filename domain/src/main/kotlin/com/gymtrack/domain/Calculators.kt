package com.gymtrack.domain

import kotlin.math.floor
import kotlin.math.roundToInt

data class WarmupSet(val weightKg: Double, val reps: Int)

/** Genera una progresión de calentamiento a partir de la carga de trabajo. Es una propuesta editable. */
object WarmupCalculator {
    private val scheme = listOf(0.4 to 8, 0.6 to 5, 0.8 to 3)

    /**
     * @param workingKg carga de la primera serie de trabajo.
     * @param stepKg incremento mínimo del equipamiento (se redondea a múltiplos de este paso).
     * @param minKg carga mínima útil (p. ej. la barra); las series por debajo se omiten.
     */
    fun generate(workingKg: Double, stepKg: Double, minKg: Double = 0.0): List<WarmupSet> {
        if (workingKg <= 0 || stepKg <= 0) return emptyList()
        val out = mutableListOf<WarmupSet>()
        for ((pct, reps) in scheme) {
            val rounded = (workingKg * pct / stepKg).roundToInt() * stepKg
            if (rounded < minKg || rounded <= 0) continue
            if (rounded >= workingKg * 0.95) continue // sería casi igual a la serie de trabajo
            if (out.any { it.weightKg == rounded }) continue
            out += WarmupSet(rounded, reps)
        }
        return out
    }
}

data class PlatePlan(
    /** Pares (disco, cantidad) por lado, de mayor a menor. */
    val perSide: List<Pair<Double, Int>>,
    val barWeight: Double,
    /** Carga realmente alcanzable con los discos disponibles. */
    val achievable: Double,
    /** Lo que falta para llegar al objetivo (≥ 0). */
    val remainder: Double,
)

/** Discos por lado para una barra. Todo en la unidad del usuario (kg o lb). */
object PlateCalculator {
    data class PlateSet(val bar: Double, val plates: List<Double>)

    fun plateSetFor(unit: WeightUnit): PlateSet = when (unit) {
        WeightUnit.KG -> PlateSet(20.0, listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25))
        WeightUnit.LB -> PlateSet(45.0, listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5))
    }

    fun plan(target: Double, set: PlateSet): PlatePlan {
        val perSideTarget = (target - set.bar) / 2.0
        if (perSideTarget <= 1e-9) return PlatePlan(emptyList(), set.bar, set.bar, (target - set.bar).coerceAtLeast(0.0).let { 0.0 })
        var left = perSideTarget
        val out = mutableListOf<Pair<Double, Int>>()
        for (p in set.plates.sortedDescending()) {
            val n = floor((left + 1e-9) / p).toInt()
            if (n > 0) {
                out += p to n
                left -= n * p
            }
        }
        val achievable = set.bar + 2 * (perSideTarget - left)
        return PlatePlan(out, set.bar, achievable, (target - achievable).coerceAtLeast(0.0).let { if (it < 1e-6) 0.0 else it })
    }
}
