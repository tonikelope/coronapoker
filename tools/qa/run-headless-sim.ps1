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
  -Help                    Show this help and exit

Examples:
  .\tools\qa\run-headless-sim.ps1 -Hands 200 -Faults 200 -BotHands 10 -Seed 42
  .\tools\qa\run-headless-sim.ps1 -AllNonVisual

This fast layer exercises production protocol/domain components without full
Swing/Crupier orchestration. Use run-real-game-e2e.ps1 for complete local games.
'@ | Write-Host
    exit 0
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$reactorPom = Join-Path $repoRoot 'tools\reactor\pom.xml'
$isolatedHome = Join-Path ([System.IO.Path]::GetTempPath()) 'coronapoker-headless-sim'
$userProfile = [Environment]::GetFolderPath('UserProfile')
if ([string]::IsNullOrWhiteSpace($userProfile)) {
    $userProfile = $env:USERPROFILE
}
if ([string]::IsNullOrWhiteSpace($userProfile)) {
    throw 'Cannot resolve the Windows user profile for the local Maven repository.'
}
$mavenRepo = Join-Path $userProfile '.m2\repository'
New-Item -ItemType Directory -Path $isolatedHome -Force | Out-Null

$mavenCommand = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
if ($null -ne $mavenCommand) {
    $maven = $mavenCommand.Source
} else {
    $maven = 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd'
    if (-not (Test-Path -LiteralPath $maven)) {
        throw 'Maven was not found in PATH or in the NetBeans installation.'
    }
}

$profile = if ($AllNonVisual) { 'qa-headless-all' } else { 'qa-protocol-sim' }
$arguments = @(
    '-f', $reactorPom,
    "-Dmaven.repo.local=$($mavenRepo.Replace('\', '/'))",
    "-Duser.home=$($isolatedHome.Replace('\', '/'))",
    "-Dqa.sim.hands=$Hands",
    "-Dqa.sim.faults=$Faults",
    "-Dqa.sim.bot.hands=$BotHands",
    "-Dqa.sim.seed=$Seed",
    'test',
    "-P$profile"
)

Write-Host "CoronaPoker headless simulation: profile=$profile hands=$Hands faults=$Faults botHands=$BotHands seed=$Seed"
& $maven @arguments
exit $LASTEXITCODE
