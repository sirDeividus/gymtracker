import 'dart:async';

import 'package:flutter/material.dart';

import '../domain.dart';
import '../models.dart';
import '../services.dart';
import '../state.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets.dart';
import 'picker.dart';
import 'settings.dart';
import 'tabata.dart';

Future<void> openWorkout(BuildContext context) =>
    Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const WorkoutScreen()));

class WorkoutScreen extends StatefulWidget {
  const WorkoutScreen({super.key});
  @override
  State<WorkoutScreen> createState() => _WorkoutScreenState();
}

class _WorkoutScreenState extends State<WorkoutScreen> with WidgetsBindingObserver {
  Timer? _tick;
  bool unlocked = false;
  String? _lastCount;
  final Set<String> _openTables = {};
  int _now = DateTime.now().millisecondsSinceEpoch;

  Session? get se => app.active;
  String get unit => app.settings.unit;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    keepAwake(true);
    RestNotifier.instance.requestPermission();
    _tick = Timer.periodic(const Duration(milliseconds: 250), (_) => _onTick());
  }

  @override
  void dispose() {
    _tick?.cancel();
    keepAwake(false);
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) keepAwake(true);
  }

  void _onTick() {
    final s = se;
    if (s == null || !mounted) return;
    _now = DateTime.now().millisecondsSinceEpoch;
    final ends = s.restEndsAt;
    if (ends != null) {
      final left = ((ends - _now) / 1000).ceil();
      // 3, 2, 1 en los últimos segundos del descanso (si dura más de tres).
      if (left >= 1 && left <= 3 && s.restTotal > 3) {
        final k = '$ends:$left';
        if (k != _lastCount) {
          _lastCount = k;
          Cues.instance.countdown(left, voice: app.settings.voice, sound: app.settings.sound);
        }
      }
      if (left <= 0) {
        s.restEndsAt = null;
        unlocked = false;
        RestNotifier.instance.cancel();
        if (app.settings.sound) Cues.instance.beep(times: 3);
        app.save();
        toast(context, '¡Descanso terminado!');
      }
    }
    setState(() {});
  }

  void _startRest(int sec, {bool manual = true}) {
    final s = se!;
    s.restEndsAt = DateTime.now().millisecondsSinceEpoch + sec * 1000;
    s.restTotal = sec;
    unlocked = false;
    RestNotifier.instance.schedule(DateTime.fromMillisecondsSinceEpoch(s.restEndsAt! + 1000));
    app.save();
    if (manual) toast(context, 'Descanso de ${clock(sec)}');
    setState(() {});
  }

  void _skipRest() {
    se!.restEndsAt = null;
    RestNotifier.instance.cancel();
    app.save();
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final s = se;
    if (s == null) {
      return Scaffold(body: Center(child: GButton('Volver', block: false, onPressed: () => Navigator.pop(context))));
    }
    final elapsed = (_now - s.startedAt) ~/ 1000;
    final totalSets = s.exercises.fold<int>(0, (a, e) => a + e.sets.length);
    final doneSets = s.exercises.fold<int>(0, (a, e) => a + e.sets.where((x) => x.completed).length);
    final restLeft = s.restEndsAt == null ? 0 : ((s.restEndsAt! - _now) / 1000).ceil();
    final locked = s.restEndsAt != null && !unlocked;
    return PopScope(
      canPop: !locked,
      child: Scaffold(
        body: SafeArea(
          child: Stack(children: [
            Column(children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(4, 4, 12, 0),
                child: Row(children: [
                  IconButton(icon: const Icon(Icons.keyboard_arrow_down, size: 30), tooltip: 'Minimizar', onPressed: () => Navigator.pop(context)),
                  Expanded(child: Text(clock(elapsed), style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: C.primary, fontFeatures: [FontFeature.tabularFigures()]))),
                  IconButton(icon: const Icon(Icons.alarm), tooltip: 'Descanso', onPressed: () => _startRest(app.settings.restSec)),
                  const SizedBox(width: 6),
                  GestureDetector(
                    onTap: _finish,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 11),
                      decoration: BoxDecoration(gradient: C.gradient, borderRadius: BorderRadius.circular(14)),
                      child: const Text('Terminar', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 15)),
                    ),
                  ),
                ]),
              ),
              LinearProgressIndicator(value: totalSets == 0 ? 0 : doneSets / totalSets, minHeight: 3, color: C.primary, backgroundColor: C.surface2),
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                child: Row(children: [
                  _stat('Duración', clock(elapsed), accent: true),
                  _stat('Volumen', '${numText(kgToUnit(sessionVolume(s), unit), 0)} $unit'),
                  _stat('Series', '${completedSets(s)}'),
                ]),
              ),
              const Divider(height: 1),
              Expanded(
                child: ListView(padding: EdgeInsets.fromLTRB(16, 12, 16, restLeft > 0 ? 120 : 24), children: [
                  if (s.exercises.isEmpty) const Empty(Icons.fitness_center, 'Entrenamiento libre', 'Añade el primer ejercicio para empezar a registrar series.'),
                  for (final ex in s.exercises) _exercise(s, ex),
                  const SizedBox(height: 16),
                  GButton('AGREGAR EJERCICIO', icon: Icons.add, onPressed: () async {
                    final id = await pickExercise(context);
                    if (id != null) {
                      s.exercises.add(app.buildExercise(id));
                      app.commit();
                    }
                  }),
                  const SizedBox(height: 12),
                  Row(children: [
                    Expanded(child: GSecondary('Configuración', onPressed: () => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => Scaffold(appBar: AppBar(title: const Text('Ajustes')), body: const SettingsScreen()))))),
                    const SizedBox(width: 12),
                    Expanded(child: GSecondary('Descartar Entreno', danger: true, onPressed: _discard)),
                  ]),
                ]),
              ),
            ]),
            // Bloqueo durante el descanso: los toques accidentales (bolsillo) no hacen nada.
            if (locked) Positioned.fill(bottom: 96, child: GestureDetector(behavior: HitTestBehavior.opaque, onTap: () {}, child: Container(color: C.bg.withValues(alpha: 0.45)))),
            if (restLeft > 0) Positioned(left: 0, right: 0, bottom: 0, child: _restPanel(restLeft, locked)),
          ]),
        ),
      ),
    );
  }

  Widget _stat(String label, String value, {bool accent = false}) => Expanded(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(label, style: const TextStyle(fontSize: 13, color: C.text2)),
          Text(value, style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: accent ? C.primary : C.text)),
        ]),
      );

  Widget _restPanel(int left, bool locked) => Container(
        padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
        decoration: const BoxDecoration(color: C.surface, border: Border(top: BorderSide(color: C.primary))),
        child: Row(children: [
          Expanded(child: Text(clock(left), style: const TextStyle(fontSize: 34, fontWeight: FontWeight.w700, color: C.primary, fontFeatures: [FontFeature.tabularFigures()]))),
          if (locked)
            GestureDetector(
              onLongPress: () => setState(() => unlocked = true),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                decoration: BoxDecoration(color: C.surface2, borderRadius: BorderRadius.circular(14)),
                child: const Row(children: [Icon(Icons.lock, size: 18), SizedBox(width: 8), Text('Mantén para desbloquear')]),
              ),
            )
          else ...[
            TextButton(onPressed: () => setState(() { se!.restEndsAt = se!.restEndsAt! - 15000; app.save(); }), child: const Text('−15')),
            TextButton(onPressed: () => setState(() { se!.restEndsAt = se!.restEndsAt! + 15000; app.save(); }), child: const Text('+15')),
            TextButton(onPressed: _skipRest, child: const Text('Saltar')),
          ],
        ]),
      );

  // ------------------------------------------------------------ Ejercicio

  Widget _exercise(Session s, SessionExercise ex) {
    final done = ex.sets.isNotEmpty && ex.sets.every((x) => x.completed);
    final compact = ex.tracking == 'DURATION';
    final showTable = !compact || _openTables.contains(ex.id);
    final fields = switch (ex.tracking) {
      'DURATION' => ['SEG'],
      'DISTANCE' => ['KM', 'MIN'],
      'BODYWEIGHT' => ['REPS'],
      _ => [unit.toUpperCase(), 'REPS'],
    };
    final sug = ex.suggestionDismissed ? null : (ex.suggestionKind == null ? null : (ex.suggestionKind!, ex.suggestionText ?? '', ex.suggestionWeightKg));
    return Padding(
      padding: const EdgeInsets.only(top: 18),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          CircleAvatar(radius: 24, backgroundColor: C.surface2, child: Text(ex.name.characters.first, style: const TextStyle(color: C.text2, fontWeight: FontWeight.w700))),
          const SizedBox(width: 12),
          Expanded(child: Text(ex.name, style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: done ? C.text2 : C.primary))),
          IconButton(icon: const Icon(Icons.more_vert), onPressed: () => _exerciseMenu(s, ex)),
        ]),
        if (!compact)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Wrap(spacing: 10, children: [
              _pill(Icons.timer_outlined, 'Descanso: ${ex.restSec > 0 ? clock(ex.restSec) : '--'}', () => _restPicker(ex)),
              _pill(Icons.note_add_outlined, ex.notes.isEmpty ? 'Nota' : 'Nota ✓', () => _noteEditor(ex)),
            ]),
          ),
        if (compact) ...[
          const SizedBox(height: 10),
          _timerButton(s, ex, done),
          TextButton(
            onPressed: () => setState(() => _openTables.contains(ex.id) ? _openTables.remove(ex.id) : _openTables.add(ex.id)),
            child: Text(showTable ? 'Ocultar series' : 'Ver series (${ex.sets.where((x) => x.completed).length}/${ex.sets.length})', style: const TextStyle(color: C.text2)),
          ),
        ],
        if (showTable && sug != null && sug.$1 != 'hold')
          Container(
            margin: const EdgeInsets.only(top: 10),
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(color: C.primary.withValues(alpha: 0.08), border: Border.all(color: C.primary.withValues(alpha: 0.4)), borderRadius: BorderRadius.circular(12)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(sug.$1 == 'first' ? 'PRIMERA VEZ' : 'SUGERENCIA', style: const TextStyle(color: C.primary, fontSize: 12, fontWeight: FontWeight.w700, letterSpacing: 0.6)),
              Text(sug.$2),
              Row(children: [
                if (sug.$1 == 'up')
                  TextButton(
                    onPressed: () {
                      for (final x in ex.sets.where((x) => !x.completed)) {
                        x.weightKg = sug.$3;
                      }
                      ex.suggestionDismissed = true;
                      app.commit();
                    },
                    child: const Text('Aplicar a series pendientes', style: TextStyle(color: C.primary)),
                  ),
                TextButton(onPressed: () { ex.suggestionDismissed = true; app.commit(); }, child: Text(sug.$1 == 'up' ? 'Ignorar' : 'Entendido', style: const TextStyle(color: C.text2))),
              ]),
            ]),
          ),
        if (showTable) ...[
          Padding(
            padding: const EdgeInsets.only(top: 14, bottom: 4),
            child: Row(children: [
              const SizedBox(width: 44, child: Center(child: Text('SERIE', style: _hd))),
              const Expanded(flex: 12, child: Center(child: Text('ANTERIOR', style: _hd))),
              Expanded(flex: 10, child: Center(child: Text(fields[0], style: _hd))),
              Expanded(flex: 10, child: Center(child: Text(fields.length > 1 ? fields[1] : '', style: _hd))),
              const SizedBox(width: 48, child: Center(child: Text('✓', style: _hd))),
            ]),
          ),
          for (var i = 0; i < ex.sets.length; i++) _setRow(s, ex, ex.sets[i], i),
          const SizedBox(height: 6),
          GSecondary('+  Agregar Serie', onPressed: () {
            ex.sets.add(SetRec());
            app.commit();
          }),
        ],
      ]),
    );
  }

  static const _hd = TextStyle(fontSize: 12, color: C.muted, letterSpacing: 0.6);

  Widget _pill(IconData icon, String text, VoidCallback onTap) => ActionChip(
        avatar: Icon(icon, size: 18, color: C.text),
        label: Text(text),
        onPressed: onTap,
        backgroundColor: C.surface2,
        side: BorderSide.none,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      );

  Widget _timerButton(Session s, SessionExercise ex, bool done) {
    final work = ex.durationSec ?? ex.sets.firstOrNull?.durationSec ?? 20;
    final summary = '${ex.cycles > 1 ? '${ex.cycles} ciclos × ' : ''}${ex.sets.length} × $work s${ex.restSec > 0 ? ' · descanso ${ex.restSec} s' : ''}';
    return Material(
      color: done ? C.ok : C.primary,
      borderRadius: BorderRadius.circular(18),
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: () async {
          await openTabata(context, ex);
          if (mounted) setState(() {});
        },
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          child: Row(children: [
            const Icon(Icons.timer_outlined, color: Colors.white, size: 26),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(done ? 'Completado' : 'Iniciar temporizador', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 17)),
                Text(summary, style: TextStyle(color: Colors.white.withValues(alpha: 0.85), fontSize: 13)),
              ]),
            ),
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(shape: BoxShape.circle, color: done ? Colors.white : Colors.white.withValues(alpha: 0.25)),
              child: Icon(Icons.check, size: 20, color: done ? C.ok : Colors.white70),
            ),
          ]),
        ),
      ),
    );
  }

  // ------------------------------------------------------------ Series

  Widget _setRow(Session s, SessionExercise ex, SetRec st, int i) {
    final t = ex.tracking;
    final normalIdx = ex.sets.where((x) => x.type == 'N').toList().indexOf(st) + 1;
    final label = st.type == 'N' ? '$normalIdx' : st.type;
    final prev = st.prev;
    final prevTxt = prev == null
        ? '—'
        : t == 'DURATION'
            ? '${prev.durationSec ?? '—'}s'
            : t == 'BODYWEIGHT'
                ? '${prev.reps ?? '—'} reps'
                : '${numText(prev.weightKg == null ? null : kgToUnit(prev.weightKg!, unit))} × ${prev.reps ?? '—'}';
    Widget field(String v, String hint, void Function(String) on, {bool dec = false}) => SetField(value: v, hint: hint, decimal: dec, onChanged: (x) { on(x); app.save(); setState(() {}); });
    final w = field(st.weightKg == null ? '' : numText(kgToUnit(st.weightKg!, unit), 2), prev?.weightKg == null ? '—' : numText(kgToUnit(prev!.weightKg!, unit)), (v) {
      final p = parseNum(v);
      st.weightKg = p == null ? null : unitToKg(p, unit);
    }, dec: true);
    final r = field(st.reps?.toString() ?? '', '${ex.repMin}${ex.repMax != ex.repMin ? '–${ex.repMax}' : ''}', (v) => st.reps = int.tryParse(v));
    final d = field(st.durationSec?.toString() ?? '', '${ex.durationSec ?? 30}', (v) => st.durationSec = int.tryParse(v));
    final km = field(st.distanceM == null ? '' : numText(st.distanceM! / 1000, 3), '0', (v) {
      final p = parseNum(v);
      st.distanceM = p == null ? null : p * 1000;
    }, dec: true);
    final mn = field(st.durationSec == null ? '' : numText(st.durationSec! / 60, 2), '0', (v) {
      final p = parseNum(v);
      st.durationSec = p == null ? null : (p * 60).round();
    }, dec: true);
    final cells = switch (t) {
      'DURATION' => [d, const SizedBox()],
      'DISTANCE' => [km, mn],
      'BODYWEIGHT' => [r, const SizedBox()],
      _ => [w, r],
    };
    return Container(
      margin: const EdgeInsets.only(bottom: 4),
      padding: const EdgeInsets.symmetric(vertical: 4),
      decoration: BoxDecoration(color: st.completed ? C.ok.withValues(alpha: 0.16) : null, borderRadius: BorderRadius.circular(12)),
      child: Row(children: [
        SizedBox(
          width: 44,
          child: GestureDetector(
            onTap: () => _setMenu(ex, st, i),
            child: Container(
              height: 40,
              alignment: Alignment.center,
              decoration: BoxDecoration(color: st.completed ? Colors.black26 : C.surface2, borderRadius: BorderRadius.circular(10)),
              child: Text(label, style: TextStyle(fontWeight: FontWeight.w700, color: st.type == 'W' ? C.warn : (st.type == 'F' ? C.primary : C.text))),
            ),
          ),
        ),
        Expanded(flex: 12, child: GestureDetector(onTap: () => _copyPrev(ex, st), behavior: HitTestBehavior.opaque, child: Center(child: Text(prevTxt, style: const TextStyle(fontSize: 13, color: C.text2), textAlign: TextAlign.center)))),
        Expanded(flex: 10, child: Padding(padding: const EdgeInsets.symmetric(horizontal: 3), child: cells[0])),
        Expanded(flex: 10, child: Padding(padding: const EdgeInsets.symmetric(horizontal: 3), child: cells[1])),
        SizedBox(
          width: 48,
          child: GestureDetector(
            onTap: () => _toggle(s, ex, st),
            child: Container(
              height: 40,
              margin: const EdgeInsets.only(left: 4),
              decoration: BoxDecoration(color: st.completed ? C.ok : C.surface2, borderRadius: BorderRadius.circular(10)),
              child: Icon(Icons.check, color: st.completed ? Colors.white : C.muted),
            ),
          ),
        ),
      ]),
    );
  }

  void _copyPrev(SessionExercise ex, SetRec st) {
    final p = st.prev;
    if (p == null) return;
    if (p.weightKg != null) st.weightKg = p.weightKg;
    if (p.reps != null) st.reps = p.reps;
    if (p.durationSec != null && ex.tracking == 'DURATION') st.durationSec = p.durationSec;
    app.commit();
  }

  void _toggle(Session s, SessionExercise ex, SetRec st) {
    if (!st.completed) {
      String? need;
      switch (ex.tracking) {
        case 'WEIGHT_REPS':
        case 'BODYWEIGHT':
          if (st.reps == null) need = 'las repeticiones';
        case 'DURATION':
          if (st.durationSec == null) need = 'los segundos';
        default:
          if (st.distanceM == null && st.durationSec == null) need = 'distancia o tiempo';
      }
      if (need != null) {
        toast(context, 'Escribe $need antes de completar');
        return;
      }
      // Registro en 2 toques: si dejó el peso vacío, usa el de la sesión anterior.
      if (ex.tracking == 'WEIGHT_REPS' && st.weightKg == null) st.weightKg = st.prev?.weightKg ?? 0;
      st.completed = true;
      // Tras la última serie del ejercicio se usa el descanso «al cambiar de ejercicio» (Ajustes);
      // si era el último ejercicio pendiente, no hace falta descansar.
      final exDone = ex.sets.every((x) => x.completed);
      final othersPending = s.exercises.any((e) => e != ex && e.sets.any((x) => !x.completed));
      final restSec = !exDone ? ex.restSec : (othersPending ? app.settings.changeRestSec : 0);
      if (app.settings.autoRest && restSec > 0) _startRest(restSec, manual: false);
    } else {
      st.completed = false;
    }
    app.commit();
  }

  void _setMenu(SessionExercise ex, SetRec st, int i) {
    showOptions(context, 'Serie', [
      ('Tipo de serie…', () => _typeSheet(st), danger: false),
      if (ex.tracking == 'WEIGHT_REPS' && (st.weightKg ?? 0) > 0) ('Calculadora de discos', () => _plates(st), danger: false),
      if (i > 0)
        ('Repetir serie anterior', () {
          final p = ex.sets[i - 1];
          st.weightKg = p.weightKg;
          st.reps = p.reps;
          st.durationSec = p.durationSec;
          app.commit();
        }, danger: false),
      ('Esfuerzo (RPE)…', () => _rpe(st), danger: false),
      ('Eliminar serie', () {
        ex.sets.removeAt(i);
        app.commit();
      }, danger: true),
    ]);
  }

  void _typeSheet(SetRec st) => showOptions(context, 'Tipo de serie', [
        for (final e in setTypeNames.entries)
          ('${e.value}${e.key == 'W' ? ' (no cuenta en volumen ni récords)' : ''}', () {
            st.type = e.key;
            app.commit();
          }, danger: false),
      ]);

  void _rpe(SetRec st) => showOptions(context, 'Esfuerzo percibido (RPE)', [
        ('Sin dato', () { st.rpe = null; app.save(); }, danger: false),
        for (final v in [6, 7, 8, 9, 10]) ('RPE $v', () { st.rpe = v.toDouble(); app.save(); }, danger: false),
      ]);

  void _plates(SetRec st) {
    final target = kgToUnit(st.weightKg!, unit);
    final plan = platePlan(target, lb: unit == 'lb');
    showSheet<void>(context, 'Discos para ${numText(target)} $unit', (ctx) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('Barra ${unit == 'lb' ? 45 : 20} $unit. Por lado:', style: const TextStyle(color: C.text2)),
          const SizedBox(height: 6),
          Text(plan.plates.isEmpty ? 'Solo la barra' : plan.plates.join('  ·  '), style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
          if (plan.remainder > 0) Text('Faltan ${numText(plan.remainder, 2)} $unit con estos discos.', style: const TextStyle(color: C.warn)),
        ]));
  }

  void _exerciseMenu(Session s, SessionExercise ex) {
    showOptions(context, ex.name, [
      if (ex.tracking == 'WEIGHT_REPS')
        ('Añadir calentamiento', () {
          final base = ex.sets.where((x) => x.type == 'N').firstOrNull?.weightKg ?? ex.sets.firstOrNull?.prev?.weightKg;
          final w = warmupSets(base);
          if (w.isEmpty) {
            toast(context, 'Escribe primero el peso de trabajo (≥ 20 kg)');
            return;
          }
          ex.sets.insertAll(0, [for (final x in w) SetRec(type: 'W', weightKg: x.weightKg, reps: x.reps)]);
          app.commit();
        }, danger: false),
      if (ex.tracking == 'DURATION') ('Modo intervalos (Tabata)', () async { await openTabata(context, ex); if (mounted) setState(() {}); }, danger: false),
      ('Quitar de la sesión', () {
        s.exercises.remove(ex);
        app.commit();
      }, danger: true),
    ]);
  }

  Future<void> _restPicker(SessionExercise ex) async {
    final opts = restOptions([0, 30, 45, 60, 90, 120, 180, 240, 300], ex.restSec);
    showOptions(context, 'Descanso entre series', [
      for (final v in opts) ('${v == 0 ? 'Sin descanso' : clock(v)}${v == ex.restSec ? '  ✓' : ''}', () { ex.restSec = v; app.commit(); }, danger: false),
      ('Personalizado…', () async {
        final v = await customRestDialog(context, 'Descanso entre series', ex.restSec);
        if (v != null) {
          ex.restSec = v;
          app.commit();
        }
      }, danger: false),
    ]);
  }

  void _noteEditor(SessionExercise ex) {
    final ctl = TextEditingController(text: ex.notes);
    showSheet<void>(context, 'Nota', (ctx) => Column(children: [
          TextField(controller: ctl, maxLines: 4, decoration: const InputDecoration(hintText: 'Notas del ejercicio')),
          const SizedBox(height: 12),
          GButton('Guardar', onPressed: () {
            ex.notes = ctl.text;
            app.commit();
            Navigator.pop(ctx);
          }),
        ]));
  }

  // ------------------------------------------------------------ Terminar / descartar

  void _finish() {
    final s = se!;
    final n = completedSets(s);
    if (n == 0) {
      showOptions(context, 'Aún no hay series completadas', [('Descartar entrenamiento', _discard, danger: true)],
          extra: const Text('Completa al menos una serie para guardar la sesión.', style: TextStyle(color: C.text2)));
      return;
    }
    final notes = TextEditingController();
    showSheet<void>(context, 'Finalizar entrenamiento', (ctx) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('Duración ${clock((DateTime.now().millisecondsSinceEpoch - s.startedAt) / 1000)} · $n series · ${numText(kgToUnit(sessionVolume(s), unit), 0)} $unit'),
          const SizedBox(height: 10),
          TextField(controller: notes, maxLines: 3, decoration: const InputDecoration(hintText: 'Notas generales (opcional)')),
          const SizedBox(height: 12),
          GButton('Finalizar y guardar', onPressed: () {
            app.finishSession(s, notes.text);
            RestNotifier.instance.cancel();
            Navigator.pop(ctx);
            Navigator.pop(context);
          }),
        ]));
  }

  Future<void> _discard() async {
    if (await confirm(context, '¿Descartar entrenamiento?', 'Se perderán las series registradas en esta sesión.', ok: 'Descartar', danger: true)) {
      RestNotifier.instance.cancel();
      app.discardSession(se!);
      if (mounted) Navigator.pop(context);
    }
  }
}

