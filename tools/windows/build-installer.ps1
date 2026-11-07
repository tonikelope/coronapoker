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
$resolvedJdkHome = Split-Path -Parent (Split-Path -Parent $jpackage)
$java = Join-Path $resolvedJdkHome "bin\java.exe"
$savedErrorActionPreference = $ErrorActionPreference
try {
    # Windows PowerShell reports native stderr as ErrorRecord objects when
    # ErrorActionPreference is Stop. java -XshowSettings intentionally writes
    # its successful diagnostic output there, so capture it without turning it
    # into a terminating PowerShell error.
    $ErrorActionPreference = "Continue"
    $jdkProperties = @(& $java -XshowSettings:properties -version 2>&1 |
            ForEach-Object { $_.ToString() })
    $javaExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $savedErrorActionPreference
}
if ($javaExitCode -ne 0 -or -not ($jdkProperties -match
        '^\s*java\.specification\.version\s*=\s*25\s*$')) {
    throw "The Windows installer must be built with JDK 25."
}
$jdkModules = @(Invoke-Checked $java @("--list-modules") "Source JDK module inventory")
if ($jdkModules.Count -eq 0) {
    throw "The source JDK reported no modules."
}

if (-not [string]::Equals((Split-Path -Parent $stagingRoot), $targetRoot,
        [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean staging outside the repository target directory."
}
if (Test-Path -LiteralPath $stagingRoot) {
    Remove-Item -LiteralPath $stagingRoot -Recurse -Force
}

$inputRoot = Join-Path $stagingRoot "input"
$imageOutput = Join-Path $stagingRoot "image"
New-Item -ItemType Directory -Path $inputRoot, $imageOutput |
        Out-Null
Copy-Item -LiteralPath $JarPath -Destination (Join-Path $inputRoot "CoronaPoker.jar")

$icon = Join-Path $repositoryRoot "packaging\windows\CoronaPoker.ico"
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
    "--runtime-image", $resolvedJdkHome,
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
$installedJavaw = Join-Path $appImage "runtime\bin\javaw.exe"
foreach ($requiredFile in @($nativeLauncher, $installedJar,
        $installedConfig, $installedJava, $installedJavaw)) {
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
$runtimeModules = @(Invoke-Checked $installedJava @("--list-modules") "Packaged Java module inventory")
if ($runtimeModules.Count -eq 0 -or
        (Compare-Object -ReferenceObject $jdkModules -DifferenceObject $runtimeModules)) {
    throw "The packaged Java module set differs from the source JDK."
}
Write-Host "Packaged private Java contains all $($runtimeModules.Count) source JDK modules."
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
    $savedErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $installedJavaVersion = @(& $installedJava -version 2>&1 |
                ForEach-Object { $_.ToString() }) | Select-Object -First 1
        $installedJavaExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $savedErrorActionPreference
    }
    if ($installedJavaExitCode -ne 0) {
        throw "The packaged private Java runtime could not report its version."
    }
    $succeeded = $true
    [pscustomobject]@{
        Type = "exe"
        Version = $version
        Path = $finalPath
        Size = (Get-Item -LiteralPath $finalPath).Length
        Sha256 = $hash
        JarSha256 = (Get-FileHash -LiteralPath $installedJar -Algorithm SHA256).Hash
        LauncherVersion = (Get-Item -LiteralPath $nativeLauncher).VersionInfo.ProductVersion
        PerUser = $true
        PrivateRuntime = $true
        UpdaterCompatible = $true
        Languages = "Spanish (automatic), English"
        JavaRuntime = $installedJavaVersion
        Signed = -not [string]::IsNullOrWhiteSpace(
                $SigningCertificateThumbprint)
    }
} finally {
    if ($succeeded -and -not $KeepStaging -and (Test-Path -LiteralPath $stagingRoot)) {
        Remove-Item -LiteralPath $stagingRoot -Recurse -Force
    }
}
