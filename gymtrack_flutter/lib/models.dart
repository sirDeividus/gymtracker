// Modelos de datos. Todo se guarda como JSON en un solo archivo local (sin cuenta ni servidor).
import 'dart:math' as math;

String uid() => '${DateTime.now().microsecondsSinceEpoch.toRadixString(36)}${math.Random().nextInt(1 << 32).toRadixString(36)}';

double? _d(dynamic v) => v == null ? null : (v as num).toDouble();

class Exercise {
  final String id, name, muscle, equipment, movement, tracking, description, cues;
  final List<String> secondary;
  final bool unilateral, custom;
  const Exercise({
    required this.id,
    required this.name,
    required this.muscle,
    this.secondary = const [],
    required this.equipment,
    this.movement = 'ISOLATION',
    required this.tracking,
    this.unilateral = false,
    this.description = '',
    this.cues = '',
    this.custom = false,
  });

  factory Exercise.fromJson(Map<String, dynamic> j) => Exercise(
        id: j['id'] as String,
        name: j['name'] as String,
        muscle: j['muscle'] as String,
        secondary: (j['secondary'] as List? ?? const []).cast<String>(),
        equipment: j['equipment'] as String,
        movement: (j['movement'] as String?) ?? 'ISOLATION',
        tracking: j['tracking'] as String,
        unilateral: (j['unilateral'] as bool?) ?? false,
        description: (j['description'] as String?) ?? '',
        cues: (j['cues'] as String?) ?? '',
        custom: (j['custom'] as bool?) ?? false,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'muscle': muscle,
        'secondary': secondary,
        'equipment': equipment,
        'movement': movement,
        'tracking': tracking,
        'unilateral': unilateral,
        'description': description,
        'cues': cues,
        'custom': custom,
      };
}

class RoutineItem {
  String id, exerciseId;
  int sets, repMin, repMax, restSec, cycles, cycleRestSec;
  int? durationSec;
  double? startWeightKg;
  RoutineItem({
    String? id,
    required this.exerciseId,
    this.sets = 3,
    this.repMin = 8,
    this.repMax = 12,
    this.restSec = 90,
    this.durationSec,
    this.startWeightKg,
    this.cycles = 1,
    this.cycleRestSec = 30,
  }) : id = id ?? uid();

  factory RoutineItem.fromJson(Map<String, dynamic> j) => RoutineItem(
        id: j['id'] as String?,
        exerciseId: j['exerciseId'] as String,
        sets: (j['sets'] as num?)?.toInt() ?? 3,
        repMin: (j['repMin'] as num?)?.toInt() ?? 8,
        repMax: (j['repMax'] as num?)?.toInt() ?? 12,
        restSec: (j['restSec'] as num?)?.toInt() ?? 90,
        durationSec: (j['durationSec'] as num?)?.toInt(),
        startWeightKg: _d(j['startWeightKg']),
        cycles: (j['cycles'] as num?)?.toInt() ?? 1,
        cycleRestSec: (j['cycleRestSec'] as num?)?.toInt() ?? 30,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'exerciseId': exerciseId,
        'sets': sets,
        'repMin': repMin,
        'repMax': repMax,
        'restSec': restSec,
        'durationSec': durationSec,
        'startWeightKg': startWeightKg,
        'cycles': cycles,
        'cycleRestSec': cycleRestSec,
      };
}

class Routine {
  String id, name;
  List<RoutineItem> items;
  Routine({String? id, this.name = 'Nueva rutina', List<RoutineItem>? items})
      : id = id ?? uid(),
        items = items ?? [];

  factory Routine.fromJson(Map<String, dynamic> j) => Routine(
        id: j['id'] as String,
        name: (j['name'] as String?) ?? '',
        items: (j['items'] as List? ?? const []).map((e) => RoutineItem.fromJson(e as Map<String, dynamic>)).toList(),
      );

  Map<String, dynamic> toJson() => {'id': id, 'name': name, 'items': items.map((e) => e.toJson()).toList()};
}

class PrevSet {
  final double? weightKg;
  final int? reps, durationSec;
  const PrevSet({this.weightKg, this.reps, this.durationSec});
  Map<String, dynamic> toJson() => {'weightKg': weightKg, 'reps': reps, 'durationSec': durationSec};
  factory PrevSet.fromJson(Map<String, dynamic> j) =>
      PrevSet(weightKg: _d(j['weightKg']), reps: (j['reps'] as num?)?.toInt(), durationSec: (j['durationSec'] as num?)?.toInt());
}

