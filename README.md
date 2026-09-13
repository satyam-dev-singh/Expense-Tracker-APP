# Left — Smart Expense Tracker

An original, minimalist Android app built around one question: **"How much money do I have left?"**

Fast expense/income capture, an immediately readable monthly position, simple budgets, and subscription tracking — local-first and privacy-first. This is an independent Android implementation: all code, branding, design tokens, and copy are original (see the project documentation pack for context).

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture, feature-oriented packages |
| DI | Hilt |
| Local storage | Room (source of truth) + DataStore (preferences) |
| Async | Kotlin Coroutines + Flow |
| Navigation | Navigation Compose |
| Background | WorkManager (wired for later phases) |
| Tests | JUnit 4, kotlinx-coroutines-test, AndroidX Test, Compose UI test |

Minimum SDK 26 (Android 8.0) — native `java.time` without desugaring while keeping broad device coverage. Target/compile SDK 35.

## Build & run

Prerequisites: Android Studio (current stable) or JDK 17 + Android SDK 35.

```bash
./gradlew assembleDebug          # build debug APK
./gradlew testDebugUnitTest      # unit tests
./gradlew connectedDebugAndroidTest   # instrumented tests (device/emulator)
./gradlew lintDebug              # static analysis
```

Debug builds use application id suffix `.debug` so they can sit next to a release install. See [DEVELOPMENT.md](DEVELOPMENT.md) for details.

## Architecture

```text
Compose screens (feature/*)
        ↓
ViewModel (feature-scoped, from Phase 2/3 onward)
        ↓
Use cases (core/domain)
        ↓
Repository interfaces (core/data)
        ↓
Room / DataStore implementations (core/database, core/datastore)
```

UI never touches DAOs; money is never Float/Double (integer minor units via the `Money` value class); monthly filtering is exact epoch-day range math via `MonthRange`. See [ARCHITECTURE.md](ARCHITECTURE.md) and [DATABASE.md](DATABASE.md).

## Current implementation status

| Phase | Status |
| --- | --- |
| **Phase 0 — Foundation** | ✅ Gradle/Compose/Hilt/Room/DataStore config, package structure, navigation skeleton (Splash, Onboarding, Home, Transactions, AddTransaction, Analytics, Settings), centralized design system |
| **Phase 1 — Local data layer** | ✅ 6 entities, 6 DAOs, 6 repositories, 11 use cases, Money value object, month engine, default-category seeding, unit + instrumented tests |
| **Phase 2 — Onboarding** | ✅ Five-step flow (welcome → currency → income → budget → categories) persisting profile, current-month budget, recurring income, category prefs + completion flag; validation-before-write, flag-last ordering, ViewModel + use-case tests |
| Phase 3 — Core transactions | ⬜ next up (not started) |
| Phases 4–12 | ⬜ intentionally not started |

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) — layers, package map, engineering decisions
- [DATABASE.md](DATABASE.md) — schema, relationships, indices, migration policy
- [DEVELOPMENT.md](DEVELOPMENT.md) — setup, commands, testing, verification
