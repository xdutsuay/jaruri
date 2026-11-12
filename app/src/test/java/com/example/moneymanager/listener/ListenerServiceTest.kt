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
    private fun waitUntil(timeoutMs: Long = 5_000, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!cond() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
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
        assertEquals(1, n.actions.size)
        assertEquals("Stop", n.actions[0].title.toString())

        val r = row(rec.sessionId)!!
        assertEquals("Design review", r.title)
        assertEquals(ListenerSessionEntity.STATE_RECORDING, r.state)

        sendStop(service)
        waitUntil { row(rec.sessionId)?.state == ListenerSessionEntity.STATE_DONE }
        assertTrue(!ListenerState.isBusy)
        assertTrue(shadowOf(service).isForegroundStopped)
        assertEquals("USER", row(rec.sessionId)!!.notes)
        assertTrue(shadowOf(service).isStoppedBySelf)
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
        waitUntil { row(id)?.state == ListenerSessionEntity.STATE_DONE }
        assertTrue(!ListenerState.isBusy)
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
}
