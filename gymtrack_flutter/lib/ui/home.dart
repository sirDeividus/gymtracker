import 'package:flutter/material.dart';

import '../domain.dart';
import '../state.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets.dart';
import 'history.dart';
import 'workout.dart';

class HomeScreen extends StatelessWidget {
  final void Function(int) onGoTab;
  const HomeScreen({super.key, required this.onGoTab});

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: app,
        builder: (context, _) {
          final act = app.active;
          final done = app.finished;
          final w0 = weekStartOf(DateTime.now());
          final week = done.where((s) => !DateTime.fromMillisecondsSinceEpoch(s.startedAt).isBefore(w0)).toList();
          final trained = week.map((s) => (DateTime.fromMillisecondsSinceEpoch(s.startedAt).weekday - 1) % 7).toSet();
          final last = done.isEmpty ? null : done.first;
          final next = app.routines.isEmpty ? null : app.routines.first;
          return ListView(padding: const EdgeInsets.fromLTRB(16, 8, 16, 24), children: [
            const SizedBox(height: 8),
            const Text('GymTrack', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800)),
            Text(done.isEmpty ? 'Empieza tu primer entrenamiento' : '${week.length} entrenamientos esta semana · racha ${weekStreak(app.sessions, DateTime.now())} sem.',
                style: const TextStyle(color: C.text2)),
            const SizedBox(height: 16),
            GCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Text('ESTA SEMANA', style: TextStyle(fontSize: 12, letterSpacing: 0.8, color: C.muted)),
                const SizedBox(height: 10),
                Row(children: [
                  for (var i = 0; i < 7; i++)
                    Expanded(
                      child: Container(
                        height: 8,
                        margin: const EdgeInsets.symmetric(horizontal: 3),
                        decoration: BoxDecoration(color: trained.contains(i) ? C.primary : C.surface2, borderRadius: BorderRadius.circular(4)),
                      ),
                    ),
                ]),
                const SizedBox(height: 6),
                Row(children: [
                  for (final d in const ['L', 'M', 'X', 'J', 'V', 'S', 'D']) Expanded(child: Center(child: Text(d, style: const TextStyle(fontSize: 12, color: C.muted)))),
                ]),
              ]),
            ),
            if (act != null)
              GButton('CONTINUAR ENTRENAMIENTO', icon: Icons.play_arrow, onPressed: () => openWorkout(context))
            else ...[
              if (next != null)
                GButton('INICIAR ${next.name.toUpperCase()}', icon: Icons.play_arrow, onPressed: () {
                  app.startSession(next);
                  openWorkout(context);
                }),
              const SizedBox(height: 10),
              next != null
                  ? GSecondary('Entrenamiento libre', onPressed: () {
                      app.startSession(null);
                      openWorkout(context);
                    })
                  : GButton('ENTRENAMIENTO LIBRE', icon: Icons.play_arrow, onPressed: () {
                      app.startSession(null);
                      openWorkout(context);
                    }),
            ],
            if (last != null) ...[
              const Label('Último entrenamiento'),
              GCard(
                onTap: () => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => SessionScreen(sessionId: last.id))),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(last.name, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                  Text('${dayText(last.startedAt)} · ${completedSets(last)} series · ${weightText(sessionVolume(last), app.settings.unit).replaceFirst('— ', '0 ')}',
                      style: const TextStyle(color: C.text2)),
                ]),
              ),
            ],
            if (app.routines.isEmpty)
              GCard(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const Text('Crea tu primera rutina', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                  const SizedBox(height: 4),
                  const Text('Usa una plantilla (Push, Pull, Piernas…) o empieza desde cero.', style: TextStyle(color: C.text2)),
                  const SizedBox(height: 10),
                  GSecondary('Ir a rutinas', block: false, onPressed: () => onGoTab(1)),
                ]),
              ),
          ]);
        },
      );
}
