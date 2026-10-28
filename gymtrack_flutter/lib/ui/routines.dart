import 'package:flutter/material.dart';

import '../models.dart';
import '../state.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets.dart';
import 'picker.dart';
import 'workout.dart';

class RoutinesScreen extends StatelessWidget {
  const RoutinesScreen({super.key});

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: app,
        builder: (context, _) => ListView(padding: const EdgeInsets.fromLTRB(16, 8, 16, 24), children: [
          Row(children: [
            const Expanded(child: Text('Rutinas', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800))),
            GSecondary('+ Nueva', block: false, onPressed: () {
              final r = app.newRoutine();
              Navigator.push(context, MaterialPageRoute<void>(builder: (_) => RoutineEditScreen(routineId: r.id)));
            }),
          ]),
          const SizedBox(height: 8),
          if (app.routines.isEmpty) const Empty(Icons.list_alt, 'Sin rutinas', 'Crea una o usa una plantilla.'),
          for (final r in app.routines) _card(context, r),
          const Label('Plantillas'),
          for (final t in app.templates)
            GCard(
              onTap: () {
                final r = app.fromTemplate(t);
                toast(context, 'Rutina creada');
                Navigator.push(context, MaterialPageRoute<void>(builder: (_) => RoutineEditScreen(routineId: r.id)));
              },
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(t['name'] as String, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                Text('${t['description']} · ${(t['items'] as List).length} ejercicios', style: const TextStyle(color: C.text2)),
              ]),
            ),
        ]),
      );

  Widget _card(BuildContext context, Routine r) {
    final m = r.items.map((i) => muscles[app.exerciseById(i.exerciseId)?.muscle]).whereType<String>().toSet().take(3).join(' · ');
    return GCard(
      onTap: () => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => RoutineEditScreen(routineId: r.id))),
      child: Row(children: [
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(r.name.isEmpty ? 'Sin nombre' : r.name, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
            Text('${r.items.length} ejercicios${m.isEmpty ? '' : ' · $m'}', style: const TextStyle(color: C.text2)),
          ]),
        ),
        IconButton.filled(
          style: IconButton.styleFrom(backgroundColor: C.primary),
          icon: const Icon(Icons.play_arrow),
          onPressed: () {
            if (r.items.isEmpty) {
              toast(context, 'Añade ejercicios primero');
              return;
            }
            if (app.active != null) {
              openWorkout(context);
              return;
            }
            app.startSession(r);
            openWorkout(context);
          },
        ),
      ]),
    );
  }
}

class RoutineEditScreen extends StatefulWidget {
  final String routineId;
  const RoutineEditScreen({super.key, required this.routineId});
  @override
  State<RoutineEditScreen> createState() => _RoutineEditScreenState();
}

class _RoutineEditScreenState extends State<RoutineEditScreen> {
  Routine? get r {
    for (final x in app.routines) {
      if (x.id == widget.routineId) return x;
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final routine = r;
    if (routine == null) return Scaffold(appBar: AppBar(title: const Text('Rutina')), body: const Empty(Icons.search, 'Rutina no encontrada', 'Puede que se haya eliminado.'));
    return Scaffold(
      appBar: AppBar(title: const Text('Editar rutina')),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 0, 16, 24), children: [
        TextFormField(
          initialValue: routine.name,
          decoration: const InputDecoration(hintText: 'Nombre de la rutina'),
          onChanged: (v) {
            routine.name = v;
            app.save();
          },
        ),
        Label('${routine.items.length} ejercicios'),
        for (var i = 0; i < routine.items.length; i++) _itemCard(routine, i),
        const SizedBox(height: 8),
        GSecondary('+  Agregar ejercicio', onPressed: () async {
          final id = await pickExercise(context);
          if (id != null) {
            routine.items.add(app.newItem(id));
            app.commit();
            setState(() {});
          }
        }),
        const SizedBox(height: 10),
        GButton('INICIAR', icon: Icons.play_arrow, onPressed: () {
          if (routine.items.isEmpty) {
            toast(context, 'Añade ejercicios primero');
            return;
          }
          if (app.active == null) app.startSession(routine);
          openWorkout(context);
        }),
        const SizedBox(height: 10),
        GSecondary('Eliminar rutina', danger: true, onPressed: () async {
          if (await confirm(context, '¿Eliminar rutina?', 'Tus entrenamientos anteriores no cambian.', ok: 'Eliminar', danger: true)) {
            app.routines.removeWhere((x) => x.id == routine.id);
            app.commit();
            if (context.mounted) Navigator.pop(context);
          }
        }),
      ]),
    );
  }

  Widget _itemCard(Routine routine, int i) {
    final it = routine.items[i];
    final ex = app.exerciseById(it.exerciseId);
    final dur = ex?.tracking == 'DURATION';
    final sum = dur
        ? '${it.cycles > 1 ? '${it.cycles} ciclos × ' : ''}${it.sets} × ${it.durationSec ?? 30} s'
        : '${it.sets} × ${it.repMin == it.repMax ? it.repMin : '${it.repMin}–${it.repMax}'}';
    return GCard(
      padding: const EdgeInsets.fromLTRB(14, 8, 4, 8),
      child: Row(children: [
        Expanded(
          child: InkWell(
            onTap: () => _editItem(it, ex),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(ex?.name ?? 'Ejercicio', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                Text('$sum · ${it.restSec > 0 ? clock(it.restSec) : 'sin'} descanso', style: const TextStyle(color: C.text2)),
              ]),
            ),
          ),
        ),
        IconButton(
          icon: const Icon(Icons.arrow_upward, size: 20),
          onPressed: i == 0
              ? null
              : () {
                  routine.items.insert(i - 1, routine.items.removeAt(i));
                  app.commit();
                  setState(() {});
                },
        ),
        IconButton(
          icon: const Icon(Icons.arrow_downward, size: 20),
          onPressed: i == routine.items.length - 1
              ? null
              : () {
                  routine.items.insert(i + 1, routine.items.removeAt(i));
                  app.commit();
                  setState(() {});
                },
        ),
        IconButton(
          icon: const Icon(Icons.close, size: 20),
          onPressed: () {
            routine.items.removeAt(i);
            app.commit();
            setState(() {});
          },
        ),
      ]),
    );
  }

  Future<void> _editItem(RoutineItem it, Exercise? ex) async {
    await Navigator.push(context, MaterialPageRoute<void>(builder: (_) => ExerciseEditScreen(item: it, exercise: ex)));
    app.commit();
    if (mounted) setState(() {});
  }
}

