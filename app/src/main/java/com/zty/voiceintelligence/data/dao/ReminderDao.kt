package com.zty.voiceintelligence.data.dao

import androidx.room.*
import com.zty.voiceintelligence.data.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY isCompleted ASC, triggerTimeMillis ASC")
    fun observeAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("UPDATE reminders SET isCompleted = :completed WHERE id = :id")
    suspend fun updateCompletedStatus(id: Long, completed: Boolean)

    @Query("UPDATE reminders SET triggerTimeMillis = :newTimeMillis, snoozeCount = snoozeCount + 1, isCompleted = 0 WHERE id = :id")
    suspend fun snoozeReminder(id: Long, newTimeMillis: Long)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)
}
