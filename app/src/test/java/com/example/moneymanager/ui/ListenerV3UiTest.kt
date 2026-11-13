package com.example.moneymanager.ui

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.moneymanager.R
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.listener.ListenerMarkerEntity
import com.example.moneymanager.listener.ListenerRepository
import com.example.moneymanager.listener.ListenerSessionEntity
import com.example.moneymanager.listener.ListenerStopReason
import com.example.moneymanager.listener.transcribe.TranscriptSegmentEntity
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.Duration

/** Listener v3 UI: your notes, marks, line corrections, speed, delete-audio state, search date filter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ListenerV3UiTest {
    private lateinit var ctx: Context
    private lateinit var repo: ListenerRepository
    private var sessionId = 0L
    private val word = "qux" + (1..8).map { ('a'..'z').random() }.joinToString("")

    @Before
    fun setUp() = runBlocking {
        ctx = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        repo = ListenerRepository(ctx)
        // Started 10 days ago: inside "30 days", outside "7 days".
        val s = repo.create("Infra review", System.currentTimeMillis() - 10 * 86_400_000L)
        File(repo.storage.sessionDir(s.dirName), "seg-001.m4a").writeBytes(ByteArray(2048))
        repo.finish(s.id, s.startedAt + 120_000, ListenerStopReason.USER)
        sessionId = s.id
        val dao = AppDatabase.getDatabase(ctx).transcriptDao()
        dao.commitChunk(s.id, 0, listOf(
            TranscriptSegmentEntity(sessionId = s.id, chunkIndex = 0, startMs = 2_000, endMs = 4_000, text = "the $word cluster is down")
        ))
        dao.setState(s.id, ListenerSessionEntity.TRANSCRIPT_DONE, null)
        AppDatabase.getDatabase(ctx).listenerMarkerDao().insert(ListenerMarkerEntity(sessionId = s.id, atMs = 61_000, createdAt = 1))
        Unit
    }

    private fun waitFor(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!check() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(20)
        }
        assertTrue("timed out", check())
    }

    private fun layOut(list: RecyclerView) {
        list.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY)
        )
        list.layout(0, 0, 1080, 4000)
    }

    private fun text(g: View): String = when (g) {
        is android.view.ViewGroup -> (0 until g.childCount).joinToString("\n") { text(g.getChildAt(it)) }
        is TextView -> g.text.toString()
        else -> ""
    }

    private fun session() = runBlocking { repo.get(sessionId)!! }

    @Test
    fun notesMarksAndLineCorrection() {
        val scenario = launchFragmentInContainer<ListenerSessionFragment>(
            fragmentArgs = bundleOf("sessionId" to sessionId, "startMs" to -1L),
            themeResId = R.style.Theme_MoneyManager
        )
        var view: View? = null
        scenario.onFragment { view = it.requireView() }
        val list = view!!.findViewById<RecyclerView>(R.id.rv_ls_segments)
        waitFor { list.adapter!!.itemCount >= 2 && text(run { layOut(list); list.getChildAt(0) }).contains("00:01:01") }
        val header = list.getChildAt(0)
        assertTrue(text(header).contains(ctx.getString(R.string.listener_user_notes_empty)))

        // Notes: tap → dialog → save.
        header.findViewById<TextView>(R.id.tv_user_notes).performClick()
        var dialog = ShadowDialog.getLatestDialog() as AlertDialog
        findEdit(dialog.window!!.decorView)!!.setText("Ask Priya about DNS")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        waitFor { session().userNotes == "Ask Priya about DNS" }

        // Line correction: long-press → dialog → save; FTS follows; "edited" shows.
        layOut(list)
        val line = (0 until list.childCount).map { list.getChildAt(it) }.first { it.findViewById<TextView?>(R.id.tv_seg_text) != null }
        line.performLongClick()
        dialog = ShadowDialog.getLatestDialog() as AlertDialog
        findEdit(dialog.window!!.decorView)!!.setText("the staging cluster is down")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        waitFor {
            layOut(list)
            (0 until list.childCount).map { list.getChildAt(it) }.any {
                it.findViewById<TextView?>(R.id.tv_seg_time)?.text?.contains(ctx.getString(R.string.listener_seg_edited)) == true
            }
        }
        val segs = runBlocking { AppDatabase.getDatabase(ctx).transcriptDao().forSession(sessionId) }
        assertEquals("the staging cluster is down", segs.single().text)
        assertTrue(segs.single().edited)

        // Speed cycles.
        val speed = view!!.findViewById<Button>(R.id.btn_ls_speed)
        assertEquals("1×", speed.text.toString())
        speed.performClick()
        assertEquals("1.25×", speed.text.toString())
    }

    @Test
    fun audioDeletedSession_keepsTranscript_butCannotPlayOrRetranscribe() {
        assertTrue(runBlocking { repo.deleteAudioKeepTranscript(sessionId, System.currentTimeMillis()) })
        val scenario = launchFragmentInContainer<ListenerSessionFragment>(
            fragmentArgs = bundleOf("sessionId" to sessionId),
            themeResId = R.style.Theme_MoneyManager
        )
        var view: View? = null
        scenario.onFragment { view = it.requireView() }
        val v = view!!
        waitFor { v.findViewById<TextView>(R.id.tv_ls_meta).text.contains(ctx.getString(R.string.listener_state_audio_deleted)) }
        assertFalse(v.findViewById<View>(R.id.btn_ls_play).isEnabled)
        assertFalse(v.findViewById<Button>(R.id.btn_ls_transcribe).isEnabled)
        assertTrue(v.findViewById<Button>(R.id.btn_ls_export).isEnabled)
    }

    @Test
    fun searchDateFilter_hidesOlderSessions() {
        val scenario = launchFragmentInContainer<ListenerFragment>(themeResId = R.style.Theme_MoneyManager)
        var view: View? = null
        scenario.onFragment {
            view = it.requireView()
            it.requireView().findViewById<TextInputEditText>(R.id.et_listener_search).setText(word.take(7))
        }
        val hits = view!!.findViewById<RecyclerView>(R.id.rv_listener_hits)
        waitFor { hits.visibility == View.VISIBLE && hits.adapter!!.itemCount == 1 }
        scenario.onFragment { it.requireView().findViewById<Chip>(R.id.chip_range_week).isChecked = true }
        waitFor { hits.adapter!!.itemCount == 0 }
        scenario.onFragment { it.requireView().findViewById<Chip>(R.id.chip_range_month).isChecked = true }
        waitFor { hits.adapter!!.itemCount == 1 }
        // Mark is only offered while recording.
        assertEquals(View.GONE, view!!.findViewById<View>(R.id.btn_listener_mark).visibility)
    }

    private fun findEdit(v: View): EditText? = when (v) {
        is EditText -> v
        is android.view.ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { findEdit(v.getChildAt(it)) }
        else -> null
    }
}
