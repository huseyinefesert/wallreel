package com.efesert.wallreel.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * Orta boy widget. Bir önceki ve bir sonraki fotoğrafı gösterir; karolara
 * dokununca geri/ileri gider. Çizim WidgetRenderer'da.
 */
class QueueWidgetMedium : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = WidgetRenderer.renderMedium(context)
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, views)
    }
}
