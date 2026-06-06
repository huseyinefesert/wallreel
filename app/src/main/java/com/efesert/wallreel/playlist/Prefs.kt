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

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // Zamanlayıcı aralığı (dakika). Varsayılan 60 dk.
    fun intervalMinutes(context: Context): Int =
        prefs(context).getInt(KEY_INTERVAL, 60)

    fun setIntervalMinutes(context: Context, minutes: Int) =
        prefs(context).edit().putInt(KEY_INTERVAL, minutes).apply()

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
        }
        editor.apply()
    }

    // Mevcut duvar kağıdının en son ne zaman değiştiği (epoch millis). 0 = hiç.
    fun lastChangeTime(context: Context): Long =
        prefs(context).getLong(KEY_LAST_CHANGE, 0L)
}
