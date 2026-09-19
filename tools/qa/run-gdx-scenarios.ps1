param(
    [ValidateSet('fast', 'balanced', 'stress')]
    [string]$Mode = 'fast',

    [string]$Scenario = 'all',

    [string]$StartAt = '',

    [switch]$FailFast,

    [switch]$ListOnly,

    [switch]$Help
)

$ErrorActionPreference = 'Stop'

if ($Help) {
    @'
CoronaPoker isolated GDX scenario suite

Usage:
  .\tools\qa\gdx-scenarios.cmd [-Mode fast|balanced|stress]
  .\tools\qa\gdx-scenarios.cmd -Scenario spectator-rebuy-cycle
  .\tools\qa\gdx-scenarios.cmd -StartAt bot-bust-recover-drop
  .\tools\qa\gdx-scenarios.cmd -ListOnly

Every strict GDX homologue runs in its own Maven process and therefore in a
fresh JVM. This deliberately matches the process isolation of the established
Swing real-game scenarios: static QA properties, sockets, dealers and executor
threads from one scenario cannot contaminate the next one.

The selected GDX reactor is compiled from empty, explicitly validated module
target directories once before the isolated executions, preventing leftover
.class files from hiding missing or stale production sources.

FAST runs every mapped test once, BALANCED twice and STRESS five times.
Logs and the machine-readable summary are written below target\gdx-scenarios.
'@ | Write-Host
    exit 0
}

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$contractPath = Join-Path $root ('modules\coronapoker-gdx\src\test\java\com\' +
    'tonikelope\coronapoker\gdx\scenarios\GdxScenarioContract.java')
$testRoot = Join-Path $root 'modules\coronapoker-gdx\src\test\java'
$reactor = Join-Path $root 'modules\pom.xml'

if (-not (Test-Path -LiteralPath $contractPath)) {
    throw "GDX scenario contract not found: $contractPath"
}

$source = Get-Content -LiteralPath $contractPath -Raw
$strictStart = $source.IndexOf('STRICT_HOMOLOGUE_TESTS')
$strictEnd = $source.IndexOf('private GdxScenarioContract', $strictStart)
if ($strictStart -lt 0 -or $strictEnd -le $strictStart) {
    throw 'Cannot locate STRICT_HOMOLOGUE_TESTS in GdxScenarioContract.java'
}
$strictBlock = $source.Substring($strictStart, $strictEnd - $strictStart)
$entryPattern = [regex]::new(
    'Map\.entry\("([^"]+)"\s*,\s*Set\.of\((.*?)\)\)',
    [System.Text.RegularExpressions.RegexOptions]::Singleline)
$quotedPattern = [regex]::new('"([^"]+)"')
$entries = [System.Collections.Generic.List[object]]::new()
foreach ($match in $entryPattern.Matches($strictBlock)) {
    $methods = @($quotedPattern.Matches($match.Groups[2].Value) |
        ForEach-Object { $_.Groups[1].Value })
    if ($methods.Count -eq 0) {
        throw "Strict GDX scenario '$($match.Groups[1].Value)' has no tests"
    }
    $entries.Add([pscustomobject]@{
        Scenario = $match.Groups[1].Value
        Methods = $methods
    })
}
if ($entries.Count -eq 0) {
    throw 'No strict GDX scenarios were discovered'
}

if ($Scenario -ne 'all') {
    $entries = @($entries | Where-Object { $_.Scenario -eq $Scenario })
    if ($entries.Count -eq 0) {
        throw "Unknown GDX scenario '$Scenario'. Use -ListOnly to inspect the catalogue."
    }
}
if ($StartAt -and $Scenario -ne 'all') {
    throw '-StartAt can only be used while running the complete catalogue'
}

$testSources = @(Get-ChildItem -LiteralPath $testRoot -Recurse -Filter '*Test.java')
$resolved = [System.Collections.Generic.List[object]]::new()
foreach ($entry in $entries) {
    foreach ($method in $entry.Methods) {
        $owners = [System.Collections.Generic.List[string]]::new()
        foreach ($file in $testSources) {
            $testSource = Get-Content -LiteralPath $file.FullName -Raw
            if ($testSource -notmatch ('\bvoid\s+' + [regex]::Escape($method) + '\s*\(')) {
                continue
            }
            $packageMatch = [regex]::Match($testSource,
                '(?m)^package\s+([A-Za-z0-9_.]+)\s*;')
            $classMatch = [regex]::Match($testSource,
                '(?m)^\s*(?:public\s+)?(?:final\s+)?class\s+([A-Za-z0-9_]+)')
            if ($packageMatch.Success -and $classMatch.Success) {
                $owners.Add($packageMatch.Groups[1].Value + '.' +
                    $classMatch.Groups[1].Value)
            }
        }
        if ($owners.Count -ne 1) {
            throw "GDX test method '$method' must have exactly one owning test class; found $($owners.Count)"
        }
        $resolved.Add([pscustomobject]@{
            Scenario = $entry.Scenario
            Class = $owners[0]
            Method = $method
        })
    }
}

