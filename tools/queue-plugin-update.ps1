param(
    [Parameter(Mandatory)][string]$PluginJar,
    [Parameter(Mandatory)][string]$TargetJar,
    [Parameter(Mandatory)][string]$ClionHome,
    [Parameter(Mandatory)][string]$JdkHome,
    [Parameter(Mandatory)][string]$KotlinHome
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$product = Get-Content -LiteralPath (Join-Path $ClionHome 'product-info.json') -Raw | ConvertFrom-Json
$expectedTarget = Join-Path $env:APPDATA "JetBrains\$($product.dataDirectoryName)\plugins\trace32-toolkit\lib\trace32-toolkit.jar"
if ([IO.Path]::GetFullPath($TargetJar) -ne [IO.Path]::GetFullPath($expectedTarget)) { throw 'Unexpected plugin update target.' }
$hash = (Get-FileHash -LiteralPath $PluginJar -Algorithm SHA256).Hash.ToLowerInvariant()
$startupDirectory = Join-Path $env:LOCALAPPDATA "JetBrains\$($product.dataDirectoryName)\plugins"
$stagingDirectory = Join-Path $startupDirectory 'trace32-toolkit-updates'
New-Item -ItemType Directory -Path $stagingDirectory -Force | Out-Null
$stagedJar = Join-Path $stagingDirectory "trace32-toolkit-$hash.jar"
Copy-Item -LiteralPath $PluginJar -Destination $stagedJar -Force
if ((Get-FileHash -LiteralPath $stagedJar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash) { throw 'Staged plugin checksum mismatch.' }
$classes = Join-Path $stagingDirectory 'installer-classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$sdk = (Get-ChildItem -LiteralPath (Join-Path $ClionHome 'lib') -Filter '*.jar' | Sort-Object Name | ForEach-Object FullName) -join ';'
$compilerArgs = @('-no-stdlib', '-no-reflect', '-jvm-target', '21', '-api-version', '2.2', '-classpath', $sdk, '-d', $classes, (Join-Path $PSScriptRoot 'QueuePluginUpdate.kt'))
$compilerArgFile = Join-Path $stagingDirectory 'installer-kotlinc.args'
[IO.File]::WriteAllLines($compilerArgFile, @($compilerArgs | ForEach-Object { '"' + $_.Replace('\','/') + '"' }), [Text.UTF8Encoding]::new($false))
& (Join-Path $JdkHome 'bin\java.exe') '-classpath' (Join-Path $KotlinHome 'lib\*') 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler' "@$compilerArgFile"
if ($LASTEXITCODE -ne 0) { throw 'Update installer compilation failed.' }
$scriptFile = Join-Path $startupDirectory 'action.script'
$javaArgs = @('-classpath', "$classes;$sdk", 'QueuePluginUpdateKt', $stagedJar, $TargetJar, $scriptFile)
$javaArgFile = Join-Path $stagingDirectory 'installer-java.args'
[IO.File]::WriteAllLines($javaArgFile, @($javaArgs | ForEach-Object { '"' + $_.Replace('\','/') + '"' }), [Text.UTF8Encoding]::new($false))
& (Join-Path $JdkHome 'bin\java.exe') "@$javaArgFile"
if ($LASTEXITCODE -ne 0) { throw 'Could not queue the plugin update.' }
@{ status='pending-restart'; sha256=$hash; stagedJar=$stagedJar; targetJar=$TargetJar; actionScript=$scriptFile } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $stagingDirectory 'pending-install.json') -Encoding utf8
