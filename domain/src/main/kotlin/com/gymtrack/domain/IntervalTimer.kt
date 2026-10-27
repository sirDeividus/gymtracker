package com.gymtrack.domain

/**
 * Plan de intervalos: trabajo/descanso × rondas por ciclo × ciclos, con cuenta atrás inicial.
 * Tabata clásico = 20 s / 10 s × 8 rondas × 1 ciclo. Entre ciclos puede haber un descanso propio ([cycleRestSec]).
 */
data class IntervalPlan(
    val workSec: Int,
    val restSec: Int,
    /** Rondas (repeticiones de trabajo) por ciclo. */
    val rounds: Int,
    val prepSec: Int = DEFAULT_PREP_SEC,
    val cycles: Int = 1,
    /** Descanso entre un ciclo y el siguiente (no hay tras el último). */
    val cycleRestSec: Int = 0,
) {
    init {
        require(workSec in 1..3600) { "Trabajo fuera de rango: $workSec" }
        require(restSec in 0..3600) { "Descanso fuera de rango: $restSec" }
        require(rounds in 1..MAX_ROUNDS) { "Rondas fuera de rango: $rounds" }
        require(prepSec in 0..60) { "Preparación fuera de rango: $prepSec" }
        require(cycles in 1..MAX_CYCLES) { "Ciclos fuera de rango: $cycles" }
        require(cycleRestSec in 0..3600) { "Descanso entre ciclos fuera de rango: $cycleRestSec" }
    }

    /** Rondas de trabajo en total (rondas × ciclos). */
    val totalRounds: Int get() = rounds * cycles

    /** Duración total: preparación + trabajo + descansos entre rondas + descansos entre ciclos (nada tras el último trabajo). */
    val totalSec: Int
        get() = prepSec + totalRounds * workSec + (rounds - 1) * restSec * cycles + (cycles - 1) * cycleRestSec

    companion object {
        const val DEFAULT_PREP_SEC = 5
        const val MAX_ROUNDS = 60
        const val MAX_CYCLES = 30
        val TABATA = IntervalPlan(workSec = 20, restSec = 10, rounds = 8)
    }
}

enum class IntervalPhase { PREP, WORK, REST, CYCLE_REST, DONE }

data class IntervalSnapshot(
    val phase: IntervalPhase,
    /** Ronda actual dentro del ciclo (1..rounds); 0 durante la preparación. */
    val round: Int,
    val phaseRemainingMs: Long,
    val phaseTotalMs: Long,
    /** Rondas de trabajo ya terminadas (en total, sumando ciclos). */
    val completedRounds: Int,
    val totalRemainingMs: Long,
    /** Ciclo actual (1..cycles); 0 durante la preparación. */
    val cycle: Int = 0,
) {
    val finished: Boolean get() = phase == IntervalPhase.DONE
}

/** Cálculo determinista a partir del tiempo transcurrido: no acumula errores y soporta pausas. */
object IntervalTimer {
    private class Segment(val phase: IntervalPhase, val cycle: Int, val round: Int, val ms: Long, val doneBefore: Int)

    private fun timeline(plan: IntervalPlan): List<Segment> = buildList {
        var done = 0
        for (c in 1..plan.cycles) {
            for (r in 1..plan.rounds) {
                add(Segment(IntervalPhase.WORK, c, r, plan.workSec * 1000L, done))
                done++
                val lastOfCycle = r == plan.rounds
                if (!lastOfCycle && plan.restSec > 0) add(Segment(IntervalPhase.REST, c, r, plan.restSec * 1000L, done))
                if (lastOfCycle && c < plan.cycles && plan.cycleRestSec > 0) {
                    add(Segment(IntervalPhase.CYCLE_REST, c, r, plan.cycleRestSec * 1000L, done))
                }
            }
        }
    }

    fun snapshot(plan: IntervalPlan, elapsedMs: Long): IntervalSnapshot {
        val e = elapsedMs.coerceAtLeast(0)
        val prep = plan.prepSec * 1000L
        val total = plan.totalSec * 1000L
        val remainingTotal = (total - e).coerceAtLeast(0)
        if (e < prep) return IntervalSnapshot(IntervalPhase.PREP, 0, prep - e, prep, 0, remainingTotal, 0)

        var t = e - prep
        for (s in timeline(plan)) {
            if (t < s.ms) {
                return IntervalSnapshot(s.phase, s.round, s.ms - t, s.ms, s.doneBefore, remainingTotal, s.cycle)
            }
            t -= s.ms
        }
        return IntervalSnapshot(IntervalPhase.DONE, plan.rounds, 0, 0, plan.totalRounds, 0, plan.cycles)
    }
}
