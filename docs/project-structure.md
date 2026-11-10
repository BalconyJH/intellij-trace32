# Project structure

Toolkit for Lauterbach TRACE32® uses a Gradle Kotlin DSL build with two modules.
The layout separates language analysis from IntelliJ integration and exposes
standard tasks for testing, sandbox development, packaging, and verification.

## Layout

```text
intellij-trace32/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradlew
  gradlew.bat
  gradle/
    libs.versions.toml
    wrapper/
  practice-core/
    build.gradle.kts
    src/main/kotlin/
    src/test/java/
    src/test/resources/corpus/
  intellij-plugin/
    build.gradle.kts
    src/main/kotlin/
    src/main/resources/
    src/test/java/
  tools/
  examples/
  docs/
  .github/workflows/
```

| Component | Responsibility |
| --- | --- |
| `practice-core` | Catalog, restartable scanner, static model, and core JUnit tests |
| `intellij-plugin` | PSI, editor providers, navigation, refactoring, settings, optional LSP, and platform tests |
| `gradle/libs.versions.toml` | Shared dependency and Gradle plugin versions |
| `gradle.properties` | Project version, platform target, and build settings |
| `gradle/wrapper` | Pinned Gradle launcher and distribution checksum |
| `tools` | Publication checks and separate language-server provisioning/protocol checks |

The plugin module depends on the core. The core has no IntelliJ dependency.
Its tests run without an IDE SDK, while plugin tests use the platform test
framework. Java tests intentionally remain supported alongside production Kotlin.

Two modules match the current implementation. Additional runtime or debugger
modules should be introduced with those features. Shared convention plugins in
`build-logic` become useful when multiple modules repeat meaningful build logic;
this project does not need that extra layer yet.

## Build lifecycle

Use `:practice-core:test` for independent language tests,
`:intellij-plugin:check` for plugin checks, `:intellij-plugin:buildPlugin` for the
installable ZIP, `:intellij-plugin:runIde` for a development sandbox, and
`:intellij-plugin:verifyPlugin` for IDE API compatibility verification.

The current build replaces the former manual compiler, SDK-fetch, archive,
smoke-launch, and active-profile installation scripts. The auxiliary `t32ls`
installer remains separate because the language server is optional and is not
part of the plugin distribution.

Normal builds resolve the configured public CLion SDK. Developers can supply a
local SDK through `localPlatformPath`; those results must identify the selected
build. No machine-specific SDK or credential setting belongs in tracked source.

The publication guard recognizes module-local fixtures and excludes nested
build output. It permits only the exact, checksum-verified Gradle wrapper JAR as
a source binary. The CI wrapper-validation action independently checks wrapper
provenance.

## Upstream references

These repositories were inspected on 2026-11-10. The references pin the reviewed
source rather than assuming their default branches remain unchanged.

| Reference | Structure used as a reference |
| --- | --- |
| [JetBrains plugin template, `c7c9f997a7e6`](https://github.com/JetBrains/intellij-platform-plugin-template/tree/c7c9f997a7e67920efc534202e7ec29684d3b161) | Gradle Kotlin DSL, wrapper, platform tests, and separate build/test/verification jobs |
| [IntelliJ D Language, `263ceef9e8c7`](https://github.com/intellij-dlanguage/intellij-dlanguage/tree/263ceef9e8c7dfd8ed6845373d80f93f50fe718c) | Language/PSI/IDE modules, a dependency version catalog, and feature-specific fixtures |
| [IdeaVim, `42dfc92c5367`](https://github.com/JetBrains/ideavim/tree/42dfc92c5367f5ddede4afb14fdc831fa633933c) | Separate engine, IDE integration, and specialized test modules |
| [detekt, `fbf7c543c2f0`](https://github.com/detekt/detekt/tree/fbf7c543c2f03a4339843c536b433fba89b449ce) | Kotlin modules, shared test fixtures, version catalogs, and reusable build conventions |

The repositories have different scopes. Their IDEA targets, module counts, and
release automation were not adopted as compatibility or publication guarantees.
The implementation uses [IntelliJ Platform Gradle Plugin 2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)
and its [standard tasks](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html).
See [development](development.md) for commands and [verification](verification.md)
for results actually observed in this repository.
