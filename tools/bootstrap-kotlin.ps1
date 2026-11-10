#requires -Version 7.4
param([string]$ToolsDirectory = (Join-Path (Split-Path $PSScriptRoot) '.tools'))
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# JetBrains compiler and its published Maven dependencies. Every cached binary
# and download is checked against a pinned SHA256, including on offline builds.
$compilerHome = Join-Path $ToolsDirectory 'kotlin-maven-2.4.21'
$lib = Join-Path $compilerHome 'lib'
New-Item -ItemType Directory -Path $lib -Force | Out-Null
$artifacts = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'kotlin-artifacts.json') -Raw | ConvertFrom-Json
foreach ($artifact in $artifacts) {
    $destination = Join-Path $lib $artifact.name
    if (-not (Test-Path -LiteralPath $destination)) {
        $download = "$destination.download"
        Write-Host "Downloading Kotlin build dependency: $($artifact.name)"
        Invoke-WebRequest -Uri ('https://repo.maven.apache.org/maven2/' + $artifact.path) -OutFile $download -TimeoutSec 300
        if ((Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash.ToLowerInvariant() -ne $artifact.sha256) {
            throw "Kotlin dependency checksum mismatch: $($artifact.name)"
        }
        Move-Item -LiteralPath $download -Destination $destination -Force
    }
    if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant() -ne $artifact.sha256) {
        throw "Cached Kotlin dependency checksum mismatch: $destination"
    }
}
return $compilerHome
