import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:path_provider/path_provider.dart';

import 'domain.dart';
import 'models.dart';

/// Estado global de la app. Todo vive en un archivo JSON local (más una carpeta con las fotos).
class AppState extends ChangeNotifier {
  Settings settings = Settings();
  List<Exercise> library = [];
  List<Exercise> custom = [];
  List<Routine> routines = [];
  List<Session> sessions = [];
  List<WeightEntry> weights = [];
  List<PhotoEntry> photos = [];
  List<Map<String, dynamic>> templates = [];

  Directory? _dir;
  Timer? _saveTimer;
  bool loaded = false;

  Directory get photosDir => Directory('${_dir!.path}/photos');

  /// [dir] permite usar una carpeta temporal en las pruebas.
  Future<void> load({Directory? dir, String? exercisesJson, String? templatesJson}) async {
    _dir = dir ?? await getApplicationDocumentsDirectory();
    final ex = exercisesJson ?? await rootBundle.loadString('assets/exercises.json');
    library = (jsonDecode(ex) as List).map((e) => Exercise.fromJson(e as Map<String, dynamic>)).toList();
    final tp = templatesJson ?? await rootBundle.loadString('assets/templates.json');
    templates = (jsonDecode(tp) as List).cast<Map<String, dynamic>>().where((t) => (t['items'] as List).isNotEmpty).toList();
    final f = File('${_dir!.path}/gymtrack.json');
    if (await f.exists()) {
      try {
        _apply(jsonDecode(await f.readAsString()) as Map<String, dynamic>);
      } catch (_) {
        // Archivo dañado: se conserva una copia y se empieza limpio.
        await f.copy('${_dir!.path}/gymtrack.corrupt.json');
      }
    }
    await photosDir.create(recursive: true);
    loaded = true;
    notifyListeners();
  }

  void _apply(Map<String, dynamic> j) {
    settings = Settings.fromJson((j['settings'] as Map<String, dynamic>?) ?? {});
    custom = (j['custom'] as List? ?? const []).map((e) => Exercise.fromJson(e as Map<String, dynamic>)).toList();
    routines = (j['routines'] as List? ?? const []).map((e) => Routine.fromJson(e as Map<String, dynamic>)).toList();
    sessions = (j['sessions'] as List? ?? const []).map((e) => Session.fromJson(e as Map<String, dynamic>)).toList();
    weights = (j['weights'] as List? ?? const []).map((e) => WeightEntry.fromJson(e as Map<String, dynamic>)).toList();
    photos = (j['photos'] as List? ?? const []).map((e) => PhotoEntry.fromJson(e as Map<String, dynamic>)).toList();
  }

  Map<String, dynamic> toJson({bool withPhotos = true}) => {
        'app': 'gymtrack-flutter',
        'version': 1,
        'settings': settings.toJson(),
        'custom': custom.map((e) => e.toJson()).toList(),
        'routines': routines.map((e) => e.toJson()).toList(),
        'sessions': sessions.map((e) => e.toJson()).toList(),
        'weights': weights.map((e) => e.toJson()).toList(),
        if (withPhotos) 'photos': photos.map((e) => e.toJson()).toList(),
      };

  /// Guarda en disco (con un pequeño retardo para agrupar cambios seguidos).
  void save({bool now = false}) {
    _saveTimer?.cancel();
    if (_dir == null) return;
    Future<void> write() async {
      final f = File('${_dir!.path}/gymtrack.json');
      final tmp = File('${_dir!.path}/gymtrack.json.tmp');
      await tmp.writeAsString(jsonEncode(toJson()));
      await tmp.rename(f.path);
    }

    if (now) {
      unawaited(write());
    } else {
      _saveTimer = Timer(const Duration(milliseconds: 300), () => unawaited(write()));
    }
  }

  /// Guarda y avisa a la interfaz.
  void commit({bool now = false}) {
    save(now: now);
    notifyListeners();
  }

  /// Sustituye los datos por los de una copia de seguridad (solo rutinas, sesiones, ejercicios propios, peso y ajustes).
  Future<void> importBackup(String text) async {
    final j = jsonDecode(text) as Map<String, dynamic>;
    if (j['app'] != 'gymtrack-flutter' && j['app'] != 'gymtrack-web') throw const FormatException('Archivo no válido');
    if (j['sessions'] is! List || j['routines'] is! List) throw const FormatException('Archivo no válido');
    final keepPhotos = photos;
    _apply(j);
    if (j['photos'] == null) photos = keepPhotos;
    commit(now: true);
  }

  // ------------------------------------------------------------ Consultas

  List<Exercise> get allExercises => [...library, ...custom];
  Exercise? exerciseById(String id) {
    for (final e in allExercises) {
      if (e.id == id) return e;
    }
    return null;
  }

  Session? get active {
    for (final s in sessions) {
      if (s.status == 'active') return s;
    }
    return null;
  }

