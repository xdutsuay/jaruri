# Jaruri

Local Android money and time tracker (Kotlin). Tracks income/expenses and manual time spent on-device, with optional Indian bank/UPI/credit-card SMS import.

**License:** [Apache-2.0](LICENSE)

> Flutter prototype history remains on archived `master`. Active product line is this Gradle app (`app/`) on **`main`** (Google-free / F-Droid-ready). Optional Google Sign-In lives on the **`signin`** branch only.

## Requirements

- **JDK 17** (required for Room/KSP)
- Android Studio or command-line Android SDK
- Device/emulator API 24+

## Features (v1.7.0)

- Feature hub to open Money or Time
- Dashboard with **month/year toggle**, income / expense / balance
- Add, **edit**, delete (confirm), and **search/filter** transactions
- Categories, accounts (cash/bank/CC debt), monthly budgets
- Month pie charts
- CSV export **and import**
- Settings: currency selector (₹ default), date format, demo history, opt-in SMS auto-import
- SMS import: inbox, paste, sample SMS, editable preview
- **Time**: manual time entries with category, duration, date, and monthly totals
- **Listener** (unreleased, on `feature/listener`): manual meeting recorder: start it, and it records until you stop it (4 h safety stop). Microphone foreground service with a Stop notification, Quick Settings tile, 15-min local audio chunks, sessions list. Audio stays on the phone and is excluded from backups. v2 adds offline transcription (Vosk, with a model you import), transcript search that jumps to the moment, Markdown/text export, and keyword highlights (not AI). See [docs/listener.md](docs/listener.md)
- **Quick expense & accounts** (on `feature/listener`): Quick Settings tile / launcher shortcut sheet for amount → category → account → note, signed per-account balances, account picker on Add/Edit. See [docs/quick-expense-and-accounts.md](docs/quick-expense-and-accounts.md)
- **Tunnel** (unreleased): local DNS-filter VPN with per-app Bypass / best-effort Block, a domain blocklist (StevenBlack bundled by default, or a licence-clean permissive list; `-Pjaruri.tunnel.unifiedList=false` leaves StevenBlack out), and a 7-day lookup log. See [docs/tunnel.md](docs/tunnel.md)
- **No Google account or Play Services.** The app holds INTERNET only so Tunnel can forward allowed DNS lookups; no other code touches the network (enforced by `NoNetworkOutsideTunnelTest`)

## Branches

| Branch | Purpose |
|--------|---------|
| `main` | FOSS / F-Droid build (no GMS) |
| `signin` | Same app + optional Google Sign-In / Drive auth helpers |
| `master` | Archived Flutter prototype |

## Run

```bash
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest
```

## F-Droid notes

- FOSS dependencies only on `main` (AndroidX, Material, Room, MPAndroidChart via JitPack)
- Store listing text under `fastlane/metadata/android/`
- Tag releases (`v1.6.2`) before submitting to [fdroiddata](https://gitlab.com/fdroid/fdroiddata)
- Repo must be **public** for F-Droid builders to clone
- F-Droid builds must pass `-Pjaruri.tunnel.unifiedList=false` (`gradleprops`): the StevenBlack list has non-free sources. See `metadata/fdroid-template.md`

## Test SMS without bank messages

1. Drawer → **Import from SMS**
2. Tap **Load sample SMS** → **Parse pasted SMS**
3. Review proposals → **Confirm import**

```bash
./gradlew :app:testDebugUnitTest --tests com.example.moneymanager.utils.SmsParserTest
./gradlew :app:testDebugUnitTest
```

## Package

- applicationId: `com.kaustubhtripathi.jaruri`
