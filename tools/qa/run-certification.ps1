[CmdletBinding()]
param(
    [ValidateSet('quick', 'balanced', 'stress')]
    [string]$Mode = 'balanced',

    [ValidateRange(1, 100000)]
    [int]$Hands,

    [ValidateRange(1, 100000)]
    [int]$Faults,

    [ValidateRange(1, 1000000)]
    [int]$BotHands,

    [ValidateRange(5, 1000)]
    [int]$SoakHands,

    [ValidateRange(1, 5)]
    [int]$EdgeRepeats,

    [long]$Seed = 3231711270,

    [ValidateSet('hidden', 'minimized', 'visible')]
    [string]$WindowMode = 'hidden',

    [ValidateRange(1, 16)]
    [int]$Screen = 2,

    [switch]$Animations,

    [switch]$ProductionTiming,

    [switch]$IncludeBotQuality,

    [string]$StartAtScenario,

    [switch]$VerboseOutput,

    [switch]$Help
)

$ErrorActionPreference = 'Stop'

if ($Help) {
    @'
CoronaPoker complete local certification

Usage:
  powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\qa\run-certification.ps1 [options]

Default mode is balanced: the recommended production gate with every scenario
once and bounded campaign sizes. Phases are fail-fast and sequential:
  1. qa-release: deterministic tests plus every non-bot slow lane
  2. Seeded headless protocol/fault campaigns
  3. Every real-game loopback scenario in separate production JVMs

Options:
  -Mode <mode>             quick, balanced or stress (default: balanced)
  -Hands <1..100000>       Override headless protocol campaign hands
  -Faults <1..100000>      Override headless critical-stream fault cases
  -BotHands <1..1000000>   Override headless production-bot hands
  -SoakHands <5..1000>     Override hands in the real-socket soak game
  -EdgeRepeats <1..5>      Override seeds per destructive/racy scenario
  -Seed <long>             Reproducible base seed (default: 3231711270)
  -WindowMode <mode>       hidden, minimized or visible (default: hidden)
  -Screen <1..16>          Monitor assigned to real-game JVMs (default: 2)
  -Animations              Enable animations in real-game scenarios
  -ProductionTiming        Use production pauses and real Swing action clocks
  -IncludeBotQuality       Also run statistical bot-quality tests (bot changes only)
  -StartAtScenario <label> Continue at a real-game label after a diagnosed failure;
                           skips QA/headless and is not a standalone certificate
  -VerboseOutput           Stream raw Maven/game logs to the console as well as files
  -Help                    Show this help and exit

Examples:
  .\tools\qa\run-certification.ps1
  .\tools\qa\run-certification.ps1 -Mode quick
  .\tools\qa\run-certification.ps1 -Mode stress -Seed 42
  .\tools\qa\run-certification.ps1 -Hands 750 -Faults 750 -EdgeRepeats 2
  .\tools\qa\run-certification.ps1 -IncludeBotQuality
  .\tools\qa\run-certification.ps1 -StartAtScenario reconnect-every-street

Mode defaults (explicit numeric options always win):
  quick     50 hands/faults, 20 bot hands, 5-hand soak, critical scenario subset
  balanced  500 hands/faults, 100 bot hands, 20-hand soak, every scenario once
  stress    5000 hands/faults, 500 bot hands, 50-hand soak, race scenarios x3

Compact progress is printed by default. Full phase logs plus summary.csv and
summary.json are written under target\certification\<timestamp>. The command
exits non-zero at the first failed phase and prints the log tail/path.
'@ | Write-Host
    exit 0
}

