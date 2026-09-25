package com.example.notification

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

object PrayerTimeCalculator {
    
    // Default: Cairo, Egypt
    const val DEFAULT_LATITUDE = 30.0444
    const val DEFAULT_LONGITUDE = 31.2357

    /**
     * Calculates prayer times for a specific date, latitude, longitude, and timezone.
     * Returns a map of prayerKey to Pair(hour, minute).
     */
    fun calculatePrayerTimes(
        year: Int,
        month: Int, // 1-12
        day: Int,
        latitude: Double,
        longitude: Double,
        calcMethod: Int = 0,
        timezoneOffset: Double = getUserTimezoneOffset(year, month, day, latitude, longitude)
    ): Map<String, Pair<Int, Int>> {
        
        // 1. Calculate Julian Date minus 2451545.0
        val julianDate = getJulianDate(year, month, day)
        val d = julianDate - 2451545.0
        
        // 2. Solar coordinates (standard NOAA solar equations used by PrayTimes)
        val g = (357.529 + 0.98560028 * d)
        val q = (280.459 + 0.98564736 * d)
        
        // Normalize angles to [0, 360)
        val gNorm = normalizeAngle(g)
        val qNorm = normalizeAngle(q)
        
        val gRad = Math.toRadians(gNorm)
        val lambda = normalizeAngle(qNorm + 1.915 * sin(gRad) + 0.020 * sin(2.0 * gRad))
        val lambdaRad = Math.toRadians(lambda)
        
        // Obliquity of the ecliptic
        val obliquity = 23.439 - 0.00000036 * d
        val obRad = Math.toRadians(obliquity)
        
        // Sun declination (in degrees)
        val declination = Math.toDegrees(asin(sin(obRad) * sin(lambdaRad)))
        
        // Right Ascension (RA) in hours
        var alpha = Math.toDegrees(atan2(cos(obRad) * sin(lambdaRad), cos(lambdaRad))) / 15.0
        alpha = (alpha + 24.0) % 24.0
        
        // Equation of Time in hours
        var equationOfTime = (qNorm / 15.0) - alpha
        while (equationOfTime < -12.0) equationOfTime += 24.0
        while (equationOfTime > 12.0) equationOfTime -= 24.0
        
        // 3. MIDDAY (Dhuhr)
        // Local apparent noon
        val dhuhrLocal = 12.0 + timezoneOffset - (longitude / 15.0) - equationOfTime
        
        // Helper to calculate hour angle of altitude
        fun hourAngle(altitude: Double): Double? {
            val latRad = Math.toRadians(latitude)
            val decRad = Math.toRadians(declination)
            val altRad = Math.toRadians(altitude)
            
            val numerator = sin(altRad) - sin(latRad) * sin(decRad)
            val denominator = cos(latRad) * cos(decRad)
            val cosH = numerator / denominator
            
            if (cosH < -1.0 || cosH > 1.0) return null // Out of range
            return Math.toDegrees(acos(cosH))
        }
        
        // Determine Angles based on Calculation Method
        // 0: Egypt Survey, 1: UmmAlQura (Makkah), 2: MWL, 3: ISNA, 4: Karachi, 5: Gulf Region
        val fajrAngle = when (calcMethod) {
            0 -> -19.5
            1 -> -18.5
            2 -> -18.0
            3 -> -15.0
            4 -> -18.0
            5 -> -19.5
            else -> -19.5
        }
        
        val ishaAngle = when (calcMethod) {
            0 -> -17.5
            2 -> -17.0
            3 -> -15.0
            4 -> -18.0
            else -> -17.5
        }
        
        // Fajr (twilight angle based on method)
        val fajrHA = hourAngle(fajrAngle) ?: 100.0
        val fajrLocal = dhuhrLocal - (fajrHA / 15.0)
        
        // Sunrise (approximate, altitude -0.833)
        val sunriseHA = hourAngle(-0.833) ?: 90.0
        val sunriseLocal = dhuhrLocal - (sunriseHA / 15.0)
        
        // Asr (Shafi'i/Standard Method: shadow ratio = 1)
        val latRad = Math.toRadians(latitude)
        val decRad = Math.toRadians(declination)
        val asrAltRad = atan(1.0 / (1.0 + tan(abs(latRad - decRad))))
        val asrAlt = Math.toDegrees(asrAltRad)
        val asrHA = hourAngle(asrAlt) ?: 50.0
        val asrLocal = dhuhrLocal + (asrHA / 15.0)
        
        // Maghrib (Sunset, altitude -0.833)
        val maghribHA = hourAngle(-0.833) ?: 90.0
        val maghribLocal = dhuhrLocal + (maghribHA / 15.0)
        
        // Isha
        val ishaLocal = if (calcMethod == 1 || calcMethod == 5) {
            // Umm Al-Qura & Gulf is always Maghrib + 90 minutes
            maghribLocal + (90.0 / 60.0)
        } else {
            val ishaHA = hourAngle(ishaAngle) ?: 100.0
            dhuhrLocal + (ishaHA / 15.0)
        }
        
        // Helper to format hours in daily range
        fun formatTime(hours: Double): Pair<Int, Int> {
            var m = (hours * 60.0).roundToInt()
            m = (m + 1440) % 1440
            val h = m / 60
            val min = m % 60
            return Pair(h, min)
        }
        
        val fTime = formatTime(fajrLocal)
        val dTime = formatTime(dhuhrLocal)
        val aTime = formatTime(asrLocal)
        val mTime = formatTime(maghribLocal)
        val iTime = formatTime(ishaLocal)
        
        // Morning dhikr is 20 minutes after sunrise
        val sunriseTime = formatTime(sunriseLocal)
        var morningDhikrMin = sunriseTime.first * 60 + sunriseTime.second + 20
        morningDhikrMin = (morningDhikrMin + 1440) % 1440
        val mdTime = Pair(morningDhikrMin / 60, morningDhikrMin % 60)
        
        // Evening dhikr is usually 1 hour before Maghrib
        var eveningDhikrMin = mTime.first * 60 + mTime.second - 60
        eveningDhikrMin = (eveningDhikrMin + 1440) % 1440
        val edTime = Pair(eveningDhikrMin / 60, eveningDhikrMin % 60)
        
        return mapOf(
            "fajr" to fTime,
            "dhuhr" to dTime,
            "asr" to aTime,
            "maghrib" to mTime,
            "isha" to iTime,
            "morning_dhikr" to mdTime,
            "evening_dhikr" to edTime
        )
    }
    
