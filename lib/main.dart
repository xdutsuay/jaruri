import 'package:flutter/material.dart';
import 'transaction_form.dart';
import 'persi.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Money Manager App',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        visualDensity: VisualDensity.adaptivePlatformDensity,
      ),
      home: HomeScreen(),
    );
  }
}

class HomeScreen extends StatefulWidget {
  @override
  _HomeScreenState createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  List<Transaction> _transactions = [];

  @override
  void initState() {
    super.initState();
    _loadTransactions();
  }

  Future<void> _loadTransactions() async {
    print("_lt fn called");
    final transactions = await Persi.getTransactions();
    setState(() {
      _transactions = transactions;
    });
    print("_lt fn completed");
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Money Manager'),
      ),
      body: Column(
        children: [
          // Top bar showing income and expense
          Container(
            color: Colors.blue,
            padding: const EdgeInsets.symmetric(vertical: 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                Column(
                  children: [
                    const Text(
                      'Income',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 20,
                      ),
                    ),
                    Text(
                      '\$${calculateTotalAmount(true)}',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 18,
                      ),
                    ),
                  ],
                ),
                Column(
                  children: [
                    const Text(
                      'Expense',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 20,
                      ),
                    ),
                    Text(
                      '\$${calculateTotalAmount(false)}',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 18,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          // Recent transactions
          Expanded(
            child: Container(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Text(
                    'Recent Transactions',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 10),
                  // Display user-added transactions
                  Expanded(
                    child: ListView.builder(
                      itemCount: _transactions.length,
                      itemBuilder: (context, index) {
                        return ListTile(
                          title: Text(_transactions[index].description),
                          subtitle: Text('Amount: \$${_transactions[index].amount}'),
                        );
                      },
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          _navigateToTransactionForm(context);
        },
        child: const Icon(Icons.add),
      ),
    );
  }

  // Calculate total income or expense
  double calculateTotalAmount(bool isIncome) {
    double totalAmount = 0;
    for (var transaction in _transactions) {
      if (isIncome && transaction.amount > 0) {
        totalAmount += transaction.amount;
      } else if (!isIncome && transaction.amount < 0) {
        totalAmount += transaction.amount;
      }
    }
    return totalAmount;
  }

  // Navigate to transaction form
  void _navigateToTransactionForm(BuildContext context) async {
    final newTransaction = await Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => TransactionForm(onTransactionAdded: _addTransaction)),
    );
    if (newTransaction != null) {
      setState(() {
        _transactions.add(newTransaction);
      });
      await Persi.saveTransactions(_transactions);
    }
  }

  // Add transaction to the list
  void _addTransaction(Transaction newTransaction) {
    setState(() {
      _transactions.add(newTransaction);
    });
    Persi.saveTransactions(_transactions);
  }
}
