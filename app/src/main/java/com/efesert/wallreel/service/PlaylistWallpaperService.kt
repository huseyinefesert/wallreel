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
import android.service.wallpaper.WallpaperService
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.efesert.wallreel.playlist.PlaylistController
import com.efesert.wallreel.playlist.Prefs

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

        // Playlist değiştiğinde (alarm, çift dokunma, UI) yeniden çiz.
        private val changeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                requestDraw()
            }
        }

        private val gestureDetector by lazy {
            GestureDetector(this@PlaylistWallpaperService,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        // Çift dokunma -> 1 saati beklemeden hemen sıradakine geç.
                        PlaylistController.advance(this@PlaylistWallpaperService)
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
            if (v) requestDraw()
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
            drawThread.quitSafely()
            super.onDestroy()
        }
    }
}
