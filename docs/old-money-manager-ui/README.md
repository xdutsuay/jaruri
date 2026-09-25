# Old Money Manager UI reference

Package: `money.expense.budget.wallet.manager.track.finance.tracker`  
Activity: `meevii.beatles.moneymanage.ui.activity.*`  
Store listing name: **Money Manager** (v1.2.6)

Jaruri Fastlane shots under `fastlane/metadata/.../phoneScreenshots` are **our** app, not this.

## Captured (2026-09-25)

| File | Screen |
|------|--------|
| `01_home.png` | Home ledger — month summary + dated groups + yellow FAB |
| `02_drawer.png` | Nav drawer (Sign In, Chart, Categories, Export, Settings, Rate Us, About) |
| `03_sign_in.png` | Facebook / Google sign-in |
| `03_chart.png` | Donut chart + expenses list with % bars |
| `03_categories.png` | Category grid / list |
| `03_export.png` | Export form dialog (Start/End/Format CSV) |
| `03_settings.png` | Settings — Smart reminder 20:00 |
| `03_settings_scrolled.png` | Settings scrolled (same sparse screen) |
| `03_about.png` | About dialog Version 1.2.6 |
| `03_rate_us.png` | Rate Us |
| `04_add_chooser.png` / `04_add_transaction.png` | Add flow entry |
| `04_add_form.png` | Add expense — category grid + keypad + Today/memo |
| `04_add_expense.png` / `04_add_income.png` | Expense/Income mode |
| `jaruri_after_legacy_import.png` | Jaruri after one-time XLS/CSV replace (totals match) |

## Re-capture

```powershell
python E:\codes\jaruri\docs\old-money-manager-ui\capture_all.py
# or coordinate-driven:
python E:\codes\jaruri\docs\old-money-manager-ui\capture_coords.py
```

## Design notes for Jaruri parity
- Yellow accent FAB + drawer header
- Home: Income / Expenses / Balance strip; transactions grouped by date with day expense total
- Colored circular category icons
- Chart: donut + ranked list with thin progress bars
- Add: category icon grid + numeric keypad (not a long form)
- Export: date range + CSV (Money Manager format: Date, Income/Expenses, Category, Memo, Amount)
