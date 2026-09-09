package com.example.moneymanager.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony

/**
 * Reads recent inbox SMS via the system ContentProvider.
 * Requires READ_SMS permission (caller must request it first).
 */
object SmsInboxReader {

    data class InboxSms(
        val id: Long,
        val address: String,
        val body: String,
        val dateMillis: Long
    )

    /**
     * Returns up to [limit] recent inbox messages that look financial,
     * newest first. Returns empty list on permission / provider errors.
     */
    fun readFinancialSms(context: Context, limit: Int = 100): List<InboxSms> {
        val uri: Uri = try {
            Telephony.Sms.Inbox.CONTENT_URI
        } catch (_: Throwable) {
            Uri.parse("content://sms/inbox")
        }

        val projection = arrayOf("_id", "address", "body", "date")
        val results = mutableListOf<InboxSms>()

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "date DESC"
            ) ?: return emptyList()

            val idIdx = cursor.getColumnIndex("_id")
            val addrIdx = cursor.getColumnIndex("address")
            val bodyIdx = cursor.getColumnIndex("body")
            val dateIdx = cursor.getColumnIndex("date")

            while (cursor.moveToNext() && results.size < limit) {
                val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else ""
                if (!SmsParser.looksFinancial(body)) continue
                results += InboxSms(
                    id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L,
                    address = if (addrIdx >= 0) cursor.getString(addrIdx).orEmpty() else "",
                    body = body,
                    dateMillis = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                )
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        } finally {
            cursor?.close()
        }
        return results
    }
}
