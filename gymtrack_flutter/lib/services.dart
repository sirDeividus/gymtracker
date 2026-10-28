import 'dart:async';
import 'dart:math' as math;

import 'package:audio_session/audio_session.dart';
import 'package:audioplayers/audioplayers.dart' show AudioPlayer, BytesSource, ReleaseMode;
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:timezone/data/latest_all.dart' as tzdata;
import 'package:timezone/timezone.dart' as tz;
import 'package:wakelock_plus/wakelock_plus.dart';

/// Pitidos y voz para los avisos. Baja el volumen de la música de otras apps mientras suena un aviso
/// (audio ducking) y lo devuelve al terminar.
class Cues {
  Cues._();
  static final Cues instance = Cues._();

  // Se crean al iniciar (no al construir) para que las pruebas sin plugins no fallen.
  FlutterTts? _tts;
  AudioPlayer? _player;
  AudioSession? _session;
  Timer? _release;
  bool _ready = false;

  Future<void> init() async {
    if (_ready) return;
    try {
      _tts = FlutterTts();
      _player = AudioPlayer();
      _session = await AudioSession.instance;
      await _session!.configure(AudioSessionConfiguration(
        avAudioSessionCategory: AVAudioSessionCategory.playback,
        avAudioSessionCategoryOptions: AVAudioSessionCategoryOptions.duckOthers,
        avAudioSessionMode: AVAudioSessionMode.spokenAudio,
        androidAudioAttributes: const AndroidAudioAttributes(
          contentType: AndroidAudioContentType.speech,
          usage: AndroidAudioUsage.assistanceNavigationGuidance,
        ),
        androidAudioFocusGainType: AndroidAudioFocusGainType.gainTransientMayDuck,
        androidWillPauseWhenDucked: false,
      ));
      await _tts!.setLanguage('es-ES');
      await _tts!.setSpeechRate(0.5);
      await _tts!.awaitSpeakCompletion(false);
      await _player!.setReleaseMode(ReleaseMode.stop);
    } catch (e) {
      debugPrint('Cues.init: $e');
    }
    _ready = true;
  }

  Future<void> _duck(int holdMs) async {
    _release?.cancel();
    try {
      await _session?.setActive(true);
    } catch (_) {}
    _release = Timer(Duration(milliseconds: holdMs), () async {
      try {
        await _session?.setActive(false);
      } catch (_) {}
    });
  }

  Future<void> speak(String text) async {
    try {
      await _duck(2200);
      await _tts?.stop();
      await _tts?.speak(text);
    } catch (e) {
      debugPrint('speak: $e');
    }
  }

  Future<void> _tone(double hz, int ms, {double volume = 0.6}) async {
    try {
      await _duck(ms + 600);
      await _player?.stop();
      await _player?.play(BytesSource(_wav(hz, ms), mimeType: 'audio/wav'), volume: volume);
    } catch (e) {
      debugPrint('tone: $e');
    }
  }

  /// Pitido de aviso (fin de tramo / descanso).
  Future<void> beep({int times = 1}) async {
    for (var i = 0; i < times; i++) {
      await _tone(880, 180);
      if (i < times - 1) await Future<void>.delayed(const Duration(milliseconds: 260));
    }
  }

  /// Pitido corto de cuenta atrás.
  Future<void> tick() => _tone(660, 90, volume: 0.5);

  static const _numbers = {3: 'tres', 2: 'dos', 1: 'uno'};

  /// Cuenta atrás: voz con el número o, si no hay voz, un pitido corto.
  Future<void> countdown(int n, {required bool voice, required bool sound}) async {
    if (voice) {
      await speak(_numbers[n] ?? '$n');
    } else if (sound) {
      await tick();
    }
  }

  /// WAV de 16 bits generado en memoria (sin archivos de audio en la app).
  static Uint8List _wav(double hz, int ms) {
    const rate = 22050;
    final n = rate * ms ~/ 1000;
    final data = ByteData(44 + n * 2);
    void str(int o, String s) {
      for (var i = 0; i < s.length; i++) {
        data.setUint8(o + i, s.codeUnitAt(i));
      }
    }

    str(0, 'RIFF');
    data.setUint32(4, 36 + n * 2, Endian.little);
    str(8, 'WAVEfmt ');
    data.setUint32(16, 16, Endian.little);
    data.setUint16(20, 1, Endian.little);
    data.setUint16(22, 1, Endian.little);
    data.setUint32(24, rate, Endian.little);
    data.setUint32(28, rate * 2, Endian.little);
    data.setUint16(32, 2, Endian.little);
    data.setUint16(34, 16, Endian.little);
    str(36, 'data');
    data.setUint32(40, n * 2, Endian.little);
    for (var i = 0; i < n; i++) {
      final env = math.min(1.0, math.min(i / 400, (n - i) / 400)); // entrada/salida suave: sin chasquidos
      final v = math.sin(2 * math.pi * hz * i / rate) * 0.6 * env;
      data.setInt16(44 + i * 2, (v * 32767).round(), Endian.little);
    }
    return data.buffer.asUint8List();
  }
}

/// Aviso de fin de descanso con la app en segundo plano o la pantalla bloqueada (notificación local programada).
class RestNotifier {
  RestNotifier._();
  static final RestNotifier instance = RestNotifier._();
  final FlutterLocalNotificationsPlugin _plugin = FlutterLocalNotificationsPlugin();
  bool _ready = false;
  static const _id = 4242;

  Future<void> init() async {
    if (_ready) return;
    try {
      tzdata.initializeTimeZones();
      await _plugin.initialize(
        settings: const InitializationSettings(
          android: AndroidInitializationSettings('@mipmap/ic_launcher'),
          iOS: DarwinInitializationSettings(requestAlertPermission: false, requestBadgePermission: false, requestSoundPermission: false),
        ),
      );
      _ready = true;
    } catch (e) {
      debugPrint('RestNotifier.init: $e');
    }
  }

  /// Pide permiso para avisar (iOS lo muestra una sola vez).
  Future<void> requestPermission() async {
    await init();
    try {
      await _plugin.resolvePlatformSpecificImplementation<IOSFlutterLocalNotificationsPlugin>()?.requestPermissions(alert: true, sound: true);
      await _plugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()?.requestNotificationsPermission();
    } catch (e) {
      debugPrint('permiso: $e');
    }
  }

  Future<void> schedule(DateTime at) async {
    await init();
    if (!_ready) return;
    try {
      await _plugin.cancel(id: _id);
      await _plugin.zonedSchedule(
        id: _id,
        title: 'Descanso terminado',
        body: 'Es hora de la siguiente serie',
        scheduledDate: tz.TZDateTime.from(at, tz.UTC),
        notificationDetails: const NotificationDetails(
          android: AndroidNotificationDetails('rest', 'Fin del descanso', importance: Importance.high, priority: Priority.high),
          iOS: DarwinNotificationDetails(presentSound: true, presentAlert: true, interruptionLevel: InterruptionLevel.timeSensitive),
        ),
        androidScheduleMode: AndroidScheduleMode.inexactAllowWhileIdle,
      );
    } catch (e) {
      debugPrint('schedule: $e');
    }
  }

  Future<void> cancel() async {
    if (!_ready) return;
    try {
      await _plugin.cancel(id: _id);
    } catch (_) {}
  }
}


/// Mantiene la pantalla encendida (sin romper si el plugin no está disponible, p. ej. en pruebas).
Future<void> keepAwake(bool on) async {
  try {
    if (on) {
      await WakelockPlus.enable();
    } else {
      await WakelockPlus.disable();
    }
  } catch (_) {}
}
