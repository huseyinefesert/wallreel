package com.efesert.wallreel.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.efesert.wallreel.playlist.Prefs

/**
 * Otomatik geçişi AlarmManager ile planlar. setAndAllowWhileIdle tek seferlik tetiklediği
 * için her tetiklemede AlarmReceiver içinden yeniden planlanır (Doze altında da çalışır,
 * özel "exact alarm" izni gerektirmez).
 */
object WallpaperScheduler {

    const val ACTION_ADVANCE = "com.efesert.wallreel.ADVANCE"

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_ADVANCE)
        return PendingIntent.getBroadcast(
            context, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Prefs'teki aralığa göre bir sonraki geçişi planlar. */
    fun schedule(context: Context) {
        val minutes = Prefs.intervalMinutes(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)
        if (minutes <= 0) {
            am.cancel(pi)
            return
        }
        val triggerAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }
}
