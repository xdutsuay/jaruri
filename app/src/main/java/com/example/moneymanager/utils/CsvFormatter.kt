package com.example.moneymanager.utils

import com.example.moneymanager.data.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvFormatter {
    fun format(transactions: List<TransactionEntity>, datePattern: String = "yyyy-MM-dd"): String {
        val sb = StringBuilder()
        sb.append("Type,Category,Amount,Date,Memo\n")

        val dateFormat = try {
            SimpleDateFormat(datePattern, Locale.getDefault())
        } catch (e: Exception) {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }

        for (t in transactions) {
            val dateStr = dateFormat.format(Date(t.dateTimestamp))
            val row = listOf(
                t.type,
                t.category,
                t.amount.toString(),
                dateStr,
                t.memo
            ).joinToString(",") { escapeCsvField(it) }
            sb.append(row).append('\n')
        }
        return sb.toString()
    }

    /**
     * Parses Jaruri export CSV (Type,Category,Amount,Date,Memo).
     * Skips header and invalid rows.
     */
    fun parse(csv: String, datePattern: String = "yyyy-MM-dd"): List<TransactionEntity> {
        val dateFormat = try {
            SimpleDateFormat(datePattern, Locale.getDefault())
        } catch (e: Exception) {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
        dateFormat.isLenient = true

        val fallbackFormats = listOf(
            dateFormat,
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        )

        val lines = csv.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return emptyList()

        val start = if (lines.first().startsWith("Type", ignoreCase = true)) 1 else 0
        val result = mutableListOf<TransactionEntity>()

        for (i in start until lines.size) {
            val fields = parseCsvLine(lines[i])
            if (fields.size < 4) continue
            val type = fields[0].trim().uppercase()
            if (type != "INCOME" && type != "EXPENSE") continue
            val category = fields[1].trim().ifEmpty { "Other" }
            val amount = fields[2].trim().toDoubleOrNull() ?: continue
            if (amount <= 0) continue
            val dateMillis = parseDate(fields[3].trim(), fallbackFormats) ?: continue
            val memo = fields.getOrNull(4)?.trim().orEmpty()
            result.add(
                TransactionEntity(
                    type = type,
                    category = category,
                    amount = amount,
                    dateTimestamp = dateMillis,
                    memo = memo
                )
            )
        }
        return result
    }

    private fun parseDate(value: String, formats: List<SimpleDateFormat>): Long? {
        for (fmt in formats) {
            try {
                return fmt.parse(value)?.time
            } catch (_: Exception) {
                // try next
            }
        }
        return null
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }

    private fun escapeCsvField(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
