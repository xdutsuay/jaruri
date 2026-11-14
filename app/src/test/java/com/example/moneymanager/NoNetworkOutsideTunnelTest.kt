package com.example.moneymanager

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app holds INTERNET for Tunnel, and that permission applies to the whole app.
 * This keeps the promise that only the Tunnel's DNS forwarder uses the network:
 * no code outside the `tunnel` package may open sockets or URLs.
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
        val roots = listOf("src/main/java").map(::File)
        assertTrue("run from the app module", roots[0].isDirectory)
        val tunnelDir = File(roots[0], "com/example/moneymanager/tunnel").canonicalPath + File.separator
        val offenders = roots.filter { it.isDirectory }.flatMap { root ->
            root.walkTopDown().filter {
                it.isFile && (it.extension == "kt" || it.extension == "java") &&
                    !it.canonicalPath.startsWith(tunnelDir)
            }.flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line ->
                    val code = line.substringBefore("//")
                    if (banned.any { it.containsMatchIn(code) }) "${f.path}:${i + 1}: ${line.trim()}" else null
                }
            }
        }
        assertTrue("Network code outside the tunnel package:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
