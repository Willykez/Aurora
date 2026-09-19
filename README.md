# Aurora

A clean, general-purpose AI chat app for Android, built with Jetpack Compose. "Aurora" is
a placeholder name — rename the `namespace`/`applicationId` in `app/build.gradle.kts` and
`strings.xml` freely.

This was originally built as a coding-agent app (file tools, a sandbox workspace, project
picking) and then deliberately stripped back down to a plain chat app on request, keeping
everything about the *experience* — the visual design, streaming behavior, provider management,
history — while dropping everything that made it agent-specific.

## What's here

- **Streaming chat** (`viewmodel/AppViewModel.kt`, `data/AiClient.kt`): talks to any
  OpenAI-compatible `/chat/completions` endpoint — OpenAI, Gemini's OpenAI shim, DeepSeek,
  a local Ollama, or a custom proxy. Plain request/response streaming, no tool calling.
- **Saved provider profiles, not one global key** (`data/ProviderProfile.kt`,
  `data/ProviderProfileStore.kt`): add as many provider credentials as you need — several
  different keys for the same provider included — each fully saved (Keystore-encrypted via
  `data/SecureCrypto.kt`) and switchable by name from a tappable chip in the chat input or from
  Settings. No re-pasting a key you forgot you had.
- **A history sidebar** (`ui/components/HistorySidebar.kt`): new-chat action, live search, and
  the full conversation list, opened via the hamburger icon in the top bar or a swipe from the
  left edge — the app's only navigation surface besides Settings, deliberately modeled on a
  reference chat UI's sidebar-first structure rather than bottom tabs.
- **A markdown/code renderer built for a chat screen, not a document** (`ui/markdown/Markdown.kt`):
  headers, lists, blockquotes, inline code, and fenced code blocks that force-expand into a live
  auto-scrolling view while a reply is still streaming, then settle into a normal block with a
  collapse/expand toggle, a copy button, and a download action that shares the snippet as a real
  file with the right extension for its language.
- **Persistent chat history** (`data/history/ChatHistoryStore.kt`): every conversation is
  JSON on disk, resumable with full context, browsable/deletable from the sidebar.
- **Theming**: a real light and dark color scheme (not an inverted filter), a System/Light/Dark
  picker in Settings, true edge-to-edge system bars that match the app's own background instead
  of a separate black status-bar strip.
- **Sensible back-button behavior**: Settings goes back to Chat on the first press; Chat itself
  needs a second press within 2 seconds to actually exit, with a confirming snackbar — so a
  stray back tap never kills the app mid-conversation.

## Known caveats — please read before opening in Android Studio

- **This was written and carefully reviewed, but not compiled.** This environment has no
  Android SDK and no network access to fetch Gradle/AGP/dependencies, so a build or Android
  Studio's inline checker could still catch something small (an import ordering issue, a
  Compose API name drift between library versions) that this review didn't.
- **No launcher icon / branding assets** — it'll build with the default system icon until you
  add one.
- **First launch needs a provider added before you can send a message** — there's no
  auto-created default profile with a guessed key; the app will prompt you to add one via the
  picker rather than silently failing.

## Repo workflow files

Ported from earlier iterations of this project, not newly invented:

- `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar` — the actual wrapper jar, so
  `./gradlew assembleDebug` runs standalone without Android Studio regenerating anything first.
- `.github/workflows/ci.yml` — debug APK on every push/PR; signed release APK on `main`,
  conditional on `KEYSTORE_B64`/`STORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` secrets being set
  (falls back to an unsigned release build otherwise — see `app/build.gradle.kts`'s
  `hasReleaseSigning` check).
- `.gitignore`, `CONTRIBUTING.md`, `SECURITY.md`.

## Structure

```
app/src/main/java/com/nexusforge/app/
  data/                    wire models, AiClient (SSE), SettingsStore, provider profiles
  data/history/            ChatSession model + on-disk JSON store
  ui/theme/                color, type, Material3 theme (light + dark)
  ui/markdown/             the markdown/code-block renderer
  ui/components/           ChatBubble, ChatInputBar, ProviderBadge/Picker/FormDialog, HistorySidebar
  ui/screens/               Chat, Settings
  viewmodel/AppViewModel.kt streaming chat loop + all app state
  MainActivity.kt           edge-to-edge scaffold + sidebar drawer
```
