package com.example.moneymanager.listener.transcribe

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.listener.ListenerPolicy
import com.example.moneymanager.listener.ListenerRepository
import com.example.moneymanager.listener.ListenerSessionEntity
import com.example.moneymanager.listener.ListenerStopReason
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
import java.io.IOException

/**
 * Transcription state machine with a fake engine and decoder. Each fake chunk
 * file holds its length in samples at 1 kHz; the fake engine emits one
 * utterance per second of audio: "c<chunk>s<second>".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TranscriptionRunnerTest {
    private lateinit var db: AppDatabase
    private lateinit var storage: ListenerStorage
    private lateinit var repo: ListenerRepository
    private lateinit var root: File

    private var failOnChunk: String? = null
    private var cancelAfterSamples: Pair<String, Int>? = null
    private var cancelled = false
    private var engineAvailable = true
    var enginesClosed = 0

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        root = File(ctx.cacheDir, "runner-${System.nanoTime()}")
        storage = ListenerStorage(root)
        repo = ListenerRepository(db.listenerSessionDao(), storage, db.transcriptDao())
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private inner class FakeDecoder : PcmDecoder {
        override fun open(file: File): PcmStream = object : PcmStream {
            val total = file.readText().trim().toInt()
            var pos = 0
            override val sampleRate = 1000
            override fun read(buffer: ShortArray): Int {
                if (pos >= total) return -1
                val c = cancelAfterSamples
                if (c != null && c.first == file.name && pos >= c.second) cancelled = true
                val n = minOf(250, buffer.size, total - pos)
                pos += n
                return n
            }
            override fun close() {}
        }
    }

    private inner class FakeEngine : SpeechEngine {
        override val label = "Fake"
        var currentFile = ""
        override fun newStream(sampleRate: Int): SpeechStream = object : SpeechStream {
            var fed = 0
            var emitted = 0
            val chunk = currentFile
            override fun accept(pcm: ShortArray, length: Int): List<RawSegment> {
                if (failOnChunk == chunk) throw IOException("decoder exploded")
                fed += length
                val out = mutableListOf<RawSegment>()
                while ((emitted + 1) * sampleRate <= fed) {
                    out += RawSegment(emitted * 1000L, emitted * 1000L + 900, "c${chunk.take(7)}s$emitted")
                    emitted++
                }
                return out
            }
            override fun finish() = listOf(RawSegment(fed.toLong(), fed.toLong(), "  ")) // blank → dropped
            override fun close() {}
        }
        override fun close() { enginesClosed++ }
    }

    private fun runner(engine: FakeEngine = FakeEngine()) = TranscriptionRunner(
        sessions = db.listenerSessionDao(),
        transcripts = db.transcriptDao(),
        storage = storage,
        engineFactory = {
            if (!engineAvailable) throw EngineUnavailableException("No speech model imported.")
            engine
        },
        decoder = object : PcmDecoder {
            val inner = FakeDecoder()
            override fun open(file: File): PcmStream {
                engine.currentFile = file.name
                return inner.open(file)
            }
        },
        durationsOf = { files -> files.map { it.readText().trim().toLong() } } // samples at 1 kHz == ms
    )

    /** Session with chunks of the given lengths (ms), finished by the user. */
    private fun session(vararg chunkMs: Int): ListenerSessionEntity = runBlocking {
        val s = repo.create("t", 1_000)
        chunkMs.forEachIndexed { i, ms ->
            File(storage.sessionDir(s.dirName), ListenerPolicy.segmentFileName(i + 1)).writeText(ms.toString())
        }
        repo.finish(s.id, 2_000, ListenerStopReason.USER)
        db.listenerSessionDao().getById(s.id)!!
    }

    private fun state(id: Long) = runBlocking { db.listenerSessionDao().getById(id)!! }
    private fun texts(id: Long) = runBlocking { db.transcriptDao().forSession(id).map { it.text } }

    @Test
    fun happyPath_stampsSegmentsAcrossChunks_andReportsProgress() = runBlocking {
        val s = session(3000, 2000)
        val progress = mutableListOf<TranscriptionRunner.Progress>()
        val out = runner().run(s.id, { cancelled }) { progress += it }
        assertEquals(TranscriptionRunner.Outcome.Done, out)
        val segs = db.transcriptDao().forSession(s.id)
        assertEquals(listOf(0L, 1000L, 2000L, 3000L, 4000L), segs.map { it.startMs })
        assertEquals(listOf(0, 0, 0, 1, 1), segs.map { it.chunkIndex })
        assertEquals(3900L, segs[3].endMs)
        val row = state(s.id)
        assertEquals(ListenerSessionEntity.TRANSCRIPT_DONE, row.transcriptState)
        assertEquals(2, row.transcribedChunks)
        assertEquals(1f, progress.last().fraction, 0.0001f)
        assertTrue(progress.zipWithNext().all { (a, b) -> b.fraction >= a.fraction })
        assertEquals(1, enginesClosed)
    }

    @Test
    fun cancelMidChunk_keepsFinishedChunks_thenResumeCompletesWithoutDuplicates() = runBlocking {
        val s = session(2000, 2000, 2000)
        cancelAfterSamples = "seg-002.m4a" to 1000
        assertEquals(TranscriptionRunner.Outcome.Cancelled, runner().run(s.id, { cancelled }))
        assertEquals(1, state(s.id).transcribedChunks)
        assertEquals(2, texts(s.id).size) // only chunk 0 committed

        cancelled = false
        cancelAfterSamples = null
        assertEquals(TranscriptionRunner.Outcome.Done, runner().run(s.id, { cancelled }))
        val segs = db.transcriptDao().forSession(s.id)
        assertEquals(6, segs.size)
        assertEquals(segs.size, segs.map { it.startMs }.toSet().size)
        assertEquals(3, state(s.id).transcribedChunks)
    }

    @Test
    fun engineError_marksFailed_keepsProgress_andRetryResumes() = runBlocking {
        val s = session(1000, 1000, 1000)
        failOnChunk = "seg-002.m4a"
        val out = runner().run(s.id, { false })
        assertTrue(out is TranscriptionRunner.Outcome.Failed)
        val row = state(s.id)
        assertEquals(ListenerSessionEntity.TRANSCRIPT_FAILED, row.transcriptState)
        assertEquals("decoder exploded", row.transcriptError)
        assertEquals(1, row.transcribedChunks)

        failOnChunk = null
        assertEquals(TranscriptionRunner.Outcome.Done, runner().run(s.id, { false }))
        assertEquals(3, texts(s.id).size)
        assertEquals(null, state(s.id).transcriptError)
    }

    @Test
    fun noModel_failsWithReason_andWritesNothing() = runBlocking {
        val s = session(1000)
        engineAvailable = false
        val out = runner().run(s.id, { false })
        assertEquals(TranscriptionRunner.Outcome.Failed("No speech model imported."), out)
        assertEquals(ListenerSessionEntity.TRANSCRIPT_FAILED, state(s.id).transcriptState)
        assertTrue(texts(s.id).isEmpty())
    }

    @Test
    fun refusesSessionThatIsStillRecording() = runBlocking {
        val s = repo.create("live", 5)
        val out = runner().run(s.id, { false })
        assertTrue(out is TranscriptionRunner.Outcome.Failed)
        assertEquals(ListenerSessionEntity.STATE_RECORDING, state(s.id).state)
    }

    @Test
    fun userCancelBeforeStart_isRespected() = runBlocking {
        val s = session(1000)
        db.transcriptDao().setState(s.id, ListenerSessionEntity.TRANSCRIPT_CANCELLED, null)
        assertEquals(TranscriptionRunner.Outcome.Cancelled, runner().run(s.id, { false }))
        assertEquals(ListenerSessionEntity.TRANSCRIPT_CANCELLED, state(s.id).transcriptState)
        assertTrue(texts(s.id).isEmpty())
    }

    @Test
    fun segmentsAreClampedInsideTheirChunk() = runBlocking {
        // Durations from metadata can be a little shorter than decoded audio.
        val s = session(2500)
        val r = TranscriptionRunner(
            db.listenerSessionDao(), db.transcriptDao(), storage,
            engineFactory = { FakeEngine() },
            decoder = FakeDecoder(),
            durationsOf = { listOf(1500L) }
        )
        assertEquals(TranscriptionRunner.Outcome.Done, r.run(s.id, { false }))
        assertTrue(db.transcriptDao().forSession(s.id).all { it.startMs <= 1500 && it.endMs <= 1500 })
    }
}
