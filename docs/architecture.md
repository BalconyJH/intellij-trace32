# Architecture

Toolkit for Lauterbach TRACE32® combines native IntelliJ language support with an optional language server. The native layer is usable without a server process or a Lauterbach TRACE32® installation.

## Source layout

| Path | Responsibility |
| --- | --- |
| `src/main/kotlin/io/github/balconyjh/trace32/cmm/core` | Scanner, static semantic model, and curated command catalog |
| `src/main/kotlin/io/github/balconyjh/trace32/cmm` | PSI adapters and editor, navigation, refactoring, settings, and UI providers |
| `src/main/kotlin/io/github/balconyjh/trace32/cmm/navigation` | File-content index data and serialization |
| `src/main/kotlin/io/github/balconyjh/trace32/cmm/lsp` | Optional native LSP integration and settings-driven lifecycle |
| `src/main/resources` | Plugin descriptors, templates, and icon |
| `src/test/java` | Core and behavior regressions using the Kotlin JVM API |
| `src/smoke/java` | IntelliJ core platform integration checks |
| `tools` | Pinned tool provisioning, protocol checks, and installation helpers |
| `examples` | Self-contained scripts for exploration and test input |

## Language analysis

`CmmScanner` produces restartable tokens. A separate highlighting lexer can split macros inside strings while the parser retains whole string tokens. This keeps highlighting useful without breaking PSI ranges for references and edits.

`CmmModel` derives static symbols, references, statement/comment folds, and script paths. It handles logical lines, documented command aliases, and specific event-action positions. Macro visibility is a lexical approximation; it does not reconstruct the PRACTICE runtime stack or evaluate arbitrary macro substitutions.

`CmmCatalog` and `CmmDocumentation` supply curated command information. Unknown target-specific commands are not automatically errors. Add aliases only when supported by vendor documentation, with a regression for ambiguous abbreviations.

## IntelliJ adapters

The parser creates lightweight PSI atoms. References resolve through the static model and use exact ranges, including macros inside strings. Native completion, documentation, parameter info, diagnostics, folding, templates, and indentation formatting are registered in `META-INF/plugin.xml`.

Local rename first simulates the declaration and reference edits to detect collisions or changed bindings. It then groups selected edits by PSI atom and replaces each atom once. Grouping prevents one string replacement from invalidating the pointers for other macro occurrences in that string.

`CmmNavigationIndex` stores content-derived symbol and static script-call information. It does not resolve project settings while indexing. Queries apply scope and directory settings when resolving files. Recursive hierarchy edges stop further expansion.

## Optional LSP layer

`META-INF/cmm-lsp.xml` is loaded only when the IDE provides `com.intellij.modules.lsp`. The integration launches the configured `t32ls` executable directly, without a shell, and passes the configured system and temporary directories as individual arguments.

Project settings control whether the server starts. Applying a configuration change notifies the optional lifecycle listener. Native folding remains authoritative; LSP folding is disabled to avoid duplicate regions.

The server supplies definitions, references, and semantic tokens. Native code supplies completion, documentation, diagnostics, formatting, templates, and local rename. The integration does not introduce a Lauterbach TRACE32® runtime connection.

## Distribution

The plugin ZIP contains the plugin JAR and its resources. It does not bundle the Kotlin compiler, an extra Kotlin standard library, `t32ls`, Lauterbach TRACE32®, tests, or development scripts. The plugin uses the Kotlin runtime provided by the supported IDE.

See [development](development.md) for checks and [compatibility](compatibility.md) for the limits of the current validation.
