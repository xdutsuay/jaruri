package com.example.moneymanager.listener

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.moneymanager.R
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.listener.transcribe.TranscriptionCancelReceiver
import kotlinx.coroutines.runBlocking
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
class ListenerV3SmallTest {
    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
    }

    private fun newSession(title: String = "S", transcript: String = ListenerSessionEntity.TRANSCRIPT_NONE): ListenerSessionEntity = runBlocking {
        val repo = ListenerRepository(ctx)
        val row = repo.create(title, System.currentTimeMillis())
        File(repo.storage.sessionDir(row.dirName), "seg-001.m4a").writeBytes(ByteArray(1000))
        repo.finish(row.id, row.startedAt + 1000, ListenerStopReason.USER)
        AppDatabase.getDatabase(ctx).transcriptDao().setState(row.id, transcript, null)
        repo.get(row.id)!!
    }

    @Test
    fun partsAndMarksUseRealPlurals() {
        val r = ctx.resources
        assertEquals("d · 1 MB · 1 part", r.getQuantityString(R.plurals.listener_item_sub, 1, "d", "1 MB", 1))
        assertEquals("d · 1 MB · 3 parts", r.getQuantityString(R.plurals.listener_item_sub, 3, "d", "1 MB", 3))
        assertEquals("Transcription paused (0 of 1 part done)", r.getQuantityString(R.plurals.listener_tr_state_paused, 1, 0, 1))
        assertEquals("1 mark", r.getQuantityString(R.plurals.listener_marks, 1, 1))
        assertEquals("2 marks", r.getQuantityString(R.plurals.listener_marks, 2, 2))
    }

    @Test
    fun notificationCancel_recordsUserCancel_onEveryApiLevel() {
        val row = newSession(transcript = ListenerSessionEntity.TRANSCRIPT_RUNNING)
        val receiver = TranscriptionCancelReceiver()
        receiver.onReceive(ctx, TranscriptionCancelReceiver.intent(ctx, row.id))
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end &&
            runBlocking { AppDatabase.getDatabase(ctx).listenerSessionDao().getById(row.id)!!.transcriptState } != ListenerSessionEntity.TRANSCRIPT_CANCELLED
        ) Thread.sleep(20)
        assertEquals(
            ListenerSessionEntity.TRANSCRIPT_CANCELLED,
            runBlocking { AppDatabase.getDatabase(ctx).listenerSessionDao().getById(row.id)!!.transcriptState }
        )
        // Ignores foreign actions.
        receiver.onReceive(ctx, Intent("x"))
    }

    @Test
    fun deleteAudioKeepsTranscriptNotesAndMarkers_butNotWhileTranscribing() = runBlocking {
        val repo = ListenerRepository(ctx)
        val busy = newSession(transcript = ListenerSessionEntity.TRANSCRIPT_QUEUED)
        assertTrue(!repo.deleteAudioKeepTranscript(busy.id, 5))

        val row = newSession(transcript = ListenerSessionEntity.TRANSCRIPT_DONE)
        repo.setNotes(row.id, "  decisions: ship v3  ")
        assertEquals(1, repo.addMarker(row.id, 1234, 1))
        assertTrue(repo.deleteAudioKeepTranscript(row.id, 99))
        val after = repo.get(row.id)!!
        assertTrue(!after.hasAudio)
        assertEquals(0L, after.bytes)
        assertEquals("decisions: ship v3", after.userNotes)
        assertTrue(!repo.storage.sessionDir(row.dirName).exists())
        assertEquals(1, AppDatabase.getDatabase(ctx).listenerMarkerDao().count(row.id))

        assertTrue(repo.delete(row.id))
        assertEquals(0, AppDatabase.getDatabase(ctx).listenerMarkerDao().count(row.id))
    }
}
