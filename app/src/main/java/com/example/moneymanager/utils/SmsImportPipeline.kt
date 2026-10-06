package com.example.moneymanager.utils

import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.TransactionEntity
import java.util.concurrent.TimeUnit

/**
 * Shared construction of a [TransactionEntity] from a [ParsedSms], including
 * payee resolution and refund matching.
 */
object SmsImportPipeline {

    data class Built(
        val entity: TransactionEntity,
        val parsed: ParsedSms
    )

    suspend fun buildEntity(
        db: AppDatabase,
        parsed: ParsedSms,
        rawBody: String,
        needsCategoryReview: Boolean,
        forceCategory: String? = null,
        forceType: String? = null
    ): Built {
        val payeeDao = db.payeeDao()
        val learnDao = db.categoryLearnDao()
        val customRules = db.categoryRuleDao().getEnabledList()

        val extracted = PayeeResolver.extractFromSms(rawBody, parsed.description)
        val payee = PayeeStore.resolveOrCreate(payeeDao, extracted)
        val refs = RefundMatcher.Refs(
            upiRef = parsed.upiRef ?: RefundMatcher.extractRefs(rawBody).upiRef,
            rrn = parsed.rrn ?: RefundMatcher.extractRefs(rawBody).rrn
        )
        val isRefund = parsed.isRefundOrReversal ||
            RefundMatcher.isRefundOrReversal(rawBody, parsed.isIncome)

        var linkedId: Long? = null
        var refundNeutral = false
        var type = forceType ?: parsed.typeLabel()
        var category = forceCategory

        if (isRefund && (parsed.isIncome == true || type == "INCOME")) {
            val since = parsed.dateTimestamp - TimeUnit.DAYS.toMillis(14)
            val until = parsed.dateTimestamp + TimeUnit.DAYS.toMillis(1)
            val recent = db.transactionDao().getRecentForRefundMatch(since, until)
            val match = RefundMatcher.findOriginalExpense(
                candidates = recent.map {
                    RefundMatcher.MatchCandidate(
                        it.id, it.amount, it.type, it.dateTimestamp,
                        it.payeeId, it.upiRef, it.rrn, it.memo
                    )
                },
                amount = parsed.amount ?: 0.0,
                payeeId = payee?.payeeId,
                refs = refs,
                refundAt = parsed.dateTimestamp
            )
            if (match != null) {
                linkedId = match.id
                refundNeutral = true
                type = "INCOME"
                category = category ?: "Refund"
            }
        }

        if (category == null) {
            val learnKey = CategoryLearning.keyFromDescription(parsed.description, parsed.toMemo())
            val learned = learnKey?.let { learnDao.get(it)?.category }
            category = SmsCategorizer.categorize(parsed, rawBody, learned, customRules)
        }

        val accountId = InstrumentLedger.resolveOrCreate(db.accountDao(), parsed)
        val baseMemo = parsed.toMemo()
        val memo = if (payee != null) {
            val before = baseMemo.substringBefore(" ·").substringBefore(" |").trim()
            if (before.isBlank()) "${payee.displayName} · $baseMemo"
            else payee.displayName + baseMemo.removePrefix(before)
        } else {
            baseMemo
        }

        val review = if (forceCategory != null) {
            needsCategoryReview
        } else {
            needsCategoryReview || SmsCategorizer.needsCategoryReview(category, null)
        }

        val entity = TransactionEntity(
            type = type,
            category = category,
            amount = parsed.amount ?: 0.0,
            dateTimestamp = parsed.dateTimestamp,
            memo = memo,
            accountId = accountId,
            needsCategoryReview = review,
            payeeId = payee?.payeeId,
            upiRef = refs.upiRef,
            rrn = refs.rrn,
            linkedTransactionId = linkedId,
            isRefundNeutral = refundNeutral
        )
        return Built(entity, parsed)
    }
}
