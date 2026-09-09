# Money Manager

> **Note**: This project has reached the end of active development. The core features (including dual-path authentication, CSV export, dynamic charts, and settings) are complete and stable.

Money Manager is an Android application designed to help users track their income and expenses. It provides a simple and intuitive interface for managing personal finances.

## Features

*   **Authentication:** Dual-path authentication (Primary: Google Sign-In, Secondary: Google Drive plain-text fallback).
*   **Dashboard:** A quick overview of your income, expenses, and current balance.
*   **Transactions:** Add, view, and manage your income and expense transactions.
*   **Categories:** Organize your transactions by creating and managing custom categories for both income and expenses.
*   **Charts:** Visualize your financial data with interactive pie charts for both income and expenses.
*   **Export:** Export your transaction data to a CSV file.
*   **Settings:** Customize the app to your preferences.
*   **SMS import:** Parse common Indian bank/UPI SMS into editable transactions (requires `READ_SMS` on Android, or paste SMS as a fallback). Nothing is saved until you confirm the preview.

## SMS import

1. Open **Add Transaction** → **Import from SMS**, or use the drawer item **Import from SMS**.
2. Tap **Read SMS inbox** (grant `READ_SMS` when prompted) or paste SMS text and tap **Parse pasted SMS**.
3. Select messages → **Preview selected** → edit amount / type / category / memo → **Confirm import**.
4. Duplicates are skipped when the memo already contains the same `[sms:<hash>]` tag.

Sample SMS bodies that the parser understands:

```
Rs.1,250.00 debited from A/c XX4521 on 08-09-2026 at AMAZON. Avl Bal Rs.12,340.50
INR 5000.00 credited to your A/c XX7788 on 01-Sep-26. Info: SALARY.
₹249.00 spent on UPI to SWIGGY using PhonePe. UPI Ref 123456789012.
You have received Rs 1,000.00 from RAHUL SHARMA via UPI. Ref: 987654321098.
```

Unit tests: `./gradlew :app:testDebugUnitTest --tests com.example.moneymanager.utils.SmsParserTest`

## Screenshots

(Coming Soon)

## Getting Started

To get started with the Money Manager app, you'll need to have Android Studio installed. You can then clone the repository and open it in Android Studio.

```
https://github.com/your-username/money-manager.git
```

Once the project is open, you can build and run the app on an Android emulator or a physical device. Note that the project is pinned to JDK 17 for build stability.

## Contributing

This project is no longer in active development. Forking and independent continuation are welcome!
