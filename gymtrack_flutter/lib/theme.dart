import 'package:flutter/material.dart';

/// Paleta «Cyber-Premium» de GymTrack: negro, rojo eléctrico y neutros.
class C {
  static const bg = Color(0xFF09090B);
  static const surface = Color(0xFF131316);
  static const surface2 = Color(0xFF1C1C21);
  static const outline = Color(0xFF2A2A31);
  static const primary = Color(0xFFFF1E46);
  static const primary2 = Color(0xFFFF5A3C);
  static const text = Color(0xFFF4F4F5);
  static const text2 = Color(0xFFA1A1AA);
  static const muted = Color(0xFF71717A);
  static const ok = Color(0xFF34C759);
  static const warn = Color(0xFFF5A524);
  static const err = Color(0xFFFF453A);
  static const rest = Color(0xFF38BDF8);
  static const gradient = LinearGradient(colors: [primary, primary2], begin: Alignment.topLeft, end: Alignment.bottomRight);
}

ThemeData buildTheme() {
  final base = ThemeData.dark(useMaterial3: true);
  return base.copyWith(
    scaffoldBackgroundColor: C.bg,
    colorScheme: const ColorScheme.dark(
      primary: C.primary,
      secondary: C.primary2,
      surface: C.surface,
      error: C.err,
      onSurface: C.text,
    ),
    canvasColor: C.bg,
    dividerColor: C.outline,
    appBarTheme: const AppBarTheme(backgroundColor: C.bg, elevation: 0, scrolledUnderElevation: 0, centerTitle: false),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: C.bg,
      indicatorColor: C.primary.withValues(alpha: 0.14),
      labelTextStyle: WidgetStateProperty.all(const TextStyle(fontSize: 10, letterSpacing: 0.3, fontWeight: FontWeight.w600)),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: C.surface,
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: C.outline)),
      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: C.outline)),
      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: C.primary)),
      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
    ),
    textTheme: base.textTheme.apply(bodyColor: C.text, displayColor: C.text),
    bottomSheetTheme: const BottomSheetThemeData(
      backgroundColor: C.surface,
      showDragHandle: true,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(20))),
    ),
    dialogTheme: DialogThemeData(backgroundColor: C.surface2, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20))),
    snackBarTheme: const SnackBarThemeData(backgroundColor: C.surface2, contentTextStyle: TextStyle(color: C.text), behavior: SnackBarBehavior.floating),
  );
}
