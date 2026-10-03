[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('build', 'test', 'extended', 'scenarios', 'all', 'list', 'help')]
    [string] $Action = 'test',

    [Parameter(Position = 1)]
    [ValidateSet('quick', 'fast', 'balanced', 'stress')]
    [string] $Mode = 'balanced',

    [string] $Scenario = 'all',

    [long] $Seed,

    [switch] $IncludeBots,

    [switch] $VerboseScenarios,

    [switch] $Help
)

$ErrorActionPreference = 'Stop'

if ($Help) {
    @'
CoronaPoker local build, tests and GDX scenarios

Usage:
  .\qa.cmd build
  .\qa.cmd test
  .\qa.cmd extended
  .\qa.cmd scenarios fast
  .\qa.cmd all balanced
  .\qa.cmd scenarios fast -Scenario spectator-rebuy-cycle -Seed 42
  .\qa.cmd list

Commands:
  build      Clean package only. Skip tests and leave the runnable JAR
  test       Default. Clean build + product module and architecture tests
  extended   Run test, then the slow replayable non-bot QA lane
  scenarios  Run only the unified GDX scenario catalogue
  all        Run extended, then scenarios (complete release command)
  list       Print the executable GDX scenario catalogue
  help       Show this help

Scenario depth (only scenario repetitions/depth change):
  quick      Critical subset, one pass, 5-hand normal soak
  fast       Complete catalogue, one pass, 5-hand normal soak
  balanced   Complete catalogue, two passes, 20-hand normal soak
  stress     Complete catalogue, five passes, 50-hand normal soak

Options:
  -Scenario <name>       Run only one GDX scenario and its mapped tests
  -Seed <long>           Replay a scenario base seed
  -IncludeBots           Add statistical bot QA to extended or all
  -VerboseScenarios      Stream scenario Maven output (logs are always saved)
  -Help                  Show this help

The command stops on the first failed stage and preserves its exit code.
The runnable JAR is left in target\. Scenario evidence is stored below
target\certification\ and the overall run summary below target\qa\.

Requires Windows PowerShell, JDK 17+ and Maven 3+. The tool detects and prints
the selected Maven/JDK. It does not install them. A first run also needs access
to the configured Maven repositories unless the checkout-local cache is full.
'@ | Write-Host
    exit 0
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$certifier = Join-Path $PSScriptRoot 'run-certification.ps1'
$mavenRepo = Join-Path $repoRoot '.m2\repository'
$rootPom = Join-Path $repoRoot 'pom.xml'
$qaPom = Join-Path $repoRoot 'tools\qa\pom.xml'
$seedWasProvided = $PSBoundParameters.ContainsKey('Seed')
$modeWasProvided = $PSBoundParameters.ContainsKey('Mode')
$runsTests = $Action -in @('test', 'extended', 'all')
$runsExtended = $Action -in @('extended', 'all')
$runsScenarios = $Action -in @('scenarios', 'all')

if ($Action -eq 'help') {
    & $MyInvocation.MyCommand.Path -Help
    exit $LASTEXITCODE
}
if ($Action -eq 'list') {
    if ($modeWasProvided) {
        throw 'list does not accept a scenario mode. It always shows the full catalogue.'
    }
    & $certifier -ListOnly
    exit $LASTEXITCODE
}
if (-not $runsScenarios -and $modeWasProvided) {
    throw 'A scenario mode requires scenarios or all.'
}
if ($Action -eq 'build' -and ($IncludeBots -or $Scenario -ne 'all' -or
        $seedWasProvided -or $VerboseScenarios)) {
    throw 'Build cannot be combined with test or scenario options.'
}
if (-not $runsScenarios -and ($Scenario -ne 'all' -or $seedWasProvided -or
        $VerboseScenarios)) {
    throw '-Scenario, -Seed and -VerboseScenarios require scenarios or all.'
}
if (-not $runsExtended -and $IncludeBots) {
    throw '-IncludeBots requires extended or all.'
}
if ($runsScenarios -and -not $seedWasProvided) {
    . (Join-Path $PSScriptRoot 'qa-seed.ps1')
    $Seed = New-CoronaPokerQaSeed
}

$maven = $null
$wrapper = Join-Path $repoRoot 'mvnw.cmd'
if (Test-Path -LiteralPath $wrapper) {
    $maven = $wrapper
}
foreach ($candidate in @('mvn.cmd', 'mvn')) {
    if ($null -eq $maven) {
        $command = Get-Command $candidate -ErrorAction SilentlyContinue
        if ($null -ne $command) {
            $maven = $command.Source
        }
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

$mavenVersionOutput = @(& $maven -version 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "Maven could not start:`n$($mavenVersionOutput -join [Environment]::NewLine)"
}
$mavenVersionLine = $mavenVersionOutput |
    Where-Object { $_ -match '^Apache Maven\s+(\d+)\.' } |
    Select-Object -First 1
$javaVersionLine = $mavenVersionOutput |
    Where-Object { $_ -match '^Java version:\s+(.+)$' } |
    Select-Object -First 1
if ($null -eq $mavenVersionLine -or $null -eq $javaVersionLine) {
    throw "Cannot determine Maven/JDK versions:`n$($mavenVersionOutput -join [Environment]::NewLine)"
}
$mavenMajor = [int]([regex]::Match($mavenVersionLine, '^Apache Maven\s+(\d+)\.').Groups[1].Value)
$javaRaw = [regex]::Match($javaVersionLine, '^Java version:\s+([^,\s]+)').Groups[1].Value
$javaMajorMatch = [regex]::Match($javaRaw, '^(?:1\.)?(\d+)')
if (-not $javaMajorMatch.Success) {
    throw "Cannot parse JDK version from: $javaVersionLine"
}
$javaMajor = [int]$javaMajorMatch.Groups[1].Value
if ($mavenMajor -lt 3) {
    throw "Apache Maven 3 or newer is required. Detected: $mavenVersionLine"
}
if ($javaMajor -lt 17) {
    throw "JDK 17 or newer is required. Detected: $javaVersionLine"
}
Write-Host "Environment: $mavenVersionLine | $javaVersionLine"

$results = [System.Collections.Generic.List[object]]::new()
$runStarted = Get-Date
$reportDir = $null

function Write-RunSummary {
    if ($null -eq $script:reportDir) {
        $stamp = $script:runStarted.ToString('yyyyMMdd-HHmmss')
        $suffix = if ($script:runsScenarios) {
            "$($script:Action)-$($script:Mode)"
        } else {
            $script:Action
        }
        $script:reportDir = Join-Path $script:repoRoot "target\qa\$stamp-$suffix"
    }
    New-Item -ItemType Directory -Path $script:reportDir -Force | Out-Null
    $summary = [pscustomobject]@{
        Action = $script:Action
        Mode = if ($script:runsScenarios) { $script:Mode } else { $null }
        Scenario = $script:Scenario
        Seed = if ($script:runsScenarios) {
            $script:Seed
        } else {
            $null
        }
        Started = $script:runStarted.ToString('o')
        Finished = (Get-Date).ToString('o')
        Result = if (@($script:results | Where-Object Result -eq 'FAIL').Count) {
            'FAIL'
        } else {
            'PASS'
        }
        Stages = @($script:results)
    }
    $json = Join-Path $script:reportDir 'summary.json'
    $text = Join-Path $script:reportDir 'summary.txt'
    $summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $json -Encoding utf8
    @(
        "CoronaPoker QA $($summary.Result)"
        "Action: $($summary.Action)"
        "Scenario mode: $($summary.Mode)"
        "Scenario: $($summary.Scenario)"
        "Started: $($summary.Started)"
        "Finished: $($summary.Finished)"
        ''
        $summary.Stages | ForEach-Object {
            '{0}: {1} ({2} s)' -f $_.Name, $_.Result, $_.Seconds
        }
    ) | Set-Content -LiteralPath $text -Encoding utf8
    Write-Host "QA summary: $text"
    Write-Host "Machine-readable summary: $json"
}

function Invoke-QaStage {
    param(
        [Parameter(Mandatory = $true)] [string] $Name,
        [Parameter(Mandatory = $true)] [scriptblock] $Action
    )

    Write-Host "`n=== $Name ===" -ForegroundColor Cyan
    $timer = [System.Diagnostics.Stopwatch]::StartNew()
    & $Action
    $exitCode = $LASTEXITCODE
    $timer.Stop()
    $result = if ($exitCode -eq 0) { 'PASS' } else { 'FAIL' }
    $script:results.Add([pscustomobject]@{
            Name = $Name
            Result = $result
            ExitCode = $exitCode
            Seconds = [math]::Round($timer.Elapsed.TotalSeconds, 3)
        })
    Write-Host ("{0}: {1}" -f $Name, $result) -ForegroundColor $(
        if ($exitCode -eq 0) { 'Green' } else { 'Red' })
    if ($exitCode -ne 0) {
        Write-RunSummary
        exit $exitCode
    }
}

if ($Action -eq 'build') {
    $buildArgs = @(
        '-B', '-f', $rootPom,
        "-Dmaven.repo.local=$($mavenRepo.Replace('\', '/'))",
        '-DskipTests', 'clean', 'package'
    )
    Invoke-QaStage 'Product build (tests skipped)' {
        & $maven @buildArgs
    }
    Write-RunSummary
    Write-Host 'CORONAPOKER BUILD PASS' -ForegroundColor Green
    exit 0
}

if ($runsTests) {
    $productArgs = @(
        '-B', '-f', $rootPom,
        "-Dmaven.repo.local=$($mavenRepo.Replace('\', '/'))",
        'clean', 'install'
    )
    Invoke-QaStage 'Product build and tests' {
        & $maven @productArgs
    }
}

if ($runsExtended) {
    $extendedArgs = @(
        '-B', '-f', $qaPom,
        "-Dmaven.repo.local=$($mavenRepo.Replace('\', '/'))",
        'test', '-Pqa-all'
    )
    Invoke-QaStage 'Additional replayable tests' {
        & $maven @extendedArgs
    }
    if ($IncludeBots) {
        $botArgs = @(
            '-B', '-f', $qaPom,
            "-Dmaven.repo.local=$($mavenRepo.Replace('\', '/'))",
            'test', '-Pqa-bots'
        )
        Invoke-QaStage 'Statistical bot-quality QA' {
            & $maven @botArgs
        }
    }
}

if ($runsScenarios) {
    $certificationParams = @{
        Mode = $Mode
        Scenario = $Scenario
        Seed = $Seed
    }
    if ($VerboseScenarios) {
        $certificationParams.VerboseOutput = $true
    }
    Invoke-QaStage "GDX scenarios ($Mode)" {
        & $certifier @certificationParams
    }
}

Write-RunSummary
Write-Host 'CORONAPOKER COMPLETE QA PASS' -ForegroundColor Green
exit 0
