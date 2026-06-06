package com.efesert.wallreel.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix

object BitmapUtils {

    /** Belleği şişirmeden, ekran boyutuna uygun örneklenmiş bitmap döndürür. */
    fun decodeSampled(path: String, reqW: Int, reqH: Int): Bitmap? {
        if (reqW <= 0 || reqH <= 0) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        var halfW = bounds.outWidth / 2
        var halfH = bounds.outHeight / 2
        while (halfW / sample >= reqW && halfH / sample >= reqH) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, opts)
    }

    /**
     * Resmi ekrana yerleştirecek matrisi hesaplar.
     * FILL = center-crop (kaplar, kırpar), FIT = center-inside (tamamı görünür).
     */
    fun matrixFor(
        bmpW: Int,
        bmpH: Int,
        viewW: Int,
        viewH: Int,
        fill: Boolean
    ): Matrix {
        val scaleX = viewW.toFloat() / bmpW
        val scaleY = viewH.toFloat() / bmpH
        val scale = if (fill) maxOf(scaleX, scaleY) else minOf(scaleX, scaleY)
        val dx = (viewW - bmpW * scale) / 2f
        val dy = (viewH - bmpH * scale) / 2f
        return Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
    }
}
