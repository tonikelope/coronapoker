[CmdletBinding()]
param(
    [string] $JarPath = "",
    [ValidateSet("exe", "app-image")]
    [string] $PackageType = "exe",
    [string] $Destination = "target",
    [string] $JdkHome = "",
    [string] $InnoSetupHome = "",
    [string] $SigningCertificateThumbprint = "",
    [string] $TimestampUrl = "http://timestamp.digicert.com",
    [switch] $KeepStaging
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$targetRoot = [IO.Path]::GetFullPath((Join-Path $repositoryRoot "target"))
$stagingRoot = [IO.Path]::GetFullPath((Join-Path $targetRoot "windows-installer-staging"))

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)] [string] $Tool,
        [Parameter(Mandatory = $true)] [string[]] $Arguments,
        [Parameter(Mandatory = $true)] [string] $Activity
    )

    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Activity failed with exit code $LASTEXITCODE."
    }
}

function Assert-Tool {
    param([Parameter(Mandatory = $true)] [string] $Name)
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        throw "Required tool '$Name' was not found on PATH."
    }
    return $command.Source
}

function Resolve-JdkTool {
    param([Parameter(Mandatory = $true)] [string] $Name)
    if (-not [string]::IsNullOrWhiteSpace($JdkHome)) {
        $tool = Join-Path $JdkHome "bin\$Name.exe"
        if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
            throw "Required JDK tool was not found: $tool"
        }
        return [IO.Path]::GetFullPath($tool)
    }
    return Assert-Tool "$Name.exe"
}

function Resolve-InnoCompiler {
    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($InnoSetupHome)) {
        $candidates += Join-Path $InnoSetupHome "ISCC.exe"
    }
    $command = Get-Command "ISCC.exe" -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        $candidates += $command.Source
    }
    if (-not [string]::IsNullOrWhiteSpace(${env:ProgramFiles(x86)})) {
        $candidates += Join-Path ${env:ProgramFiles(x86)} `
                "Inno Setup 6\ISCC.exe"
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ProgramFiles)) {
        $candidates += Join-Path $env:ProgramFiles `
                "Inno Setup 7\ISCC.exe"
    }
    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $candidates += Join-Path $env:LOCALAPPDATA `
                "Programs\Inno Setup 6\ISCC.exe"
        $candidates += Join-Path $env:LOCALAPPDATA `
                "Programs\Inno Setup 7\ISCC.exe"
    }
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return [IO.Path]::GetFullPath($candidate)
        }
    }
    throw "Inno Setup compiler ISCC.exe was not found. Pass -InnoSetupHome pointing to Inno Setup 6.7 or newer."
}

function Invoke-Sign {
    param([Parameter(Mandatory = $true)] [string] $File)
    if ([string]::IsNullOrWhiteSpace($SigningCertificateThumbprint)) {
        return
    }
    $signtool = Assert-Tool "signtool.exe"
    Invoke-Checked $signtool @(
        "sign", "/sha1", $SigningCertificateThumbprint,
        "/fd", "SHA256", "/tr", $TimestampUrl, "/td", "SHA256", $File
    ) "Authenticode signing of $File"
}

if ($env:OS -ne "Windows_NT") {
    throw "The CoronaPoker Windows package must be built on Windows."
}

$pom = Get-Content -LiteralPath (Join-Path $repositoryRoot "pom.xml") -Raw
$versionMatch = [regex]::Match($pom,
        '<version>\s*([0-9]+\.[0-9]+)\s*</version>')
if (-not $versionMatch.Success) {
    throw "Cannot read the CoronaPoker version from pom.xml."
}
$version = $versionMatch.Groups[1].Value

if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $JarPath = Join-Path $targetRoot "CoronaPoker_$version.jar"
} elseif (-not [IO.Path]::IsPathRooted($JarPath)) {
    $JarPath = Join-Path $repositoryRoot $JarPath
}
$JarPath = [IO.Path]::GetFullPath($JarPath)
if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
    throw "Product JAR not found: $JarPath"
}
if ([IO.Path]::GetFileName($JarPath) -ne "CoronaPoker_$version.jar") {
    throw "Expected CoronaPoker_$version.jar, got $([IO.Path]::GetFileName($JarPath))."
}

