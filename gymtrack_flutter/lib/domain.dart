// Lógica de entrenamiento (puerto de `domain/` en Kotlin y de web/js/domain.js).
import 'dart:math' as math;

import 'models.dart';

bool isWorking(SetRec s) => s.type != 'W';

double epley1RM(double w, int r) => (w > 0 && r > 0) ? (r == 1 ? w : w * (1 + r / 30)) : 0;

double exerciseVolume(SessionExercise e) => e.sets
    .where((s) => s.completed && isWorking(s))
    .fold(0.0, (a, s) => a + math.max(0, s.weightKg ?? 0) * (s.reps ?? 0));

double sessionVolume(Session s) => s.exercises.fold(0.0, (a, e) => a + exerciseVolume(e));

int completedSets(Session s) => s.exercises.fold(0, (a, e) => a + e.sets.where((x) => x.completed && isWorking(x)).length);

class Bests {
  final double weight, e1rm;
  final int reps;
  const Bests(this.weight, this.e1rm, this.reps);
}

Bests bestOf(SessionExercise e) {
  double w = 0, r1 = 0;
  int reps = 0;
  for (final s in e.sets) {
    if (!s.completed || !isWorking(s)) continue;
    w = math.max(w, s.weightKg ?? 0);
    r1 = math.max(r1, epley1RM(s.weightKg ?? 0, s.reps ?? 0));
    reps = math.max(reps, s.reps ?? 0);
  }
  return Bests(w, r1, reps);
}

/// Última sesión finalizada (más reciente primero) que contiene el ejercicio.
SessionExercise? lastPerformance(List<Session> sessions, String exerciseId, {String? beforeId}) {
  final list = sessions.where((s) => s.status == 'finished' && s.id != beforeId).toList()
    ..sort((a, b) => b.startedAt.compareTo(a.startedAt));
  for (final se in list) {
    for (final e in se.exercises) {
      if (e.exerciseId == exerciseId && e.sets.any((s) => s.completed && isWorking(s))) return e;
    }
  }
  return null;
}

const _lower = {'QUADS', 'HAMSTRINGS', 'GLUTES', 'CALVES'};

double stepFor(String equipment, String muscle) {
  switch (equipment) {
    case 'DUMBBELL':
      return 2;
    case 'BARBELL':
      return _lower.contains(muscle) ? 5 : 2.5;
    default:
      return 2.5;
  }
}

class Suggestion {
  final String kind; // first | up | hold
  final double? weightKg;
  final String text;
  const Suggestion(this.kind, this.text, [this.weightKg]);
}

/// Sugerencia de progresión: nunca automática; solo se muestra y el usuario decide.
Suggestion? suggest(SessionExercise? prev, String tracking, int repMax, double step) {
  if (prev == null) return const Suggestion('first', 'Primera vez · introduce una carga inicial');
  final work = prev.sets.where((s) => s.completed && isWorking(s)).toList();
  if (work.isEmpty) return null;
  final top = work.map((s) => s.weightKg ?? 0).reduce(math.max);
  if (tracking != 'WEIGHT_REPS' || top <= 0) return null;
  final atTop = work.where((s) => (s.weightKg ?? 0) == top).toList();
  final allHit = atTop.length == work.length && atTop.every((s) => (s.reps ?? 0) >= repMax);
  final hardRpe = work.any((s) => (s.rpe ?? 0) >= 9);
  String n(double v) => v == v.roundToDouble() ? v.toInt().toString() : v.toString();
  if (allHit && !hardRpe) {
    return Suggestion('up', 'Completaste ${work.length}×$repMax con ${n(top)} kg. Si el esfuerzo fue adecuado, prueba ${n(top + step)} kg.', top + step);
  }
  return Suggestion('hold', 'Mantén ${n(top)} kg y busca llegar a $repMax repeticiones en todas las series.');
}

DateTime _weekStart(DateTime d) {
  final day = DateTime(d.year, d.month, d.day);
  return day.subtract(Duration(days: (day.weekday - 1) % 7));
}

int weekStreak(List<Session> sessions, DateTime now) {
  final weeks = sessions.where((s) => s.status == 'finished').map((s) => _weekStart(DateTime.fromMillisecondsSinceEpoch(s.startedAt))).toSet();
  var streak = 0;
  var w = _weekStart(now);
  if (!weeks.contains(w)) w = w.subtract(const Duration(days: 7));
  while (weeks.contains(w)) {
    streak++;
    w = w.subtract(const Duration(days: 7));
  }
  return streak;
}

DateTime weekStartOf(DateTime d) => _weekStart(d);

class WarmupSet {
  final double weightKg;
  final int reps;
  const WarmupSet(this.weightKg, this.reps);
}

List<WarmupSet> warmupSets(double? workKg) {
  if (workKg == null || workKg < 20) return const [];
  WarmupSet mk(double pct, int reps) => WarmupSet((workKg * pct / 2.5).round() * 2.5, reps);
  return [mk(0.4, 8), mk(0.6, 5), mk(0.8, 3)];
}

