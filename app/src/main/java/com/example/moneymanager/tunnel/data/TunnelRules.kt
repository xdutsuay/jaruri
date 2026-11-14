package com.example.moneymanager.tunnel.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/*
 * Tunnel rules live in the main DB (v14) with the rest of the app's tables;
 * they stay empty until Tunnel is used.
 */

/** Per-app rule. No row = default (filtered like everyone else). */
@Entity(tableName = "tunnel_app_rules")
data class TunnelAppRuleEntity(
    @PrimaryKey val packageName: String,
    /** [MODE_BYPASS] or [MODE_BLOCK]. */
    val mode: String,
    val updatedAt: Long
) {
    companion object {
        /** Not routed through the tunnel at all (VpnService disallowed app). Reliable. */
        const val MODE_BYPASS = "BYPASS"
        /** DNS refused when Android tells us the app asked. Best effort. */
        const val MODE_BLOCK = "BLOCK"
    }
}

/** A manual domain rule: "x.com" (exact) or "*.x.com" (domain + subdomains). */
@Entity(tableName = "tunnel_domain_rules", indices = [Index(value = ["pattern"], unique = true)])
data class TunnelDomainRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    /** [ACTION_BLOCK] or [ACTION_ALLOW] (allow wins over every blocklist). */
    val action: String,
    val createdAt: Long
) {
    companion object {
        const val ACTION_BLOCK = "BLOCK"
        const val ACTION_ALLOW = "ALLOW"
    }
}

/** A user-imported blocklist; its domains are stored as a file under files/tunnel/lists/. */
@Entity(tableName = "tunnel_lists")
data class TunnelListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fileName: String,
    val entryCount: Int,
    @ColumnInfo(name = "enabled") val enabled: Boolean,
    val importedAt: Long
)

@Dao
interface TunnelRuleDao {
    @Query("SELECT * FROM tunnel_app_rules")
    fun observeAppRules(): Flow<List<TunnelAppRuleEntity>>

    @Query("SELECT * FROM tunnel_app_rules")
    suspend fun appRules(): List<TunnelAppRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setAppRule(rule: TunnelAppRuleEntity)

    @Query("DELETE FROM tunnel_app_rules WHERE packageName = :packageName")
    suspend fun clearAppRule(packageName: String)

    @Query("SELECT * FROM tunnel_domain_rules ORDER BY action, pattern")
    fun observeDomainRules(): Flow<List<TunnelDomainRuleEntity>>

    @Query("SELECT * FROM tunnel_domain_rules")
    suspend fun domainRules(): List<TunnelDomainRuleEntity>

    /** Same pattern again replaces the action (block ↔ allow). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDomainRule(rule: TunnelDomainRuleEntity): Long

    @Query("DELETE FROM tunnel_domain_rules WHERE id = :id")
    suspend fun deleteDomainRule(id: Long)

    @Query("SELECT * FROM tunnel_lists ORDER BY importedAt DESC")
    fun observeLists(): Flow<List<TunnelListEntity>>

    @Query("SELECT * FROM tunnel_lists")
    suspend fun lists(): List<TunnelListEntity>

    @Insert
    suspend fun insertList(list: TunnelListEntity): Long

    @Query("UPDATE tunnel_lists SET enabled = :enabled WHERE id = :id")
    suspend fun setListEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM tunnel_lists WHERE id = :id")
    suspend fun deleteList(id: Long)
}
