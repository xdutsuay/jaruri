import 'package:flutter/material.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Your App Title',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        visualDensity: VisualDensity.adaptivePlatformDensity,
      ),
      home: MyHomePage(),
    );
  }
}

class MyHomePage extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Your App Title'),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text('Your Home Page Content Here'),
            PlaceYourDesignHereWidget(),
          ],
        ),
      ),
    );
  }
}

class PlaceYourDesignHereWidget extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      width: 390,
      height: 844,
      child: Stack(
        children: <Widget>[
          Positioned(
            top: 0,
            left: 0,
            child: Container(
              width: 390,
              height: 844,
              decoration: BoxDecoration(
                image: DecorationImage(
                  image: AssetImage('assets/images/Image2.png'),
                  fit: BoxFit.fitWidth,
                ),
              ),
            ),
          ),
          Positioned(
            top: 523,
            left: 64,
            child: Text(
              'Place your design here',
              textAlign: TextAlign.left,
              style: TextStyle(
                color: Color.fromRGBO(255, 255, 255, 1),
                fontFamily: 'SF Pro Display',
                fontSize: 80,
                letterSpacing: 0,
                fontWeight: FontWeight.normal,
                height: 0.8625,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
