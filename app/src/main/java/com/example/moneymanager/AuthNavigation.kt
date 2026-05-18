package com.example.moneymanager

import androidx.navigation.NavOptions

internal object AuthNavigation {
    fun signOutNavOptions(graphId: Int): NavOptions {
        return NavOptions.Builder()
            .setPopUpTo(graphId, true)
            .setLaunchSingleTop(true)
            .build()
    }
}
