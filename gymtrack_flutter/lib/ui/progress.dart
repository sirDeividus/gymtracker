import 'package:flutter/material.dart';

import '../domain.dart';
import '../models.dart';
import '../state.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets.dart';
import 'body.dart';

class ProgressScreen extends StatefulWidget {
  const ProgressScreen({super.key});
  @override
  State<ProgressScreen> createState() => _ProgressScreenState();
}

class _ProgressScreenState extends State<ProgressScreen> {
  String? cur;
  String metric = 'e1rm';

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: app,
        builder: (context, _) {
          final done = app.finished;
          final unit = app.settings.unit;
          final used = <String, String>{};
          for (final s in done) {
            for (final e in s.exercises) {
              if (e.sets.any((x) => x.completed && isWorking(x))) used[e.exerciseId] = e.name;
            }
          }
          if (used.isNotEmpty && !used.containsKey(cur)) cur = used.keys.first;
          return ListView(padding: const EdgeInsets.fromLTRB(16, 8, 16, 24), children: [
            const Text('Progreso', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800)),
            const SizedBox(height: 8),
            GCard(
              onTap: () => Navigator.push(context, MaterialPageRoute<void>(builder: (_) => const BodyScreen())),
              child: const Row(children: [
                Icon(Icons.photo_camera, color: C.primary),
                SizedBox(width: 12),
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('FOTOS Y PESO CORPORAL', style: TextStyle(fontWeight: FontWeight.w700)),
                    Text('Compara tus cambios · solo en tu teléfono', style: TextStyle(color: C.text2, fontSize: 13)),
                  ]),
                ),
              ]),
            ),
            if (done.isEmpty)
              const Empty(Icons.show_chart, 'Aún no hay datos', 'Finaliza tu primer entrenamiento para ver la evolución de tus cargas y tus récords.')
            else ..._content(done, used, unit),
          ]);
        },
      );

  List<Widget> _content(List<Session> done, Map<String, String> used, String unit) {
    final pts = <(int, double)>[];
    for (final s in done.reversed) {
      final e = s.exercises.where((x) => x.exerciseId == cur).firstOrNull;
      if (e == null) continue;
      final b = bestOf(e);
      final v = switch (metric) {
        'weight' => b.weight,
        'reps' => b.reps.toDouble(),
        'volume' => exerciseVolume(e),
        _ => b.e1rm,
      };
      if (v > 0) pts.add((s.startedAt, metric == 'reps' ? v : kgToUnit(v, unit)));
    }
    var bw = 0.0, b1 = 0.0, br = 0;
    for (final s in done) {
      for (final e in s.exercises.where((x) => x.exerciseId == cur)) {
        final b = bestOf(e);
        bw = bw > b.weight ? bw : b.weight;
        b1 = b1 > b.e1rm ? b1 : b.e1rm;
        br = br > b.reps ? br : b.reps;
      }
    }
    final w0 = weekStartOf(DateTime.now());
    final weeks = [
      for (var i = 7; i >= 0; i--)
        (() {
          final a = w0.subtract(Duration(days: 7 * i));
          final b = a.add(const Duration(days: 7));
          final n = done.where((s) {
            final d = DateTime.fromMillisecondsSinceEpoch(s.startedAt);
            return !d.isBefore(a) && d.isBefore(b);
          }).length;
          return (a.day, n);
        })(),
    ];
    return [
      const Label('Evolución por ejercicio'),
      GCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          DropdownButtonFormField<String>(
            initialValue: cur,
            isExpanded: true,
            items: [for (final e in used.entries) DropdownMenuItem(value: e.key, child: Text(e.value))],
            onChanged: (v) => setState(() => cur = v),
          ),
          const SizedBox(height: 10),
          DropdownButtonFormField<String>(
            initialValue: metric,
            items: const [
              DropdownMenuItem(value: 'e1rm', child: Text('1RM estimado')),
              DropdownMenuItem(value: 'weight', child: Text('Peso máximo')),
              DropdownMenuItem(value: 'reps', child: Text('Repeticiones máx.')),
              DropdownMenuItem(value: 'volume', child: Text('Volumen del ejercicio')),
            ],
            onChanged: (v) => setState(() => metric = v ?? 'e1rm'),
          ),
          const SizedBox(height: 14),
          if (pts.length >= 2) LineChart(points: [for (final p in pts) p.$2], labels: (dayText(pts.first.$1), dayText(pts.last.$1)), unit: metric == 'reps' ? '' : unit)
          else const Text('Necesitas al menos 2 sesiones con este ejercicio para ver la gráfica.', style: TextStyle(color: C.text2)),
        ]),
      ),
      GCard(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('Récords', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
          _kv('Peso máximo', weightText(bw, unit)),
          _kv('1RM estimado', weightText(b1, unit)),
          _kv('Repeticiones máx.', '$br'),
        ]),
      ),
      const Label('Sesiones por semana'),
      GCard(child: BarChart(data: weeks)),
    ];
  }

  Widget _kv(String k, String v) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Text(k), Text(v, style: const TextStyle(fontWeight: FontWeight.w700))]),
      );
}

