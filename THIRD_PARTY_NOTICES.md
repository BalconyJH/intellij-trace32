# Third-party notices

## Product names and trademarks

This is an unofficial community project maintained by BalconyJH. It is not
affiliated with, sponsored by, or endorsed by Lauterbach GmbH or JetBrains.

Lauterbach TRACE32® is a product of Lauterbach GmbH. TRACE32® is a registered
trademark of Lauterbach GmbH. Product names identify the software this project
supports; they do not indicate ownership of, or official status within, that
product. This repository does not redistribute vendor logos or manuals.

The IntelliJ plugin descriptor uses the name `Toolkit for Lauterbach TRACE32`
because the platform's plugin-name validation does not accept the registered
trademark symbol. The description, interface, and documentation retain the
full Lauterbach TRACE32® attribution.

CLion, IntelliJ IDEA, and other JetBrains product names belong to their respective
owners. Current compatibility is limited to the versions documented in
[compatibility](docs/compatibility.md).

## Optional language server

[t32-language-server](https://github.com/xasc/t32-language-server) is a separate
project by Christoph Sax, licensed under
[EUPL-1.2](https://interoperable-europe.ec.europa.eu/sites/default/files/custom-page/attachment/2020-03/EUPL-1.2%20EN.txt).
The server is **not bundled** with this plugin. The optional installation helper
downloads an upstream release with a pinned checksum into a local ignored cache.
Its own license and notices continue to apply to that separate software.

## Build dependencies

- [Kotlin](https://github.com/JetBrains/kotlin): compiler and build dependencies,
  primarily Apache-2.0; versions are recorded in `gradle/libs.versions.toml`
  and dependency checksums in `gradle/verification-metadata.xml`.
- [Gradle](https://github.com/gradle/gradle): the checked-in wrapper is Apache-2.0.
  Its upstream [license](gradle/wrapper/LICENSE) is retained. The wrapper JAR and
  distribution have pinned SHA-256 checksums.
- [IntelliJ Platform](https://github.com/JetBrains/intellij-community): SDK APIs;
  community platform components are Apache-2.0. A CLion distribution also includes
  components under other terms. Obtain and use the SDK under its upstream terms.
- The plugin uses the Kotlin runtime supplied by the IDE. The compiler, IDE SDK,
  and an additional Kotlin runtime are not included in the plugin ZIP.

The repository's [MIT license](LICENSE) covers this project's original code. It
does not relicense third-party software or grant rights to third-party trademarks.
