package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted! Rescheduling prayer reminders and updating widgets...")
            try {
                PrayerNotificationManager.createNotificationChannel(context)
                PrayerNotificationManager.scheduleDailyPrayerReminders(context)
                
                // Immediately refresh the home screen widgets on boot
                try {
                    val intent1 = Intent(context, Class.forName("com.example.widget.CountdownWidgetProvider")).apply {
                        action = "com.example.widget.REFRESH_COUNTDOWN"
                    }
                    context.sendBroadcast(intent1)
                    val intent2 = Intent(context, Class.forName("com.example.widget.PrayerTimesWidgetProvider")).apply {
                        action = "com.example.widget.REFRESH_TIMES"
                    }
                    context.sendBroadcast(intent2)
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error updating widgets on boot", e)
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error in rescheduling alarms on boot", e)
            }
        }
    }
}
