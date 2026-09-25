package com.example.moneymanager.utils

import com.example.moneymanager.data.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses Money Manager–style spreadsheet exports:
 * Date | Income/Expenses | Category | Memo | Amount
 * Amount may be negative for expenses.
 */
object LegacySpreadsheetParser {

    fun parse(csvOrTsv: String): List<TransactionEntity> {
        val lines = csvOrTsv.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return emptyList()

        val sep = detectSeparator(lines.first())
        val header = split(lines.first(), sep).map { it.trim().lowercase(Locale.ROOT) }
        val start = if (header.any { it.contains("date") || it.contains("income") }) 1 else 0

        val iDate = indexOf(header, "date")
        val iType = indexOf(header, "income", "expense", "type")
        val iCat = indexOf(header, "category", "cate")
        val iMemo = indexOf(header, "memo", "note", "remark")
        val iAmt = indexOf(header, "amount", "money")

        val dateFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("dd/MM/yyyy", Locale.US),
            SimpleDateFormat("MM/dd/yyyy", Locale.US)
        ).onEach { it.isLenient = true }

        val out = mutableListOf<TransactionEntity>()
        for (i in start until lines.size) {
            val cols = split(lines[i], sep)
            if (cols.isEmpty()) continue
            fun col(idx: Int) = cols.getOrNull(idx)?.trim().orEmpty()

            val dateStr = if (iDate >= 0) col(iDate) else col(0)
            val typeRaw = if (iType >= 0) col(iType) else ""
            val cat = if (iCat >= 0) col(iCat).ifBlank { "Others" } else "Others"
            val memo = if (iMemo >= 0) col(iMemo) else ""
            val amtRaw = if (iAmt >= 0) col(iAmt) else cols.lastOrNull().orEmpty()

            val amtSigned = amtRaw.replace(",", "").toDoubleOrNull() ?: continue
            val abs = kotlin.math.abs(amtSigned)
            if (abs <= 0) continue

            val type = when {
                typeRaw.contains("income", ignoreCase = true) -> "INCOME"
                typeRaw.contains("expense", ignoreCase = true) -> "EXPENSE"
                amtSigned < 0 -> "EXPENSE"
                else -> "INCOME"
            }
            val dateMillis = parseDate(dateStr, dateFormats) ?: continue
            out += TransactionEntity(
                type = type,
                category = cat,
                amount = abs,
                dateTimestamp = dateMillis,
                memo = memo  // blank OK; UI shows category
            )
        }
        return out
    }

    private fun detectSeparator(header: String): Char {
        val commas = header.count { it == ',' }
        val tabs = header.count { it == '\t' }
        val semis = header.count { it == ';' }
        return when {
            tabs >= commas && tabs >= semis -> '\t'
            semis > commas -> ';'
            else -> ','
        }
    }

    private fun split(line: String, sep: Char): List<String> {
        if (sep == ',') return CsvFormatter.parseLinePublic(line)
        return line.split(sep)
    }

    private fun indexOf(header: List<String>, vararg needles: String): Int {
        for (n in needles) {
            val i = header.indexOfFirst { it.contains(n) }
            if (i >= 0) return i
        }
        return -1
    }

    private fun parseDate(value: String, formats: List<SimpleDateFormat>): Long? {
        for (fmt in formats) {
            try {
                return fmt.parse(value)?.time
            } catch (_: Exception) {
            }
        }
        return null
    }
}
