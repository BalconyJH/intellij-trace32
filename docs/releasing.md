# Release process

Build release artifacts from reviewed source using the intended CLion SDK.
Automated tests, API compatibility verification, and interactive IDE acceptance
provide different evidence and should be recorded separately.

## Prepare a version

1. Update `pluginVersion` in `gradle.properties` and the unreleased changelog.
2. Review `pluginSinceBuild`, `pluginUntilBuild`, and `platformVersion`. Widen
   compatibility only after testing the additional IDE builds.
3. Run the Gradle tests, package task, and Plugin Verifier:

   ```shell
   ./gradlew :practice-core:test :intellij-plugin:check :intellij-plugin:buildPlugin
   ./gradlew :intellij-plugin:verifyPlugin
   ```

4. Run the real `t32ls` protocol check when relevant and perform applicable manual
   IDE acceptance checks.
5. Review source, examples, reports, and archive contents for private data.

The wrapper and dependency versions must remain pinned. Verify upstream checksums
when updating the wrapper; update the publication guard's explicit JAR checksum
at the same time. Review dependency verification metadata when dependencies change.

## Inspect the distribution

The unsigned ZIP is generated in `intellij-plugin/build/distributions/`. It
contains the integration and core libraries, descriptors, icon, templates, and
license notices. Tests, tool installers, compiler dependencies, extra Kotlin
runtime libraries, and `t32ls` must not be bundled.

Run the source and archive checks:

```shell
python tools/test_check_repository.py
python tools/check_repository.py --artifact intellij-plugin/build/distributions/trace32-toolkit-0.2.0.zip
```

Use the actual versioned filename produced by the build. Generate and retain a
SHA-256 sidecar for the final ZIP. The packaging workflow does this after the
archive audit. Sanitize raw reports before attaching them to a public release;
logs can contain machine paths.

## Tag and publish

Use a version tag such as `v0.2.0`, matching the Gradle project version. The tag
workflow runs tests and Plugin Verifier, audits the ZIP, and uploads the plugin
and checksum as workflow artifacts. It does not create a GitHub Release or
publish to JetBrains Marketplace.

Release notes should identify tested IDE builds, changes, native versus optional
LSP responsibilities, and known limits. Review the actual workflow results and
artifacts before publishing.

Gradle provides plugin signing and publication tasks, but Marketplace credentials,
certificates, publisher ownership, and submission require separate configuration.
The SSH key used to sign Git commits is not a plugin archive signing certificate.
Keep all signing secrets out of repository properties and source files.

## Install or roll back

The public plugin ID remains `io.github.balconyjh.trace32`. Install a verified ZIP
through **Settings > Plugins > Install Plugin from Disk** and restart the IDE.
For rollback, reinstall a previously verified ZIP through the same manager.
Development uses the separate `runIde` sandbox rather than modifying the active
IDE profile.
