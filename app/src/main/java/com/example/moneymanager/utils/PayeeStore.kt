package com.example.moneymanager.utils

import com.example.moneymanager.data.PayeeAliasEntity
import com.example.moneymanager.data.PayeeDao
import com.example.moneymanager.data.PayeeEntity

/**
 * Resolves / creates payees and aliases during SMS import.
 * Side-effecting (DAO writes); matching logic lives in [PayeeResolver].
 */
object PayeeStore {

    data class Resolution(
        val payeeId: Long,
        val displayName: String
    )

    suspend fun resolveOrCreate(
        payeeDao: PayeeDao,
        extracted: PayeeResolver.Extracted,
        now: Long = System.currentTimeMillis()
    ): Resolution? {
        val keys = PayeeResolver.aliasKeys(extracted)
        if (keys.isEmpty() && extracted.displaySeed.isNullOrBlank()) return null

        // 1) Exact alias hit
        for (key in keys) {
            val alias = payeeDao.getAlias(key)
            if (alias != null) {
                val payee = payeeDao.getById(alias.payeeId) ?: continue
                // Ensure all keys linked
                ensureAliases(payeeDao, payee.id, keys)
                return Resolution(payee.id, payee.displayName)
            }
        }

        // 2) Exact normalizedKey on payee
        val seedKey = extracted.displaySeed?.let { PayeeResolver.normalizeKey(it) }.orEmpty()
        if (seedKey.isNotBlank()) {
            payeeDao.getByNormalizedKey(seedKey)?.let { existing ->
                ensureAliases(payeeDao, existing.id, keys)
                return Resolution(existing.id, existing.displayName)
            }
        }

        // 3) Conservative fuzzy against existing payees / aliases
        val allPayees = payeeDao.getAllList()
        val allAliases = payeeDao.getAllAliases()
        val probeKeys = keys + listOfNotNull(seedKey.takeIf { it.isNotBlank() })
        for (probe in probeKeys) {
            for (alias in allAliases) {
                if (PayeeResolver.isNearDuplicate(probe, alias.aliasKey)) {
                    val payee = payeeDao.getById(alias.payeeId) ?: continue
                    ensureAliases(payeeDao, payee.id, keys)
                    return Resolution(payee.id, payee.displayName)
                }
            }
            for (payee in allPayees) {
                if (PayeeResolver.isNearDuplicate(probe, payee.normalizedKey) ||
                    PayeeResolver.isNearDuplicate(probe, payee.displayName)
                ) {
                    ensureAliases(payeeDao, payee.id, keys)
                    return Resolution(payee.id, payee.displayName)
                }
            }
        }

        // 4) Create new payee
        val display = extracted.displaySeed?.trim()?.takeIf { it.isNotBlank() }
            ?: keys.firstOrNull()?.substringBefore('@')?.replace('.', ' ')
            ?: return null
        val normalized = PayeeResolver.normalizeKey(display)
        val id = payeeDao.insert(
            PayeeEntity(
                displayName = display,
                normalizedKey = normalized,
                createdAt = now,
                updatedAt = now
            )
        )
        ensureAliases(payeeDao, id, keys + normalized)
        return Resolution(id, display)
    }

    /**
     * Rename payee once: updates displayName and rewrites leading merchant text
     * in linked transaction memos when they still start with the old name.
     */
    suspend fun renamePayee(
        payeeDao: PayeeDao,
        payeeId: Long,
        newDisplayName: String,
        now: Long = System.currentTimeMillis()
    ): PayeeEntity? {
        val payee = payeeDao.getById(payeeId) ?: return null
        val trimmed = newDisplayName.trim()
        if (trimmed.isEmpty()) return payee
        val oldName = payee.displayName
        val updated = payee.copy(
            displayName = trimmed,
            normalizedKey = PayeeResolver.normalizeKey(trimmed),
            updatedAt = now
        )
        payeeDao.update(updated)
        if (oldName.isNotBlank() && oldName != trimmed) {
            payeeDao.rewriteMemosForPayee(payeeId, oldName, trimmed)
        }
        // Also store new name as alias
        payeeDao.insertAlias(
            PayeeAliasEntity(
                payeeId = payeeId,
                aliasKey = PayeeResolver.normalizeKey(trimmed)
            )
        )
        return updated
    }

    private suspend fun ensureAliases(payeeDao: PayeeDao, payeeId: Long, keys: List<String>) {
        for (key in keys.distinct()) {
            if (key.isBlank()) continue
            val existing = payeeDao.getAlias(key)
            if (existing == null) {
                payeeDao.insertAlias(PayeeAliasEntity(payeeId = payeeId, aliasKey = key))
            }
        }
    }
}
