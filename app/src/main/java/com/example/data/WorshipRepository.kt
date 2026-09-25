package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class WorshipRepository(private val worshipDao: WorshipDao) {
    val allRecords: Flow<List<DailyRecord>> = worshipDao.getAllRecords()
    val userProfile: Flow<UserProfile?> = worshipDao.getUserProfile()

    fun getRecordForDate(date: String): Flow<DailyRecord?> {
        return worshipDao.getRecordForDate(date)
    }

    suspend fun getRecordForDateSync(date: String): DailyRecord? {
        return worshipDao.getRecordForDateSync(date)
    }

    suspend fun saveRecord(record: DailyRecord) {
        worshipDao.insertOrUpdateRecord(record)
    }

    suspend fun saveProfile(profile: UserProfile) {
        worshipDao.insertOrUpdateProfile(profile)
    }

    suspend fun ensureProfileExists() {
        val existing = worshipDao.getUserProfileSync()
        if (existing == null) {
            worshipDao.insertOrUpdateProfile(UserProfile(id = 1, userName = "عابد لله"))
        }
    }
}
