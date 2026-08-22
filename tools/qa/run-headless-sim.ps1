[CmdletBinding()]
param(
    [ValidateRange(1, 100000)]
    [int] $Hands = 5000,

    [ValidateRange(1, 100000)]
    [int] $Faults = 5000,

    [ValidateRange(1, 1000000)]
    [int] $BotHands = 100,

    [long] $Seed = 3231711270,

    [switch] $AllNonVisual,

    [switch] $SkipGameBuild,

    [switch] $Help
)

$ErrorActionPreference = 'Stop'

if ($Help) {
    @'
CoronaPoker headless protocol simulator

Usage:
  powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\qa\run-headless-sim.ps1 [options]

Options:
  -Hands <1..100000>       Protocol, side-pot, Rabbit, lifecycle and SQL cases (default: 5000)
  -Faults <1..100000>      Random critical-stream fault cases (default: 5000)
  -BotHands <1..1000000>   Production-bot hands (default: 100)
  -Seed <long>             Reproducible campaign seed (default: 3231711270)
  -AllNonVisual            Run every automated non-visual QA test, not only protocol simulation
  -SkipGameBuild           Reuse the exact checkout already installed by run-certification.ps1
  -Help                    Show this help and exit

Examples:
  .\tools\qa\run-headless-sim.ps1 -Hands 200 -Faults 200 -BotHands 10 -Seed 42
  .\tools\qa\run-headless-sim.ps1 -AllNonVisual

This fast layer exercises production protocol/domain components without full
Swing/Crupier orchestration. Use run-real-game-e2e.ps1 for complete local games.
The exact checkout is built into the ignored local .m2/repository cache.
'@ | Write-Host
    exit 0
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$reactorPom = Join-Path $repoRoot 'tools\reactor\pom.xml'
$qaPom = Join-Path $repoRoot 'tools\qa\pom.xml'
$mavenRepo = Join-Path $repoRoot '.m2\repository'

$maven = $null
$wrapper = Join-Path $repoRoot 'mvnw.cmd'
if (Test-Path -LiteralPath $wrapper) {
    $maven = $wrapper
}
foreach ($candidate in @('mvn.cmd', 'mvn')) {
    if ($null -eq $maven) {
        $mavenCommand = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($null -ne $mavenCommand) {
            $maven = $mavenCommand.Source
        }
    }
}
if ($null -eq $maven) {
    $netBeansMaven = 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd'
    if (Test-Path -LiteralPath $netBeansMaven) {
        $maven = $netBeansMaven
    } else {
        throw 'Maven was not found (checked mvnw.cmd, PATH and Apache NetBeans).'
    }
}

$profile = if ($AllNonVisual) { 'qa-headless-all' } else { 'qa-protocol-sim' }
$pom = if ($SkipGameBuild) { $qaPom } else { $reactorPom }
$goal = if ($SkipGameBuild) { 'test' } else { 'install' }
$qaRunId = '{0}-{1}' -f (Get-Date -Format 'yyyyMMdd-HHmmss-fff'), $PID
$qaUserHome = Join-Path $repoRoot "tools\qa\target\qa-home\run-$qaRunId"
$arguments = @(
    '-f', $pom,
    "-Dmaven.repo.local=$($mavenRepo.Replace('\', '/'))",
    "-Dqa.user.home=$($qaUserHome.Replace('\', '/'))",
    "-Dqa.sim.hands=$Hands",
    "-Dqa.sim.faults=$Faults",
    "-Dqa.sim.bot.hands=$BotHands",
    "-Dqa.sim.seed=$Seed",
    $goal,
    "-P$profile"
)

Write-Host "CoronaPoker headless simulation: profile=$profile hands=$Hands faults=$Faults botHands=$BotHands seed=$Seed"
$qaHomeRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'tools\qa\target\qa-home'))
$qaHomePath = [IO.Path]::GetFullPath($qaUserHome)
if (-not $qaHomePath.StartsWith($qaHomeRoot + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing unsafe QA home path: $qaHomePath"
}
$exitCode = 1
try {
    & $maven @arguments
    $exitCode = $LASTEXITCODE
} finally {
    if (Test-Path -LiteralPath $qaHomePath) {
        Remove-Item -LiteralPath $qaHomePath -Recurse -Force
    }
}
exit $exitCode
