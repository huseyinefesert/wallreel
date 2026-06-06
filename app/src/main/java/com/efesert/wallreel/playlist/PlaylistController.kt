package com.efesert.wallreel.playlist

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Duvar kağıdı sırasını (playlist) yöneten saf mantık. Veritabanına ihtiyaç duymaz;
 * tüm durum Prefs içinde tutulur. Hem Alarm (otomatik geçiş), hem çift dokunma,
 * hem de UI buradan geçiş yapar.
 */
object PlaylistController {

    const val ACTION_CHANGED = "com.efesert.wallreel.WALLPAPER_CHANGED"

    /** Sıradaki tek bir öğe: çözümlenmiş dosya yolu + nihai scale (FILL/FIT). */
    data class Entry(val path: String, val scale: String)

    /**
     * Sırayı yeniden kurar. Aktif albüm, fotoğraflar, scale çözümü ve shuffle
     * durumu değiştiğinde çağrılır.
     */
    fun rebuild(context: Context, entries: List<Entry>, shuffle: Boolean) {
        val ordered = if (shuffle) entries.shuffled() else entries
        val arr = JSONArray()
        for (e in ordered) {
            arr.put(JSONObject().put("p", e.path).put("s", e.scale))
        }
        Prefs.setQueueJson(context, arr.toString())
        Prefs.setCurrentIndex(context, 0)
        applyCurrent(context)
        broadcast(context)
    }

    /**
     * Sıradaki resimleri değiştirmeden, sadece scale değerlerini günceller.
     * Mevcut konum (index) korunur; o an gösterilen fotoğraf düzenlendiyse
     * yeni ölçeğiyle yerinde güncellenir, başka fotoğrafa GEÇİLMEZ.
     */
    fun updateScales(context: Context, pathToScale: Map<String, String>) {
        try {
            val arr = JSONArray(Prefs.queueJson(context))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                pathToScale[o.getString("p")]?.let { o.put("s", it) }
            }
            Prefs.setQueueJson(context, arr.toString())
            applyCurrent(context)
            broadcast(context)
        } catch (e: Exception) {
            // sıra okunamazsa sessizce geç
        }
    }

    /** Bir sonraki resme geçer (otomatik zamanlayıcı veya çift dokunma). */
    fun advance(context: Context) {
        val queue = readQueue(context)
        if (queue.isEmpty()) {
            Prefs.setCurrent(context, null, "FILL")
            broadcast(context)
            return
        }
        val next = (Prefs.currentIndex(context) + 1) % queue.size
        Prefs.setCurrentIndex(context, next)
        applyCurrent(context)
        broadcast(context)
    }

    /** Index'e karşılık gelen öğeyi "şu an gösterilen" olarak yazar. */
    private fun applyCurrent(context: Context) {
        val queue = readQueue(context)
        if (queue.isEmpty()) {
            Prefs.setCurrent(context, null, "FILL")
            return
        }
        val index = Prefs.currentIndex(context).coerceIn(0, queue.size - 1)
        val entry = queue[index]
        Prefs.setCurrent(context, entry.path, entry.scale)
    }

    private fun readQueue(context: Context): List<Entry> {
        return try {
            val arr = JSONArray(Prefs.queueJson(context))
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(Entry(o.getString("p"), o.getString("s")))
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun broadcast(context: Context) {
        val intent = Intent(ACTION_CHANGED).setPackage(context.packageName)
        context.applicationContext.sendBroadcast(intent)
    }
}
