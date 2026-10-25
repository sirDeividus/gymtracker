package com.gymtrack.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gymtrack.MainActivity
import com.gymtrack.R
import com.gymtrack.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Aviso de fin de descanso con la app en segundo plano.
 * Usa una alarma inexacta (no requiere permiso de alarmas exactas ni servicio en primer plano):
 * respeta el ahorro de batería de Android. El estado real del temporizador vive en la base de datos.
 */
object RestNotifier {
    private const val CHANNEL_ID = "rest_timer"
    private const val NOTIFICATION_ID = 1001

    /** True mientras la app está visible; en ese caso la UI ya avisa por sí misma. */
    @Volatile
    var inForeground: Boolean = false

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "Descanso entre series", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Avisa cuando termina el descanso"
            nm.createNotificationChannel(ch)
        }
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, RestAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Programa (o cancela si [endsAtMs] es null o ya pasó) la alarma del fin de descanso. */
    fun sync(context: Context, endsAtMs: Long?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(context)
        if (endsAtMs == null || endsAtMs <= System.currentTimeMillis()) {
            am.cancel(pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMs, pi)
        }
    }

    fun vibrate(context: Context) {
        val v = context.getSystemService(Vibrator::class.java) ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 90, 180), -1))
    }

    fun beep() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            Handler(Looper.getMainLooper()).postDelayed({ tg.release() }, 700)
        } catch (_: RuntimeException) {
            // Sin sonido disponible: no es crítico.
        }
    }

    fun notifyRestOver(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.rest_over_title))
            .setContentText(context.getString(R.string.rest_over_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(120_000)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, n)
    }
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (RestNotifier.inForeground) return // la pantalla de entrenamiento ya avisa
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val s = SettingsStore(app).current()
                if (s.vibration) RestNotifier.vibrate(app)
                if (s.sound) RestNotifier.beep()
                if (s.backgroundAlert) RestNotifier.notifyRestOver(app)
            } finally {
                pending.finish()
            }
        }
    }
}
