//This is called from login.dart or from main.dart if user is already logged in

import 'package:cloud_firestore/cloud_firestore.dart';
import 'persi.dart'; // Assuming you have persi.dart for local database operations

Future<void> syncData() async {
  // Implement logic to synchronize data between local and cloud backup
  // For example, you can compare timestamps or use other strategies to determine which data to update
}

Future<void> loadDataToLocalDB() async {
  try {
    final QuerySnapshot querySnapshot = await FirebaseFirestore.instance.collection('your_collection').get();
    final List<Map<String, dynamic>> documents = querySnapshot.docs.map((doc) => doc.data() as Map<String, dynamic>).toList();

    // Save documents to local database using Persi
    await Persi.saveData(documents);
  } catch (error) {
    print('Error loading data to local database: $error');
  }
}
