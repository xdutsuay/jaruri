package com.example.moneymanager.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.moneymanager.utils.SmsAutoImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Listens for [Telephony.Sms.Intents.SMS_RECEIVED_ACTION] and auto-imports
 * bank/UPI transaction SMS when the user has opted in via Settings.
 */
class SmsTransactionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Multipart SMS: concatenate bodies in order; keep first originator address.
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        if (body.isBlank()) return
        val address = messages.firstOrNull()?.originatingAddress

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SmsAutoImporter.tryImport(context, body, notify = true, address = address)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
