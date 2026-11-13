package com.example.moneymanager

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The tunnel flavor holds INTERNET, which applies to the whole app. This keeps
 * the promise that only the Tunnel's DNS forwarder uses the network: no other
 * source set may open sockets or URLs. (Runs in both flavors.)
 */
class NoNetworkOutsideTunnelTest {
    private val banned = listOf(
        Regex("""\bjava\.net\.(Socket|ServerSocket|DatagramSocket|URL|HttpURLConnection|URLConnection)\b"""),
        Regex("""\b(DatagramSocket|ServerSocket|HttpURLConnection|HttpsURLConnection)\s*\("""),
        Regex("""\bURL\s*\(\s*["a-zA-Z]"""),
        Regex("""\.openConnection\s*\("""),
        Regex("""\bokhttp3\b|\bretrofit2\b|\bio\.ktor\b""")
    )

    @Test
    fun onlyTunnelSourcesTouchTheNetwork() {
        val roots = listOf("src/main/java", "src/offline/java").map(::File)
        assertTrue("run from the app module", roots[0].isDirectory)
        val offenders = roots.filter { it.isDirectory }.flatMap { root ->
            root.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line ->
                    val code = line.substringBefore("//")
                    if (banned.any { it.containsMatchIn(code) }) "${f.path}:${i + 1}: ${line.trim()}" else null
                }
            }
        }
        assertTrue("Network code outside src/tunnel:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
