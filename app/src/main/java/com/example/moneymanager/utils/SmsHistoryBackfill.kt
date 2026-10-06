package com.example.moneymanager.utils

import android.content.Context
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * One-shot deeper SMS inbox rescan beyond the normal Import SMS limits.
 * Results are inserted with needsCategoryReview=true; deduped via sms hash.
 * Progress cursor stored in DataStore for resume.
 */
object SmsHistoryBackfill {

    data class Progress(
        val scanned: Int,
        val imported: Int,
        val skippedDup: Int,
        val skippedIncomplete: Int,
        val done: Boolean,
        val message: String
    )

    /**
     * @param maxScan how many newest inbox rows to scan (default from settings / 8000)
     * @param limit max financial messages to attempt import in this run
     */
    suspend fun run(
        context: Context,
        maxScan: Int? = null,
        limit: Int = 500,
        onProgress: (Progress) -> Unit = {}
    ): Progress = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val settings = SettingsRepository(app)
        val scanCap = maxScan ?: settings.smsHistoryMaxScan.first()
        val cursorDate = settings.smsHistoryBackfillCursor.first()

        onProgress(Progress(0, 0, 0, 0, false, "Scanning inbox…"))

        // Prefer messages older than cursor when resuming; first run takes newest batch.
        val all = SmsInboxReader.readFinancialSms(app, limit = limit * 2, maxScan = scanCap)
        val toProcess = if (cursorDate > 0L) {
            all.filter { it.dateMillis < cursorDate }.take(limit)
        } else {
            all.take(limit)
        }.sortedByDescending { it.dateMillis }

        val db = AppDatabase.getDatabase(app)
        val dao = db.transactionDao()
        val known = InstrumentLedger.knownInstruments(db.accountDao())

        var imported = 0
        var skippedDup = 0
        var skippedIncomplete = 0
        var scanned = 0
        var oldestProcessed = Long.MAX_VALUE

        for (sms in toProcess) {
            scanned++
            oldestProcessed = minOf(oldestProcessed, sms.dateMillis)
            if (SmsParser.isPromotionalSender(sms.address)) continue
            val parsed = SmsParser.parse(sms.body, fallbackNow = sms.dateMillis)
            if (parsed == null || !parsed.isComplete) {
                skippedIncomplete++
                continue
            }
            val hash = parsed.smsHash
            if (hash.isNotEmpty() && dao.countByMemoTag("[sms:$hash]") > 0) {
                skippedDup++
                continue
            }

            val trust = SmsTrustEvaluator.evaluate(sms.body, sms.address, parsed, known)
            // Backfill is more permissive than live auto-import.
            if (trust.isLikelyScam || trust.isPromotional || trust.score < 0.25f) {
                skippedIncomplete++
                continue
            }

            val built = SmsImportPipeline.buildEntity(
                db = db,
                parsed = parsed,
                rawBody = sms.body,
                needsCategoryReview = true
            )
            val entity = built.entity.copy(needsCategoryReview = true)
            val rowId = dao.insertTransaction(entity)
            InstrumentLedger.applyEntityDelta(db.accountDao(), entity)
            InstrumentLedger.recordReportedBalance(
                db.accountDao(),
                db.balanceObservationDao(),
                entity.accountId,
                parsed,
                transactionId = rowId
            )
            imported++

            if (scanned % 25 == 0) {
                onProgress(
                    Progress(
                        scanned, imported, skippedDup, skippedIncomplete, false,
                        "Scanned $scanned · imported $imported"
                    )
                )
            }
        }

        val newCursor = when {
            oldestProcessed != Long.MAX_VALUE -> oldestProcessed
            cursorDate > 0L -> cursorDate
            else -> System.currentTimeMillis()
        }
        settings.setSmsHistoryBackfillCursor(newCursor)
        settings.setSmsHistoryMaxScan(scanCap)

        val result = Progress(
            scanned = scanned,
            imported = imported,
            skippedDup = skippedDup,
            skippedIncomplete = skippedIncomplete,
            done = true,
            message = "Done: imported $imported (dup $skippedDup, skipped $skippedIncomplete)"
        )
        onProgress(result)
        result
    }
}
