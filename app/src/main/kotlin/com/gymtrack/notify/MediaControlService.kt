package com.gymtrack.notify

import android.service.notification.NotificationListenerService

/**
 * Servicio vacío: Android solo deja leer las sesiones de música activas (YT Music, Spotify…) a una app con
 * «Acceso a notificaciones». GymTrack no lee ni guarda el contenido de ninguna notificación: solo usa las
 * sesiones multimedia para mostrar qué suena y ofrecer pausa / anterior / siguiente.
 */
class MediaControlService : NotificationListenerService()
