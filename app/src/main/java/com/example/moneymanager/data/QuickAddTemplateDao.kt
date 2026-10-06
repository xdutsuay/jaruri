package com.example.moneymanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickAddTemplateDao {
    @Query("SELECT * FROM quick_add_templates ORDER BY pinOrder ASC, name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<QuickAddTemplateEntity>>

    @Query("SELECT * FROM quick_add_templates ORDER BY pinOrder ASC, name COLLATE NOCASE ASC")
    suspend fun getAllList(): List<QuickAddTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: QuickAddTemplateEntity): Long

    @Update
    suspend fun update(template: QuickAddTemplateEntity)

    @Delete
    suspend fun delete(template: QuickAddTemplateEntity)

    @Query("DELETE FROM quick_add_templates WHERE id = :id")
    suspend fun deleteById(id: Long)
}
