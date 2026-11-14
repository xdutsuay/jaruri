package com.example.moneymanager.tunnel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import android.util.LruCache
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.tunnel.dns.IpPacket
import com.example.moneymanager.tunnel.log.DnsLogWriter
import com.example.moneymanager.tunnel.log.TunnelLogDatabase
import com.example.moneymanager.tunnel.log.TunnelRetention
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Local DNS-filter VPN. Only our virtual DNS addresses are routed into the
 * tun, so ordinary traffic never passes through the app. Allowed lookups are
 * forwarded to the current network's resolvers over sockets that bypass the
 * VPN (protect + bindSocket). Bypass apps are excluded from the VPN.
 *
 * Not a foreground service: an established VpnService is bound by the system
 * and keeps foreground priority; we post an ongoing notification instead.
 */
class TunnelVpnService : VpnService() {

    /** Any unexpected crash in the rules/establish path ends in Failed + a clean stop, never a stuck "Starting". */
    private val crashHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "tunnel failed", e)
        TunnelState.set(TunnelState.Status.Failed(getString(com.example.moneymanager.R.string.tunnel_err_establish)))
        mainHandler.post { stopTunnel(keepStatus = true) }
    }
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + crashHandler)
    private lateinit var prefs: TunnelPrefs
    private lateinit var blocklists: Blocklists
    private lateinit var networks: UpstreamNetworks
    private var logWriter: DnsLogWriter? = null
    private val policy = AtomicReference(TunnelPolicy())
    private var session: Session? = null
    private var currentBypass: Set<String> = emptySet()
    private var rulesJob: Job? = null
    private val refresh = MutableStateFlow(0L)
    private val pkgCache = LruCache<Int, String>(256)

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = TunnelPrefs(this)
        blocklists = Blocklists(this)
        networks = UpstreamNetworks(this) { onNetworksChanged() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopTunnel(); return START_NOT_STICKY }
            ACTION_PAUSE -> { pause(intent.getLongExtra(EXTRA_MINUTES, 10)); return START_NOT_STICKY }
            ACTION_RESUME -> { resume(); return START_NOT_STICKY }
            // ACTION_START, or SERVICE_INTERFACE when Android starts us as the always-on VPN.
            else -> startTunnel()
        }
        return START_STICKY
    }

    @OptIn(FlowPreview::class)
    private fun startTunnel() {
        if (session != null || rulesJob != null) return
        TunnelState.set(TunnelState.Status.Starting)
        networks.start()
        val db = TunnelLogDatabase.get(this)
        logWriter = DnsLogWriter(db, ::packageOf, scope)
        scheduleRetention()
        scope.launch { TunnelRetention.purge(db.dao(), System.currentTimeMillis()) }

        val dao = AppDatabase.getDatabase(this).tunnelRuleDao()
        rulesJob = scope.launch {
            combine(dao.observeAppRules(), dao.observeDomainRules(), dao.observeLists(), prefs.changes, refresh) { _, _, _, _, _ -> Unit }
                .debounce(300)
                .collect { applyRules() }
        }
    }

    /** Rebuilds the policy; re-establishes the interface only when the bypass list changed. */
    private suspend fun applyRules() {
        val snap = try {
            blocklists.snapshot(prefs, ::uidOfPackage)
        } catch (e: Exception) {
            Log.w(TAG, "rules failed to load", e)
            Blocklists.Snapshot(TunnelPolicy(), currentBypass)
        }
        policy.set(snap.policy)
        val needEstablish = session == null || snap.bypass != currentBypass
        if (needEstablish) {
            if (!establish(snap.bypass)) return
        }
        TunnelNotifications.update(this, prefs.pausedUntil)
    }

    @Synchronized
    private fun establish(bypass: Set<String>): Boolean {
        val builder = Builder()
            .setSession(getString(com.example.moneymanager.R.string.tunnel_title))
            .addAddress(VIRTUAL_CLIENT_V4, 32)
            .addAddress(VIRTUAL_CLIENT_V6, 128)
            .addDnsServer(VIRTUAL_DNS_V4)
            .addDnsServer(VIRTUAL_DNS_V6)
            .addRoute(VIRTUAL_DNS_V4, 32)
            .addRoute(VIRTUAL_DNS_V6, 128)
            .setMtu(1500)
            .setConfigureIntent(TunnelNotifications.openIntent(this))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        // Our own lookups never need filtering, and this avoids any loop.
        try { builder.addDisallowedApplication(packageName) } catch (_: PackageManager.NameNotFoundException) {}
        var bypassed = 0
        for (pkg in bypass) {
            try { builder.addDisallowedApplication(pkg); bypassed++ } catch (_: PackageManager.NameNotFoundException) {}
        }
        networks.current()?.let { builder.setUnderlyingNetworks(arrayOf(it)) }
        val pfd = try {
            builder.establish()
        } catch (e: Exception) {
            Log.w(TAG, "establish failed", e)
            null
        }
        if (pfd == null) {
            // null = we are no longer prepared (another VPN / revoked).
            TunnelState.set(TunnelState.Status.Failed(getString(com.example.moneymanager.R.string.tunnel_err_establish)))
            stopTunnel(keepStatus = true)
            return false
        }
        val old = session
        session = Session(pfd).also { it.start() }
        old?.close()
        currentBypass = bypass
        TunnelState.set(TunnelState.Status.On(System.currentTimeMillis(), bypassed))
        return true
    }

    private fun onNetworksChanged() {
        try { session?.let { networks.current()?.let { n -> setUnderlyingNetworks(arrayOf(n)) } } } catch (_: Exception) {}
    }

    fun pause(minutes: Long) {
        prefs.pausedUntil = System.currentTimeMillis() + minutes * 60_000
        TunnelNotifications.update(this, prefs.pausedUntil)
        TunnelNotifications.scheduleResumeRefresh(this, prefs.pausedUntil)
    }

    fun resume() {
        prefs.pausedUntil = 0
        TunnelNotifications.update(this, 0)
    }

    @Synchronized
    fun stopTunnel(keepStatus: Boolean = false) {
        rulesJob?.cancel()
        rulesJob = null
        session?.close()
        session = null
        currentBypass = emptySet()
        networks.stop()
        logWriter?.close()
        logWriter = null
        prefs.pausedUntil = 0
        TunnelNotifications.cancel(this)
        if (!keepStatus) TunnelState.set(TunnelState.Status.Off)
        stopSelf()
    }

    override fun onRevoke() {
        // Another VPN started, or the user turned us off in Settings. Never fight back.
        TunnelState.set(TunnelState.Status.Revoked)
        stopTunnel(keepStatus = true)
        super.onRevoke()
    }

    override fun onDestroy() {
        if (session != null) stopTunnel(keepStatus = TunnelState.status.value !is TunnelState.Status.On)
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    // ---- engine wiring ----

    private val pausedPolicy = TunnelPolicy(paused = true)

    private fun currentPolicy(): TunnelPolicy =
        if (System.currentTimeMillis() < prefs.pausedUntil) pausedPolicy else policy.get()

    private fun uidOf(ip: IpPacket): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return TunnelPolicy.UNKNOWN_UID
        val cm = getSystemService(ConnectivityManager::class.java)
        val uid = cm.getConnectionOwnerUid(
            OsConstants.IPPROTO_UDP,
            InetSocketAddress(InetAddress.getByAddress(ip.src), ip.srcPort),
            InetSocketAddress(InetAddress.getByAddress(ip.dst), ip.dstPort)
        )
        // The system resolver (AID_DNS) or "not found" means we don't know which app asked.
        return if (uid == Process.INVALID_UID || uid == AID_DNS || uid < Process.FIRST_APPLICATION_UID) TunnelPolicy.UNKNOWN_UID else uid
    }

    private fun packageOf(uid: Int): String? {
        pkgCache.get(uid)?.let { return it }
        val pkg = packageManager.getPackagesForUid(uid)?.sorted()?.firstOrNull() ?: return null
        pkgCache.put(uid, pkg)
        return pkg
    }

    private fun uidOfPackage(pkg: String): Int? = try {
        packageManager.getApplicationInfo(pkg, 0).uid
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun protectedSocket(): DatagramSocket {
        val s = DatagramSocket()
        if (!protect(s)) {
            s.close()
            throw IOException("protect() failed")
        }
        try { networks.current()?.bindSocket(s) } catch (_: Exception) {}
        return s
    }

    private fun scheduleRetention() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TunnelPurgeWorker.NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TunnelPurgeWorker>(1, TimeUnit.DAYS).build()
        )
    }

    /** One established interface + its reader thread. Closing wakes the reader through a pipe. */
    private inner class Session(private val pfd: ParcelFileDescriptor) {
        private val wake: Array<FileDescriptor> = Os.pipe()
        private val forwarders = Executors.newFixedThreadPool(4)
        private val thread = Thread({ loop() }, "jaruri-tunnel")

        fun start() = thread.start()

        private fun loop() {
            val input = FileInputStream(pfd.fileDescriptor)
            val output = FileOutputStream(pfd.fileDescriptor)
            val engine = DnsTunnelEngine(
                policy = ::currentPolicy,
                blockMode = { prefs.blockMode },
                upstream = UdpUpstream(servers = { networks.dnsServers() }, socketFactory = ::protectedSocket),
                uidOf = ::uidOf,
                onEvent = { logWriter?.offer(it) },
                forwarders = forwarders
            )
            val fds = arrayOf(
                StructPollfd().apply { fd = pfd.fileDescriptor; events = OsConstants.POLLIN.toShort() },
                StructPollfd().apply { fd = wake[0]; events = OsConstants.POLLIN.toShort() }
            )
            engine.run(
                source = { buf ->
                    while (true) {
                        fds.forEach { it.revents = 0 }
                        try {
                            Os.poll(fds, -1)
                        } catch (e: ErrnoException) {
                            if (e.errno == OsConstants.EINTR) continue
                            return@run -1
                        }
                        if (fds[1].revents.toInt() != 0) return@run -1
                        val r = fds[0].revents.toInt()
                        if (r and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL) != 0) return@run -1
                        if (r and OsConstants.POLLIN != 0) {
                            val n = input.read(buf)
                            if (n != 0) return@run n
                        }
                    }
                    @Suppress("UNREACHABLE_CODE") -1
                },
                sink = { output.write(it) }
            )
        }

        fun close() {
            try { Os.write(wake[1], byteArrayOf(1), 0, 1) } catch (_: Exception) {}
            try { thread.join(1_000) } catch (_: InterruptedException) {}
            forwarders.shutdownNow()
            try { pfd.close() } catch (_: IOException) {}
            wake.forEach { try { Os.close(it) } catch (_: Exception) {} }
        }
    }

    companion object {
        private const val TAG = "JaruriTunnel"
        const val ACTION_START = "com.kaustubhtripathi.jaruri.tunnel.START"
        const val ACTION_STOP = "com.kaustubhtripathi.jaruri.tunnel.STOP"
        const val ACTION_PAUSE = "com.kaustubhtripathi.jaruri.tunnel.PAUSE"
        const val ACTION_RESUME = "com.kaustubhtripathi.jaruri.tunnel.RESUME"
        const val EXTRA_MINUTES = "minutes"
        private const val AID_DNS = 1051

        const val VIRTUAL_CLIENT_V4 = "10.111.222.2"
        const val VIRTUAL_DNS_V4 = "10.111.222.1"
        const val VIRTUAL_CLIENT_V6 = "fd66:6a61:7275::2"
        const val VIRTUAL_DNS_V6 = "fd66:6a61:7275::1"
        val virtualDns: Set<InetAddress> by lazy {
            setOf(InetAddress.getByName(VIRTUAL_DNS_V4), InetAddress.getByName(VIRTUAL_DNS_V6))
        }

        @Volatile internal var instance: TunnelVpnService? = null

        /** Must be called from a visible activity (after VpnService.prepare() returned null). */
        fun start(context: Context) {
            context.startService(Intent(context, TunnelVpnService::class.java).setAction(ACTION_START))
        }

        /** In-process calls: the service is ours and running, so no background-start question arises. */
        fun stop(context: Context) {
            instance?.stopTunnel() ?: TunnelState.set(TunnelState.Status.Off)
        }

        fun pause(context: Context, minutes: Long = 10) {
            instance?.pause(minutes)
        }

        fun resume(context: Context) {
            instance?.resume()
        }

        fun refreshRules() {
            instance?.let { it.refresh.value = System.nanoTime() }
        }
    }
}
