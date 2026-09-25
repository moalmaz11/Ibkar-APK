package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateHelper {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    private val arabicDayAndMonthFormat = SimpleDateFormat("EEEE، d MMMM", Locale.forLanguageTag("ar"))

    fun getTodayDateString(context: android.content.Context? = null): String {
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences("notification_settings", android.content.Context.MODE_PRIVATE)
                val lat = prefs.getFloat("user_latitude", com.example.notification.PrayerTimeCalculator.DEFAULT_LATITUDE.toFloat()).toDouble()
                val lng = prefs.getFloat("user_longitude", com.example.notification.PrayerTimeCalculator.DEFAULT_LONGITUDE.toFloat()).toDouble()
                val localCal = com.example.notification.PrayerTimeCalculator.getLocalCalendar(lat, lng)
                return dateFormat.format(localCal.time)
            } catch (e: Exception) {}
        }
        return dateFormat.format(Date())
    }

    fun getDisplayDateString(dateStr: String): String {
        return try {
            val date = dateFormat.parse(dateStr)
            displayFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateStr
        }
    }

    fun getArabicDisplayDate(dateStr: String): String {
        return try {
            val date = dateFormat.parse(dateStr) ?: Date()
            arabicDayAndMonthFormat.format(date)
        } catch (e: Exception) {
            dateStr
        }
    }

    fun getPreviousDayDateString(dateStr: String): String {
        return try {
            val date = dateFormat.parse(dateStr) ?: Date()
            val cal = Calendar.getInstance()
            cal.time = date
            cal.add(Calendar.DATE, -1)
            dateFormat.format(cal.time)
        } catch (e: Exception) {
            dateStr
        }
    }

    fun getNextDayDateString(dateStr: String): String {
        return try {
            val date = dateFormat.parse(dateStr) ?: Date()
            val cal = Calendar.getInstance()
            cal.time = date
            cal.add(Calendar.DATE, 1)
            dateFormat.format(cal.time)
        } catch (e: Exception) {
            dateStr
        }
    }
}
