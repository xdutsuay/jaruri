package com.example.moneymanager.tunnel

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Plain DNS over UDP to the current network's resolvers, tried in order.
 * [socketFactory] must return a socket that bypasses the VPN (protect() +
 * Network.bindSocket on Android). Replies are matched on the query id.
 */
class UdpUpstream(
    private val servers: () -> List<InetAddress>,
    private val socketFactory: () -> DatagramSocket,
    private val port: Int = 53,
    private val timeoutMs: Int = 2_500
) : Upstream {
    override fun resolve(query: ByteArray): ByteArray? {
        if (query.size < 2) return null
        val list = servers()
        if (list.isEmpty()) return null
        val perServer = (timeoutMs / list.size.coerceAtMost(2)).coerceAtLeast(500)
        val deadline = System.currentTimeMillis() + timeoutMs
        for (server in list) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) break
            val answer = try {
                socketFactory().use { s ->
                    s.soTimeout = minOf(perServer.toLong(), left).toInt().coerceAtLeast(1)
                    s.connect(InetSocketAddress(server, port)) // kernel drops replies from anyone else
                    s.send(DatagramPacket(query, query.size))
                    val buf = ByteArray(4096)
                    var reply: ByteArray? = null
                    while (reply == null) {
                        val p = DatagramPacket(buf, buf.size)
                        s.receive(p)
                        if (p.length >= 2 && buf[0] == query[0] && buf[1] == query[1]) reply = buf.copyOf(p.length)
                    }
                    reply
                }
            } catch (_: SocketTimeoutException) {
                null
            } catch (_: Exception) {
                null
            }
            if (answer != null) return answer
        }
        return null
    }
}
