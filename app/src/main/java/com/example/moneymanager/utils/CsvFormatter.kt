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
            sb.append("${t.type},${t.category},${t.amount},$dateStr,${t.memo.replace(",", " ")}\n")
        }
        return sb.toString()
    }
}
