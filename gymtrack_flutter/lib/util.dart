import 'models.dart';

String clock(num sec) {
  final s = sec.floor().clamp(0, 1 << 30);
  final h = s ~/ 3600, m = (s % 3600) ~/ 60, r = s % 60;
  return h > 0 ? '$h:${m.toString().padLeft(2, '0')}:${r.toString().padLeft(2, '0')}' : '$m:${r.toString().padLeft(2, '0')}';
}

String mmss(int s) => '${(s ~/ 60).toString().padLeft(2, '0')}:${(s % 60).toString().padLeft(2, '0')}';

String numText(double? v, [int max = 1]) {
  if (v == null || v.isNaN) return '';
  final f = (v * _pow10(max)).round() / _pow10(max);
  var t = f.toStringAsFixed(max);
  if (t.contains('.')) t = t.replaceFirst(RegExp(r'0+$'), '').replaceFirst(RegExp(r'\.$'), '');
  return t;
}

double _pow10(int n) {
  var r = 1.0;
  for (var i = 0; i < n; i++) {
    r *= 10;
  }
  return r;
}

String weightText(double? kg, String unit) => kg == null ? '—' : '${numText(kgToUnit(kg, unit))} $unit';

double? parseNum(String s) => double.tryParse(s.trim().replaceAll(',', '.'));

const _days = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
const _months = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

String dayText(int ms) {
  final d = DateTime.fromMillisecondsSinceEpoch(ms);
  return '${_days[d.weekday - 1]} ${d.day} ${_months[d.month - 1]}';
}

String fullDate(int ms) {
  final d = DateTime.fromMillisecondsSinceEpoch(ms);
  return '${d.day} ${_months[d.month - 1]} ${d.year}';
}

const muscles = {
  'CHEST': 'Pecho', 'BACK': 'Espalda', 'SHOULDERS': 'Hombros', 'BICEPS': 'Bíceps', 'TRICEPS': 'Tríceps', 'FOREARMS': 'Antebrazos',
  'QUADS': 'Cuádriceps', 'HAMSTRINGS': 'Isquiotibiales', 'GLUTES': 'Glúteos', 'CALVES': 'Gemelos', 'CORE': 'Abdominales y core', 'FULL_BODY': 'Cuerpo completo',
};
const equipmentNames = {
  'BARBELL': 'Barra', 'DUMBBELL': 'Mancuernas', 'MACHINE': 'Máquina', 'CABLE': 'Polea', 'BODYWEIGHT': 'Peso corporal', 'BANDS': 'Bandas', 'OTHER': 'Otros',
};
const trackingNames = {
  'WEIGHT_REPS': 'Peso y repeticiones', 'BODYWEIGHT': 'Peso corporal', 'DURATION': 'Duración', 'DISTANCE': 'Distancia y tiempo',
};
const setTypeNames = {'N': 'Normal', 'W': 'Calentamiento', 'D': 'Drop set', 'F': 'Al fallo'};

/// Opciones típicas de descanso con el valor actual incluido (p. ej. 75 s), ordenadas.
List<int> restOptions(List<int> base, int current) => {...base, current}.toList()..sort();
