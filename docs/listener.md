# Listener

v1: manual recorder. v2: offline transcription, transcript search, export, keyword highlights.

A manual meeting recorder inside Jaruri. You turn it on, and it stays on until you turn it off. It is never always-on.

## What it does

- **Hub → Listener** (also in the drawer). Optional title, then **Start listening**.
- Runs as a foreground service of type `microphone` with an ongoing notification that has a **Stop** action and a running timer.
- Audio: AAC-LC mono, 16 kHz, ~32 kbps (≈14 MB/hour), written to app-private
  `filesDir/listener/session-<startedAt>/seg-NNN.m4a` in **15-minute chunks**, so a crash loses at most the current chunk. Between chunks there is a gap well under a second, because the recorder is stopped and restarted.
- Sessions list: tap opens the session screen (v2); long-press to Play (external player) / Rename / Share audio / Delete. Delete removes the files first and then the row, so if any file can't be removed the row stays and you can retry.
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
| Privacy | No network permission and no network code (v2 also removes the `ACCESS_NETWORK_STATE` that WorkManager would merge in). `files/listener/` is excluded from cloud backup and device transfer (`data_extraction_rules.xml`); `backup_rules.xml` (Android ≤ 11) only includes prefs and the DB, so it already excludes the audio. Share/play goes through the existing FileProvider (`files-path name="listener"`). |

## Why the tile opens the app

Since Android 11, a foreground service started from the background can't use the microphone (the "while-in-use" restriction). On Android 14, a `microphone`-type FGS also needs the app to be eligible to use the mic when it starts. So a tile tap that starts the service from the background would either throw or record silence.

The tile therefore never starts the recorder itself. It calls `startActivityAndCollapse(PendingIntent)` on API 34+, or the `Intent` overload below 34, to open `MainActivity` with `ACTION_LISTENER_START`. `MainActivity` sets a one-shot flag and navigates to the Listener screen, and the visible screen runs the normal consent → permission → free-space → start flow. Stopping from the tile is a direct `startService(STOP)` to our own running FGS. That is allowed because the app already holds a foreground service.

## Data

Room v13 (`MIGRATION_12_13`, additive): `listener_sessions` gains `transcriptState` (`NONE`/`QUEUED`/`RUNNING`/`DONE`/`FAILED`/`CANCELLED`), `transcribedChunks` (resume pointer) and `transcriptError`; new `transcript_segments` (sessionId, chunkIndex, startMs/endMs in **session** time, text) and the FTS4 external-content index `transcript_fts` kept in sync by Room's triggers.

Room v12 adds the `listener_sessions` table through `MIGRATION_11_12`, an additive migration: id, title, startedAt, endedAt?, durationMs, segmentCount, bytes, state (`RECORDING` / `DONE` / `INTERRUPTED`), dirName, and notes (the stop reason: `USER`, `MAX_DURATION`, `LOW_STORAGE`, `RECORDER_ERROR` or `PROCESS_DIED`).

## Debug-only knobs (evidence runs)

These are read only when the app is debuggable. Write them with `run-as`:

```bash
adb shell run-as com.kaustubhtripathi.jaruri sh -c 'echo 30000 > files/listener_debug_segment_ms'  # 30 s chunks
adb shell run-as com.kaustubhtripathi.jaruri sh -c 'echo 60000 > files/listener_debug_max_ms'      # 1 min auto-stop
adb shell run-as com.kaustubhtripathi.jaruri rm files/listener_debug_segment_ms files/listener_debug_max_ms
```

Release builds ignore them (`ListenerPolicy.effectiveSegmentMs` plus the `FLAG_DEBUGGABLE` check), and the shipped defaults don't change.

## v2: offline transcription

### Engine choice: Vosk (lowest-risk option)

| | Vosk (chosen) | whisper.cpp | Android `SpeechRecognizer` |
|---|---|---|---|
| Offline | yes | yes | only with an on-device pack, and not for files |
| Integration | Maven AAR, plain Java API (`com.alphacephei:vosk-android:0.3.75`, Apache-2.0) | NDK/CMake build in this repo | can't transcribe a recorded file |
| Timestamps | per word | per segment | none |
| Speed on mid-range phones | faster than real time with the small models | small/base models are slower than real time on many phones | n/a |
| Quality | fine for search, rough for verbatim | better | n/a |

Vosk was picked because it was the least risky way to get working, timestamped, offline text into search. `SpeechEngine` is a small interface, so whisper.cpp can be dropped in later without touching the runner, the database or the UI.

