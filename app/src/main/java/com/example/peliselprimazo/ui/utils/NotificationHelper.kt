package com.example.peliselprimazo.ui.utils

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.peliselprimazo.MainActivity
import com.example.peliselprimazo.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun showNewEpisodeNotification(movieTitle: String, season: Int?, episode: Int?, movieId: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("MOVIE_ID", movieId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (season != null && episode != null) {
            "¡Nuevo episodio disponible! T$season E$episode"
        } else {
            "¡Nuevo contenido disponible!"
        }

        val notification = NotificationCompat.Builder(context, "content_updates")
            .setSmallIcon(android.R.drawable.ic_menu_slideshow) // Cambiar por icono de la app si existe
            .setContentTitle(movieTitle)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(movieId, notification)
    }
}
