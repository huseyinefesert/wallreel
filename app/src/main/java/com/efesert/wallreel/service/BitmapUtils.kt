package com.efesert.wallreel.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface

object BitmapUtils {

    /** Belleği şişirmeden, ekran boyutuna uygun örneklenmiş bitmap döndürür. */
    fun decodeSampled(path: String, reqW: Int, reqH: Int): Bitmap? {
        if (reqW <= 0 || reqH <= 0) return null
        val orientation = exifOrientation(path)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val rotated = swapsDimensions(orientation)
        val sourceW = if (rotated) bounds.outHeight else bounds.outWidth
        val sourceH = if (rotated) bounds.outWidth else bounds.outHeight
        var sample = 1
        val halfW = sourceW / 2
        val halfH = sourceH / 2
        while (halfW / sample >= reqW && halfH / sample >= reqH) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, opts) ?: return null
        return applyExifOrientation(decoded, orientation)
    }

    private fun exifOrientation(path: String): Int {
        return runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private fun swapsDimensions(orientation: Int): Boolean {
        return orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrElse {
            bitmap
        }.also { oriented ->
            if (oriented != bitmap) bitmap.recycle()
        }
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
