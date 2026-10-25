package com.gymtrack.domain

/** Plan de intervalos: trabajo/descanso × rondas, con una breve cuenta atrás inicial. Tabata = 20 s / 10 s × 8. */
data class IntervalPlan(
    val workSec: Int,
    val restSec: Int,
    val rounds: Int,
    val prepSec: Int = DEFAULT_PREP_SEC,
) {
    init {
        require(workSec in 1..3600) { "Trabajo fuera de rango: $workSec" }
        require(restSec in 0..3600) { "Descanso fuera de rango: $restSec" }
        require(rounds in 1..SessionPlanner.MAX_SETS) { "Rondas fuera de rango: $rounds" }
        require(prepSec in 0..60) { "Preparación fuera de rango: $prepSec" }
    }

    /** Duración total: preparación + trabajo de cada ronda + descansos entre rondas (no tras la última). */
    val totalSec: Int get() = prepSec + rounds * workSec + (rounds - 1) * restSec

    companion object {
        const val DEFAULT_PREP_SEC = 5
        val TABATA = IntervalPlan(workSec = 20, restSec = 10, rounds = 8)
    }
}

enum class IntervalPhase { PREP, WORK, REST, DONE }

data class IntervalSnapshot(
    val phase: IntervalPhase,
    /** Ronda actual (1..rounds); 0 durante la preparación. */
    val round: Int,
    val phaseRemainingMs: Long,
    val phaseTotalMs: Long,
    /** Rondas de trabajo ya terminadas. */
    val completedRounds: Int,
    val totalRemainingMs: Long,
) {
    val finished: Boolean get() = phase == IntervalPhase.DONE
}

/** Cálculo determinista a partir del tiempo transcurrido: no acumula errores y soporta pausas. */
object IntervalTimer {
    fun snapshot(plan: IntervalPlan, elapsedMs: Long): IntervalSnapshot {
        val e = elapsedMs.coerceAtLeast(0)
        val prep = plan.prepSec * 1000L
        val work = plan.workSec * 1000L
        val rest = plan.restSec * 1000L
        val total = plan.totalSec * 1000L
        val remainingTotal = (total - e).coerceAtLeast(0)

        if (e < prep) {
            return IntervalSnapshot(IntervalPhase.PREP, 0, prep - e, prep, 0, remainingTotal)
        }
        val t = e - prep
        val cycle = work + rest
        val i = (t / cycle).toInt()
        if (i >= plan.rounds) {
            return IntervalSnapshot(IntervalPhase.DONE, plan.rounds, 0, 0, plan.rounds, 0)
        }
        val within = t % cycle
        return if (within < work) {
            IntervalSnapshot(IntervalPhase.WORK, i + 1, work - within, work, i, remainingTotal)
        } else if (i == plan.rounds - 1) {
            // Tras la última ronda de trabajo no hay descanso.
            IntervalSnapshot(IntervalPhase.DONE, plan.rounds, 0, 0, plan.rounds, 0)
        } else {
            IntervalSnapshot(IntervalPhase.REST, i + 1, cycle - within, rest, i + 1, remainingTotal)
        }
    }
}
