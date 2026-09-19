# Contributing to Aurora

## Development

1. Open the project in Android Studio (Koala or newer) and let Gradle sync.
2. Use JDK 17.
3. Use the Gradle version declared by CI (8.9) — the wrapper already pins this.
4. Never commit API keys, keystores, passwords, or `local.properties`.
5. Provider credentials are always Keystore-encrypted at rest via `SecureCrypto` —
   never store a raw API key in DataStore, a log line, or anywhere else.
6. Adding a new provider preset is just adding an entry to `SettingsStore.PRESETS`;
   the app itself is provider-agnostic (anything OpenAI-compatible works without
   code changes).

## Validation

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The GitHub Actions workflow (`.github/workflows/ci.yml`) performs the same
build validation on every push and pull request. There are no unit tests
included yet — `testDebugUnitTest` will currently just pass trivially; adding
real coverage for the streaming response parser and `ChatHistoryStore` would
be a good first contribution.
