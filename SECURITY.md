# Security

Toolkit for Lauterbach TRACE32® currently provides local editing and language analysis. It does not include target-control or script-execution features.

## Reporting a vulnerability

Use the repository's **Report a vulnerability** option if private reporting is enabled. If it is unavailable, open an issue requesting a private contact channel without posting exploit details, secrets, or proprietary files. Do not use the author's GitHub noreply address as a security mailbox.

Include the plugin version, IDE build, affected component, a minimal sanitized reproduction, and the expected impact. Acknowledgement and remediation timelines depend on maintainer availability; this project has no guaranteed response SLA.

## Relevant boundaries

- The optional language server is a local executable chosen by the user. It can read project scripts and referenced files to analyze them. Select a trusted executable.
- The provided installer fetches a pinned upstream release and checks its archive digest before running it. Build compiler dependencies are pinned and verified separately.
- Runtime configuration and generated test reports can contain absolute paths. These files should remain outside version control and be reviewed before sharing.
- The plugin's native analysis does not execute PRACTICE commands. Future runtime features require a separate design and security review.

Report vulnerabilities in `t32ls`, the IDE, or Lauterbach TRACE32® itself to the respective upstream project when appropriate. Cross-component findings can also be reported here with the affected boundary explained.
