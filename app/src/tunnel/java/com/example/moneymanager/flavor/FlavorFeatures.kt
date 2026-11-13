package com.example.moneymanager.flavor

import android.content.Context
import android.content.Intent
import com.example.moneymanager.tunnel.ui.TunnelActivity

/** tunnel flavor: exposes the Tunnel screen to the hub. */
object FlavorFeatures {
    val tunnel: FeatureEntry? = object : FeatureEntry {
        override fun open(context: Context) {
            context.startActivity(Intent(context, TunnelActivity::class.java))
        }
    }
}
