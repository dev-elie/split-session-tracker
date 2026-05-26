# Agent Notes

## Project Overview

This is a RuneLite external plugin named **Auto Split Manager**. It tracks OSRS group loot splits by maintaining a session roster, detecting values from clan/friends chat, attributing loot to players or their mains, and showing settlement guidance in a Swing sidebar panel.

The plugin entry point is `com.splitmanager.ManagerPlugin`, declared in `runelite-plugin.properties`.

## Build And Test

- `./gradlew test` runs the JUnit 4 test suite.
- `./gradlew build` compiles and runs tests.
- `./gradlew shadowJar` builds a runnable RuneLite test jar using `com.example.ExamplePluginTest`.
- The project targets Java 11 in Gradle, even if local IDE/Qodana uses a newer JDK.
- On this workstation, use Java 21 for Gradle because Lombok `1.18.30` does not compile under Java 25:
  `env JAVA_HOME=/usr/lib/jvm/java-21-temurin-jdk ./gradlew test`
- RuneLite dependencies use `latest.release`, so dependency behavior can drift over time.

## Java 11 Practices

Uphold these during normal changes:

- Keep source compatible with Java 11. Avoid records, text blocks, switch expressions, pattern matching, sealed classes, and APIs introduced after Java 11.
- Use a real JDK, not only a JRE. Local and CI environments need `javac` available for Gradle compilation.
- Prefer constructor injection and `private final` fields for service/controller dependencies.
- Use clear null handling. Validate inputs early, return before mutation when preconditions fail, and avoid passing null into methods that do not explicitly allow it.
- Do not silently swallow exceptions. Log useful context for config parsing, JSON persistence, amount parsing, and UI edit failures.
- Use parameterized logging, for example `log.debug("Failed to parse value {}", value, e);`.
- Keep model/business logic independent from Swing and RuneLite event classes where practical. UI confirmation and display should live in view/controller code, not core state classes.
- Use structured serializers/parsers for structured data. Prefer Gson for JSON rather than manual string concatenation.
- Keep dependencies conservative. Prefer Java/RuneLite APIs for small utilities unless a new dependency clearly reduces risk or complexity.

Work toward these over time:

- Reduce static mutable state such as global formatter config. Prefer passing configuration/defaults explicitly.
- Strengthen model encapsulation with private final fields and getters where it does not fight Swing table model usage.
- Move chat parsing into a small testable parser/service so `ManagerPlugin` stays thin.
- Remove stale commented-out code and keep TODOs specific to a missing behavior or known defect.
- Keep tests headless-friendly. Avoid direct dependencies on dialogs, clipboard, or live RuneLite client state unless mocked cleanly.

## Main Architecture

- `ManagerPlugin` is the RuneLite plugin boundary. It registers the toolbar panel and overlay, handles RuneLite events, parses chat messages, and adds chat/player context menu entries.
- `ManagerPanel` is the composition root for the sidebar UI. It creates `PanelController` and `PanelView`, and can rebuild the panel after certain config changes.
- `PanelController` owns Swing event handling and UI refresh orchestration. View mutations should generally flow through controller methods.
- `PanelView` is the Swing `PluginPanel`. Keep it mostly passive: render UI, expose components/models, and forward user actions through `PanelActions`.
- `ManagerSession` owns sessions, pending values, split math, roster changes, persistence of session JSON, and the current active session id.
- `ManagerKnownPlayers` owns known players plus alt-to-main mappings, persisted through hidden config values.
- `models/*Table.java` classes are Swing table models. `PlayerMetrics`, `Session`, `Kill`, `PendingValue`, and `Transfer` are the core data records.
- `utils/Formats.java`, `MarkdownFormatter.java`, and `PaymentProcessor.java` contain shared parsing/formatting/settlement logic.

## Session Model Invariants

- A new split thread starts with two `Session` objects:
  - a root "mother" session with `motherId == null`
  - an initial active child session whose `motherId` points to the root
- Kills are recorded only on the active child session.
- Roster changes after any kill/event exists fork a new child session under the same mother. The previous child is ended and the new child becomes current.
- Roster changes before any kill/event mutate the current child in place.
- `Kill.type == null` or `"LOOT"` is counted in split math.
- `Kill.type == "JOINED"` or `"LEFT"` is shown in recent splits but excluded from split math.
- Alt names should be resolved through `ManagerKnownPlayers.getMainName()` before adding players, kills, pending values, or checking roster membership.
- `computeMetricsFor(session, true)` is what the UI generally uses so inactive players from the thread remain visible.
- Current split sign convention: `split = sessionAverage - playerTotal`. Negative means that player owes; positive means that player should receive.

## Chat Detection

`ManagerPlugin.onChatMessage` only processes clan/friends chat messages when chat detection and the relevant channel toggles are enabled.

Supported detections:

- PvM drop messages matching `received a drop: ... (N coins)`
- PvP loot messages matching `has defeated ... and received (N coins) worth of loot!`
- Player `!add` commands with one or more values, such as `!add 100`, `!add 1.2m`, or `!add 100, 200m 300k`

Detected values become `PendingValue` entries unless `autoApplyWhenInSession()` is enabled and the suggested/resolved player is already in the active roster.

## Persistence

RuneLite `PluginConfig` is used as the backing store:

- `sessionsJson` stores all `Session` objects as JSON.
- `currentSessionId` stores the active child session id.
- `PlayersCsv` stores known players.
- `altsJson` stores alt-to-main mappings.
- `InstantTypeAdapter` must be registered on Gson instances that serialize session/player data containing `Instant`.

Avoid changing hidden config key names unless you also plan a migration path.

## Formatting And Units

