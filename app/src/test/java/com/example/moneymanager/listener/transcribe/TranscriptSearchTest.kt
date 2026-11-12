package com.example.moneymanager.listener.transcribe

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.listener.ListenerRepository
import com.example.moneymanager.listener.ListenerStorage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TranscriptSearchTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ListenerRepository
    private lateinit var root: File

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        root = File(ctx.cacheDir, "search-${System.nanoTime()}")
        repo = ListenerRepository(db.listenerSessionDao(), ListenerStorage(root), db.transcriptDao())
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private fun seg(session: Long, start: Long, text: String) =
        TranscriptSegmentEntity(sessionId = session, chunkIndex = 0, startMs = start, endMs = start + 1000, text = text)

    @Test
    fun prefixSearchAcrossSessions_newestSessionFirst_thenByTime() = runBlocking {
        val a = repo.create("Monday sync", 1_000)
        val b = repo.create("Vendor call", 2_000)
        db.transcriptDao().insertAll(
            listOf(
                seg(a.id, 5_000, "we need the budget by friday"),
                seg(a.id, 1_000, "budget numbers look fine"),
                seg(b.id, 9_000, "vendor wants a bigger budget"),
                seg(b.id, 3_000, "unrelated small talk")
            )
        )
        val hits = db.transcriptDao().search(FtsQuery.build("budg")!!)
        assertEquals(3, hits.size)
        assertEquals("Vendor call", hits[0].sessionTitle)
        assertEquals(listOf(1_000L, 5_000L), hits.drop(1).map { it.startMs })

        val both = db.transcriptDao().search(FtsQuery.build("budget friday")!!)
        assertEquals(1, both.size)
        assertEquals(5_000L, both[0].startMs)
    }

    @Test
    fun deletingSessionRemovesItsSegmentsFromSearch() = runBlocking {
        val a = repo.create("Keep", 1_000)
        val b = repo.create("Drop", 2_000)
        db.transcriptDao().insertAll(listOf(seg(a.id, 0, "alpha launch"), seg(b.id, 0, "alpha retro")))
        assertEquals(2, db.transcriptDao().search("alpha*").size)
        assertTrue(repo.delete(b.id))
        val hits = db.transcriptDao().search("alpha*")
        assertEquals(listOf("Keep"), hits.map { it.sessionTitle })
    }

    @Test
    fun resumeCleanupDropsOnlyLaterChunks() = runBlocking {
        val a = repo.create("x", 1)
        db.transcriptDao().insertAll(
            listOf(
                TranscriptSegmentEntity(sessionId = a.id, chunkIndex = 0, startMs = 0, endMs = 1, text = "zero"),
                TranscriptSegmentEntity(sessionId = a.id, chunkIndex = 1, startMs = 2, endMs = 3, text = "one"),
                TranscriptSegmentEntity(sessionId = a.id, chunkIndex = 2, startMs = 4, endMs = 5, text = "two")
            )
        )
        db.transcriptDao().deleteFromChunk(a.id, 1)
        assertEquals(listOf("zero"), db.transcriptDao().forSession(a.id).map { it.text })
        assertEquals(0, db.transcriptDao().search("one*").size)
    }
}
