import 'package:flutter/material.dart';

import '../domain.dart';
import '../models.dart';
import '../state.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets.dart';

class HistoryScreen extends StatelessWidget {
  const HistoryScreen({super.key});

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: app,
        builder: (context, _) {
          final list = app.finished;
          final unit = app.settings.unit;
          return ListView(padding: const EdgeInsets.fromLTRB(16, 8, 16, 24), children: [
            const Text('Historial', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800)),
            const SizedBox(height: 8),
            if (list.isEmpty) const Empty(Icons.calendar_month, 'Aún no hay entrenamientos', 'Cuando finalices uno, aparecerá aquí.'),
            for (final s in list)
              GCard(
                onTap: () => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => SessionScreen(sessionId: s.id))),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(children: [
                    Expanded(child: Text(s.name, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700))),
                    Text(dayText(s.startedAt), style: const TextStyle(color: C.text2)),
                  ]),
                  Text('${clock(((s.endedAt ?? s.startedAt) - s.startedAt) / 1000)} · ${completedSets(s)} series · ${weightText(sessionVolume(s), unit).replaceFirst('— ', '0 ')}',
                      style: const TextStyle(color: C.text2)),
                ]),
              ),
          ]);
        },
      );
}

class SessionScreen extends StatelessWidget {
  final String sessionId;
  const SessionScreen({super.key, required this.sessionId});

  @override
  Widget build(BuildContext context) {
    final s = app.sessions.where((x) => x.id == sessionId).firstOrNull;
    final unit = app.settings.unit;
    if (s == null) return Scaffold(appBar: AppBar(), body: const Empty(Icons.search, 'No encontrada', ''));
    return Scaffold(
      appBar: AppBar(title: Text(s.name)),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 0, 16, 24), children: [
        Text('${fullDate(s.startedAt)} · ${clock(((s.endedAt ?? s.startedAt) - s.startedAt) / 1000)} · ${weightText(sessionVolume(s), unit).replaceFirst('— ', '0 ')}',
            style: const TextStyle(color: C.text2)),
        if ((s.notes ?? '').isNotEmpty) GCard(child: Text(s.notes!)),
        const SizedBox(height: 8),
        for (final e in s.exercises)
          GCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(e.name, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
              for (final x in e.sets)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Row(children: [
                    Expanded(
                      child: Text(
                        x.type == 'N' ? 'Serie ${e.sets.where((y) => y.type == 'N').toList().indexOf(x) + 1}' : setTypeNames[x.type]!,
                        style: TextStyle(color: x.type == 'W' ? C.warn : C.text),
                      ),
                    ),
                    Text(switch (e.tracking) {
                      'DURATION' => '${x.durationSec} s',
                      'DISTANCE' => '${numText((x.distanceM ?? 0) / 1000, 2)} km · ${clock(x.durationSec ?? 0)}',
                      'BODYWEIGHT' => '${x.reps} reps',
                      _ => '${numText(x.weightKg == null ? null : kgToUnit(x.weightKg!, unit))} $unit × ${x.reps}',
                    }),
                  ]),
                ),
            ]),
          ),
        GSecondary('Eliminar sesión', danger: true, onPressed: () async {
          if (await confirm(context, '¿Eliminar sesión?', 'No se puede deshacer.', ok: 'Eliminar', danger: true)) {
            app.sessions.removeWhere((x) => x.id == s.id);
            app.commit(now: true);
            if (context.mounted) Navigator.pop(context);
          }
        }),
      ]),
    );
  }
}
