# Optional language server

[t32ls](https://github.com/xasc/t32-language-server) analyzes Lauterbach TRACE32® PRACTICE scripts through the Language Server Protocol. It provides definition navigation, references, semantic highlighting, and folding. Toolkit for Lauterbach TRACE32® uses its navigation and semantic capabilities and retains native folding.

The server does not operate Lauterbach TRACE32®, connect to target hardware, or execute a PRACTICE script. Installing or enabling it does not add a debugger.

## Installation

Run the provided installer from the repository root:

```powershell
.\tools\t32ls-install.ps1
```

The installer selects the Windows x64 or ARM64 asset for the pinned 0.14.2 release, verifies its archive SHA-256, checks extraction paths, and prints the resulting executable path. It writes an installation manifest under `.tools/t32ls/0.14.2`. This directory is local build state and must not be committed.

You can also obtain a server from the [upstream releases](https://github.com/xasc/t32-language-server/releases). A different server version needs separate compatibility testing. The plugin ZIP contains the integration code, not the server executable.

## Project configuration

Open **Settings > Languages & Frameworks > Lauterbach TRACE32®**:

1. Enable the language server.
2. Select the `t32ls` executable, not a shell command or a directory.
3. Optionally select the Lauterbach TRACE32® system directory for `~~` paths.
4. Optionally select the Lauterbach TRACE32® temporary directory for `~~~` paths.
5. Apply the settings and open a supported script.

New projects have LSP disabled. Directory settings also serve native path completion and navigation when LSP is disabled. Leave a directory empty when its correct value is unknown; an arbitrary temporary directory can resolve the wrong file.

Settings are project-specific and can contain absolute paths. Do not commit the generated `.idea` configuration. A configuration change stops or restarts the corresponding server through the IDE's LSP lifecycle API.

## Responsibilities

| Capability | Implementation |
| --- | --- |
| Contextual completion, quick docs, parameter hints | Native plugin |
| Formatting, diagnostics, templates, local rename | Native plugin |
| Block and comment folding | Native plugin; LSP folding is disabled |
| Cross-file definitions and references | Optional `t32ls` integration |
| Semantic tokens | Optional `t32ls` integration |
| Target control and script execution | Not provided by this repository |

## Protocol verification

After installation, use Python 3 to run the stdio smoke test:

```powershell
$manifest = Get-Content .\.tools\t32ls\0.14.2\install-manifest.json -Raw | ConvertFrom-Json
python .\tools\t32ls-smoke.py --server $manifest.executable --report .\build\t32ls-result.json
```

The test creates temporary synthetic scripts and verifies initialization, macro definitions, cross-file script paths, configured directory prefixes, references, folding, semantic tokens, incremental updates, and clean shutdown. It does not attach to a running Lauterbach TRACE32® instance. This protocol check does not replace testing the IDE's LSP lifecycle.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Native editing works but no server features appear | Enable LSP in this project and verify the executable exists. |
| `~~` or `~~~` does not resolve | Set the matching directory and confirm the referenced file exists there. |
| An arbitrary macro-based path does not resolve | Dynamic macro expansion is outside the static analysis scope. |
| Server changes do not take effect | Apply the settings, reopen a script, and inspect the IDE's LSP status/log output. |
| The protocol command cannot find Python | Use a real Python 3 interpreter rather than an operating-system launcher placeholder. |
| Other PCs cannot use a copied configuration | Install the server on each machine and select that machine's executable and directories. |

Reduce and sanitize logs before posting them in an issue. Runtime logs and manifests can contain project and filesystem paths.
