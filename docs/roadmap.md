# Roadmap

The current scope is a PRACTICE editor and language-analysis toolkit. Future work below is a direction, not a statement of implemented support or a delivery commitment.

## Implemented baseline

- Kotlin language core and native IntelliJ editing providers.
- Contextual completion, curated documentation/signatures, conservative diagnostics, templates, folding, and indentation formatting.
- Local symbol navigation, collision-aware rename, project symbol index, and static script call hierarchy.
- Optional `t32ls` integration with project settings and checksum-pinned provisioning.
- Gradle Kotlin DSL with separate language-core and IntelliJ plugin modules.
- JUnit regressions, IntelliJ platform fixtures, synthetic examples, and public build documentation.
- Gradle sandbox, packaging, and Plugin Verifier tasks with shared CI entry points.

## Next validation work

- Run and record full IDE acceptance for hierarchy UI, refactoring preview/undo, and IDE-managed LSP lifecycle.
- Establish a verified product/version matrix before supporting IDEA or widening the platform range.
- Improve command coverage with documented aliases, signatures, and regression fixtures.
- Expand verified operating-system coverage for plugin builds and language-server installation.

## Potential feature work

- Cross-file refactoring with clear ownership and scope semantics.
- Better explanations for dynamic macro paths and runtime-dependent symbols that static analysis cannot resolve.
- An explicit runtime integration layer for running scripts and connecting a debugger, with separate configuration and execution controls.
- Debug adapter integration and optional automation interfaces, designed independently from LSP.

Target control would require a Lauterbach TRACE32® installation and the appropriate runtime interfaces. `t32ls` is a language server and will not provide those operations. No debug adapter, board configuration, or flashing workflow is included in the current repository.

## References

- [Lauterbach PRACTICE reference](https://www.lauterbach.com/pdf/practice_ref.pdf)
- [Lauterbach PRACTICE training](https://www.lauterbach.com/pdf/training_practice.pdf)
- [JetBrains custom language support](https://plugins.jetbrains.com/docs/intellij/custom-language-support-tutorial.html)
- [JetBrains LSP integration](https://plugins.jetbrains.com/docs/intellij/language-server-protocol.html)
- [t32ls upstream](https://github.com/xasc/t32-language-server)
