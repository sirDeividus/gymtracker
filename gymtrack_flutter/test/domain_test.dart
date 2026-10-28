import 'package:flutter_test/flutter_test.dart';
import 'package:gymtrack/domain.dart';
import 'package:gymtrack/models.dart';

void main() {
  group('intervalos', () {
    const tabata = IntervalPlan(workSec: 20, restSec: 10, rounds: 8, prepSec: 5);

    test('tabata dura 235 s', () => expect(tabata.totalSec, 235));

    test('fases básicas', () {
      expect(intervalSnapshot(tabata, 0).phase, Phase.prep);
      final w = intervalSnapshot(tabata, 5000);
      expect(w.phase, Phase.work);
      expect(w.round, 1);
      final r = intervalSnapshot(tabata, 25000);
      expect(r.phase, Phase.rest);
      expect(r.completed, 1);
      expect(intervalSnapshot(tabata, 235000).phase, Phase.done);
      expect(intervalSnapshot(tabata, 235000).completed, 8);
    });

    test('varios ciclos con descanso entre ciclos', () {
      const p = IntervalPlan(workSec: 10, restSec: 5, rounds: 2, prepSec: 3, cycles: 2, cycleRestSec: 20);
      expect(p.totalRounds, 4);
      expect(p.totalSec, 3 + 40 + 10 + 20);
      expect(intervalSnapshot(p, 28000).phase, Phase.cycleRest);
      final c2 = intervalSnapshot(p, 48000);
      expect(c2.phase, Phase.work);
      expect(c2.cycle, 2);
      expect(c2.completed, 2);
      expect(intervalSnapshot(p, p.totalSec * 1000).completed, 4);
    });

    test('lista de tramos y fase siguiente', () {
      const p = IntervalPlan(workSec: 10, restSec: 5, rounds: 2, prepSec: 3, cycles: 2, cycleRestSec: 20);
      final steps = intervalSchedule(p);
      expect(steps.map((s) => s.phase).toList(),
          [Phase.prep, Phase.work, Phase.rest, Phase.work, Phase.cycleRest, Phase.work, Phase.rest, Phase.work]);
      expect(steps.fold<int>(0, (a, s) => a + s.seconds), p.totalSec);
      expect(nextPhase(p, 0), Phase.work);
      expect(nextPhase(p, 5000), Phase.rest);
      expect(nextPhase(p, 20000), Phase.cycleRest);
      expect(nextPhase(p, p.totalSec * 1000 - 1), Phase.done);
    });

    test('completadas nunca decrecen', () {
      var prev = 0;
      for (var ms = 0; ms <= 240000; ms += 100) {
        final c = intervalSnapshot(tabata, ms).completed;
        expect(c >= prev, isTrue);
        prev = c;
      }
      expect(prev, 8);
    });
  });

  group('entrenamiento', () {
    SetRec s(double w, int r, {String type = 'N', bool done = true}) => SetRec(weightKg: w, reps: r, type: type, completed: done);

    test('calentamientos no cuentan en volumen ni récords', () {
      final e = SessionExercise(exerciseId: 'x', name: 'x', tracking: 'WEIGHT_REPS', muscle: 'CHEST', equipment: 'BARBELL', sets: [
        s(40, 8, type: 'W'),
        s(100, 5),
        s(100, 5, done: false),
      ]);
      expect(exerciseVolume(e), 500);
      expect(bestOf(e).weight, 100);
    });

    test('Epley', () {
      expect(epley1RM(100, 1), 100);
      expect(epley1RM(100, 10), closeTo(133.33, 0.01));
    });

    test('sugerencia sube carga si se cumplieron todas las repeticiones', () {
      final prev = SessionExercise(exerciseId: 'x', name: 'x', tracking: 'WEIGHT_REPS', muscle: 'CHEST', equipment: 'BARBELL', sets: [s(60, 10), s(60, 10), s(60, 10)]);
      final sg = suggest(prev, 'WEIGHT_REPS', 10, 2.5)!;
      expect(sg.kind, 'up');
      expect(sg.weightKg, 62.5);
      expect(suggest(null, 'WEIGHT_REPS', 10, 2.5)!.kind, 'first');
    });

    test('sugerencia mantiene si no llegó al tope o hubo RPE alto', () {
      final prev = SessionExercise(exerciseId: 'x', name: 'x', tracking: 'WEIGHT_REPS', muscle: 'CHEST', equipment: 'BARBELL', sets: [s(60, 10), s(60, 8)]);
      expect(suggest(prev, 'WEIGHT_REPS', 10, 2.5)!.kind, 'hold');
      final hard = SessionExercise(exerciseId: 'x', name: 'x', tracking: 'WEIGHT_REPS', muscle: 'CHEST', equipment: 'BARBELL', sets: [SetRec(weightKg: 60, reps: 10, completed: true, rpe: 9.5)]);
      expect(suggest(hard, 'WEIGHT_REPS', 10, 2.5)!.kind, 'hold');
    });

    test('discos y calentamientos', () {
      final p = platePlan(100, lb: false);
      expect(p.plates, ['25 × 1', '15 × 1']);
      expect(p.remainder, 0);
      expect(warmupSets(100).map((w) => w.weightKg).toList(), [40, 60, 80]);
      expect(warmupSets(10), isEmpty);
    });

    test('racha semanal', () {
      final now = DateTime(2026, 10, 28);
      final week = now.subtract(const Duration(days: 7)).millisecondsSinceEpoch;
      final sessions = [
        Session(name: 'a', startedAt: now.millisecondsSinceEpoch, status: 'finished'),
        Session(name: 'b', startedAt: week, status: 'finished'),
      ];
      expect(weekStreak(sessions, now), 2);
    });
  });

  test('modelos ida y vuelta JSON', () {
    final se = Session(name: 'x', exercises: [
      SessionExercise(exerciseId: 'a', name: 'A', tracking: 'WEIGHT_REPS', muscle: 'CHEST', equipment: 'BARBELL', sets: [SetRec(weightKg: 50, reps: 5, completed: true)])
    ]);
    final back = Session.fromJson(se.toJson());
    expect(back.exercises.single.sets.single.weightKg, 50);
  });
}
