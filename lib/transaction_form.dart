import 'package:flutter/material.dart';

class Transaction {
  final String description;
  final double amount;
  final bool isIncome;
  final String category;
  final String modeOfPayment;
  final String remarks;

  Transaction({
    required this.description,
    required this.amount,
    required this.isIncome,
    required this.category,
    required this.modeOfPayment,
    required this.remarks,
  });

  // Define a factory method to deserialize JSON into a Transaction object
  factory Transaction.fromJson(Map<String, dynamic> json) {
    return Transaction(
      description: json['description'],
      amount: json['amount'],
      isIncome: json['isIncome'],
      category: json['category'],
      modeOfPayment: json['modeOfPayment'],
      remarks: json['remarks'],
    );
  }
  Map<String, dynamic> toMap() {
    return {
      'description': description,
      'amount': amount,
      'isIncome': isIncome ? 1 : 0,
      'category': category,
      'modeOfPayment': modeOfPayment,
      'remarks': remarks,
    };
  }
}

class TransactionForm extends StatefulWidget {
  final Function(Transaction) onTransactionAdded;

  TransactionForm({required this.onTransactionAdded});

  @override
  _TransactionFormState createState() => _TransactionFormState();
}

class _TransactionFormState extends State<TransactionForm> {
  bool _isIncome = true; // Default to income
  late TextEditingController _amountController;
  late TextEditingController _categoryController;
  late TextEditingController _modeOfPaymentController;
  late TextEditingController _remarksController;

  @override
  void initState() {
    super.initState();
    _amountController = TextEditingController();
    _categoryController = TextEditingController();
    _modeOfPaymentController = TextEditingController();
    _remarksController = TextEditingController();
  }

  @override
  void dispose() {
    _amountController.dispose();
    _categoryController.dispose();
    _modeOfPaymentController.dispose();
    _remarksController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Add Transaction'),
      ),
      body: SingleChildScrollView( // previously i was using padding so overflow was coming with keyboard
        padding: const EdgeInsets.all(16.0),
        child: Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Input fields for transaction details (omitted for brevity)
              const Text(
                'Type:',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              Row(
                children: [
                  Radio(
                    value: true,
                    groupValue: _isIncome,
                    onChanged: (value) {
                      setState(() {
                        _isIncome = value as bool;
                      });
                    },
                  ),
                  const Text('Income'),
                  const SizedBox(width: 20),
                  Radio(
                    value: false,
                    groupValue: _isIncome,
                    onChanged: (value) {
                      setState(() {
                        _isIncome = value as bool;
                      });
                    },
                  ),
                  const Text('Expense'),
                ],
              ),
              const SizedBox(height: 20),
              const Text(
                'Amount:',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              TextField(
                controller: _amountController,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  hintText: 'Enter amount',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 20),
              const Text(
                'Category:',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              TextField(
                controller: _categoryController,
                decoration: const InputDecoration(
                  hintText: 'Enter category',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 20),
              const Text(
                'Mode of Payment:',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              TextField(
                controller: _modeOfPaymentController,
                decoration: const InputDecoration(
                  hintText: 'Enter mode of payment',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 20),
              const Text(
                'Remarks:',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              TextField(
                controller: _remarksController,
                decoration: const InputDecoration(
                  hintText: 'Enter remarks',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                onPressed: () {
                  // Save the transaction
                  final transaction = Transaction(
                    description: 'Sample Transaction', // Change to actual description
                    amount: double.parse(_amountController.text),
                    isIncome: _isIncome,
                    category: _categoryController.text,
                    modeOfPayment: _modeOfPaymentController.text,
                    remarks: _remarksController.text,
                  );
                  widget.onTransactionAdded(transaction); // Call the callback to add transaction
                  // Clear text field controllers
                  _amountController.clear();
                  _categoryController.clear();
                  _modeOfPaymentController.clear();
                  _remarksController.clear();
                  // Show a snackbar to indicate the transaction is saved
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Transaction saved')),
                  );
                },
                child: const Text('Save'),
              ),
            ],
          ),
        ),
      ),resizeToAvoidBottomInset: false,
    );
  }
}
