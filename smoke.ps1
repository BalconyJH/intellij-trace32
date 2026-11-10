#requires -Version 7.4
param([string]$ClionHome = $env:CLION_HOME, [string]$JdkHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $ClionHome) { throw 'Set CLION_HOME or pass -ClionHome with a CLion 263-series SDK.' }
if (-not $JdkHome) { $JdkHome = Join-Path $ClionHome 'jbr' }
$product = Get-Content -LiteralPath (Join-Path $ClionHome 'product-info.json') -Raw | ConvertFrom-Json
if ($product.productCode -ne 'CL' -or $product.buildNumber -notmatch '^263\.') { throw 'This smoke harness targets CLion 2026.3 (CL-263.*).' }
$build = Join-Path $PSScriptRoot 'build'
$pluginJar = Join-Path $build 'trace32-toolkit\lib\trace32-toolkit.jar'
if (-not (Test-Path -LiteralPath $pluginJar)) { throw 'Run build.ps1 first.' }
$sandbox = Join-Path $build ('smoke-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$classes = Join-Path $sandbox 'smoke-classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$sdk = (Get-ChildItem -LiteralPath (Join-Path $ClionHome 'lib') -Filter '*.jar' | ForEach-Object FullName) -join ';'
$compilerArgs = @('-encoding', 'UTF-8', '--release', '21', '-proc:none', '-classpath', ('"' + "$pluginJar;$sdk".Replace('\','/') + '"'), '-d', ('"' + $classes.Replace('\','/') + '"'))
$compilerArgs += Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src\smoke\java') -Filter '*.java' -Recurse | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' }
$argsFile = Join-Path $sandbox 'javac.args'
[IO.File]::WriteAllLines($argsFile, $compilerArgs, [Text.UTF8Encoding]::new($false))
& (Join-Path $JdkHome 'bin\javac.exe') "@$argsFile"
if ($LASTEXITCODE -ne 0) { throw 'Smoke test compilation failed.' }
$report = Join-Path $sandbox 'result.txt'
$javaArgs = @('-Xmx768m', '-Djava.awt.headless=true', ('-Didea.home.path=' + $ClionHome), ('-Didea.config.path=' + (Join-Path $sandbox 'config')), ('-Didea.system.path=' + (Join-Path $sandbox 'system')), ('-Dcmm.smoke.output=' + $report))
$javaArgs += @($product.launch[0].additionalJvmArguments | Where-Object { $_ -match '^--add-opens=|^--enable-native-access=' })
$javaArgs += @('-classpath', "$classes;$pluginJar;$sdk", 'io.github.balconyjh.trace32.cmm.smoke.CmmSmokeStarter')
$javaArgFile = Join-Path $sandbox 'java.args'
[IO.File]::WriteAllLines($javaArgFile, @($javaArgs | ForEach-Object { '"' + $_.Replace('\','/') + '"' }), [Text.UTF8Encoding]::new($false))
Write-Output "IntelliJ core platform test: $sandbox"
& (Join-Path $JdkHome 'bin\java.exe') "@$javaArgFile"
$exitCode = $LASTEXITCODE
if (Test-Path -LiteralPath $report) { Get-Content -LiteralPath $report }
if ($exitCode -ne 0 -or -not (Test-Path -LiteralPath $report)) { throw "IDE smoke test failed; see $sandbox" }
if ((Get-Content -LiteralPath $report -First 1) -notmatch '^PASS ') { throw 'IDE smoke test did not pass.' }
