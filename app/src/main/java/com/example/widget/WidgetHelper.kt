package com.example.widget

import android.content.Context
import android.graphics.*
import com.example.notification.PrayerTimeCalculator
import java.util.Calendar

data class UpcomingPrayerInfoForWidget(
    val tag: String,
    val name: String,
    val timeStr: String,
    val diffMinutes: Int,
    val diffSeconds: Int
)

object WidgetHelper {
    fun getUpcomingPrayerInfo(context: Context): UpcomingPrayerInfoForWidget? {
        val prefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("user_latitude", PrayerTimeCalculator.DEFAULT_LATITUDE.toFloat()).toDouble()
        val lng = prefs.getFloat("user_longitude", PrayerTimeCalculator.DEFAULT_LONGITUDE.toFloat()).toDouble()
        val offsetMinutes = prefs.getInt("prayer_offset_minutes", 0)
        val calcMethod = prefs.getInt("prayer_calc_method", 0)

        val now = PrayerTimeCalculator.getLocalCalendar(lat, lng)
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val day = now.get(Calendar.DAY_OF_MONTH)

        val todayTimes = PrayerTimeCalculator.calculatePrayerTimes(year, month, day, lat, lng, calcMethod)
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val currentSeconds = now.get(Calendar.SECOND)
        val currentSecsFromMidnight = currentMinutes * 60 + currentSeconds

        val prayerList = listOf(
            "fajr" to "الفجر",
            "dhuhr" to "الظهر",
            "asr" to "العصر",
            "maghrib" to "المغرب",
            "isha" to "العشاء"
        )

        for (p in prayerList) {
            val t = todayTimes[p.first]
            if (t != null) {
                val pMinutes = t.first * 60 + t.second
                val prayerSecsFromMidnight = pMinutes * 60
                if (prayerSecsFromMidnight > currentSecsFromMidnight) {
                    val remainingSeconds = prayerSecsFromMidnight - currentSecsFromMidnight
                    val diffMin = (remainingSeconds / 60).toInt()
                    val diffSec = (remainingSeconds % 60).toInt()
                    val h12 = if (t.first % 12 == 0) 12 else t.first % 12
                    val amPm = if (t.first >= 12) "م" else "ص"
                    val timeStr = "%d:%02d %s".format(h12, t.second, amPm)
                    return UpcomingPrayerInfoForWidget(p.first, p.second, timeStr, diffMin, diffSec)
                }
            }
        }

        val t = todayTimes["fajr"]
        if (t != null) {
            val pMinutes = t.first * 60 + t.second
            val prayerSecsFromMidnight = (pMinutes + 24 * 60) * 60
            val remainingSeconds = prayerSecsFromMidnight - currentSecsFromMidnight
            val diffMin = (remainingSeconds / 60).toInt()
            val diffSec = (remainingSeconds % 60).toInt()
            val h12 = if (t.first % 12 == 0) 12 else t.first % 12
            val amPm = "ص"
            val timeStr = "%d:%02d %s".format(h12, t.second, amPm)
            return UpcomingPrayerInfoForWidget("fajr", "فجر الغد", timeStr, diffMin, diffSec)
        }
        return null
    }

    fun getTodayPrayerTimes(context: Context): List<Pair<String, String>> {
        val prefs = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("user_latitude", PrayerTimeCalculator.DEFAULT_LATITUDE.toFloat()).toDouble()
        val lng = prefs.getFloat("user_longitude", PrayerTimeCalculator.DEFAULT_LONGITUDE.toFloat()).toDouble()
        val offsetMinutes = prefs.getInt("prayer_offset_minutes", 0)
        val calcMethod = prefs.getInt("prayer_calc_method", 0)

        val now = PrayerTimeCalculator.getLocalCalendar(lat, lng)
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val day = now.get(Calendar.DAY_OF_MONTH)

        val todayTimes = PrayerTimeCalculator.calculatePrayerTimes(year, month, day, lat, lng, calcMethod)
        val names = listOf(
            "fajr" to "الفجر",
            "dhuhr" to "الظهر",
            "asr" to "العصر",
            "maghrib" to "المغرب",
            "isha" to "العشاء"
        )

        val result = mutableListOf<Pair<String, String>>()
        for (n in names) {
            val t = todayTimes[n.first]
            if (t != null) {
                val h12 = if (t.first % 12 == 0) 12 else t.first % 12
                val amPm = if (t.first >= 12) "م" else "ص"
                val timeStr = "%d:%02d %s".format(h12, t.second, amPm)
                result.add(n.second to timeStr)
            } else {
                result.add(n.second to "--:--")
            }
        }
        return result
    }

