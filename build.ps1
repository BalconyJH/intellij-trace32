#requires -Version 7.4
param(
    [string]$ClionHome = $env:CLION_HOME,
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$KotlinHome = '',
    [switch]$SkipTests,
    [switch]$SkipSmoke,
    [switch]$Install
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$project = $PSScriptRoot
$build = Join-Path $project 'build'
if (-not $IsWindows) { throw 'The full SDK build currently supports Windows. Use tools/test-core.ps1 for IDE-independent tests.' }
if (-not $ClionHome) { throw 'Set CLION_HOME or pass -ClionHome with a CLion 263-series installation directory.' }
$product = Get-Content -LiteralPath (Join-Path $ClionHome 'product-info.json') -Raw | ConvertFrom-Json
if ($product.productCode -ne 'CL' -or $product.buildNumber -notmatch '^263\.') { throw 'This plugin currently targets CLion 2026.3 (CL-263.*).' }
if (-not $JdkHome) { $JdkHome = Join-Path $ClionHome 'jbr' }
$javac = Join-Path $JdkHome 'bin\javac.exe'
$java = Join-Path $JdkHome 'bin\java.exe'
foreach ($tool in @($javac, $java)) {
    if (-not (Test-Path -LiteralPath $tool)) { throw "Missing JDK tool: $tool. Specify -JdkHome." }
}
if (-not $KotlinHome) { $KotlinHome = & (Join-Path $project 'tools\bootstrap-kotlin.ps1') }
if (-not (Test-Path -LiteralPath (Join-Path $KotlinHome 'lib\kotlin-compiler.jar'))) { throw 'Kotlin compiler is missing.' }
if ($Install -and ($SkipTests -or $SkipSmoke)) { throw 'Installation requires all checks. Remove -SkipTests and -SkipSmoke.' }
# Only remove our own generated output, after validating its absolute location.
$build = [IO.Path]::GetFullPath($build)
if ($build -ne [IO.Path]::GetFullPath((Join-Path $project 'build'))) { throw 'Unexpected build directory.' }
if (Test-Path -LiteralPath $build) { Remove-Item -LiteralPath $build -Recurse -Force }
$classes = Join-Path $build 'classes'
$testClasses = Join-Path $build 'test-classes'
$plugin = Join-Path $build 'trace32-toolkit'
$pluginLib = Join-Path $plugin 'lib'
New-Item -ItemType Directory -Path $classes, $testClasses, $pluginLib -Force | Out-Null
$sdk = (Get-ChildItem -LiteralPath (Join-Path $ClionHome 'lib') -Filter '*.jar' | Sort-Object Name | ForEach-Object FullName) -join ';'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $project 'src\main\kotlin') -Recurse -Filter '*.kt' | Sort-Object FullName)
if (-not $sources.Count) { throw 'No Kotlin production sources found.' }
$legacy = @(Get-ChildItem -LiteralPath (Join-Path $project 'src\main') -Recurse -Filter '*.java')
if ($legacy.Count) { throw 'Production Java sources remain; finish the Kotlin migration before building.' }
$compilerArgs = @('-no-stdlib', '-no-reflect', '-jvm-target', '21', '-api-version', '2.2', '-classpath', ('"' + $sdk.Replace('\','/') + '"'), '-d', ('"' + $classes.Replace('\','/') + '"'))
$compilerArgs += $sources | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' }
$argsFile = Join-Path $build 'kotlinc.args'
[IO.File]::WriteAllLines($argsFile, $compilerArgs, [Text.UTF8Encoding]::new($false))
& $java '-Xmx1g' '-classpath' (Join-Path $KotlinHome 'lib\*') 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler' "@$argsFile"
if ($LASTEXITCODE -ne 0) { throw 'Plugin compilation failed.' }

$testSources = @(Get-ChildItem -LiteralPath (Join-Path $project 'src\test\java') -Recurse -Filter '*.java' | Sort-Object FullName)
if ($testSources.Count -and -not $SkipTests) {
    $testArgs = @('-encoding', 'UTF-8', '--release', '21', '-proc:none', '-classpath', ('"' + "$classes;$sdk".Replace('\','/') + '"'), '-d', ('"' + $testClasses.Replace('\','/') + '"'))
    $testArgs += $testSources | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' }
    $testArgsFile = Join-Path $build 'test-javac.args'
    [IO.File]::WriteAllLines($testArgsFile, $testArgs, [Text.UTF8Encoding]::new($false))
    & $javac "@$testArgsFile"
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    $testMains = $testSources | Where-Object Name -Like '*Test.java' | ForEach-Object {
        $_.FullName.Substring((Join-Path $project 'src\test\java').Length + 1).Replace('\', '.').Replace('/', '.').Replace('.java', '')
    }
    foreach ($test in $testMains) {
        $corpus = @()
        if ($test -eq 'io.github.balconyjh.trace32.cmm.core.CmmScannerTest') {
            foreach ($relative in @('examples', 'src/test/resources')) {
                $scripts = Join-Path $project $relative
                if (Test-Path -LiteralPath $scripts) {
                    $corpus += @(Get-ChildItem -LiteralPath $scripts -File -Recurse | Where-Object Extension -In @('.cmm', '.cmmt') | Sort-Object FullName | ForEach-Object FullName)
                }
            }
        }
        $runArgs = @('-ea', '-classpath', "$classes;$testClasses;$sdk", $test) + $corpus
        $runArgsFile = Join-Path $build ($test + '.args')
        [IO.File]::WriteAllLines($runArgsFile, @($runArgs | ForEach-Object { '"' + $_.Replace('\','/') + '"' }), [Text.UTF8Encoding]::new($false))
        & $java "@$runArgsFile"
        if ($LASTEXITCODE -ne 0) { throw "Test failed: $test" }
    }
}
Copy-Item -Path (Join-Path $project 'src\main\resources\*') -Destination $classes -Recurse -Force
foreach ($notice in @('LICENSE', 'THIRD_PARTY_NOTICES.md')) {
    if (Test-Path -LiteralPath (Join-Path $project $notice)) {
        Copy-Item -LiteralPath (Join-Path $project $notice) -Destination (Join-Path $classes "META-INF/$notice")
    }
}
$pluginJar = Join-Path $pluginLib 'trace32-toolkit.jar'
[IO.Compression.ZipFile]::CreateFromDirectory($classes, $pluginJar)
[xml]$descriptor = Get-Content -LiteralPath (Join-Path $classes 'META-INF\plugin.xml') -Raw
$version = $descriptor.'idea-plugin'.version
$zip = Join-Path $build "trace32-toolkit-$version.zip"
Compress-Archive -LiteralPath $plugin -DestinationPath $zip
$hash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText("$zip.sha256", "$hash  $([IO.Path]::GetFileName($zip))`n", [Text.UTF8Encoding]::new($false))
Write-Output "Built: $zip"
Write-Output "SDK: $($product.productCode)-$($product.buildNumber)"
Write-Output "SHA256: $((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash)"
if (-not $SkipSmoke) { & (Join-Path $project 'smoke.ps1') -ClionHome $ClionHome -JdkHome $JdkHome }

if ($Install) {
    $target = Join-Path $env:APPDATA "JetBrains\$($product.dataDirectoryName)\plugins\trace32-toolkit"
    if (Test-Path -LiteralPath $target) {
        $backup = Join-Path $project ('backups\trace32-toolkit-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
        New-Item -ItemType Directory -Path (Split-Path $backup) -Force | Out-Null
        Copy-Item -LiteralPath $target -Destination $backup -Recurse
        Write-Output "Previous plugin backed up: $backup"
    }
    New-Item -ItemType Directory -Path (Join-Path $target 'lib') -Force | Out-Null
    $queued = $false
    try { Copy-Item -LiteralPath $pluginJar -Destination (Join-Path $target 'lib\trace32-toolkit.jar') -Force }
    catch [IO.IOException] {
        if (($_.Exception.HResult -band 0xFFFF) -notin @(32, 33, 1224)) { throw }
        & (Join-Path $project 'tools\queue-plugin-update.ps1') -PluginJar $pluginJar -TargetJar (Join-Path $target 'lib\trace32-toolkit.jar') -ClionHome $ClionHome -JdkHome $JdkHome -KotlinHome $KotlinHome
        $queued = $true
    }
    if (-not $queued) {
        if ((Get-FileHash -LiteralPath $pluginJar).Hash -ne (Get-FileHash -LiteralPath (Join-Path $target 'lib\trace32-toolkit.jar')).Hash) {
            throw 'Installed plugin checksum does not match the build.'
        }
        Write-Output "Installed: $target"
    }
    Write-Output 'Restart CLion to load the plugin.'
}
