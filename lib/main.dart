import 'package:flutter/material.dart';
import 'transaction_form.dart';
import 'persi.dart';
import 'sidedrawer.dart';
import 'package:intl/intl.dart'; // Import the intl package to format dates
import  'methods.dart';

void main() {
  runApp(MyApp()); 
}
class MyApp extends StatelessWidget {
  const MyApp({super.key});

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
  late String _selectedMonth = DateFormat('month').format(DateTime.now());
  late Methods methods;
  List<Transaction> _transactions = [];


  @override // modify this function to call _filterTransactionsByMonth
  void initState() {
    super.initState();
    _selectedMonth = DateFormat('MMMM').format(DateTime.now());
    methods = Methods(setState, _transactions);
    //methods.loadTransactions(_selectedMonth, context);
    methods.filterTransactionsByMonth(_selectedMonth);
  }

  @override
  Widget build(BuildContext context) {
    // Group transactions by date
    Map<String, List<Transaction>> groupedTransactions = {};
    _transactions.forEach((transaction) {
      print(transaction.amount);
      String date = transaction.date; // Assuming 'date' is a property in the Transaction class
      groupedTransactions.putIfAbsent(date, () => []);
      groupedTransactions[date]!.add(transaction);
    });

    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            const Text(
              'Money Manager',
              style: TextStyle(
                color: Colors.black,
                fontSize: 18,
              ),
            ),
            const SizedBox(width: 8),
            DropdownButton<String>(
              value: _selectedMonth,
              icon: const Icon(Icons.arrow_drop_down),
              iconSize: 10,
              elevation: 8,
              style: const TextStyle(color: Colors.black),
              onChanged: (String? newValue) {
                if (newValue != null) {
                  setState(() {
                    _selectedMonth = newValue;
                    methods.filterTransactionsByMonth(newValue);
                  });
                }
              },
              isExpanded: false, // Set isExpanded to false
              items: <String>['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']
                  .map<DropdownMenuItem<String>>((String value) {
                return DropdownMenuItem<String>(
                  value: value,
                  child: Text(value),
                );
              }).toList(),
            ),


          ],
        ),
        actions: [
          IconButton(
            onPressed: () {
              // Add your sync functionality here
            },
            icon: const Icon(Icons.sync),
          ),
        ],
      ),
      body: Column(
        children: [
          // Top bar showing income, expense, and balance
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
                      '\$${methods.calculateTotalAmount(_transactions,true)}',
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
                      'Balance',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 20,
                      ),
                    ),
                    Text(
                      '\$${methods.calculateBalance(_transactions)}',
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
                      '\$${methods.calculateTotalAmount(_transactions,false)}',
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
          methods.navigateToTransactionForm(context, (transaction) {
            methods.addTransaction(_transactions, transaction);
          });
        },
        child: const Icon(Icons.add),
      ),
      drawer: SideDrawer(), // Add drawer to the screen
    );


  }

}
