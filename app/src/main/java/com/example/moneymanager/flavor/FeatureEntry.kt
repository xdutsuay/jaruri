package com.example.moneymanager.flavor

import android.content.Context

/** A feature that only some distribution flavors ship (see FlavorFeatures in src/<flavor>/). */
interface FeatureEntry {
    fun open(context: Context)
}
