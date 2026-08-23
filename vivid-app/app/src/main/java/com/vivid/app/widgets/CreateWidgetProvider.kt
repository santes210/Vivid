package com.vivid.app.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vivid.app.MainActivity
import com.vivid.app.R

/**
 * Widget del launcher "Crear" (2x2): la forma Burst de VividMaterialShapes
 * (drawable/vivid_burst.xml) como fondo y una sola acción — abrir el creador.
 *
 * Funciona con el MISMO mecanismo que los atajos del launcher (task 36):
 * MainActivity recibe el extra [MainActivity.EXTRA_SHORTCUT_ACTION] en
 * onCreate (frío) o onNewIntent (warm, singleTask) y VividNavigation
 * navega a [MainActivity.SHORTCUT_CREATE_POST], esperando a que la sesión
 * esté restaurada si el proceso arrancó de cero.
 *
 * El widget es estático: no hay onUpdate periódico (updatePeriodMillis=0).
 * El cambio claro/oscuro lo resuelve el propio recurso de color al
 * reinflarse por el launcher cuando el tema del sistema cambia.
 */
class CreateWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_create)

            // Tap en cualquier punto del widget → MainActivity con la
            // acción "create_post". FLAG_UPDATE_CURRENT + requestCode fijo:
            // si el usuario tiene varias instancias del widget todas
            // abren el mismo flujo (no hay estado por instancia).
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_SHORTCUT_ACTION, MainActivity.SHORTCUT_CREATE_POST)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_CREATE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    companion object {
        private const val REQUEST_CODE_CREATE = 0x517_310
    }
}