  List<Session> get finished => sessions.where((s) => s.status == 'finished').toList()..sort((a, b) => b.startedAt.compareTo(a.startedAt));

  // ------------------------------------------------------------ Rutinas

  Routine newRoutine() {
    final r = Routine();
    routines.add(r);
    commit();
    return r;
  }

  RoutineItem newItem(String exerciseId) {
    final ex = exerciseById(exerciseId);
    final dur = ex?.tracking == 'DURATION';
    return RoutineItem(exerciseId: exerciseId, restSec: settings.restSec, repMin: dur ? 1 : 8, repMax: dur ? 1 : 12, durationSec: dur ? 30 : null);
  }

  Routine fromTemplate(Map<String, dynamic> t) {
    final r = Routine(
      name: t['name'] as String,
      items: (t['items'] as List).map((raw) {
        final it = raw as Map<String, dynamic>;
        final base = newItem(it['exerciseId'] as String);
        base
          ..sets = (it['sets'] as num).toInt()
          ..repMin = (it['repMin'] as num).toInt()
          ..repMax = (it['repMax'] as num).toInt()
          ..restSec = (it['restSec'] as num?)?.toInt() ?? 90;
        return base;
      }).toList(),
    );
    routines.add(r);
    commit();
    return r;
  }

  // ------------------------------------------------------------ Entrenamiento

  SessionExercise buildExercise(String exerciseId, [RoutineItem? item]) {
    final def = exerciseById(exerciseId);
    final it = item ?? RoutineItem(exerciseId: exerciseId, restSec: settings.restSec, durationSec: def?.tracking == 'DURATION' ? 30 : null);
    final prev = lastPerformance(sessions, exerciseId);
    final prevWork = prev == null ? <SetRec>[] : prev.sets.where((s) => s.completed && isWorking(s)).toList();
    final sets = <SetRec>[
      for (var i = 0; i < it.sets; i++)
        SetRec(
          prev: i < prevWork.length ? PrevSet(weightKg: prevWork[i].weightKg, reps: prevWork[i].reps, durationSec: prevWork[i].durationSec) : null,
        ),
    ];
    final tracking = def?.tracking ?? 'WEIGHT_REPS';
    final sug = suggest(prev, tracking, it.repMax, stepFor(def?.equipment ?? 'OTHER', def?.muscle ?? 'FULL_BODY'));
    return SessionExercise(
      exerciseId: exerciseId,
      name: def?.name ?? 'Ejercicio',
      tracking: tracking,
      muscle: def?.muscle ?? 'FULL_BODY',
      equipment: def?.equipment ?? 'OTHER',
      restSec: it.restSec,
      repMin: it.repMin,
      repMax: it.repMax,
      durationSec: it.durationSec,
      cycles: it.cycles,
      cycleRestSec: it.cycleRestSec,
      suggestionKind: sug?.kind,
      suggestionText: sug?.text,
      suggestionWeightKg: sug?.weightKg,
      sets: sets,
    );
  }

  Session startSession(Routine? routine) {
    final s = Session(name: routine?.name ?? 'Entrenamiento libre', routineId: routine?.id, exercises: [
      for (final it in routine?.items ?? <RoutineItem>[]) buildExercise(it.exerciseId, it),
    ]);
    sessions.add(s);
    commit(now: true);
    return s;
  }

  void discardSession(Session s) {
    sessions.removeWhere((x) => x.id == s.id);
    commit(now: true);
  }

  void finishSession(Session s, String notes) {
    for (final e in s.exercises) {
      e.sets = e.sets.where((x) => x.completed).toList();
    }
    s.exercises = s.exercises.where((e) => e.sets.isNotEmpty).toList();
    s.notes = notes;
    s.status = 'finished';
    s.endedAt = DateTime.now().millisecondsSinceEpoch;
    s.restEndsAt = null;
    commit(now: true);
  }

  // ------------------------------------------------------------ Peso y fotos

  void addWeight(double kg) {
    weights.add(WeightEntry(measuredAt: DateTime.now().millisecondsSinceEpoch, weightKg: kg));
    commit(now: true);
  }

  Future<void> addPhoto({required List<int> bytes, required String pose, double? weightKg, String note = ''}) async {
    final id = uid();
    final name = '$id.jpg';
    await File('${photosDir.path}/$name').writeAsBytes(bytes);
    photos.insert(0, PhotoEntry(id: id, takenAt: DateTime.now().millisecondsSinceEpoch, pose: pose, weightKg: weightKg, note: note, fileName: name));
    commit(now: true);
  }

  Future<void> deletePhoto(PhotoEntry p) async {
    final f = File('${photosDir.path}/${p.fileName}');
    if (await f.exists()) await f.delete();
    photos.removeWhere((x) => x.id == p.id);
    commit(now: true);
  }

  File photoFile(PhotoEntry p) => File('${photosDir.path}/${p.fileName}');
}

final AppState app = AppState();
