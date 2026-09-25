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
     * Returns up to [limit] recent inbox messages that look like real money
     * movements (not OTP / loan promos), newest first.
     *
     * Scans at most [maxScan] newest inbox rows so large inboxes (thousands of
     * SMS) still return a useful list quickly.
     */
    fun readFinancialSms(
        context: Context,
        limit: Int = 200,
        maxScan: Int = 1500
    ): List<InboxSms> {
        val uri: Uri = try {
            Telephony.Sms.Inbox.CONTENT_URI
        } catch (_: Throwable) {
            Uri.parse("content://sms/inbox")
        }

        val projection = arrayOf("_id", "address", "body", "date")
        val results = mutableListOf<InboxSms>()

        var cursor: Cursor? = null
        try {
            // Many OEMs honor LIMIT in sortOrder; fall back without it if needed.
            cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "date DESC LIMIT $maxScan"
            ) ?: return emptyList()

            val idIdx = cursor.getColumnIndex("_id")
            val addrIdx = cursor.getColumnIndex("address")
            val bodyIdx = cursor.getColumnIndex("body")
            val dateIdx = cursor.getColumnIndex("date")

            var scanned = 0
            while (cursor.moveToNext() && results.size < limit && scanned < maxScan) {
                scanned++
                val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else ""
                val address = if (addrIdx >= 0) cursor.getString(addrIdx).orEmpty() else ""
                if (SmsParser.isPromotionalSender(address)) continue
                if (!SmsParser.looksLikeTransaction(body)) continue
                results += InboxSms(
                    id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L,
                    address = address,
                    body = body,
                    dateMillis = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                )
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            // Some providers reject LIMIT in ORDER BY — retry without it.
            return readFinancialSmsFallback(context, uri, projection, limit, maxScan)
        } finally {
            cursor?.close()
        }
        return results
    }

    private fun readFinancialSmsFallback(
        context: Context,
        uri: Uri,
        projection: Array<String>,
        limit: Int,
        maxScan: Int
    ): List<InboxSms> {
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

            var scanned = 0
            while (cursor.moveToNext() && results.size < limit && scanned < maxScan) {
                scanned++
                val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else ""
                val address = if (addrIdx >= 0) cursor.getString(addrIdx).orEmpty() else ""
                if (SmsParser.isPromotionalSender(address)) continue
                if (!SmsParser.looksLikeTransaction(body)) continue
                results += InboxSms(
                    id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L,
                    address = address,
                    body = body,
                    dateMillis = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                )
            }
        } catch (_: Exception) {
            return emptyList()
        } finally {
            cursor?.close()
        }
        return results
    }
}
