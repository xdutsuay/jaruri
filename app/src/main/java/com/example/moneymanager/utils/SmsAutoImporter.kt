package com.example.moneymanager.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.moneymanager.R
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.SettingsRepository
import com.example.moneymanager.data.TransactionEntity
import kotlinx.coroutines.flow.first

/**
 * Shared path for turning a raw SMS body into a ledger row (with dedup + opt-in).
 * Used by [com.example.moneymanager.sms.SmsTransactionReceiver].
 */
object SmsAutoImporter {

    private const val CHANNEL_ID = "sms_auto_import"
    private const val NOTIFICATION_ID = 4101

    /**
     * @param address SMS originator (DLT ID). Used to skip promotional `-P` senders.
     * @return inserted entity, or null if skipped (disabled / not financial / incomplete / duplicate).
     */
    suspend fun tryImport(
        context: Context,
        body: String,
        notify: Boolean = true,
        address: String? = null
    ): TransactionEntity? {
        val appContext = context.applicationContext
        val settings = SettingsRepository(appContext)
        if (!settings.autoImportSmsEnabled.first()) return null

        if (!address.isNullOrBlank() && SmsParser.isPromotionalSender(address)) return null
        if (!SmsParser.looksLikeTransaction(body)) return null
        val parsed = SmsParser.parse(body) ?: return null
        if (!parsed.isComplete) return null

        val dao = AppDatabase.getDatabase(appContext).transactionDao()
        val hash = parsed.smsHash
        if (hash.isNotEmpty()) {
            val tag = "[sms:$hash]"
            if (dao.countByMemoTag(tag) > 0) return null
        }

        val learnDao = AppDatabase.getDatabase(appContext).categoryLearnDao()
        val learnKey = CategoryLearning.keyFromDescription(parsed.description, parsed.toMemo())
        val learned = learnKey?.let { learnDao.get(it)?.category }
        val category = SmsCategorizer.categorize(parsed, body, learned)
        val type = parsed.typeLabel()
        val entity = TransactionEntity(
            type = type,
            category = category,
            amount = parsed.amount!!,
            dateTimestamp = parsed.dateTimestamp,
            memo = parsed.toMemo()
        )
        dao.insertTransaction(entity)

        if (notify) {
            showAddedNotification(appContext, entity)
        }
        return entity
    }

    private fun showAddedNotification(context: Context, entity: TransactionEntity) {
        try {
            ensureChannel(context)
            val merchant = entity.memo.substringBefore(" ·").substringBefore(" |").trim()
                .ifBlank { entity.category }
            val amountLabel = "₹${"%.2f".format(entity.amount)}"
            val title = context.getString(R.string.sms_auto_import_notification_title)
            val text = context.getString(
                R.string.sms_auto_import_notification_text,
                amountLabel,
                merchant
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_add)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS may be missing on API 33+ — ignore.
        } catch (_: Exception) {
            // Notification is best-effort.
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.sms_auto_import_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }
}
