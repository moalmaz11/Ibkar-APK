package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

class CountdownWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.widget.REFRESH_COUNTDOWN" || intent.action == Intent.ACTION_SCREEN_ON) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, CountdownWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            
            val bitmap = WidgetHelper.drawCountdownWidget(context)
            views.setImageViewBitmap(R.id.widget_image_view, bitmap)

            val configIntent = Intent(context, MainActivity::class.java)
            val configPendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                configIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_image_view, configPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            
            // Automatically schedule the next update in 60 seconds to keep the countdown active
            scheduleNextUpdate(context)
        }

        private fun scheduleNextUpdate(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val intent = Intent(context, CountdownWidgetProvider::class.java).apply {
                    action = "com.example.widget.REFRESH_COUNTDOWN"
                }
                val pending = PendingIntent.getBroadcast(
                    context,
                    999,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val triggerTime = System.currentTimeMillis() + 60_000 // In 60 seconds
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pending)
                    } else {
                        alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pending)
                    }
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pending)
                } else {
                    alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pending)
                }
            } catch (e: Exception) {
                android.util.Log.e("CountdownWidget", "Failed to schedule next minutes tick", e)
            }
        }
    }
}
