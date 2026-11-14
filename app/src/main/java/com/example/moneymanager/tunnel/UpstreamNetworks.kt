package com.example.moneymanager.tunnel

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks real (non-VPN) networks and their DNS servers so allowed lookups go
 * to the network the phone is actually on. Prefers Wi-Fi, then cellular.
 */
class UpstreamNetworks(context: Context, private val onChange: () -> Unit) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val nets = ConcurrentHashMap<Network, Pair<NetworkCapabilities?, LinkProperties?>>()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            nets[network] = caps to (nets[network]?.second ?: cm.getLinkProperties(network))
            onChange()
        }
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            nets[network] = (nets[network]?.first ?: cm.getNetworkCapabilities(network)) to lp
            onChange()
        }
        override fun onLost(network: Network) {
            nets.remove(network)
            onChange()
        }
    }

    fun start() {
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        cm.registerNetworkCallback(req, callback)
    }

    fun stop() {
        try { cm.unregisterNetworkCallback(callback) } catch (_: IllegalArgumentException) {}
        nets.clear()
    }

    fun current(): Network? = nets.entries
        .sortedBy { (_, v) ->
            val caps = v.first
            when {
                caps == null -> 3
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 0
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 1
                else -> 2
            }
        }
        .firstOrNull()?.key

    fun dnsServers(): List<InetAddress> {
        val n = current() ?: return emptyList()
        val lp = nets[n]?.second ?: cm.getLinkProperties(n) ?: return emptyList()
        return lp.dnsServers.filterNot { it in TunnelVpnService.virtualDns }
    }
}
