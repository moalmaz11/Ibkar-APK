package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.DailyRecord
import com.example.data.DateHelper
import com.example.data.WorshipDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object PrayerNotificationManager {
    const val CHANNEL_ID = "PRAYER_REMINDERS_CHANNEL"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "تنبيهات تطبيق إِبْكَـار"
            val descriptionText = "إشعارات مواقيت الصلاة وأذكار الصباح والمساء"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendPrayerNotification(context: Context, prayerKey: String, prayerArabicName: String) {
        val sharedPrefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        val enabled = sharedPrefs.getBoolean("notify_all", true)
        if (!enabled) return

        if (prayerKey == "morning_dhikr") {
            val dhikrEnabled = sharedPrefs.getBoolean("notify_morning_dhikr", true)
            if (!dhikrEnabled) return
        } else if (prayerKey == "evening_dhikr") {
            val dhikrEnabled = sharedPrefs.getBoolean("notify_evening_dhikr", true)
            if (!dhikrEnabled) return
        } else {
            val prayersEnabled = sharedPrefs.getBoolean("notify_prayers", true)
            if (!prayersEnabled) return
        }

        val intentYes = Intent(context, PrayerActionReceiver::class.java).apply {
            action = "ACTION_PRAYER_DONE"
            putExtra("PRAYER_KEY", prayerKey)
            putExtra("NOTIFICATION_ID", prayerKey.hashCode())
        }
        val pendingIntentYes = PendingIntent.getBroadcast(
            context,
            prayerKey.hashCode(),
            intentYes,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            if (prayerKey == "morning_dhikr") {
                putExtra("OPEN_DHIKR", "morning")
            } else if (prayerKey == "evening_dhikr") {
                putExtra("OPEN_DHIKR", "evening")
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingMainIntent = PendingIntent.getActivity(
            context,
            prayerKey.hashCode() + 200,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isDhikr = prayerKey.contains("dhikr")
        val contentTitle = if (prayerKey == "morning_dhikr") {
            "موعد أذكار الصباح 🌅"
        } else if (prayerKey == "evening_dhikr") {
            "موعد أذكار المساء 🌇"
        } else {
            "حان الآن موعد صلاة $prayerArabicName 🕌"
        }

        val contentText = if (isDhikr) {
            "ابدأ بقراءة $prayerArabicName لحفظ يومك وبركته"
        } else {
            "حي على الصلاة.. حي على الفلاح، لا تؤخر صلاتك عن وقتها"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingMainIntent)
            .setAutoCancel(true)

        if (isDhikr) {
            builder.addAction(
                android.R.drawable.ic_menu_agenda,
                "قراءة الأذكار الآن",
                pendingMainIntent
            )
        } else {
            builder.addAction(
                android.R.drawable.checkbox_on_background,
                "تمت الصلاة ✓",
                pendingIntentYes
            )
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(prayerKey.hashCode(), builder.build())
    }

    fun scheduleSinglePrayerReminder(context: Context, prayerKey: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val sharedPrefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        val lat = sharedPrefs.getFloat("user_latitude", PrayerTimeCalculator.DEFAULT_LATITUDE.toFloat()).toDouble()
        val lng = sharedPrefs.getFloat("user_longitude", PrayerTimeCalculator.DEFAULT_LONGITUDE.toFloat()).toDouble()
        val offsetMinutes = sharedPrefs.getInt("prayer_offset_minutes", 0)

        val localCalendar = PrayerTimeCalculator.getLocalCalendar(lat, lng)
        val year = localCalendar.get(Calendar.YEAR)
        val month = localCalendar.get(Calendar.MONTH) + 1
        val day = localCalendar.get(Calendar.DAY_OF_MONTH)

        val times = PrayerTimeCalculator.calculatePrayerTimes(year, month, day, lat, lng)
        val time = times[prayerKey] ?: return

        var hour = time.first
        var minute = time.second

        val fivePrayers = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
        if (prayerKey in fivePrayers) {
            val totalMin = hour * 60 + minute + offsetMinutes
            hour = (totalMin / 60) % 24
            minute = totalMin % 60
        }

        val intent = Intent(context, PrayerNotificationReceiver::class.java).apply {
            putExtra("PRAYER_KEY", prayerKey)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            prayerKey.hashCode() + 100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timezoneOffset = PrayerTimeCalculator.getUserTimezoneOffset(year, month, day, lat, lng)
        val targetCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT")).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var triggerMillis = targetCal.timeInMillis - (timezoneOffset * 3600000.0).toLong()

        if (triggerMillis < System.currentTimeMillis()) {
            targetCal.add(Calendar.DAY_OF_YEAR, 1)
            val tomYear = targetCal.get(Calendar.YEAR)
            val tomMonth = targetCal.get(Calendar.MONTH) + 1
            val tomDay = targetCal.get(Calendar.DAY_OF_MONTH)

            val tomTimes = PrayerTimeCalculator.calculatePrayerTimes(tomYear, tomMonth, tomDay, lat, lng)
            val tomTime = tomTimes[prayerKey] ?: Pair(hour, minute)
            var tomHour = tomTime.first
            var tomMin = tomTime.second
            if (prayerKey in fivePrayers) {
                val totalMin = tomHour * 60 + tomMin + offsetMinutes
                tomHour = (totalMin / 60) % 24
                tomMin = totalMin % 60
            }

            val tomOffset = PrayerTimeCalculator.getUserTimezoneOffset(tomYear, tomMonth, tomDay, lat, lng)
            targetCal.apply {
                set(Calendar.HOUR_OF_DAY, tomHour)
                set(Calendar.MINUTE, tomMin)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            triggerMillis = targetCal.timeInMillis - (tomOffset * 3600000.0).toLong()
        }

        val calendar = Calendar.getInstance().apply {
            timeInMillis = triggerMillis
        }

        try {
            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (canScheduleExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
                Log.d("PrayerNotification", "Scheduled reminder for $prayerKey at ${calendar.time}")
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            }
        } catch (e: Exception) {
            Log.e("PrayerNotification", "Error scheduling alarm for $prayerKey", e)
        }
    }

    fun scheduleDailyPrayerReminders(context: Context) {
        val reminders = listOf("fajr", "dhuhr", "asr", "maghrib", "isha", "morning_dhikr", "evening_dhikr")
        for (key in reminders) {
            scheduleSinglePrayerReminder(context, key)
        }
    }
}

class PrayerNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerKey = intent.getStringExtra("PRAYER_KEY") ?: return
        val arabicName = when (prayerKey) {
            "fajr" -> "الفجر"
            "dhuhr" -> "الظهر"
            "asr" -> "العصر"
            "maghrib" -> "المغرب"
            "isha" -> "العشاء"
            "morning_dhikr" -> "أذكار الصباح"
            "evening_dhikr" -> "أذكار المساء"
            else -> "الصلاة"
        }
        PrayerNotificationManager.sendPrayerNotification(context, prayerKey, arabicName)
        PrayerNotificationManager.scheduleSinglePrayerReminder(context, prayerKey)
    }
}

class PrayerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "ACTION_PRAYER_DONE") {
            val prayerKey = intent.getStringExtra("PRAYER_KEY") ?: return
            val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)
            
            if (notificationId != -1) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
            }

            val todayStr = DateHelper.getTodayDateString(context)
            val database = WorshipDatabase.getDatabase(context)
            val dao = database.worshipDao()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val existing = dao.getRecordForDateSync(todayStr) ?: DailyRecord(date = todayStr)
                    val updated = when (prayerKey) {
                        "fajr" -> existing.copy(fajrDone = true)
                        "dhuhr" -> existing.copy(dhuhrDone = true)
                        "asr" -> existing.copy(asrDone = true)
                        "maghrib" -> existing.copy(maghribDone = true)
                        "isha" -> existing.copy(ishaDone = true)
                        "morning_dhikr" -> existing.copy(morningDhikrDone = true)
                        "evening_dhikr" -> existing.copy(eveningDhikrDone = true)
                        else -> existing
                    }
                    dao.insertOrUpdateRecord(updated)

                    CoroutineScope(Dispatchers.Main).launch {
                        val displayMsg = when (prayerKey) {
                            "morning_dhikr" -> "تم تسجيل قراءة أذكار الصباح بنجاح!"
                            "evening_dhikr" -> "تم تسجيل قراءة أذكار المساء بنجاح!"
                            else -> {
                                val prayerArabic = when (prayerKey) {
                                    "fajr" -> "الفجر"
                                    "dhuhr" -> "الظهر"
                                    "asr" -> "العصر"
                                    "maghrib" -> "المغرب"
                                    "isha" -> "العشاء"
                                    else -> "الصلاة"
                                }
                                "تقبل الله صلاة $prayerArabic! تم تسجيل الإنجاز."
                            }
                        }
                        Toast.makeText(context, displayMsg, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("PrayerActionReceiver", "Failed to register prayer completion", e)
                }
            }
        }
    }
}
