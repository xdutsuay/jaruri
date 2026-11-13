package com.example.moneymanager.tunnel

import android.content.Context
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.tunnel.data.TunnelAppRuleEntity
import com.example.moneymanager.tunnel.data.TunnelDomainRuleEntity
import com.example.moneymanager.tunnel.data.TunnelListEntity
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.zip.GZIPInputStream

/**
 * Loads the bundled StevenBlack list (cached after the first parse, always
 * off the main thread), imports user lists as normalised files, and turns the
 * rule tables into a [TunnelPolicy].
 */
class Blocklists(private val context: Context) {
    private val dir = File(context.filesDir, "tunnel/lists")
    private val dao = AppDatabase.getDatabase(context).tunnelRuleDao()

    /** Must not be called on the main thread (about 0.2-1 s on a phone). */
    fun bundled(): DomainSet = synchronized(Companion) {
        bundledCache ?: context.assets.open(BUNDLED_ASSET).use { raw ->
            DomainSet(HostsParser.parse(GZIPInputStream(raw).bufferedReader()))
        }.also { bundledCache = it }
    }

    /** Imports a hosts/plain list; returns the entry count. Throws IOException on empty/unreadable input. */
    suspend fun import(input: InputStream, name: String): Int {
        val domains = input.bufferedReader().let { HostsParser.parse(it, maxEntries = MAX_IMPORT) }
        if (domains.isEmpty()) throw IOException("No domains found in this file")
        dir.mkdirs()
        val file = File(dir, UUID.randomUUID().toString() + ".txt")
        file.bufferedWriter().use { w -> domains.forEach { w.write(it); w.newLine() } }
        dao.insertList(
            TunnelListEntity(name = name.take(60), fileName = file.name, entryCount = domains.size, enabled = true,
                importedAt = System.currentTimeMillis())
        )
        return domains.size
    }

    suspend fun delete(list: TunnelListEntity) {
        File(dir, list.fileName).delete()
        dao.deleteList(list.id)
    }

    data class Snapshot(val policy: TunnelPolicy, val bypass: Set<String>)

    /** Builds the policy from the DB and enabled lists. Blocking I/O: call from a background dispatcher. */
    suspend fun snapshot(prefs: TunnelPrefs, uidOfPackage: (String) -> Int?): Snapshot {
        val apps = dao.appRules()
        val bypass = apps.filter { it.mode == TunnelAppRuleEntity.MODE_BYPASS }.map { it.packageName }.toSet()
        val blockedUids = apps.filter { it.mode == TunnelAppRuleEntity.MODE_BLOCK }
            .mapNotNull { uidOfPackage(it.packageName) }.toSet()
        val rules = dao.domainRules()
        val allow = DomainSet.ofPatterns(rules.filter { it.action == TunnelDomainRuleEntity.ACTION_ALLOW }.map { it.pattern })
        val block = DomainSet.ofPatterns(rules.filter { it.action == TunnelDomainRuleEntity.ACTION_BLOCK }.map { it.pattern })
        val lists = ArrayList<NamedList>()
        if (prefs.bundledListEnabled) lists += NamedList(BUNDLED_NAME, bundled())
        for (l in dao.lists().filter { it.enabled }) {
            val f = File(dir, l.fileName)
            if (!f.exists()) continue
            lists += NamedList(l.name, DomainSet(f.readLines().filter { it.isNotBlank() }))
        }
        return Snapshot(TunnelPolicy(blockedUids = blockedUids, allow = allow, block = block, lists = lists), bypass)
    }

    companion object {
        // Not ".gz": AGP inflates .gz assets at merge time and drops the extension.
        const val BUNDLED_ASSET = "blocklists/stevenblack-hosts.gzip"
        const val BUNDLED_NOTICE = "blocklists/STEVENBLACK-NOTICE.txt"
        const val BUNDLED_NAME = "StevenBlack"
        private const val MAX_IMPORT = 1_000_000
        @Volatile private var bundledCache: DomainSet? = null
    }
}
