package com.example.moneymanager.listener.transcribe

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.example.moneymanager.R
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.listener.ListenerPolicy
import com.example.moneymanager.listener.ListenerRepository
import com.example.moneymanager.listener.ListenerSessionEntity
import com.example.moneymanager.listener.ListenerStopReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Real worker + scheduler wiring (no model on the box, so the engine is unavailable). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TranscriptionWorkerTest {
    private lateinit var ctx: Context
    private lateinit var repo: ListenerRepository

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        SttModelStore(ctx).remove()
        repo = ListenerRepository(ctx)
    }

    private fun finishedSession(): ListenerSessionEntity = runBlocking {
        val s = repo.create("Worker test", System.nanoTime())
        File(repo.storage.sessionDir(s.dirName), ListenerPolicy.segmentFileName(1)).writeBytes(ByteArray(10))
        repo.finish(s.id, s.startedAt + 1000, ListenerStopReason.USER)
        repo.get(s.id)!!
    }

    @Test
    fun withoutModel_workerInstallsBundledOne_thenFailsCleanlyOnJvm() = runBlocking {
        val s = finishedSession()
        val worker = TestListenableWorkerBuilder<TranscriptionWorker>(ctx)
            .setInputData(workDataOf(TranscriptionWorker.KEY_SESSION_ID to s.id))
            .build()
        val result = worker.doWork()
        // No libvosk on the JVM, so the run fails either way, with a reason.
        assertEquals(ListenableWorker.Result.failure(), result)
        val row = repo.get(s.id)!!
        assertEquals(ListenerSessionEntity.TRANSCRIPT_FAILED, row.transcriptState)
        val bundled = BundledSttModels.available(ctx)
        if (bundled.isEmpty()) {
            assertEquals(ctx.getString(R.string.listener_stt_no_model), row.transcriptError)
        } else {
            assertEquals(bundled.first().dirName, SttModelStore(ctx).installedName)
            assertTrue(row.transcriptError != ctx.getString(R.string.listener_stt_no_model))
        }
    }

    @Test
    fun workerSkipsSessionTheUserCancelled() = runBlocking {
        val s = finishedSession()
        AppDatabase.getDatabase(ctx).transcriptDao()
            .setState(s.id, ListenerSessionEntity.TRANSCRIPT_CANCELLED, null)
        val worker = TestListenableWorkerBuilder<TranscriptionWorker>(ctx)
            .setInputData(workDataOf(TranscriptionWorker.KEY_SESSION_ID to s.id))
            .build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(ListenerSessionEntity.TRANSCRIPT_CANCELLED, repo.get(s.id)!!.transcriptState)
    }

    @Test
    fun schedulerEnqueueThenCancel_setsStatesAndCancelsUniqueWork() = runBlocking {
        val s = finishedSession()
        TranscriptionScheduler.enqueue(ctx, s.id)
        assertEquals(ListenerSessionEntity.TRANSCRIPT_QUEUED, repo.get(s.id)!!.transcriptState)
        val wm = WorkManager.getInstance(ctx)
        val before = wm.getWorkInfosForUniqueWork(TranscriptionScheduler.workName(s.id)).get()
        assertEquals(1, before.size)

        TranscriptionScheduler.cancel(ctx, s.id)
        assertEquals(ListenerSessionEntity.TRANSCRIPT_CANCELLED, repo.get(s.id)!!.transcriptState)
        val after = wm.getWorkInfosForUniqueWork(TranscriptionScheduler.workName(s.id)).get()
        assertTrue(after.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun restartClearsTranscriptAndProgress() = runBlocking {
        val s = finishedSession()
        val dao = AppDatabase.getDatabase(ctx).transcriptDao()
        dao.commitChunk(s.id, 0, listOf(TranscriptSegmentEntity(sessionId = s.id, chunkIndex = 0, startMs = 0, endMs = 1, text = "old")))
        dao.setState(s.id, ListenerSessionEntity.TRANSCRIPT_DONE, null)
        TranscriptionScheduler.restart(ctx, s.id)
        assertTrue(dao.forSession(s.id).isEmpty())
        val row = repo.get(s.id)!!
        assertEquals(0, row.transcribedChunks)
        assertEquals(ListenerSessionEntity.TRANSCRIPT_QUEUED, row.transcriptState)
    }
}
