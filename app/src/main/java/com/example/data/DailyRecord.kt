package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_records")
data class DailyRecord(
    @PrimaryKey
    val date: String, // Format: YYYY-MM-DD
    val fajrDone: Boolean = false,
    val dhuhrDone: Boolean = false,
    val asrDone: Boolean = false,
    val maghribDone: Boolean = false,
    val ishaDone: Boolean = false,
    val quranPages: Int = 0,
    val dhikrCount: Int = 0,
    val morningDhikrDone: Boolean = false,
    val eveningDhikrDone: Boolean = false
) {
    // Dynamically calculate points for this daily record
    fun calculatePoints(): Int {
        var points = 0
        if (fajrDone) points += 12
        if (dhuhrDone) points += 12
        if (asrDone) points += 12
        if (maghribDone) points += 12
        if (ishaDone) points += 12
        
        // Bonus if all 5 prayers are completed
        if (fajrDone && dhuhrDone && asrDone && maghribDone && ishaDone) {
            points += 20
        }
        
        // Quran pages read: +1 point per page, up to a maximum of 10 points
        points += quranPages.coerceAtMost(10)
        
        // Morning and Evening Dhikr: +5 points each
        if (morningDhikrDone) points += 5
        if (eveningDhikrDone) points += 5
        
        return points.coerceAtMost(100)
    }

    // Dhikr count points are bonus points, calculated separately and not limited by 90 max
    fun calculateBonusPoints(): Int {
        return (dhikrCount / 33)
    }
}