/// Campo de una serie: conserva el foco y el cursor aunque la pantalla se redibuje, y se actualiza si el valor cambia desde fuera.
class SetField extends StatefulWidget {
  final String value, hint;
  final bool decimal;
  final ValueChanged<String> onChanged;
  const SetField({super.key, required this.value, required this.hint, required this.onChanged, this.decimal = false});
  @override
  State<SetField> createState() => _SetFieldState();
}

class _SetFieldState extends State<SetField> {
  late final TextEditingController ctl = TextEditingController(text: widget.value);
  final FocusNode focus = FocusNode();

  @override
  void didUpdateWidget(covariant SetField old) {
    super.didUpdateWidget(old);
    if (!focus.hasFocus && widget.value != ctl.text) ctl.text = widget.value;
  }

  @override
  void dispose() {
    ctl.dispose();
    focus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => TextField(
        controller: ctl,
        focusNode: focus,
        keyboardType: TextInputType.numberWithOptions(decimal: widget.decimal),
        textAlign: TextAlign.center,
        onChanged: widget.onChanged,
        style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
        decoration: InputDecoration(
          hintText: widget.hint,
          isDense: true,
          filled: true,
          fillColor: C.surface2,
          contentPadding: const EdgeInsets.symmetric(horizontal: 2, vertical: 12),
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
          enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
        ),
      );
}
