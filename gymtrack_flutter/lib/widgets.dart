import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'theme.dart';

class GCard extends StatelessWidget {
  final Widget child;
  final VoidCallback? onTap;
  final EdgeInsets padding;
  final Color? color;
  const GCard({super.key, required this.child, this.onTap, this.padding = const EdgeInsets.all(14), this.color});

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Material(
          color: color ?? C.surface,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16), side: const BorderSide(color: C.outline)),
          child: InkWell(
            borderRadius: BorderRadius.circular(16),
            onTap: onTap,
            child: Padding(padding: padding, child: SizedBox(width: double.infinity, child: child)),
          ),
        ),
      );
}

/// Botón principal en cápsula con degradado rojo.
class GButton extends StatelessWidget {
  final String text;
  final VoidCallback? onPressed;
  final IconData? icon;
  final bool block;
  const GButton(this.text, {super.key, required this.onPressed, this.icon, this.block = true});

  @override
  Widget build(BuildContext context) {
    final child = Opacity(
      opacity: onPressed == null ? 0.5 : 1,
      child: Container(
        height: 52,
        padding: const EdgeInsets.symmetric(horizontal: 22),
        decoration: BoxDecoration(
          gradient: C.gradient,
          borderRadius: BorderRadius.circular(26),
          boxShadow: [BoxShadow(color: C.primary.withValues(alpha: 0.25), blurRadius: 18, offset: const Offset(0, 6))],
        ),
        child: Row(mainAxisSize: block ? MainAxisSize.max : MainAxisSize.min, mainAxisAlignment: MainAxisAlignment.center, children: [
          if (icon != null) ...[Icon(icon, color: Colors.white, size: 20), const SizedBox(width: 8)],
          Flexible(child: Text(text, overflow: TextOverflow.ellipsis, maxLines: 1, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700, fontSize: 15, letterSpacing: 0.4))),
        ]),
      ),
    );
    return GestureDetector(behavior: HitTestBehavior.opaque, onTap: onPressed, child: child);
  }
}

class GSecondary extends StatelessWidget {
  final String text;
  final VoidCallback? onPressed;
  final IconData? icon;
  final bool danger, block;
  const GSecondary(this.text, {super.key, required this.onPressed, this.icon, this.danger = false, this.block = true});

  @override
  Widget build(BuildContext context) {
    final b = FilledButton.tonal(
      onPressed: onPressed,
      style: FilledButton.styleFrom(
        backgroundColor: C.surface2,
        foregroundColor: danger ? C.err : C.text,
        minimumSize: const Size(0, 48),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        textStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15),
      ),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        if (icon != null) ...[Icon(icon, size: 18), const SizedBox(width: 8)],
        Flexible(child: Text(text, overflow: TextOverflow.ellipsis)),
      ]),
    );
    return block ? SizedBox(width: double.infinity, child: b) : b;
  }
}

class Label extends StatelessWidget {
  final String text;
  const Label(this.text, {super.key});
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 20, bottom: 8),
        child: Text(text.toUpperCase(), style: const TextStyle(fontSize: 12, letterSpacing: 0.8, color: C.muted)),
      );
}

class Empty extends StatelessWidget {
  final IconData icon;
  final String title, message;
  const Empty(this.icon, this.title, this.message, {super.key});
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.all(32),
        child: Column(children: [
          Container(width: 72, height: 72, decoration: const BoxDecoration(color: C.surface2, shape: BoxShape.circle), child: Icon(icon, size: 34, color: C.muted)),
          const SizedBox(height: 14),
          Text(title, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600), textAlign: TextAlign.center),
          const SizedBox(height: 6),
          Text(message, style: const TextStyle(color: C.text2), textAlign: TextAlign.center),
        ]),
      );
}

class Chips<T> extends StatelessWidget {
  final List<T> items;
  final Set<T> selected;
  final String Function(T) label;
  final void Function(T) onTap;
  const Chips({super.key, required this.items, required this.selected, required this.label, required this.onTap});
  @override
  Widget build(BuildContext context) => SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(children: [
          for (final i in items)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: ChoiceChip(
                label: Text(label(i)),
                selected: selected.contains(i),
                onSelected: (_) => onTap(i),
                showCheckmark: false,
                selectedColor: C.primary,
                backgroundColor: C.surface,
                side: const BorderSide(color: C.outline),
                shape: const StadiumBorder(),
                labelStyle: TextStyle(color: selected.contains(i) ? Colors.white : C.text),
              ),
            ),
        ]),
      );
}

