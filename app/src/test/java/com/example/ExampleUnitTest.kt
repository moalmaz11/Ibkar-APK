package com.example

import org.junit.Assert.*
import org.junit.Test
import com.example.notification.PrayerTimeCalculator

class ExampleUnitTest {
  @Test
  fun testCairoPrayerTimes() {
    val times = PrayerTimeCalculator.calculatePrayerTimes(2026, 5, 30, 30.0444, 31.2357, 0, 3.0)
    println("--- Computed Cairo Prayer Times for 2026-05-30 ---")
    times.forEach { (key, value) ->
       println("$key = ${"%02d:%02d".format(value.first, value.second)}")
    }
  }
}