class SetRec {
  String id;
  String type; // N normal · W calentamiento · D drop · F fallo
  double? weightKg, distanceM;
  int? reps, durationSec;
  double? rpe;
  bool completed;
  PrevSet? prev;
  SetRec({String? id, this.type = 'N', this.weightKg, this.reps, this.durationSec, this.distanceM, this.rpe, this.completed = false, this.prev})
      : id = id ?? uid();

  factory SetRec.fromJson(Map<String, dynamic> j) => SetRec(
        id: j['id'] as String?,
        type: (j['type'] as String?) ?? 'N',
        weightKg: _d(j['weightKg']),
        reps: (j['reps'] as num?)?.toInt(),
        durationSec: (j['durationSec'] as num?)?.toInt(),
        distanceM: _d(j['distanceM']),
        rpe: _d(j['rpe']),
        completed: (j['completed'] as bool?) ?? false,
        prev: j['prev'] == null ? null : PrevSet.fromJson(j['prev'] as Map<String, dynamic>),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'type': type,
        'weightKg': weightKg,
        'reps': reps,
        'durationSec': durationSec,
        'distanceM': distanceM,
        'rpe': rpe,
        'completed': completed,
        'prev': prev?.toJson(),
      };
}

class SessionExercise {
  String id, exerciseId, name, tracking, muscle, equipment, notes;
  int restSec, repMin, repMax, cycles, cycleRestSec, prepSec;
  int? durationSec;
  String? suggestionKind, suggestionText;
  double? suggestionWeightKg;
  bool suggestionDismissed;
  List<SetRec> sets;
  SessionExercise({
    String? id,
    required this.exerciseId,
    required this.name,
    required this.tracking,
    required this.muscle,
    required this.equipment,
    this.notes = '',
    this.restSec = 90,
    this.repMin = 8,
    this.repMax = 12,
    this.cycles = 1,
    this.cycleRestSec = 30,
    this.prepSec = 10,
    this.durationSec,
    this.suggestionKind,
    this.suggestionText,
    this.suggestionWeightKg,
    this.suggestionDismissed = false,
    List<SetRec>? sets,
  })  : id = id ?? uid(),
        sets = sets ?? [];

  factory SessionExercise.fromJson(Map<String, dynamic> j) => SessionExercise(
        id: j['id'] as String?,
        exerciseId: j['exerciseId'] as String,
        name: j['name'] as String,
        tracking: j['tracking'] as String,
        muscle: (j['muscle'] as String?) ?? 'FULL_BODY',
        equipment: (j['equipment'] as String?) ?? 'OTHER',
        notes: (j['notes'] as String?) ?? '',
        restSec: (j['restSec'] as num?)?.toInt() ?? 90,
        repMin: (j['repMin'] as num?)?.toInt() ?? 8,
        repMax: (j['repMax'] as num?)?.toInt() ?? 12,
        cycles: (j['cycles'] as num?)?.toInt() ?? 1,
        cycleRestSec: (j['cycleRestSec'] as num?)?.toInt() ?? 30,
        prepSec: (j['prepSec'] as num?)?.toInt() ?? 10,
        durationSec: (j['durationSec'] as num?)?.toInt(),
        suggestionKind: j['suggestionKind'] as String?,
        suggestionText: j['suggestionText'] as String?,
        suggestionWeightKg: _d(j['suggestionWeightKg']),
        suggestionDismissed: (j['suggestionDismissed'] as bool?) ?? false,
        sets: (j['sets'] as List? ?? const []).map((e) => SetRec.fromJson(e as Map<String, dynamic>)).toList(),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'exerciseId': exerciseId,
        'name': name,
        'tracking': tracking,
        'muscle': muscle,
        'equipment': equipment,
        'notes': notes,
        'restSec': restSec,
        'repMin': repMin,
        'repMax': repMax,
        'cycles': cycles,
        'cycleRestSec': cycleRestSec,
        'prepSec': prepSec,
        'durationSec': durationSec,
        'suggestionKind': suggestionKind,
        'suggestionText': suggestionText,
        'suggestionWeightKg': suggestionWeightKg,
        'suggestionDismissed': suggestionDismissed,
        'sets': sets.map((e) => e.toJson()).toList(),
      };
}

