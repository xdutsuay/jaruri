# Jaruri Development Guide

## Cursor Cloud specific instructions

### Overview
Jaruri is a Flutter mobile app for personal finance management (expense/income tracking) with Firebase Auth (Google Sign-In) and Cloud Firestore sync.

### Development setup
- Flutter SDK is installed at `/opt/flutter` (added to PATH via `~/.bashrc`)
- Run `flutter pub get` to install dependencies
- Run `flutter analyze` for lint checks (expect info/warning level issues in existing code)
- Run `flutter test` for tests (default template test exists but does not match the app)

### Key gotchas
- Firebase config files (`google-services.json` for Android, `GoogleService-Info.plist` for iOS) are **not committed** to the repo. The app requires a Firebase project with Auth + Firestore enabled to build for mobile targets.
- Web target (`flutter run -d chrome`) may partially work without Firebase config but will not authenticate.
- The widget test in `test/widget_test.dart` is the default Flutter template test and does not match the actual app UI; it will fail.
- `pubspec.yaml` requires SDK `>=3.2.6 <4.0.0`; the installed Flutter 3.41.x satisfies this.
