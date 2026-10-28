# MobControl offline build script (no Maven needed).
# ASCII only on purpose: Windows PowerShell 5.1 decodes UTF-8-without-BOM as ANSI,
# and non-ASCII comments then corrupt later statements. Keep this file ASCII.
#
# Usage (from D:\Java\MobControl):
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
# The default -BentoBoxJar / -PaperLibraries point at the DSH workspace where those
# artifacts live, so no arguments are needed on this machine.

param(
    [string]$JavaHome = 'F:\Java\JDK25',
    [string]$BentoBoxJar = 'F:\Download\Documents\deepseek-harness\default-workspace\bb3.jar',
    [string]$PaperLibraries = 'F:\Download\Documents\deepseek-harness\default-workspace\paper-test\libraries',
    [string]$Version = '1.0.0'
)

$ErrorActionPreference = 'Stop'
$javac = Join-Path $JavaHome 'bin\javac.exe'
$jar = Join-Path $JavaHome 'bin\jar.exe'

foreach ($p in @($javac, $BentoBoxJar, $PaperLibraries)) {
    if (-not (Test-Path -LiteralPath $p)) { throw "Not found: $p" }
}

# JDK 25 is required: BentoBox 3.23.4 core classes are Java 25 bytecode (class 69).
# --release 21 keeps the output loadable by servers running Java 21.
$libs = @(Get-ChildItem -LiteralPath $PaperLibraries -Recurse -Filter '*.jar' | Sort-Object FullName | ForEach-Object { $_.FullName })
$cpParts = @($BentoBoxJar) + $libs
$cp = $cpParts -join ';'

$out = Join-Path $PSScriptRoot 'out'
$dist = Join-Path $PSScriptRoot 'dist'
Remove-Item -LiteralPath $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $out, $dist | Out-Null

$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })

Write-Host "Compiling $($sources.Count) source file(s), classpath has $($libs.Count) dependency jars ..."
& $javac -encoding UTF-8 --release 21 -cp $cp -d $out $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed (exit $LASTEXITCODE)" }

$resourceDir = Join-Path $PSScriptRoot 'src\main\resources'
$target = Join-Path $dist "MobControl-$Version.jar"
Remove-Item -LiteralPath $target -Force -ErrorAction SilentlyContinue

Write-Host "Packaging $target ..."
& $jar --create --file $target -C $out . -C $resourceDir .
if ($LASTEXITCODE -ne 0) { throw "jar failed (exit $LASTEXITCODE)" }

Write-Host "OK: $target"
