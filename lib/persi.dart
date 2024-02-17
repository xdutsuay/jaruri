import 'dart:convert';
import 'package:sqflite/sqflite.dart';
import 'transaction_form.dart' as Transaction; // Importing the file where Transaction class is defined

class Persi {
  static Future<Database> _openDatabase() async {
    return openDatabase(
      'transactions.db',
      version: 1,
      onCreate: (db, version) {
        return db.execute(
          'CREATE TABLE transactions(id INTEGER PRIMARY KEY, description TEXT, amount REAL, isIncome INTEGER, category TEXT, modeOfPayment TEXT, remarks TEXT)',
        );
      },
    );
  }

  static Future<List<Transaction.Transaction>> getTransactions() async {
    final Database db = await _openDatabase();
    final List<Map<String, dynamic>> maps = await db.query('transactions');
    return List.generate(
      maps.length,
          (i) => Transaction.Transaction(
        description: maps[i]['description'],
        amount: maps[i]['amount'],
        isIncome: maps[i]['isIncome'] == 1,
        category: maps[i]['category'],
        modeOfPayment: maps[i]['modeOfPayment'],
        remarks: maps[i]['remarks'],
      ),
    );
  }

  static Future<void> saveTransactions(List<Transaction.Transaction> transactions) async {
    final Database db = await _openDatabase();
    await db.transaction((txn) async {
      for (final transaction in transactions) {
        await txn.insert(
          'transactions',
          transaction.toMap(),
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
      }
    });
  }
}
