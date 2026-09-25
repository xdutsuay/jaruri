package com.example.moneymanager.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "category_learn")
data class CategoryLearnEntity(
    @PrimaryKey val merchantKey: String,
    val category: String,
    val type: String,
    val hitCount: Int = 1,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface CategoryLearnDao {
    @Query("SELECT * FROM category_learn WHERE merchantKey = :key LIMIT 1")
    suspend fun get(key: String): CategoryLearnEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: CategoryLearnEntity)

    @Query("SELECT * FROM category_learn ORDER BY hitCount DESC LIMIT :limit")
    suspend fun top(limit: Int = 200): List<CategoryLearnEntity>
}
