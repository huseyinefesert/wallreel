package com.efesert.wallreel.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * 1x4 hızlı kontrol widget'ı: next, shuffle ve ana listedeki ilk iki albümü aktif yapma.
 */
class QuickControlsWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = WidgetRenderer.renderQuickControls(context)
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, views)
    }
}
