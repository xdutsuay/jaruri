import 'dart:convert';
import 'dart:io'; // Import this for File class
import 'package:sqflite/sqflite.dart';
import 'transaction_form.dart' as Transaction; // Importing the file where Transaction class is defined

class Persi {
  static Future<Database> _openDatabase() async {
    return openDatabase(
      'transactions.db',
      version: 1,
      onCreate: (db, version) {
        return db.execute(
          'CREATE TABLE transactions(id INTEGER PRIMARY KEY, description TEXT, amount REAL, isIncome INTEGER, category TEXT, modeOfPayment TEXT, remarks TEXT, date TEXT)',
        );
      },
    );
  }

  static Future<List<Transaction.Transaction>> getTransactions() async {
    final Database db = await _openDatabase();
    final List<Map<String, dynamic>> maps = await db.query('transactions');
    return List.generate(
      maps.length,
          (i) {
        final map = maps[i];
        return Transaction.Transaction(
          description: map['description'] ?? '', // Provide a default empty string if null
          amount: map['amount'] ?? 0.0, // Provide a default value if null
          isIncome: map['isIncome'] == 1,
          category: map['category'] ?? '', // Provide a default empty string if null
          modeOfPayment: map['modeOfPayment'] ?? '', // Provide a default empty string if null
          remarks: map['remarks'] ?? '', // Provide a default empty string if null
          date: map['date'] ?? '', // Provide a default empty string if null
        );
      },
    );
  }

  static Future<void> saveTransactions(List<Transaction.Transaction> transactions) async {
    final Database db = await _openDatabase();
    await db.transaction((txn) async {
      for (final transaction in transactions) {
        await txn.insert(
          'transactions',
          {
            'description': transaction.description,
            'amount': transaction.amount,
            'isIncome': transaction.isIncome ? 1 : 0,
            'category': transaction.category,
            'modeOfPayment': transaction.modeOfPayment,
            'remarks': transaction.remarks,
            'date': DateTime.now().toIso8601String(), // Save current date without seconds
          },
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
      }
    });
  }

  static Future<void> saveToJsonLocal(List<Transaction.Transaction> transactions) async {
    final String data = jsonEncode(transactions);
    await File('transactions.json').writeAsString(data);
  }
  static Future<void> saveData(List<Map<String, dynamic>> documents) async {
    // Convert the list of documents to a list of Transaction objects
    final List<Transaction.Transaction> transactions = documents.map((doc) {
      return Transaction.Transaction(
        description: doc['description'],
        amount: doc['amount'],
        isIncome: doc['isIncome'] == 1,
        category: doc['category'],
        modeOfPayment: doc['modeOfPayment'],
        remarks: doc['remarks'],
        date: doc['date'],
      );
    }).toList();

    // Save the transactions to the local database
    await saveTransactions(transactions);
  }
}

