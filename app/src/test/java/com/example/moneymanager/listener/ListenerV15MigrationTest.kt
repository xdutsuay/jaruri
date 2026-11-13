package com.example.moneymanager.listener

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.listener.transcribe.FtsQuery
import com.example.moneymanager.listener.transcribe.TranscriptSegmentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A real v14 `listener_sessions` (no v15 columns, no markers table) → MIGRATION_14_15 → Room schema validation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ListenerV15MigrationTest {
    private val dbName = "listener-v15-migration-test.db"
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

    @Test
    fun migrate14To15_addsMarkersNotesAndEditFlag_keepsData() = runBlocking {
        Room.databaseBuilder(context, AppDatabase::class.java, dbName).allowMainThreadQueries().build().apply {
            val sql = openHelper.writableDatabase
            sql.execSQL("DROP TABLE listener_markers")
            sql.execSQL("ALTER TABLE listener_sessions RENAME TO ls_v15")
            sql.execSQL(
                """
                CREATE TABLE listener_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL,
                    startedAt INTEGER NOT NULL, endedAt INTEGER, durationMs INTEGER NOT NULL,
                    segmentCount INTEGER NOT NULL, bytes INTEGER NOT NULL, state TEXT NOT NULL,
                    dirName TEXT NOT NULL, notes TEXT NOT NULL,
                    transcriptState TEXT NOT NULL DEFAULT 'NONE', transcribedChunks INTEGER NOT NULL DEFAULT 0,
                    transcriptError TEXT)
                """.trimIndent()
            )
            sql.execSQL("DROP TABLE ls_v15")
            sql.execSQL(
                "INSERT INTO listener_sessions (title, startedAt, endedAt, durationMs, segmentCount, bytes, state, dirName, notes, " +
                    "transcriptState, transcribedChunks) VALUES ('Standup', 1000, 2000, 1000, 1, 42, 'DONE', 'session-1000', 'USER', 'DONE', 1)"
            )
            sql.version = 14
            close()
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_14_15)
            .allowMainThreadQueries()
            .build()
        assertEquals(15, db.openHelper.writableDatabase.version)
        val row = db.listenerSessionDao().getAll().first().single()
        assertEquals("Standup", row.title)
        assertEquals("", row.userNotes)
        assertNull(row.audioDeletedAt)
        assertTrue(row.hasAudio)

        db.listenerSessionDao().setUserNotes(row.id, "follow up with infra")
        db.listenerSessionDao().markAudioDeleted(row.id, 5)
        val after = db.listenerSessionDao().getById(row.id)!!
        assertEquals("follow up with infra", after.userNotes)
        assertEquals(0L, after.bytes)
        assertTrue(!after.hasAudio)

        val markers = db.listenerMarkerDao()
        markers.insert(ListenerMarkerEntity(sessionId = row.id, atMs = 90_000, createdAt = 1))
        markers.insert(ListenerMarkerEntity(sessionId = row.id, atMs = 30_000, createdAt = 2))
        assertEquals(listOf(30_000L, 90_000L), markers.forSession(row.id).map { it.atMs })

        // Hand edits update the FTS index (Room's content-sync triggers).
        val t = db.transcriptDao()
        t.insertAll(listOf(TranscriptSegmentEntity(sessionId = row.id, chunkIndex = 0, startMs = 0, endMs = 1000, text = "the kubernetes cluster")))
        val seg = t.forSession(row.id).single()
        t.editText(seg.id, "the staging cluster")
        assertTrue(t.forSession(row.id).single().edited)
        assertEquals(1, t.editedCount(row.id))
        assertTrue(t.search(FtsQuery.build("kubernetes")!!).isEmpty())
        assertEquals(1, t.search(FtsQuery.build("staging")!!).size)
        assertTrue("date filter excludes older sessions", t.search(FtsQuery.build("staging")!!, sinceMs = 1001).isEmpty())
        db.close()
    }
}
