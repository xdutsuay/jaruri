package com.example.moneymanager.flavor

import android.content.Context

/** A hub feature that opens its own screen (see [FlavorFeatures]). */
interface FeatureEntry {
    fun open(context: Context)
}
