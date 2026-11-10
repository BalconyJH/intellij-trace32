# Development and verification

Run commands from the repository root. Build output and downloaded tools are ignored by Git.

See the [standalone verification record](verification.md) for the completed local checks and their limits.

## Toolchain

The full plugin build currently requires Windows, PowerShell 7.4+, a CLion 263 installation, and a full JDK compatible with the selected SDK. Hosted SDK builds use JDK 25 while the plugin targets JVM 21 bytecode. The bundled IDE runtime can be used when it includes `java` and `javac`; otherwise provide `JAVA_HOME` explicitly.

The build bootstraps Kotlin 2.4.21 from Maven Central using the pinned digests in `tools/kotlin-artifacts.json`. Cached artifacts are verified before reuse. It compiles against the selected IDE's libraries and does not bundle another Kotlin runtime.

The repository uses explicit SDK-based scripts instead of a Gradle build. No separately installed `kotlinc` is required.

## Build

Set `CLION_HOME` to the IDE installation directory, then run:

```powershell
.\build.ps1 -ClionHome $env:CLION_HOME
```

To use a separate full JDK:

```powershell
.\build.ps1 -ClionHome $env:CLION_HOME -JdkHome $env:JAVA_HOME
```

The build compiles production Kotlin, runs Java regression entry points, packages resources, and runs the core platform smoke suite. It rejects unsupported SDK versions and leftover Java production sources. The version is read from `src/main/resources/META-INF/plugin.xml`.

Output: `build/trace32-toolkit-0.2.0.zip`. The ZIP contains `trace32-toolkit/lib/trace32-toolkit.jar`.

`-KotlinHome` selects an already prepared compiler directory. `-SkipTests` and `-SkipSmoke` are development shortcuts; they cannot be combined with `-Install` and are not release validation.

## Test layers

The scanner and model regressions can run without an IDE SDK. They require PowerShell 7.4+ and a full JDK 21 or newer:

```powershell
.\tools\test-core.ps1 -JdkHome $env:JAVA_HOME
```

This lightweight suite does not compile or exercise the IntelliJ adapters.

| Suite | What it checks |
| --- | --- |
| `CmmScannerTest` | Token boundaries, restart equivalence, deterministic fuzz cases, and supplied script corpus |
| `CmmModelTest` | Symbols, scopes, references, logical lines, folds, paths, and event-action parsing |
| `CmmEditorTest` | Curated editing behavior and edge cases |
| `CmmNavigationTest` | Symbol/call data, serialization, scope filtering, and recursion behavior |
| `CmmCompletionRenameTest` | Actual command-position completion and rename binding/conflict rules |
| `CmmSmokeStarter` | Real IntelliJ core PSI, references, edits, providers, resources, and LSP descriptor behavior |
| `t32ls-smoke.py` | Real server stdio behavior using temporary scripts |

Run the platform suite against an existing built JAR:

```powershell
.\smoke.ps1 -ClionHome $env:CLION_HOME -JdkHome $env:JAVA_HOME
```

Use a valid full JDK value for the explicit `-JdkHome` argument. The full build also runs this suite. Platform reports are written under `build/smoke-*/result.txt`. Follow the [LSP guide](lsp.md#protocol-verification) for the separate server test.

Test corpus files must be checked-in, synthetic, and hardware-independent. A passing suite must not depend on another development checkout or a private Lauterbach TRACE32® installation.

Run the repository hygiene checks with Python 3:

```powershell
python .\tools\test_check_repository.py
python .\tools\check_repository.py
python .\tools\check_repository.py --artifact .\build\trace32-toolkit-0.2.0.zip
```

The final command requires a completed build. These checks enforce repository conventions and inspect the distribution for private data and unintended contents; they do not replace reviewing newly added files and logs.

## Manual IDE acceptance

Use an authorized CLion installation after automated checks pass:

1. Install the ZIP, restart, and confirm the plugin name and version.
2. Open `examples/editor-demo.cmm`. Check highlighting, completion, documentation, parameter hints, comments, folding, indentation, and live templates.
3. Use declaration navigation and usages. Rename a macro used several times in one string; inspect the preview and verify undo restores the original text. Confirm a conflicting rename is rejected.
4. After indexing, use Navigate Symbol and the script Call Hierarchy. Check caller/callee views, scope selection, file navigation, and recursive edges.
5. Configure `t32ls`, open a script, and inspect server startup. Check definitions and references, then change settings and disable LSP to exercise lifecycle handling.
6. Confirm native editing still works with LSP disabled.

Record the IDE build, operating system, plugin version, and exact checks performed. Keep GUI acceptance separate from core platform smoke results.

## CI and release checks

The workflows define [lightweight checks](../.github/workflows/ci.yml) for pushes to `main` and pull requests, a [manual full SDK check](../.github/workflows/sdk-build.yml), and [artifact packaging](../.github/workflows/release.yml) for version tags. Hosted SDK builds use the checksum-pinned public distribution in `tools/clion-sdk.json`; the download is approximately 2.5 GB. Check the workflow definitions for prerequisites and inputs.

Packaging uploads a workflow artifact. It does not create a public GitHub Release or publish to JetBrains Marketplace. A checked-in workflow is a specification of checks to run, not evidence that those checks have executed. SDK-independent jobs cannot validate platform APIs or GUI behavior.

For changes to descriptors, SDK assumptions, indexing, UI providers, or refactoring, run a full SDK build even if lightweight CI passes. See [releasing](releasing.md) for the artifact and compatibility review.
