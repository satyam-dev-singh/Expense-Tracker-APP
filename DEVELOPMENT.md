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

Phase 6 added analytics ViewModel tests and `verification/AnalyticsHarness.java`.
Phase 7 currently adds subscription domain/UI foundation and `verification/SubscriptionHarness.java`.

## Security checklist (Phase 0–7 posture)

- Runtime permissions are feature-gated: RECORD_AUDIO for voice and POST_NOTIFICATIONS for Phase 7 notifications.
- No contacts/location/SMS.
- No API keys in source. Keystores and generated credentials are not committed.
- `SafeLogger` is used for technical failures only; do not log financial content.
- Analytics and subscriptions are local-only and repository-driven; no network, tracking, export, or AI/ML.
- `allowBackup=false` until explicit user-controlled backup exists (Phase 9).

## Verification status of this checkpoint

The sandbox has no Android SDK/Gradle/network, so `./gradlew` builds could not execute here. Verified in-sandbox:

- `verification/VerificationHarness.java` — 61/61 assertions pass.
- `verification/OnboardingHarness.java` — 19/19 assertions pass.
- `verification/TransactionsHarness.java` — 24/24 assertions pass.
- `verification/BudgetHarness.java` — 19/19 assertions pass.
- `verification/VoiceHarness.java` — 31/31 assertions pass.
- `verification/AnalyticsHarness.java` — 10/10 assertions pass.
- `verification/SubscriptionHarness.java` — 9/9 assertions pass.

Run on a real dev machine:

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

## Git

Recent checkpoint commits include:

```text
feat: phase 6 analytics
docs: update phase 6 verification status
feat: phase 7 subscriptions foundation
feat: wire phase 7 subscription entry and notification permission
docs: record phase 7 subscription progress
```
