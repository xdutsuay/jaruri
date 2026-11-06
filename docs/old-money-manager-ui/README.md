# Old Money Manager UI reference

Package: `money.expense.budget.wallet.manager.track.finance.tracker`  
Activity: `meevii.beatles.moneymanage.ui.activity.*`  
Store listing name: **Money Manager** (v1.2.6)

Jaruri Fastlane shots under `fastlane/metadata/.../phoneScreenshots` are **our** app, not this. Those shots use the in-app demo ledger only.

## Captured (2026-09-25)

Screenshots and UI dumps that showed a real populated ledger were removed. Do not commit captures of a personal ledger, balances, memos, or account masks.

| File | Screen |
|------|--------|
| `03_sign_in.png` | Facebook / Google sign-in |
| `03_categories.png` | Category grid / list (default categories only) |
| `03_settings.png` | Settings — Smart reminder 20:00 |
| `03_settings_scrolled.png` | Settings scrolled (same sparse screen) |
| `04_add_form.png` | Add expense — category grid + keypad + Today/memo |
| `ui.xml` | About dialog hierarchy (version text only) |

## Re-capture

```powershell
python E:\codes\jaruri\docs\old-money-manager-ui\capture_all.py
# or coordinate-driven:
python E:\codes\jaruri\docs\old-money-manager-ui\capture_coords.py
```

Review every new capture before committing. Home, chart, export, and dialog screenshots often include the ledger behind the UI.

## Design notes for Jaruri parity
- Yellow accent FAB + drawer header
- Home: Income / Expenses / Balance strip; transactions grouped by date with day expense total
- Colored circular category icons
- Chart: donut + ranked list with thin progress bars
- Add: category icon grid + numeric keypad (not a long form)
- Export: date range + CSV (Money Manager format: Date, Income/Expenses, Category, Memo, Amount)
