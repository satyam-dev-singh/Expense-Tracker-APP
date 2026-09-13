# Development

## Prerequisites

- JDK 17 (AGP 8.7 requires it)
- Android SDK 35 with build-tools (Android Studio current stable handles this)
- First Gradle run downloads the wrapper (`gradle-8.11.1`) and dependencies

## Everyday commands

```bash
./gradlew assembleDebug            # debug APK (app/build/outputs/apk/debug)
./gradlew installDebug             # install on connected device
./gradlew testDebugUnitTest        # JVM unit tests (fast)
./gradlew connectedDebugAndroidTest  # instrumented Room/Compose tests
./gradlew lintDebug                # Android lint
```

Debug builds install as `com.left.app.debug` alongside any release install.

## Project layout

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full map. Quick rules:

- Feature code goes in `feature/<name>/` and never reaches into Room directly.
- Cross-cutting code goes in the matching `core/` bucket.
- New money handling must use `Money` (integer minor units) — never Float/Double.
- New month logic must use `MonthRange` — never string/date-text comparison.
- UI states use `UiState` (Loading / Success / Empty / Error).
- User-visible strings: placeholders are inline during Phase 0–1; extract to
  `strings.xml` when each feature screen is implemented.

## Testing

```text
app/src/test/          JVM unit tests (JUnit4 + kotlinx-coroutines-test)
  core/utils/MoneyTest.kt
  core/common/MonthRangeTest.kt
  core/domain/TransactionUseCasesTest.kt      (against in-memory fakes)
  core/domain/CalculationUseCasesTest.kt      (PRD §19 scenarios)
  core/database/seed/DefaultCategoriesTest.kt
  core/domain/CompleteOnboardingTest.kt        (Phase 2: ordering/skip/validation)
  feature/onboarding/OnboardingViewModelTest.kt (Phase 2: step flow state machine)
  core/data/fake/FakeRepositories.kt          (shared test doubles)
  core/data/fake/FakeOnboardingRepositories.kt (onboarding fakes + event ordering)
  testutil/MainDispatcherRule.kt               (ViewModels on the JVM)
  feature/dashboard/DashboardViewModelTest.kt
  feature/transactions/TransactionsViewModelTest.kt   (search/filters/month/groups)
  feature/transactions/AddTransactionViewModelTest.kt
  feature/transactions/TransactionDetailViewModelTest.kt
  core/domain/BudgetUseCasesTest.kt             (Phase 4: allowance + thresholds)
  feature/budgets/BudgetViewModelTest.kt        (Phase 4: rollover prefill, save/clear)
  core/voice/VoiceExpenseParserTest.kt          (Phase 5: parser rules)
  feature/voice/VoiceViewModelTest.kt           (Phase 5: capture state machine)

app/src/androidTest/   instrumented tests (AndroidX Test, real in-memory Room)
  core/database/TransactionDaoTest.kt
  core/database/DatabaseSeedTest.kt
```

Use cases take a `java.time.Clock` — tests inject `Clock.fixed(...)` so month
boundaries, leap days and timezones are deterministic. Add new repository
methods to the fakes as well.

## Security checklist (Phase 0–5 posture)

- Only runtime permission is RECORD_AUDIO (Phase 5 voice), requested inline when the
  voice screen opens — never at launch. Notifications arrive in Phase 7.
- No API keys in source. If a future phase needs one: `local.properties` +
  BuildConfig, never VCS. Keystores never committed (see .gitignore).
- Do not log amounts, merchants, notes, or other financial content — use
  `SafeLogger` and keep messages technical.
- `allowBackup=false` until explicit user-controlled backup exists (Phase 9).

## Verification status of this checkpoint

The first development session ran in a network-restricted Linux sandbox
without an Android SDK or Gradle, so `./gradlew` builds could not execute
there. What was verified in-sandbox:

- The money/month/calculation algorithms were executed through a JVM harness
  mirroring `Money`, `MonthRange` and the calculation use cases 1:1 — all
  PRD §19 scenarios pass (see `verification/` notes in the session report).
- Structural checks: all required files present, XML well-formed, no
  Float/Double money fields, full DAO capability coverage.
- Phase 2: `verification/OnboardingHarness.java` mirrors the onboarding
  completion rules (validation, skip semantics, flag-last write ordering,
  next-occurrence date math) — 19/19 assertions pass.
- Phase 3: `verification/TransactionsHarness.java` mirrors list/detail rules
  (day-group labels, inclusive amount-range predicates, search-vs-month
  decision, edit-prefill format→parse round-trip) — 24/24 assertions pass.
- Phase 4: `verification/BudgetHarness.java` mirrors budget pacing rules
  (80/100% thresholds, daily allowance incl. leap months + clamps, rollover
  prefill decisions) — 19/19 assertions pass.
- Phase 5: `verification/VoiceHarness.java` mirrors the voice parser rules
  (amount/type/category/merchant/date extraction, NoAmount path) and the
  duplicate-signature guard — 31/31 assertions pass.

**First thing to do on a machine with Android Studio / network:**

```bash
cd Left
./gradlew lintDebug testDebugUnitTest assembleDebug
```

CI (.github/workflows/android-ci.yml) runs the same on every push/PR.

## Git

Checkpoint commits so far:

```text
feat: initialize Android architecture and local finance data layer
fix: parse amounts like "Rs. 10" without treating stray dot as decimal
docs: add progress_so_far.txt (built vs remaining phases)
feat: phase 2 onboarding flow
feat: phase 3 core transaction experience
feat: phase 4 budgeting
feat: phase 5 voice quick entry
```

Do not commit `local.properties`, keystores, or generated credentials.
Room schema JSON under `app/schemas/` **should** be committed.
