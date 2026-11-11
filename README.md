# Toolkit for Lauterbach TRACE32®

PRACTICE script editing for CLion, with syntax highlighting, contextual completion, navigation, and local refactoring. Written in Kotlin, the plugin supports `.cmm` and `.cmmt` files and offers optional language-server integration through [t32ls](https://github.com/xasc/t32-language-server).

**Supported IDE:** CLion 2026.3 / IntelliJ Platform `263.*`. See [compatibility](docs/compatibility.md) for the configured SDK, tested scope, and analysis limits.

## Features

| Feature | What you can do |
| --- | --- |
| Syntax highlighting | Read commands, functions, labels, comments, numbers, strings, and embedded `&name` / `&(name)` macros. |
| Contextual completion | Complete curated commands and functions, documented abbreviations, common options, visible macros, labels, and script paths. |
| Navigation | Jump to local declarations and static script paths, browse the file structure, search project symbols, and inspect script callers and callees. |
| Documentation | View quick documentation and parameter hints for curated command and function signatures. |
| Editing | Toggle line comments, fold blocks and comments, pair brackets and quotes, format indentation, and insert live templates. |
| Local refactoring | Find usages and rename resolvable symbols within a file, with collision and binding checks. |
| Diagnostics | Identify structural errors, duplicate labels, and unresolved local branch targets. |
| Optional LSP | Add definitions, references, and semantic highlighting through a separately installed `t32ls` server. |

The command catalog is curated, and macro analysis is static. Formatting adjusts indentation; call hierarchies describe static script calls. See [analysis limits](docs/compatibility.md#analysis-limits) for details.

The current scope is editing and language analysis. Script execution, probe connections, target control, and debug-adapter support are tracked in the [roadmap](docs/roadmap.md).

## Build and install

Install JDK 25 and set `JAVA_HOME` to that JDK. Open the repository root as a Gradle project. The checked-in Gradle Wrapper downloads the pinned Gradle version; the build resolves Kotlin and the configured CLion SDK.

From the repository root:

```shell
./gradlew :practice-core:test :intellij-plugin:check :intellij-plugin:buildPlugin
```

On Windows PowerShell, use `.\gradlew.bat` in place of `./gradlew`. The distribution is written to `intellij-plugin/build/distributions/`. Run `./gradlew :intellij-plugin:verifyPlugin` to check compatibility against the configured IDE.

In CLion, choose **Settings > Plugins > Install Plugin from Disk**, select the ZIP, and restart. If an earlier development version of this language plugin is installed, disable or uninstall it first to avoid duplicate file associations and extensions.

For development, run `./gradlew :intellij-plugin:runIde` to launch a separate CLion sandbox with the plugin installed. To use a local SDK, pass `-PlocalPlatformPath` as described in the [development guide](docs/development.md).

Open [examples/editor-demo.cmm](examples/editor-demo.cmm) to try the editor. Opening a script does not execute it. Build details and test commands are in the [development guide](docs/development.md).

## Optional language server

`t32ls` is **not bundled in the plugin ZIP**. Native editing features work without it, and LSP is disabled by default for new projects.

To download the pinned Windows server:

```powershell
.\tools\t32ls-install.ps1
```

Open **Settings > Languages & Frameworks > Lauterbach TRACE32®**, enable the language server, and select the executable reported by the installer. Optionally configure the Lauterbach TRACE32® system and temporary directories to resolve `~~` and `~~~` paths. `~~~~` refers to the current script directory.

See [LSP setup and troubleshooting](docs/lsp.md) for configuration, capabilities, and protocol tests.

## Common actions

These shortcuts use the default Windows keymap; use **Find Action** if your keymap differs.

| Action | Shortcut |
| --- | --- |
| Complete code or a script path | `Ctrl+Space` |
| Go to declaration | `Ctrl+B` |
| Quick documentation / parameter info | `Ctrl+Q` / `Ctrl+P` |
| Find usages / rename | `Alt+F7` / `Shift+F6` |
| Script call hierarchy | `Ctrl+Alt+H` |
| Toggle line comment / reformat indentation | `Ctrl+/` / `Ctrl+Alt+L` |

Live templates: `cmmif`, `cmmifelse`, `cmmwhile`, `cmmrepeat`, `cmmsub`, and `cmmerror`. Colors are configurable under **Editor > Color Scheme > Lauterbach TRACE32® PRACTICE**.

## Project information

- [Architecture](docs/architecture.md)
- [Project structure and upstream references](docs/project-structure.md)
- [Development and verification](docs/development.md)
- [Latest local verification record](docs/verification.md)
- [Compatibility and limitations](docs/compatibility.md)
- [Release process](docs/releasing.md)
- [Roadmap](docs/roadmap.md) and [changelog](CHANGELOG.md)
- [Contributing](CONTRIBUTING.md) and [security reporting](SECURITY.md)

Licensed under the [MIT License](LICENSE). This project is independent and is not affiliated with or endorsed by Lauterbach or JetBrains. See [third-party and trademark notices](THIRD_PARTY_NOTICES.md) for product attribution and external software licenses.
