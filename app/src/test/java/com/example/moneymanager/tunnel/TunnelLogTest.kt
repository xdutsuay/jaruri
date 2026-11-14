package com.example.moneymanager.tunnel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.moneymanager.tunnel.log.DnsLogEntity
import com.example.moneymanager.tunnel.log.DnsLogWriter
import com.example.moneymanager.tunnel.log.DomainCount
import com.example.moneymanager.tunnel.log.TunnelLogDatabase
import com.example.moneymanager.tunnel.log.TunnelLogExporter
import com.example.moneymanager.tunnel.log.TunnelRetention
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TunnelLogTest {
    private lateinit var db: TunnelLogDatabase
    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private val day0 = 1_788_000_000_000L // 2026-08-29 16:10 IST

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TunnelLogDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun ev(ts: Long, uid: Int, name: String, d: DnsDecision) = DnsEvent(ts, uid, name, 1, d, null, 0, 3)

    @Test
    fun writerStoresDetailAndAggregatesDaily() = runBlocking {
        val w = DnsLogWriter(db, { if (it == 10100) "com.chat" else null }, CoroutineScope(Dispatchers.Unconfined), timeZone = { ist })
        w.write(listOf(
            ev(day0, 10100, "ads.x", DnsDecision.BLOCKED_DOMAIN),
            ev(day0 + 1, 10100, "ads.x", DnsDecision.BLOCKED_DOMAIN),
            ev(day0 + 2, 10100, "api.x", DnsDecision.ALLOWED),
            ev(day0 + 3, -1, "api.x", DnsDecision.ALLOWED)
        ))
        w.write(listOf(ev(day0 + 4, 10100, "ads.x", DnsDecision.BLOCKED_APP)))
        w.close()
        val dao = db.dao()
        assertEquals(5, dao.count())
        val day = DnsLogWriter.dayOf(day0, ist)
        assertEquals(2, dao.observeDay(day).first().allowed)
        assertEquals(3, dao.observeDay(day).first().blocked)
        assertEquals(listOf(DomainCount("ads.x", 3)), dao.topBlocked(day, 10))
        val unknown = dao.since(0).filter { it.uid == -1 }
        assertEquals(null, unknown.single().pkg)
        assertEquals(listOf("ads.x"), dao.observeRecent(blockedOnly = true, search = "").first().map { it.name }.distinct())
        assertEquals(1, dao.observeRecent(blockedOnly = false, search = "api", limit = 1).first().size)
    }

    @Test
    fun batchingWriterFlushesOnItsOwn() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val w = DnsLogWriter(db, { null }, scope, batchSize = 3, flushMs = 100)
        repeat(7) { assertTrue(w.offer(ev(day0 + it, -1, "n$it.x", DnsDecision.ALLOWED))) }
        val end = System.currentTimeMillis() + 5_000
        while (db.dao().count() < 7 && System.currentTimeMillis() < end) Thread.sleep(20)
        assertEquals(7, db.dao().count())
        scope.cancel()
    }

    @Test
    fun retentionKeeps7DaysDetail90DaysCountsAndRowCap() = runBlocking {
        val w = DnsLogWriter(db, { null }, CoroutineScope(Dispatchers.Unconfined), timeZone = { ist })
        val dayMs = 86_400_000L
        w.write(listOf(
            ev(day0 - 8 * dayMs, -1, "old.x", DnsDecision.ALLOWED),
            ev(day0 - 91 * dayMs, -1, "ancient.x", DnsDecision.BLOCKED_DOMAIN),
            ev(day0 - 1 * dayMs, -1, "recent.x", DnsDecision.ALLOWED)
        ))
        TunnelRetention.purge(db.dao(), day0, ist)
        assertEquals(listOf("recent.x"), db.dao().since(0).map { it.name })
        val today = DnsLogWriter.dayOf(day0, ist)
        assertEquals("8-day-old counts kept", 1, db.dao().observeDay(today - 8).first().allowed)
        assertEquals("91-day-old counts gone", 0, db.dao().observeDay(today - 91).first().blocked)

        db.dao().clearLog()
        w.write((0 until 50).map { ev(day0 + it, -1, "n$it", DnsDecision.ALLOWED) })
        db.dao().capRows(10)
        val left = db.dao().since(0)
        assertEquals(10, left.size)
        assertEquals("n49", left.last().name)
    }

    @Test
    fun exportCsvQuotesAndNeutralisesFormulas() {
        val rows = listOf(
            DnsLogEntity(1, day0, 10100, "com.chat", "a.b", 1, "BLOCKED_DOMAIN", "list:StevenBlack, unified", 3, 0),
            DnsLogEntity(2, day0 + 1000, -1, null, "=cmd.x", 28, "ALLOWED", null, 0, 12)
        )
        val csv = TunnelLogExporter.csv(rows, ist)
        assertEquals(
            "time,app,uid,domain,qtype,decision,rule,rcode,latency_ms\n" +
                "2026-08-29 16:10:00,com.chat,10100,a.b,1,BLOCKED_DOMAIN,\"list:StevenBlack, unified\",3,0\n" +
                "2026-08-29 16:10:01,,-1,'=cmd.x,28,ALLOWED,,0,12\n",
            csv
        )
        val md = TunnelLogExporter.markdown(rows, listOf(DomainCount("a.b", 1)), day0, ist)
        assertTrue(md, md.contains("- Lookups: 2 (blocked 1)"))
        assertTrue(md, md.contains("- `a.b`: 1"))
        assertTrue(md, md.contains("- (unknown app): 1"))
    }
}
