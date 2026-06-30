package com.efesert.wallreel.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.efesert.wallreel.R
import com.efesert.wallreel.data.Album
import com.efesert.wallreel.data.AppDatabase
import com.efesert.wallreel.playlist.PlaylistController
import com.efesert.wallreel.playlist.Prefs
import com.efesert.wallreel.service.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Tüm widget boyutlarının (1x1 / orta / büyük) çizimini ve güncellenmesini tek
 * yerden yöneten yardımcı. Tıklamalar WidgetActionReceiver'a yönlendirilir.
 */
object WidgetRenderer {

    const val ACTION_NEXT = "com.efesert.wallreel.WIDGET_NEXT"
    const val ACTION_PREV = "com.efesert.wallreel.WIDGET_PREV"
    const val ACTION_SHUFFLE = "com.efesert.wallreel.WIDGET_SHUFFLE"
    const val ACTION_DOUBLETAP = "com.efesert.wallreel.WIDGET_DOUBLETAP"
    const val ACTION_SET_ACTIVE_ALBUM = "com.efesert.wallreel.WIDGET_SET_ACTIVE_ALBUM"
    const val EXTRA_ALBUM_ID = "album_id"

    private val DARK = 0xFF0E0E19.toInt()
    private val LIGHT = 0xFFFFFFFF.toInt()

    /** Tüm boyutlardaki tüm widget örneklerini günceller. */
    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        updateProvider(context, mgr, NextPhotoWidget::class.java) { renderSmall(context) }
        updateProvider(context, mgr, QueueWidgetMedium::class.java) { renderMedium(context) }
        updateProvider(context, mgr, QueueWidgetLarge::class.java) { renderLarge(context) }
        updateProvider(context, mgr, QuickControlsWidget::class.java) { renderQuickControls(context) }
    }

    private inline fun updateProvider(
        context: Context,
        mgr: AppWidgetManager,
        cls: Class<*>,
        render: () -> RemoteViews
    ) {
        val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
        if (ids.isEmpty()) return
        val views = render()
        for (id in ids) mgr.updateAppWidget(id, views)
    }

    fun renderSmall(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_next_photo)
        views.setOnClickPendingIntent(R.id.widget_root, action(context, ACTION_NEXT))
        return views
    }

    fun renderMedium(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_medium)
        val snap = PlaylistController.snapshot(context)
        bindPhoto(views, R.id.prev_image, prevPath(snap))
        bindPhoto(views, R.id.next_image, nextPath(snap))
        views.setOnClickPendingIntent(R.id.prev_cell, action(context, ACTION_PREV))
        views.setOnClickPendingIntent(R.id.next_cell, action(context, ACTION_NEXT))
        return views
    }

    fun renderLarge(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_large)
        val snap = PlaylistController.snapshot(context)
        bindPhoto(views, R.id.prev_image, prevPath(snap))
        bindPhoto(views, R.id.next_image, nextPath(snap))
        views.setOnClickPendingIntent(R.id.prev_cell, action(context, ACTION_PREV))
        views.setOnClickPendingIntent(R.id.next_cell, action(context, ACTION_NEXT))

        val shuffleOn = Prefs.shuffle(context)
        bindToggle(views, R.id.shuffle_btn, "Shuffle: " + onOff(shuffleOn), shuffleOn)
        views.setOnClickPendingIntent(R.id.shuffle_btn, action(context, ACTION_SHUFFLE))

        val dtOn = Prefs.doubleTapEnabled(context)
        bindToggle(views, R.id.dt_btn, "Double-tap: " + onOff(dtOn), dtOn)
        views.setOnClickPendingIntent(R.id.dt_btn, action(context, ACTION_DOUBLETAP))
        return views
    }

    fun renderQuickControls(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_controls)
        views.setOnClickPendingIntent(R.id.quick_next_btn, action(context, ACTION_NEXT))

        val shuffleOn = Prefs.shuffle(context)
        bindToggle(views, R.id.quick_shuffle_btn, "Shuffle\n" + onOff(shuffleOn), shuffleOn)
        views.setOnClickPendingIntent(R.id.quick_shuffle_btn, action(context, ACTION_SHUFFLE))

        val albums = runCatching {
            runBlocking(Dispatchers.IO) {
                AppDatabase.get(context).dao().getAlbumsOnce().take(2)
            }
        }.getOrDefault(emptyList())
        bindAlbumShortcut(context, views, R.id.quick_album_one_btn, albums.getOrNull(0))
        bindAlbumShortcut(context, views, R.id.quick_album_two_btn, albums.getOrNull(1))
        return views
    }

    private fun onOff(on: Boolean) = if (on) "On" else "Off"

    private fun bindToggle(views: RemoteViews, viewId: Int, text: String, on: Boolean) {
        views.setTextViewText(viewId, text)
        views.setInt(
            viewId, "setBackgroundResource",
            if (on) R.drawable.widget_btn_on else R.drawable.widget_btn_off
        )
        views.setTextColor(viewId, if (on) DARK else LIGHT)
    }

    private fun bindAlbumShortcut(
        context: Context,
        views: RemoteViews,
        viewId: Int,
        album: Album?
    ) {
        if (album == null) {
            bindToggle(views, viewId, "Album\n-", false)
            return
        }
        bindToggle(views, viewId, album.name, album.isActive)
        views.setOnClickPendingIntent(viewId, albumAction(context, album.id))
    }

    // Widget bitmap'leri RemoteViews binder işlemine sığmalı (~1MB). Bu yüzden
    // küçük bir kenar boyutuna indirgenir.
    private const val THUMB_PX = 256

    private fun bindPhoto(views: RemoteViews, viewId: Int, path: String?) {
        val bmp = thumb(path)
        if (bmp != null) {
            views.setImageViewBitmap(viewId, bmp)
        } else {
            views.setImageViewResource(viewId, R.drawable.widget_photo_placeholder)
        }
    }

    private fun thumb(path: String?): android.graphics.Bitmap? {
        val decoded = path?.let {
            runCatching { BitmapUtils.decodeSampled(it, THUMB_PX, THUMB_PX) }.getOrNull()
        } ?: return null
        val maxSide = maxOf(decoded.width, decoded.height)
        if (maxSide <= THUMB_PX) return decoded
        val scale = THUMB_PX.toFloat() / maxSide
        val w = (decoded.width * scale).toInt().coerceAtLeast(1)
        val h = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = android.graphics.Bitmap.createScaledBitmap(decoded, w, h, true)
        if (scaled != decoded) decoded.recycle()
        return scaled
    }

    private fun prevPath(s: PlaylistController.Snapshot): String? {
        if (s.paths.isEmpty() || s.currentIndex < 0) return null
        val n = s.paths.size
        return s.paths[(s.currentIndex - 1 + n) % n]
    }

    private fun nextPath(s: PlaylistController.Snapshot): String? {
        if (s.paths.isEmpty() || s.currentIndex < 0) return null
        val n = s.paths.size
        return s.paths[(s.currentIndex + 1) % n]
    }

    private fun action(context: Context, actionName: String): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).setAction(actionName)
        return PendingIntent.getBroadcast(
            context,
            actionName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun albumAction(context: Context, albumId: Long): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java)
            .setAction(ACTION_SET_ACTIVE_ALBUM)
            .putExtra(EXTRA_ALBUM_ID, albumId)
        return PendingIntent.getBroadcast(
            context,
            (ACTION_SET_ACTIVE_ALBUM + albumId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
