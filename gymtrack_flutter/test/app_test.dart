import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gymtrack/main.dart';
import 'package:gymtrack/models.dart';
import 'package:gymtrack/state.dart';
import 'package:gymtrack/theme.dart';
import 'package:gymtrack/ui/tabata.dart';

void main() {
  tabataTests();
  testWidgets('recorrido completo: rutina, entrenamiento, serie, descanso bloqueado, finalizar', (tester) async {
    // Las pruebas usan una fuente de prueba muy ancha: se ignoran los avisos de desborde (solo afectan a la fuente de prueba).
    final original = FlutterError.onError;
    FlutterError.onError = (d) {
      if (d.exceptionAsString().contains('overflowed')) return;
      original?.call(d);
    };
    addTearDown(() => FlutterError.onError = original);
    tester.view.physicalSize = const Size(1170, 2532);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);

    await tester.runAsync(() async {
      final dir = await Directory.systemTemp.createTemp('gymtrack_test');
      await app.load(
        dir: dir,
        exercisesJson: File('assets/exercises.json').readAsStringSync(),
        templatesJson: File('assets/templates.json').readAsStringSync(),
      );
    });
    await tester.pumpWidget(const GymTrackApp());
    await tester.pump();

    expect(find.text('GymTrack'), findsOneWidget);
    expect(app.library.length, 94);

    // Plantilla Push -> editar -> iniciar
    await tester.tap(find.text('RUTINAS'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Push'));
    await tester.pumpAndSettle();
    expect(find.text('Editar rutina'), findsOneWidget);
    expect(app.routines.single.items.length, 6);
    await tester.dragUntilVisible(find.text('INICIAR'), find.byType(ListView), const Offset(0, -300));
    await tester.drag(find.byType(ListView), const Offset(0, -250));
    await tester.pump();
    await tester.tap(find.text('INICIAR'));
    await tester.pump(const Duration(milliseconds: 600));
    await tester.pump(const Duration(milliseconds: 600));
    expect(find.text('Terminar'), findsOneWidget);

    // Completar la primera serie: peso y reps
    final fields = find.byType(TextField);
    await tester.enterText(fields.at(0), '60');
    await tester.enterText(fields.at(1), '8');
    await tester.pump();
    await tester.tap(find.byIcon(Icons.check).first);
    await tester.pump();
    expect(app.active!.exercises.first.sets.first.completed, isTrue);
    expect(app.active!.restEndsAt, isNotNull);
    expect(find.textContaining('Mantén para desbloquear'), findsOneWidget);

    // Ajuste del descanso al cambiar de ejercicio con 3 series completadas.
    app.active!.restEndsAt = null;
    await tester.pump(const Duration(milliseconds: 300));

    await tester.tap(find.text('Terminar'));
    await tester.pump(const Duration(milliseconds: 600));
    await tester.pump(const Duration(milliseconds: 600));
    expect(find.text('Finalizar entrenamiento'), findsOneWidget);
    await tester.tap(find.text('Finalizar y guardar'));
    await tester.pump(const Duration(milliseconds: 600));
    await tester.pump(const Duration(milliseconds: 600));
    expect(app.finished.length, 1);
    expect(app.active, isNull);
    // Deja que los temporizadores pendientes terminen.
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 1));
  });
}

// ---------------------------------------------------------------------------
void tabataTests() {
  testWidgets('temporizador Tabata: se abre en pausa con los valores del ejercicio', (tester) async {
    final original = FlutterError.onError;
    FlutterError.onError = (d) {
      if (d.exceptionAsString().contains('overflowed')) return;
      original?.call(d);
    };
    addTearDown(() => FlutterError.onError = original);
    tester.view.physicalSize = const Size(1170, 2532);
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    final ex = SessionExercise(
      exerciseId: 'x', name: 'Tabata abs', tracking: 'DURATION', muscle: 'CORE', equipment: 'BODYWEIGHT',
      durationSec: 20, restSec: 10, cycles: 2, cycleRestSec: 30, prepSec: 10,
      sets: List.generate(8, (_) => SetRec()),
    );
    await tester.pumpWidget(MaterialApp(theme: buildTheme(), home: TabataScreen(ex: ex)));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('PREPÁRATE'), findsWidgets);
    expect(find.text('00:10'), findsOneWidget);
    expect(find.text('RONDAS'), findsOneWidget);
    expect(find.text('CICLOS'), findsOneWidget);
    expect(find.byIcon(Icons.play_arrow), findsOneWidget);
    await tester.tap(find.byIcon(Icons.play_arrow));
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.byIcon(Icons.pause), findsOneWidget);
    expect(ex.sets.length, 16); // 8 rondas × 2 ciclos: se añaden las series que faltan
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 4)); // deja terminar el temporizador de los avisos de audio
  });
}
