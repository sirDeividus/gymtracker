import 'dart:async';

import 'package:flutter/material.dart';

import '../domain.dart';
import '../models.dart';
import '../services.dart';
import '../state.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets.dart';

Future<void> openTabata(BuildContext context, SessionExercise ex) =>
    Navigator.push(context, MaterialPageRoute<void>(fullscreenDialog: true, builder: (_) => TabataScreen(ex: ex)));

Color _color(Phase p) => switch (p) {
      Phase.prep => C.text,
      Phase.work => C.primary,
      Phase.rest => C.rest,
      Phase.cycleRest => C.warn,
      Phase.done => C.ok,
    };

String _title(Phase p) => switch (p) {
      Phase.prep => 'PREPÁRATE',
      Phase.work => 'ENTRENAR',
      Phase.rest => 'DESCANSA',
      Phase.cycleRest => 'DESCANSO DE CICLO',
      Phase.done => '¡LISTO!',
    };

String _next(Phase p) => switch (p) {
      Phase.prep => 'PREPÁRATE',
      Phase.work => 'ENTRENAR',
      Phase.rest => 'DESCANSAR',
      Phase.cycleRest => 'DESCANSO DE CICLO',
      Phase.done => 'FIN',
    };

/// Temporizador de intervalos a pantalla completa. Se abre listo (en pausa); el botón central empieza.
class TabataScreen extends StatefulWidget {
  final SessionExercise ex;
  const TabataScreen({super.key, required this.ex});
  @override
  State<TabataScreen> createState() => _TabataScreenState();
}

class _TabataScreenState extends State<TabataScreen> {
  late IntervalPlan plan;
  bool started = false, muted = false;
  int? pausedAt;
  int pausedTotal = 0, startMs = 0, filled = 0;
  String? lastKey, lastCount;
  List<SetRec> sets = [];
  Timer? timer;

  SessionExercise get ex => widget.ex;

  @override
  void initState() {
    super.initState();
    plan = IntervalPlan(
      workSec: ex.durationSec ?? ex.sets.firstOrNull?.durationSec ?? 20,
      restSec: ex.restSec,
      rounds: ex.sets.length.clamp(1, 60),
      prepSec: ex.prepSec,
      cycles: ex.cycles,
      cycleRestSec: ex.cycleRestSec,
    );
    keepAwake(true);
    timer = Timer.periodic(const Duration(milliseconds: 200), (_) => _tick());
  }

  @override
  void dispose() {
    timer?.cancel();
    app.commit(now: true);
    super.dispose();
  }

  int get elapsed => !started ? 0 : (pausedAt ?? DateTime.now().millisecondsSinceEpoch) - startMs - pausedTotal;

  void _playPause() {
    final now = DateTime.now().millisecondsSinceEpoch;
    if (!started) {
      started = true;
      startMs = now;
      pausedAt = null;
      pausedTotal = 0;
      // Asegura una serie pendiente por ronda.
      final total = plan.totalRounds;
      final pend = ex.sets.where((s) => !s.completed).toList();
      while (pend.length < total) {
        final s = SetRec();
        ex.sets.add(s);
        pend.add(s);
      }
      sets = pend.take(total).toList();
    } else if (pausedAt == null) {
      pausedAt = now;
    } else {
      pausedTotal += now - pausedAt!;
      pausedAt = null;
    }
    setState(() {});
  }

