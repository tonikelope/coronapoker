[CmdletBinding()]
param(
    [ValidateSet('quick', 'fast', 'balanced', 'stress')]
    [string] $Mode = 'balanced',
    [string] $Scenario = 'all',
    [string] $StartAtScenario = '',
    [ValidateRange(1, 10)] [int] $StartAtRepeat = 1,
    [ValidateRange(1, 10)] [int] $ScenarioRepeats,
    [ValidateRange(5, 1000)] [int] $SoakHands,
    [long] $Seed,
    [switch] $ListOnly,
    [switch] $VerboseOutput,
    [switch] $Help
)

$ErrorActionPreference = 'Stop'

if ($Help) {
    @'
CoronaPoker GDX scenario certification

Usage:
  .\tools\qa\certify.cmd [-Mode quick|fast|balanced|stress]
  .\tools\qa\certify.cmd -Scenario spectator-rebuy-cycle -Mode fast
  .\tools\qa\certify.cmd -StartAtScenario reconnect-every-street -StartAtRepeat 2 -Seed 42
  .\tools\qa\certify.cmd -ListOnly

This command certifies game behaviour. It runs the authoritative GDX scenario
mapping: every historical Swing GOLD scenario plus the GDX-only product
scenarios. Each mapped test receives a fresh Maven process and therefore a
fresh JVM. Code tests and mass headless campaigns are separate lanes.

Modes:
  quick      Critical iteration subset, one pass, 5-hand normal soak
  fast       Complete scenario catalogue, one pass, 5-hand normal soak
  balanced   Complete catalogue, two passes, 20-hand normal soak
  stress     Complete catalogue, five passes, 50-hand normal soak

Options:
  -Scenario <name>          Run one scenario and all of its mapped tests
  -StartAtScenario <name>   Resume the complete schedule at a scenario
  -StartAtRepeat <1..10>    Repetition used with -StartAtScenario
  -ScenarioRepeats <1..10>  Override the mode repetition count
  -SoakHands <5..1000>      Override the normal soak length
  -Seed <long>              Replay a base seed; otherwise one is generated
  -ListOnly                 Print the executable catalogue without running it
  -VerboseOutput            Stream Maven output as well as writing logs
  -Help                     Show this help

Reports are written below target\certification. A failure stops the run and
leaves CSV, JSON and per-test logs. A resumed run is continuation evidence and
must be combined with the preceding partial report.
'@ | Write-Host
    exit 0
}

. (Join-Path $PSScriptRoot 'qa-seed.ps1')

if ($StartAtScenario -and $Scenario -ne 'all') {
    throw '-StartAtScenario can only be used with the complete catalogue.'
}
if ($PSBoundParameters.ContainsKey('StartAtRepeat') -and -not $StartAtScenario) {
    throw '-StartAtRepeat requires -StartAtScenario.'
}
if ($StartAtScenario -and -not $PSBoundParameters.ContainsKey('Seed')) {
    throw '-StartAtScenario requires the BaseSeed from the original run.'
}
if (-not $PSBoundParameters.ContainsKey('Seed')) {
    $Seed = New-CoronaPokerQaSeed
}