    fun drawCountdownWidget(context: Context): Bitmap {
        val width = 800
        val height = 200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val upcoming = getUpcomingPrayerInfo(context)
        val colors = when (upcoming?.tag) {
            "fajr" -> intArrayOf(0xFF0F1E36.toInt(), 0xFF1D3557.toInt())
            "dhuhr" -> intArrayOf(0xFF4D342F.toInt(), 0xFF3E2723.toInt())
            "asr" -> intArrayOf(0xFF37474F.toInt(), 0xFF263238.toInt())
            "maghrib" -> intArrayOf(0xFF4A148C.toInt(), 0xFF311B92.toInt())
            "isha" -> intArrayOf(0xFF0D1B2A.toInt(), 0xFF1B263B.toInt())
            else -> intArrayOf(0xFF064E3B.toInt(), 0xFF022C22.toInt())
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bgGrad = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors, null, Shader.TileMode.CLAMP)
        paint.shader = bgGrad
        val rect = RectF(8f, 8f, (width - 8).toFloat(), (height - 8).toFloat())
        canvas.drawRoundRect(rect, 30f, 30f, paint)

        paint.shader = null
        paint.color = Color.WHITE
        paint.alpha = 30
        paint.strokeWidth = 2.5f
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(rect, 30f, 30f, paint)

        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE

        if (upcoming != null) {
            val h = upcoming.diffMinutes / 60
            val m = upcoming.diffMinutes % 60
            val countdownFormatted = if (h > 0) {
                "%02d:%02d".format(h, m)
            } else if (m > 0) {
                "%02d د".format(m)
            } else {
                "حان الآن"
            }
            val countdownLabel = if (h > 0) {
                "ساعة ودقيقة"
            } else if (m > 0) {
                "دقيقة متبقية"
            } else {
                "وقت الأذان"
            }

            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            dividerPaint.color = Color.WHITE
            dividerPaint.alpha = 35
            dividerPaint.strokeWidth = 1.5f
            canvas.drawLine(400f, 35f, 400f, 165f, dividerPaint)

            paint.textAlign = Paint.Align.RIGHT
            paint.color = Color.WHITE
            paint.alpha = 190
            paint.textSize = 22f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("الصلاة القادمة:", 745f, 58f, paint)

            paint.alpha = 255
            paint.textSize = 38f
            canvas.drawText("صلاة ${upcoming.name}", 745f, 112f, paint)

            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            pillPaint.color = Color.WHITE
            pillPaint.alpha = 35
            val pillRect = RectF(515f, 134f, 745f, 178f)
            canvas.drawRoundRect(pillRect, 16f, 16f, pillPaint)

            paint.textSize = 22f
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("الأذان: ${upcoming.timeStr}", 630f, 165f, paint)

            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 22f
            paint.alpha = 190
            canvas.drawText("الوقت المتبقي:", 55f, 58f, paint)

            paint.alpha = 255
            paint.textSize = 62f
            paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            paint.color = Color.parseColor("#FFD54F")
            canvas.drawText(countdownFormatted, 50f, 125f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 20f
            paint.color = Color.WHITE
            paint.alpha = 150
            canvas.drawText(countdownLabel, 55f, 168f, paint)
        } else {
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 28f
            canvas.drawText("جاري تحميل مواقيت الصلاة...", 400f, 115f, paint)
        }
        return bitmap
    }

    fun drawPrayerTimesWidget(context: Context): Bitmap {
        val width = 800
        val height = 600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val colors = intArrayOf(0xFF071913.toInt(), 0xFF142D21.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bgGrad = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), colors, null, Shader.TileMode.CLAMP)
        paint.shader = bgGrad
        val rect = RectF(10f, 10f, (width - 10).toFloat(), (height - 10).toFloat())
        canvas.drawRoundRect(rect, 40f, 40f, paint)

        paint.shader = null
        paint.color = Color.parseColor("#44B0BEC5")
        paint.strokeWidth = 2.5f
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(rect, 40f, 40f, paint)

        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.parseColor("#FFD54F")
        paint.textSize = 34f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("مواقيت الصلاة اليوم", 740f, 75f, paint)

        paint.color = Color.WHITE
        paint.alpha = 150
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("تطبيق إِبْكَـار", 740f, 115f, paint)

        paint.alpha = 40
        paint.color = Color.WHITE
        paint.strokeWidth = 2f
        canvas.drawLine(60f, 140f, 740f, 140f, paint)

        val upcoming = getUpcomingPrayerInfo(context)
        val times = getTodayPrayerTimes(context)

        var currentY = 205f
        val stepY = 82f
        for (item in times) {
            val isUpcoming = upcoming != null && upcoming.name.contains(item.first)
            val rowRect = RectF(60f, currentY - 45f, 740f, currentY + 15f)
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            if (isUpcoming) {
                fillPaint.color = Color.parseColor("#10B981")
                fillPaint.alpha = 70
                canvas.drawRoundRect(rowRect, 18f, 18f, fillPaint)

                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                borderPaint.color = Color.parseColor("#FFD54F")
                borderPaint.style = Paint.Style.STROKE
                borderPaint.strokeWidth = 2.2f
                canvas.drawRoundRect(rowRect, 18f, 18f, borderPaint)
            } else {
                fillPaint.color = Color.WHITE
                fillPaint.alpha = 15
                canvas.drawRoundRect(rowRect, 18f, 18f, fillPaint)
            }

            paint.reset()
            paint.isAntiAlias = true
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.RIGHT
            paint.textSize = 28f
            if (isUpcoming) {
                paint.color = Color.parseColor("#FFD54F")
                paint.alpha = 255
                canvas.drawText("${item.first} (القادمة)", 715f, currentY - 5f, paint)
            } else {
                paint.color = Color.WHITE
                paint.alpha = 255
                canvas.drawText(item.first, 715f, currentY - 5f, paint)
            }

            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 28f
            if (isUpcoming) {
                paint.color = Color.parseColor("#FFD54F")
            } else {
                paint.color = Color.parseColor("#FFE082")
            }
            canvas.drawText(item.second, 85f, currentY - 5f, paint)
            currentY += stepY
        }

        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color.WHITE
        paint.alpha = 100
        paint.textSize = 18f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("إِبْكَـار • رفيقك الإيماني", 400f, 560f, paint)
        return bitmap
    }
}
