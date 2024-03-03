import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:jaruri/transaction_form.dart';
import 'sidedrawer.dart';
import 'transaction_form.dart' as Transaction;
import 'persi.dart';

class Methods  {

  // Define instance variables to hold state and transactions
  final Function(void Function()) setState;
  late List<Transaction.Transaction> _transactions = [];

  // Constructor to initialize the instance variables
  Methods(this.setState, this._transactions);

  Future<void> loadTransactions(String selectedMonth, BuildContext context) async {
    await filterTransactionsByMonth(selectedMonth);
    final transactions = await Persi.getTransactions();
    setState(() {
      _transactions = transactions;
    });
  }

  Future<void> filterTransactionsByMonth(String month) async {
    final transactions = await Persi.getTransactions();
    var filteredTransactions = transactions.where((transaction) {
      try {
        DateTime transactionDate = DateTime.parse(transaction.date);
        return DateFormat('MMMM').format(transactionDate) == month;
      } catch (e) {
        print('Error parsing transaction date: $e');
        return false;
      }
    }).toList();
  }

  void navigateTosidedrawer(BuildContext context) async {
    final newTransaction = await Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => SideDrawer()),
    );
  }

  double calculateBalance(_transactions) {
    return calculateTotalAmount(_transactions, true) - calculateTotalAmount(_transactions, false);
  }

  double calculateTotalAmount(_transactions, bool isIncome) {
    return _transactions
        .where((transaction) => transaction.isIncome == isIncome)
        .map((transaction) => transaction.amount)
        .fold(0.0, ( a,  b) =>  a +  b);
  }

  void navigateToTransactionForm(BuildContext context, Function(Transaction.Transaction) onTransactionAdded) async {
    final newTransaction = await Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => TransactionForm(onTransactionAdded: onTransactionAdded)),
    );
    if (newTransaction != null) {
      onTransactionAdded(newTransaction);
      await Persi.saveTransactions(_transactions);
    }
  }

  void addTransaction(_transactions, Transaction.Transaction newTransaction) {
    _transactions.add(newTransaction);
    Persi.saveTransactions(_transactions);
  }


}
