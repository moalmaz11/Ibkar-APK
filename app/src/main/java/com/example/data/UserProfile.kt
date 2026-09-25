package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey
    val id: Int = 1, // Single profile row
    val userName: String = "المسلم الذاكر",
    val joinDate: Long = System.currentTimeMillis()
)
