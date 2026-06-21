package com.efesert.wallreel.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * Büyük boy widget. Önceki/sonraki fotoğraflara ek olarak shuffle ve double-tap
 * özelliklerini açıp kapatan iki düğme gösterir. Çizim WidgetRenderer'da.
 */
class QueueWidgetLarge : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = WidgetRenderer.renderLarge(context)
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, views)
    }
}
