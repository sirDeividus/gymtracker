import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../models.dart';
import '../state.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets.dart';
import 'progress.dart';

const _poses = {'FRONT': 'Frente', 'SIDE': 'Perfil', 'BACK': 'Espalda', 'OTHER': 'Otra'};

/// Peso corporal y fotos de progreso. Todo queda en el teléfono.
class BodyScreen extends StatefulWidget {
  const BodyScreen({super.key});
  @override
  State<BodyScreen> createState() => _BodyScreenState();
}

class _BodyScreenState extends State<BodyScreen> {
  bool comparing = false;
  List<String> picked = [];

  @override
  Widget build(BuildContext context) {
    final unit = app.settings.unit;
    final ws = [...app.weights]..sort((a, b) => a.measuredAt.compareTo(b.measuredAt));
    return Scaffold(
      appBar: AppBar(title: const Text('Fotos y peso')),
      body: ListenableBuilder(
        listenable: app,
        builder: (context, _) => ListView(padding: const EdgeInsets.fromLTRB(16, 0, 16, 24), children: [
          const Text('Guardado solo en tu teléfono', style: TextStyle(color: C.text2)),
          const SizedBox(height: 12),
          GCard(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('PESO CORPORAL', style: TextStyle(fontSize: 12, letterSpacing: 0.8, color: C.muted)),
              const SizedBox(height: 8),
              if (ws.isEmpty)
                const Text('Registra tu peso para ver la tendencia.', style: TextStyle(color: C.text2))
              else ...[
                Text(weightText(ws.last.weightKg, unit), style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w700)),
                if (ws.length > 1) ...[
                  Text('${ws.last.weightKg - ws.first.weightKg > 0 ? '+' : ''}${numText(kgToUnit(ws.last.weightKg - ws.first.weightKg, unit))} $unit desde el primer registro', style: const TextStyle(color: C.text2)),
                  const SizedBox(height: 10),
                  LineChart(points: [for (final w in ws) kgToUnit(w.weightKg, unit)], labels: (dayText(ws.first.measuredAt), dayText(ws.last.measuredAt)), unit: unit),
                ],
              ],
              const SizedBox(height: 10),
              GSecondary('REGISTRAR PESO', icon: Icons.monitor_weight_outlined, onPressed: _logWeight),
            ]),
          ),
          const Label('Fotos de progreso'),
          Row(children: [
            Expanded(child: GButton('AÑADIR FOTO', icon: Icons.add_a_photo, onPressed: _addPhoto)),
            if (app.photos.length >= 2) ...[
              const SizedBox(width: 10),
              Expanded(child: GSecondary(comparing ? 'CANCELAR' : 'COMPARAR', onPressed: () => setState(() { comparing = !comparing; picked = []; }))),
            ],
          ]),
          if (comparing) Padding(padding: const EdgeInsets.only(top: 8), child: Text('Elige dos fotos (${picked.length}/2)', style: const TextStyle(color: C.muted, fontSize: 13))),
          const SizedBox(height: 10),
          if (app.photos.isEmpty)
            const Empty(Icons.photo_library, 'Aún no hay fotos', 'Haz una foto cada pocas semanas con la misma luz y pose. Se guardan privadas en tu teléfono.')
          else
            GridView.count(
              crossAxisCount: 3,
              mainAxisSpacing: 8,
              crossAxisSpacing: 8,
              childAspectRatio: 0.75,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              children: [for (final p in app.photos) _tile(p)],
            ),
        ]),
      ),
    );
  }

  Widget _tile(PhotoEntry p) => GestureDetector(
        onTap: () {
          if (comparing) {
            setState(() {
              if (picked.contains(p.id)) {
                picked.remove(p.id);
              } else if (picked.length < 2) {
                picked.add(p.id);
              }
            });
            if (picked.length == 2) _compare();
          } else {
            _detail(p);
          }
        },
        child: ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: Stack(fit: StackFit.expand, children: [
            Image.file(app.photoFile(p), fit: BoxFit.cover, cacheWidth: 400, errorBuilder: (_, _, _) => Container(color: C.surface2)),
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: Container(color: Colors.black54, padding: const EdgeInsets.all(5), child: Text('${dayText(p.takenAt)} · ${_poses[p.pose]}', style: const TextStyle(fontSize: 11))),
            ),
            if (picked.contains(p.id)) Container(decoration: BoxDecoration(border: Border.all(color: C.primary, width: 3), color: C.primary.withValues(alpha: 0.25))),
          ]),
        ),
      );

  void _logWeight() {
    final unit = app.settings.unit;
    final ctl = TextEditingController();
    showSheet<void>(context, 'Registrar peso', (ctx) => Column(children: [
          NumField(controller: ctl, hint: unit, decimal: true),
          const SizedBox(height: 12),
          GButton('Guardar', onPressed: () {
            final v = parseNum(ctl.text);
            final kg = v == null ? null : unitToKg(v, unit);
            if (kg == null || kg < 20 || kg > 500) {
              toast(ctx, 'El peso debe estar entre 20 y 500 kg');
              return;
            }
            app.addWeight(kg);
            Navigator.pop(ctx);
          }),
        ]));
  }

  void _addPhoto() {
    final unit = app.settings.unit;
    var pose = 'FRONT';
    final wt = TextEditingController();
    final note = TextEditingController();
    showSheet<void>(context, 'Nueva foto', (ctx) => StatefulBuilder(builder: (ctx, set) {
          Future<void> take(ImageSource src) async {
            final p = parseNum(wt.text);
            final kg = p == null ? null : unitToKg(p, unit);
            if (wt.text.isNotEmpty && (kg == null || kg < 20 || kg > 500)) {
              toast(ctx, 'Peso no válido');
              return;
            }
            try {
              final f = await ImagePicker().pickImage(source: src, maxWidth: 1600, maxHeight: 1600, imageQuality: 85);
              if (f == null) return;
              await app.addPhoto(bytes: await f.readAsBytes(), pose: pose, weightKg: kg, note: note.text.trim());
              if (ctx.mounted) Navigator.pop(ctx);
            } catch (e) {
              if (ctx.mounted) toast(ctx, 'No se pudo guardar la foto: $e');
            }
          }

          return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Chips<String>(items: _poses.keys.toList(), selected: {pose}, label: (k) => _poses[k]!, onTap: (k) => set(() => pose = k)),
            const SizedBox(height: 12),
            NumField(controller: wt, hint: 'Peso ($unit, opcional)', decimal: true),
            const SizedBox(height: 10),
            TextField(controller: note, decoration: const InputDecoration(hintText: 'Nota (opcional)')),
            const SizedBox(height: 14),
            Row(children: [
              Expanded(child: GButton('CÁMARA', icon: Icons.photo_camera, onPressed: () => take(ImageSource.camera))),
              const SizedBox(width: 10),
              Expanded(child: GSecondary('GALERÍA', icon: Icons.photo_library, onPressed: () => take(ImageSource.gallery))),
            ]),
          ]);
        }));
  }

  void _detail(PhotoEntry p) {
    final unit = app.settings.unit;
    showSheet<void>(context, '${_poses[p.pose]} · ${fullDate(p.takenAt)}', (ctx) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          ClipRRect(borderRadius: BorderRadius.circular(12), child: Image.file(app.photoFile(p), fit: BoxFit.contain)),
          if (p.weightKg != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(weightText(p.weightKg, unit), style: const TextStyle(color: C.text2))),
          if (p.note.isNotEmpty) Text(p.note, style: const TextStyle(color: C.text2)),
          const SizedBox(height: 12),
          GSecondary('Eliminar foto', danger: true, onPressed: () async {
            await app.deletePhoto(p);
            if (ctx.mounted) Navigator.pop(ctx);
          }),
        ]));
  }

  void _compare() {
    final unit = app.settings.unit;
    final two = picked.map((id) => app.photos.firstWhere((p) => p.id == id)).toList()..sort((a, b) => a.takenAt.compareTo(b.takenAt));
    final a = two[0], b = two[1];
    final days = DateTime.fromMillisecondsSinceEpoch(b.takenAt).difference(DateTime.fromMillisecondsSinceEpoch(a.takenAt)).inDays;
    final dw = a.weightKg != null && b.weightKg != null ? b.weightKg! - a.weightKg! : null;
    setState(() { comparing = false; picked = []; });
    showSheet<void>(context, 'Antes y después', (ctx) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            for (final (p, l) in [(a, 'ANTES'), (b, 'DESPUÉS')])
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    AspectRatio(aspectRatio: 0.75, child: ClipRRect(borderRadius: BorderRadius.circular(12), child: Image.file(app.photoFile(p), fit: BoxFit.cover, width: double.infinity))),
                    Text(l, style: const TextStyle(fontSize: 12, color: C.muted)),
                    Text(fullDate(p.takenAt), style: const TextStyle(fontSize: 13)),
                  ]),
                ),
              ),
          ]),
          const SizedBox(height: 10),
          Text('$days días de diferencia${dw != null ? ' · ${dw > 0 ? '+' : ''}${numText(kgToUnit(dw, unit))} $unit' : ''}', style: const TextStyle(color: C.text2)),
        ]));
  }
}
