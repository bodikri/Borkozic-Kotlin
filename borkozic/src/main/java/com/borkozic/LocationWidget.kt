package com.borkozic

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

class LocationWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ANCROZIC", "Widget onReceive()")
        super.onReceive(context, intent)
        val action = intent.action
        Log.d("ANCROZIC", "action:" + action)
        if (action.contentEquals(WidgetService.WIDGET_REFRESH)) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, LocationWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (appWidgetId in appWidgetIds) {
                // appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            setAlarm(context, appWidgetId, 2000)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun setAlarm(context: Context, appWidgetId: Int, updateRate: Int) {
            val active = Intent(context, WidgetService::class.java)
            active.setAction(WidgetService.WIDGET_UPDATE)
            active.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            val intent = PendingIntent.getService(context, 0, active, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (updateRate >= 0) {
                alarms.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime().toLong(), updateRate.toLong(), intent)
            } else {
                alarms.cancel(intent)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            setAlarm(context, appWidgetId, -1)
        }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        context.stopService(Intent(context, WidgetService::class.java))
        super.onDisabled(context)
    }
}
