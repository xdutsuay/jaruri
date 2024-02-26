import 'package:jaruri/transaction_form.dart';
import 'persi.dart';
import 'package:flutter/material.dart';

// 1 button for settings page, 1 for about page
class SideDrawer extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Drawer(
      child: ListView(
        children: [
          // Add an icon at the top of the list view
          ListTile(
            leading: const Icon(Icons.login), // Icon for login
            title: const Text('Login'), // Text for login
            onTap: () {
              // Navigate to the login page when tapped
              Navigator.pushNamed(context, '/login');
            },
          ),
          // Divider to separate login from other options
          Divider(),
          ListTile(
            title: const Text('Settings'),
            onTap: () {
              Navigator.pushNamed(context, '/settings');
            },
          ),
          ListTile(
            title: const Text('About'),
            onTap: () {
              Navigator.pushNamed(context, '/about');
            },
          ),
        ],
      ),
    );
  }
}
