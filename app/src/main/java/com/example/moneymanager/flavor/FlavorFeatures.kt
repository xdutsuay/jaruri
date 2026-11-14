package com.example.moneymanager.flavor

import android.content.Context
import android.content.Intent
import com.example.moneymanager.tunnel.ui.TunnelActivity

/** Optional features shown on the hub. Tunnel ships in every build. */
object FlavorFeatures {
    val tunnel: FeatureEntry? = object : FeatureEntry {
        override fun open(context: Context) {
            context.startActivity(Intent(context, TunnelActivity::class.java))
        }
    }
}
