package com.zty.voiceintelligence.data.dao

import androidx.room.*
import com.zty.voiceintelligence.data.entity.RuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM command_rules ORDER BY priority DESC, updatedAt DESC")
    fun observeAllRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM command_rules WHERE isEnabled = 1 ORDER BY priority DESC")
    suspend fun getEnabledRules(): List<RuleEntity>

    @Query("SELECT * FROM command_rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: Long): RuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RuleEntity): Long

    @Update
    suspend fun updateRule(rule: RuleEntity)

    @Delete
    suspend fun deleteRule(rule: RuleEntity)

    @Query("UPDATE command_rules SET triggerCount = triggerCount + 1 WHERE id = :ruleId")
    suspend fun incrementTriggerCount(ruleId: Long)

    @Query("SELECT COUNT(*) FROM command_rules")
    suspend fun countRules(): Int
}
