package com.example.moneymanager.tunnel.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.moneymanager.R
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.tunnel.Blocklists
import com.example.moneymanager.tunnel.DomainSet
import com.example.moneymanager.tunnel.TunnelPrefs
import com.example.moneymanager.tunnel.TunnelState
import com.example.moneymanager.tunnel.TunnelVpnService
import com.example.moneymanager.tunnel.data.TunnelAppRuleEntity
import com.example.moneymanager.tunnel.data.TunnelDomainRuleEntity
import com.example.moneymanager.tunnel.data.TunnelListEntity
import com.example.moneymanager.tunnel.dns.BlockMode
import com.example.moneymanager.tunnel.log.DnsLogEntity
import com.example.moneymanager.tunnel.log.DnsLogWriter
import com.example.moneymanager.tunnel.log.TunnelLogDatabase
import com.example.moneymanager.tunnel.log.TunnelLogExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.TimeZone

data class AppRow(val packageName: String, val label: String, val uid: Int)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class TunnelViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    val prefs = TunnelPrefs(application)
    private val rules = AppDatabase.getDatabase(application).tunnelRuleDao()
    private val logDb = TunnelLogDatabase.get(application)
    private val blocklists = Blocklists(application)

    val status: LiveData<TunnelState.Status> = TunnelState.status.asLiveData()
    val prefsChanged: LiveData<Long> = prefs.changes.asLiveData()
    val today = logDb.dao().observeDay(DnsLogWriter.dayOf(System.currentTimeMillis(), TimeZone.getDefault())).asLiveData()
    val appRules: LiveData<List<TunnelAppRuleEntity>> = rules.observeAppRules().asLiveData()
    val domainRules: LiveData<List<TunnelDomainRuleEntity>> = rules.observeDomainRules().asLiveData()
    val lists: LiveData<List<TunnelListEntity>> = rules.observeLists().asLiveData()

    // ---- apps ----
    val apps = MutableLiveData<List<AppRow>>()
    val appCounts = MutableLiveData<Map<String, Int>>(emptyMap())

    fun loadApps() {
        viewModelScope.launch {
            apps.value = withContext(Dispatchers.IO) {
                val pm = app.packageManager
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcher, 0)
                    .map { it.activityInfo.applicationInfo }
                    .distinctBy { it.packageName }
                    .filter { it.packageName != app.packageName }
                    .map { AppRow(it.packageName, pm.getApplicationLabel(it).toString(), it.uid) }
                    .sortedBy { it.label.lowercase() }
            }
            appCounts.value = withContext(Dispatchers.IO) {
                logDb.dao().perApp(System.currentTimeMillis() - 7 * 86_400_000L).associate { it.pkg to it.count }
            }
        }
    }

    fun icon(pkg: String): Drawable? = try {
        app.packageManager.getApplicationIcon(pkg)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun setAppMode(pkg: String, mode: String?) = viewModelScope.launch {
        if (mode == null) rules.clearAppRule(pkg)
        else rules.setAppRule(TunnelAppRuleEntity(pkg, mode, System.currentTimeMillis()))
    }

    // ---- rules / lists ----
    val bundledCount = MutableLiveData<Int?>(null)

    fun loadBundledCount() = viewModelScope.launch {
        bundledCount.value = withContext(Dispatchers.Default) { blocklists.bundled().size }
    }

    fun bundledNotice(): String = app.assets.open(Blocklists.BUNDLED_NOTICE).bufferedReader().use { it.readText() }

    fun setBundled(on: Boolean) { prefs.bundledListEnabled = on }
    fun setZeroIp(on: Boolean) { prefs.blockMode = if (on) BlockMode.ZERO_IP else BlockMode.NXDOMAIN }

    /** Returns false if [raw] isn't a usable pattern. */
    fun putRule(raw: String, action: String): Boolean {
        val pattern = DomainSet.normalizePattern(raw) ?: return false
        viewModelScope.launch {
            rules.putDomainRule(TunnelDomainRuleEntity(pattern = pattern, action = action, createdAt = System.currentTimeMillis()))
        }
        return true
    }

    fun deleteRule(id: Long) = viewModelScope.launch { rules.deleteDomainRule(id) }
    fun setListEnabled(id: Long, on: Boolean) = viewModelScope.launch { rules.setListEnabled(id, on) }
    fun deleteList(list: TunnelListEntity) = viewModelScope.launch { withContext(Dispatchers.IO) { blocklists.delete(list) } }

    fun importList(uri: Uri, onResult: (Result<Int>) -> Unit) = viewModelScope.launch {
        val r = withContext(Dispatchers.IO) {
            runCatching {
                val resolver = app.contentResolver
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "Imported list"
                val input = resolver.openInputStream(uri) ?: throw java.io.IOException("Can't open file")
                input.use { blocklists.import(it, name) }
            }
        }
        onResult(r)
    }

    // ---- log ----
    private val logQuery = MutableStateFlow("")
    private val blockedOnly = MutableStateFlow(false)
    val log: LiveData<List<DnsLogEntity>> = combine(logQuery.debounce(200), blockedOnly) { q, b -> q.trim() to b }
        .flatMapLatest { (q, b) -> logDb.dao().observeRecent(b, q) }
        .asLiveData()

    fun setLogQuery(q: String) { logQuery.value = q }
    fun setBlockedOnly(b: Boolean) { blockedOnly.value = b }
    fun clearLog() = viewModelScope.launch { logDb.dao().clearLog(); logDb.dao().clearDaily() }

    /** Writes cache/exports/<name> and returns it, or null when there is nothing to export. */
    suspend fun exportLog(markdown: Boolean): File? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val rows = logDb.dao().since(0)
        if (rows.isEmpty()) return@withContext null
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val body = if (markdown) {
            val top = logDb.dao().topBlocked(DnsLogWriter.dayOf(now, TimeZone.getDefault()) - 7, 20)
            TunnelLogExporter.markdown(rows, top, now)
        } else TunnelLogExporter.csv(rows)
        File(dir, "jaruri-tunnel-log." + if (markdown) "md" else "csv").apply { writeText(body) }
    }

    // ---- warnings ----
    fun warnings(): List<String> {
        val out = ArrayList<String>()
        val cm = app.getSystemService(ConnectivityManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = cm.activeNetwork?.let { cm.getLinkProperties(it) }
            val strict = lp?.privateDnsServerName
            if (lp != null && lp.isPrivateDnsActive && strict != null) out += app.getString(R.string.tunnel_warn_private_dns, strict)
        }
        if (!TunnelState.isOn) {
            @Suppress("DEPRECATION")
            val otherVpn = cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
            if (otherVpn) out += app.getString(R.string.tunnel_warn_other_vpn)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && TunnelVpnService.instance?.isLockdownEnabled == true) {
            out += app.getString(R.string.tunnel_warn_lockdown)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) out += app.getString(R.string.tunnel_warn_old_android)
        return out
    }
}
