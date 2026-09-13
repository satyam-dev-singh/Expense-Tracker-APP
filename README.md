# Left — Smart Expense Tracker

An original, minimalist Android app built around one question: **"How much money do I have left?"**

Fast expense/income capture, an immediately readable monthly position, simple budgets, and subscription tracking — local-first and privacy-first.

## Current implementation status

| Phase | Status |
| --- | --- |
| **Phase 0 — Foundation** | ✅ Gradle/Compose/Hilt/Room/DataStore config, package structure, navigation skeleton, centralized design system |
| **Phase 1 — Local data layer** | ✅ 6 entities, 6 DAOs, repositories, use cases, Money value object, month engine, default-category seeding |
| **Phase 2 — Onboarding** | ✅ Five-step flow persisting profile, budget, income, category prefs + completion flag |
| **Phase 3 — Core transactions** | ✅ Dashboard wired, amount-first add form, list/search/filter/grouping, detail edit/delete |
| **Phase 4 — Budgeting** | ✅ Daily allowance + pacing status, budgets screen, per-category limits, rollover prefill |
| **Phase 5 — Voice** | ✅ Rule-based offline parser, permission-gated capture, confirm-before-save, duplicate protection |
| **Phase 6 — Analytics** | ✅ Words-first analytics screen with category distribution, 6-month trends, month-over-month comparison, recurring totals, ViewModel tests + JVM harness |
| **Phase 7 — Subscriptions + notifications** | 🚧 Subscription CRUD foundation, renewal math, Settings entry, notification permission; WorkManager reminders/alerts still in progress |
| Phases 8–12 | ⬜ intentionally not started |

See [ARCHITECTURE.md](ARCHITECTURE.md), [DATABASE.md](DATABASE.md), and [DEVELOPMENT.md](DEVELOPMENT.md).
