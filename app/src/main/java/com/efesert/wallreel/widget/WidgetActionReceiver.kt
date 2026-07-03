package com.efesert.wallreel.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.efesert.wallreel.data.Repository
import com.efesert.wallreel.data.ScaleMode
import com.efesert.wallreel.playlist.PlaylistController
import com.efesert.wallreel.playlist.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Widget'lardaki tüm tıklamaları (next/prev/shuffle/double-tap) ve duvar kağıdı
 * değişim yayınını (WALLPAPER_CHANGED) tek noktadan işler; ardından tüm widget'ları
 * yeniden çizer. İş parçacığı: goAsync + IO (bitmap çözme ve DB işlemleri için).
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    WidgetRenderer.ACTION_NEXT -> PlaylistController.advance(app)
                    WidgetRenderer.ACTION_PREV -> PlaylistController.previous(app)
                    WidgetRenderer.ACTION_SET_ACTIVE_ALBUM -> {
                        val albumId = intent.getLongExtra(WidgetRenderer.EXTRA_ALBUM_ID, -1L)
                        if (albumId > 0L) Repository(app).setActiveAlbum(albumId)
                    }
                    WidgetRenderer.ACTION_SET_CURRENT_SCALE -> {
                        val scaleMode = intent.getStringExtra(WidgetRenderer.EXTRA_SCALE_MODE)
                        if (scaleMode == ScaleMode.FILL || scaleMode == ScaleMode.FIT) {
                            Repository(app).setCurrentWallpaperScale(scaleMode)
                        }
                    }
                    WidgetRenderer.ACTION_DOUBLETAP ->
                        Prefs.setDoubleTapEnabled(app, !Prefs.doubleTapEnabled(app))
                    WidgetRenderer.ACTION_SHUFFLE -> {
                        Prefs.setShuffle(app, !Prefs.shuffle(app))
                        // Kuyruğu yeni shuffle durumuna göre yeniden kur (mevcut foto korunur).
                        Repository(app).refreshQueue()
                    }
                    // PlaylistController.ACTION_CHANGED: sadece görselleri tazele.
                }
                WidgetRenderer.updateAll(app)
            } finally {
                pending.finish()
            }
        }
    }
}
