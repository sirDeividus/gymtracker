import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'services.dart';
import 'state.dart';
import 'theme.dart';
import 'ui/history.dart';
import 'ui/home.dart';
import 'ui/progress.dart';
import 'ui/routines.dart';
import 'ui/settings.dart';
import 'ui/workout.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setSystemUIOverlayStyle(SystemUiOverlayStyle.light);
  await app.load();
  // Servicios de aviso: no bloquean el arranque si fallan.
  Cues.instance.init();
  RestNotifier.instance.init();
  runApp(const GymTrackApp());
}

class GymTrackApp extends StatelessWidget {
  const GymTrackApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'GymTrack',
        debugShowCheckedModeBanner: false,
        theme: buildTheme(),
        home: const Shell(),
      );
}

class Shell extends StatefulWidget {
  const Shell({super.key});
  @override
  State<Shell> createState() => _ShellState();
}

class _ShellState extends State<Shell> {
  int tab = 0;

  @override
  void initState() {
    super.initState();
    // Si había un entrenamiento en curso al cerrar la app, se retoma.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (app.active != null && mounted) openWorkout(context);
    });
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      HomeScreen(onGoTab: (i) => setState(() => tab = i)),
      const RoutinesScreen(),
      const HistoryScreen(),
      const ProgressScreen(),
      const SettingsScreen(),
    ];
    return Scaffold(
      body: SafeArea(bottom: false, child: IndexedStack(index: tab, children: pages)),
      bottomNavigationBar: NavigationBar(
        selectedIndex: tab,
        onDestinationSelected: (i) => setState(() => tab = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home), label: 'INICIO'),
          NavigationDestination(icon: Icon(Icons.list_alt), label: 'RUTINAS'),
          NavigationDestination(icon: Icon(Icons.calendar_month_outlined), selectedIcon: Icon(Icons.calendar_month), label: 'HISTORIAL'),
          NavigationDestination(icon: Icon(Icons.show_chart), label: 'PROGRESO'),
          NavigationDestination(icon: Icon(Icons.settings_outlined), selectedIcon: Icon(Icons.settings), label: 'AJUSTES'),
        ],
      ),
    );
  }
}
