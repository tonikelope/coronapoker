param(
    [ValidateRange(1, 7)]
    [int]$Clients = 1,

    [ValidateRange(0, 7)]
    [int]$Bots = 2,

    [ValidateRange(1, 100)]
    [int]$Hands = 1,

    [long]$Seed = 23059,

    [ValidateSet('normal', 'abrupt-exit')]
    [string]$Scenario = 'normal',

    [ValidateSet('hidden', 'minimized', 'visible')]
    [string]$WindowMode = 'hidden',

    [ValidateRange(1, 16)]
    [int]$Screen = 2,

    [switch]$Animations,

    [switch]$ProductionTiming,

    [switch]$Help
)

$ErrorActionPreference = 'Stop'

if ($Help) {
    @'
CoronaPoker real-game loopback E2E simulator

Usage:
  powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\qa\run-real-game-e2e.ps1 [options]

Options:
  -Clients <1..7>          Human client JVMs in addition to the host (default: 1)
  -Bots <0..7>             Production bots hosted by the server (default: 2)
  -Hands <1..100>          Complete hands to play (default: 1)
  -Seed <long>             Reproducible action-driver seed (default: 23059)
  -Scenario <name>         normal or abrupt-exit (default: normal)
  -WindowMode <mode>       hidden, minimized or visible (default: hidden)
  -Screen <1..16>          Target monitor for every mode (default: 2)
  -Animations              Enable production animations; disabled by default
  -ProductionTiming        Disable presentation-only TEST_MODE shortcuts
  -Help                    Show this help and exit

Constraints:
  Host + clients + bots cannot exceed 8 seats. Every JVM gets an isolated
  temporary user.home, identity and SQLite database; JUnit removes them after
  the processes stop. Error dialogs are converted into failing log evidence.

Examples:
  .\tools\qa\run-real-game-e2e.ps1
  .\tools\qa\run-real-game-e2e.ps1 -Clients 2 -Bots 1 -Hands 3 -Seed 42
  .\tools\qa\run-real-game-e2e.ps1 -Scenario abrupt-exit
  .\tools\qa\run-real-game-e2e.ps1 -WindowMode visible -Screen 2 -Animations
  .\tools\qa\run-real-game-e2e.ps1 -ProductionTiming -WindowMode minimized

This layer launches separate JVMs and runs the production WaitingRoomFrame,
encrypted sockets, Crupier, rondaApuestas, bots, consensus and SQLite close.
'@ | Write-Host
    exit 0
}

if (($Clients + $Bots + 1) -gt 8) {
    throw 'Host + clients + bots cannot exceed 8 seats.'
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$maven = 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $maven)) {
    $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($null -eq $mavenCommand) {
        throw 'Maven not found (checked Apache NetBeans and PATH).'
    }
    $maven = $mavenCommand.Source
}

$userProfile = [Environment]::GetFolderPath('UserProfile')
if ([string]::IsNullOrWhiteSpace($userProfile)) {
    $userProfile = $env:USERPROFILE
}
if ([string]::IsNullOrWhiteSpace($userProfile)) {
    throw 'Cannot resolve the Windows user profile for the local Maven repository.'
}
$mavenRepo = Join-Path $userProfile '.m2\repository'
$animationsEnabled = if ($Animations) { 'true' } else { 'false' }
$testModeEnabled = if ($ProductionTiming) { 'false' } else { 'true' }
& $maven `
    -f (Join-Path $repoRoot 'tools\reactor\pom.xml') `
    -o `
    "-Dmaven.repo.local=$mavenRepo" `
    test `
    -P qa-real-game-e2e `
    "-Dqa.e2e.clients=$Clients" `
    "-Dqa.e2e.bots=$Bots" `
    "-Dqa.e2e.hands=$Hands" `
    "-Dqa.e2e.seed=$Seed" `
    "-Dqa.e2e.scenario=$Scenario" `
    "-Dqa.e2e.windowMode=$WindowMode" `
    "-Dqa.e2e.screen=$Screen" `
    "-Dqa.e2e.animations=$animationsEnabled" `
    "-Dqa.e2e.testMode=$testModeEnabled"

exit $LASTEXITCODE
