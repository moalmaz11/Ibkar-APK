package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DailyRecord::class, UserProfile::class],
    version = 2,
    exportSchema = false
)
abstract class WorshipDatabase : RoomDatabase() {
    abstract fun worshipDao(): WorshipDao

    companion object {
        @Volatile
        private var INSTANCE: WorshipDatabase? = null

        fun getDatabase(context: Context): WorshipDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WorshipDatabase::class.java,
                    "worship_database"
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
