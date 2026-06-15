package com.efesert.wallreel.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.efesert.wallreel.R
import com.efesert.wallreel.playlist.PlaylistController

/**
 * 1x1 ana ekran widget'ı. Dokununca çift dokunma ile AYNI işi yapar:
 * playlist'te bir sonraki fotoğrafa geçer. Çift dokunmayı kullanmak
 * istemeyen kullanıcılar için alternatif bir tetikleyici.
 */
class NextPhotoWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_next_photo)
            views.setOnClickPendingIntent(R.id.widget_root, nextPendingIntent(context))
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_NEXT) {
            // Çift dokunmadaki davranışın aynısı: sıradaki fotoğrafa geç.
            PlaylistController.advance(context)
        }
        super.onReceive(context, intent)
    }

    private fun nextPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, NextPhotoWidget::class.java).setAction(ACTION_NEXT)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val ACTION_NEXT = "com.efesert.wallreel.WIDGET_NEXT"
    }
}
