param(
    [ValidateRange(1, 9)]
    [int]$Clients = 1,

    [ValidateRange(0, 9)]
    [int]$Bots = 2,

    [ValidateRange(1, 1000)]
    [int]$Hands = 1,

    [long]$Seed = 23059,

    [ValidateSet('normal', 'raise-mix', 'allin-single-board', 'allin-rebuy', 'allin-reconnect', 'abrupt-exit', 'controlled-exit', 'allin-rit', 'rit-network-cut', 'allin-controlled-exit', 'straddle-post', 'straddle-network-cut', 'pause-resume', 'reconnect-midhand', 'reconnect-twice', 'reconnect-every-street', 'reconnect-storm', 'dual-reconnect', 'host-channel-flap', 'reconnect-force-recover', 'transport-chaos', 'lifecycle-chaos', 'dual-abrupt-exit', 'mixed-exit-crash', 'allin-abrupt-exit', 'force-recover', 'double-force-recover', 'crash-rejoin-recover', 'force-recover-add-client', 'force-recover-add-two', 'force-recover-swap-client')]
    [string]$Scenario = 'normal',

    [ValidateSet('hidden', 'minimized', 'visible')]
    [string]$WindowMode = 'hidden',

    [ValidateRange(1, 16)]
    [int]$Screen = 2,

    [switch]$Animations,

    [switch]$ProductionTiming,

    [switch]$DeterministicCrypto,

    [switch]$SkipGameBuild,

    [switch]$Help
)

$ErrorActionPreference = 'Stop'

