# Verification record

This record covers the Gradle multi-module migration, verified on 2026-11-10
on Windows x64. Results below come from the replacement Gradle build.

## Migration checks

| Check | Recorded result |
| --- | --- |
| Publication guard unit tests | 12 passed |
| Gradle wrapper provenance | Official distribution and wrapper SHA-256 pins verified |
| Core JUnit tests | 27 passed; no failures, errors, or skipped tests |
| Plugin regressions and platform fixtures | 29 passed: 22 platform fixtures, 4 asynchronous completion-popup tests, and 3 regression suites; no failures, errors, or skipped tests |
| Dependency verification | `check` and `buildPlugin` passed in strict mode against the public SDK; default public SDK coordinates also resolved successfully |
| Plugin packaging and archive hygiene | ZIP built with searchable options; source and archive publication checks passed |
| Plugin structure and project configuration | Both tasks passed; the configuration task recommends removing the intentionally bounded `until-build` |
| Plugin Verifier | 1.411 reports **Compatible** against CL-263.7307.35, with no deprecated or internal API usages reported |
| Real `t32ls` protocol | 0.14.2 passed all 9 protocol checks and exited with code zero |
| Hosted CI | Not run locally |
| Interactive IDE acceptance | Separate manual verification remains required |

The verified toolchain is OpenJDK 25.0.2, JVM target 25, Gradle 9.8.1, Kotlin
2.4.21, and IntelliJ Platform Gradle Plugin 2.20.1. The SDK is public CLion
2026.3, build **CL-263.7307.35**. Its official Windows SDK archive was checked
against SHA-256
`65094ff8732d8d7710ef8bb851b1c8b1d77c2001b4a04f5d3000f7f0f9b709d7`
and extracted locally for `localPlatformPath`. Resolving the default SDK
coordinates is not evidence of a complete clean download and build in hosted CI.

JUnit reports are written beneath each module's `build/test-results/` and
`build/reports/tests/`. Plugin Verifier reports are generated beneath
`intellij-plugin/build/reports/pluginVerifier/`. The upper build bound remains
`263.*` because compatibility with later platform branches has not been tested.

The scanner corpus consists of six resource fixtures and two example scripts.
The deterministic restart fuzz test retains 350 generated cases. All fixtures
are synthetic; the tests do not connect to target hardware or execute PRACTICE.

Platform fixtures exercise actual descriptor registration, file associations,
highlighting, completion and insertion, case-sensitive symbols, comments,
folding, formatting with blank-line preservation, diagnostics, parameter hints,
navigation, symbol indexing, usages, rename, and optional LSP configuration.
Completion-popup tests cover command dots, macro ampersands, and both script-path
separators. The three regression suites retain 144 direct assertions.
The final fixture run also completed without startup errors in the captured
test logs.

The real language-server test covers initialization, local and cross-file
definitions, configured system/temp path prefixes, references, folding,
semantic tokens, document changes, and shutdown. It does not establish that a
graphical IDE session or a target-debugging connection was exercised.

## Verified artifact

- File: `intellij-plugin/build/distributions/trace32-toolkit-0.2.0.zip`
- SHA-256: `f26491780ca89d794d81941432003d9b6dbeeea92220fd7899655d5ff2eafab9`
- Contents: plugin JAR, core JAR, and generated searchable-options JAR.
- Metadata: plugin version `0.2.0`, platform range `263` through `263.*`.
- Resources: plugin descriptor, icon, templates, MIT license, and third-party notices.
- No bundled Kotlin standard library, test classes, compiler, SDK, or `t32ls` executable.

This checksum identifies the local verified artifact, not a published release.
Retain relevant reports outside Git and repeat the release checks when inputs
change. See [development](development.md) for commands and
[compatibility](compatibility.md) for the scope of the evidence.
