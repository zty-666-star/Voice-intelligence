package com.zty.voiceintelligence.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.zty.voiceintelligence.data.dao.ChatDao
import com.zty.voiceintelligence.data.dao.ReminderDao
import com.zty.voiceintelligence.data.dao.RuleDao
import com.zty.voiceintelligence.data.entity.ChatMessageEntity
import com.zty.voiceintelligence.data.entity.ReminderEntity
import com.zty.voiceintelligence.data.entity.RuleEntity

@Database(
    entities = [RuleEntity::class, ReminderEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun reminderDao(): ReminderDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "voice_intelligence.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