if ($Help) {
    @'
CoronaPoker real-game loopback E2E simulator

Usage:
  powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\qa\run-real-game-e2e.ps1 [options]

Options:
  -Clients <1..9>          Human client JVMs in addition to the host (default: 1)
  -Bots <0..9>             Production bots hosted by the server (default: 2)
  -Hands <1..1000>         Complete hands to play (default: 1)
  -Seed <long>             Reproducible action/scenario seed (default: 23059)
  -Scenario <name>         Select one scenario listed below (default: normal)
  -WindowMode <mode>       hidden, minimized or visible (default: hidden)
  -Screen <1..16>          Target monitor for every mode (default: 2)
  -Animations              Enable production animations; disabled by default
  -ProductionTiming        Enable normal pauses and real Swing action clocks
  -DeterministicCrypto     Also seed QA-only CSPRNG (diagnostic, not release gate)
  -SkipGameBuild           Reuse the exact checkout already installed by run-certification.ps1
  -Help                    Show this help and exit

Constraints:
  Host + clients + bots cannot exceed 10 seats. Every JVM gets an isolated
  temporary user.home, identity and SQLite database; JUnit removes them after
  the processes stop. Error dialogs are converted into failing log evidence.
  allin-rit requires -Bots 0 and -Hands 1. force-recover requires at least
  -Hands 2: the recovered hand plus a completely new following hand.
  allin-controlled-exit requires -Clients 1, -Bots 0 and -Hands 1.
  rit-network-cut requires exactly -Clients 2 -Bots 0 -Hands 1.
  double-force-recover requires exactly -Hands 4.
  crash-rejoin-recover requires exactly -Clients 1 and -Hands 2.
  force-recover-add-client requires exactly -Clients 2 and -Hands 2.
  force-recover-add-two requires exactly -Clients 3 and -Hands 2.
  force-recover-swap-client requires exactly -Clients 2 and -Hands 2.
  allin-single-board requires exactly -Clients 1 -Bots 0 -Hands 1.
  allin-rebuy requires exactly -Clients 1 -Bots 0 and at least -Hands 5.
  allin-reconnect requires exactly -Clients 2 -Bots 0 -Hands 1.
  straddle-post requires at least -Clients 2 and exactly -Bots 0.
  straddle-network-cut requires exactly -Clients 2 -Bots 0 -Hands 3.
  pause-resume and reconnect-midhand require at least -Hands 2.
  reconnect-twice requires at least -Clients 2 and -Hands 3.
  reconnect-every-street requires at least -Clients 1 and exactly -Hands 4.
  reconnect-storm requires at least -Clients 2 and -Hands 3.
  dual-reconnect requires at least -Clients 3 and -Hands 2.
  host-channel-flap requires at least -Clients 3 and -Hands 2.
  reconnect-force-recover requires exactly -Clients 2 and -Hands 3.
  transport-chaos requires exactly -Clients 3 and -Hands 5.
  lifecycle-chaos requires exactly -Clients 2 and -Hands 7.
  dual-abrupt-exit and mixed-exit-crash require at least -Clients 3.
  allin-abrupt-exit requires exactly -Clients 2 -Bots 0 -Hands 1.

Scenarios:
  normal                  Plays complete hands and requires identical consensus
                          hashes and canonical balances on every JVM.
  raise-mix               Makes human peers exercise real bet/raise buttons and
                          verifies signed cents, consensus and settlement.
  allin-single-board      Forces a heads-up all-in without RIT and verifies the
                          atomic POTCARDS/showdown/settlement path.
  allin-rebuy             Repeats real all-ins for at least five hands and
                          requires a busted seat's rebuy in a later hand.
  allin-reconnect         Drops an all-in peer before showdown, reconnects it and
                          requires complete POTCARDS proof and exact settlement.
  abrupt-exit             Kills one client during preflop; requires MISDEAL,
                          full refund and a live host.
  controlled-exit         Sends the production EXIT testament during preflop;
                          requires normal settlement without MISDEAL.
  allin-rit               Forces every human seat all-in, accepts RIT, unlocks
                          and settles both boards with conserved money.
  rit-network-cut         Cuts a remote voter after its real RIT vote and requires
                          reconnect, SIDE-B unlock and identical settlement.
  allin-controlled-exit   Makes both human seats all-in, then the client sends
                          EXIT with mandatory pocket proof and community keys.
  straddle-post           Enables production straddle with three or more humans,
                          accepts every UTG dialog and verifies signed results.
  straddle-network-cut    Cuts a remote straddler after signed acceptance and
                          before deferred pocket delivery, then completes play.
  pause-resume            Pauses every peer mid-hand, resumes from the host and
                          then completes this and a following hand.
  reconnect-midhand       Drops a live client socket, requires successful secure
                          reconnect and completes this and a following hand.
  reconnect-twice         Drops two different human sockets in consecutive hands;
                          both must reconnect and all peers must remain identical.
  reconnect-every-street  Drops and reconnects a client at preflop, flop, turn and
                          river across four hands to cover every street boundary.
  reconnect-storm         Drops the same freshly reconnected socket again, then a
                          second client's socket in the following hand.
  dual-reconnect          Drops two clients together in one live hand and requires
                          both authenticated reconnects before play continues.
  host-channel-flap       Drops every client channel together while the host stays
                          alive; all peers must reauthenticate and converge.
  reconnect-force-recover Cuts a client channel after force-recovery has started;
                          forbids spurious auto-reconnect and completes recovery.
  transport-chaos         Dual reconnect, immediate relapse, distributed pause,
                          force-recover, fresh hand and a post-recovery reconnect.
  lifecycle-chaos         In one table: reconnect, pause/resume, force-recover,
                          another reconnect, a second force-recover and fresh hands.
  dual-abrupt-exit        Kills two client JVMs together; requires one MISDEAL,
                          exact refund and recovery readiness in every survivor.
  mixed-exit-crash        Combines one valid EXIT testament with another client's
                          hard crash and requires deterministic safe recovery.
  allin-abrupt-exit       Kills an all-in client before showdown proof; requires
                          MISDEAL/refund instead of accepting incomplete evidence.
  force-recover           Stops hand 1 through SERVEREXITRECOVER, rebuilds the
                          lobby/table/sockets, recovers it and settles hand 2.
  double-force-recover    Repeats that full cycle on hands 1 and 3, and also
                          settles fresh hands 2 and 4 without divergence.
  crash-rejoin-recover    Kills the client JVM, relaunches its same home and
                          identity, rejoins recovery and completes a new hand.
  force-recover-add-client Recovers hand 1 with a brand-new passive observer;
                          that peer then joins and settles fresh hand 2.
  force-recover-add-two   Recovers hand 1 while two brand-new observers join;
                          both participate in and verify fresh hand 2.
  force-recover-swap-client An original peer disappears in the recovery lobby
                          and a new peer joins before fresh hand 2.

Examples:
  .\tools\qa\run-real-game-e2e.ps1
  .\tools\qa\run-real-game-e2e.ps1 -Clients 2 -Bots 1 -Hands 3 -Seed 42
  .\tools\qa\run-real-game-e2e.ps1 -Clients 9 -Bots 0 -Hands 1
  .\tools\qa\run-real-game-e2e.ps1 -Clients 4 -Bots 5 -Hands 3
  .\tools\qa\run-real-game-e2e.ps1 -Scenario abrupt-exit
  .\tools\qa\run-real-game-e2e.ps1 -Scenario controlled-exit
  .\tools\qa\run-real-game-e2e.ps1 -Scenario raise-mix -Hands 5
  .\tools\qa\run-real-game-e2e.ps1 -Scenario allin-single-board -Clients 1 -Bots 0
  .\tools\qa\run-real-game-e2e.ps1 -Scenario allin-rebuy -Clients 1 -Bots 0 -Hands 5
  .\tools\qa\run-real-game-e2e.ps1 -Scenario allin-reconnect -Clients 2 -Bots 0 -Hands 1
  .\tools\qa\run-real-game-e2e.ps1 -Scenario allin-rit -Bots 0
  .\tools\qa\run-real-game-e2e.ps1 -Scenario rit-network-cut -Clients 2 -Bots 0 -Hands 1
  .\tools\qa\run-real-game-e2e.ps1 -Scenario allin-controlled-exit -Clients 1 -Bots 0
  .\tools\qa\run-real-game-e2e.ps1 -Scenario straddle-post -Clients 2 -Bots 0 -Hands 3
  .\tools\qa\run-real-game-e2e.ps1 -Scenario straddle-network-cut -Clients 2 -Bots 0 -Hands 3
  .\tools\qa\run-real-game-e2e.ps1 -Scenario pause-resume -Hands 2
  .\tools\qa\run-real-game-e2e.ps1 -Scenario reconnect-midhand -Hands 2
  .\tools\qa\run-real-game-e2e.ps1 -Scenario reconnect-twice -Clients 2 -Hands 3
  .\tools\qa\run-real-game-e2e.ps1 -Scenario reconnect-every-street -Clients 2 -Bots 1 -Hands 4
  .\tools\qa\run-real-game-e2e.ps1 -Scenario reconnect-storm -Clients 2 -Hands 3
  .\tools\qa\run-real-game-e2e.ps1 -Scenario dual-reconnect -Clients 3 -Hands 2
  .\tools\qa\run-real-game-e2e.ps1 -Scenario host-channel-flap -Clients 3 -Hands 2
  .\tools\qa\run-real-game-e2e.ps1 -Scenario reconnect-force-recover -Clients 2 -Bots 1 -Hands 3
  .\tools\qa\run-real-game-e2e.ps1 -Scenario transport-chaos -Clients 3 -Bots 1 -Hands 5
  .\tools\qa\run-real-game-e2e.ps1 -Scenario lifecycle-chaos -Clients 2 -Bots 1 -Hands 7
  .\tools\qa\run-real-game-e2e.ps1 -Scenario dual-abrupt-exit -Clients 3
  .\tools\qa\run-real-game-e2e.ps1 -Scenario mixed-exit-crash -Clients 3
  .\tools\qa\run-real-game-e2e.ps1 -Scenario allin-abrupt-exit -Clients 2 -Bots 0
  .\tools\qa\run-real-game-e2e.ps1 -Scenario force-recover -Hands 2
  .\tools\qa\run-real-game-e2e.ps1 -Scenario double-force-recover -Hands 4
  .\tools\qa\run-real-game-e2e.ps1 -Scenario crash-rejoin-recover -Clients 1 -Hands 2
  .\tools\qa\run-real-game-e2e.ps1 -Scenario force-recover-add-client -Clients 2 -Hands 2
  .\tools\qa\run-real-game-e2e.ps1 -Scenario force-recover-add-two -Clients 3 -Hands 2
  .\tools\qa\run-real-game-e2e.ps1 -Scenario force-recover-swap-client -Clients 2 -Hands 2
  .\tools\qa\run-real-game-e2e.ps1 -WindowMode visible -Screen 2 -Animations
  .\tools\qa\run-real-game-e2e.ps1 -ProductionTiming -WindowMode minimized

This layer launches separate JVMs and runs the production WaitingRoomFrame,
encrypted sockets, Crupier, rondaApuestas, bots, consensus and SQLite close.
'@ | Write-Host
    exit 0
}

