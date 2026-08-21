[CmdletBinding()]
param(
    [ValidateRange(1, 100000)]
    [int]$Hands = 5000,

    [ValidateRange(1, 100000)]
    [int]$Faults = 5000,

    [ValidateRange(1, 1000000)]
    [int]$BotHands = 100,

    [long]$Seed = 3231711270,

    [ValidateSet('hidden', 'minimized', 'visible')]
    [string]$WindowMode = 'hidden',

    [ValidateRange(1, 16)]
    [int]$Screen = 2,

    [switch]$Animations,

    [switch]$ProductionTiming,

    [switch]$SkipBotQuality,

    [switch]$Help
)

$ErrorActionPreference = 'Stop'

if ($Help) {
    @'
CoronaPoker complete local certification

Usage:
  powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\qa\run-certification.ps1 [options]

Default phases (fail-fast and sequential):
  1. qa-release: deterministic tests plus every non-bot slow lane
  2. qa-bots: statistical bot-quality tests
  3. Seeded headless protocol/fault campaigns
  4. Every real-game loopback scenario in separate production JVMs

Options:
  -Hands <1..100000>       Headless protocol campaign hands (default: 5000)
  -Faults <1..100000>      Headless critical-stream fault cases (default: 5000)
  -BotHands <1..1000000>   Headless production-bot hands (default: 100)
  -Seed <long>             Reproducible base seed (default: 3231711270)
  -WindowMode <mode>       hidden, minimized or visible (default: hidden)
  -Screen <1..16>          Monitor assigned to real-game JVMs (default: 2)
  -Animations              Enable animations in real-game scenarios
  -ProductionTiming        Use production pauses instead of test acceleration
  -SkipBotQuality          Skip only statistical bot-quality tests
  -Help                    Show this help and exit

Examples:
  .\tools\qa\run-certification.ps1
  .\tools\qa\run-certification.ps1 -Seed 42 -WindowMode hidden -Screen 2
  .\tools\qa\run-certification.ps1 -Hands 500 -Faults 500 -SkipBotQuality

Reports and full phase logs are written under target\certification\<timestamp>.
The command exits non-zero at the first failed phase and prints its log path.
'@ | Write-Host
    exit 0
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$reactorPom = Join-Path $repoRoot 'tools\reactor\pom.xml'
$headlessRunner = Join-Path $PSScriptRoot 'run-headless-sim.ps1'
$realGameRunner = Join-Path $PSScriptRoot 'run-real-game-e2e.ps1'
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $repoRoot "target\certification\$timestamp"
$isolatedHome = Join-Path ([System.IO.Path]::GetTempPath()) `
    "coronapoker-certification-$timestamp"

New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
New-Item -ItemType Directory -Path $isolatedHome -Force | Out-Null

$userProfile = [Environment]::GetFolderPath('UserProfile')
if ([string]::IsNullOrWhiteSpace($userProfile)) {
    $userProfile = $env:USERPROFILE
}
if ([string]::IsNullOrWhiteSpace($userProfile)) {
    throw 'Cannot resolve the Windows user profile for the local Maven repository.'
}
$mavenRepo = Join-Path $userProfile '.m2\repository'

$maven = 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $maven)) {
    $mavenCommand = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
    if ($null -eq $mavenCommand) {
        throw 'Maven was not found in PATH or in the NetBeans installation.'
    }
    $maven = $mavenCommand.Source
}

$phaseResults = [System.Collections.Generic.List[object]]::new()
$phaseNumber = 0
$totalTimer = [System.Diagnostics.Stopwatch]::StartNew()

function Invoke-CertificationPhase {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$Command,

        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

    $script:phaseNumber++
    $safeName = $Name.ToLowerInvariant() -replace '[^a-z0-9]+', '-'
    $safeName = $safeName.Trim('-')
    $logPath = Join-Path $script:reportDir ('{0:D2}-{1}.log' -f $script:phaseNumber, $safeName)
    $timer = [System.Diagnostics.Stopwatch]::StartNew()

    Write-Host ''
    Write-Host ('=' * 78)
    Write-Host ("CERTIFICATION PHASE {0}: {1}" -f $script:phaseNumber, $Name)
    Write-Host ("Log: {0}" -f $logPath)
    Write-Host ('=' * 78)

    # Native tools such as Maven legitimately write progress/warnings to stderr.
    # With ErrorActionPreference=Stop PowerShell can turn those lines into a
    # terminating NativeCommandError before $LASTEXITCODE can be inspected.
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $Command @Arguments 2>&1 | Tee-Object -FilePath $logPath
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    $timer.Stop()

    if ($exitCode -ne 0) {
        $script:phaseResults.Add([pscustomobject]@{
                Phase = $Name
                Result = 'FAIL'
                Seconds = [math]::Round($timer.Elapsed.TotalSeconds, 1)
                Log = $logPath
            })
        Write-Host ''
        Write-Host "CERTIFICATION FAILED: $Name (exit $exitCode)" -ForegroundColor Red
        Write-Host "Inspect: $logPath" -ForegroundColor Red
        throw "Certification phase failed: $Name"
    }

    $script:phaseResults.Add([pscustomobject]@{
            Phase = $Name
            Result = 'PASS'
            Seconds = [math]::Round($timer.Elapsed.TotalSeconds, 1)
            Log = $logPath
        })
    Write-Host ("PASS: {0} ({1:n1}s)" -f $Name, $timer.Elapsed.TotalSeconds) -ForegroundColor Green
}

$commonMavenArgs = @(
    '-f', $reactorPom,
    '-o',
    "-Dmaven.repo.local=$($mavenRepo.Replace('\', '/'))",
    "-Duser.home=$($isolatedHome.Replace('\', '/'))",
    'test'
)

try {
    Invoke-CertificationPhase `
        -Name 'QA release suite' `
        -Command $maven `
        -Arguments ($commonMavenArgs + @(
                '-Pqa-release',
                '-Dqa.sim.hands=1',
                '-Dqa.sim.faults=1',
                '-Dqa.sim.bot.hands=1'
            ))

    if (-not $SkipBotQuality) {
        Invoke-CertificationPhase `
            -Name 'Bot quality suite' `
            -Command $maven `
            -Arguments ($commonMavenArgs + @('-Pqa-bots'))
    }

    Invoke-CertificationPhase `
        -Name 'Headless protocol campaigns' `
        -Command 'powershell.exe' `
        -Arguments @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $headlessRunner,
            '-Hands', "$Hands", '-Faults', "$Faults", '-BotHands', "$BotHands", '-Seed', "$Seed"
        )

    $scenarios = @(
        @{ Name = 'normal'; Clients = 2; Bots = 2; Hands = 3 },
        @{ Name = 'abrupt-exit'; Clients = 1; Bots = 2; Hands = 1 },
        @{ Name = 'controlled-exit'; Clients = 1; Bots = 2; Hands = 1 },
        @{ Name = 'allin-rit'; Clients = 1; Bots = 0; Hands = 1 },
        @{ Name = 'allin-controlled-exit'; Clients = 1; Bots = 0; Hands = 1 },
        @{ Name = 'force-recover'; Clients = 1; Bots = 2; Hands = 2 },
        @{ Name = 'double-force-recover'; Clients = 1; Bots = 2; Hands = 4 },
        @{ Name = 'crash-rejoin-recover'; Clients = 1; Bots = 2; Hands = 2 },
        @{ Name = 'force-recover-add-client'; Clients = 2; Bots = 2; Hands = 2 }
    )

    for ($scenarioIndex = 0; $scenarioIndex -lt $scenarios.Count; $scenarioIndex++) {
        $scenario = $scenarios[$scenarioIndex]
        $scenarioSeed = $Seed + $scenarioIndex + 1
        $scenarioArgs = @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $realGameRunner,
            '-Scenario', $scenario.Name,
            '-Clients', "$($scenario.Clients)",
            '-Bots', "$($scenario.Bots)",
            '-Hands', "$($scenario.Hands)",
            '-Seed', "$scenarioSeed",
            '-WindowMode', $WindowMode,
            '-Screen', "$Screen"
        )
        if ($Animations) {
            $scenarioArgs += '-Animations'
        }
        if ($ProductionTiming) {
            $scenarioArgs += '-ProductionTiming'
        }

        Invoke-CertificationPhase `
            -Name "Real game: $($scenario.Name)" `
            -Command 'powershell.exe' `
            -Arguments $scenarioArgs
    }
} catch {
    $totalTimer.Stop()
    $summaryPath = Join-Path $reportDir 'summary.csv'
    $phaseResults | Export-Csv -LiteralPath $summaryPath -NoTypeInformation -Encoding UTF8
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host "Partial summary: $summaryPath"
    exit 1
}

$totalTimer.Stop()
$summaryPath = Join-Path $reportDir 'summary.csv'
$phaseResults | Export-Csv -LiteralPath $summaryPath -NoTypeInformation -Encoding UTF8

Write-Host ''
Write-Host ('=' * 78)
Write-Host 'CORONAPOKER CERTIFICATION PASS' -ForegroundColor Green
Write-Host ("Phases: {0}; elapsed: {1}" -f $phaseResults.Count, $totalTimer.Elapsed)
Write-Host ("Summary: {0}" -f $summaryPath)
Write-Host ('=' * 78)

exit 0