class Session {
  String id, name, status; // active | finished
  String? routineId, notes;
  int startedAt;
  int? endedAt, restEndsAt;
  int restTotal;
  List<SessionExercise> exercises;
  Session({
    String? id,
    required this.name,
    this.routineId,
    int? startedAt,
    this.endedAt,
    this.status = 'active',
    this.restEndsAt,
    this.restTotal = 0,
    this.notes,
    List<SessionExercise>? exercises,
  })  : id = id ?? uid(),
        startedAt = startedAt ?? DateTime.now().millisecondsSinceEpoch,
        exercises = exercises ?? [];

  factory Session.fromJson(Map<String, dynamic> j) => Session(
        id: j['id'] as String,
        name: (j['name'] as String?) ?? 'Entrenamiento',
        routineId: j['routineId'] as String?,
        startedAt: (j['startedAt'] as num).toInt(),
        endedAt: (j['endedAt'] as num?)?.toInt(),
        status: (j['status'] as String?) ?? 'finished',
        restEndsAt: (j['restEndsAt'] as num?)?.toInt(),
        restTotal: (j['restTotal'] as num?)?.toInt() ?? 0,
        notes: j['notes'] as String?,
        exercises: (j['exercises'] as List? ?? const []).map((e) => SessionExercise.fromJson(e as Map<String, dynamic>)).toList(),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'routineId': routineId,
        'startedAt': startedAt,
        'endedAt': endedAt,
        'status': status,
        'restEndsAt': restEndsAt,
        'restTotal': restTotal,
        'notes': notes,
        'exercises': exercises.map((e) => e.toJson()).toList(),
      };
}

class WeightEntry {
  final String id;
  final int measuredAt;
  final double weightKg;
  WeightEntry({String? id, required this.measuredAt, required this.weightKg}) : id = id ?? uid();
  factory WeightEntry.fromJson(Map<String, dynamic> j) =>
      WeightEntry(id: j['id'] as String?, measuredAt: (j['measuredAt'] as num).toInt(), weightKg: (j['weightKg'] as num).toDouble());
  Map<String, dynamic> toJson() => {'id': id, 'measuredAt': measuredAt, 'weightKg': weightKg};
}

class PhotoEntry {
  final String id, pose, note, fileName;
  final int takenAt;
  final double? weightKg;
  PhotoEntry({String? id, required this.takenAt, required this.pose, this.weightKg, this.note = '', required this.fileName}) : id = id ?? uid();
  factory PhotoEntry.fromJson(Map<String, dynamic> j) => PhotoEntry(
        id: j['id'] as String?,
        takenAt: (j['takenAt'] as num).toInt(),
        pose: (j['pose'] as String?) ?? 'OTHER',
        weightKg: _d(j['weightKg']),
        note: (j['note'] as String?) ?? '',
        fileName: j['fileName'] as String,
      );
  Map<String, dynamic> toJson() => {'id': id, 'takenAt': takenAt, 'pose': pose, 'weightKg': weightKg, 'note': note, 'fileName': fileName};
}

class Settings {
  String unit; // kg | lb
  int restSec, changeRestSec;
  bool sound, voice, autoRest;
  Settings({this.unit = 'kg', this.restSec = 90, this.changeRestSec = 120, this.sound = true, this.voice = true, this.autoRest = true});
  factory Settings.fromJson(Map<String, dynamic> j) => Settings(
        unit: (j['unit'] as String?) ?? 'kg',
        restSec: (j['restSec'] as num?)?.toInt() ?? 90,
        changeRestSec: (j['changeRestSec'] as num?)?.toInt() ?? 120,
        sound: (j['sound'] as bool?) ?? true,
        voice: (j['voice'] as bool?) ?? true,
        autoRest: (j['autoRest'] as bool?) ?? true,
      );
  Map<String, dynamic> toJson() =>
      {'unit': unit, 'restSec': restSec, 'changeRestSec': changeRestSec, 'sound': sound, 'voice': voice, 'autoRest': autoRest};
}

double kgToUnit(double kg, String unit) => unit == 'lb' ? kg / 0.45359237 : kg;
double unitToKg(double v, String unit) => unit == 'lb' ? v * 0.45359237 : v;
