# Architecture

Toolkit for Lauterbach TRACE32® combines native IntelliJ language support with an
optional language server. Native editing works without a server process or a
Lauterbach TRACE32® installation.

## Modules and ownership

| Path | Responsibility |
| --- | --- |
| `practice-core/src/main/kotlin` | Restartable scanner, static semantic model, and curated catalog; no IntelliJ dependency |
| `practice-core/src/test` | JUnit core regressions and synthetic corpus resources |
| `intellij-plugin/src/main/kotlin` | PSI, editing, completion, navigation, refactoring, settings, and optional LSP integration |
| `intellij-plugin/src/main/resources` | Plugin descriptors, templates, and icon |
| `intellij-plugin/src/test` | JUnit behavior regressions and IntelliJ platform fixtures |
| `gradle` | Wrapper and shared dependency versions |
| `tools` | Publication checks and optional language-server provisioning/protocol checks |
| `examples` | Self-contained scripts for exploration and test input |

`intellij-plugin` depends on `practice-core`. The core has no dependency on the
plugin, the IDE SDK, or the language server. Existing package names identify the
language implementation; module boundaries govern build dependencies.

## Language analysis

`CmmScanner` produces restartable tokens. A separate highlighting lexer splits
macros inside strings while the parser retains whole string tokens. This keeps
highlighting useful without breaking PSI ranges for references and edits.

`CmmModel` derives static symbols, references, statement/comment folds, and
script paths. It handles logical lines, documented command aliases, and specific
event-action positions. Macro visibility is a lexical approximation; it does not
reconstruct the PRACTICE runtime stack or evaluate arbitrary macro substitutions.

`CmmCatalog` and `CmmDocumentation` supply curated command information. Unknown
target-specific commands are not automatically errors. Add aliases only when
supported by vendor documentation, with regression coverage for ambiguities.

## IntelliJ integration

The parser creates lightweight PSI atoms. References resolve through the static
model and use exact ranges, including macros inside strings. Providers are
registered in `META-INF/plugin.xml`.

Local rename first simulates declaration and reference edits to detect collisions
or changed bindings. It groups selected edits by PSI atom and replaces each atom
once, preserving repeated macro references inside a single string.

`CmmNavigationIndex` stores content-derived symbol and static script-call data.
It does not resolve project settings while indexing. Queries apply scope and
directory settings when resolving files. Recursive hierarchy edges stop further
expansion.

## Optional LSP integration

`META-INF/cmm-lsp.xml` loads only when the IDE provides
`com.intellij.modules.lsp`. It launches the configured `t32ls` executable
directly, without a shell, and passes configured directories as separate
arguments. Project settings control startup and lifecycle changes.

The server supplies definitions, references, and semantic tokens. Native code
supplies completion, documentation, diagnostics, formatting, templates, and local
rename. Native folding remains authoritative; server folding is disabled. Neither
layer opens a Lauterbach TRACE32® runtime connection.

## Distribution

The IntelliJ Platform Gradle Plugin packages the IDE integration and language
core into one installable ZIP. Gradle handles the compile classpaths, test
framework, sandbox, descriptor patching, and Plugin Verifier task. Resources
include the project's MIT license and third-party notices.

The distribution excludes tests, development scripts, compiler dependencies,
`t32ls`, and an additional Kotlin runtime. See [development](development.md),
[compatibility](compatibility.md), and the [structure rationale](project-structure.md).
