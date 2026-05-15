# AGENTS.md

## Cursor Cloud specific instructions

### Project overview

Jaruri is a Flutter-based personal money manager app. Core functionality: add/view/filter transactions by month, income/expense tracking, pie chart analytics. Firebase Auth + Firestore are partially implemented but commented out in `main.dart`.

### Flutter SDK version

This project requires **Flutter 3.22.3** (Dart 3.4.4). The pinned dependency versions (Firebase 4.x, win32 5.2.0, etc.) are incompatible with Flutter 3.41+. The SDK is installed at `/opt/flutter` and added to `PATH` via `~/.bashrc`.

### Running the app

- **Linux desktop** (primary target on Cloud VMs): `flutter run -d linux`
- The `libstdc++.so` symlink at `/usr/lib/x86_64-linux-gnu/libstdc++.so` must exist for clang++ to link successfully; the update script ensures this.
- **sqflite caveat**: The app uses `sqflite` for local persistence, which works on mobile but throws `databaseFactory not initialized` on Linux desktop at runtime. The UI still launches and is fully interactive, but transactions are not persisted to SQLite. To fix this, the app would need `sqflite_common_ffi` configured for desktop.
- Web build (`flutter build web`) fails due to JSObject interop incompatibilities in the older Firebase web packages.

### Lint and test

- **Lint**: `flutter analyze` — reports ~43 info/warning-level issues (no errors). All are pre-existing in the codebase.
- **Tests**: `flutter test` — the single widget test (`test/widget_test.dart`) is the default counter-app template and fails because it doesn't match the actual app. This is a pre-existing issue.

### Key commands

| Action | Command |
|---|---|
| Install deps | `flutter pub get` |
| Lint | `flutter analyze` |
| Test | `flutter test` |
| Run (Linux) | `flutter run -d linux` |
| Build (Linux) | `flutter build linux` |
