import 'package:flutter/material.dart';

import '../models.dart';
import '../state.dart';
import '../theme.dart';
import '../util.dart';
import '../widgets.dart';

/// Selector de ejercicios a pantalla completa con búsqueda y filtros por músculo y equipo.
Future<String?> pickExercise(BuildContext context) => Navigator.push<String>(context, MaterialPageRoute<String>(fullscreenDialog: true, builder: (_) => const _Picker()));

class _Picker extends StatefulWidget {
  const _Picker();
  @override
  State<_Picker> createState() => _PickerState();
}

class _PickerState extends State<_Picker> {
  String q = '';
  String? muscle, equip;

  String _norm(String s) => s.toLowerCase().replaceAll('á', 'a').replaceAll('é', 'e').replaceAll('í', 'i').replaceAll('ó', 'o').replaceAll('ú', 'u').replaceAll('ñ', 'n');

  @override
  Widget build(BuildContext context) {
    final items = app.allExercises.where((e) {
      if (muscle != null && e.muscle != muscle && !e.secondary.contains(muscle)) return false;
      if (equip != null && e.equipment != equip) return false;
      return q.isEmpty || _norm(e.name).contains(_norm(q));
    }).toList();
    return Scaffold(
      appBar: AppBar(
        title: const Text('Agregar ejercicio'),
        actions: [TextButton(onPressed: _create, child: const Text('Crear', style: TextStyle(color: C.primary)))],
      ),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
          child: TextField(
            decoration: const InputDecoration(hintText: 'Buscar', prefixIcon: Icon(Icons.search)),
            onChanged: (v) => setState(() => q = v),
          ),
        ),
        Padding(
          padding: const EdgeInsets.only(left: 16, bottom: 6),
          child: Chips<String?>(
            items: [null, ...muscles.keys],
            selected: {muscle},
            label: (k) => k == null ? 'Todos' : muscles[k]!,
            onTap: (k) => setState(() => muscle = muscle == k ? null : k),
          ),
        ),
        Padding(
          padding: const EdgeInsets.only(left: 16, bottom: 6),
          child: Chips<String?>(
            items: [null, ...equipmentNames.keys],
            selected: {equip},
            label: (k) => k == null ? 'Todos' : equipmentNames[k]!,
            onTap: (k) => setState(() => equip = equip == k ? null : k),
          ),
        ),
        Padding(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4), child: Align(alignment: Alignment.centerLeft, child: Text('${items.length} ejercicios', style: const TextStyle(color: C.muted, fontSize: 13)))),
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
            itemCount: items.length,
            itemBuilder: (context, i) {
              final e = items[i];
              return GCard(
                onTap: () => Navigator.pop(context, e.id),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(e.name, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
                  Text('${muscles[e.muscle]} · ${equipmentNames[e.equipment]}${e.custom ? ' · Personalizado' : ''}', style: const TextStyle(color: C.text2)),
                ]),
              );
            },
          ),
        ),
      ]),
    );
  }

  Future<void> _create() async {
    final name = TextEditingController();
    var mus = 'CHEST', eq = 'BARBELL', tr = 'WEIGHT_REPS';
    final id = await showSheet<String>(context, 'Nuevo ejercicio', (ctx) => StatefulBuilder(builder: (ctx, set) {
          DropdownButtonFormField<String> dd(String v, Map<String, String> m, void Function(String) on) => DropdownButtonFormField<String>(
                initialValue: v,
                items: [for (final e in m.entries) DropdownMenuItem(value: e.key, child: Text(e.value))],
                onChanged: (x) => set(() => on(x ?? v)),
              );
          return Column(children: [
            TextField(controller: name, decoration: const InputDecoration(hintText: 'Nombre')),
            const SizedBox(height: 10),
            dd(mus, muscles, (x) => mus = x),
            const SizedBox(height: 10),
            dd(eq, equipmentNames, (x) => eq = x),
            const SizedBox(height: 10),
            dd(tr, trackingNames, (x) => tr = x),
            const SizedBox(height: 14),
            GButton('Guardar', onPressed: () {
              if (name.text.trim().isEmpty) {
                toast(ctx, 'Escribe un nombre');
                return;
              }
              final ex = Exercise(id: 'custom_${uid()}', name: name.text.trim(), muscle: mus, equipment: eq, tracking: tr, custom: true);
              app.custom.add(ex);
              app.commit(now: true);
              Navigator.pop(ctx, ex.id);
            }),
          ]);
        }));
    if (id != null && mounted) Navigator.pop(context, id);
  }
}