$modeDefaults = @{
    quick = @{ Hands = 50; Faults = 50; BotHands = 20; SoakHands = 5; EdgeRepeats = 1 }
    balanced = @{ Hands = 500; Faults = 500; BotHands = 100; SoakHands = 20; EdgeRepeats = 1 }
    stress = @{ Hands = 5000; Faults = 5000; BotHands = 500; SoakHands = 50; EdgeRepeats = 3 }
}[$Mode]
if (-not $PSBoundParameters.ContainsKey('Hands')) { $Hands = $modeDefaults.Hands }
if (-not $PSBoundParameters.ContainsKey('Faults')) { $Faults = $modeDefaults.Faults }
if (-not $PSBoundParameters.ContainsKey('BotHands')) { $BotHands = $modeDefaults.BotHands }
if (-not $PSBoundParameters.ContainsKey('SoakHands')) { $SoakHands = $modeDefaults.SoakHands }
if (-not $PSBoundParameters.ContainsKey('EdgeRepeats')) { $EdgeRepeats = $modeDefaults.EdgeRepeats }

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$reactorPom = Join-Path $repoRoot 'tools\reactor\pom.xml'
$headlessRunner = Join-Path $PSScriptRoot 'run-headless-sim.ps1'
$realGameRunner = Join-Path $PSScriptRoot 'run-real-game-e2e.ps1'
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $repoRoot "target\certification\$timestamp"
New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
$qaUserHome = Join-Path $reportDir 'qa-home'