if ($StartAt) {
    $startIndex = -1
    for ($index = 0; $index -lt $resolved.Count; $index++) {
        if ($resolved[$index].Scenario -eq $StartAt) {
            $startIndex = $index
            break
        }
    }
    if ($startIndex -lt 0) {
        throw "Unknown GDX start scenario '$StartAt'. Use -ListOnly to inspect the catalogue."
    }
    $resolved = @($resolved | Select-Object -Skip $startIndex)
}

if ($ListOnly) {
    $resolved | ForEach-Object {
        '{0} -> {1}#{2}' -f $_.Scenario, $_.Class, $_.Method
    }
    exit 0
}

$maven = (Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue).Source
if (-not $maven) {
    $netBeansMaven = 'C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd'
    if (Test-Path -LiteralPath $netBeansMaven) {
        $maven = $netBeansMaven
    } else {
        throw 'mvn.cmd was not found in PATH or in the Apache NetBeans installation'
    }
}

$repetitions = switch ($Mode) {
    'fast' { 1 }
    'balanced' { 2 }
    'stress' { 5 }
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$resultRoot = Join-Path $root "target\gdx-scenarios\$stamp-$Mode"
New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
$mavenRepo = Join-Path $root '.m2\repository'
$preflightLog = Join-Path $resultRoot '00-clean-test-compile.log'

Write-Host 'Clean-compiling the current GDX reactor before isolated scenarios...'
$modulesRoot = [IO.Path]::GetFullPath((Join-Path $root 'modules'))
foreach ($relativeTarget in @(
        'modules\target',
        'modules\coronapoker-core\target',
        'modules\coronapoker-assets\target',
        'modules\coronapoker-gdx\target')) {
    $targetPath = [IO.Path]::GetFullPath((Join-Path $root $relativeTarget))
    if (-not $targetPath.StartsWith(
            $modulesRoot + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase) `
            -or [IO.Path]::GetFileName($targetPath) -ne 'target') {
        throw "Refusing unsafe module target path: $targetPath"
    }
    if (Test-Path -LiteralPath $targetPath) {
        Remove-Item -LiteralPath $targetPath -Recurse -Force
    }
}
$preflightArgs = @(
    '-B', '-o', '-f', $reactor,
    '-pl', 'coronapoker-gdx', '-am',
    "-Dmaven.repo.local=$mavenRepo",
    '-DskipTests',
    'test-compile'
)
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    & $maven @preflightArgs 2>&1 |
        Out-File -LiteralPath $preflightLog -Encoding utf8
    $preflightExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorActionPreference
}
if ($preflightExitCode -ne 0) {
    Get-Content -LiteralPath $preflightLog -Tail 100 | Write-Host
    throw "Current GDX checkout does not clean-compile; no scenario was run. Log: $preflightLog"
}
Write-Host "Current GDX checkout clean-compiled. Log: $preflightLog"

$results = [System.Collections.Generic.List[object]]::new()
$failed = $false

for ($iteration = 1; $iteration -le $repetitions; $iteration++) {
    foreach ($test in $resolved) {
        $safeName = ($test.Scenario + '-' + $test.Method) -replace '[^A-Za-z0-9_.-]', '_'
        $log = Join-Path $resultRoot ("{0:D2}-{1}.log" -f $iteration, $safeName)
        $selector = $test.Class + '#' + $test.Method
        $started = Get-Date
        Write-Host ("[{0}/{1}] {2} -> {3}" -f $iteration, $repetitions,
            $test.Scenario, $selector)
        $mavenArgs = @(
            '-B', '-o', '-f', $reactor,
            '-pl', 'coronapoker-gdx',
            '-am',
            "-Dmaven.repo.local=$mavenRepo",
            "-Dtest=$selector",
            '-Dsurefire.failIfNoSpecifiedTests=false',
            'test'
        )
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            & $maven @mavenArgs 2>&1 |
                Out-File -LiteralPath $log -Encoding utf8
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
        $elapsed = [math]::Round(((Get-Date) - $started).TotalSeconds, 3)
        $status = if ($exitCode -eq 0) { 'PASS' } else { 'FAIL' }
        $results.Add([pscustomobject]@{
            Iteration = $iteration
            Scenario = $test.Scenario
            Test = $selector
            Status = $status
            Seconds = $elapsed
            Log = $log
        })
        Write-Host ("  {0} ({1}s)" -f $status, $elapsed)
        if ($exitCode -ne 0) {
            $failed = $true
            Get-Content -LiteralPath $log -Tail 80 | Write-Host
            if ($FailFast) { break }
        }
    }
    if ($failed -and $FailFast) { break }
}

$summary = Join-Path $resultRoot 'summary.csv'
$results | Export-Csv -LiteralPath $summary -NoTypeInformation -Encoding utf8
$passed = @($results | Where-Object { $_.Status -eq 'PASS' }).Count
$failures = @($results | Where-Object { $_.Status -eq 'FAIL' }).Count
Write-Host "GDX isolated scenarios: PASS=$passed FAIL=$failures"
Write-Host "Summary: $summary"
if ($failed) { exit 1 }
