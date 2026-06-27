package com.efesert.wallreel.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.efesert.wallreel.playlist.PlaylistController
import com.efesert.wallreel.playlist.Prefs
import com.efesert.wallreel.scheduler.WallpaperScheduler

/**
 * Canlı duvar kağıdı servisi. Şu an gösterilmesi gereken resmi (Prefs.currentPath) çizer,
 * ekrana çift dokunulduğunda sıradakine geçer ve playlist değiştiğinde yeniden çizer.
 */
class PlaylistWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = PlaylistEngine()

    inner class PlaylistEngine : Engine() {

        private var width = 0
        private var height = 0
        private var visible = false

        private val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }

        private val drawThread = HandlerThread("wp-draw").apply { start() }
        private val drawHandler = Handler(drawThread.looper)

        // Ekran açıkken kesin zamanlama için (Doze'a takılmadan) ana iş parçacığı
        // zamanlayıcısı. Ekran kapanınca durur; AlarmManager o dönemi kapsar.
        private val timerHandler = Handler(Looper.getMainLooper())
        private val advanceRunnable: Runnable = Runnable {
            PlaylistController.advanceIfDue(this@PlaylistWallpaperService)
            // Arka plan alarmını yeni değişim zamanına göre yeniden kur.
            WallpaperScheduler.schedule(this@PlaylistWallpaperService)
            // Boş kuyrukta sıkı döngüyü önlemek için sonrakini tam aralık sonrasına kur.
            timerHandler.removeCallbacks(this@PlaylistEngine.advanceRunnable)
            val minutes = Prefs.intervalMinutes(this@PlaylistWallpaperService)
            if (minutes > 0 && !Prefs.timerPaused(this@PlaylistWallpaperService)) {
                timerHandler.postDelayed(this@PlaylistEngine.advanceRunnable, minutes * 60_000L)
            }
        }

        // Playlist değiştiğinde (alarm, çift dokunma, UI) yeniden çiz ve
        // görünürse zamanlayıcıyı yeni değişim zamanına göre hizala.
        private val changeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                requestDraw()
                if (visible) scheduleNextTick()
            }
        }

        private val gestureDetector by lazy {
            GestureDetector(this@PlaylistWallpaperService,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        // Çift dokunma -> hemen sıradakine geç (ayardan kapatılabilir).
                        if (Prefs.doubleTapEnabled(this@PlaylistWallpaperService)) {
                            PlaylistController.advance(this@PlaylistWallpaperService)
                        }
                        return true
                    }
                })
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            val filter = IntentFilter(PlaylistController.ACTION_CHANGED)
            ContextCompat.registerReceiver(
                this@PlaylistWallpaperService,
                changeReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w
            height = h
            requestDraw()
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (v) {
                // Görünür olunca: süre dolduysa kalan süreye (0 ise hemen) bir geçiş kur.
                scheduleNextTick()
                requestDraw()
            } else {
                // Ekran kapalıyken zamanlayıcıyı durdur; o dönemi AlarmManager kapsar.
                timerHandler.removeCallbacks(advanceRunnable)
            }
        }

        /** Son değişimden bu yana kalan süreye göre bir sonraki geçişi kurar. */
        private fun scheduleNextTick() {
            timerHandler.removeCallbacks(advanceRunnable)
            val minutes = Prefs.intervalMinutes(this@PlaylistWallpaperService)
            if (minutes <= 0 || Prefs.timerPaused(this@PlaylistWallpaperService)) return
            val delay = Prefs.timerRemainingMillis(this@PlaylistWallpaperService)
            timerHandler.postDelayed(advanceRunnable, delay)
        }

        override fun onTouchEvent(event: MotionEvent) {
            gestureDetector.onTouchEvent(event)
            super.onTouchEvent(event)
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder?) {
            requestDraw()
        }

        private fun requestDraw() {
            drawHandler.removeCallbacksAndMessages(null)
            drawHandler.post { drawFrame() }
        }

        private fun drawFrame() {
            if (width <= 0 || height <= 0) return
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas == null) return
                canvas.drawColor(Color.BLACK)

                val path = Prefs.currentPath(this@PlaylistWallpaperService)
                if (path != null) {
                    val bmp = BitmapUtils.decodeSampled(path, width, height)
                    if (bmp != null) {
                        val fill = Prefs.currentScale(this@PlaylistWallpaperService) != "FIT"
                        val matrix = BitmapUtils.matrixFor(bmp.width, bmp.height, width, height, fill)
                        canvas.drawBitmap(bmp, matrix, paint)
                        bmp.recycle()
                    }
                }
            } catch (e: Exception) {
                // çizim hatasında siyah kal
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        override fun onDestroy() {
            try {
                this@PlaylistWallpaperService.unregisterReceiver(changeReceiver)
            } catch (_: Exception) {
            }
            timerHandler.removeCallbacks(advanceRunnable)
            drawThread.quitSafely()
            super.onDestroy()
        }
    }
}
