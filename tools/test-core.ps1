#requires -Version 7.4
param([string]$JdkHome = $env:JAVA_HOME, [string]$KotlinHome = '')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repository = Split-Path $PSScriptRoot
if (-not $JdkHome) { throw 'Set JAVA_HOME or pass -JdkHome with a full JDK 21 or newer.' }
$suffix = if ($IsWindows) { '.exe' } else { '' }
$java = Join-Path $JdkHome "bin/java$suffix"
$javac = Join-Path $JdkHome "bin/javac$suffix"
foreach ($tool in @($java, $javac)) {
    if (-not (Test-Path -LiteralPath $tool)) { throw "Missing JDK tool: $tool" }
}
if (-not $KotlinHome) { $KotlinHome = & (Join-Path $PSScriptRoot 'bootstrap-kotlin.ps1') }
$output = [IO.Path]::GetFullPath((Join-Path $repository 'build/core-tests'))
if ($output -ne [IO.Path]::GetFullPath((Join-Path (Split-Path $PSScriptRoot) 'build/core-tests'))) { throw 'Unexpected test output path.' }
if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output -Recurse -Force }
$classes = Join-Path $output 'classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
function Write-Arguments([string]$Path, [string[]]$Arguments) {
    foreach ($argument in $Arguments) {
        if ($argument.Contains('"') -or $argument.Contains("`n") -or $argument.Contains("`r")) { throw 'An argument contains an unsupported quote or line break.' }
    }
    [IO.File]::WriteAllLines($Path, @($Arguments | ForEach-Object { '"' + $_.Replace('\', '/') + '"' }), [Text.UTF8Encoding]::new($false))
}
$library = (Get-ChildItem -LiteralPath (Join-Path $KotlinHome 'lib') -Filter '*.jar' | Sort-Object Name | ForEach-Object FullName) -join [IO.Path]::PathSeparator
$packagePath = 'io/github/balconyjh/trace32/cmm/core'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $repository "src/main/kotlin/$packagePath") -Filter '*.kt' | Sort-Object FullName | ForEach-Object FullName)
$compileArgs = @('-no-stdlib', '-no-reflect', '-jvm-target', '21', '-api-version', '2.2', '-classpath', $library, '-d', $classes) + $sources
$compileFile = Join-Path $output 'kotlin.args'
Write-Arguments $compileFile $compileArgs
& $java '-classpath' (Join-Path $KotlinHome 'lib/*') 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler' "@$compileFile"
if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed.' }
$tests = @(Get-ChildItem -LiteralPath (Join-Path $repository "src/test/java/$packagePath") -Filter '*Test.java' | Sort-Object FullName)
$classpath = "$classes$([IO.Path]::PathSeparator)$library"
$javaArgs = @('-encoding', 'UTF-8', '--release', '21', '-proc:none', '-classpath', $classpath, '-d', $classes) + @($tests.FullName)
$javaFile = Join-Path $output 'javac.args'
Write-Arguments $javaFile $javaArgs
& $javac "@$javaFile"
if ($LASTEXITCODE -ne 0) { throw 'Core test compilation failed.' }
$report = Join-Path $output 'result.txt'
foreach ($test in $tests) {
    $className = 'io.github.balconyjh.trace32.cmm.core.' + $test.BaseName
    $corpus = @()
    if ($test.BaseName -eq 'CmmScannerTest') {
        foreach ($relative in @('examples', 'src/test/resources')) {
            $directory = Join-Path $repository $relative
            if (Test-Path -LiteralPath $directory) {
                $corpus += @(Get-ChildItem -LiteralPath $directory -File -Recurse | Where-Object Extension -In @('.cmm', '.cmmt') | Sort-Object FullName | ForEach-Object FullName)
            }
        }
    }
    $runFile = Join-Path $output ($test.BaseName + '.args')
    Write-Arguments $runFile (@('-ea', '-classpath', $classpath, $className) + $corpus)
    & $java "@$runFile" | Tee-Object -FilePath $report -Append
    if ($LASTEXITCODE -ne 0) { throw "Core regression failed: $className" }
}