$modeDefaults = @{
    quick = @{ Repeats = 1; Soak = 5; HeadsUp = 5; FullMixed = 1; FullHuman = 1 }
    fast = @{ Repeats = 1; Soak = 5; HeadsUp = 5; FullMixed = 1; FullHuman = 1 }
    balanced = @{ Repeats = 2; Soak = 20; HeadsUp = 20; FullMixed = 3; FullHuman = 1 }
    stress = @{ Repeats = 5; Soak = 50; HeadsUp = 50; FullMixed = 10; FullHuman = 3 }
}[$Mode]
if (-not $PSBoundParameters.ContainsKey('ScenarioRepeats')) {
    $ScenarioRepeats = $modeDefaults.Repeats
}
if (-not $PSBoundParameters.ContainsKey('SoakHands')) {
    $SoakHands = $modeDefaults.Soak
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$contractPath = Join-Path $repoRoot ('modules\coronapoker-gdx\src\test\java\com\' +
    'tonikelope\coronapoker\gdx\scenarios\GdxScenarioContract.java')
$testRoot = Join-Path $repoRoot 'modules\coronapoker-gdx\src\test\java'
$reactorPom = Join-Path $repoRoot 'modules\pom.xml'
$mavenRepo = Join-Path $repoRoot '.m2\repository'

if (-not (Test-Path -LiteralPath $contractPath)) {
    throw "GDX scenario contract not found: $contractPath"
}

# The Java contract is the single catalogue. GOLD is intentionally read from
# its dedicated multi-process map; native GDX UI checks and GDX-only scenarios
# are additive gates, never substitutes for a historical Swing scenario.
$contractSource = Get-Content -LiteralPath $contractPath -Raw
$entryPattern = [regex]::new(
    'Map\.entry\("([^"]+)"\s*,\s*Set\.of\((.*?)\)\)',
    [System.Text.RegularExpressions.RegexOptions]::Singleline)
$quotedPattern = [regex]::new('"([^"]+)"')
$catalogue = [System.Collections.Generic.List[object]]::new()
$certificationMaps = @(
    'SWING_GOLD_MULTIPROCESS_TESTS',
    'NATIVE_GDX_UI_TESTS',
    'GDX_ONLY_SCENARIOS'
)
foreach ($mapName in $certificationMaps) {
    $mapStart = $contractSource.IndexOf($mapName)
    if ($mapStart -lt 0) {
        throw "Cannot locate $mapName in GdxScenarioContract.java."
    }
    $nextMap = $contractSource.IndexOf('static final Map', $mapStart + $mapName.Length)
    $constructor = $contractSource.IndexOf('private GdxScenarioContract', $mapStart)
    $mapEnd = if ($nextMap -ge 0 -and
            ($constructor -lt 0 -or $nextMap -lt $constructor)) {
        $nextMap
    } else {
        $constructor
    }
    if ($mapEnd -le $mapStart) {
        throw "Cannot delimit $mapName in GdxScenarioContract.java."
    }
    $mapBlock = $contractSource.Substring($mapStart, $mapEnd - $mapStart)
    foreach ($match in $entryPattern.Matches($mapBlock)) {
        $methods = @($quotedPattern.Matches($match.Groups[2].Value) |
            ForEach-Object { $_.Groups[1].Value })
        if ($methods.Count -eq 0) {
            throw "GDX scenario '$($match.Groups[1].Value)' has no tests in $mapName."
        }
        $catalogue.Add([pscustomobject]@{
                Scenario = $match.Groups[1].Value
                Methods = $methods
                Lane = $mapName
            })
    }
}
if ($catalogue.Count -eq 0) {
    throw 'No GDX certification scenarios were discovered.'
}

$testSources = @(Get-ChildItem -LiteralPath $testRoot -Recurse -Filter '*Test.java' |
    ForEach-Object {
        [pscustomobject]@{
            Source = Get-Content -LiteralPath $_.FullName -Raw
        }
    })
$resolved = [System.Collections.Generic.List[object]]::new()
foreach ($entry in $catalogue) {
    foreach ($method in $entry.Methods) {
        $owners = [System.Collections.Generic.List[string]]::new()
        foreach ($file in $testSources) {
            if ($file.Source -notmatch ('\bvoid\s+' +
                    [regex]::Escape($method) + '\s*\(')) {
                continue
            }
            $packageMatch = [regex]::Match($file.Source,
                '(?m)^package\s+([A-Za-z0-9_.]+)\s*;')
            $classMatch = [regex]::Match($file.Source,
                '(?m)^\s*(?:public\s+)?(?:final\s+)?class\s+([A-Za-z0-9_]+)')
            if ($packageMatch.Success -and $classMatch.Success) {
                $owners.Add($packageMatch.Groups[1].Value + '.' +
                    $classMatch.Groups[1].Value)
            }
        }
        if ($owners.Count -ne 1) {
            throw "GDX test '$method' must have one owning class; found $($owners.Count)."
        }
        $resolved.Add([pscustomobject]@{
                Scenario = $entry.Scenario
                Class = $owners[0]
                Method = $method
            })
    }
}

$quickScenarios = @(
    'normal', 'allin-single-board', 'allin-rebuy', 'allin-rit',
    'pause-resume', 'mixed-exit-crash', 'host-channel-flap',
    'reconnect-force-recover', 'force-recover', 'crash-rejoin-recover',
    'spectator-rebuy-cycle', 'rabbit-hunting'
)
if ($Scenario -ne 'all') {
    $resolved = @($resolved | Where-Object { $_.Scenario -eq $Scenario })
    if ($resolved.Count -eq 0) {
        throw "Unknown GDX scenario '$Scenario'. Use -ListOnly to inspect the catalogue."
    }
} elseif ($Mode -eq 'quick') {
    $resolved = @($resolved | Where-Object {
            $quickScenarios -contains $_.Scenario
        })
}

if ($ListOnly) {
    $resolved | ForEach-Object {
        '{0} -> {1}#{2}' -f $_.Scenario, $_.Class, $_.Method
    }
    exit 0
}

$maven = $null
$wrapper = Join-Path $repoRoot 'mvnw.cmd'
if (Test-Path -LiteralPath $wrapper) { $maven = $wrapper }
foreach ($candidate in @('mvn.cmd', 'mvn')) {
    if ($null -eq $maven) {
        $command = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($null -ne $command) { $maven = $command.Source }
    }
}
if ($null -eq $maven) {
    $netBeansMaven = 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd'
    if (Test-Path -LiteralPath $netBeansMaven) {
        $maven = $netBeansMaven
    } else {
        throw 'Maven was not found in mvnw.cmd, PATH or Apache NetBeans.'
    }
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $repoRoot "target\certification\$stamp-$Mode"
New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
$results = [System.Collections.Generic.List[object]]::new()
$timer = [System.Diagnostics.Stopwatch]::StartNew()

function Write-CertificationSummary {
    $csv = Join-Path $script:reportDir 'summary.csv'
    $json = Join-Path $script:reportDir 'summary.json'
    $script:results | Export-Csv -LiteralPath $csv -NoTypeInformation -Encoding utf8
    $script:results | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $json -Encoding utf8
    Write-Host "Summary: $csv"
    Write-Host "Machine-readable summary: $json"
}

Write-Host 'Clean-compiling the current GDX scenario reactor...' -ForegroundColor Cyan
$preflightLog = Join-Path $reportDir '00-clean-test-compile.log'
$preflightArgs = @(
    '-B', '-f', $reactorPom,
    '-pl', 'coronapoker-gdx', '-am',
    "-Dmaven.repo.local=$mavenRepo",
    '-DskipTests', 'clean', 'test-compile'
)
$previousPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    & $maven @preflightArgs 2>&1 |
        Out-File -LiteralPath $preflightLog -Encoding utf8
    $preflightExit = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousPreference
}
if ($preflightExit -ne 0) {
    Get-Content -LiteralPath $preflightLog -Tail 80 | Write-Host
    throw "Scenario reactor does not clean-compile. Log: $preflightLog"
}

$schedule = [System.Collections.Generic.List[object]]::new()
for ($repeat = 1; $repeat -le $ScenarioRepeats; $repeat++) {
    foreach ($test in $resolved) {
        $schedule.Add([pscustomobject]@{
                Repeat = $repeat
                Scenario = $test.Scenario
                Class = $test.Class
                Method = $test.Method
            })
    }
}
if ($StartAtScenario) {
    $startIndex = -1
    for ($index = 0; $index -lt $schedule.Count; $index++) {
        if ($schedule[$index].Scenario -eq $StartAtScenario -and
                $schedule[$index].Repeat -eq $StartAtRepeat) {
            $startIndex = $index
            break
        }
    }
    if ($startIndex -lt 0) {
        throw "Unknown continuation point '$StartAtScenario' repeat $StartAtRepeat."
    }
    $schedule = @($schedule | Select-Object -Skip $startIndex)
}

Write-Host ("Mode={0} BaseSeed={1} scenarios={2} tests={3} repeats={4} soakHands={5}" -f
    $Mode, $Seed, (@($resolved.Scenario | Sort-Object -Unique).Count),
    $resolved.Count, $ScenarioRepeats, $SoakHands)
Write-Host "Reports: $reportDir"

$failed = $false
for ($index = 0; $index -lt $schedule.Count; $index++) {
    $test = $schedule[$index]
    $scenarioSeed = $Seed + (($index + 1) * 1009)
    $selector = $test.Class + '#' + $test.Method
    $safeName = ($test.Scenario + '-' + $test.Method) -replace '[^A-Za-z0-9_.-]', '_'
    $log = Join-Path $reportDir ("{0:D3}-r{1}-{2}.log" -f
        ($index + 1), $test.Repeat, $safeName)
    $started = Get-Date
    Write-Host ("[{0}/{1}] r{2} {3} -> {4}" -f
        ($index + 1), $schedule.Count, $test.Repeat, $test.Scenario, $selector)
    $arguments = @(
        '-B', '-f', $reactorPom,
        '-pl', 'coronapoker-gdx', '-am', 'test',
        "-Dmaven.repo.local=$mavenRepo",
        "-Dtest=$selector",
        '-Dsurefire.failIfNoSpecifiedTests=false',
        '-Dcoronapoker.gdx.excludedGroups=',
        "-Dqa.sim.seed=$scenarioSeed",
        "-Dcoronapoker.qa.gdx.soakHands=$SoakHands",
        "-Dcoronapoker.qa.gdx.headsUpHands=$($modeDefaults.HeadsUp)",
        "-Dcoronapoker.qa.gdx.fullMixedHands=$($modeDefaults.FullMixed)",
        "-Dcoronapoker.qa.gdx.fullHumanHands=$($modeDefaults.FullHuman)"
    )
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if ($VerboseOutput) {
            & $maven @arguments 2>&1 | Tee-Object -FilePath $log
        } else {
            & $maven @arguments 2>&1 |
                Out-File -LiteralPath $log -Encoding utf8
        }
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    $seconds = [math]::Round(((Get-Date) - $started).TotalSeconds, 3)
    $status = if ($exitCode -eq 0) { 'PASS' } else { 'FAIL' }
    $results.Add([pscustomobject]@{
            Repeat = $test.Repeat
            Scenario = $test.Scenario
            Test = $selector
            Seed = $scenarioSeed
            Result = $status
            Seconds = $seconds
            Log = $log
            Mode = $Mode
            BaseSeed = $Seed
            ScenarioRepeats = $ScenarioRepeats
            SoakHands = $SoakHands
        })
    Write-Host "  $status ($seconds s)"
    if ($exitCode -ne 0) {
        $failed = $true
        Get-Content -LiteralPath $log -Tail 80 | Write-Host
        break
    }
}

$timer.Stop()
Write-CertificationSummary
Write-Host ("Elapsed: {0}" -f $timer.Elapsed)
if ($failed) {
    Write-Host 'CORONAPOKER GDX CERTIFICATION FAIL' -ForegroundColor Red
    exit 1
}
if ($StartAtScenario) {
    Write-Host 'CORONAPOKER GDX CERTIFICATION CONTINUATION PASS' -ForegroundColor Green
} else {
    Write-Host 'CORONAPOKER GDX CERTIFICATION PASS' -ForegroundColor Green
}
exit 0