**APK size:** the debug APK grows from 7.62 MB (v1) to **15.48 MB** (+7.86 MB). About 7.1 MB of that is `libvosk.so` for arm64-v8a and armeabi-v7a, stored compressed (`useLegacyPackaging = true`, about 19 MB once installed). The rest is dex for JNA and WorkManager, unminified in debug. x86/x86_64 Vosk and JNA's mips/armeabi/x86 libraries are left out, so **emulators report "transcription unavailable"**. Shipping an AAB or using ABI splits would make each install about 3.5 MB smaller.

**The model is not in the APK.** The user downloads a Vosk model .zip themselves, for example `vosk-model-small-en-in-0.4` (Indian English, about 36 MB) or `vosk-model-small-hi-0.22` (Hindi), from alphacephei.com/vosk/models, then picks it with **Listener → Import model**. The app itself never downloads anything. Import unzips to `filesDir/stt-model/current` with these checks:
- zip-slip guard;
- 3 GB size cap;
- the model must contain `am/final.mdl` and `conf/mfcc.conf`;
- the new model replaces the old one only after it validates, so a bad zip leaves the previous model in place.

**F-Droid risk:** the Vosk AAR ships prebuilt native libraries from Maven. F-Droid normally wants native code built from source, so an F-Droid build would probably need a source-built Vosk/Kaldi or a flavor without transcription. This hasn't been verified with F-Droid. A flavor split was deliberately avoided for now because it would rename `testDebugUnitTest`.

### How a session gets transcribed

1. When a session stops, it is queued automatically if a model is installed. Otherwise use **Transcribe** on the session screen.
2. `TranscriptionScheduler` runs one unique WorkManager job per session (`listener-transcribe-<id>`, KEEP, storage-not-low constraint).
3. `TranscriptionWorker` asks to run as a foreground `dataSync` job with a progress notification and **Cancel** action. If Android refuses (background-start limits), it carries on in the background.
4. `TranscriptionRunner` decodes each chunk with MediaCodec (16-bit mono PCM) and feeds Vosk. Segments are stamped in session time using the chunk durations from `ChunkDurations` (MediaMetadataRetriever). The in-app player uses the same `ChunkTimeline`, which is why tapping a line seeks to the right spot.
5. Each chunk is committed together with the resume pointer (`commitChunk`, one transaction). A stop, kill or reboot therefore loses at most the chunk in progress, and the job resumes from `transcribedChunks`.
6. **Cancel** sets `CANCELLED` and then cancels the job; finished chunks are kept and **Resume** continues from there. A system stop puts the session back to `QUEUED` and WorkManager retries. A failure (no model, decode error) shows `FAILED` with the reason, and Resume continues from the last good chunk. **Transcribe again** deletes the transcript and starts over, for example after importing a better model.
7. A session that is still recording is refused. Deleting a session cancels its job, and a worker that finishes after the delete drops whatever it wrote.

### Search

FTS4 prefix search over every transcript. Each word you type becomes `word*`, with at most 8 terms. Hits replace the sessions list and show title · date · [HH:MM:SS] and the line. Tapping a hit opens the session screen and plays from that timestamp.

### Export

On the session screen, tap **Export transcript** and choose **Markdown** or **Plain text**. The export includes the title, date, duration, a "may contain errors" note, and `[HH:MM:SS]` lines. It is written to `cache/exports/` (only the latest export is kept) and shared through the share sheet via FileProvider (`cache-path name="exports"`).

### Keyword highlights (not AI)

`SessionSummarizer` is the extension point. Today the only implementation is `HeuristicSummarizer`, labelled in the UI as **"Keyword highlights (not AI)"**. It produces:
- **possible to-dos**: lines matching English or Hinglish phrases such as "need to", "follow up", "by friday", "karna hai" or "kal tak";
- **frequent-topic lines**: lines that repeat the session's most common content words.

A real summary is **deferred**: no model small enough to ship credibly summarises on-device inside this APK, and Listener stays offline. A future on-device LLM can implement `SessionSummarizer` with `isAi = true`.

## v3: offline follow-ups (no AI, no cloud)

