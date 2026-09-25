package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorshipDao {
    @Query("SELECT * FROM daily_records WHERE date = :date")
    fun getRecordForDate(date: String): Flow<DailyRecord?>

    @Query("SELECT * FROM daily_records WHERE date = :date")
    suspend fun getRecordForDateSync(date: String): DailyRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRecord(record: DailyRecord)

    @Query("SELECT * FROM daily_records ORDER BY date DESC")
    fun getAllRecords(): Flow<List<DailyRecord>>

    @Query("SELECT * FROM daily_records ORDER BY date DESC")
    suspend fun getAllRecordsSync(): List<DailyRecord>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getUserProfileSync(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfile)
}
