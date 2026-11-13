package com.example.moneymanager.tunnel

import com.example.moneymanager.BuildConfig

/**
 * Lists shipped inside the tunnel APK. Asset names end in ".gzip", not ".gz":
 * AGP inflates .gz assets at merge time and drops the extension.
 */
enum class BundledList(val displayName: String, val asset: String, val notice: String) {
    /** Only MIT / CC0 / CC BY sources: fine for any distribution, with attribution. Always bundled. */
    PERMISSIVE("Permissive", "blocklists/permissive-hosts.gzip", "blocklists/PERMISSIVE-NOTICE.txt"),

    /** StevenBlack unified (adware + malware). Includes non-commercial / share-alike sources: personal use. */
    STEVENBLACK("StevenBlack", "blocklists/stevenblack-hosts.gzip", "blocklists/STEVENBLACK-NOTICE.txt");

    /** STEVENBLACK is left out of builds made with -Pjaruri.tunnel.unifiedList=false. */
    val available: Boolean get() = this != STEVENBLACK || BuildConfig.TUNNEL_UNIFIED_LIST

    companion object {
        val default: BundledList get() = if (STEVENBLACK.available) STEVENBLACK else PERMISSIVE
        val choices: List<BundledList> get() = entries.filter { it.available }
    }
}