/// Gráfica de línea con área y último punto resaltado.
class LineChart extends StatelessWidget {
  final List<double> points;
  final (String, String) labels;
  final String unit;
  const LineChart({super.key, required this.points, required this.labels, required this.unit});
  @override
  Widget build(BuildContext context) => SizedBox(height: 170, width: double.infinity, child: CustomPaint(painter: _LinePainter(points, labels, unit)));
}

class _LinePainter extends CustomPainter {
  final List<double> v;
  final (String, String) labels;
  final String unit;
  _LinePainter(this.v, this.labels, this.unit);

  void _text(Canvas c, String t, Offset o) {
    final tp = TextPainter(text: TextSpan(text: t, style: const TextStyle(color: C.text2, fontSize: 11)), textDirection: TextDirection.ltr)..layout();
    tp.paint(c, o);
  }

  @override
  void paint(Canvas canvas, Size size) {
    if (v.isEmpty) return;
    final mn = v.reduce((a, b) => a < b ? a : b), mx = v.reduce((a, b) => a > b ? a : b);
    final span = (mx - mn) == 0 ? 1.0 : mx - mn;
    const pad = 12.0;
    final w = size.width, h = size.height;
    Offset pt(int i) => Offset(pad + i * (w - pad * 2) / (v.length - 1).clamp(1, 1 << 20), h - 28 - ((v[i] - mn) / span) * (h - 56));
    final path = Path()..moveTo(pt(0).dx, pt(0).dy);
    for (var i = 1; i < v.length; i++) {
      path.lineTo(pt(i).dx, pt(i).dy);
    }
    final area = Path.from(path)..lineTo(pt(v.length - 1).dx, h - 28)..lineTo(pt(0).dx, h - 28)..close();
    canvas.drawPath(area, Paint()..shader = LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [C.primary.withValues(alpha: 0.25), C.primary.withValues(alpha: 0)]).createShader(Rect.fromLTWH(0, 0, w, h)));
    canvas.drawPath(path, Paint()..color = C.primary..style = PaintingStyle.stroke..strokeWidth = 2.5..strokeJoin = StrokeJoin.round);
    for (var i = 0; i < v.length; i++) {
      canvas.drawCircle(pt(i), 3.5, Paint()..color = C.primary);
    }
    _text(canvas, '${numText(mn)} $unit  ·  ${labels.$1}', Offset(4, h - 16));
    _text(canvas, '${numText(mx)} $unit  ·  ${labels.$2}', Offset(w - 150, 0));
  }

  @override
  bool shouldRepaint(covariant _LinePainter old) => old.v != v;
}

class BarChart extends StatelessWidget {
  final List<(int, int)> data;
  const BarChart({super.key, required this.data});
  @override
  Widget build(BuildContext context) {
    final mx = data.map((d) => d.$2).fold<int>(1, (a, b) => a > b ? a : b);
    return SizedBox(
      height: 120,
      child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
        for (var i = 0; i < data.length; i++)
          Expanded(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: Column(mainAxisAlignment: MainAxisAlignment.end, children: [
                if (data[i].$2 > 0) Text('${data[i].$2}', style: const TextStyle(fontSize: 11, color: C.text2)),
                Container(
                  height: (data[i].$2 / mx) * 70 + 2,
                  decoration: BoxDecoration(color: i == data.length - 1 ? C.primary : const Color(0xFF3A3A44), borderRadius: BorderRadius.circular(4)),
                ),
                const SizedBox(height: 4),
                Text('${data[i].$1}', style: const TextStyle(fontSize: 11, color: C.text2)),
              ]),
            ),
          ),
      ]),
    );
  }
}
