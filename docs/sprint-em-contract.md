# Jaruri Sprint Contract — EM / Shared Surfaces

**Role:** Engineering Manager + Product Owner (shared surfaces only)  
**App:** `com.kaustubhtripathi.jaruri` / package `com.example.moneymanager`  
**Sprint peers:** Money polish · Voice journal · Usage-stats (Digital Wellbeing lite)

This contract is binding for parallel agents. Prefer small diffs. Do not redesign brand (amber primary, dark ink on yellow).

---

## 1. Layering

| Layer | Owns | Must not |
| --- | --- | --- |
| **UI** (`ui/`, layouts, menus) | Views, binding, navigation calls, permission prompts | Business rules, SMS parsing, Room SQL |
| **ViewModel** (`viewmodel/`) | UI state, coroutines to repos/DAOs, Livedata/Flow | Android View inflation, heavy parsing loops |
| **Data** (`data/`) | Room entities/DAOs, DataStore repos, migrations | UI strings, SpeechRecognizer, UsageStatsManager |
| **Utils** (`utils/`) | Pure Kotlin helpers (parse, format, trust, ledger math) | Context leaks, View references, DAO side effects when avoidable |
| **Wellbeing** (`wellbeing/`) | UsageStats wrappers, usage UI/VM, `usage_daily` | Money SMS pipeline, voice audio storage |
| **Journal** (`utils/journal/`) | SpeechRecognizer wrapper, journal text helpers | Bundled ASR models |

Rules:

- Prefer **pure Kotlin** functions in `utils/` / aggregators for anything testable without Robolectric.
- Side-effecting orchestration (DAO writes) may live in utils objects (existing pattern: `InstrumentLedger`, `SmsAutoImporter`) but keep **delta/format/trust** logic pure and unit-tested (`txnBalanceDelta`, `transferSideDelta`, `TransactionAccounting.*`).
- New screens: Fragment + optional ViewModel; reuse `MainViewModel` / `TimeViewModel` only when the concern is already owned there.

---

## 2. Room migration rules

Current: `AppDatabase` **version = 11**, migrations through `10→11` registered (`quick_add_templates`).

| From→To | Owner theme | Change |
| --- | --- | --- |
| 2→3 | Money | soft-delete + category_learn |
| 3→4 | Time | time_entries |
| 4→5 | Money | needsCategoryReview + account SMS balance fields + balance_observations |
| 5→6 | Money | transferToAccountId + category_rules |
| 6→7 | Voice | time_entries.source |
| 7→8 | Usage | usage_daily |
| 8→9 | Money | payees + payee_aliases + transactions.payeeId |
| 9→10 | Money | upiRef/rrn/linkedTransactionId/isRefundNeutral |
| 10→11 | Money | quick_add_templates |

**Required for every schema change:**

1. Bump `@Database(version = N)` by **exactly +1**.
2. Add `MIGRATION_(N-1)_N` that is **additive** (new tables/columns with defaults; no silent wipe of user ledger).
3. Register it in `.addMigrations(...)`.
4. **Never** rely on `fallbackToDestructiveMigration()` for production data paths. It remains as a last-resort safety net only.
5. Coordinate bumps: if two agents need schema in the same sprint, serialize — second agent starts from the first’s new version.
6. Entity ownership: Money → transactions/accounts/budgets/recurring/category_*; Voice → time_entries columns; Usage → `usage_*` tables only.

---

## 3. Agnostic SMS / instrument patterns

- **No bank allow-lists** as a gate for import.
- Identity = **instrument shape**: type + last-4 (+ optional free-text `bankHint`).
- Prefer body heuristics + Indian DLT sender class (`-T` / `-S` / `-P`) via `SmsTrustEvaluator` / `SmsParser`.
- Bank names in sample SMS / seed data are OK; hard-coded issuer accept/reject lists are not.
- Custom rules: user-defined `category_rules` only — still no hardcoded bank roster.

---

## 4. File ownership boundaries

### Shared (EM owns — ask before large edits)

| Path | Notes |
| --- | --- |
| `app/src/main/res/values/themes.xml` | Styles, filter buttons, sharpness |
| `app/src/main/res/values/colors.xml` | Brand + hairline/contrast tokens |
| `app/src/main/res/values/dimens.xml` | Shared spacing/stroke tokens |
| `app/src/main/res/color/*` | Shared selectors (filter chips) |
| Cross-cutting strings in `strings.xml` | Use **namespaced** prefixes |
| `app/src/main/res/navigation/nav_graph.xml` | Coordinate IDs before adding destinations |
| `app/src/main/res/menu/menu_drawer.xml` | Drawer IDs must match graph |
| `docs/sprint-em-contract.md` | This file |

**String namespaces:** Money `money_`/`transfer_`/`goal_`/`sub_`/`rule_`; Voice `time_`/`voice_`; Usage `usage_`/`hub_usage_`/`settings_usage_*`; shared `hub_`/`menu_`.

### Money polish

**Primary:** `utils/Sms*`, `InstrumentLedger`, `CreditCardLedger`, `TransactionAccounting`, `SubscriptionDetector`, money UI, money entities/DAOs, `MainViewModel` money surfaces, `category_rules`.  
**Avoid:** `utils/journal/`, `wellbeing/`.  
**Nav:** Prefer dialogs/sheets; optional reserved IDs `nav_goals`, `nav_transfers`.

### Voice journal

**Primary:** `TimeHomeFragment`, time layouts/dialogs, `TimeViewModel`, `TimeEntry*`, `utils/journal/*` via **SpeechRecognizer** (no Whisper/LLM models).  
**Avoid:** Money SMS pipeline, `wellbeing/` ownership fights.  
**Nav:** Stay on `nav_time`.

### Usage-stats

**Primary:** `wellbeing/*`, `nav_usage`, hub `card_usage`, usage strings, Usage Access flow, `usage_daily`.  
**Avoid:** Bundled ML; rewriting Time manual log; money ledger schema.

---

## 5. Size & dependency budget

| Budget | Limit |
| --- | --- |
| APK binary | **&lt; 20 MB** |
| On-device app data (typical) | **mostly &lt; 100 MB** |
| Models | **None** bundled |
| New deps | Prefer system APIs; reuse MPAndroidChart; EM sign-off for heavy libs |

---

## 6. Test expectations

- Unit tests for pure logic (parsers, trust, ledger deltas, totals, usage aggregators, journal text helpers).
- Run `./gradlew testDebugUnitTest` under **JDK 17 or 21** (not 25).

---

## 7. Navigation

Current: `nav_hub` (start), `nav_home`, `nav_time`, **`nav_usage`**, money secondary screens. Drawer id must match graph id. Announce new IDs in `COORDINATION.md` before editing `nav_graph.xml`.

---

## 8. UI sharpness tokens

- `@color/hairline`, `@dimen/hairline_stroke` / `hairline_thin`
- `@style/Widget.Jaruri.FilterButton` + `@color/filter_button_*`
- `@style/Widget.Jaruri.Card` / `@dimen/card_corner_sharp`
- Hub cards: `@dimen/card_corner_feature`
- Text: `@color/text_primary` / `@color/text_secondary`

---

## 9. EM non-goals

- Full Money / Voice / Usage product implementation.
- Brand redesign / dark-mode overhaul.
- Commits unless the user asks.

---

## Changelog

- 2026-10-06 — Initial contract; EM owns shared themes/colors/dimens/nav coordination.
- 2026-10-06 — Updated for live peer progress: DB v8, `nav_usage`, transfer/voice/usage packages in tree.
