# Database

Room database `left.db`, version 1. Class: `LeftDatabase`
(`core/database/LeftDatabase.kt`). All access flows through repositories in
`core/data` — UI and ViewModels never touch DAOs (PRD §13).

## Schema (v1)

### `user_profile`
| Column | Type | Notes |
| --- | --- | --- |
| id | TEXT PK | constant `local_user` (single-profile local MVP) |
| name | TEXT NOT NULL | |
| currency_code | TEXT NOT NULL | ISO 4217 uppercase, e.g. `INR` |
| locale | TEXT NOT NULL | e.g. `en-IN` |
| created_at / updated_at | INTEGER NOT NULL | epoch millis UTC |

### `categories`
| Column | Type | Notes |
| --- | --- | --- |
| id | TEXT PK | defaults use deterministic `default-<slug>` |
| name | TEXT NOT NULL | indexed |
| icon_key | TEXT NOT NULL | maps via `LeftIcons.category()` |
| type | TEXT NOT NULL | `EXPENSE` / `INCOME` / `BOTH` (Room enum support) |
| budget_limit_minor | INTEGER NULL | per-category monthly cap (PRD FR-06), minor units |
| is_default / is_archived | INTEGER NOT NULL | archive-first lifecycle |
| created_at / updated_at | INTEGER NOT NULL | epoch millis UTC |

### `transactions`
| Column | Type | Notes |
| --- | --- | --- |
| id | TEXT PK | UUID |
| type | TEXT NOT NULL | `EXPENSE` / `INCOME` |
| amount_minor | INTEGER NOT NULL | minor units; > 0 enforced by use cases |
| currency_code | TEXT NOT NULL | ISO 4217 |
| category_id | TEXT NULL | FK → categories.id, **ON DELETE SET NULL** |
| merchant / note | TEXT NULL | blank input normalized to NULL |
| transaction_date | INTEGER NOT NULL | **epoch day** (LocalDate) |
| created_at / updated_at | INTEGER NOT NULL | epoch millis UTC |
| source | TEXT NOT NULL | `MANUAL` / `VOICE` / `IMPORT` |
| is_recurring | INTEGER NOT NULL | |

Indices: `(category_id)`, `(transaction_date)`.

### `subscriptions`
| Column | Type | Notes |
| --- | --- | --- |
| id | TEXT PK | UUID |
| name | TEXT NOT NULL | |
| amount_minor | INTEGER NOT NULL | minor units |
| currency_code | TEXT NOT NULL | |
| category_id | TEXT NULL | FK → categories.id, ON DELETE SET NULL; indexed |
| billing_cycle | TEXT NOT NULL | `WEEKLY` / `MONTHLY` / `QUARTERLY` / `YEARLY` |
| next_billing_date | INTEGER NOT NULL | epoch day |
| reminder_enabled / active | INTEGER NOT NULL | |
| created_at / updated_at | INTEGER NOT NULL | epoch millis UTC |

### `income_sources`
| Column | Type | Notes |
| --- | --- | --- |
| id | TEXT PK | UUID |
| name | TEXT NOT NULL | |
| amount_minor | INTEGER NOT NULL | minor units; currency follows the profile currency |
| frequency | TEXT NOT NULL | `WEEKLY` / `BIWEEKLY` / `MONTHLY` / `QUARTERLY` / `YEARLY` / `ONE_TIME` |
| next_date | INTEGER NULL | epoch day; null for irregular/one-time |
| active | INTEGER NOT NULL | |
| created_at / updated_at | INTEGER NOT NULL | epoch millis UTC |

### `monthly_budgets`
| Column | Type | Notes |
| --- | --- | --- |
| id | TEXT PK | UUID |
| year | INTEGER NOT NULL | |
| month | INTEGER NOT NULL | 1..12 |
| total_limit_minor | INTEGER NOT NULL | minor units |
| created_at / updated_at | INTEGER NOT NULL | epoch millis UTC |

Unique index `(year, month)` — at most one budget per month, enforced by SQLite.

## Relationships

```text
categories 1 ─── * transactions     (FK SET_NULL; archiving preferred)
categories 1 ─── * subscriptions    (FK SET_NULL)
user_profile 1 ─ * income_sources   (implicit: single-profile MVP, no FK column)
monthly_budgets ── budget calculations (via repositories/use cases)
```

Historical data survives category removal: rows keep their `category_id`
(a resolvable archived row), or become `NULL` (uncategorized) after a hard
delete. Transaction history is never cascade-deleted (PRD §11).

## Query patterns

- **Monthly filtering**: `transaction_date >= startEpochDay AND < endEpochDay`
  where bounds come from `MonthRange.of(year, month)` (epoch-day integers).
- **Monthly totals**: `SELECT type, SUM(amount_minor) ... GROUP BY type` over
  the month range; exposed as `observeMonthlyTotals` (Flow) and
  `getMonthlyTotals` (one-shot).
- **Search**: `merchant/note LIKE '%' || :q || '%' ESCAPE '\'`; repositories
  escape `%`, `_`, `\` so user input is matched literally.
- **Reactive reads**: all list/detail reads return `Flow` (Room invalidation
  drives the UI automatically).

## Seeding

`DefaultCategories` (10 PRD categories) inserted with `INSERT OR IGNORE` using
deterministic IDs → idempotent on every cold start (`LeftApplication`).
All defaults are `EXPENSE` categories (the PRD list is spending-focused);
income may be uncategorized until Phase 2.

## Migrations

- v1 is the baseline. `exportSchema = true`; schema JSON lands in
  `app/schemas/` and is committed for review.
- Future versions ship explicit `Migration` classes plus instrumented
  migration tests (PRD MVP release criteria).
- `fallbackToDestructiveMigration()` is deliberately **not** used — silent
  loss of financial data is never acceptable.