Fixed gaps:
- **Start during the Stop window**: a Start that arrives while the previous session is still
  finalising (Stop → service shutdown) used to be ignored. It is now queued: the service goes
  foreground at once (Android's 5-second rule) and starts a fresh session as soon as the old one
  is closed.
- **Plurals**: "part(s)", "line(s)" and similar strings are real Android plurals.
- **Cancel from the transcription notification on Android 11 and older**: WorkManager's
  `stopReason` exists only on API 31+, so the session stayed "paused". The notification's
  Cancel now goes through our own (non-exported) `TranscriptionCancelReceiver`, which marks the
  transcript CANCELLED before cancelling the work.

New (all on-device):
- **Marks (bookmarks)**: a **Mark** action on the recording notification (and a button on the
  Listener screen) saves the current offset in the session; the notification shows the count.
  On the session screen tap a mark to seek, long-press to remove. Exported with the transcript.
- **Notes**: free text per session (up to 10,000 chars), shown above the transcript, exported.
- **Edit transcript lines**: long-press a line to fix it; edited lines show an "edited" tag, the
  search index follows (FTS4 external-content triggers), and re-transcribing warns how many
  edits will be lost.
- **Delete audio, keep transcript**: frees the storage (the Listener screen now shows how much
  recordings use). Refused while recording or transcribing. Play/Share are hidden for such
  sessions and the row reads "Audio deleted · transcript kept".
- **Playback speed**: 1×, 1.25×, 1.5×, 2× (applied while playing).
- **Search date filter**: All / last 7 days / last 30 days chips.
- DB v15 (`MIGRATION_14_15`, additive): `listener_markers`, `listener_sessions.userNotes`,
  `listener_sessions.audioDeletedAt`, `transcript_segments.edited`.

Deferred, and why:
- **Tags/folders**: notes + search cover the need for now; a tag model is a schema and UI
  decision worth making with real usage.
- **Skip silence**: needs per-chunk silence analysis (decode + energy scan) and seek mapping;
  doable offline but large, and it is risky to verify without a device.
- **AI summary, speaker labels, live transcription**: no credible on-device model fits in this
  APK; see "Not done yet".
- **Cloud sync/backup**: Listener stays offline by design.

## Not done yet

- A real AI summary (see above).
- Live transcription while recording.
- Speaker labels.
- Bundling or downloading a model.
- Anything in the cloud.
- Always-on or wake-word recording.
- Capturing phone or VoIP call audio (Android doesn't allow third-party apps to do this).

## Code

- `listener/`: `ListenerPolicy` (pure rules), `ListenerSessionEntity` / `Dao`, `ListenerStorage`, `ListenerRepository`, `ListenerState`, `ListenerRecorder`, `ListenerService`, `ListenerNotifications`, `ListenerTileService`, `ListenerMarkerEntity` / `ListenerMarkerDao`, `transcribe/TranscriptionCancelReceiver`
- `ui/ListenerFragment.kt`, `viewmodel/ListenerViewModel.kt`, `res/layout/fragment_listener.xml`, `item_listener_session.xml`
- `listener/transcribe/`:
  - data: `TranscriptSegmentEntity` (+ FTS entity), `TranscriptDao`, `FtsQuery`, `ChunkTimeline`, `ChunkDurations`;
  - engine: `SpeechEngine` (interfaces), `VoskSpeechEngine`, `VoskResultParser`, `MediaCodecPcmDecoder`, `SttModelStore`;
  - jobs: `TranscriptionRunner`, `TranscriptionWorker`, `TranscriptionScheduler`;
  - `ChunkPlayer`, `TranscriptExporter`, `SessionSummarizer` / `HeuristicSummarizer`.
- `ui/ListenerSessionFragment.kt`, `viewmodel/ListenerSessionViewModel.kt`, `fragment_listener_session.xml`, `item_transcript_segment.xml`, `item_transcript_notes.xml`.
- Tests (Robolectric, SDK 33):
  - v1: `ListenerPolicyTest`, `ListenerMigrationTest` (now also 12→13), `ListenerRepositoryTest`, `ListenerServiceTest`, `ui/ListenerFragmentTest`.
  - v3: `ListenerV15MigrationTest`, `ListenerV3SmallTest` (plurals, cancel receiver, delete audio), `ui/ListenerV3UiTest`, new cases in `ListenerServiceTest` (queued start, marks).
  - v2: `FtsQueryTest`, `ChunkTimelineTest`, `TranscriptSearchTest`, `VoskResultParserTest`, `SttModelStoreTest`, `TranscriptionRunnerTest` (fake engine: resume, cancel, failure, clamping), `TranscriptionWorkerTest` (real worker/scheduler, no model), `TranscriptExporterTest`, `HeuristicSummarizerTest`, `ui/ListenerTranscriptUiTest` (search → open at timestamp, session screen).
