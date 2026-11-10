# Changelog

Notable user-facing changes are recorded here. A version listed as unreleased is not a claim that a public release artifact exists.

## 0.2.0 - Unreleased

### Added

- Native PRACTICE editing for `.cmm` and `.cmmt`, including uppercase extensions.
- Contextual command, function, option, macro, label, and script-path completion.
- Curated documentation, parameter hints, structure view, project symbol indexing, and static script call hierarchies.
- File-local usages and rename with conflict checks and grouped updates for repeated string macros.
- Conservative structural diagnostics, indentation formatting, quote support, comment/block folding, color settings, and live templates.
- Optional native IntelliJ LSP integration with a separately installed `t32ls` 0.14.2 server.
- Checksum-pinned tool downloads, regression tests, core platform checks, and a real stdio LSP test.
- Standalone public repository documentation and build infrastructure.

### Changed

- Moved all production language-support code to Kotlin.
- Adopted the public name **Toolkit for Lauterbach TRACE32®** and plugin ID `io.github.balconyjh.trace32`.
- Removed dependencies on an external development workspace and machine-specific project configuration.

### Fixed

- Script-path completion and navigation now recognize actual command or event-action positions.
- Local rename rejects conflicting declarations and reference capture before modifying text.
- Repeated macro occurrences within one string are updated together during rename.

### Compatibility

- Targets CLion 2026.3 / IntelliJ Platform 263 with JVM 21 bytecode.
- Other IDE versions, runtime macro semantics, and cross-file native rename remain outside the current support scope.
