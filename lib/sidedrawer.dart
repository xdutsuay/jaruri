import 'package:flutter/material.dart';
import 'chart.dart';
import 'login.dart';

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
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => Login()), // Navigate to the Chart widget
              );
            },
          ),
          // Divider to separate login from other options
          Divider(),
          // List title for Upload
          ListTile(
            title: const Text('Upload'),
            onTap: () {
              Navigator.pushNamed(context, '/upload');
            },
          ),
          ListTile(
            title: const Text('Categories'),
            onTap: () {
              Navigator.pushNamed(context, '/Categories');
            },
          ),
          // List title for Chart
          ListTile(
            title: const Text('Chart'),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => Chart()), // Navigate to the Chart widget
              );
            },
          ),
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
