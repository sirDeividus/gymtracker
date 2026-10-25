package com.gymtrack.domain

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToLong

@Serializable
enum class WeightUnit(val symbol: String, val kgPerUnit: Double) {
    KG("kg", 1.0),
    LB("lb", 0.45359237),
    ;

    fun fromKg(kg: Double): Double = kg / kgPerUnit
    fun toKg(value: Double): Double = value * kgPerUnit
}

object Formatting {
    /** Número con coma decimal y sin ceros sobrantes: 42.5 → "42,5", 40.0 → "40". */
    fun number(value: Double, maxDecimals: Int = 1): String {
        // NaN/∞ (p. ej. la media de una lista vacía) no se pueden redondear: nunca deben tumbar la pantalla.
        if (value.isNaN() || value.isInfinite()) return "—"
        val factor = Math.pow(10.0, maxDecimals.toDouble())
        val rounded = (value * factor).roundToLong() / factor
        val text = if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
        return text.replace('.', ',')
    }

    fun weight(kg: Double, unit: WeightUnit, withSymbol: Boolean = true): String {
        val v = number(unit.fromKg(kg))
        return if (withSymbol) "$v ${unit.symbol}" else v
    }

    /** "+10 kg", "−20 kg" (asistencia) o "Peso corporal". */
    fun bodyweightLoad(kg: Double?, unit: WeightUnit): String = when {
        kg == null || abs(kg) < 1e-9 -> "Peso corporal"
        kg > 0 -> "+" + weight(kg, unit)
        else -> "−" + weight(-kg, unit) + " asist."
    }

    fun duration(totalSec: Int): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun distance(m: Double): String =
        if (m >= 1000) number(m / 1000.0, 2) + " km" else number(m, 0) + " m"

    /** Parsea texto del usuario ("42,5" o "42.5"). Devuelve null si no es válido o es negativo. */
    fun parseDecimal(text: String): Double? {
        val v = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return if (v.isNaN() || v.isInfinite() || v < 0) null else v
    }
}
