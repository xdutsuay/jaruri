package com.example.moneymanager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.moneymanager.MainActivity
import com.example.moneymanager.R
import com.example.moneymanager.data.AppDatabase
import com.example.moneymanager.data.SettingsRepository
import com.example.moneymanager.viewmodel.MainViewModel
import com.example.moneymanager.utils.TransactionAccounting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class SummaryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val txs = AppDatabase.getDatabase(app).transactionDao().getAllActiveList()
            val symbol = SettingsRepository(app).currencySymbol.first()
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val monthList = MainViewModel.filterByMonth(txs, year, month)
            val totals = TransactionAccounting.sumTotals(monthList)
            val balance = totals.balance
            val open = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                app,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            for (id in appWidgetIds) {
                val views = RemoteViews(app.packageName, R.layout.widget_summary)
                views.setTextViewText(R.id.tv_widget_income, "$symbol${"%.0f".format(totals.income)}")
                views.setTextViewText(R.id.tv_widget_expense, "$symbol${"%.0f".format(totals.expense)}")
                views.setTextViewText(R.id.tv_widget_balance, "$symbol${"%.0f".format(balance)}")
                views.setOnClickPendingIntent(R.id.widget_root, pending)
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }
}
