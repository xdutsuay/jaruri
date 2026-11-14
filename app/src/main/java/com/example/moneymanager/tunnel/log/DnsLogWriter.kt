package com.example.moneymanager.tunnel.log

import androidx.room.withTransaction
import com.example.moneymanager.tunnel.DnsDecision
import com.example.moneymanager.tunnel.DnsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.TimeZone

/**
 * Batches DNS events (flush every [flushMs] or [batchSize] rows) so a busy
 * phone isn't writing to disk on every lookup. Drops events rather than
 * blocking the tunnel if the queue is full.
 */
class DnsLogWriter(
    private val db: TunnelLogDatabase,
    private val packageOf: (Int) -> String?,
    scope: CoroutineScope,
    private val batchSize: Int = 200,
    private val flushMs: Long = 1_000,
    private val timeZone: () -> TimeZone = { TimeZone.getDefault() }
) {
    private val queue = Channel<DnsEvent>(capacity = 5_000)

    init {
        scope.launch {
            val batch = ArrayList<DnsEvent>(batchSize)
            while (true) {
                val first = queue.receiveCatching().getOrNull() ?: break
                batch += first
                withTimeoutOrNull(flushMs) {
                    while (batch.size < batchSize) batch += queue.receive()
                }
                write(batch)
                batch.clear()
            }
            if (batch.isNotEmpty()) write(batch)
        }
    }

    /** Never blocks; returns false if the event was dropped. */
    fun offer(e: DnsEvent): Boolean = queue.trySend(e).isSuccess

    fun close() {
        queue.close()
    }

    suspend fun write(events: List<DnsEvent>) {
        if (events.isEmpty()) return
        val rows = events.map {
            DnsLogEntity(
                ts = it.timeMs, uid = it.uid, pkg = if (it.uid >= 0) packageOf(it.uid) else null,
                name = it.name, qtype = it.qtype, decision = it.decision.name, ref = it.ref,
                rcode = it.rcode, latencyMs = it.latencyMs
            )
        }
        val daily = rows.groupBy { Triple(dayOf(it.ts, timeZone()), it.pkg ?: "", it.name) }
        val dao = db.dao()
        db.withTransaction {
            dao.insertAll(rows)
            for ((key, list) in daily) {
                val blocked = list.count { it.decision == DnsDecision.BLOCKED_APP.name || it.decision == DnsDecision.BLOCKED_DOMAIN.name }
                dao.insertDailyIgnore(DnsDailyEntity(key.first, key.second, key.third, 0, 0))
                dao.bumpDaily(key.first, key.second, key.third, list.size - blocked, blocked)
            }
        }
    }

    companion object {
        fun dayOf(ts: Long, tz: TimeZone): Long = Math.floorDiv(ts + tz.getOffset(ts), 86_400_000L)
    }
}

/** 7 days of detail (max 100k rows) and 90 days of daily counts. */
object TunnelRetention {
    const val DETAIL_DAYS = 7
    const val DAILY_DAYS = 90
    const val MAX_ROWS = 100_000

    suspend fun purge(dao: DnsLogDao, now: Long, tz: TimeZone = TimeZone.getDefault()) {
        dao.deleteBefore(now - DETAIL_DAYS * 86_400_000L)
        dao.capRows(MAX_ROWS)
        dao.deleteDailyBefore(DnsLogWriter.dayOf(now, tz) - DAILY_DAYS)
    }
}
