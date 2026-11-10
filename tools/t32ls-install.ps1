#requires -Version 7.4
param(
    [string]$Destination = (Join-Path (Split-Path $PSScriptRoot) '.tools\t32ls\0.14.2')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $IsWindows) { throw 'This installer provides Windows binaries. Obtain other platforms from the upstream releases page.' }

# Pin the release and its GitHub-published archive digest; no code is executed before verification.
$version = '0.14.2'
$architecture = [Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
if ($architecture -eq 'Arm64') {
    $asset = 't32ls-win-arm64.zip'
    $expectedHash = '13c9bae9f43377993327a8e48c7706785ff8eb82f5bdba19a0791af5a92f7d36'
} elseif ($architecture -eq 'X64') {
    $asset = 't32ls-win64.zip'
    $expectedHash = 'c656bbcc46332f678b108ae9c519591219d6c751b37c55117b3c7cf5534d33ae'
} else {
    throw "Unsupported Windows architecture: $architecture"
}
$destinationPath = [IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Path $destinationPath -Force | Out-Null
$archive = Join-Path $destinationPath $asset
$url = "https://github.com/xasc/t32-language-server/releases/download/v$version/$asset"
if (-not (Test-Path -LiteralPath $archive) -or (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expectedHash) {
    Invoke-WebRequest -Uri $url -OutFile $archive -TimeoutSec 180
}
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expectedHash) {
    throw 'The t32ls archive does not match the published SHA-256 digest.'
}
# Validate archive paths before extraction, including Windows rooted paths.
$zip = [IO.Compression.ZipFile]::OpenRead($archive)
try {
    $prefix = $destinationPath.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    foreach ($entry in $zip.Entries) {
        $resolved = [IO.Path]::GetFullPath((Join-Path $destinationPath $entry.FullName))
        if (-not $resolved.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Archive entry escapes the installation directory: $($entry.FullName)"
        }
    }
} finally { $zip.Dispose() }
Expand-Archive -LiteralPath $archive -DestinationPath $destinationPath -Force
$executables = @(Get-ChildItem -LiteralPath $destinationPath -Filter 't32ls.exe' -File -Recurse)
if ($executables.Count -ne 1) { throw "Expected one t32ls.exe, found $($executables.Count)." }
$server = $executables[0].FullName
& $server '--version'
if ($LASTEXITCODE -ne 0) { throw 'The downloaded t32ls executable could not run.' }
[ordered]@{
    version = $version
    source = $url
    archiveSha256 = $expectedHash
    executable = $server
    executableSha256 = (Get-FileHash -LiteralPath $server -Algorithm SHA256).Hash.ToLowerInvariant()
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $destinationPath 'install-manifest.json') -Encoding utf8
Write-Output "Verified t32ls executable: $server"
Write-Output 'Set this path in Settings > Languages & Frameworks > Lauterbach TRACE32® and enable the language server.'
