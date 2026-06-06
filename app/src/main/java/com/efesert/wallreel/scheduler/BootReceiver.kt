package com.efesert.wallreel.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Cihaz yeniden başladığında zamanlayıcıyı tekrar kurar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            WallpaperScheduler.schedule(context)
        }
    }
}