if (($Clients + $Bots + 1) -gt 10) {
    throw 'Host + clients + bots cannot exceed 10 seats.'
}
if (($Scenario -eq 'allin-rit') -and ($Bots -ne 0)) {
    throw 'Scenario allin-rit requires -Bots 0 so every surviving seat follows the forced all-in policy.'
}
if (($Scenario -eq 'allin-rit') -and ($Hands -ne 1)) {
    throw 'Scenario allin-rit requires -Hands 1 because a forced full-stack heads-up hand may eliminate a seat.'
}
if (($Scenario -eq 'rit-network-cut') -and (($Clients -ne 2) -or ($Bots -ne 0) -or ($Hands -ne 1))) {
    throw 'Scenario rit-network-cut requires exactly -Clients 2 -Bots 0 -Hands 1.'
}
if (($Scenario -eq 'allin-controlled-exit') -and (($Clients -ne 1) -or ($Bots -ne 0) -or ($Hands -ne 1))) {
    throw 'Scenario allin-controlled-exit requires exactly -Clients 1 -Bots 0 -Hands 1.'
}
if (($Scenario -eq 'force-recover') -and ($Hands -lt 2)) {
    throw 'Scenario force-recover requires at least -Hands 2: recover/finish the interrupted hand and complete a fresh following hand.'
}
if (($Scenario -eq 'double-force-recover') -and ($Hands -ne 4)) {
    throw 'Scenario double-force-recover requires exactly -Hands 4: recover hands 1 and 3 and settle fresh hands 2 and 4.'
}
if (($Scenario -eq 'crash-rejoin-recover') -and (($Clients -ne 1) -or ($Hands -ne 2))) {
    throw 'Scenario crash-rejoin-recover requires exactly -Clients 1 -Hands 2.'
}
if (($Scenario -eq 'force-recover-add-client') -and (($Clients -ne 2) -or ($Hands -ne 2))) {
    throw 'Scenario force-recover-add-client requires exactly -Clients 2 -Hands 2.'
}
if (($Scenario -eq 'force-recover-add-two') -and (($Clients -ne 3) -or ($Hands -ne 2))) {
    throw 'Scenario force-recover-add-two requires exactly -Clients 3 -Hands 2.'
}
if (($Scenario -eq 'force-recover-swap-client') -and (($Clients -ne 2) -or ($Hands -ne 2))) {
    throw 'Scenario force-recover-swap-client requires exactly -Clients 2 -Hands 2.'
}
if (($Scenario -eq 'allin-single-board') -and (($Clients -ne 1) -or ($Bots -ne 0) -or ($Hands -ne 1))) {
    throw 'Scenario allin-single-board requires exactly -Clients 1 -Bots 0 -Hands 1.'
}
if (($Scenario -eq 'allin-rebuy') -and (($Clients -ne 1) -or ($Bots -ne 0) -or ($Hands -lt 5))) {
    throw 'Scenario allin-rebuy requires exactly -Clients 1 -Bots 0 and at least -Hands 5.'
}
if (($Scenario -eq 'allin-reconnect') -and (($Clients -ne 2) -or ($Bots -ne 0) -or ($Hands -ne 1))) {
    throw 'Scenario allin-reconnect requires exactly -Clients 2 -Bots 0 -Hands 1.'
}
if (($Scenario -eq 'straddle-post') -and (($Clients -lt 2) -or ($Bots -ne 0))) {
    throw 'Scenario straddle-post requires at least -Clients 2 and exactly -Bots 0.'
}
if (($Scenario -eq 'straddle-network-cut') -and (($Clients -ne 2) -or ($Bots -ne 0) -or ($Hands -ne 3))) {
    throw 'Scenario straddle-network-cut requires exactly -Clients 2 -Bots 0 -Hands 3.'
}
if (($Scenario -eq 'pause-resume') -and ($Hands -lt 2)) {
    throw 'Scenario pause-resume requires at least -Hands 2.'
}
if (($Scenario -eq 'reconnect-midhand') -and (($Clients -lt 1) -or ($Hands -lt 2))) {
    throw 'Scenario reconnect-midhand requires at least -Clients 1 -Hands 2.'
}
if (($Scenario -eq 'reconnect-twice') -and (($Clients -lt 2) -or ($Hands -lt 3))) {
    throw 'Scenario reconnect-twice requires at least -Clients 2 -Hands 3.'
}
if (($Scenario -eq 'reconnect-every-street') -and (($Clients -lt 1) -or ($Hands -ne 4))) {
    throw 'Scenario reconnect-every-street requires at least -Clients 1 and exactly -Hands 4.'
}
if (($Scenario -eq 'reconnect-storm') -and (($Clients -lt 2) -or ($Hands -lt 3))) {
    throw 'Scenario reconnect-storm requires at least -Clients 2 -Hands 3.'
}
if (($Scenario -eq 'dual-reconnect') -and (($Clients -lt 3) -or ($Hands -lt 2))) {
    throw 'Scenario dual-reconnect requires at least -Clients 3 -Hands 2.'
}
if (($Scenario -eq 'host-channel-flap') -and (($Clients -lt 3) -or ($Hands -lt 2))) {
    throw 'Scenario host-channel-flap requires at least -Clients 3 -Hands 2.'
}
if (($Scenario -eq 'reconnect-force-recover') -and (($Clients -ne 2) -or ($Hands -ne 3))) {
    throw 'Scenario reconnect-force-recover requires exactly -Clients 2 -Hands 3.'
}
if (($Scenario -eq 'transport-chaos') -and (($Clients -ne 3) -or ($Hands -ne 5))) {
    throw 'Scenario transport-chaos requires exactly -Clients 3 -Hands 5.'
}
if (($Scenario -eq 'lifecycle-chaos') -and (($Clients -ne 2) -or ($Hands -ne 7))) {
    throw 'Scenario lifecycle-chaos requires exactly -Clients 2 -Hands 7.'
}
if ((($Scenario -eq 'dual-abrupt-exit') -or ($Scenario -eq 'mixed-exit-crash')) -and ($Clients -lt 3)) {
    throw 'Compound exit scenarios require at least -Clients 3.'
}
if (($Scenario -eq 'allin-abrupt-exit') -and (($Clients -ne 2) -or ($Bots -ne 0) -or ($Hands -ne 1))) {
    throw 'Scenario allin-abrupt-exit requires exactly -Clients 2 -Bots 0 -Hands 1.'
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
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

$mavenRepo = Join-Path $repoRoot '.m2\repository'
$qaRunId = '{0}-{1}' -f (Get-Date -Format 'yyyyMMdd-HHmmss-fff'), $PID
$qaUserHome = Join-Path $repoRoot "tools\qa\target\qa-home\run-$qaRunId"
$animationsEnabled = if ($Animations) { 'true' } else { 'false' }
$testModeEnabled = if ($ProductionTiming) { 'false' } else { 'true' }
$deterministicCryptoEnabled = if ($DeterministicCrypto) { 'true' } else { 'false' }
$mavenPom = if ($SkipGameBuild) {
    Join-Path $repoRoot 'tools\qa\pom.xml'
} else {
    Join-Path $repoRoot 'tools\reactor\pom.xml'
}
$mavenGoal = if ($SkipGameBuild) { 'test' } else { 'install' }
$qaHomeRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'tools\qa\target\qa-home'))
$qaHomePath = [IO.Path]::GetFullPath($qaUserHome)
if (-not $qaHomePath.StartsWith($qaHomeRoot + [IO.Path]::DirectorySeparatorChar,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing unsafe QA home path: $qaHomePath"
}
$exitCode = 1
try {
    & $maven `
        -f $mavenPom `
        "-Dmaven.repo.local=$mavenRepo" `
        "-Dqa.user.home=$qaUserHome" `
        $mavenGoal `
        -P qa-real-game-e2e `
        "-Dqa.e2e.clients=$Clients" `
        "-Dqa.e2e.bots=$Bots" `
        "-Dqa.e2e.hands=$Hands" `
        "-Dqa.e2e.seed=$Seed" `
        "-Dqa.e2e.scenario=$Scenario" `
        "-Dqa.e2e.windowMode=$WindowMode" `
        "-Dqa.e2e.screen=$Screen" `
        "-Dqa.e2e.animations=$animationsEnabled" `
        "-Dqa.e2e.testMode=$testModeEnabled" `
        "-Dqa.e2e.deterministicCrypto=$deterministicCryptoEnabled"
    $exitCode = $LASTEXITCODE
} finally {
    if (Test-Path -LiteralPath $qaHomePath) {
        Remove-Item -LiteralPath $qaHomePath -Recurse -Force
    }
}
exit $exitCode
