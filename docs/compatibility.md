# Compatibility and limitations

## Current target

| Component | Scope |
| --- | --- |
| Plugin | `io.github.balconyjh.trace32`, version from `pluginVersion` in `gradle.properties` |
| IDE | CLion 2026.3 / IntelliJ Platform `263.*` |
| Default public SDK | `CL-263.7307.35`, configured by `platformVersion` |
| Local integration SDK | `CL-263.7307.209`; selectable with `localPlatformPath` |
| Build JDK / bytecode target | JDK 25 / JVM 25 |
| Kotlin | 2.4.21, API level 2.2 |
| Build | Gradle Wrapper and IntelliJ Platform Gradle Plugin 2.x |
| Optional server | `t32ls` 0.14.2; installer supports Windows x64 and ARM64 |
| Files | `.cmm`, `.CMM`, `.cmmt`, `.CMMT` |

The descriptor limits compatibility to `263.*`. Shared APIs do not establish
compatibility with IDEA, other JetBrains products, or different platform builds.
The local and downloadable SDKs are distinct builds; verification must identify
which one was used.

Gradle supplies Unix and Windows launchers. The CI core matrix includes Windows
and Linux, while the full hosted plugin workflow uses Windows. A workflow
configuration is not a completed operating-system compatibility test. Consult
[verification](verification.md) for actual results.

## Analysis limits

- The catalog, documentation, and signatures are curated. They do not cover every
  target, extension, or Lauterbach TRACE32® release.
- Macro scope is static. Runtime declarations, dynamic macro names, and
  execution-dependent substitutions can remain unresolved.
- Native rename and usages operate on resolvable symbols in the current file.
  Cross-file native rename is not implemented.
- Relative script navigation uses the current file directory. The actual
  Lauterbach TRACE32® working directory can differ at runtime.
- `~~` and `~~~` require configured system and temporary directories. `~~~~`
  uses the current script directory.
- Call hierarchy represents static script calls, not a live call stack or runtime
  `GOSUB` execution.
- Formatting changes leading indentation and preserves statement contents and
  continuation indentation.
- Diagnostics are conservative. No editor warnings does not guarantee a script
  is valid for a specific target or execution state.

## Verification boundaries

Core tests exercise scanner restart behavior and static semantics without an IDE.
Plugin tests exercise integration with the platform. Plugin Verifier checks
compatibility against the configured SDK; it does not operate the UI or target
hardware. The separate protocol test talks to a real `t32ls` process using
synthetic scripts.

Interactive hierarchy rendering, refactoring preview/undo, and IDE-managed LSP
lifecycle still require the [manual acceptance checks](development.md#manual-ide-acceptance).
Only checks recorded for a particular run should be reported as passed.
