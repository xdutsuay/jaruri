# Jaruri

Local Android money manager (Kotlin). Tracks income and expenses on-device, with optional Indian bank/UPI/credit-card SMS import.

**License:** [Apache-2.0](LICENSE)

> Flutter prototype history remains on archived `master`. Active product line is this Gradle app (`app/`) on **`main`** (Google-free / F-Droid-ready). Optional Google Sign-In lives on the **`signin`** branch only.

## Requirements

- **JDK 17** (required for Room/KSP)
- Android Studio or command-line Android SDK
- Device/emulator API 24+

## Features (v1.6.0)

- Dashboard with **month/year toggle**, income / expense / balance
- Add, **edit**, delete (confirm), and **search/filter** transactions
- Categories, accounts (cash/bank/CC debt), monthly budgets
- Month pie charts
- CSV export **and import**
- Settings: currency selector (₹ default), date format, demo history, opt-in SMS auto-import
- SMS import: inbox, paste, sample SMS, editable preview
- **No Google account, Play Services, or network permission** on `main`

## Branches

| Branch | Purpose |
|--------|---------|
| `main` | FOSS / F-Droid build (no GMS) |
| `signin` | Same app + optional Google Sign-In / Drive auth helpers |
| `master` | Archived Flutter prototype |

## Run

```bash
./gradlew :app:installDebug
```

## F-Droid notes

- FOSS dependencies only on `main` (AndroidX, Material, Room, MPAndroidChart via JitPack)
- Store listing text under `fastlane/metadata/android/`
- Tag releases (`v1.6.0`) before submitting to [fdroiddata](https://gitlab.com/fdroid/fdroiddata)
- Repo must be **public** for F-Droid builders to clone

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
