package com.example.moneymanager.utils

import com.example.moneymanager.data.CategoryLearnDao
import com.example.moneymanager.data.CategoryLearnEntity
import com.example.moneymanager.data.CategoryRepository
import java.util.Locale

/**
 * Normalizes merchant / description text into a stable learning key.
 */
object CategoryLearning {
    fun keyFromDescription(description: String?, memo: String = ""): String? {
        val raw = when {
            !description.isNullOrBlank() -> description
            else -> memo.substringBefore("·").substringBefore("|").trim()
        }
        val cleaned = raw.lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (cleaned.length < 3) return null
        if (cleaned in IGNORED) return null
        return cleaned.take(48)
    }

    /**
     * Learn "this memo means this category" (used later to auto-categorise imports) and make
     * sure the category is in the user's list. Shared by every manual add path.
     */
    suspend fun remember(
        learnDao: CategoryLearnDao,
        categories: CategoryRepository,
        memo: String,
        description: String?,
        category: String,
        type: String,
        now: Long = System.currentTimeMillis()
    ) {
        if (TransactionAccounting.isTransferType(type)) return
        val key = keyFromDescription(description, memo) ?: return
        val existing = learnDao.get(key)
        learnDao.upsert(
            CategoryLearnEntity(
                merchantKey = key,
                category = category,
                type = type,
                hitCount = (existing?.hitCount ?: 0) + 1,
                updatedAt = now
            )
        )
        categories.addCategory(
            category,
            if (type == "INCOME") CategoryRepository.TYPE_INCOME else CategoryRepository.TYPE_EXPENSE
        )
    }

    private val IGNORED = setOf(
        "sms transaction", "bank transaction", "credit card transaction",
        "upi transaction", "others", "unknown"
    )
}