  void _tick() {
    if (!mounted) return;
    final snap = intervalSnapshot(plan, elapsed);
    if (started) {
      while (filled < snap.completed && filled < sets.length) {
        sets[filled]
          ..durationSec = plan.workSec
          ..completed = true;
        filled++;
      }
      final voice = app.settings.voice, sound = app.settings.sound;
      final key = '${snap.phase}${snap.cycle}:${snap.round}';
      if (key != lastKey) {
        final first = lastKey == null;
        lastKey = key;
        if (!muted) {
          if (!first && snap.phase != Phase.done) Cues.instance.beep();
          if (snap.phase == Phase.done) Cues.instance.beep(times: 3);
          // Al empezar cada tramo, qué toca hacer.
          if (voice) {
            Cues.instance.speak(switch (snap.phase) {
              Phase.work => 'Go',
              Phase.rest || Phase.cycleRest => 'Descanso',
              Phase.prep => 'Prepárate',
              Phase.done => 'Terminado',
            });
          }
        }
      } else if (!muted && pausedAt == null && snap.phase != Phase.done) {
        final left = (snap.remainingMs / 1000).ceil();
        final total = (snap.totalMs / 1000).round();
        if (left >= 1 && left <= 3 && left < total) {
          final ck = '$key:$left';
          if (ck != lastCount) {
            lastCount = ck;
            // En los descansos, a 3 s: «Prepárate»; después 2, 1. En trabajo y preparación: 3, 2, 1.
            if ((snap.phase == Phase.rest || snap.phase == Phase.cycleRest) && left == 3 && voice) {
              Cues.instance.speak('Prepárate');
            } else {
              Cues.instance.countdown(left, voice: voice, sound: sound);
            }
          }
        }
      }
    }
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final snap = intervalSnapshot(plan, elapsed);
    final nx = nextPhase(plan, elapsed);
    final col = _color(snap.phase);
    final paused = !started || pausedAt != null;
    final sec = snap.finished ? 0 : (snap.remainingMs / 1000).ceil();
    final rLeft = switch (snap.phase) {
      Phase.prep || Phase.cycleRest => plan.rounds,
      Phase.work => plan.rounds - snap.round + 1,
      Phase.rest => plan.rounds - snap.round,
      Phase.done => 0,
    };
    final cLeft = switch (snap.phase) {
      Phase.prep => plan.cycles,
      Phase.work || Phase.rest => plan.cycles - snap.cycle + 1,
      Phase.cycleRest => plan.cycles - snap.cycle,
      Phase.done => 0,
    };
    final progress = snap.totalMs <= 0 ? 0.0 : (snap.remainingMs / snap.totalMs).clamp(0.0, 1.0);
    return PopScope(
      canPop: false,
      child: Scaffold(
        body: Container(
          decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.center, colors: [col.withValues(alpha: 0.18), C.bg])),
          child: SafeArea(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Column(children: [
                const SizedBox(height: 8),
                Row(children: [
                  InkWell(
                    onTap: _close,
                    borderRadius: BorderRadius.circular(22),
                    child: Container(width: 44, height: 44, decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: C.outline)), child: const Icon(Icons.close, size: 22)),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(ex.name, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600), maxLines: 1, overflow: TextOverflow.ellipsis),
                      Text('Quedan ${mmss((snap.totalLeftMs / 1000).ceil())}', style: const TextStyle(color: C.muted, fontSize: 13)),
                    ]),
                  ),
                ]),
                const SizedBox(height: 14),
                Row(children: [
                  _pill(Icons.tune, 'Editar', _edit),
                  const SizedBox(width: 8),
                  _pill(muted ? Icons.volume_off : Icons.volume_up, muted ? 'Sin sonido' : 'Sonido', () => setState(() => muted = !muted)),
                  const SizedBox(width: 8),
                  _pill(null, 'Intervalos', _showList),
                ]),
                const Spacer(),
                Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                  Container(width: 8, height: 8, decoration: BoxDecoration(shape: BoxShape.circle, color: col)),
                  const SizedBox(width: 8),
                  Text(_title(snap.phase), style: TextStyle(color: col, fontSize: 15, fontWeight: FontWeight.w600, letterSpacing: 3)),
                ]),
                FittedBox(
                  child: Text(snap.finished ? '✓' : mmss(sec), style: const TextStyle(fontSize: 112, fontWeight: FontWeight.w300, fontFamily: 'monospace', height: 1.05)),
                ),
                FractionallySizedBox(
                  widthFactor: 0.7,
                  child: ClipRRect(borderRadius: BorderRadius.circular(4), child: LinearProgressIndicator(value: progress, minHeight: 4, color: col, backgroundColor: C.surface2)),
                ),
                if (snap.round > 0 && !snap.finished)
                  Padding(
                    padding: const EdgeInsets.only(top: 14),
                    child: Text('${plan.cycles > 1 ? 'Ciclo ${snap.cycle}/${plan.cycles}  ·  ' : ''}Ronda ${snap.round}/${plan.rounds}', style: const TextStyle(color: C.text2, fontSize: 15)),
                  ),
                const Spacer(),
                Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                  const Text('SIGUIENTE  ', style: TextStyle(color: C.muted, fontSize: 12, letterSpacing: 2)),
                  Container(width: 7, height: 7, decoration: BoxDecoration(shape: BoxShape.circle, color: _color(nx))),
                  Text('  ${_next(nx)}', style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
                ]),
                const SizedBox(height: 14),
                Row(children: [
                  Expanded(child: _counter('RONDAS', rLeft)),
                  GestureDetector(
                    onTap: _playPause,
                    child: Container(
                      width: 88,
                      height: 88,
                      decoration: BoxDecoration(shape: BoxShape.circle, gradient: C.gradient, boxShadow: [BoxShadow(color: C.primary.withValues(alpha: 0.3), blurRadius: 24, offset: const Offset(0, 8))]),
                      child: Icon(snap.finished ? Icons.check : (paused ? Icons.play_arrow : Icons.pause), color: Colors.white, size: 44),
                    ),
                  ),
                  Expanded(child: _counter('CICLOS', cLeft)),
                ]),
                const SizedBox(height: 20),
              ]),
            ),
          ),
        ),
      ),
    );
  }

  Widget _counter(String label, int v) => Column(children: [
        Text('$v', style: const TextStyle(fontSize: 36, fontWeight: FontWeight.w300, fontFamily: 'monospace')),
        Text(label, style: const TextStyle(color: C.muted, fontSize: 11, letterSpacing: 2)),
      ]);

  Widget _pill(IconData? icon, String text, VoidCallback onTap) => InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(18),
        child: Container(
          height: 36,
          padding: const EdgeInsets.symmetric(horizontal: 14),
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(18), border: Border.all(color: C.outline)),
          child: Row(children: [
            if (icon != null) ...[Icon(icon, size: 16, color: C.text2), const SizedBox(width: 6)],
            Text(text, style: const TextStyle(fontSize: 13)),
          ]),
        ),
      );

  void _close() {
    timer?.cancel();
    final n = filled;
    Navigator.pop(context);
    if (n > 0) WidgetsBinding.instance.addPostFrameCallback((_) {});
  }

  Future<void> _edit() async {
    if (started && pausedAt == null) pausedAt = DateTime.now().millisecondsSinceEpoch;
    final f = {
      'work': TextEditingController(text: '${plan.workSec}'),
      'rest': TextEditingController(text: '${plan.restSec}'),
      'rounds': TextEditingController(text: '${plan.rounds}'),
      'cycles': TextEditingController(text: '${plan.cycles}'),
      'cycleRest': TextEditingController(text: '${plan.cycleRestSec}'),
      'prep': TextEditingController(text: '${plan.prepSec}'),
    };
    int n(String k, int lo, int hi, int cur) {
      final v = int.tryParse(f[k]!.text);
      return v == null ? cur : v.clamp(lo, hi);
    }

    await showSheet<void>(context, 'Editar temporizador', (ctx) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Chips<String>(
            items: const ['Tabata', '30/30', '40/20'],
            selected: const {},
            label: (x) => x,
            onTap: (x) {
              final p = {'Tabata': [20, 10, 8], '30/30': [30, 30, 10], '40/20': [40, 20, 10]}[x]!;
              f['work']!.text = '${p[0]}';
              f['rest']!.text = '${p[1]}';
              f['rounds']!.text = '${p[2]}';
            },
          ),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(child: NumField(controller: f['work']!, label: 'Trabajo (s)')),
            const SizedBox(width: 12),
            Expanded(child: NumField(controller: f['rest']!, label: 'Descanso (s)')),
          ]),
          const SizedBox(height: 10),
          Row(children: [
            Expanded(child: NumField(controller: f['rounds']!, label: 'Rondas')),
            const SizedBox(width: 12),
            Expanded(child: NumField(controller: f['cycles']!, label: 'Ciclos')),
          ]),
          const SizedBox(height: 10),
          Row(children: [
            Expanded(child: NumField(controller: f['cycleRest']!, label: 'Descanso entre ciclos (s)')),
            const SizedBox(width: 12),
            Expanded(child: NumField(controller: f['prep']!, label: 'Cuenta atrás inicial (s)')),
          ]),
          const SizedBox(height: 10),
          const Text('Cada ronda de trabajo completa una serie del ejercicio; si faltan series, se añaden.', style: TextStyle(color: C.muted, fontSize: 13)),
          const SizedBox(height: 14),
          GButton('Guardar', onPressed: () {
            plan = IntervalPlan(
              workSec: n('work', 1, 3600, plan.workSec),
              restSec: n('rest', 0, 3600, plan.restSec),
              rounds: n('rounds', 1, 60, plan.rounds),
              cycles: n('cycles', 1, 30, plan.cycles),
              cycleRestSec: n('cycleRest', 0, 3600, plan.cycleRestSec),
              prepSec: n('prep', 0, 60, plan.prepSec),
            );
            ex.cycles = plan.cycles;
            ex.cycleRestSec = plan.cycleRestSec;
            ex.prepSec = plan.prepSec;
            started = false;
            pausedAt = null;
            pausedTotal = 0;
            filled = 0;
            lastKey = null;
            lastCount = null;
            sets = [];
            Navigator.pop(ctx);
          }),
        ]));
    if (mounted) setState(() {});
  }

  void _showList() {
    final snap = intervalSnapshot(plan, elapsed);
    final steps = intervalSchedule(plan);
    showSheet<void>(context, 'Intervalos', (ctx) => SizedBox(
          height: MediaQuery.of(ctx).size.height * 0.55,
          child: ListView(children: [
            for (final st in steps)
              Container(
                padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 6),
                color: (st.phase == snap.phase && st.cycle == snap.cycle && st.round == snap.round) ? _color(st.phase).withValues(alpha: 0.2) : null,
                child: Row(children: [
                  Container(width: 14, height: 14, color: _color(st.phase)),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(switch (st.phase) {
                      Phase.prep => 'Preparación',
                      Phase.work => '${plan.cycles > 1 ? 'C${st.cycle} · ' : ''}Entrenar ${st.round}',
                      Phase.rest => 'Descansar',
                      _ => 'Descanso de ciclo',
                    }),
                  ),
                  Text(mmss(st.seconds), style: const TextStyle(fontWeight: FontWeight.w700)),
                ]),
              ),
          ]),
        ));
  }
}
