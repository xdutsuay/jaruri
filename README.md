# Jaruri

Local Android money manager (Kotlin). Tracks income and expenses on-device, with optional Indian bank/UPI/credit-card SMS import.

> Flutter prototype history remains on the archived `master` / tags. Active product line is this Gradle app (`app/`).

## Requirements

- **JDK 17** (required for Room/KSP)
- Android Studio or command-line Android SDK
- Device/emulator API 24+

## Features (v1.3.1)

- Dashboard with **month/year toggle**, income / expense / balance (color-coded)
- Add, **edit**, delete (confirm), and **search/filter** transactions
- Categories (incl. Shopping, Credit Card, Transfer)
- Month pie charts
- CSV export (RFC4180 escaping)
- Settings: **currency selector** (₹ default), date format, auto-load demo history when empty, opt-in SMS auto-import
- **Demo history**: ~4 months of sample income, expenses, and credit-card spends on first launch (never auto-deleted; Settings can reload only if the ledger is empty)
- SMS import: inbox, paste, **Load sample SMS** (bank + credit card), editable preview
- Google sign-in is **optional** (drawer → Link Google). Ledger does **not** sync to the cloud yet.

## Run

```bash
# From repo root (Kotlin tree)
./gradlew :app:installDebug
```

Or open this folder in Android Studio → **Load Gradle Project** → Run on a device.

## Test SMS without bank messages

1. Drawer → **Import from SMS**
2. Tap **Load sample SMS** → **Parse pasted SMS**
3. Review proposals (including credit-card spend/payment) → **Confirm import**

Unit tests:

```bash
./gradlew :app:testDebugUnitTest --tests com.example.moneymanager.utils.SmsParserTest
./gradlew :app:testDebugUnitTest --tests com.example.moneymanager.utils.SmsCategorizerTest
./gradlew :app:testDebugUnitTest
```

## Coming later (Pass 2)

Budgets, multiple accounts / per-card debt balances, recurring transactions, real ledger backup/restore.

## Package

- applicationId: `com.kaustubhtripathi.jaruri`
- First install of this ID replaces the old `com.example.moneymanager` debug app (uninstall old package if signatures conflict).
