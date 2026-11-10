#requires -Version 7.4
param([string]$ToolsDirectory = (Join-Path (Split-Path $PSScriptRoot) '.tools'))
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $IsWindows -or [Runtime.InteropServices.RuntimeInformation]::OSArchitecture -ne 'X64') { throw 'The pinned SDK archive is for Windows x64.' }
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'clion-sdk.json') -Raw | ConvertFrom-Json
$directory = [IO.Path]::GetFullPath((Join-Path $ToolsDirectory "clion-sdk/$($manifest.build)"))
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$archive = Join-Path $directory 'clion-sdk.zip'
$unpacked = Join-Path $directory 'sdk'
$marker = Join-Path $directory 'verified-sdk.json'
if (Test-Path -LiteralPath $marker) {
    $cached = Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json
    $cachedHome = [IO.Path]::GetFullPath((Join-Path $unpacked $cached.relativeHome))
    $cachePrefix = $unpacked.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    if ($cachedHome -ne $unpacked -and -not $cachedHome.StartsWith($cachePrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'SDK cache marker points outside its installation directory.' }
    if ($cached.archiveSha256 -eq $manifest.sha256 -and (Test-Path -LiteralPath (Join-Path $cachedHome 'product-info.json'))) {
        $product = Get-Content -LiteralPath (Join-Path $cachedHome 'product-info.json') -Raw | ConvertFrom-Json
        if ($product.productCode -eq 'CL' -and $product.buildNumber -eq $manifest.build) { return $cachedHome }
    }
}
if (-not (Test-Path -LiteralPath $archive) -or (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.sha256) {
    Write-Host "Downloading the pinned CLion SDK $($manifest.build) (approximately 2.5 GB)."
    $download = "$archive.download"
    Invoke-WebRequest -Uri $manifest.url -OutFile $download -TimeoutSec 1800
    if ((Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.sha256) { throw 'CLion SDK archive checksum mismatch.' }
    Move-Item -LiteralPath $download -Destination $archive -Force
}
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.sha256) { throw 'Cached SDK archive checksum mismatch.' }
$zip = [IO.Compression.ZipFile]::OpenRead($archive)
try {
    $prefix = $unpacked.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    foreach ($entry in $zip.Entries) {
        $resolved = [IO.Path]::GetFullPath((Join-Path $unpacked $entry.FullName))
        if (-not $resolved.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'SDK archive contains an unsafe path.' }
    }
} finally { $zip.Dispose() }
Expand-Archive -LiteralPath $archive -DestinationPath $unpacked -Force
$products = @(Get-ChildItem -LiteralPath $unpacked -File -Recurse -Filter 'product-info.json')
$matches = @($products | Where-Object {
    $metadata = Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json
    $metadata.PSObject.Properties['productCode'] -and $metadata.PSObject.Properties['buildNumber'] -and $metadata.productCode -eq 'CL' -and $metadata.buildNumber -eq $manifest.build
})
if ($matches.Count -ne 1) { throw 'Could not identify exactly one matching CLion installation in the archive.' }
$sdkHome = $matches[0].DirectoryName
@{ archiveSha256=$manifest.sha256; relativeHome=[IO.Path]::GetRelativePath($unpacked,$sdkHome) } | ConvertTo-Json | Set-Content -LiteralPath $marker -Encoding utf8
return $sdkHome
