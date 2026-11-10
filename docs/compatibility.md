# Compatibility and limitations

## Current target

| Component | Scope |
| --- | --- |
| Plugin | `io.github.balconyjh.trace32`, version 0.2.0 |
| IDE | CLion 2026.3 / IntelliJ Platform `263.*` |
| SDK used for integration development | `CL-263.7307.209` |
| Public SDK pinned for hosted packaging | `CL-263.7307.35`; see `tools/clion-sdk.json` |
| JVM target | 21 |
| Kotlin compiler | 2.4.21, with API level 2.2 |
| Build and provisioning scripts | Windows and PowerShell 7.4+ |
| Optional server | `t32ls` 0.14.2; provided installer supports Windows x64 and ARM64 |
| Files | `.cmm`, `.CMM`, `.cmmt`, `.CMMT` |

The descriptor intentionally limits the platform range to `263.*`. Shared IntelliJ APIs do not by themselves establish compatibility with IDEA, other products, or other platform builds. Those combinations need explicit validation before the support range changes.

The local development SDK and downloadable CI SDK are distinct builds. A passing local run does not establish the result of a hosted workflow against the public SDK; inspect that workflow's result before publishing its artifact.

## Analysis limits

- The catalog, documentation, and option signatures are curated. They do not cover every target, extension, or Lauterbach TRACE32® release.
- Macro scope is modeled statically. Runtime declarations, dynamic macro names, and execution-dependent substitutions can remain unresolved.
- Native rename and usages operate on statically resolvable symbols in the current file. Cross-file native rename is not implemented.
- Static script navigation uses the current file directory as its relative base. The actual Lauterbach TRACE32® working directory can differ at runtime.
- `~~` and `~~~` require configured system and temporary directories. `~~~~` uses the current script directory.
- Call hierarchy describes static script calls. It is not a live call stack and does not model runtime `GOSUB` execution.
- Formatting changes leading indentation only; it deliberately preserves statement contents and continuation indentation.
- Diagnostics are conservative. A script without editor warnings is not necessarily valid for a particular target or execution state.

## Verification boundaries

The automated suites cover scanner restart behavior, static semantics, editing helpers, index payloads, real PSI references and edits, extension class loading, and the LSP descriptor. The stdio test talks to a real `t32ls` process using temporary synthetic scripts.

The core platform harness uses lightweight editor and parameter-context substitutes. It is not a full running IDE. Actual file-index queries, hierarchy rendering, refactoring preview and undo, and the IDE-managed LSP lifecycle require the [manual acceptance checks](development.md#manual-ide-acceptance).

Build/test output records the result of a specific run. A source migration, a passing helper test, or a configured CI workflow is not evidence that every GUI action or every supported IDE build has been tested.