if (-not [IO.Path]::IsPathRooted($Destination)) {
    $Destination = Join-Path $repositoryRoot $Destination
}
$Destination = [IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Path $Destination -Force | Out-Null

$jpackage = Resolve-JdkTool "jpackage"
$jlink = Resolve-JdkTool "jlink"
$jdeps = Resolve-JdkTool "jdeps"
$java = Resolve-JdkTool "java"
$jdkProperties = @(& $java -XshowSettings:properties -version 2>&1)
if ($LASTEXITCODE -ne 0 -or -not ($jdkProperties -match
        '^\s*java\.specification\.version\s*=\s*25\s*$')) {
    throw "The Windows installer must be built with JDK 25."
}
$resolvedJdkHome = Split-Path -Parent (Split-Path -Parent $jlink)
$jmods = Join-Path $resolvedJdkHome "jmods"

if (-not [string]::Equals((Split-Path -Parent $stagingRoot), $targetRoot,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean staging outside the repository target directory."
}
if (Test-Path -LiteralPath $stagingRoot) {
    Remove-Item -LiteralPath $stagingRoot -Recurse -Force
}

$inputRoot = Join-Path $stagingRoot "input"
$runtimeRoot = Join-Path $stagingRoot "runtime"
$imageOutput = Join-Path $stagingRoot "image"
New-Item -ItemType Directory -Path $inputRoot, $imageOutput |
        Out-Null
Copy-Item -LiteralPath $JarPath -Destination (Join-Path $inputRoot "CoronaPoker.jar")

$jdepsArguments = @(
    "--ignore-missing-deps", "--multi-release", "17",
    "--print-module-deps", $JarPath
)
$jdepsOutput = @(& $jdeps @jdepsArguments 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "jdeps could not calculate the private runtime modules:`n$($jdepsOutput -join [Environment]::NewLine)"
}
$dependencyLine = $jdepsOutput | Where-Object {
    $_ -match '^[a-z0-9_.]+(?:,[a-z0-9_.]+)*$'
} | Select-Object -Last 1
if ([string]::IsNullOrWhiteSpace($dependencyLine)) {
    throw "jdeps returned no Java module set."
}

# The updater is downloaded separately, so its Swing/logging requirements are
# deliberately added even if the game JAR does not make all of them visible to
# jdeps. Native launchers must remain in the image: coronaupdater.jar launches
# through <private runtime>/bin/java.exe and then relaunches CoronaPoker.jar.
$requiredModules = @(
    "java.desktop", "java.logging", "java.management", "java.naming",
    "java.net.http", "java.prefs", "java.sql", "jdk.charsets",
    "jdk.crypto.ec", "jdk.localedata", "jdk.unsupported", "jdk.zipfs"
)
$modules = ((@($dependencyLine -split ',') + $requiredModules) |
        Sort-Object -Unique) -join ','

$jlinkArguments = @()
if (Test-Path -LiteralPath $jmods -PathType Container) {
    $jlinkArguments += @("--module-path", $jmods)
}
$jlinkArguments += @(
    "--add-modules", $modules,
    "--include-locales=en,es",
    "--compress=zip-9",
    "--strip-debug",
    "--no-header-files",
    "--no-man-pages",
    "--output", $runtimeRoot
)
Invoke-Checked $jlink $jlinkArguments "Private Java runtime creation"

foreach ($launcher in @("java.exe", "javaw.exe")) {
    $launcherPath = Join-Path $runtimeRoot "bin\$launcher"
    if (-not (Test-Path -LiteralPath $launcherPath -PathType Leaf)) {
        throw "Updater-compatible runtime is missing $launcherPath."
    }
}

$icon = Join-Path $repositoryRoot "packaging\windows\CoronaPoker.ico"
$assetBuilder = Join-Path $repositoryRoot `
        "tools\windows\build-installer-assets.ps1"
& $assetBuilder | Out-Host
$wizardImage = Join-Path $repositoryRoot `
        "packaging\windows\installer-wizard.png"
$smallImage = Join-Path $repositoryRoot `
        "packaging\windows\installer-small.png"
foreach ($installerAsset in @($icon, $wizardImage, $smallImage)) {
    if (-not (Test-Path -LiteralPath $installerAsset -PathType Leaf)) {
        throw "Missing Windows installer asset: $installerAsset"
    }
}
$description = "CoronaPoker - multiplayer Texas Hold'em"
$appImageArgs = @(
    "--type", "app-image",
    "--dest", $imageOutput,
    "--input", $inputRoot,
    "--name", "CoronaPoker",
    "--main-jar", "CoronaPoker.jar",
    "--main-class", "com.tonikelope.coronapoker.gdx.GdxLauncher",
    "--runtime-image", $runtimeRoot,
    "--app-version", $version,
    "--vendor", "tonikelope",
    "--description", $description,
    "--copyright", "Copyright (C) tonikelope",
    "--icon", $icon,
    "--java-options", "-Dfile.encoding=UTF-8",
    "--java-options", "--enable-native-access=ALL-UNNAMED"
)
Invoke-Checked $jpackage $appImageArgs "Native application image creation"

$appImage = Join-Path $imageOutput "CoronaPoker"
$nativeLauncher = Join-Path $appImage "CoronaPoker.exe"
$installedJar = Join-Path $appImage "app\CoronaPoker.jar"
$installedConfig = Join-Path $appImage "app\CoronaPoker.cfg"
$installedJava = Join-Path $appImage "runtime\bin\java.exe"
foreach ($requiredFile in @($nativeLauncher, $installedJar,
        $installedConfig, $installedJava)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Incomplete application image; missing $requiredFile."
    }
}
if ((Get-FileHash -LiteralPath $JarPath -Algorithm SHA256).Hash -ne
        (Get-FileHash -LiteralPath $installedJar -Algorithm SHA256).Hash) {
    throw "The installed CoronaPoker.jar differs from the canonical product JAR."
}
if ((Get-Content -LiteralPath $installedConfig -Raw) -match 'user\.home') {
    throw "The native launcher must preserve %USERPROFILE%\.coronapoker."
}
$runtimeModules = @(& $installedJava --list-modules | ForEach-Object {
        ($_ -split '@')[0]
    })
foreach ($updaterModule in @("java.base", "java.desktop", "java.logging")) {
    if ($updaterModule -notin $runtimeModules) {
        throw "Updater-compatible runtime is missing module $updaterModule."
    }
}
Invoke-Sign $nativeLauncher

$architecture = switch ($env:PROCESSOR_ARCHITECTURE.ToUpperInvariant()) {
    "AMD64" { "x64" }
    "ARM64" { "arm64" }
    default { $env:PROCESSOR_ARCHITECTURE.ToLowerInvariant() }
}
$appImageArtifactBase = "CoronaPoker_${version}_Windows_${architecture}"
$setupArtifactBase = "${appImageArtifactBase}_Setup"
$succeeded = $false
try {
    if ($PackageType -eq "app-image") {
        $finalPath = Join-Path $Destination $appImageArtifactBase
        if (Test-Path -LiteralPath $finalPath) {
            throw "Destination already exists: $finalPath"
        }
        Move-Item -LiteralPath $appImage -Destination $finalPath
        $succeeded = $true
        [pscustomobject]@{
            Type = "app-image"
            Version = $version
            Path = $finalPath
            Launcher = Join-Path $finalPath "CoronaPoker.exe"
            UpdatedJar = Join-Path $finalPath "app\CoronaPoker.jar"
            PrivateJava = Join-Path $finalPath "runtime\bin\java.exe"
        }
        return
    }

    $innoCompiler = Resolve-InnoCompiler
    $innoScript = Join-Path $repositoryRoot `
            "packaging\windows\CoronaPoker.iss"
    Invoke-Checked $innoCompiler @(
        "/DAppVersion=$version",
        "/DAppImage=$appImage",
        "/DOutputDir=$Destination",
        "/DOutputBase=$setupArtifactBase",
        "/DAppArchitecture=$architecture",
        $innoScript
    ) "Branded Windows installer creation"

    $finalPath = Join-Path $Destination "$setupArtifactBase.exe"
    if (-not (Test-Path -LiteralPath $finalPath -PathType Leaf)) {
        throw "Inno Setup did not create the expected installer: $finalPath"
    }
    Invoke-Sign $finalPath
    $hash = (Get-FileHash -LiteralPath $finalPath -Algorithm SHA256).Hash
    $succeeded = $true
    [pscustomobject]@{
        Type = "exe"
        Version = $version
        Path = $finalPath
        Size = (Get-Item -LiteralPath $finalPath).Length
        Sha256 = $hash
        PerUser = $true
        PrivateRuntime = $true
        UpdaterCompatible = $true
        Languages = "Spanish (automatic), English"
        JavaRuntime = (& $installedJava -version 2>&1 | Select-Object -First 1)
        Signed = -not [string]::IsNullOrWhiteSpace(
                $SigningCertificateThumbprint)
    }
} finally {
    if ($succeeded -and -not $KeepStaging -and (Test-Path -LiteralPath $stagingRoot)) {
        Remove-Item -LiteralPath $stagingRoot -Recurse -Force
    }
}