    fun getUserTimezoneOffset(year: Int, month: Int, day: Int, latitude: Double = DEFAULT_LATITUDE, longitude: Double = DEFAULT_LONGITUDE): Double {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        var offset = TimeZone.getDefault().getOffset(cal.timeInMillis) / 3600000.0
        
        // Auto-compensation for UTC emulators/platforms when coordinates are definitely in Egypt or Middle East
        if (offset == 0.0) {
            if (longitude in 24.0..37.0 && latitude in 22.0..32.0) {
                // Determine if in DST period for Egypt (roughly May to October)
                offset = if (month in 5..10) 3.0 else 2.0
            } else if (longitude in 35.0..60.0 && latitude in 15.0..33.0) {
                // Gulf region (Saudi Arabia, UAE etc. are UTC+3 or UTC+4)
                offset = if (longitude > 45.0) 4.0 else 3.0
            }
        }
        return offset
    }
    
    // Julian date helper
    private fun getJulianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2.0 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }
    
    fun getLocalCalendar(latitude: Double, longitude: Double): Calendar {
        val gmtCal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        val yr = gmtCal.get(Calendar.YEAR)
        val mo = gmtCal.get(Calendar.MONTH) + 1
        val dy = gmtCal.get(Calendar.DAY_OF_MONTH)
        
        val offsetHours = getUserTimezoneOffset(yr, mo, dy, latitude, longitude)
        
        val localMillis = gmtCal.timeInMillis + (offsetHours * 3600000.0).toLong()
        val localCal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        localCal.timeInMillis = localMillis
        return localCal
    }

    private fun normalizeAngle(angle: Double): Double {
        var a = angle % 360.0
        if (a < 0) {
            a += 360.0
        }
        return a
    }
}
