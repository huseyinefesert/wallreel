package com.efesert.wallreel.playlist

import android.content.Context

/**
 * Basit SharedPreferences sarmalayıcı. Hem UI hem de duvar kağıdı servisi
 * (farklı thread'ler) buraya senkron erişebilsin diye DataStore yerine tercih edildi.
 */
object Prefs {
    private const val FILE = "wp_prefs"

    private const val KEY_INTERVAL = "interval_minutes"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_QUEUE = "queue_json"
    private const val KEY_INDEX = "current_index"
    private const val KEY_CURRENT_PATH = "current_path"
    private const val KEY_CURRENT_SCALE = "current_scale"
    private const val KEY_LAST_CHANGE = "last_change_time"
    private const val KEY_TIMER_PAUSED = "timer_paused"
    private const val KEY_TIMER_REMAINING = "timer_remaining_ms"
    private const val KEY_DOUBLE_TAP = "double_tap_enabled"
    private const val KEY_SORT = "photo_sort"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // Zamanlayıcı aralığı (dakika). Varsayılan 60 dk.
    fun intervalMinutes(context: Context): Int =
        prefs(context).getInt(KEY_INTERVAL, 60)

    fun setIntervalMinutes(context: Context, minutes: Int) {
        val intervalMs = minutes * 60_000L
        val editor = prefs(context).edit().putInt(KEY_INTERVAL, minutes)
        if (timerPaused(context)) {
            editor.putLong(
                KEY_TIMER_REMAINING,
                timerPausedRemainingMillis(context).coerceIn(0L, intervalMs)
            )
        }
        editor.apply()
    }

    // Shuffle açık/kapalı.
    fun shuffle(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHUFFLE, false)

    fun setShuffle(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_SHUFFLE, value).apply()

    // Çalan sıra (JSON) ve mevcut konum.
    fun queueJson(context: Context): String =
        prefs(context).getString(KEY_QUEUE, "[]") ?: "[]"

    fun setQueueJson(context: Context, json: String) =
        prefs(context).edit().putString(KEY_QUEUE, json).apply()

    fun currentIndex(context: Context): Int =
        prefs(context).getInt(KEY_INDEX, 0)

    fun setCurrentIndex(context: Context, index: Int) =
        prefs(context).edit().putInt(KEY_INDEX, index).apply()

    // Şu an gösterilen resim (servis sadece bunu okur).
    fun currentPath(context: Context): String? =
        prefs(context).getString(KEY_CURRENT_PATH, null)

    fun currentScale(context: Context): String =
        prefs(context).getString(KEY_CURRENT_SCALE, "FILL") ?: "FILL"

    fun setCurrent(context: Context, path: String?, scale: String) {
        val previous = currentPath(context)
        val editor = prefs(context).edit()
            .putString(KEY_CURRENT_PATH, path)
            .putString(KEY_CURRENT_SCALE, scale)
        // Sadece gösterilen FOTOĞRAF gerçekten değiştiğinde zamanı güncelle.
        // (Yalnızca scale değişiminde sayaç sıfırlanmaz.)
        if (path != null && path != previous) {
            editor.putLong(KEY_LAST_CHANGE, System.currentTimeMillis())
            if (timerPaused(context)) {
                editor.putLong(KEY_TIMER_REMAINING, intervalMillis(context))
            }
        }
        editor.apply()
    }

    // Mevcut duvar kağıdının en son ne zaman değiştiği (epoch millis). 0 = hiç.
    fun lastChangeTime(context: Context): Long =
        prefs(context).getLong(KEY_LAST_CHANGE, 0L)

    // Değişim zamanını "şimdi" olarak işaretle (timer'ı sıfırlamak için).
    fun markChangedNow(context: Context) {
        val editor = prefs(context).edit().putLong(KEY_LAST_CHANGE, System.currentTimeMillis())
        if (timerPaused(context)) {
            editor.putLong(KEY_TIMER_REMAINING, intervalMillis(context))
        }
        editor.apply()
    }

    fun intervalMillis(context: Context): Long =
        intervalMinutes(context) * 60_000L

    fun timerPaused(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TIMER_PAUSED, false)

    fun timerPausedRemainingMillis(context: Context): Long =
        prefs(context).getLong(KEY_TIMER_REMAINING, intervalMillis(context))

    fun timerRemainingMillis(context: Context): Long {
        val intervalMs = intervalMillis(context)
        if (intervalMs <= 0L) return 0L
        if (timerPaused(context)) {
            return timerPausedRemainingMillis(context).coerceIn(0L, intervalMs)
        }
        val last = lastChangeTime(context)
        return if (last > 0L) {
            (intervalMs - (System.currentTimeMillis() - last)).coerceIn(0L, intervalMs)
        } else {
            intervalMs
        }
    }

    fun pauseTimer(context: Context): Long {
        val remaining = timerRemainingMillis(context)
        prefs(context).edit()
            .putBoolean(KEY_TIMER_PAUSED, true)
            .putLong(KEY_TIMER_REMAINING, remaining)
            .apply()
        return remaining
    }

    fun resumeTimer(context: Context) {
        val intervalMs = intervalMillis(context)
        val remaining = timerPausedRemainingMillis(context).coerceIn(0L, intervalMs)
        val elapsed = intervalMs - remaining
        prefs(context).edit()
            .putBoolean(KEY_TIMER_PAUSED, false)
            .remove(KEY_TIMER_REMAINING)
            .putLong(KEY_LAST_CHANGE, System.currentTimeMillis() - elapsed)
            .apply()
    }

    // Ana ekrandaki çift dokunma ile fotoğraf değiştirme açık mı? Varsayılan açık.
    fun doubleTapEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DOUBLE_TAP, true)

    fun setDoubleTapEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_DOUBLE_TAP, value).apply()

    // Albüm içi fotoğraf sıralama modu. Hem gösterimi hem (shuffle kapalıyken)
    // kuyruğun sırasını belirler. Bkz. PhotoSort.
    fun photoSort(context: Context): String =
        prefs(context).getString(KEY_SORT, "ADDED_OLD") ?: "ADDED_OLD"

    fun setPhotoSort(context: Context, mode: String) =
        prefs(context).edit().putString(KEY_SORT, mode).apply()
}
