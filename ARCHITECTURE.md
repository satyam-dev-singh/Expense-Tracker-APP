# Architecture

Left follows MVVM + Clean Architecture with feature-oriented packages
(Technical Architecture §2). Dependencies point downward only.

```text
feature/* (Compose screens + ViewModels)
        ↓
core/domain (use cases, validation)
        ↓
core/data (repository interfaces + Room implementations)
        ↓
core/database / core/datastore (Room, DataStore)
```

## Package map (`com.left.app`)

```text
LeftApplication.kt        @HiltAndroidApp; idempotent default-category seeding on cold start
MainActivity.kt           Single activity; edge-to-edge; LeftTheme + LeftNavHost

core/
├── common/               MonthRange (month math), UiState (Loading/Success/Empty/Error)
├── data/                 Repository interfaces + Room implementations (+mappers)
├── database/             LeftDatabase, Converters, entity/, dao/, seed/
├── datastore/            UserPreferencesDataStore (onboarding flag, default currency)
├── designsystem/         Color/Type/Tokens/Icon + theme/ + component/ (Button, TextField, Card)
├── di/                   AppModule, DatabaseModule, DataStoreModule, RepositoryModule
├── domain/               Use cases (transactions + monthly calculations)
├── model/                Domain models + enums
├── navigation/           LeftNavHost, LeftDestination, top-level destinations
├── network/              NetworkMonitor contract only (offline-first; implementation in Phase 9)
├── security/             SafeLogger (never logs financial content)
└── utils/                Money value class, CurrencyUtils

feature/
├── splash/               S01: loads local state, routes to onboarding or Home
├── onboarding/           Placeholder → Phase 2
├── dashboard/            Placeholder with locked visual hierarchy → Phase 3
├── transactions/         Placeholders (list + add) → Phase 3
├── analytics/            Placeholder → Phase 6
└── settings/             Placeholder → phased
(voice/, budgets/, subscriptions/, insights/ are added in their phases)
```

The master structure lists `core/` buckets; `core/model`, `core/data` and
`core/domain` were added because Clean Architecture's repository-interface and
use-case layers need a home outside `database/` and outside features.

## Key decisions

1. **Money is a `@JvmInline value class Money` wrapping `Long` minor units**
   (paise for INR). `+`/`-` use `Math.addExact`/`subtractExact` — overflow
   throws instead of silently corrupting financial data. Parsing accepts
   symbols/grouping (`₹1,00,000.50`) but rejects over-precise input rather
   than rounding. Float/Double are banned for money; the only Double in the
   codebase is the derived budget-usage *percentage* (display metric, not money).
2. **Dates**: calendar dates (`transactionDate`, billing dates) are stored as
   INTEGER epoch days; timestamps (`createdAt`, `updatedAt`) as epoch millis.
   Monthly filtering is a half-open range `[month start, next month start)`
   computed by `MonthRange` from `java.time.YearMonth` — never string
   comparison. Leap years, month lengths and Dec→Jan rollover are correct by
   construction; "current month" comes from an injected `Clock` (timezone-aware,
   unit-testable).
3. **minSdk 26**: native `java.time` (no desugaring) while covering the vast
   majority of devices; target/compile SDK 35.
4. **Category lifecycle**: archive-first. `isArchived` hides categories from
   pickers while keeping rows, and the FK is `onDelete = SET_NULL`, so even a
   hard delete leaves historical transactions intact as uncategorized (PRD §11).
5. **Seeding is idempotent**: default categories use deterministic IDs
   (`default-food`, …) + `INSERT OR IGNORE`; safe to run on every cold start.
6. **Validation lives in use cases, not the UI** (PRD §18): amount > 0, ISO
   4217 uppercase currency, category must exist when set (null = uncategorized
   is explicitly allowed), dates are `LocalDate` by construction. Failures are
   typed `TransactionValidationException`s with user-safe messages.
7. **Privacy**: zero runtime permissions in Phase 0–1; `allowBackup=false`
   (financial data must not silently leave the device via platform backup;
   explicit backup/restore is a Phase 9 feature); `usesCleartextTraffic=false`;
   `SafeLogger` contract forbids logging amounts/merchants/notes.
8. **No destructive DB fallback**: Room has no `fallbackToDestructiveMigration`;
   upgrades will be explicit `Migration` classes with exported schemas in
   `app/schemas` (see DATABASE.md).
9. **Icons**: `material-icons-extended` (first-party Compose artifact) so the
   category `iconKey` convention maps to real glyphs without custom assets.
10. **CI/lint**: Android lint runs in CI with build+unit tests
    (.github/workflows/android-ci.yml); no third-party static analysis yet.

## Error handling & UI states

Screens standardize on `UiState` = Loading / Success / Empty / Error (PRD §20).
Errors surface as friendly text stating what happened and what to do next;
technical details stay in debug logs only (SafeLogger).

## Testing approach

- **Unit (JVM)**: `Money`, `MonthRange`, transaction use cases, calculation use
  cases, seeding definitions — running against in-memory fake repositories.
- **Instrumented (device)**: real Room/SQLite behavior for TransactionDao
  (CRUD, month boundaries, search, totals, FK SET_NULL) and seed idempotency.
- Later phases add Compose UI tests per feature (Technical Architecture §9).
