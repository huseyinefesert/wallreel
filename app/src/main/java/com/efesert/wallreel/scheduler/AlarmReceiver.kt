package com.efesert.wallreel.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.efesert.wallreel.playlist.PlaylistController

/** Zamanlayıcı tetiklendiğinde sıradaki resme geçer ve bir sonraki geçişi yeniden planlar. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        PlaylistController.advance(context)
        WallpaperScheduler.schedule(context)
    }
}
