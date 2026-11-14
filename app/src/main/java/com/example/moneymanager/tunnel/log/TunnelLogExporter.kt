package com.example.moneymanager.tunnel.log

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** CSV (all detail rows) and a short Markdown report. Pure; unit-tested. */
object TunnelLogExporter {
    fun csv(rows: List<DnsLogEntity>, tz: TimeZone = TimeZone.getDefault()): String = buildString {
        append("time,app,uid,domain,qtype,decision,rule,rcode,latency_ms\n")
        val f = fmt(tz)
        for (r in rows) {
            append(f.format(Date(r.ts))).append(',')
            append(cell(r.pkg ?: "")).append(',')
            append(r.uid).append(',')
            append(cell(r.name)).append(',')
            append(r.qtype).append(',')
            append(r.decision).append(',')
            append(cell(r.ref ?: "")).append(',')
            append(r.rcode).append(',')
            append(r.latencyMs).append('\n')
        }
    }

    fun markdown(
        rows: List<DnsLogEntity>,
        topBlocked: List<DomainCount>,
        now: Long,
        tz: TimeZone = TimeZone.getDefault()
    ): String = buildString {
        val blocked = rows.count { it.decision.startsWith("BLOCKED") }
        append("# Jaruri Tunnel report\n\n")
        append("- Generated: ").append(fmt(tz).format(Date(now))).append('\n')
        append("- Lookups: ").append(rows.size).append(" (blocked ").append(blocked).append(")\n")
        append("- DNS only: no URLs, page contents or byte counts are visible to the tunnel.\n\n")
        append("## Most blocked domains\n\n")
        if (topBlocked.isEmpty()) append("None.\n")
        topBlocked.forEach { append("- `").append(it.domain).append("`: ").append(it.count).append('\n') }
        append("\n## Lookups per app\n\n")
        val perApp = rows.groupBy { it.pkg ?: "(unknown app)" }.mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
        if (perApp.isEmpty()) append("None.\n")
        perApp.forEach { append("- ").append(it.key).append(": ").append(it.value).append('\n') }
    }

    private fun fmt(tz: TimeZone) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = tz }

    /** RFC 4180 quoting; also neutralises spreadsheet formula injection. */
    fun cell(s: String): String {
        val safe = if (s.isNotEmpty() && s[0] in "=+-@") "'$s" else s
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
