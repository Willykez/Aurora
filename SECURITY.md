# Security

Please do not report security vulnerabilities in public issues.

Remove API keys, access tokens, private URLs, signing credentials, and
keystore material from bug reports and logs before sharing them.

## How Aurora handles sensitive data

- Provider API keys are stored using Android Keystore-backed AES/GCM
  encryption (`SecureCrypto`, used by `ProviderProfileStore`), and the
  files they live in are explicitly excluded from cloud backup and device
  transfer (`data_extraction_rules.xml`, `backup_rules.xml`).
- The app makes no changes to anything outside itself — there is no file
  system access, no tool execution, and no permissions beyond internet
  access. The only outbound network calls are to the base URL of whichever
  provider profile is active.
- Do not hard-code provider credentials into source code or Gradle files.
