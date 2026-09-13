# Development

## Prerequisites

- JDK 17 (AGP 8.7 requires it)
- Android SDK 35 with build-tools (Android Studio current stable handles this)
- First Gradle run downloads the wrapper (`gradle-8.11.1`) and dependencies

## Everyday commands

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew lintDebug
```

## Project layout

- Feature code goes in `feature/<name>/` and never reaches into Room directly.
- Cross-cutting code goes in the matching `core/` bucket.
- Money uses `Money` integer minor units — never Float/Double.
- Month logic uses `MonthRange` — never string/date-text comparison.
- UI state is immutable and exposed as StateFlow.

## Testing

Phase 6 adds:

```text
app/src/test/java/com/left/app/core/data/fake/FakeSubscriptionRepository.kt
app/src/test/java/com/left/app/feature/analytics/AnalyticsViewModelTest.kt
verification/AnalyticsHarness.java
```

Existing tests remain under `app/src/test/` and `app/src/androidTest/` for Phases 0–5.

## Security checklist (Phase 0–6 posture)

- Only runtime permission is RECORD_AUDIO (Phase 5 voice), requested inline when the voice screen opens.
- Notifications arrive in Phase 7. No contacts/location/SMS.
- No API keys in source. Keystores and generated credentials are not committed.
- `SafeLogger` is used for technical failures only; do not log financial content.
- Analytics is local-only and repository-driven; no network, tracking, export, or AI/ML.
- `allowBackup=false` until explicit user-controlled backup exists (Phase 9).

## Verification status of this checkpoint

The sandbox has no Android SDK/Gradle/network, so `./gradlew` builds could not execute here. Verified in-sandbox:

- `verification/VerificationHarness.java` — 61/61 assertions pass.
- `verification/OnboardingHarness.java` — 19/19 assertions pass.
- `verification/TransactionsHarness.java` — 24/24 assertions pass.
- `verification/BudgetHarness.java` — 19/19 assertions pass.
- `verification/VoiceHarness.java` — 31/31 assertions pass.
- `verification/AnalyticsHarness.java` — 10/10 assertions pass.
- `verification/structural_checks.py` registers Phase 6 files.

Run on a real dev machine:

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

## Git

Checkpoint commits so far include:

```text
feat: initialize Android architecture and local finance data layer
fix: parse amounts like "Rs. 10" without treating stray dot as decimal
docs: add progress_so_far.txt (built vs remaining phases)
feat: phase 2 onboarding flow
feat: phase 3 core transaction experience
feat: phase 4 budgeting
feat: phase 5 voice quick entry
feat: phase 6 analytics
docs: update phase 6 verification status
```
