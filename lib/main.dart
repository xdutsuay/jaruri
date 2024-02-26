import 'package:flutter/material.dart';
import 'transaction_form.dart';
import 'persi.dart';
import 'sidedrawer.dart';


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
    final transactions = await Persi.getTransactions();
    setState(() {
      _transactions = transactions;
    });
  }

  @override
  Widget build(BuildContext context) {
    // Group transactions by date
    Map<String, List<Transaction>> groupedTransactions = {};
    _transactions.forEach((transaction) {
      String date = transaction.date; // Assuming 'date' is a property in the Transaction class
      groupedTransactions.putIfAbsent(date, () => []);
      groupedTransactions[date]!.add(transaction);
    });

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
            child: ListView.separated(
              itemCount: groupedTransactions.length,
              separatorBuilder: (context, index) => const Divider(),
              itemBuilder: (context, index) {
                String date = groupedTransactions.keys.elementAt(index);
                List<Transaction> transactions = groupedTransactions[date]!;
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      date,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 5),
                    ...transactions.map((transaction) {
                      return ListTile(
                        title: Text(transaction.description),
                        subtitle: Text('Amount: \$${transaction.amount}'),
                      );
                    }).toList(),
                  ],
                );
              },
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        
        onPressed: () {
          _navigateToTransactionForm(context);
          // //navigate to sidedrawer #this was only test code to check if side drawer is working
          //_navigateTosidedrawer(context);
        },
        child: const Icon(Icons.add),
      ),
      drawer: SideDrawer(), // Add drawer to the screen


    );
  }

  // navigate to sidedrawer method unused in this code
  void _navigateTosidedrawer(BuildContext context) async {
     // only navigate to sidedrawer when button pressed cal
    final newTransaction = await Navigator.push(
      context,
      MaterialPageRoute(builder: (context) => SideDrawer()),
    );
  }

  // Calculate total income or expense sort based on transaction.isIncome and then return sum of either income or expense
  double calculateTotalAmount(bool isIncome) {
    return _transactions
        .where((transaction) => transaction.isIncome == isIncome)
        .map((transaction) => transaction.amount)
        .fold(0, (a, b) => a + b);
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
