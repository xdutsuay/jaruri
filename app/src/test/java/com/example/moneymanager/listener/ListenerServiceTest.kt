package com.example.moneymanager.listener

import android.Manifest
import android.app.Application
import android.app.Notification
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

/**
 * Service lifecycle on Robolectric (MediaRecorder is shadowed, so no real audio
 * is written; this checks state, notification, chunk timing and limits — the
 * on-device run checks real audio).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33]) // Robolectric 4.10.3 tops out at 33; Android 14 FGS rules need a device
class ListenerServiceTest {

    private lateinit var app: Application
    private var controller: ServiceController<ListenerService>? = null

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        ListenerState.set(ListenerState.Status.Idle)
        ListenerState.consumeError()
        File(app.filesDir, ListenerService.DEBUG_SEGMENT_FILE).delete()
        File(app.filesDir, ListenerService.DEBUG_MAX_FILE).delete()
    }

    @After
    fun tearDown() {
        try { controller?.destroy() } catch (_: Exception) {}
        ListenerState.set(ListenerState.Status.Idle)
        File(app.filesDir, ListenerService.DEBUG_SEGMENT_FILE).delete()
        File(app.filesDir, ListenerService.DEBUG_MAX_FILE).delete()
    }

    private fun start(): ListenerService {
        val intent = Intent(app, ListenerService::class.java)
            .setAction(ListenerService.ACTION_START)
            .putExtra(ListenerService.EXTRA_TITLE, "Design review")
        val c = Robolectric.buildService(ListenerService::class.java, intent).create().startCommand(0, 1)
        controller = c
        waitUntil { ListenerState.isRecording || ListenerState.lastError.value != null }
        return c.get()
    }

    private fun sendStop(service: ListenerService) {
        service.onStartCommand(Intent(app, ListenerService::class.java).setAction(ListenerService.ACTION_STOP), 0, 2)
    }

    /** Coroutines hop to Dispatchers.IO (real threads) and back to the paused main looper. */
    private fun waitUntil(timeoutMs: Long = 20_000, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!cond() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        if (!cond()) fail("Timed out after ${timeoutMs} ms; status=${ListenerState.status.value}")
    }

    private fun row(id: Long) = runBlocking {
        AppDatabase.getDatabase(app).listenerSessionDao().getById(id)
    }

    @Test
    fun withoutMicPermission_doesNotRecord_andReportsWhy() {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        start()
        assertTrue(!ListenerState.isBusy)
        assertNotNull(ListenerState.lastError.value)
        assertTrue(ListenerState.lastError.value!!.contains("Microphone"))
    }

    @Test
    fun startGoesForegroundWithStopAction_andStopFinishesRow() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val service = start()
        val rec = ListenerState.status.value as ListenerState.Status.Recording
        assertEquals(1, rec.segmentIndex)

        val n: Notification? = shadowOf(service).lastForegroundNotification
        assertNotNull("must be a foreground service", n)
        assertTrue((n!!.flags and Notification.FLAG_ONGOING_EVENT) != 0)
        assertEquals(2, n.actions.size)
        assertEquals("Stop", n.actions[0].title.toString())
        assertEquals("Mark", n.actions[1].title.toString())

        val r = row(rec.sessionId)!!
        assertEquals("Design review", r.title)
        assertEquals(ListenerSessionEntity.STATE_RECORDING, r.state)

        sendStop(service)
        // stopSelf runs after the row is written, so wait for that too.
        waitUntil {
            !ListenerState.isBusy && shadowOf(service).isStoppedBySelf &&
                row(rec.sessionId)?.state == ListenerSessionEntity.STATE_DONE
        }
        assertTrue(shadowOf(service).isForegroundStopped)
        assertEquals("USER", row(rec.sessionId)!!.notes)
    }

    @Test
    fun secondStartWhileRecordingIsIgnored() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val service = start()
        val first = (ListenerState.status.value as ListenerState.Status.Recording).sessionId
        service.onStartCommand(Intent(app, ListenerService::class.java).setAction(ListenerService.ACTION_START), 0, 3)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(first, (ListenerState.status.value as ListenerState.Status.Recording).sessionId)
        sendStop(service)
        waitUntil { !ListenerState.isBusy }
    }

    @Test
    fun debugKnob_rollsChunks() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        File(app.filesDir, ListenerService.DEBUG_SEGMENT_FILE).writeText("30000")
        val service = start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
        assertEquals(2, (ListenerState.status.value as ListenerState.Status.Recording).segmentIndex)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertEquals(3, (ListenerState.status.value as ListenerState.Status.Recording).segmentIndex)
        sendStop(service)
        waitUntil { !ListenerState.isBusy }
    }

    @Test
    fun withoutKnob_chunkIsFifteenMinutes() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val service = start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(14))
        assertEquals(1, (ListenerState.status.value as ListenerState.Status.Recording).segmentIndex)
        sendStop(service)
        waitUntil { !ListenerState.isBusy }
    }

    @Test
    fun safetyLimitStopsAndMarksRow() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        File(app.filesDir, ListenerService.DEBUG_MAX_FILE).writeText("20000")
        start()
        val id = (ListenerState.status.value as ListenerState.Status.Recording).sessionId
        // The watchdog compares wall-clock time, so let real time pass, then tick the looper.
        Thread.sleep(20_500)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16))
        waitUntil { !ListenerState.isBusy && row(id)?.state == ListenerSessionEntity.STATE_DONE }
        assertEquals("MAX_DURATION", row(id)!!.notes)
    }

    @Test
    fun stopWithNothingRunningJustStops() {
        val c = Robolectric.buildService(
            ListenerService::class.java,
            Intent(app, ListenerService::class.java).setAction(ListenerService.ACTION_STOP)
        ).create().startCommand(0, 1)
        controller = c
        assertTrue(shadowOf(c.get()).isStoppedBySelf)
        assertNull(ListenerState.activeSessionIdForTest())
    }

    @Test
    fun startDuringStopWindow_isQueued_andStartsAFreshSession() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val service = start()
        val first = (ListenerState.status.value as ListenerState.Status.Recording).sessionId
        // Stop, then Start before the stop coroutine has closed the row (main looper not idled).
        sendStop(service)
        service.onStartCommand(
            Intent(app, ListenerService::class.java).setAction(ListenerService.ACTION_START)
                .putExtra(ListenerService.EXTRA_TITLE, "Second"), 0, 3
        )
        waitUntil { (ListenerState.status.value as? ListenerState.Status.Recording)?.sessionId?.let { it != first } == true }
        val second = (ListenerState.status.value as ListenerState.Status.Recording).sessionId
        assertTrue(second != first)
        assertEquals(ListenerSessionEntity.STATE_DONE, row(first)!!.state)
        assertEquals("Second", row(second)!!.title)
        assertTrue("still foreground for the new session", !shadowOf(service).isStoppedBySelf)
        sendStop(service)
        waitUntil { shadowOf(service).isStoppedBySelf && row(second)?.state == ListenerSessionEntity.STATE_DONE }
    }

    @Test
    fun markSavesBookmarksAtSessionTime_andCountsThem() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val service = start()
        val id = (ListenerState.status.value as ListenerState.Status.Recording).sessionId
        val mark = Intent(app, ListenerService::class.java).setAction(ListenerService.ACTION_MARK)
        service.onStartCommand(mark, 0, 4)
        waitUntil { ListenerState.markCount.value == 1 }
        service.onStartCommand(mark, 0, 5)
        waitUntil { ListenerState.markCount.value == 2 }
        val markers = runBlocking { AppDatabase.getDatabase(app).listenerMarkerDao().forSession(id) }
        assertEquals(2, markers.size)
        assertTrue(markers.all { it.atMs in 0..60_000 })
        sendStop(service)
        waitUntil { !ListenerState.isBusy && row(id)?.state == ListenerSessionEntity.STATE_DONE }
    }

    @Test
    fun markWithNothingRecording_doesNothing_andStops() {
        val c = Robolectric.buildService(
            ListenerService::class.java,
            Intent(app, ListenerService::class.java).setAction(ListenerService.ACTION_MARK)
        ).create().startCommand(0, 1)
        controller = c
        assertTrue(shadowOf(c.get()).isStoppedBySelf)
    }
}