- `Formats.OsrsAmountFormatter` parses OSRS amounts like `10k`, `1.1m`, `1b`, and `coins`.
- Raw amounts in model/math code are coin values, despite some older comments or variable names mentioning K.
- The default multiplier comes from `PluginConfig.defaultValueMultiplier()` when a user omits a suffix.
- `PaymentProcessor` treats negative `PlayerMetrics.split` values as payers and positive values as receivers.

## UI Notes

- This is Swing UI inside RuneLite, not a web frontend.
- Keep RuneLite UI code on established Swing patterns already present in `PanelView`.
- After model mutations, call the relevant controller or `ManagerPanel.refreshAllView()` path so metrics, waitlist, recent splits, and button states stay in sync.
- `ManagerPlugin.restartViewFix()` rebuilds the sidebar panel for config changes that affect structure.

## Testing Guidance

Existing tests cover:

- session lifecycle and split math in `ManagerSessionTest`
- alt/main behavior in `ManagerKnownPlayersTest`
- direct settlement routing in `PaymentProcessorTest`
- markdown output in `MarkdownFormatterTest`
- chat `!add` parsing in `ManagerPluginTest`

When changing behavior, add or update focused JUnit 4 tests near the affected class. For split math changes, include multi-segment roster-change scenarios because most regressions hide there.

## Unit Test Conventions

Use `src/test/java` for automated unit tests and keep the full RuneLite client launcher separate from those tests.

- Test names should follow `<ClassUnderTest>Test`, matching the current JUnit 4 style.
- Use JUnit 4 assertions and `@Test`; do not introduce JUnit 5 unless the project is deliberately migrated.
- Use Mockito for RuneLite boundaries such as `Client`, `PluginConfig`, `ClientToolbar`, `OverlayManager`, `ManagerPanel`, and `ManagerPlugin`.
- Prefer real domain objects and mocked RuneLite/client objects. Unit tests should not start RuneLite, log into OSRS, hit the network, require graphics, or depend on the user's RuneLite config directory.
- Stub config values explicitly. Mockito will not reliably exercise interface default methods unless configured to call real methods.
- Use a real `Gson` configured with `InstantTypeAdapter` when testing persistence or session JSON.
- Keep tests deterministic: avoid assertions based on wall-clock timestamps, random UUID values, Swing focus, table row selection side effects, or map ordering unless the ordering is part of the contract.
- Assert raw amounts in coins. Use formatted strings only when testing formatter/table output.
- For split math, assert both totals and split sign. Negative split means payer/owes; positive split means receiver/is owed.
- For multi-segment sessions, assert current roster, inactive players, child-session behavior, and final metrics. Most regressions in this plugin happen around roster changes after loot.
- For chat detection, construct `ChatMessage` objects directly, set `type`, `name`, and `message`, then verify the resulting `PendingValue` captured from `ManagerSession.addPendingValue`.
- For table models such as `Metrics`, `WaitlistTable`, and `RecentSplitsTable`, instantiate the model directly and assert `getValueAt`, editability, and mutation behavior.
- Avoid unit tests that invoke `JOptionPane`, clipboard access, or actual `NavigationButton`/toolbar registration. If behavior needs testing, move the decision into controller/domain code and leave the UI call as a thin shell.
- If Swing behavior must be tested, run UI mutation code on the EDT with `SwingUtilities.invokeAndWait`, and keep it headless-safe.

RuneLite-specific testing layers:

- **Domain unit tests:** `ManagerSession`, `ManagerKnownPlayers`, `Formats`, `PaymentProcessor`, and `MarkdownFormatter`. These should be the default for most changes.
- **Boundary unit tests:** `ManagerPlugin` event handlers with mocked config/session/panel dependencies and synthetic RuneLite events.
- **View/model tests:** Swing table models and formatting behavior without starting the RuneLite client.
- **Manual smoke test:** `com.example.ExamplePluginTest` loads `ManagerPlugin` via `ExternalPluginManager.loadBuiltin(...)` and launches RuneLite. Treat this as a manual development-client path, not an automated unit test.

Best setup for this RuneLite external plugin:

- Keep `testImplementation 'junit:junit:4.12'`, Mockito, `net.runelite:client`, and `net.runelite:jshell`.
- Keep automated verification on `./gradlew test`.
- Consider adding the official-template `run` Gradle task if local manual smoke testing is needed often. It should use `sourceSets.test.runtimeClasspath`, `com.example.ExamplePluginTest`, and pass `--developer-mode`/`--debug`.
- Run tests with a Java 11 JDK. A JRE-only install is not enough because Gradle needs `javac`.

Research references:

- RuneLite Plugin Hub README: Java 11 is the expected development setup, and local plugin launch is done through the Gradle `run` task.
- RuneLite `example-plugin` `build.gradle`: uses JUnit 4 plus `net.runelite:client` and `net.runelite:jshell` in `testImplementation`, and defines `run` as a test-runtime `JavaExec`.
- RuneLite `example-plugin` launcher: `ExamplePluginTest` uses `ExternalPluginManager.loadBuiltin(...)` before calling `RuneLite.main(args)`.
- RuneLite Developer Guide: plugin architecture centers on the plugin class/config/overlays/event subscribers/Swing panels, which should shape test boundaries.

## Current Cautions

- The repository may contain local uncommitted changes; check `git status --short` before editing and do not revert unrelated user work.
- There is an older `com.example.ExamplePluginTest` launcher even though the plugin package is `com.splitmanager`.
- `settings.gradle` still names the root project `example`.
- Some TODOs are intentional placeholders, including JSON export methods and direct payment UI limitations.
- `ManagerSession.stopSession` displays a `JOptionPane`, which makes complete unit testing awkward in headless tests.
- Be careful around `ManagerSession.sessionHasPlayer`; callers should avoid passing a null session.
