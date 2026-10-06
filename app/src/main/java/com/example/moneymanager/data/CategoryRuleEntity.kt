package com.example.moneymanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * User-defined contains-keyword → category rule.
 * Applied after learned merchant keys, before built-in keyword lists.
 */
@Entity(tableName = "category_rules")
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Case-insensitive substring matched against SMS body / merchant / memo. */
    val keyword: String,
    val category: String,
    /** INCOME, EXPENSE, or ANY */
    val type: String = TYPE_ANY,
    /** Lower = earlier match. */
    val priority: Int = 100,
    val enabled: Boolean = true
) {
    companion object {
        const val TYPE_ANY = "ANY"
        const val TYPE_INCOME = "INCOME"
        const val TYPE_EXPENSE = "EXPENSE"
    }
}

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules ORDER BY priority ASC, id ASC")
    fun getAll(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules WHERE enabled = 1 ORDER BY priority ASC, id ASC")
    suspend fun getEnabledList(): List<CategoryRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: CategoryRuleEntity): Long

    @Update
    suspend fun update(rule: CategoryRuleEntity)

    @Delete
    suspend fun delete(rule: CategoryRuleEntity)
}
