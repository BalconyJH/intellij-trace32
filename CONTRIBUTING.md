# Contributing

Issues and focused pull requests are welcome. Use English for source comments, documentation, issues, and pull request descriptions.

## Before making a change

Read the [architecture](docs/architecture.md) and [compatibility](docs/compatibility.md) notes. For a behavior change, describe a small PRACTICE example, the observed behavior, and the expected result. Link to the relevant public vendor documentation when changing language rules or command abbreviations.

Keep language editing separate from target control. Adding a command to completion must not cause it to execute. Do not infer undocumented command aliases by removing lowercase letters from a command name.

## Development workflow

1. Create a focused branch and make the smallest change that addresses the issue.
2. Add a regression when it exercises a real language or integration failure. Preserve restartable lexer behavior and exact text ranges.
3. Run the relevant Gradle tasks from the [development guide](docs/development.md). Run plugin tests, packaging, and Plugin Verifier for changes to platform adapters, resources, or packaging.
4. Update documentation and the unreleased changelog when user-visible behavior changes.
5. Open a pull request explaining the problem, resulting behavior, and verification performed. Identify any checks you could not run.

The production implementation is Kotlin. JUnit tests may use Java or Kotlin. Keep the `practice-core` module independent of IntelliJ APIs, and put platform integration in `intellij-plugin`. Test fixtures must be self-contained and synthetic; do not depend on another checkout, a specific board, a probe, or an installed Lauterbach TRACE32® session.

## Repository hygiene

- Do not commit IDE settings, build output, downloaded tools, caches, backups, or local test reports.
- Do not add private project names, credentials, personal filesystem paths, hardware identifiers, customer scripts, or proprietary logs.
- Use relative paths, environment variables, or synthetic fixture paths in examples.
- Keep public examples safe to inspect without requiring target hardware. Clearly describe any future feature that executes commands.
- Preserve third-party license notices. Prefer links to vendor manuals over redistributing them.

Issue reproductions and attached logs should be reduced and sanitized before submission. Do not include a full workspace or configuration archive unless the relevant files have been reviewed.

## Review expectations

Platform fixture tests do not replace an interactive IDE check. Changes to hierarchy rendering, refactoring preview/undo, or LSP lifecycle should include the applicable manual checks from the development guide. Compatibility with a new IDE build must be demonstrated before widening the plugin descriptor's version range. Preserve wrapper and dependency verification when updating build tools.
