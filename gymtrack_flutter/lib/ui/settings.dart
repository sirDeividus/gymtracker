import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

import '../state.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: app,
        builder: (context, _) {
          final s = app.settings;
          return ListView(padding: const EdgeInsets.fromLTRB(16, 8, 16, 24), children: [
            const Text('Ajustes', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800)),
            const Label('Unidades'),
            Chips<String>(items: const ['kg', 'lb'], selected: {s.unit}, label: (u) => u, onTap: (u) { s.unit = u; app.commit(); }),
            const Label('Descanso por defecto'),
            _restChips(context, [30, 45, 60, 90, 120, 180, 240], s.restSec, (v) => s.restSec = v, minSec: 5, title: 'Descanso por defecto'),
            const Label('Descanso al cambiar de ejercicio'),
            _restChips(context, [0, 60, 90, 120, 180, 240, 300], s.changeRestSec, (v) => s.changeRestSec = v, title: 'Descanso al cambiar de ejercicio'),
            const Padding(padding: EdgeInsets.only(top: 6), child: Text('Se usa al completar la última serie de un ejercicio y pasar al siguiente.', style: TextStyle(color: C.muted, fontSize: 13))),
            const Label('Avisos'),
            SwitchListTile(contentPadding: EdgeInsets.zero, title: const Text('Iniciar el descanso al completar una serie'), value: s.autoRest, onChanged: (v) { s.autoRest = v; app.commit(); }),
            SwitchListTile(contentPadding: EdgeInsets.zero, title: const Text('Voz en los avisos'), subtitle: const Text('3, 2, 1 · Descanso · Prepárate · Go', style: TextStyle(color: C.muted)), value: s.voice, onChanged: (v) { s.voice = v; app.commit(); }),
            SwitchListTile(contentPadding: EdgeInsets.zero, title: const Text('Sonido (pitidos)'), value: s.sound, onChanged: (v) { s.sound = v; app.commit(); }),
            const Label('Copia de seguridad'),
            GSecondary('Exportar copia (JSON)', onPressed: () => _export(context)),
            const SizedBox(height: 8),
            GSecondary('Importar copia', onPressed: () => _import(context)),
            const Padding(padding: EdgeInsets.only(top: 8), child: Text('La copia incluye rutinas, historial, ejercicios propios y peso corporal. Las fotos NO se incluyen.', style: TextStyle(color: C.muted, fontSize: 13))),
            const Label('Almacenamiento'),
            const Text('Todo se guarda solo en este teléfono. Si desinstalas la app, se pierde: exporta copias con frecuencia.', style: TextStyle(color: C.muted, fontSize: 13)),
          ]);
        },
      );

  Widget _restChips(BuildContext context, List<int> base, int current, void Function(int) set, {int minSec = 0, required String title}) => Wrap(spacing: 8, runSpacing: 4, children: [
        for (final v in restOptions(base, current))
          ChoiceChip(
            label: Text(v == 0 ? 'Sin descanso' : clock(v)),
            selected: v == current,
            showCheckmark: false,
            selectedColor: C.primary,
            side: const BorderSide(color: C.outline),
            onSelected: (_) {
              set(v);
              app.commit();
            },
          ),
        ActionChip(
          label: const Text('Personalizado…'),
          onPressed: () async {
            final v = await customRestDialog(context, title, current, minSec: minSec);
            if (v != null) {
              set(v);
              app.commit();
            }
          },
        ),
      ]);

  Future<void> _export(BuildContext context) async {
    try {
      final dir = await getTemporaryDirectory();
      final f = File('${dir.path}/gymtrack-${DateTime.now().toIso8601String().substring(0, 10)}.json');
      await f.writeAsString(jsonEncode(app.toJson(withPhotos: false)));
      await SharePlus.instance.share(ShareParams(files: [XFile(f.path)], text: 'Copia GymTrack'));
    } catch (e) {
      if (context.mounted) toast(context, 'No se pudo exportar: $e');
    }
  }

  Future<void> _import(BuildContext context) async {
    try {
      final r = await FilePicker.pickFile(type: FileType.custom, allowedExtensions: ['json']);
      final path = r?.path;
      if (path == null) return;
      if (!context.mounted) return;
      if (!await confirm(context, '¿Reemplazar los datos actuales?', 'Se sustituirán rutinas, historial y peso por los de la copia.', ok: 'Importar', danger: true)) return;
      await app.importBackup(await File(path).readAsString());
      if (context.mounted) toast(context, 'Copia importada');
    } catch (e) {
      if (context.mounted) toast(context, 'No se pudo leer la copia: $e');
    }
  }
}
