//this file is called from side_drawer.dart
//this file shows a pie chart of the transactions grouped by category
// under the chart, it shows the total expense grouped by category
// write this code in lib/chart.dart
import 'package:flutter/material.dart';
import 'package:pie_chart/pie_chart.dart';
import 'persi.dart';
import 'transaction_form.dart' as Transaction;
import 'package:intl/intl.dart';

class Chart extends StatefulWidget {
  @override
  _ChartState createState() => _ChartState();
}

class _ChartState extends State<Chart> {
  late Map<String, double> _data = {};
  late List<Transaction.Transaction> _transactions = [];
  late String _selectedMonth = DateFormat('MMMM').format(DateTime.now());

  @override
  void initState() {
    super.initState();
    _loadTransactions();
  }

  Future<void> _loadTransactions() async {
    final transactions = await Persi.getTransactions();
    setState(() {
      _transactions = transactions;
      _groupTransactionsByCategory();
    });
  }

  void _groupTransactionsByCategory() {
    _data = {};
    _transactions.forEach((transaction) {
      if (_data.containsKey(transaction.category)) {
        _data[transaction.category] = _data[transaction.category]! + transaction.amount;
      } else {
        _data[transaction.category] = transaction.amount;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Chart'),
      ),
      body: Column(
        children: [
          _buildMonthDropdown(),
          Expanded(
            child: PieChart(
              dataMap: _data,
              chartType: ChartType.ring,
              chartRadius: MediaQuery.of(context).size.width / 2,

            ),
          ),
          _buildTotalExpense(),
        ],
      ),
    );
  }

  Widget _buildMonthDropdown() {
    return DropdownButton<String>(
      value: _selectedMonth,
      icon: const Icon(Icons.arrow_drop_down),
      iconSize: 24,
      elevation: 16,
      style: const TextStyle(color: Colors.blue),
      onChanged: (String? newValue) {
        if (newValue != null) {
          setState(() {
            _selectedMonth = newValue;
            _filterTransactionsByMonth(newValue);
          });
        }
      },
      items: <String>[
        'January', 'February', 'March', 'April', 'May', 'June',
        'July', 'August', 'September', 'October', 'November', 'December'
      ].map<DropdownMenuItem<String>>((String value) {
        return DropdownMenuItem<String>(
          value: value,
          child: Text(value),
        );
      }).toList(),
    );
  }

  void _filterTransactionsByMonth(String month) {
    setState(() {
      _transactions = _transactions.where((transaction) {
        return DateFormat('MMMM').format(DateTime.parse(transaction.date)) == month;
      }).toList();
      _groupTransactionsByCategory();
    });
  }

  Widget _buildTotalExpense() {
    double totalExpense = 0;
    _transactions.forEach((transaction) {
      if (!transaction.isIncome) {
        totalExpense += transaction.amount;
      }
    });

    return Container(
      padding: const EdgeInsets.all(16),
      child: Text(
        'Total Expense: $totalExpense',
        style: TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}
