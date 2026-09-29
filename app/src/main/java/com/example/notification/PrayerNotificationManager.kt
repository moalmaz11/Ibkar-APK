package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.DailyRecord
import com.example.data.DateHelper
import com.example.data.WorshipDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object PrayerNotificationManager {
    const val PRAYER_CHANNEL_ID = "PRAYER_REMINDERS_CHANNEL"
    const val DHIKR_CHANNEL_ID = "DHIKR_REMINDERS_CHANNEL"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            // 1. قناة الصلاة (بصوت الأذان)
            val prayerSoundUri = android.net.Uri.parse("android.resource://${context.packageName}/raw/adhan")
            val prayerChannel = NotificationChannel(
                PRAYER_CHANNEL_ID,
                "تنبيهات الصلاة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات مواقيت الصلاة بصوت الأذان"
                setSound(prayerSoundUri, audioAttributes)
                enableVibration(true)
            }

            // 2. قناة الأذكار (بنغمة هادئة)
            val dhikrSoundUri = android.net.Uri.parse("android.resource://${context.packageName}/raw/dhikr")
            val dhikrChannel = NotificationChannel(
                DHIKR_CHANNEL_ID,
                "تنبيهات الأذكار",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تذكير بقراءة أذكار الصباح والمساء"
                setSound(dhikrSoundUri, audioAttributes)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(prayerChannel)
            notificationManager.createNotificationChannel(dhikrChannel)
        }
    }

    fun sendPrayerNotification(context: Context, prayerKey: String, prayerArabicName: String) {
        val sharedPrefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        val enabled = sharedPrefs.getBoolean("notify_all", true)
        if (!enabled) return

        val isDhikr = prayerKey.contains("dhikr")

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

        // إعداد زر (تمت الصلاة / قراءة الأذكار) ليحفظ في قاعدة البيانات
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

        // إعداد الضغط على الإشعار لفتح التطبيق
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

        val contentTitle = if (prayerKey == "morning_dhikr") {
            "موعد أذكار الصباح 🌅"
        } else if (prayerKey == "evening_dhikr") {
            "موعد أذكار المساء 🌇"
        } else {
            "حان الآن موعد صلاة $prayerArabicName"
        }

        val contentText = if (isDhikr) {
            "ابدأ بقراءة $prayerArabicName لحفظ يومك وبركته"
        } else {
            "حي على الصلاة.. حي على الفلاح، لا تؤخر صلاتك عن وقتها"
        }

        val channelId = if (isDhikr) DHIKR_CHANNEL_ID else PRAYER_CHANNEL_ID
        val iconRes = if (isDhikr) R.drawable.ic_notification_dhikr else R.drawable.ic_notification_prayer

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setPriority(if (isDhikr) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingMainIntent)
            .setAutoCancel(true)

        if (isDhikr) {
            builder.addAction(
                android.R.drawable.ic_menu_agenda,
                "تمت القراءة ✓",
                pendingIntentYes
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
        
        var triggerMillis: Long = 0
        val isDhikr = prayerKey.contains("dhikr")

        if (isDhikr) {
            // حساب موعد الأذكار بناءً على الوقت الذي يختاره المستخدم في الإعدادات
            val hour = if (prayerKey == "morning_dhikr") sharedPrefs.getInt("morning_dhikr_hour", 6) else sharedPrefs.getInt("evening_dhikr_hour", 17)
            val minute = if (prayerKey == "morning_dhikr") sharedPrefs.getInt("morning_dhikr_minute", 0) else sharedPrefs.getInt("evening_dhikr_minute", 0)
            
            val targetCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (targetCal.timeInMillis < System.currentTimeMillis()) {
                targetCal.add(Calendar.DAY_OF_YEAR, 1)
            }
            triggerMillis = targetCal.timeInMillis
            
        } else {
            // حساب مواقيت الصلاة بناءً على الإحداثيات
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

            val totalMin = hour * 60 + minute + offsetMinutes
            hour = (totalMin / 60) % 24
            minute = totalMin % 60

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

            triggerMillis = targetCal.timeInMillis - (timezoneOffset * 3600000.0).toLong()

            if (triggerMillis < System.currentTimeMillis()) {
                targetCal.add(Calendar.DAY_OF_YEAR, 1)
                val tomYear = targetCal.get(Calendar.YEAR)
                val tomMonth = targetCal.get(Calendar.MONTH) + 1
                val tomDay = targetCal.get(Calendar.DAY_OF_MONTH)

                val tomTimes = PrayerTimeCalculator.calculatePrayerTimes(tomYear, tomMonth, tomDay, lat, lng)
                val tomTime = tomTimes[prayerKey] ?: Pair(hour, minute)
                var tomHour = tomTime.first
                var tomMin = tomTime.second
                val tomTotalMin = tomHour * 60 + tomMin + offsetMinutes
                tomHour = (tomTotalMin / 60) % 24
                tomMin = tomTotalMin % 60

                val tomOffset = PrayerTimeCalculator.getUserTimezoneOffset(tomYear, tomMonth, tomDay, lat, lng)
                targetCal.apply {
                    set(Calendar.HOUR_OF_DAY, tomHour)
                    set(Calendar.MINUTE, tomMin)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                triggerMillis = targetCal.timeInMillis - (tomOffset * 3600000.0).toLong()
            }
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

        // استخدام Exact Alarms للحفاظ على دقة الإشعارات وتجاوز سكون البطارية
        try {
            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (canScheduleExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerMillis, pendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
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
        PrayerNotificationManager.scheduleSinglePrayerReminder(context, prayerKey) // إعادة الجدولة لليوم التالي
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
