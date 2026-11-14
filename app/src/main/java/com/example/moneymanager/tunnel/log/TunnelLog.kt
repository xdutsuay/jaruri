package com.example.moneymanager.tunnel.log

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * The DNS log is a browsing history, so it lives in its own database
 * (`tunnel_log.db`), which backup_rules / data_extraction_rules exclude from
 * cloud backup and device transfer. Rules stay in the main DB.
 */
@Entity(
    tableName = "dns_log",
    indices = [Index("ts"), Index("pkg", "ts"), Index("name")]
)
data class DnsLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    /** -1 when Android didn't tell us which app asked. */
    val uid: Int,
    val pkg: String?,
    val name: String,
    val qtype: Int,
    val decision: String,
    val ref: String?,
    val rcode: Int,
    val latencyMs: Long
)

/** Daily counts per app and domain (pkg = "" when unknown); kept longer than the detail. */
@Entity(tableName = "dns_daily", primaryKeys = ["day", "pkg", "domain"])
data class DnsDailyEntity(
    /** Local-time day number (days since epoch). */
    val day: Long,
    val pkg: String,
    val domain: String,
    val allowed: Int,
    val blocked: Int
)

data class DayTotals(val allowed: Int, val blocked: Int)
data class DomainCount(val domain: String, val count: Int)

@Dao
interface DnsLogDao {
    @Insert
    suspend fun insertAll(rows: List<DnsLogEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDailyIgnore(row: DnsDailyEntity)

    @Query(
        "UPDATE dns_daily SET allowed = allowed + :allowed, blocked = blocked + :blocked " +
            "WHERE day = :day AND pkg = :pkg AND domain = :domain"
    )
    suspend fun bumpDaily(day: Long, pkg: String, domain: String, allowed: Int, blocked: Int)

    @Query(
        "SELECT * FROM dns_log WHERE (:blockedOnly = 0 OR decision IN ('BLOCKED_APP','BLOCKED_DOMAIN')) " +
            "AND (:search = '' OR name LIKE '%' || :search || '%' OR pkg LIKE '%' || :search || '%') " +
            "ORDER BY ts DESC, id DESC LIMIT :limit"
    )
    fun observeRecent(blockedOnly: Boolean, search: String, limit: Int = 500): Flow<List<DnsLogEntity>>

    @Query("SELECT * FROM dns_log WHERE ts >= :since ORDER BY ts ASC, id ASC")
    suspend fun since(since: Long): List<DnsLogEntity>

    @Query("SELECT COALESCE(SUM(allowed), 0) AS allowed, COALESCE(SUM(blocked), 0) AS blocked FROM dns_daily WHERE day = :day")
    fun observeDay(day: Long): Flow<DayTotals>

    @Query(
        "SELECT domain, SUM(blocked) AS count FROM dns_daily WHERE day >= :sinceDay AND blocked > 0 " +
            "GROUP BY domain ORDER BY count DESC LIMIT :limit"
    )
    suspend fun topBlocked(sinceDay: Long, limit: Int): List<DomainCount>

    @Query("SELECT COUNT(*) FROM dns_log")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM dns_log WHERE pkg = :pkg AND ts >= :since")
    suspend fun countFor(pkg: String, since: Long): Int

    @Query("SELECT pkg, COUNT(*) AS count FROM dns_log WHERE pkg IS NOT NULL AND ts >= :since GROUP BY pkg")
    suspend fun perApp(since: Long): List<PkgCount>

    @Query("DELETE FROM dns_log WHERE ts < :before")
    suspend fun deleteBefore(before: Long)

    /** Keep only the newest [max] rows. */
    @Query("DELETE FROM dns_log WHERE id <= (SELECT MAX(id) FROM dns_log) - :max")
    suspend fun capRows(max: Int)

    @Query("DELETE FROM dns_daily WHERE day < :beforeDay")
    suspend fun deleteDailyBefore(beforeDay: Long)

    @Query("DELETE FROM dns_log")
    suspend fun clearLog()

    @Query("DELETE FROM dns_daily")
    suspend fun clearDaily()
}

data class PkgCount(val pkg: String, val count: Int)

@Database(entities = [DnsLogEntity::class, DnsDailyEntity::class], version = 1, exportSchema = false)
abstract class TunnelLogDatabase : RoomDatabase() {
    abstract fun dao(): DnsLogDao

    companion object {
        const val NAME = "tunnel_log.db"
        @Volatile private var instance: TunnelLogDatabase? = null

        fun get(context: Context): TunnelLogDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, TunnelLogDatabase::class.java, NAME)
                    // A log, not user data: if a future schema can't migrate, start a fresh one.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
