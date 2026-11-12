package com.example.moneymanager.listener

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ListenerRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var root: File
    private lateinit var repo: ListenerRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        root = File(ctx.cacheDir, "listener-test-${System.nanoTime()}")
        repo = ListenerRepository(db.listenerSessionDao(), ListenerStorage(root))
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private fun writeSegment(dirName: String, index: Int, bytes: Int, mtime: Long? = null): File {
        val f = File(repo.storage.ensureSessionDir(dirName), ListenerPolicy.segmentFileName(index))
        f.writeBytes(ByteArray(bytes) { 1 })
        if (mtime != null) f.setLastModified(mtime)
        return f
    }

    @Test
    fun createMakesRecordingRowAndFolder() = runBlocking {
        val s = repo.create("Standup", 1_000_000)
        assertTrue(s.id > 0)
        assertEquals(ListenerSessionEntity.STATE_RECORDING, s.state)
        assertTrue(repo.storage.sessionDir(s.dirName).isDirectory)
    }

    @Test
    fun finishByUserIsDoneWithCountsFromDisk() = runBlocking {
        val s = repo.create("t", 1_000_000)
        writeSegment(s.dirName, 1, 100)
        writeSegment(s.dirName, 2, 50)
        File(repo.storage.sessionDir(s.dirName), "seg-003.m4a").writeBytes(ByteArray(0)) // empty: ignored
        repo.finish(s.id, 1_060_000, ListenerStopReason.USER)
        val row = repo.get(s.id)!!
        assertEquals(ListenerSessionEntity.STATE_DONE, row.state)
        assertEquals(60_000L, row.durationMs)
        assertEquals(2, row.segmentCount)
        assertEquals(150L, row.bytes)
        assertEquals("USER", row.notes)
    }

    @Test
    fun autoStopsAreDoneButRecorderErrorIsInterrupted() = runBlocking {
        val a = repo.create("a", 1)
        repo.finish(a.id, 2, ListenerStopReason.MAX_DURATION)
        assertEquals(ListenerSessionEntity.STATE_DONE, repo.get(a.id)!!.state)
        val b = repo.create("b", 3)
        repo.finish(b.id, 4, ListenerStopReason.LOW_STORAGE)
        assertEquals(ListenerSessionEntity.STATE_DONE, repo.get(b.id)!!.state)
        val c = repo.create("c", 5)
        repo.finish(c.id, 6, ListenerStopReason.RECORDER_ERROR)
        assertEquals(ListenerSessionEntity.STATE_INTERRUPTED, repo.get(c.id)!!.state)
    }

    @Test
    fun staleRecordingRowsBecomeInterrupted_activeOneIsLeftAlone() = runBlocking {
        val stale = repo.create("stale", 1_000_000)
        writeSegment(stale.dirName, 1, 10, mtime = 1_120_000)
        val active = repo.create("active", 2_000_000)

        val n = repo.reconcileInterrupted { it.id == active.id }
        assertEquals(1, n)

        val s = repo.get(stale.id)!!
        assertEquals(ListenerSessionEntity.STATE_INTERRUPTED, s.state)
        assertEquals(1_120_000L, s.endedAt)
        assertEquals(120_000L, s.durationMs)
        assertEquals(1, s.segmentCount)
        assertEquals("PROCESS_DIED", s.notes)
        assertTrue("files are kept", repo.storage.segments(stale.dirName).isNotEmpty())

        assertEquals(ListenerSessionEntity.STATE_RECORDING, repo.get(active.id)!!.state)
    }

    @Test
    fun reconcileWithNoActiveServiceClosesEverything() = runBlocking {
        val s = repo.create("x", 10)
        assertEquals(1, repo.reconcileInterrupted { false })
        assertEquals(ListenerSessionEntity.STATE_INTERRUPTED, repo.get(s.id)!!.state)
        assertEquals(0, repo.reconcileInterrupted { false })
    }

    @Test
    fun startingSessionIsOwnedByStartTimeBeforeItHasAnId() = runBlocking {
        val s = repo.create("new", 7_000)
        ListenerState.set(ListenerState.Status.Starting(7_000))
        try {
            assertEquals(0, repo.reconcileInterrupted())
            assertEquals(ListenerSessionEntity.STATE_RECORDING, repo.get(s.id)!!.state)
        } finally {
            ListenerState.set(ListenerState.Status.Idle)
        }
        assertEquals(1, repo.reconcileInterrupted())
    }

    @Test
    fun deleteRemovesRowAndFiles() = runBlocking {
        val s = repo.create("t", 1)
        writeSegment(s.dirName, 1, 10)
        repo.finish(s.id, 2, ListenerStopReason.USER)
        assertTrue(repo.delete(s.id))
        assertNull(repo.get(s.id))
        assertFalse(repo.storage.sessionDir(s.dirName).exists())
        assertTrue(repo.sessions.first().isEmpty())
    }

    @Test
    fun deleteToleratesMissingFolder() = runBlocking {
        val s = repo.create("t", 1)
        repo.storage.sessionDir(s.dirName).deleteRecursively()
        assertTrue(repo.delete(s.id))
        assertNull(repo.get(s.id))
    }

    @Test
    fun renameIgnoresBlank() = runBlocking {
        val s = repo.create("orig", 1)
        repo.rename(s.id, "   ")
        assertEquals("orig", repo.get(s.id)!!.title)
        repo.rename(s.id, "  Board call ")
        assertEquals("Board call", repo.get(s.id)!!.title)
    }
}