void toast(BuildContext context, String msg) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(msg), duration: const Duration(milliseconds: 2400)));
}

/// Hoja inferior con título y opciones; cada opción es (texto, acción, ¿destructiva?).
Future<void> showOptions(BuildContext context, String title, List<(String, VoidCallback, {bool danger})> options, {Widget? extra}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    builder: (ctx) => SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
          const SizedBox(height: 10),
          for (final o in options)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: GSecondary(o.$1, danger: o.danger, onPressed: () {
                Navigator.pop(ctx);
                o.$2();
              }),
            ),
          if (extra != null) extra,
        ]),
      ),
    ),
  );
}

/// Hoja inferior con contenido propio (formularios).
Future<T?> showSheet<T>(BuildContext context, String title, Widget Function(BuildContext ctx) body) {
  return showModalBottomSheet<T>(
    context: context,
    isScrollControlled: true,
    builder: (ctx) => Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(ctx).viewInsets.bottom),
      child: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
            const SizedBox(height: 12),
            body(ctx),
          ]),
        ),
      ),
    ),
  );
}

Future<bool> confirm(BuildContext context, String title, String message, {String ok = 'Aceptar', bool danger = false}) async {
  final r = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(title),
      content: Text(message, style: const TextStyle(color: C.text2)),
      actions: [
        TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
        TextButton(onPressed: () => Navigator.pop(ctx, true), child: Text(ok, style: TextStyle(color: danger ? C.err : C.primary))),
      ],
    ),
  );
  return r ?? false;
}

/// Campo numérico simple (sin flechas, teclado numérico).
class NumField extends StatelessWidget {
  final TextEditingController controller;
  final String? hint, label;
  final bool decimal;
  final TextAlign align;
  final ValueChanged<String>? onChanged;
  final bool dense;
  const NumField({super.key, required this.controller, this.hint, this.label, this.decimal = false, this.align = TextAlign.start, this.onChanged, this.dense = false});
  @override
  Widget build(BuildContext context) => TextField(
        controller: controller,
        keyboardType: TextInputType.numberWithOptions(decimal: decimal),
        inputFormatters: [FilteringTextInputFormatter.allow(decimal ? RegExp(r'[0-9.,]') : RegExp(r'[0-9]'))],
        textAlign: align,
        onChanged: onChanged,
        style: TextStyle(fontWeight: dense ? FontWeight.w700 : FontWeight.w500, fontSize: dense ? 16 : 16),
        decoration: InputDecoration(hintText: hint, labelText: label, isDense: dense, contentPadding: dense ? const EdgeInsets.symmetric(horizontal: 4, vertical: 12) : null),
      );
}

/// Descanso a medida: minutos y segundos escritos directamente.
Future<int?> customRestDialog(BuildContext context, String title, int initialSec, {int minSec = 0, int maxSec = 1800}) {
  final m = TextEditingController(text: '${initialSec ~/ 60}');
  final s = TextEditingController(text: '${initialSec % 60}');
  return showDialog<int>(
    context: context,
    builder: (ctx) => StatefulBuilder(builder: (ctx, set) {
      final total = (int.tryParse(m.text) ?? 0) * 60 + (int.tryParse(s.text) ?? 0);
      final ok = total >= minSec && total <= maxSec;
      return AlertDialog(
        title: Text(title),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          Row(children: [
            Expanded(child: NumField(controller: m, label: 'Minutos', onChanged: (_) => set(() {}))),
            const SizedBox(width: 12),
            Expanded(child: NumField(controller: s, label: 'Segundos', onChanged: (_) => set(() {}))),
          ]),
          const SizedBox(height: 8),
          Text(ok ? 'Total: ${total ~/ 60}:${(total % 60).toString().padLeft(2, '0')}' : 'Debe estar entre $minSec s y ${maxSec ~/ 60} min',
              style: TextStyle(color: ok ? C.text2 : C.err, fontSize: 13)),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancelar')),
          TextButton(onPressed: ok ? () => Navigator.pop(ctx, total) : null, child: const Text('Guardar', style: TextStyle(color: C.primary))),
        ],
      );
    }),
  );
}
