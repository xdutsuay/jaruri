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
        sql.execSQL("DROP TABLE listener_sessions")
        sql.version = 11
        db.close()
    }

    @Test
    fun migrate11To12_createsListenerTable_andKeepsExistingData() = runBlocking {
        createV11WithExistingData()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_11_12)
            .allowMainThreadQueries()
            .build()
        val sql = db.openHelper.writableDatabase // triggers migrate + validation
        assertEquals(12, sql.version)

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
            .addMigrations(broken)
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
