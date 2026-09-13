# Development

## Final status

Phases 0–12 are implemented in source form and pushed to `main`.

## Required real-machine verification

The agent sandbox has no Android SDK/Gradle/network, so final Android verification must be run on a development machine:

```bash
./gradlew lintDebug testDebugUnitTest assembleDebug
```

## Sandbox verification performed

- SubscriptionHarness: 9/9
- Phase7NotificationHarness: 6/6
- Phase8Harness: 6/6
- Phase9Harness: 6/6
- FinalHarness: 7/7

## Security posture

- Local-first by default.
- RECORD_AUDIO only for voice.
- POST_NOTIFICATIONS only after notification feature shipped.
- USE_BIOMETRIC only after native lock feature shipped.
- No contacts/location/SMS.
- No secrets committed.
- No network AI/ML added in Phase 10.
