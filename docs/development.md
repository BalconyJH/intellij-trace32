# Development and verification

Run commands from the repository root. Use `./gradlew` on Unix-like systems and
`.\gradlew.bat` in Windows PowerShell. Set `JAVA_HOME` to a full JDK 25.
A system Gradle or Kotlin installation is not required.

## Build model

The Gradle Kotlin DSL build has two modules:

- `practice-core`: IDE-independent scanner, static semantic model, and catalog.
- `intellij-plugin`: IntelliJ adapters, editor features, settings, and optional LSP.

The wrapper pins Gradle and its distribution checksum. Dependency versions are
centralized in `gradle/libs.versions.toml`; plugin version and platform settings
are in `gradle.properties`. The plugin targets JVM 25 bytecode and uses the Kotlin
runtime supplied by the IDE. The core module uses the standard library for
compilation and tests without adding a duplicate runtime to the plugin ZIP.

The former standalone build, smoke, compiler bootstrap, and local plugin update
scripts have been removed. Gradle is the supported build and development entry
point.

Dependency checksums are committed in `gradle/verification-metadata.xml`.
Public SDK archive checksums come from JetBrains. Generated SDK descriptor XML
contains local paths, and explicitly selected local SDK directories cannot be
hashed as archives; narrowly scoped exceptions cover those artifacts for the
recorded builds. Downloaded archives and bundled JARs remain verified.
When intentionally updating dependencies or selecting a different SDK build,
regenerate metadata with `--refresh-dependencies --write-verification-metadata sha256`
so cached parent POMs and BOM metadata are also recorded. Review the changes
against upstream checksums, then rerun with
`--dependency-verification strict`. Do not accept unexpected checksum changes.

## Build and package

```shell
./gradlew :practice-core:test :intellij-plugin:check :intellij-plugin:buildPlugin
./gradlew :intellij-plugin:verifyPlugin
```

The distribution appears in `intellij-plugin/build/distributions/`. The archive
contains the plugin and language-core libraries, descriptors, templates, icons,
and license notices. Tests, compiler dependencies, and `t32ls` are excluded.
Install the ZIP using **Settings > Plugins > Install Plugin from Disk**.

The default SDK is the public CLion build configured by `platformVersion`.
To use an existing installation, set `CLION_HOME` locally and pass it explicitly:

```powershell
.\gradlew.bat "-PlocalPlatformPath=$env:CLION_HOME" :intellij-plugin:check :intellij-plugin:buildPlugin
.\gradlew.bat "-PlocalPlatformPath=$env:CLION_HOME" :intellij-plugin:verifyPlugin
```

Keep machine-specific values out of tracked Gradle properties and IDE settings.
A local SDK result does not establish compatibility with a different public SDK.

## Development sandbox

```shell
./gradlew :intellij-plugin:runIde
```

This opens a separate CLion sandbox with the plugin installed. The sandbox is
build output; it does not replace the plugin in the normal IDE profile. CLion
licensing and interactive setup still apply. A successful sandbox launch is
separate from completing feature acceptance checks.

## Automated tests

```shell
./gradlew :practice-core:test
./gradlew :intellij-plugin:test
```

JUnit discovers the tests and writes XML results under each module's
`build/test-results/` and HTML reports under `build/reports/tests/`.

| Suite | Scope |
| --- | --- |
| Core scanner | Token boundaries, restart equivalence, 350 deterministic fuzz cases, and eight synthetic scripts |
| Static model | Symbols, scopes, references, logical lines, folds, paths, and event actions |
| Editor regressions | Completion, documentation, highlighting, formatting, and rename rules |
| Navigation regressions | Index payloads, serialization, path resolution, and scope filtering |
| Platform fixtures | Real PSI/editor components, references, completion, refactoring, and project integration |
| Real `t32ls` protocol | Stdio initialization, document analysis, configured directories, updates, and shutdown |

The core module's corpus contains six resource files. Its Gradle resource task
also includes the two checked-in examples. Missing corpus resources fail the
test. No test may depend on a private checkout, a board, or a running
Lauterbach TRACE32® session.

Run the separate language-server check using the [LSP guide](lsp.md#protocol-verification).
Run publication checks with Python 3:

```shell
python tools/test_check_repository.py
python tools/check_repository.py
```

After packaging, add `--artifact` and the actual ZIP path from
`intellij-plugin/build/distributions/`. The guard checks nested JARs as well as
source files. The only allowed binary source file is the checksum-pinned Gradle
wrapper JAR; wrapper upgrades must update that pin after upstream verification.
These checks do not replace reviewing new files and logs.

## Manual IDE acceptance

Use the Gradle sandbox or an authorized CLion installation:

1. Confirm the plugin name and version, then open `examples/editor-demo.cmm`.
2. Check highlighting, completion, documentation, parameter hints, comments,
   folding, indentation, and live templates.
3. Navigate to declarations and usages. Rename a macro repeated in one string;
   inspect the preview and verify undo restores the text. Reject a conflicting
   rename without modifying the document.
4. After indexing, exercise Navigate Symbol and Call Hierarchy, including scope
   selection, file navigation, and recursive edges.
5. Configure `t32ls`, inspect startup, change settings, and disable LSP to verify
   the IDE-managed lifecycle. Confirm native editing still works without LSP.

Record the IDE build, operating system, plugin version, and exact checks. Fixture
assertions and Plugin Verifier do not establish GUI rendering, preview/undo, or
language-server lifecycle behavior by themselves.

## CI and release checks

[CI](../.github/workflows/ci.yml) validates repository content, the Gradle
wrapper, and workflow syntax with checksum-pinned `actionlint` 1.7.12. It runs
core tests on Windows and Linux, exercises the real language
server, and calls the [plugin verification workflow](../.github/workflows/sdk-build.yml).
That workflow runs plugin tests, builds the ZIP, runs Plugin Verifier, and audits
the archive. It can also run manually.

The [tag workflow](../.github/workflows/release.yml) requires a tag matching the
Gradle project version and uploads the verified ZIP with a SHA-256 file. It does
not publish to GitHub Releases or JetBrains Marketplace. Hosted workflow results
must be reviewed independently of local runs.

See [verification](verification.md) for recorded evidence and
[releasing](releasing.md) for release preparation.