/// Discos por lado para una barra; devuelve (lista de "disco×n", faltante total).
({List<String> plates, double remainder}) platePlan(double target, {required bool lb}) {
  final plates = lb ? [45.0, 35, 25, 10, 5, 2.5] : [25.0, 20, 15, 10, 5, 2.5, 1.25];
  final bar = lb ? 45.0 : 20.0;
  var rem = (target - bar) / 2;
  final out = <String>[];
  if (rem > 0) {
    for (final p in plates) {
      final n = (rem / p + 1e-9).floor();
      if (n > 0) {
        out.add('${p == p.roundToDouble() ? p.toInt() : p} × $n');
        rem -= n * p;
      }
    }
  }
  return (plates: out, remainder: rem > 0.01 ? rem * 2 : 0);
}

// ---------------------------------------------------------------- Intervalos / Tabata

enum Phase { prep, work, rest, cycleRest, done }

class IntervalPlan {
  final int workSec, restSec, rounds, prepSec, cycles, cycleRestSec;
  const IntervalPlan({
    required this.workSec,
    required this.restSec,
    required this.rounds,
    this.prepSec = 10,
    this.cycles = 1,
    this.cycleRestSec = 0,
  });

  int get totalRounds => rounds * cycles;

  /// Preparación + trabajo + descansos entre rondas + descansos entre ciclos (nada tras el último trabajo).
  int get totalSec => prepSec + totalRounds * workSec + (rounds - 1) * restSec * cycles + (cycles - 1) * cycleRestSec;

  IntervalPlan copyWith({int? workSec, int? restSec, int? rounds, int? prepSec, int? cycles, int? cycleRestSec}) => IntervalPlan(
        workSec: workSec ?? this.workSec,
        restSec: restSec ?? this.restSec,
        rounds: rounds ?? this.rounds,
        prepSec: prepSec ?? this.prepSec,
        cycles: cycles ?? this.cycles,
        cycleRestSec: cycleRestSec ?? this.cycleRestSec,
      );
}

class IntervalStep {
  final Phase phase;
  final int cycle, round, seconds, doneBefore;
  const IntervalStep(this.phase, this.cycle, this.round, this.seconds, this.doneBefore);
}

class IntervalSnapshot {
  final Phase phase;
  final int cycle, round, completed;
  final int remainingMs, totalMs, totalLeftMs;
  const IntervalSnapshot(this.phase, this.cycle, this.round, this.remainingMs, this.totalMs, this.completed, this.totalLeftMs);
  bool get finished => phase == Phase.done;
}

List<IntervalStep> _timeline(IntervalPlan p) {
  final out = <IntervalStep>[];
  var done = 0;
  for (var c = 1; c <= p.cycles; c++) {
    for (var r = 1; r <= p.rounds; r++) {
      out.add(IntervalStep(Phase.work, c, r, p.workSec, done));
      done++;
      final last = r == p.rounds;
      if (!last && p.restSec > 0) out.add(IntervalStep(Phase.rest, c, r, p.restSec, done));
      if (last && c < p.cycles && p.cycleRestSec > 0) out.add(IntervalStep(Phase.cycleRest, c, r, p.cycleRestSec, done));
    }
  }
  return out;
}

/// Todos los tramos en orden, empezando por la preparación (si la hay).
List<IntervalStep> intervalSchedule(IntervalPlan p) => [
      if (p.prepSec > 0) IntervalStep(Phase.prep, 0, 0, p.prepSec, 0),
      ..._timeline(p),
    ];

/// Cálculo determinista a partir del tiempo transcurrido: no acumula errores y soporta pausas.
IntervalSnapshot intervalSnapshot(IntervalPlan p, int elapsedMs) {
  final e = math.max(0, elapsedMs);
  final prep = p.prepSec * 1000;
  final totalMs = p.totalSec * 1000;
  if (e < prep) return IntervalSnapshot(Phase.prep, 0, 0, prep - e, prep, 0, totalMs - e);
  var t = e - prep;
  for (final s in _timeline(p)) {
    final ms = s.seconds * 1000;
    if (t < ms) return IntervalSnapshot(s.phase, s.cycle, s.round, ms - t, ms, s.doneBefore, math.max(0, totalMs - e));
    t -= ms;
  }
  return IntervalSnapshot(Phase.done, p.cycles, p.rounds, 0, 0, p.totalRounds, 0);
}

/// Fase del tramo siguiente al actual (done si es el último).
Phase nextPhase(IntervalPlan p, int elapsedMs) {
  final e = math.max(0, elapsedMs);
  final prep = p.prepSec * 1000;
  final steps = _timeline(p);
  if (e < prep) return steps.first.phase;
  var t = e - prep;
  for (var i = 0; i < steps.length; i++) {
    final ms = steps[i].seconds * 1000;
    if (t < ms) return i + 1 < steps.length ? steps[i + 1].phase : Phase.done;
    t -= ms;
  }
  return Phase.done;
}
