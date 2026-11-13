package com.example.moneymanager.tunnel

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.moneymanager.tunnel.log.TunnelLogDatabase
import com.example.moneymanager.tunnel.log.TunnelRetention

/** Daily log retention (7 days detail / 90 days counts). */
class TunnelPurgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        TunnelRetention.purge(TunnelLogDatabase.get(applicationContext).dao(), System.currentTimeMillis())
        return Result.success()
    }

    companion object {
        const val NAME = "tunnel-log-retention"
    }
}
