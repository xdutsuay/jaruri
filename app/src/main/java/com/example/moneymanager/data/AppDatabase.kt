package com.example.moneymanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.moneymanager.listener.ListenerSessionDao
import com.example.moneymanager.listener.ListenerSessionEntity
import com.example.moneymanager.listener.transcribe.TranscriptDao
import com.example.moneymanager.listener.transcribe.TranscriptFtsEntity
import com.example.moneymanager.listener.transcribe.TranscriptSegmentEntity
import com.example.moneymanager.tunnel.data.TunnelAppRuleEntity
import com.example.moneymanager.tunnel.data.TunnelDomainRuleEntity
import com.example.moneymanager.tunnel.data.TunnelListEntity
import com.example.moneymanager.tunnel.data.TunnelRuleDao
import com.example.moneymanager.wellbeing.UsageDailyDao
import com.example.moneymanager.wellbeing.UsageDailyEntity

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        BudgetEntity::class,
        RecurringEntity::class,
        CategoryLearnEntity::class,
        CategoryRuleEntity::class,
        TimeEntryEntity::class,
        BalanceObservationEntity::class,
        UsageDailyEntity::class,
        PayeeEntity::class,
        PayeeAliasEntity::class,
        QuickAddTemplateEntity::class,
        ListenerSessionEntity::class,
        TranscriptSegmentEntity::class,
        TranscriptFtsEntity::class,
        TunnelAppRuleEntity::class,
        TunnelDomainRuleEntity::class,
        TunnelListEntity::class
    ],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringDao(): RecurringDao
    abstract fun categoryLearnDao(): CategoryLearnDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun timeEntryDao(): TimeEntryDao
    abstract fun balanceObservationDao(): BalanceObservationDao
    abstract fun usageDailyDao(): UsageDailyDao
    abstract fun payeeDao(): PayeeDao
    abstract fun quickAddTemplateDao(): QuickAddTemplateDao
    abstract fun listenerSessionDao(): ListenerSessionDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun tunnelRuleDao(): TunnelRuleDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN deletedAt INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS category_learn (
                        merchantKey TEXT NOT NULL PRIMARY KEY,
                        category TEXT NOT NULL,
                        type TEXT NOT NULL,
                        hitCount INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS time_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        label TEXT NOT NULL,
                        category TEXT NOT NULL,
                        durationMinutes INTEGER NOT NULL,
                        startedAt INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN needsCategoryReview INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("ALTER TABLE accounts ADD COLUMN bankHint TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE accounts ADD COLUMN lastReportedBalance REAL")
                db.execSQL("ALTER TABLE accounts ADD COLUMN lastReportedKind TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "ALTER TABLE accounts ADD COLUMN runningDifference REAL NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE accounts ADD COLUMN observationCount INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE accounts ADD COLUMN lastBalanceObservedAt INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("ALTER TABLE accounts ADD COLUMN seenCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS balance_observations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        accountId INTEGER NOT NULL,
                        reportedBalance REAL NOT NULL,
                        balanceKind TEXT NOT NULL,
                        ledgerBalance REAL NOT NULL,
                        difference REAL NOT NULL,
                        smsHash TEXT NOT NULL,
                        transactionId INTEGER,
                        observedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN transferToAccountId INTEGER"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS category_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        keyword TEXT NOT NULL,
                        category TEXT NOT NULL,
                        type TEXT NOT NULL,
                        priority INTEGER NOT NULL,
                        enabled INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Voice journal: text-only source flag on time_entries (no audio blob). */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addTimeEntrySourceColumnIfMissing(db)
            }
        }

        /** Usage wellbeing: daily per-app foreground minute aggregates. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS usage_daily (
                        day TEXT NOT NULL,
                        packageName TEXT NOT NULL,
                        minutes INTEGER NOT NULL,
                        PRIMARY KEY(day, packageName)
                    )
                    """.trimIndent()
                )
                // Concurrent agents may have ordered 6→7 differently; ensure source exists.
                addTimeEntrySourceColumnIfMissing(db)
            }
        }

        /** UPI payee resolution: payees + aliases + transactions.payeeId. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS payees (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        displayName TEXT NOT NULL,
                        normalizedKey TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS payee_aliases (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        payeeId INTEGER NOT NULL,
                        aliasKey TEXT NOT NULL,
                        FOREIGN KEY(payeeId) REFERENCES payees(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_payee_aliases_aliasKey ON payee_aliases(aliasKey)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_payee_aliases_payeeId ON payee_aliases(payeeId)"
                )
                db.execSQL("ALTER TABLE transactions ADD COLUMN payeeId INTEGER")
            }
        }

        /** Refund/reversal matching columns. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN upiRef TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN rrn TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN linkedTransactionId INTEGER")
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN isRefundNeutral INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** Pinned quick-add templates. */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quick_add_templates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        amount REAL NOT NULL,
                        category TEXT NOT NULL,
                        type TEXT NOT NULL,
                        note TEXT NOT NULL,
                        pinOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Listener v1: manual meeting recordings (index only; audio in filesDir). Additive. */
        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS listener_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        endedAt INTEGER,
                        durationMs INTEGER NOT NULL,
                        segmentCount INTEGER NOT NULL,
                        bytes INTEGER NOT NULL,
                        state TEXT NOT NULL,
                        dirName TEXT NOT NULL,
                        notes TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Listener v2: transcription state on sessions, timestamped transcript
         * segments, and an FTS4 index over them (content table + Room's sync
         * triggers, same SQL Room generates). Additive.
         */
        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE listener_sessions ADD COLUMN transcriptState TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE listener_sessions ADD COLUMN transcribedChunks INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE listener_sessions ADD COLUMN transcriptError TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transcript_segments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, `chunkIndex` INTEGER NOT NULL, `startMs` INTEGER NOT NULL, " +
                        "`endMs` INTEGER NOT NULL, `text` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transcript_segments_sessionId` ON `transcript_segments` (`sessionId`)"
                )
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `transcript_fts` USING FTS4(`text` TEXT NOT NULL, content=`transcript_segments`)"
                )
                for (sql in TRANSCRIPT_FTS_TRIGGERS) db.execSQL(sql)
            }
        }

        /** Tunnel v1: per-app and domain rules + imported list registry. Additive. */
        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tunnel_app_rules` (`packageName` TEXT NOT NULL, " +
                        "`mode` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`packageName`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tunnel_domain_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`pattern` TEXT NOT NULL, `action` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_tunnel_domain_rules_pattern` ON `tunnel_domain_rules` (`pattern`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tunnel_lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `fileName` TEXT NOT NULL, `entryCount` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, `importedAt` INTEGER NOT NULL)"
                )
            }
        }

        private val TRANSCRIPT_FTS_TRIGGERS = listOf(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_transcript_fts_BEFORE_UPDATE BEFORE UPDATE ON `transcript_segments` BEGIN DELETE FROM `transcript_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_transcript_fts_BEFORE_DELETE BEFORE DELETE ON `transcript_segments` BEGIN DELETE FROM `transcript_fts` WHERE `docid`=OLD.`rowid`; END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_transcript_fts_AFTER_UPDATE AFTER UPDATE ON `transcript_segments` BEGIN INSERT INTO `transcript_fts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END",
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_transcript_fts_AFTER_INSERT AFTER INSERT ON `transcript_segments` BEGIN INSERT INTO `transcript_fts`(`docid`, `text`) VALUES (NEW.`rowid`, NEW.`text`); END"
        )

        private fun addTimeEntrySourceColumnIfMissing(db: SupportSQLiteDatabase) {
            val cursor = db.query("PRAGMA table_info(time_entries)")
            var hasSource = false
            cursor.use {
                val nameIdx = it.getColumnIndex("name")
                while (it.moveToNext()) {
                    if (nameIdx >= 0 && it.getString(nameIdx) == "source") {
                        hasSource = true
                        break
                    }
                }
            }
            if (!hasSource) {
                db.execSQL(
                    "ALTER TABLE time_entries ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "money_manager_db"
                )
                    .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14
                    )
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
