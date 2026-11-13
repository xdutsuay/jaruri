package com.example.moneymanager.ui

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.moneymanager.R
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.listener.ListenerRepository
import com.example.moneymanager.listener.ListenerSessionEntity
import com.example.moneymanager.listener.ListenerStopReason
import com.example.moneymanager.listener.transcribe.TranscriptSegmentEntity
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Search → hit → session screen at that timestamp; session screen shows transcript + notes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ListenerTranscriptUiTest {
    private lateinit var ctx: Context
    private lateinit var repo: ListenerRepository
    private var sessionId = 0L
    // The Room singleton outlives a test in this JVM, so give each test its own searchable word.
    private val word = "zeb" + (1..8).map { ('a'..'z').random() }.joinToString("")

    @Before
    fun setUp() = runBlocking {
        ctx = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        repo = ListenerRepository(ctx)
        val s = repo.create("Budget sync", System.currentTimeMillis())
        repo.finish(s.id, s.startedAt + 120_000, ListenerStopReason.USER)
        sessionId = s.id
        val dao = AppDatabase.getDatabase(ctx).transcriptDao()
        dao.commitChunk(s.id, 0, listOf(
            TranscriptSegmentEntity(sessionId = s.id, chunkIndex = 0, startMs = 1_000, endMs = 3_000, text = "welcome to the $word review"),
            TranscriptSegmentEntity(sessionId = s.id, chunkIndex = 0, startMs = 65_000, endMs = 70_000, text = "we need to send the report by friday")
        ))
        dao.setState(s.id, ListenerSessionEntity.TRANSCRIPT_DONE, null)
    }

    /** Room/DataStore work lands on background threads; pump the main looper until [check] holds. */
    private fun waitFor(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!check() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(20)
        }
        assertTrue("timed out", check())
    }

    @Test
    fun searchHitOpensSessionAtTimestamp() {
        val nav = TestNavHostController(ctx)
        val scenario = launchFragmentInContainer<ListenerFragment>(themeResId = R.style.Theme_MoneyManager)
        scenario.onFragment {
            nav.setGraph(R.navigation.nav_graph)
            nav.setCurrentDestination(R.id.nav_listener)
            Navigation.setViewNavController(it.requireView(), nav)
            it.requireView().findViewById<TextInputEditText>(R.id.et_listener_search).setText(word.take(6))
        }
        var hits: RecyclerView? = null
        scenario.onFragment { hits = it.requireView().findViewById(R.id.rv_listener_hits) }
        waitFor {
            hits!!.visibility == View.VISIBLE && hits!!.adapter!!.itemCount == 1
        }
        layOut(hits!!)
        scenario.onFragment {
            assertEquals(View.GONE, it.requireView().findViewById<View>(R.id.rv_listener_sessions).visibility)
            val row = hits!!.getChildAt(0)
            assertEquals("Budget sync", row.findViewById<TextView>(R.id.tv_seg_header).text.toString())
            assertTrue(row.findViewById<TextView>(R.id.tv_seg_time).text.toString().endsWith("00:00:01"))
            row.performClick()
        }
        assertEquals(R.id.nav_listener_session, nav.currentDestination?.id)
        val args = nav.backStack.last().arguments!!
        assertEquals(sessionId, args.getLong("sessionId"))
        assertEquals(1_000L, args.getLong("startMs"))
    }

    @Test
    fun clearingSearchBringsBackSessions() {
        val scenario = launchFragmentInContainer<ListenerFragment>(themeResId = R.style.Theme_MoneyManager)
        scenario.onFragment { it.requireView().findViewById<TextInputEditText>(R.id.et_listener_search).setText("nomatchword") }
        var view: View? = null
        scenario.onFragment { view = it.requireView() }
        waitFor { view!!.findViewById<View>(R.id.rv_listener_hits).visibility == View.VISIBLE }
        assertEquals(View.VISIBLE, view!!.findViewById<View>(R.id.tv_listener_empty).visibility)
        scenario.onFragment { it.requireView().findViewById<TextInputEditText>(R.id.et_listener_search).setText("") }
        waitFor { view!!.findViewById<View>(R.id.rv_listener_sessions).visibility == View.VISIBLE }
        assertEquals(View.GONE, view!!.findViewById<View>(R.id.rv_listener_hits).visibility)
    }

    @Test
    fun sessionScreenShowsTranscriptNotesAndExport() {
        val scenario = launchFragmentInContainer<ListenerSessionFragment>(
            fragmentArgs = bundleOf("sessionId" to sessionId, "startMs" to -1L),
            themeResId = R.style.Theme_MoneyManager
        )
        var view: View? = null
        scenario.onFragment { view = it.requireView() }
        val list = view!!.findViewById<RecyclerView>(R.id.rv_ls_segments)
        // your-notes card + highlights card + 2 lines
        waitFor { list.adapter!!.itemCount == 4 }
        layOut(list)
        val v = view!!
        assertEquals("Budget sync", v.findViewById<TextView>(R.id.tv_ls_title).text.toString())
        assertEquals(ctx.getString(R.string.listener_tr_state_done), v.findViewById<TextView>(R.id.tv_ls_tr_status).text.toString())
        assertEquals(ctx.getString(R.string.listener_tr_again), v.findViewById<Button>(R.id.btn_ls_transcribe).text.toString())
        assertTrue(v.findViewById<Button>(R.id.btn_ls_export).isEnabled)

        val notes = list.getChildAt(1)
        val notesText = (notes as android.view.ViewGroup).let { collectText(it) }
        assertTrue(notesText, notesText.contains(ctx.getString(R.string.listener_notes_title)))
        assertTrue(notesText, notesText.contains("00:01:05  we need to send the report by friday"))
        assertEquals("00:00:01", list.getChildAt(2).findViewById<TextView>(R.id.tv_seg_time).text.toString())
    }

    @Test
    fun cancelledSessionOffersResume() = runBlocking {
        AppDatabase.getDatabase(ctx).transcriptDao().setState(sessionId, ListenerSessionEntity.TRANSCRIPT_CANCELLED, null)
        val scenario = launchFragmentInContainer<ListenerSessionFragment>(
            fragmentArgs = bundleOf("sessionId" to sessionId),
            themeResId = R.style.Theme_MoneyManager
        )
        var view: View? = null
        scenario.onFragment { view = it.requireView() }
        val btn = view!!.findViewById<Button>(R.id.btn_ls_transcribe)
        waitFor { btn.text.toString() == ctx.getString(R.string.listener_tr_resume) }
    }

    /** Robolectric's small default screen can leave the list 0px tall; give it room so rows bind. */
    private fun layOut(list: RecyclerView) {
        list.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY)
        )
        list.layout(0, 0, 1080, 4000)
    }

    private fun collectText(g: android.view.ViewGroup): String = buildString {
        for (i in 0 until g.childCount) {
            when (val c = g.getChildAt(i)) {
                is android.view.ViewGroup -> append(collectText(c))
                is TextView -> append(c.text).append('\n')
            }
        }
    }
}
