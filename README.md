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

## Install and try it

1. Download the `trace32-toolkit` artifact from a successful [CI run](https://github.com/BalconyJH/intellij-trace32/actions/workflows/ci.yml), or [build the plugin](#build-and-develop) locally.
2. Extract the downloaded artifact archive to find the plugin ZIP and its SHA-256 file. Keep the plugin ZIP intact.
3. In CLion, open **Settings > Plugins > Install Plugin from Disk**, select `trace32-toolkit-<version>.zip`, and restart the IDE.
4. Open [examples/editor-demo.cmm](examples/editor-demo.cmm) to explore completion, navigation, documentation, and refactoring.

For a local build, select the ZIP in `intellij-plugin/build/distributions/` directly. If an older development copy of this plugin is installed, remove it before installing the new one to avoid duplicate file associations.

Native editing features work immediately without a language server. Opening a script does not execute it.

### Editor shortcuts

These shortcuts use the default Windows keymap. Use **Find Action** for other keymaps.

| Action | Shortcut |
| --- | --- |
| Complete code or a script path | `Ctrl+Space` |
| Go to declaration | `Ctrl+B` |
| Quick documentation | `Ctrl+Q` |
| Parameter info | `Ctrl+P` |
| Find usages | `Alt+F7` |
| Rename | `Shift+F6` |
| Script call hierarchy | `Ctrl+Alt+H` |
| Toggle line comment | `Ctrl+/` |
| Reformat indentation | `Ctrl+Alt+L` |

Live templates: `cmmif`, `cmmifelse`, `cmmwhile`, `cmmrepeat`, `cmmsub`, and `cmmerror`.

Customize colors under **Settings > Editor > Color Scheme > Lauterbach TRACE32® PRACTICE**.

## Optional language server

The plugin includes the LSP integration code. The `t32ls` executable is installed separately, and LSP is disabled by default for new projects.

On Windows, run the installer from the repository root to download and verify the pinned server release:

```powershell
.\tools\t32ls-install.ps1
```

Then open **Settings > Languages & Frameworks > Lauterbach TRACE32®**:

1. Enable the language server.
2. Select the executable path reported by the installer.
3. Optionally configure the system directory for `~~` paths and the temporary directory for `~~~` paths.
4. Apply the settings and open a script.

`~~~~` resolves to the current script directory. Directory settings also support native path completion and navigation when LSP is disabled.

See [LSP setup and troubleshooting](docs/lsp.md) for capabilities, protocol checks, and configuration details. The language server analyzes scripts; it does not provide debugger or target-control functionality.

## Build and develop

Set `JAVA_HOME` to a full **JDK 25** and open the repository root as a Gradle project. The checked-in Gradle Wrapper downloads the pinned build tools and configured CLion SDK.

Run tests, package the plugin, and verify API compatibility:

```shell
./gradlew :practice-core:test :intellij-plugin:check
./gradlew :intellij-plugin:buildPlugin
./gradlew :intellij-plugin:verifyPlugin
```

In Windows PowerShell, replace `./gradlew` with `.\gradlew.bat`. The plugin ZIP is generated under `intellij-plugin/build/distributions/`.

Launch a separate CLion development sandbox with the plugin installed:

```shell
./gradlew :intellij-plugin:runIde
```

The project has two modules: `practice-core` contains the IDE-independent scanner, semantic model, and catalog; `intellij-plugin` contains editor integration, settings, and LSP support.

CI runs core tests on Linux and Windows, plugin tests on Windows, real `t32ls` protocol checks, Plugin Verifier, and repository and archive checks. See the [development guide](docs/development.md) for local SDK configuration, test commands, and manual IDE acceptance checks.

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
