param(
    [ValidateRange(1, 7)]
    [int]$Clients = 1,

    [ValidateRange(0, 7)]
    [int]$Bots = 2,

    [ValidateRange(1, 100)]
    [int]$Hands = 1,

    [long]$Seed = 23059,

    [ValidateSet('hidden', 'minimized', 'visible')]
    [string]$WindowMode = 'hidden',

    [ValidateRange(1, 16)]
    [int]$Screen = 2,

    [switch]$Animations,

    [switch]$ProductionTiming
)

$ErrorActionPreference = 'Stop'

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
    "-Dqa.e2e.windowMode=$WindowMode" `
    "-Dqa.e2e.screen=$Screen" `
    "-Dqa.e2e.animations=$animationsEnabled" `
    "-Dqa.e2e.testMode=$testModeEnabled"

exit $LASTEXITCODE