/// Edición de un ejercicio de la rutina. Los ejercicios por tiempo (Tabata, plancha…) solo piden cinco números.
class ExerciseEditScreen extends StatefulWidget {
  final RoutineItem item;
  final Exercise? exercise;
  const ExerciseEditScreen({super.key, required this.item, required this.exercise});
  @override
  State<ExerciseEditScreen> createState() => _ExerciseEditScreenState();
}

class _ExerciseEditScreenState extends State<ExerciseEditScreen> {
  late final c = {
    'min': TextEditingController(text: '${widget.item.repMin}'),
    'max': TextEditingController(text: '${widget.item.repMax}'),
    'weight': TextEditingController(text: widget.item.startWeightKg == null ? '' : numText(kgToUnit(widget.item.startWeightKg!, app.settings.unit), 2)),
    'work': TextEditingController(text: '${widget.item.durationSec ?? 30}'),
    'rounds': TextEditingController(text: '${widget.item.sets}'),
    'cycles': TextEditingController(text: '${widget.item.cycles}'),
    'cycleRest': TextEditingController(text: '${widget.item.cycleRestSec}'),
    'rest': TextEditingController(text: '${widget.item.restSec}'),
  };

  int _i(String k, int lo, int hi, int fallback) {
    final v = int.tryParse(c[k]!.text);
    return v != null && v >= lo && v <= hi ? v : fallback;
  }

  @override
  Widget build(BuildContext context) {
    final it = widget.item;
    final ex = widget.exercise;
    final dur = ex?.tracking == 'DURATION';
    final unit = app.settings.unit;
    return Scaffold(
      appBar: AppBar(title: const Text('Editar ejercicio')),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 0, 16, 24), children: [
        Text(ex?.name ?? 'Ejercicio', style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
        Text([ex?.muscle, ...?ex?.secondary].whereType<String>().map((m) => muscles[m]).whereType<String>().join(', '), style: const TextStyle(color: C.text2)),
        const SizedBox(height: 16),
        if (dur) ...[
          Row(children: [
            Expanded(child: NumField(controller: c['work']!, label: 'Trabajo (s)', onChanged: (_) => it.durationSec = _i('work', 1, 3600, it.durationSec ?? 30))),
            const SizedBox(width: 12),
            Expanded(child: NumField(controller: c['rest']!, label: 'Descanso (s)', onChanged: (_) => it.restSec = _i('rest', 0, 1800, it.restSec))),
          ]),
          const SizedBox(height: 12),
          NumField(controller: c['rounds']!, label: 'Rondas (series)', onChanged: (_) => it.sets = _i('rounds', 1, 60, it.sets)),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(child: NumField(controller: c['cycles']!, label: 'Ciclos', onChanged: (_) => it.cycles = _i('cycles', 1, 30, it.cycles))),
            const SizedBox(width: 12),
            Expanded(child: NumField(controller: c['cycleRest']!, label: 'Descanso entre ciclos (s)', onChanged: (_) => it.cycleRestSec = _i('cycleRest', 0, 3600, it.cycleRestSec))),
          ]),
          const SizedBox(height: 10),
          const Text('Una ronda = un trabajo + su descanso. Un ciclo repite todas las rondas. En el entrenamiento se abre un temporizador a pantalla completa.',
              style: TextStyle(color: C.muted, fontSize: 13)),
        ] else ...[
          Row(children: [
            Expanded(child: NumField(controller: c['rounds']!, label: 'Series', onChanged: (_) => it.sets = _i('rounds', 1, 20, it.sets))),
            const SizedBox(width: 12),
            Expanded(child: NumField(controller: c['rest']!, label: 'Descanso (s)', onChanged: (_) => it.restSec = _i('rest', 0, 1800, it.restSec))),
          ]),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(child: NumField(controller: c['min']!, label: 'Reps mín.', onChanged: (_) {
              it.repMin = _i('min', 1, 100, it.repMin);
              if (it.repMax < it.repMin) it.repMax = it.repMin;
            })),
            const SizedBox(width: 12),
            Expanded(child: NumField(controller: c['max']!, label: 'Reps máx.', onChanged: (_) {
              it.repMax = _i('max', 1, 100, it.repMax);
              if (it.repMin > it.repMax) it.repMin = it.repMax;
            })),
          ]),
          if (ex?.tracking == 'WEIGHT_REPS') ...[
            const SizedBox(height: 12),
            NumField(controller: c['weight']!, label: 'Peso inicial opcional ($unit)', decimal: true, onChanged: (v) {
              final p = parseNum(v);
              it.startWeightKg = p == null ? null : unitToKg(p, unit);
            }),
          ],
        ],
        const SizedBox(height: 24),
        GButton('GUARDAR', icon: Icons.check, onPressed: () => Navigator.pop(context)),
      ]),
    );
  }
}
