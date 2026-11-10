# Release process

Release artifacts must come from the reviewed source and the intended CLion SDK. A successful build is required; an interactive IDE acceptance record is a separate check.

## Prepare a version

1. Update the version in `src/main/resources/META-INF/plugin.xml` and the unreleased entry in `CHANGELOG.md`.
2. Update versioned artifact examples in the README and development guide.
3. Review the declared IDE range. Do not widen `since-build` or `until-build` merely because compilation succeeds.
4. Run the full build, the real `t32ls` protocol check when applicable, and the relevant manual acceptance checks.
5. Check the working tree for private paths, credentials, local settings, generated reports, caches, and proprietary fixtures.

## Inspect the distribution

The ZIP should contain only the plugin directory and JAR. Inspect the JAR for the main descriptor, optional LSP descriptor, icon, templates, and compiled plugin classes. Tests, tool installers, compiler dependencies, extra Kotlin libraries, and `t32ls` must not be bundled.

Run the automated repository and artifact checks before reviewing the archive:

```powershell
python .\tools\test_check_repository.py
python .\tools\check_repository.py --artifact .\build\trace32-toolkit-0.2.0.zip
```

Generate a checksum for the final ZIP:

```powershell
Get-FileHash .\build\trace32-toolkit-0.2.0.zip -Algorithm SHA256
```

Keep the checksum with the release asset. Build logs and reports may include machine paths and must be sanitized before publication.

## Publish

Use a version tag of the form `v0.2.0` matching the plugin descriptor. Follow the checked-in release workflow's prerequisites and inputs. Review generated artifacts and release notes before making the release public.

Release notes should state the tested IDE build, important changes, native versus optional LSP responsibilities, and known limitations. Do not include a developer machine's configuration or claim that unrun CI or GUI checks passed.

The packaging workflow uploads build artifacts; it does not create a GitHub Release or publish to JetBrains Marketplace. Marketplace signing, credentials, publisher ownership, and submission are separate release configuration work.

## Upgrade and rollback

The public plugin ID is `io.github.balconyjh.trace32`. Earlier development builds may have used another identity; users should disable those builds to prevent duplicate language registrations.

The local `-Install` helper preserves a backup before replacing an installed plugin. Locked JARs are updated through the IDE's startup queue after a restart. For a manual rollback, close the IDE and reinstall a previously verified ZIP through the plugin manager; do not replace mapped JAR contents while the IDE is running.
