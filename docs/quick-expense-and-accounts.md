# Quick expense, categories and accounts

All offline. No new permissions, no database migration (accounts and `transactions.accountId`
already existed; this work wires them through the UI).

## Quick expense sheet
Inspired by a "back double-tap → amount → category → remark → row in a sheet" reel, but saved
into jaruri's own `transactions` table.

- **Open it from:** the Quick Settings tile "Quick add", the launcher shortcut "Quick expense"
  (long-press the jaruri icon; appears after jaruri has been opened once), or any automation
  that can open an app shortcut or start an activity:
  - **realme / ColorOS:** if your build has *Settings → Additional settings → Gestures / Back tap*
    (or *Smart sidebar*), point it at the "Quick expense" shortcut. Not every realme build has
    back-tap.
  - **Samsung:** Good Lock → RegiStar → Back-tap action → *Open app shortcut* → jaruri "Quick
    expense"; or Modes and Routines → *Open app shortcut*.
  - **Tasker / MacroDroid:** Send Intent / Launch activity with action
    `com.kaustubhtripathi.jaruri.action.QUICK_EXPENSE` (package `com.kaustubhtripathi.jaruri`).
- The sheet: amount (decimal keypad, focused), category chips (the last one used is preselected),
  account chips (last used preselected, or "No account"), optional note, **Save**.
- Saves an EXPENSE dated now, through the same path as the in-app add (account balance moves,
  the note teaches the category for later SMS imports). A bar shows "Saved ₹50 · Food" with
  **Undo** for 4 seconds, then the sheet closes. Undo removes the row and restores the balance,
  and leaves the form filled so you can fix and save again.
- **Full form** opens the in-app Quick add (income, templates).
- **Safety:** the public action only opens the form; nothing is saved without tapping Save.
  The sheet is not shown over the lock screen; from a locked phone the tile asks you to unlock first.
- Amount rules: digits with up to 2 decimals; commas are grouping (`1,25,000.50` is fine);
  0, negatives, letters and more than 99,999,999.99 are refused with a message.

## Bug: the category picker showed 5 options or 18
Root cause: the Home list's "categorise" dialog read `MainViewModel.expenseCategories.value`.
That LiveData (`Flow.asLiveData()`) only has a value after some screen has observed it, so on a
fresh start it was `null` and the dialog fell back to a hard-coded list of 5
(`Others, Food, Shopping, Bills, Transfer`). After visiting Add or Quick add it showed the real
list (18 by default). Fix:
- `MainViewModel.categoryNames(type)` reads the repository directly; Home uses it.
- `CategoryChoices.forPicker` is the one rule for every picker: the user's list, de-duplicated,
  plus the row's own category if it was removed (so opening Edit no longer silently changes a
  row's category to the first in the list).
- A test fails if any screen hard-codes its own category list again.

## Accounts
- **Types:** Bank account, Credit card, Cash, **UPI wallet / prepaid** (new; behaves like cash).
- **Signed balances:** plus = money you have, minus = money you owe. Cards store "owed" and show
  as minus; an **overpaid card now goes below zero** (a credit) instead of being clamped at 0,
  which also made Undo/Edit inexact. Banks can go negative (overdraft). The Accounts screen's
  runway line now counts wallets and card credit.
- **Opening balance:** set when you add an account (card: the amount owed now); editing the
  account adjusts it.
- **Which account paid:** Add/Edit has an **Account** picker ("No account" allowed). New entries
  default to the last account you used (shared with the quick expense sheet). Editing a row keeps
  its payee, UPI reference, RRN and refund links (previously an edit rebuilt the row and dropped
  them) and moves the amount between accounts correctly.
- **Lists:** each transaction row shows its account after the note.
- **SMS import:** already maps by the last 4 digits of the card or account (creating
  "HDFC Card XX1234" etc. when new). New: when an SMS has no digits, it is matched by bank name
  only if exactly one account has that name or learned bank hint; otherwise it is left without
  an account rather than guessed.
- **Transfers** between accounts were already there (Accounts → Transfer between accounts);
  card as destination lowers what you owe.

## Tests
`QuickExpenseInputTest`, `QuickExpenseStoreTest`, `QuickExpenseActivityTest`,
`CategoryChoicesTest`, `CategoryListConsistencyTest`, `AccountChoicesTest`,
`AccountLedgerDbTest`, `ui/AddTransactionAccountTest`.

## Device checklist (realme)
1. Pull down Quick Settings, add **Quick add**, tap it: the sheet slides up with the keypad open
   on Amount; the last category and account are preselected.
2. Lock the phone, tap the tile from the lock screen: it asks to unlock, then opens the sheet.
3. Enter 50, pick Transportation, note "cab", Save: "Saved ₹50 · Transportation", sheet closes
   after ~4 s; the row is on Home with "cab · <account>"; the account balance dropped by 50.
4. Again, then tap **Undo**: the row disappears, the balance is back, the form stays filled.
5. Long-press the jaruri icon → **Quick expense** opens the same sheet. If ColorOS has back-tap
   (or use a Tasker intent), bind it and check it opens.
6. Fresh start (swipe jaruri away), open Home, tap a "needs review" row's categorise: the dialog
   lists all your categories (18 by default), not 5.
7. Edit an SMS-imported row: change only the account; payee/UPI details are still there, and
   both accounts' balances move.
8. Accounts: add a **UPI wallet** with opening balance 500; add a card with 2,000 owed (shows
   "−₹2,000 owed"); pay the card 2,500 via Transfer: it shows "₹500 credit".
