package com.example.moneymanager.listener

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema is not exported (exportSchema=false), so instead of MigrationTestHelper
 * we fabricate a v11 database by creating v12 and removing what 11→12 adds,
 * then reopen with only MIGRATION_11_12. Room validates every table against the
 * entities after migrating, so a column/type/nullability mismatch fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ListenerMigrationTest {

    private val dbName = "listener-migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createV11WithExistingData() {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val sql = db.openHelper.writableDatabase
        sql.execSQL(
            "INSERT INTO time_entries (label, category, durationMinutes, startedAt, notes, createdAt, source) " +
                "VALUES ('standup', 'Work', 15, 1000, 'n', 1000, 'MANUAL')"
        )
        dropV13Additions(sql)
        sql.execSQL("DROP TABLE listener_sessions")
        sql.version = 11
        db.close()
    }

    private fun dropV14Additions(sql: SupportSQLiteDatabase) {
        for (t in listOf("tunnel_app_rules", "tunnel_domain_rules", "tunnel_lists")) sql.execSQL("DROP TABLE IF EXISTS $t")
    }

    private fun dropV13Additions(sql: SupportSQLiteDatabase) {
        dropV14Additions(sql)
        for (t in listOf("BEFORE_UPDATE", "BEFORE_DELETE", "AFTER_UPDATE", "AFTER_INSERT")) {
            sql.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_transcript_fts_$t")
        }
        sql.execSQL("DROP TABLE IF EXISTS transcript_fts")
        sql.execSQL("DROP TABLE IF EXISTS transcript_segments")
    }

    /** v12 = v13 minus transcript tables and the three new listener_sessions columns. */
    private fun createV12WithExistingSession() {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val sql = db.openHelper.writableDatabase
        dropV13Additions(sql)
        sql.execSQL("ALTER TABLE listener_sessions RENAME TO ls_v13")
        sql.execSQL(
            """
            CREATE TABLE listener_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL,
                startedAt INTEGER NOT NULL, endedAt INTEGER, durationMs INTEGER NOT NULL,
                segmentCount INTEGER NOT NULL, bytes INTEGER NOT NULL, state TEXT NOT NULL,
                dirName TEXT NOT NULL, notes TEXT NOT NULL)
            """.trimIndent()
        )
        sql.execSQL("DROP TABLE ls_v13")
        sql.execSQL(
            "INSERT INTO listener_sessions (title, startedAt, endedAt, durationMs, segmentCount, bytes, state, dirName, notes) " +
                "VALUES ('Old meeting', 100, 200, 100, 1, 10, 'DONE', 'session-100', 'USER')"
        )
        sql.version = 12
        db.close()
    }

    @Test
    fun migrate12To13_addsTranscriptTables_keepsSessions_andFtsWorks() = runBlocking {
        createV12WithExistingSession()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()
        assertEquals(14, db.openHelper.writableDatabase.version)

        val old = db.listenerSessionDao().getAll().first().single()
        assertEquals("Old meeting", old.title)
        assertEquals(ListenerSessionEntity.TRANSCRIPT_NONE, old.transcriptState)
        assertEquals(0, old.transcribedChunks)

        val t = db.transcriptDao()
        t.insertAll(
            listOf(
                com.example.moneymanager.listener.transcribe.TranscriptSegmentEntity(
                    sessionId = old.id, chunkIndex = 0, startMs = 0, endMs = 900, text = "budget review tomorrow"
                )
            )
        )
        // Triggers created by the migration keep the FTS index in sync.
        val hits = t.search(com.example.moneymanager.listener.transcribe.FtsQuery.build("budg")!!)
        assertEquals(1, hits.size)
        assertEquals("Old meeting", hits[0].sessionTitle)
        db.close()
    }

    @Test
    fun migrate11To12_createsListenerTable_andKeepsExistingData() = runBlocking {
        createV11WithExistingData()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()
        val sql = db.openHelper.writableDatabase // triggers migrate + validation
        assertEquals(14, sql.version)

        val entries = db.timeEntryDao().getAll().first()
        assertEquals(1, entries.size)
        assertEquals("standup", entries[0].label)

        val dao = db.listenerSessionDao()
        val id = dao.insert(
            ListenerSessionEntity(title = "t", startedAt = 5, dirName = "session-5")
        )
        val row = dao.getById(id)!!
        assertEquals(ListenerSessionEntity.STATE_RECORDING, row.state)
        assertEquals(null, row.endedAt)
        db.close()
    }

    @Test
    fun brokenMigrationIsCaughtByValidation() {
        createV11WithExistingData()
        val broken = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Missing columns on purpose: proves the test above has teeth.
                db.execSQL(
                    "CREATE TABLE listener_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL)"
                )
            }
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(broken, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()
        var failed = false
        try {
            db.openHelper.writableDatabase
        } catch (e: IllegalStateException) {
            failed = true
            assertTrue(e.message.orEmpty().contains("listener_sessions"))
        } finally {
            db.close()
        }
        assertTrue("Room should reject a migration that doesn't match the entity", failed)
    }
}
