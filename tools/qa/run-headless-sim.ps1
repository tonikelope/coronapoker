[CmdletBinding()]
param(
    [ValidateRange(1, 100000)]
    [int] $Hands = 5000,

    [ValidateRange(1, 100000)]
    [int] $Faults = 5000,

    [ValidateRange(1, 1000000)]
    [int] $BotHands = 100,

    [long] $Seed = 3231711270,

    [switch] $AllNonVisual
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$reactorPom = Join-Path $repoRoot 'tools\reactor\pom.xml'
$isolatedHome = Join-Path ([System.IO.Path]::GetTempPath()) 'coronapoker-headless-sim'
$mavenRepo = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.m2\repository'
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
