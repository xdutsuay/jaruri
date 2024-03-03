//This file helps user login via google account, then checks if the user is already registered or not
// if user is registered then it calls syncdata() to check data between local and cloud backup
//then it loads the updated data to localdb so that main.dart could sync the latest data
// write this code in lib/login.dart
import 'package:firebase_auth/firebase_auth.dart';
import 'package:google_sign_in/google_sign_in.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'persi.dart';
import 'package:flutter/material.dart';
import 'sync.dart';
import 'main.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:firebase_core/firebase_core.dart';

class Login extends StatefulWidget {
  @override
  _LoginState createState() => _LoginState();
}

class _LoginState extends State<Login> {
  final FirebaseAuth _auth = FirebaseAuth.instance;
  final GoogleSignIn googleSignIn = GoogleSignIn();

  @override
  void initState() {
    super.initState();
    initializeFirebase();
  }

  Future<void> initializeFirebase() async {
    await Firebase.initializeApp();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Login'),
      ),
      body: Center(
        child: ElevatedButton(
          onPressed: () {
            _signInWithGoogle();
          },
          child: Text('Sign in with Google'),
        ),
      ),
    );
  }

  Future<void> _signInWithGoogle() async {
    try {
      final GoogleSignInAccount? googleSignInAccount = await googleSignIn.signIn();
      final GoogleSignInAuthentication googleSignInAuthentication = await googleSignInAccount!.authentication;

      final AuthCredential credential = GoogleAuthProvider.credential(
        accessToken: googleSignInAuthentication.accessToken,
        idToken: googleSignInAuthentication.idToken,
      );

      final UserCredential authResult = await _auth.signInWithCredential(credential);
      final User? user = authResult.user;

      if (user != null) {
        // Check if the user is already registered
        final QuerySnapshot result = await FirebaseFirestore.instance
            .collection('users')
            .where('uid', isEqualTo: user.uid)
            .get();
        final List<DocumentSnapshot> documents = result.docs;

        if (documents.isEmpty) {
          // User is not registered, do something
        } else {
          // User is already registered, call syncdata()
          await syncData(); // Assuming syncData() is defined somewhere
          // Load updated data to localdb
          await loadDataToLocalDB(); // Assuming loadDataToLocalDB() is defined somewhere

          // Navigate to main.dart
          Navigator.pushReplacement(
            context,
            MaterialPageRoute(builder: (context) => MyApp()),
          );
        }
      }
    } catch (error) {
      print('Error signing in with Google: $error');
    }
  }

// You can define syncData() and loadDataToLocalDB() methods here or in separate files
}


