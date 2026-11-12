# Listener (v1)

A manual meeting recorder inside Jaruri. You turn it on, and it stays on until you turn it off. It is never always-on.

## What it does

- **Hub → Listener** (also in the drawer). Optional title, then **Start listening**.
- Runs as a foreground service of type `microphone` with an ongoing notification that has a **Stop** action and a running timer.
- Audio: AAC-LC mono, 16 kHz, ~32 kbps (≈14 MB/hour), written to app-private
  `filesDir/listener/session-<startedAt>/seg-NNN.m4a` in **15-minute chunks**, so a crash loses at most the current chunk. Between chunks there is a gap well under a second, because the recorder is stopped and restarted.
- Sessions list: tap to play (one part, or pick a part); long-press to Play / Rename / Share audio / Delete. Delete removes the files first and then the row, so if any file can't be removed the row stays and you can retry.
- **Quick Settings tile "Listener"**: when it's recording, tapping stops it. When it's off, tapping opens the app's Listener screen, which starts the recording (see "Why the tile opens the app").

## Safety rails

| Rail | Behaviour |
|---|---|
| Consent | First start shows a one-time notice: tell people you're recording; local-only; call audio can't be captured. Stored as `listener_consent_accepted` in DataStore. |
| Mic permission | `RECORD_AUDIO` is requested on Start. If it's denied, nothing starts and a message explains why. The service checks the permission again itself. |
| Notifications (13+) | `POST_NOTIFICATIONS` is asked once per process and is optional. If it's denied, recording still works but the shade notification is hidden; the screen and the tile can still stop it. |
| Free space | Start needs ≥ 300 MB free. While recording, a watchdog that runs every 15 s stops the session if free space falls below 200 MB, and posts a notice. |
| Safety auto-stop | 4 hours (`ListenerPolicy.DEFAULT_MAX_SESSION_MS`). It posts a notice, the audio is kept, and the session is marked "Auto-stopped at the time limit". |
| No auto-start | No boot receiver, `START_NOT_STICKY`, and the service is not exported. |
| Interrupted sessions | If the process dies or is force-stopped, the row stays `RECORDING`. The next time the Listener screen opens, it is reconciled to `INTERRUPTED` with its files kept. It is **never** silently restarted. A session that the service is still creating is protected by a `Starting` state, so the reconcile step can't close it. |
| Privacy | No network permission and no network code. `files/listener/` is excluded from cloud backup and device transfer (`data_extraction_rules.xml`); `backup_rules.xml` (Android ≤ 11) only includes prefs and the DB, so it already excludes the audio. Share/play goes through the existing FileProvider (`files-path name="listener"`). |

## Why the tile opens the app

Since Android 11, a foreground service started from the background can't use the microphone (the "while-in-use" restriction). On Android 14, a `microphone`-type FGS also needs the app to be eligible to use the mic when it starts. So a tile tap that starts the service from the background would either throw or record silence.

The tile therefore never starts the recorder itself. It calls `startActivityAndCollapse(PendingIntent)` on API 34+, or the `Intent` overload below 34, to open `MainActivity` with `ACTION_LISTENER_START`. `MainActivity` sets a one-shot flag and navigates to the Listener screen, and the visible screen runs the normal consent → permission → free-space → start flow. Stopping from the tile is a direct `startService(STOP)` to our own running FGS. That is allowed because the app already holds a foreground service.

## Data

Room v12 adds the `listener_sessions` table through `MIGRATION_11_12`, an additive migration: id, title, startedAt, endedAt?, durationMs, segmentCount, bytes, state (`RECORDING` / `DONE` / `INTERRUPTED`), dirName, and notes (the stop reason: `USER`, `MAX_DURATION`, `LOW_STORAGE`, `RECORDER_ERROR` or `PROCESS_DIED`).

## Debug-only knobs (evidence runs)

These are read only when the app is debuggable. Write them with `run-as`:

```bash
adb shell run-as com.kaustubhtripathi.jaruri sh -c 'echo 30000 > files/listener_debug_segment_ms'  # 30 s chunks
adb shell run-as com.kaustubhtripathi.jaruri sh -c 'echo 60000 > files/listener_debug_max_ms'      # 1 min auto-stop
adb shell run-as com.kaustubhtripathi.jaruri rm files/listener_debug_segment_ms files/listener_debug_max_ms
```

Release builds ignore them (`ListenerPolicy.effectiveSegmentMs` plus the `FLAG_DEBUGGABLE` check), and the shipped defaults don't change.

## Not in v1

Transcription (planned for v2: offline Whisper or Vosk), summaries or action items, anything in the cloud, always-on or wake-word recording, and capturing phone or VoIP call audio (Android doesn't allow third-party apps to do this).

## Code

- `listener/`: `ListenerPolicy` (pure rules), `ListenerSessionEntity` / `Dao`, `ListenerStorage`, `ListenerRepository`, `ListenerState`, `ListenerRecorder`, `ListenerService`, `ListenerNotifications`, `ListenerTileService`
- `ui/ListenerFragment.kt`, `viewmodel/ListenerViewModel.kt`, `res/layout/fragment_listener.xml`, `item_listener_session.xml`
- Tests: `ListenerPolicyTest`, `ListenerMigrationTest`, `ListenerRepositoryTest`, `ListenerServiceTest` (Robolectric, SDK 33), `ui/ListenerFragmentTest`
