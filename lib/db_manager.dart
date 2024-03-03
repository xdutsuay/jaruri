import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';
import 'transaction_form.dart';
import 'persi.dart';// Import your Transaction model

class DatabaseManager {
  late Database _database;

  Future<void> openDatabase() async {
    _database = await openDatabase(
      join(await getDatabasesPath(), 'transactions_database.db'),
      onCreate: (db, version) {
        return db.execute(
          'CREATE TABLE transactions(id INTEGER PRIMARY KEY, description TEXT, amount REAL, date TEXT, isIncome INTEGER)',
        );
      },
      version: 1,
    );
  }

  Future<List<Transaction>> getTransactions() async {
    final List<Map<String, dynamic>> maps = await _database.query('transactions');

    return List.generate(maps.length, (i) {
      return Transaction(
        id: maps[i]['id'],
        description: maps[i]['description'],
        amount: maps[i]['amount'],
        date: maps[i]['date'],
        isIncome: maps[i]['isIncome'] == 1,
      );
    });
  }

  Future<void> deleteAllTransactions() async {
    await _database.delete('transactions');
  }
}

void main() async {
  final dbManager = DatabaseManager();
  await dbManager.openDatabase();

  // View all transactions
  List<Transaction> transactions = await dbManager.getTransactions();
  print('All Transactions:');
  transactions.forEach((transaction) {
    print(transaction);
  });

  // Delete all transactions
  await dbManager.deleteAllTransactions();
  print('All transactions deleted.');

  // Confirm deletion
  transactions = await dbManager.getTransactions();
  print('Remaining Transactions:');
  transactions.forEach((transaction) {
    print(transaction);
  });
}
