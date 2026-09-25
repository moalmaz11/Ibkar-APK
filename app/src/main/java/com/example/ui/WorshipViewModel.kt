package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DailyRecord
import com.example.data.DateHelper
import com.example.data.UserProfile
import com.example.data.WorshipDatabase
import com.example.data.WorshipRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class WorshipViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: WorshipRepository

    init {
        val database = WorshipDatabase.getDatabase(application)
        repository = WorshipRepository(database.worshipDao())
        viewModelScope.launch {
            repository.ensureProfileExists()
        }
    }

    private val _selectedDate = MutableStateFlow(DateHelper.getTodayDateString(getApplication()))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val currentRecord: StateFlow<DailyRecord?> = _selectedDate
        .flatMapLatest { date ->
            repository.getRecordForDate(date)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val userProfile: StateFlow<UserProfile?> = repository.userProfile
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val allRecords: StateFlow<List<DailyRecord>> = repository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalPoints: StateFlow<Int> = combine(allRecords) { recordsList ->
        if (recordsList.isNotEmpty() && recordsList[0].isNotEmpty()) {
            recordsList[0].sumOf { it.calculatePoints() }
        } else {
            0
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val currentStreak: StateFlow<Int> = combine(allRecords) { recordsList ->
        if (recordsList.isNotEmpty() && recordsList[0].isNotEmpty()) {
            calculateStreak(recordsList[0])
        } else {
            0
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    fun selectPreviousDay() {
        _selectedDate.value = DateHelper.getPreviousDayDateString(_selectedDate.value)
    }

    fun selectNextDay() {
        val nextDay = DateHelper.getNextDayDateString(_selectedDate.value)
        val today = DateHelper.getTodayDateString(getApplication())
        if (nextDay <= today) {
            _selectedDate.value = nextDay
        }
    }

    fun selectToday() {
        _selectedDate.value = DateHelper.getTodayDateString(getApplication())
    }

    fun togglePrayer(prayer: String) {
        viewModelScope.launch {
            val date = _selectedDate.value
            val existing = repository.getRecordForDateSync(date) ?: DailyRecord(date = date)
            val updated = when (prayer) {
                "fajr" -> existing.copy(fajrDone = !existing.fajrDone)
                "dhuhr" -> existing.copy(dhuhrDone = !existing.dhuhrDone)
                "asr" -> existing.copy(asrDone = !existing.asrDone)
                "maghrib" -> existing.copy(maghribDone = !existing.maghribDone)
                "isha" -> existing.copy(ishaDone = !existing.ishaDone)
                else -> existing
            }
            repository.saveRecord(updated)
        }
    }

    fun setQuranPages(pages: Int) {
        viewModelScope.launch {
            val date = _selectedDate.value
            val existing = repository.getRecordForDateSync(date) ?: DailyRecord(date = date)
            val updated = existing.copy(quranPages = pages.coerceAtLeast(0))
            repository.saveRecord(updated)
        }
    }

    fun incrementDhikr() {
        viewModelScope.launch {
            val date = _selectedDate.value
            val existing = repository.getRecordForDateSync(date) ?: DailyRecord(date = date)
            val updated = existing.copy(dhikrCount = existing.dhikrCount + 1)
            repository.saveRecord(updated)
        }
    }

    fun resetDhikr() {
        viewModelScope.launch {
            val date = _selectedDate.value
            val existing = repository.getRecordForDateSync(date) ?: DailyRecord(date = date)
            val updated = existing.copy(dhikrCount = 0)
            repository.saveRecord(updated)
        }
    }

    fun toggleMorningDhikr() {
        viewModelScope.launch {
            val date = _selectedDate.value
            val existing = repository.getRecordForDateSync(date) ?: DailyRecord(date = date)
            val updated = existing.copy(morningDhikrDone = !existing.morningDhikrDone)
            repository.saveRecord(updated)
        }
    }

    fun toggleEveningDhikr() {
        viewModelScope.launch {
            val date = _selectedDate.value
            val existing = repository.getRecordForDateSync(date) ?: DailyRecord(date = date)
            val updated = existing.copy(eveningDhikrDone = !existing.eveningDhikrDone)
            repository.saveRecord(updated)
        }
    }

    fun updateProfileName(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                val current = userProfile.value ?: UserProfile(id = 1)
                repository.saveProfile(current.copy(userName = name.trim()))
            }
        }
    }

    private fun calculateStreak(records: List<DailyRecord>): Int {
        if (records.isEmpty()) return 0
        
        val activeDates = records.filter { record ->
            record.fajrDone && record.dhuhrDone && record.asrDone && record.maghribDone && record.ishaDone
        }.map { it.date }.toSet()

        if (activeDates.isEmpty()) return 0
        var streak = 0
        var checkDate = DateHelper.getTodayDateString(getApplication())
        
        if (!activeDates.contains(checkDate)) {
            checkDate = DateHelper.getPreviousDayDateString(checkDate)
        }

        while (activeDates.contains(checkDate)) {
            streak++
            checkDate = DateHelper.getPreviousDayDateString(checkDate)
        }
        return streak
    }

    fun getRankInfo(points: Int): RankSpec {
        return when {
            points < 20 -> RankSpec(
                title = "مُبْتَدِئ الخَيْر",
                description = "بداية مباركة، ثابر على الفرائض",
                currentPoints = points,
                nextLevelPoints = 20
            )
            points < 40 -> RankSpec(
                title = "مُحَافِظ",
                description = "خطوات ثابتة نحو المداومة على الطاعة",
                currentPoints = points,
                nextLevelPoints = 40
            )
            points < 60 -> RankSpec(
                title = "مُجْتَهِد",
                description = "إقبال طيب وتنافس في أبواب الخير",
                currentPoints = points,
                nextLevelPoints = 60
            )
            points < 80 -> RankSpec(
                title = "مُقَرَّب",
                description = "همة عالية وحرص على الأذكار والسنن",
                currentPoints = points,
                nextLevelPoints = 80
            )
            points < 100 -> RankSpec(
                title = "سَابِقٌ بِالْخَيْرَات",
                description = "أوشكت على استكمال جميع عبادات اليوم",
                currentPoints = points,
                nextLevelPoints = 100
            )
            else -> RankSpec(
                title = "صَاحِبُ التَّمَام",
                description = "ما شاء الله! حققت العلامة الكاملة لليوم",
                currentPoints = points,
                nextLevelPoints = 100
            )
        }
    }
}

data class RankSpec(
    val title: String,
    val description: String,
    val currentPoints: Int,
    val nextLevelPoints: Int
) {
    val progress: Float
        get() = if (nextLevelPoints == 0 || nextLevelPoints == Int.MAX_VALUE) 1f else (currentPoints.toFloat() / nextLevelPoints.toFloat()).coerceIn(0f, 1f)
}
