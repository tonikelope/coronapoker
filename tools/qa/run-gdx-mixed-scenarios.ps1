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
CoronaPoker isolated mixed Swing/GDX scenario suite

Usage:
  .\tools\qa\gdx-mixed-scenarios.cmd [-Mode fast|balanced|stress]
  .\tools\qa\gdx-mixed-scenarios.cmd -Scenario gdxHostServesSwingAndGdxHumanClients
  .\tools\qa\gdx-mixed-scenarios.cmd -StartAt gdxHostAndSwingClientCompleteRunItTwiceOnBothBoards
  .\tools\qa\gdx-mixed-scenarios.cmd -ListOnly

Every mixed-front-end test runs in a fresh Maven/JVM process. FAST runs every
test once, BALANCED twice and STRESS five times. Logs and the machine-readable
summary are written below target\gdx-mixed-scenarios. Before launching any
scenario, the runner removes only generated production-class directories and
installs the current checkout once into the checkout-local Maven repository, so
isolated JVMs cannot exercise a stale CoronaPoker jar or deleted source class.
'@ | Write-Host
    exit 0
}

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$sourcePath = Join-Path $root ('tools\qa\src\test\java\com\tonikelope\' +
    'coronapoker\e2e\MixedFrontendNetworkE2EIT.java')
$pom = Join-Path $root 'tools\qa\pom.xml'
$className = 'com.tonikelope.coronapoker.e2e.MixedFrontendNetworkE2EIT'

if (-not (Test-Path -LiteralPath $sourcePath)) {
    throw "Mixed GDX scenario source not found: $sourcePath"
}

$source = Get-Content -LiteralPath $sourcePath -Raw
$methodPattern = [regex]::new(
    '@Test\s+(?:@[^\r\n]+\s+)*\s*void\s+([A-Za-z0-9_]+)\s*\(',
    [System.Text.RegularExpressions.RegexOptions]::Singleline)
$tests = @($methodPattern.Matches($source) | ForEach-Object {
    [pscustomobject]@{
        Scenario = $_.Groups[1].Value
        Class = $className
        Method = $_.Groups[1].Value
    }
})
if ($tests.Count -eq 0) {
    throw 'No mixed Swing/GDX scenarios were discovered'
}

if ($Scenario -ne 'all') {
    $tests = @($tests | Where-Object { $_.Scenario -eq $Scenario })
    if ($tests.Count -eq 0) {
        throw "Unknown mixed GDX scenario '$Scenario'. Use -ListOnly to inspect the catalogue."
    }
}
if ($StartAt -and $Scenario -ne 'all') {
    throw '-StartAt can only be used while running the complete catalogue'
}
if ($StartAt) {
    $startIndex = -1
    for ($index = 0; $index -lt $tests.Count; $index++) {
        if ($tests[$index].Scenario -eq $StartAt) {
            $startIndex = $index
            break
        }
    }
    if ($startIndex -lt 0) {
        throw "Unknown mixed GDX start scenario '$StartAt'. Use -ListOnly to inspect the catalogue."
    }
    $tests = @($tests | Select-Object -Skip $startIndex)
}

if ($ListOnly) {
    $tests | ForEach-Object {
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
$resultRoot = Join-Path $root "target\gdx-mixed-scenarios\$stamp-$Mode"
New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
$mavenRepo = Join-Path $root '.m2\repository'
$gamePom = Join-Path $root 'pom.xml'
$buildLog = Join-Path $resultRoot '00-current-checkout-install.log'

Write-Host 'Building the current CoronaPoker checkout before isolated scenarios...'
foreach ($relativeOutput in @(
        'target\classes',
        'target\generated-sources',
        'target\maven-status')) {
    $outputPath = [IO.Path]::GetFullPath((Join-Path $root $relativeOutput))
    $rootTarget = [IO.Path]::GetFullPath((Join-Path $root 'target'))
    if (-not $outputPath.StartsWith(
            $rootTarget + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing unsafe generated-output path: $outputPath"
    }
    if (Test-Path -LiteralPath $outputPath) {
        Remove-Item -LiteralPath $outputPath -Recurse -Force
    }
}
$buildArgs = @(
    '-B', '-o', '-f', $gamePom,
    "-Dmaven.repo.local=$mavenRepo",
    '-DskipTests',
    'install'
)
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    & $maven @buildArgs 2>&1 |
        Out-File -LiteralPath $buildLog -Encoding utf8
    $buildExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorActionPreference
}
if ($buildExitCode -ne 0) {
    Get-Content -LiteralPath $buildLog -Tail 100 | Write-Host
    throw "Current checkout build failed; no scenario was run. Log: $buildLog"
}
Write-Host "Current checkout installed. Log: $buildLog"

$results = [System.Collections.Generic.List[object]]::new()
$failed = $false

for ($iteration = 1; $iteration -le $repetitions; $iteration++) {
    foreach ($test in $tests) {
        $log = Join-Path $resultRoot ("{0:D2}-{1}.log" -f $iteration,
            $test.Scenario)
        $selector = $test.Class + '#' + $test.Method
        $started = Get-Date
        Write-Host ("[{0}/{1}] {2}" -f $iteration, $repetitions, $selector)
        $mavenArgs = @(
            '-B', '-o', '-f', $pom,
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
Write-Host "Mixed Swing/GDX isolated scenarios: PASS=$passed FAIL=$failures"
Write-Host "Summary: $summary"
if ($failed) { exit 1 }