function Remove-CertificationQaHome {
    $reportPath = [IO.Path]::GetFullPath($script:reportDir)
    $homePath = [IO.Path]::GetFullPath($script:qaUserHome)
    if (-not $homePath.StartsWith($reportPath + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing unsafe certification QA home path: $homePath"
    }
    if (Test-Path -LiteralPath $homePath) {
        Remove-Item -LiteralPath $homePath -Recurse -Force
    }
}

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

$phaseResults = [System.Collections.Generic.List[object]]::new()
$phaseNumber = 0
$totalTimer = [System.Diagnostics.Stopwatch]::StartNew()

function Write-CertificationSummary {
    param([string]$Prefix = 'Summary')
    $csvPath = Join-Path $script:reportDir 'summary.csv'
    $jsonPath = Join-Path $script:reportDir 'summary.json'
    $script:phaseResults | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding UTF8
    $script:phaseResults | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $jsonPath -Encoding UTF8
    Write-Host ''
    $script:phaseResults | Select-Object Phase, Result, Seconds | Format-Table -AutoSize | Out-Host
    Write-Host ("{0}: {1}" -f $Prefix, $csvPath)
    Write-Host ("Machine-readable JSON: {0}" -f $jsonPath)
}

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
    Write-Host ("[{0}] RUN  {1}" -f $script:phaseNumber, $Name) -ForegroundColor Cyan

    # Native tools such as Maven legitimately write progress/warnings to stderr.
    # With ErrorActionPreference=Stop PowerShell can turn those lines into a
    # terminating NativeCommandError before $LASTEXITCODE can be inspected.
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    $writer = New-Object System.IO.StreamWriter($logPath, $false, $utf8)
    $lastProgress = $null
    try {
        & $Command @Arguments 2>&1 | ForEach-Object {
            $line = $_.ToString()
            $writer.WriteLine($line)
            if ($script:VerboseOutput) {
                Write-Host $line
            } elseif ($line -match 'CP_E2E_PROGRESS completed=(\d+) requested=(\d+)') {
                $progress = "{0}/{1}" -f $Matches[1], $Matches[2]
                if ($progress -ne $lastProgress) {
                    Write-Host ("    hands {0}" -f $progress) -ForegroundColor DarkGray
                    $lastProgress = $progress
                }
            } elseif ($line -match 'CP_E2E_FAIL') {
                Write-Host ("    {0}" -f $line) -ForegroundColor Red
            }
        }
        $exitCode = $LASTEXITCODE
    } finally {
        $writer.Dispose()
    }
    if ($exitCode -eq 0 -and (Select-String -LiteralPath $logPath -Pattern 'CP_E2E_FAIL' -Quiet)) {
        Write-Host '    terminal CP_E2E_FAIL marker found in a nominally successful phase' `
            -ForegroundColor Red
        $exitCode = 1
    }
    $ErrorActionPreference = $previousErrorActionPreference
    $timer.Stop()

    if ($exitCode -ne 0) {
        $script:phaseResults.Add([pscustomobject]@{
                Phase = $Name
                Result = 'FAIL'
                Seconds = [math]::Round($timer.Elapsed.TotalSeconds, 1)
                Log = $logPath
            })
        Write-Host ("[{0}] FAIL {1} ({2:n1}s, exit {3})" -f $script:phaseNumber, $Name,
                $timer.Elapsed.TotalSeconds, $exitCode) -ForegroundColor Red
        Write-Host 'Last log lines:' -ForegroundColor DarkGray
        Get-Content -LiteralPath $logPath -Tail 30 | ForEach-Object { Write-Host $_ }
        Write-Host "Full log: $logPath" -ForegroundColor Red
        throw "Certification phase failed: $Name"
    }

    $script:phaseResults.Add([pscustomobject]@{
            Phase = $Name
            Result = 'PASS'
            Seconds = [math]::Round($timer.Elapsed.TotalSeconds, 1)
            Log = $logPath
        })
    Write-Host ("[{0}] PASS {1} ({2:n1}s)" -f $script:phaseNumber, $Name,
            $timer.Elapsed.TotalSeconds) -ForegroundColor Green
}

$commonMavenArgs = @(
    '-f', $reactorPom,
    "-Dmaven.repo.local=$($mavenRepo.Replace('\', '/'))",
    "-Dqa.user.home=$($qaUserHome.Replace('\', '/'))",
    'install'
)

Write-Host 'CoronaPoker local certification' -ForegroundColor Cyan
Write-Host ("Mode={0} seed={1} campaigns={2}/{3}/{4} soak={5} edgeRepeats={6}" -f `
        $Mode, $Seed, $Hands, $Faults, $BotHands, $SoakHands, $EdgeRepeats)
Write-Host ("Windows={0} screen={1} animations={2} productionTiming={3}" -f `
        $WindowMode, $Screen, [bool]$Animations, [bool]$ProductionTiming)
Write-Host ("Reports: {0}" -f $reportDir)

try {
    if (-not $StartAtScenario) {
        Invoke-CertificationPhase `
            -Name 'QA release suite' `
            -Command $maven `
            -Arguments ($commonMavenArgs + @(
                    '-Pqa-release',
                    '-Dqa.sim.hands=1',
                    '-Dqa.sim.faults=1',
                    '-Dqa.sim.bot.hands=1'
                ))

        if ($IncludeBotQuality) {
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
                '-Hands', "$Hands", '-Faults', "$Faults", '-BotHands', "$BotHands", '-Seed', "$Seed",
                '-SkipGameBuild'
            )
    } else {
        Write-Host ("Continuation mode: skipping QA/headless; starting at {0}" -f `
                $StartAtScenario) -ForegroundColor Yellow
    }

    $fullMixedHands = if ($Mode -eq 'stress') { 10 } elseif ($Mode -eq 'quick') { 1 } else { 3 }
    $fullHumanHands = if ($Mode -eq 'stress') { 3 } else { 1 }
    $headsUpHands = if ($Mode -eq 'quick') { 5 } else { 20 }
    $scenarioProfiles = @(
        @{ Label = 'normal-soak'; Name = 'normal'; Clients = 2; Bots = 2; Hands = $SoakHands; Repeat = $false },
        @{ Label = 'normal-heads-up'; Name = 'normal'; Clients = 1; Bots = 0; Hands = $headsUpHands; Repeat = $true },
        @{ Label = 'normal-full-mixed'; Name = 'normal'; Clients = 4; Bots = 5; Hands = $fullMixedHands; Repeat = $false },
        @{ Label = 'normal-full-human'; Name = 'normal'; Clients = 9; Bots = 0; Hands = $fullHumanHands; Repeat = $false },
        @{ Label = 'raise-mix'; Name = 'raise-mix'; Clients = 2; Bots = 2; Hands = 10; Repeat = $true },
        @{ Label = 'allin-single-board'; Name = 'allin-single-board'; Clients = 1; Bots = 0; Hands = 1; Repeat = $true },
        @{ Label = 'allin-rebuy'; Name = 'allin-rebuy'; Clients = 1; Bots = 0; Hands = 5; Repeat = $true },
        @{ Label = 'allin-rit'; Name = 'allin-rit'; Clients = 1; Bots = 0; Hands = 1; Repeat = $true },
        @{ Label = 'allin-controlled-exit'; Name = 'allin-controlled-exit'; Clients = 1; Bots = 0; Hands = 1; Repeat = $true },
        @{ Label = 'straddle-post'; Name = 'straddle-post'; Clients = 2; Bots = 0; Hands = 3; Repeat = $true },
        @{ Label = 'pause-resume'; Name = 'pause-resume'; Clients = 2; Bots = 1; Hands = 2; Repeat = $true },
        @{ Label = 'reconnect-midhand'; Name = 'reconnect-midhand'; Clients = 2; Bots = 1; Hands = 2; Repeat = $true },
        @{ Label = 'reconnect-twice'; Name = 'reconnect-twice'; Clients = 2; Bots = 1; Hands = 3; Repeat = $true },
        @{ Label = 'reconnect-storm'; Name = 'reconnect-storm'; Clients = 2; Bots = 1; Hands = 4; Repeat = $true },
        @{ Label = 'dual-reconnect'; Name = 'dual-reconnect'; Clients = 3; Bots = 1; Hands = 3; Repeat = $true },
        @{ Label = 'host-channel-flap'; Name = 'host-channel-flap'; Clients = 3; Bots = 1; Hands = 2; Repeat = $true },
        @{ Label = 'reconnect-every-street'; Name = 'reconnect-every-street'; Clients = 2; Bots = 1; Hands = 4; Repeat = $true },
        @{ Label = 'allin-reconnect'; Name = 'allin-reconnect'; Clients = 2; Bots = 0; Hands = 1; Repeat = $true },
        @{ Label = 'rit-network-cut'; Name = 'rit-network-cut'; Clients = 2; Bots = 0; Hands = 1; Repeat = $true },
        @{ Label = 'straddle-network-cut'; Name = 'straddle-network-cut'; Clients = 2; Bots = 0; Hands = 3; Repeat = $true },
        @{ Label = 'reconnect-force-recover'; Name = 'reconnect-force-recover'; Clients = 2; Bots = 1; Hands = 3; Repeat = $true },
        @{ Label = 'transport-chaos'; Name = 'transport-chaos'; Clients = 3; Bots = 1; Hands = 5; Repeat = $true },
        @{ Label = 'lifecycle-chaos'; Name = 'lifecycle-chaos'; Clients = 2; Bots = 1; Hands = 7; Repeat = $true },
        @{ Label = 'abrupt-exit-survivor'; Name = 'abrupt-exit'; Clients = 2; Bots = 1; Hands = 1; Repeat = $true },
        @{ Label = 'controlled-exit-survivor'; Name = 'controlled-exit'; Clients = 2; Bots = 1; Hands = 1; Repeat = $true },
        @{ Label = 'dual-abrupt-exit'; Name = 'dual-abrupt-exit'; Clients = 3; Bots = 1; Hands = 1; Repeat = $true },
        @{ Label = 'mixed-exit-crash'; Name = 'mixed-exit-crash'; Clients = 3; Bots = 1; Hands = 1; Repeat = $true },
        @{ Label = 'allin-abrupt-exit'; Name = 'allin-abrupt-exit'; Clients = 2; Bots = 0; Hands = 1; Repeat = $true },
        @{ Label = 'force-recover'; Name = 'force-recover'; Clients = 1; Bots = 2; Hands = 2; Repeat = $true },
        @{ Label = 'double-force-recover'; Name = 'double-force-recover'; Clients = 1; Bots = 2; Hands = 4; Repeat = $false },
        @{ Label = 'crash-rejoin-recover'; Name = 'crash-rejoin-recover'; Clients = 1; Bots = 2; Hands = 2; Repeat = $true },
        @{ Label = 'force-recover-add-client'; Name = 'force-recover-add-client'; Clients = 2; Bots = 2; Hands = 2; Repeat = $true },
        @{ Label = 'force-recover-add-two'; Name = 'force-recover-add-two'; Clients = 3; Bots = 1; Hands = 2; Repeat = $true },
        @{ Label = 'force-recover-swap-client'; Name = 'force-recover-swap-client'; Clients = 2; Bots = 1; Hands = 2; Repeat = $true }
    )

    if ($Mode -eq 'quick') {
        $quickLabels = @(
            'normal-soak',
            'normal-heads-up',
            'normal-full-mixed',
            'allin-single-board',
            'allin-rebuy',
            'allin-rit',
            'host-channel-flap',
            'mixed-exit-crash',
            'reconnect-force-recover',
            'force-recover',
            'crash-rejoin-recover'
        )
        $scenarioProfiles = @($scenarioProfiles | Where-Object { $quickLabels -contains $_.Label })
    }

    $scenarios = [System.Collections.Generic.List[object]]::new()
    foreach ($profile in $scenarioProfiles) {
        $repetitions = if ($profile.Repeat) { $EdgeRepeats } else { 1 }
        for ($repeat = 1; $repeat -le $repetitions; $repeat++) {
            $scenarios.Add([pscustomobject]@{
                    Label = $profile.Label
                    Name = $profile.Name
                    Clients = $profile.Clients
                    Bots = $profile.Bots
                    Hands = $profile.Hands
                    Repeat = $repeat
                })
        }
    }

    $firstScenarioIndex = 0
    if ($StartAtScenario) {
        $matchingScenario = @($scenarios | Where-Object { $_.Label -eq $StartAtScenario })
        if ($matchingScenario.Count -eq 0) {
            throw "Unknown or unavailable StartAtScenario label: $StartAtScenario"
        }
        $firstScenarioIndex = $scenarios.IndexOf($matchingScenario[0])
    }

    for ($scenarioIndex = $firstScenarioIndex; $scenarioIndex -lt $scenarios.Count; $scenarioIndex++) {
        $scenario = $scenarios[$scenarioIndex]
        $scenarioSeed = $Seed + (($scenarioIndex + 1) * 1009)
        $scenarioArgs = @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $realGameRunner,
            '-Scenario', $scenario.Name,
            '-Clients', "$($scenario.Clients)",
            '-Bots', "$($scenario.Bots)",
            '-Hands', "$($scenario.Hands)",
            '-Seed', "$scenarioSeed",
            '-WindowMode', $WindowMode,
            '-Screen', "$Screen",
            '-SkipGameBuild'
        )
        if ($Animations) {
            $scenarioArgs += '-Animations'
        }
        if ($ProductionTiming) {
            $scenarioArgs += '-ProductionTiming'
        }

        Invoke-CertificationPhase `
            -Name "Real game: $($scenario.Label) seed $scenarioSeed" `
            -Command 'powershell.exe' `
            -Arguments $scenarioArgs
    }
    Remove-CertificationQaHome
} catch {
    $failure = $_
    $totalTimer.Stop()
    try {
        Remove-CertificationQaHome
    } catch {
        Write-Host "QA home cleanup also failed: $($_.Exception.Message)" -ForegroundColor Yellow
    }
    Write-Host $failure.Exception.Message -ForegroundColor Red
    Write-CertificationSummary -Prefix 'Partial summary'
    exit 1
}

$totalTimer.Stop()
Write-Host ''
Write-Host ('=' * 78)
if ($StartAtScenario) {
    Write-Host 'CORONAPOKER CERTIFICATION CONTINUATION PASS' -ForegroundColor Green
    Write-Host 'This continuation is evidence only; combine it with the preceding checkpoint.' `
        -ForegroundColor Yellow
} else {
    Write-Host 'CORONAPOKER CERTIFICATION PASS' -ForegroundColor Green
}
Write-Host ("Phases: {0}; elapsed: {1}" -f $phaseResults.Count, $totalTimer.Elapsed)
Write-Host ('=' * 78)
Write-CertificationSummary

exit 0
