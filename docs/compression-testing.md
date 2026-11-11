# Context Compression Verification

## Existing Test Stack

The app uses Koin, JUnit 4 and kotlinx-coroutines-test for local JVM tests.
Android instrumentation uses AndroidX JUnit, Espresso, Compose testing and Room
testing. The inspected app build does not configure Robolectric, a mocking
framework, screenshot tests or JaCoCo. This change reuses the local JVM stack
without adding dependencies or changing dependency injection.

## Focused Coverage

- `CompressionSettingsTest`: defaults, independent saved ranges, mode switching,
  serialization and numeric normalization.
- `SettingsFieldsTest`: DataStore round trips and exhaustive field registration.
- `ConversationCompressionTest`: uncovered-message counting, compression boundaries,
  checkpoint exclusion, tool-message counting and stale-summary invalidation.
- `CompressionPromptTest`: full prompt replacement, locale substitution and editable
  mid-turn instructions.
- `MessageContextLimitTest`: latest/all-summary selection, ordering and exclusion of
  covered originals.
- `CompressionRequestTest`: shared request options, independent mode/lorebook
  filtering, system-context exclusions, user/system compression instructions,
  source-material packaging and independent compression-model selection.
- `ExistingSummaryMarkersTest`: compression-only summary markers and unchanged
  normal-chat content.
- `ChatMapEntriesTest`: checkpoint boundaries, default folding, independent
  expansion, search, source indexes and restoration after deleting summaries.
- `MessageJumperScrollTest`: visibility during scrolling and its timeout after
  scrolling stops.

Run `:ai:testDebugUnitTest :app:testGithubDebugUnitTest` with the project's Gradle
environment. A successful JVM test run does not validate device UI or provider APIs.

## Device Acceptance Checks

- Edit and restore both compression prompts under Model Settings / Prompts.
- Select a separate compression model and reasoning level, restart, and confirm
  manual, automatic and mid-turn compression use these saved choices.
- Under Compression Request Settings, toggle each optional input and inspect the
  outgoing request in manual, idle automatic and mid-turn compression.
- Switch the instruction role between user and system; confirm the full instruction
  is in the final user message or the first merged system message respectively.
- Verify excluded tool definitions do not remove historical tool results, and that
  disabling optional inputs does not alter normal chat requests or local messages.
- Change both manual range counts, switch modes, close/reopen and restart the app.
- Independently change the automatic range and trigger; verify saved values.
- Create multiple checkpoints and inspect provider requests for latest/all modes.
- Verify count-triggered compression starts only after a complete turn, never
  during streaming, tool execution or tool approval; queued input waits until done.
- Verify a single completion cannot cause repeated automatic compressions.
- Enable the assistant context-count limit and verify compression is unavailable.
- Edit covered content or switch its branch and verify downstream summaries vanish
  while original message nodes and their alternatives remain local.
- Check narrow screens, large fonts, scrolling, keyboard visibility and cancellation.
- Open the conversation map directly from the top-right button. Expand each
  summary's covered originals, search folded content, and verify jump positions
  and restoration when a summary is deleted.
