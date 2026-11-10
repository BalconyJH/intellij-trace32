# Verification record

The standalone repository was verified on 2026-11-10 on Windows x64, using
CLion SDK `CL-263.7307.209`, JDK 25, Kotlin 2.4.21, and JVM target 21.
The checks used only synthetic scripts from this repository or temporary
protocol fixtures. No hardware connection or PRACTICE execution was involved.

| Check | Result |
| --- | --- |
| Full plugin compilation and packaging | Passed |
| Completion and rename regressions | 54 checks passed |
| Editor regressions | 62 checks passed |
| Navigation regressions | 28 checks passed |
| Static language model | 88 checks passed |
| Scanner | 33,699 assertions passed, including 350 restart fuzz cases and 8 repository scripts |
| IntelliJ core platform harness | 75 checks passed |
| Real `t32ls` 0.14.2 stdio integration | 9 checks passed; clean shutdown with exit code 0 |
| Publication guard regressions | 10 tests passed |
| Repository and packaged ZIP/JAR hygiene | Passed |

The packaged artifact is `trace32-toolkit-0.2.0.zip`. Its SHA-256 for this run is:

```text
35b7125abe9580eac140e4d401e9a7abf425b8f2537df061fbad88166532198c
```

Archive timestamps can change between builds; this digest identifies this
specific artifact, not every future build of version 0.2.0.

The build emitted compiler/platform deprecation and lightweight registry
initialization warnings. None failed the checked assertions.

The GitHub-hosted workflows and public SDK `CL-263.7307.35` were not run as part
of this local verification. Interactive IDE acceptance, including actual index
queries, hierarchy rendering, rename preview/undo, and the IDE-managed LSP
lifecycle, remains separate from the core platform harness. See
[compatibility](compatibility.md) and the
[manual acceptance procedure](development.md#manual-ide-acceptance).

Raw build logs and local installation manifests are ignored by Git. This record
contains only portable verification details; the LSP report records the server
filename instead of its local installation path.
